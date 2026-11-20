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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicLong;
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
 */
public final class ImageMemory {
    private static final long ALIGNMENT = 64;
    private static final long ANONYMOUS_MAPPING_THRESHOLD = 128 * 1024;
    private static final AtomicLong NATIVE = new AtomicLong();
    private static final NativeMemory NATIVE_MEMORY = NativeMemory.create();

    private ImageMemory() {
    }

    /**
     * Returns the amount of native memory used by the pixels of images.
     *
     * @return the used memory in bytes
     */
    public static long used() {
        return NATIVE.get();
    }

    static MemorySegment allocate(long bytes) {
        if (NATIVE_MEMORY != null) {
            var segment = NATIVE_MEMORY.allocate(bytes);
            if (segment != null) {
                return segment;
            }
        }
        return Arena.ofAuto().allocate(bytes, ALIGNMENT);
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

        MemorySegment allocate(long bytes) {
            try {
                if (mmap != null && bytes >= ANONYMOUS_MAPPING_THRESHOLD) {
                    var address = (MemorySegment) mmap.invokeExact(MemorySegment.NULL, bytes, PROT_READ_WRITE, MAP_PRIVATE_ANONYMOUS, -1, 0L);
                    if (address.address() != MAP_FAILED) {
                        var segment = track(address, bytes, mapped -> unmap(mapped, bytes));
                        var unused = (int) madvise.invokeExact(address, bytes, MADV_HUGEPAGE);
                        return segment;
                    }
                }
                var address = (MemorySegment) calloc.invokeExact(1L, bytes);
                if (address.address() == 0) {
                    return null;
                }
                return track(address, bytes, this::free);
            } catch (Throwable e) {
                return null;
            }
        }

        private static MemorySegment track(MemorySegment address, long bytes, Consumer<MemorySegment> release) {
            NATIVE.addAndGet(bytes);
            return address.reinterpret(bytes, Arena.ofAuto(), segment -> {
                try {
                    release.accept(segment);
                } finally {
                    NATIVE.addAndGet(-bytes);
                }
            });
        }

        private void unmap(MemorySegment segment, long bytes) {
            try {
                var unused = (int) munmap.invokeExact(segment, bytes);
            } catch (Throwable e) {
            }
        }

        private void free(MemorySegment segment) {
            try {
                free.invokeExact(segment);
            } catch (Throwable e) {
            }
        }
    }
}
