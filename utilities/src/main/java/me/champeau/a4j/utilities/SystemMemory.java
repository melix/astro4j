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
package me.champeau.a4j.utilities;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

/**
 * Physical memory of the computer, which bounds the memory used by the heap
 * and by the pixels of images, since images live outside of the heap.
 */
public final class SystemMemory {
    private static final double USABLE_SHARE = 0.8;
    private static final long TOTAL = computeTotal();

    private SystemMemory() {
    }

    /**
     * Returns the physical memory of the computer, or the maximum heap size
     * when it cannot be determined.
     *
     * @return the physical memory in bytes
     */
    public static long total() {
        return TOTAL;
    }

    /**
     * Returns the share of the physical memory which processing can use.
     *
     * @return the usable memory in bytes
     */
    public static long usable() {
        return (long) (USABLE_SHARE * TOTAL);
    }

    private static long computeTotal() {
        var heap = Runtime.getRuntime().maxMemory();
        try {
            var total = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
            return total > 0 ? total : heap;
        } catch (RuntimeException e) {
            return heap;
        }
    }
}
