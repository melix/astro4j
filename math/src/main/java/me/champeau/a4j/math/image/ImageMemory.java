/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.math.image;

import me.champeau.a4j.utilities.SystemMemory;
import me.champeau.a4j.utilities.TemporaryFolder;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Allocates the memory which holds the pixels of images, outside of the Java
 * heap. Memory is released when the garbage collector finds that nothing
 * references it anymore.
 * <p>
 * On Linux, large images are mapped directly from the operating system, on
 * huge pages when possible: such memory is filled with zeros for free, takes
 * far fewer page faults to fill, and is given back to the system as soon as
 * the image is released, instead of being kept by the C allocator.
 * <p>
 * When images take more than their share of the memory of the computer, the
 * least recently used ones are moved to disk: their pixels are written to
 * a temporary file, which is mapped in place of their memory, and their memory
 * is released at once. The image stays usable, and the operating system can
 * drop it from memory without swapping.
 */
public final class ImageMemory {
    /**
     * System property which gives, in bytes, the amount of memory images may
     * use before the least recently used ones are moved to disk. It defaults
     * to 40% of the memory of the computer.
     */
    public static final String IN_MEMORY_PROPERTY = "astro4j.images.memory";

    private static final double IN_MEMORY_SHARE = 0.4;
    private static final long IN_MEMORY_LIMIT = Long.getLong(IN_MEMORY_PROPERTY, (long) (IN_MEMORY_SHARE * SystemMemory.total()));
    private static final long ALIGNMENT = 64;
    private static final long ANONYMOUS_MAPPING_THRESHOLD = 128 * 1024;
    private static final long MOVABLE_THRESHOLD = 1024 * 1024;
    private static final long WRITE_CHUNK = 256L * 1024 * 1024;
    private static final long MOVER_PERIOD_NANOS = 100_000_000L;
    private static final long PINS_TIMEOUT_NANOS = 2_000_000L;
    private static final int MAX_ATTEMPTS = 256;
    private static final int STRIPES = 64;
    private static final int MOVING = 0;
    private static final int NOT_PINNED = -1;
    private static final AtomicLong IN_MEMORY = new AtomicLong();
    private static final AtomicLong ON_DISK = new AtomicLong();
    private static final AtomicLong CLOCK = new AtomicLong();
    private static final Set<Block> MOVABLE = ConcurrentHashMap.newKeySet();
    private static final Queue<Block> COLDEST = new ConcurrentLinkedQueue<>();
    private static final ReentrantLock REFILL = new ReentrantLock();
    private static final NativeMemory NATIVE_MEMORY = NativeMemory.create();
    private static final Thread MOVER = Thread.ofPlatform().daemon().name("image-memory").unstarted(ImageMemory::moveLoop);

    static {
        MOVER.start();
    }

    private ImageMemory() {
    }

    /**
     * Returns the amount of memory used by the pixels of images, without the
     * images which were moved to disk.
     *
     * @return the used memory in bytes
     */
    public static long used() {
        return IN_MEMORY.get();
    }

    /**
     * Returns the size of the images which were moved to disk.
     *
     * @return the size in bytes
     */
    public static long onDisk() {
        return ON_DISK.get();
    }

    static Pixels allocate(long bytes) {
        if (NATIVE_MEMORY != null) {
            if (IN_MEMORY.get() + bytes > IN_MEMORY_LIMIT) {
                makeRoom(IN_MEMORY_LIMIT - bytes);
            }
            var pixels = NATIVE_MEMORY.allocate(bytes);
            if (pixels != null) {
                return pixels;
            }
        }
        return new Pixels(null, Arena.ofAuto().allocate(bytes, ALIGNMENT));
    }

    private static void moveLoop() {
        while (true) {
            CLOCK.incrementAndGet();
            var target = IN_MEMORY_LIMIT - IN_MEMORY_LIMIT / 10;
            if (IN_MEMORY.get() > target) {
                makeRoom(target);
            }
            LockSupport.parkNanos(MOVER_PERIOD_NANOS);
        }
    }

    private static void makeRoom(long target) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS && IN_MEMORY.get() > target && !Thread.currentThread().isInterrupted(); attempts++) {
            var block = COLDEST.poll();
            if (block == null) {
                if (!findColdest()) {
                    return;
                }
            } else {
                block.moveToDisk();
            }
        }
    }

    private static boolean findColdest() {
        record Candidate(Block block, long lastUse) {
        }
        REFILL.lock();
        try {
            if (!COLDEST.isEmpty()) {
                return true;
            }
            var candidates = MOVABLE.stream()
                    .map(block -> new Candidate(block, block.lastUse))
                    .sorted(Comparator.comparingLong(Candidate::lastUse))
                    .toList();
            var bytes = 0L;
            var enough = IN_MEMORY_LIMIT / 8;
            for (var candidate : candidates) {
                COLDEST.add(candidate.block());
                bytes += candidate.block().bytes;
                if (bytes >= enough) {
                    break;
                }
            }
            return !candidates.isEmpty();
        } finally {
            REFILL.unlock();
        }
    }

    /**
     * The memory which holds the pixels of an image. It may be replaced when
     * the image is moved to disk, so it must only be used between a call to
     * {@link #pin()} and the matching call to {@link #unpin(int)}.
     */
    static final class Pixels {
        private final Block block;
        private MemorySegment segment;

        private Pixels(Block block, MemorySegment segment) {
            this.block = block;
            this.segment = segment;
        }

        int pin() {
            return block == null ? NOT_PINNED : block.pin();
        }

        void unpin(int pin) {
            if (pin != NOT_PINNED) {
                block.unpin(pin);
            }
        }

        MemorySegment segment() {
            return segment;
        }

        boolean moveToDisk() {
            return block != null && block.moveToDisk();
        }

        boolean isOnDisk() {
            return block != null && block.isOnDisk();
        }
    }

    /**
     * What moving an image to disk needs to know about its memory. An image
     * is only moved while nothing uses its pixels, and uses started during a
     * move wait for its end.
     * <p>
     * Uses are counted per thread stripe, each on its own cache line, so that
     * threads working on the same image don't slow each other down.
     */
    private static final class Block {
        private final long bytes;
        private final MemorySegment memory;
        private final Consumer<MemorySegment> release;
        private final AtomicLongArray pins = new AtomicLongArray((STRIPES + 1) << 3);
        private WeakReference<Pixels> pixels;
        private long lastUse = CLOCK.getPlain();
        private boolean onDisk;

        private Block(MemorySegment memory, Consumer<MemorySegment> release) {
            this.bytes = memory.byteSize();
            this.memory = memory;
            this.release = release;
        }

        private Pixels track() {
            var tracked = new Pixels(this, memory.reinterpret(Arena.ofAuto(), unused -> releaseMemory()));
            pixels = new WeakReference<>(tracked);
            MOVABLE.add(this);
            return tracked;
        }

        private int pin() {
            var now = CLOCK.getPlain();
            if (lastUse != now) {
                lastUse = now;
            }
            var stripe = (((int) Thread.currentThread().threadId() & (STRIPES - 1)) + 1) << 3;
            while (true) {
                pins.getAndIncrement(stripe);
                if (pins.get(MOVING) == 0) {
                    return stripe;
                }
                pins.getAndDecrement(stripe);
                awaitEndOfMove();
            }
        }

        private void unpin(int stripe) {
            pins.getAndDecrement(stripe);
        }

        private synchronized void awaitEndOfMove() {
        }

        private boolean awaitNotPinned() {
            var deadline = System.nanoTime() + PINS_TIMEOUT_NANOS;
            while (true) {
                var active = 0L;
                for (int stripe = 1; stripe <= STRIPES; stripe++) {
                    active += pins.get(stripe << 3);
                }
                if (active == 0) {
                    return true;
                }
                if (System.nanoTime() > deadline) {
                    return false;
                }
                Thread.onSpinWait();
            }
        }

        private synchronized boolean isOnDisk() {
            return onDisk;
        }

        private synchronized boolean moveToDisk() {
            var current = pixels.get();
            if (current == null || onDisk) {
                return false;
            }
            pins.set(MOVING, 1);
            try {
                if (awaitNotPinned()) {
                    current.segment = writeToDisk();
                    onDisk = true;
                    MOVABLE.remove(this);
                    release.accept(memory);
                    IN_MEMORY.addAndGet(-bytes);
                    ON_DISK.addAndGet(bytes);
                }
            } catch (IOException | RuntimeException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    MOVABLE.remove(this);
                }
            } finally {
                pins.set(MOVING, 0);
            }
            return onDisk;
        }

        private MemorySegment writeToDisk() throws IOException {
            var file = TemporaryFolder.newTempFile("image", ".raw");
            var arena = Arena.ofShared();
            try (var channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                for (long offset = 0; offset < bytes; offset += WRITE_CHUNK) {
                    var buffer = memory.asSlice(offset, Math.min(WRITE_CHUNK, bytes - offset)).asByteBuffer();
                    while (buffer.hasRemaining()) {
                        channel.write(buffer, offset + buffer.position());
                    }
                }
                return channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes, arena).reinterpret(Arena.ofAuto(), unused -> releaseFile(arena, file));
            } catch (IOException | RuntimeException e) {
                arena.close();
                Files.deleteIfExists(file);
                throw e;
            }
        }

        private synchronized void releaseMemory() {
            if (!onDisk) {
                MOVABLE.remove(this);
                release.accept(memory);
                IN_MEMORY.addAndGet(-bytes);
            }
        }

        private void releaseFile(Arena arena, Path file) {
            ON_DISK.addAndGet(-bytes);
            arena.close();
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
            }
        }
    }

    private record NativeMemory(MethodHandle calloc, MethodHandle free, MethodHandle mmap, MethodHandle munmap, MethodHandle madvise) {
        private static final int PROT_READ_WRITE = 0x3;
        private static final int MAP_PRIVATE_ANONYMOUS = 0x22;
        private static final int MADV_HUGEPAGE = 14;
        private static final long MAP_FAILED = -1;

        static NativeMemory create() {
            try {
                var linker = Linker.nativeLinker();
                var lookup = linker.defaultLookup();
                var calloc = linker.downcallHandle(lookup.find("calloc").orElseThrow(), FunctionDescriptor.of(ADDRESS, JAVA_LONG, JAVA_LONG));
                var free = linker.downcallHandle(lookup.find("free").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
                if (!"Linux".equals(System.getProperty("os.name"))) {
                    return new NativeMemory(calloc, free, null, null, null);
                }
                return new NativeMemory(
                        calloc,
                        free,
                        linker.downcallHandle(lookup.find("mmap").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)),
                        linker.downcallHandle(lookup.find("munmap").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)),
                        linker.downcallHandle(lookup.find("madvise").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT))
                );
            } catch (Throwable e) {
                return null;
            }
        }

        Pixels allocate(long bytes) {
            try {
                if (mmap != null && bytes >= ANONYMOUS_MAPPING_THRESHOLD) {
                    var address = (MemorySegment) mmap.invokeExact(MemorySegment.NULL, bytes, PROT_READ_WRITE, MAP_PRIVATE_ANONYMOUS, -1, 0L);
                    if (address.address() != MAP_FAILED) {
                        var pixels = track(address.reinterpret(bytes), this::unmap);
                        var unused = (int) madvise.invokeExact(address, bytes, MADV_HUGEPAGE);
                        return pixels;
                    }
                }
                var address = (MemorySegment) calloc.invokeExact(1L, bytes);
                if (address.address() == 0) {
                    return null;
                }
                return track(address.reinterpret(bytes), this::free);
            } catch (Throwable e) {
                return null;
            }
        }

        private static Pixels track(MemorySegment memory, Consumer<MemorySegment> release) {
            var bytes = memory.byteSize();
            IN_MEMORY.addAndGet(bytes);
            if (bytes >= MOVABLE_THRESHOLD) {
                return new Block(memory, release).track();
            }
            return new Pixels(null, memory.reinterpret(Arena.ofAuto(), unused -> {
                try {
                    release.accept(memory);
                } finally {
                    IN_MEMORY.addAndGet(-bytes);
                }
            }));
        }

        private void unmap(MemorySegment memory) {
            try {
                var unused = (int) munmap.invokeExact(memory, memory.byteSize());
            } catch (Throwable e) {
            }
        }

        private void free(MemorySegment memory) {
            try {
                free.invokeExact(memory);
            } catch (Throwable e) {
            }
        }
    }
}
