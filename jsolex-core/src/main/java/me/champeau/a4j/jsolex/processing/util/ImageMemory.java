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
package me.champeau.a4j.jsolex.processing.util;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Allocates the memory which holds the pixels of images, outside of the Java
 * heap. Memory is released when the garbage collector finds that nothing
 * references it anymore.
 * <p>
 * Pixels live in RAM as long as the total stays under a budget: the limit of
 * direct memory of the JVM, reduced if needed so that the heap and the images
 * together use no more than 80% of the physical memory. Beyond that budget, new images are stored in temporary
 * files mapped in memory, which the operating system writes to disk and reads
 * back when it needs to. A garbage collection is requested when memory gets
 * close to the limit, so that the memory of images which are no longer used
 * is given back.
 * <p>
 * On Linux, large images are mapped directly from the operating system, on
 * huge pages when possible: such memory is filled with zeros for free, takes
 * far fewer page faults to fill, and is given back to the system as soon as
 * the image is released, instead of being kept by the C allocator.
 */
public final class ImageMemory {
    private static final long ALIGNMENT = 64;
    private static final long ANONYMOUS_MAPPING_THRESHOLD = 128 * 1024;
    private static final long RECLAIM_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final double RECLAIM_THRESHOLD = 0.8;
    private static final double MAX_SHARE_OF_RAM = 0.8;
    private static final long LIMIT = computeLimit();
    private static final LongSupplier DIRECT_MEMORY_USED = directMemoryUsed();
    private static final AtomicLong MAPPED = new AtomicLong();
    private static final AnonymousMemory ANONYMOUS_MEMORY = AnonymousMemory.create();
    private static final AtomicLong LAST_RECLAIM = new AtomicLong(System.nanoTime() - RECLAIM_INTERVAL_NANOS);
    private static final Queue<Path> UNDELETED_FILES = new ConcurrentLinkedQueue<>();

    private ImageMemory() {
    }

    /**
     * Returns the fraction of the RAM reserved for the pixels of images which
     * is currently used: 0 when no image is in memory, 1 or more when new
     * images are stored in temporary files.
     *
     * @return the pressure ratio
     */
    public static double pressure() {
        return (double) used() / LIMIT;
    }

    /**
     * Returns the amount of RAM used by the pixels of images.
     *
     * @return the used memory in bytes
     */
    public static long used() {
        return DIRECT_MEMORY_USED.getAsLong() + MAPPED.get();
    }

    /**
     * Returns the amount of memory which the application can use for its
     * data: the Java heap, plus the RAM reserved for the pixels of images.
     *
     * @return the memory budget in bytes
     */
    public static long budget() {
        return Runtime.getRuntime().maxMemory() + LIMIT;
    }

    static MemorySegment allocate(long bytes) {
        long used = used();
        if (used + bytes > RECLAIM_THRESHOLD * LIMIT) {
            requestReclaim();
        }
        if (used + bytes <= LIMIT) {
            var segment = allocateInRam(bytes);
            if (segment != null) {
                return segment;
            }
        }
        return mapTemporaryFile(bytes);
    }

    private static MemorySegment allocateInRam(long bytes) {
        if (ANONYMOUS_MEMORY != null && bytes >= ANONYMOUS_MAPPING_THRESHOLD) {
            var segment = ANONYMOUS_MEMORY.map(bytes);
            if (segment != null) {
                return segment;
            }
        }
        try {
            return Arena.ofAuto().allocate(bytes, ALIGNMENT);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static void requestReclaim() {
        long now = System.nanoTime();
        long last = LAST_RECLAIM.get();
        if (now - last > RECLAIM_INTERVAL_NANOS && LAST_RECLAIM.compareAndSet(last, now)) {
            Thread.startVirtualThread(System::gc);
        }
    }

    private static MemorySegment mapTemporaryFile(long bytes) {
        deleteUnmappedFiles();
        try {
            var file = TemporaryFolder.newTempFile("jsolex", ".img");
            try (var channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                var segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes, Arena.ofAuto());
                delete(file);
                return segment;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            UNDELETED_FILES.add(file);
        }
    }

    private static void deleteUnmappedFiles() {
        int pending = UNDELETED_FILES.size();
        for (int i = 0; i < pending; i++) {
            var file = UNDELETED_FILES.poll();
            if (file == null) {
                return;
            }
            delete(file);
        }
    }

    private static long computeLimit() {
        long heap = Runtime.getRuntime().maxMemory();
        long limit = maxDirectMemory(heap);
        long ram = physicalMemory();
        if (ram > 0) {
            limit = Math.min(limit, Math.max(0, (long) (MAX_SHARE_OF_RAM * ram) - heap));
        }
        return limit;
    }

    private static long maxDirectMemory(long heap) {
        try {
            long configured = Long.parseLong(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .getVMOption("MaxDirectMemorySize")
                    .getValue());
            return configured > 0 ? configured : heap;
        } catch (RuntimeException e) {
            return heap;
        }
    }

    private static long physicalMemory() {
        try {
            return ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static LongSupplier directMemoryUsed() {
        try {
            return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                    .stream()
                    .filter(pool -> "direct".equals(pool.getName()))
                    .findFirst()
                    .<LongSupplier>map(pool -> pool::getMemoryUsed)
                    .orElse(() -> 0);
        } catch (RuntimeException e) {
            return () -> 0;
        }
    }

    private record AnonymousMemory(MethodHandle mmap, MethodHandle munmap, MethodHandle madvise) {
        private static final int PROT_READ_WRITE = 0x3;
        private static final int MAP_PRIVATE_ANONYMOUS = 0x22;
        private static final int MADV_HUGEPAGE = 14;
        private static final long MAP_FAILED = -1;

        static AnonymousMemory create() {
            if (!"Linux".equals(System.getProperty("os.name"))) {
                return null;
            }
            try {
                var linker = Linker.nativeLinker();
                var lookup = linker.defaultLookup();
                return new AnonymousMemory(
                        linker.downcallHandle(lookup.find("mmap").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)),
                        linker.downcallHandle(lookup.find("munmap").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)),
                        linker.downcallHandle(lookup.find("madvise").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT))
                );
            } catch (Throwable e) {
                return null;
            }
        }

        MemorySegment map(long bytes) {
            try {
                var address = (MemorySegment) mmap.invokeExact(MemorySegment.NULL, bytes, PROT_READ_WRITE, MAP_PRIVATE_ANONYMOUS, -1, 0L);
                if (address.address() == MAP_FAILED) {
                    return null;
                }
                var unused = (int) madvise.invokeExact(address, bytes, MADV_HUGEPAGE);
                var segment = address.reinterpret(bytes, Arena.ofAuto(), mapped -> unmap(mapped, bytes));
                MAPPED.addAndGet(bytes);
                return segment;
            } catch (Throwable e) {
                return null;
            }
        }

        private void unmap(MemorySegment segment, long bytes) {
            try {
                var unused = (int) munmap.invokeExact(segment, bytes);
            } catch (Throwable e) {
                // ignore
            } finally {
                MAPPED.addAndGet(-bytes);
            }
        }
    }
}
