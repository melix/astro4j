/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.math.opencl;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Byte-counted budget guarding GPU memory allocations. Concurrent callers
 * may proceed in parallel as long as the total of their requested allocations
 * fits within {@code totalBytes}; allocations that don't fit block until
 * enough memory is released by other callers.
 *
 * <p>This is the GPU analog of a memory-pressure semaphore: it replaces a
 * coarse "max concurrent ops" cap with the only constraint that actually
 * matters — the device's physical memory size. Two requests of 1 GB each
 * on a 24 GB GPU run in parallel; a single 30 GB request fails immediately.
 *
 * <p>{@link #acquire(long)} is FIFO-ish but not strictly fair under heavy
 * contention: a steady stream of small allocations can theoretically starve
 * a large one. For the workloads this targets — a small number of states
 * each requesting buffers of similar size — that's not a problem in practice.
 */
public final class GpuMemoryBudget {
    private final long totalBytes;
    private final Lock lock = new ReentrantLock();
    private final Condition freed = lock.newCondition();
    private long available;

    public GpuMemoryBudget(long totalBytes) {
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes must be positive: " + totalBytes);
        }
        this.totalBytes = totalBytes;
        this.available = totalBytes;
    }

    /**
     * Reserves {@code bytes} from the budget, blocking until enough is
     * available. Throws if the request can never succeed (exceeds the
     * configured total).
     */
    public void acquire(long bytes) {
        if (bytes <= 0) {
            return;
        }
        if (bytes > totalBytes) {
            throw new OpenCLException("GPU allocation request of " + bytes
                    + " bytes exceeds total budget " + totalBytes);
        }
        lock.lock();
        try {
            while (available < bytes) {
                freed.await();
            }
            available -= bytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenCLException("Interrupted while waiting for GPU memory", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code bytes} to the budget and wakes up any waiting acquirers.
     */
    public void release(long bytes) {
        if (bytes <= 0) {
            return;
        }
        lock.lock();
        try {
            available += bytes;
            if (available > totalBytes) {
                available = totalBytes;
            }
            freed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Total budget in bytes.
     */
    public long totalBytes() {
        return totalBytes;
    }

    /**
     * Currently-available bytes. Snapshot value, useful for diagnostics only.
     */
    public long availableBytes() {
        lock.lock();
        try {
            return available;
        } finally {
            lock.unlock();
        }
    }
}
