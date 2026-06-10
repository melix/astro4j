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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scoped GPU operation: tracks the buffers allocated through it and releases
 * them automatically when the body returns. Created via
 * {@link OpenCLContext#runOp(java.util.function.Consumer)} and
 * {@link OpenCLContext#runOp(java.util.function.Function)}; not intended to
 * be instantiated directly.
 *
 * <p>Concurrent ops on the same {@link OpenCLContext} are safe — each op
 * holds its own buffer references, and per-op {@link #kernel(String, String)}
 * calls borrow private kernel handles from the manager's pool, so no global
 * lock is required. Buffer allocations are gated by
 * {@link OpenCLContext#memoryBudget()}, which blocks rather than fails when
 * the GPU is full.
 *
 * <p>Kernel handles borrowed by this op are held until {@link #close} drains
 * the command queue via {@code clFinish}, only then returned to the pool.
 * This is required because OpenCL stores kernel arguments on the kernel
 * object itself; releasing a handle before its enqueued execution actually
 * runs would let another op modify the args from underneath the in-flight
 * kernel, producing data corruption or native crashes.
 */
public final class GpuOp implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpuOp.class);

    private final OpenCLContext context;
    private final Deque<Long> ownedBuffers = new ArrayDeque<>();
    private final Deque<KernelHandle> borrowedKernels = new ArrayDeque<>();

    private final GpuOperationEvent event = new GpuOperationEvent();
    private final Map<String, Integer> kernelLaunches = new LinkedHashMap<>();
    private long bytesUploaded;
    private long bytesDownloaded;
    private long bytesAllocated;
    private int buffersAllocated;
    private int launches;

    GpuOp(OpenCLContext context) {
        this.context = context;
        event.begin();
    }

    OpenCLContext getContext() {
        return context;
    }

    /**
     * Adopts a kernel handle so it's returned to the pool only after {@link #close} finishes.
     */
    void trackKernel(String programName, String kernelName, long kernel) {
        borrowedKernels.push(new KernelHandle(programName, kernelName, kernel));
    }

    private record KernelHandle(String programName, String kernelName, long kernel) {
    }

    /**
     * Allocates a GPU buffer owned by this op. The buffer is released when
     * the op closes; if the budget is exhausted, the call blocks until
     * another op releases enough memory.
     */
    public long allocateBuffer(int sizeInBytes, long flags) {
        long buffer = context.allocateBuffer(sizeInBytes, flags);
        ownedBuffers.push(buffer);
        bytesAllocated += sizeInBytes;
        buffersAllocated++;
        return buffer;
    }

    /**
     * Uploads {@code data} to {@code buffer}.
     */
    public void write(long buffer, float[] data) {
        context.writeBuffer(buffer, data);
        bytesUploaded += data.length * (long) Float.BYTES;
    }

    /**
     * Uploads {@code data} to {@code buffer}.
     */
    public void writeInts(long buffer, int[] data) {
        context.writeBufferInt(buffer, data);
        bytesUploaded += data.length * (long) Integer.BYTES;
    }

    /**
     * Downloads {@code buffer} contents into {@code data}. Blocks until any
     * pending kernel work that wrote to the buffer has completed.
     */
    public void read(long buffer, float[] data) {
        context.readBuffer(buffer, data);
        bytesDownloaded += data.length * (long) Float.BYTES;
    }

    /**
     * Same as {@link #read} but for int buffers.
     */
    public void readInts(long buffer, int[] data) {
        context.readBufferInt(buffer, data);
        bytesDownloaded += data.length * (long) Integer.BYTES;
    }

    void recordKernelLaunch(String programName, String kernelName) {
        launches++;
        if (event.isEnabled()) {
            kernelLaunches.merge(programName + "." + kernelName, 1, Integer::sum);
        }
    }

    /**
     * Begins building a kernel invocation. Borrows a fresh kernel handle from
     * the manager's pool that this op tracks until {@link #close}; this is
     * what guarantees the handle isn't reused by another op while the kernel
     * is still in flight.
     */
    public KernelCall kernel(String programName, String kernelName) {
        return new KernelCall(this, programName, kernelName);
    }

    @Override
    public void close() {
        // Always wait for pending kernel work to drain. This serves three roles:
        //  - if buffers are owned, the budget can't be returned until the GPU
        //    actually releases their memory (post-completion);
        //  - if no buffers are owned but the body enqueued kernels (e.g. against
        //    externally-owned buffers passed in), the caller still expects the
        //    GPU work to be done by the time runOp returns;
        //  - kernel handles can only be safely returned to the pool after the
        //    enqueued executions that bound their args have actually run.
        try {
            context.finish();
        } catch (RuntimeException e) {
            LOGGER.error("finish failed during close", e);
        }
        // Return kernels first, then buffers — order doesn't matter for
        // correctness post-finish, but kernels are pool-cheap to release.
        KernelHandle handle;
        var manager = context.getKernelManager();
        while ((handle = borrowedKernels.poll()) != null) {
            try {
                manager.release(handle.programName(), handle.kernelName(), handle.kernel());
            } catch (RuntimeException e) {
                LOGGER.error("kernel release failed", e);
            }
        }
        // LIFO release: most-recently-allocated first.
        Long buffer;
        while ((buffer = ownedBuffers.poll()) != null) {
            try {
                context.releaseBuffer(buffer);
            } catch (RuntimeException e) {
                // Best-effort cleanup: log and continue so a single failure
                // doesn't leak the rest. The OpenCLContext records the error.
                LOGGER.error("releaseBuffer failed", e);
            }
        }
        event.end();
        if (event.shouldCommit()) {
            event.kernels = kernelLaunches.entrySet().stream()
                    .map(e -> e.getValue() == 1 ? e.getKey() : e.getKey() + " x" + e.getValue())
                    .collect(Collectors.joining(", "));
            event.kernelLaunches = launches;
            event.bytesUploaded = bytesUploaded;
            event.bytesDownloaded = bytesDownloaded;
            event.bytesAllocated = bytesAllocated;
            event.buffersAllocated = buffersAllocated;
            event.commit();
        }
    }
}
