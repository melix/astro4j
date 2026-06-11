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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clSetKernelArg;
import static org.lwjgl.opencl.CL10.clSetKernelArg1f;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;

/**
 * Fluent builder for one OpenCL kernel invocation. Returned by
 * {@link GpuOp#kernel(String, String)}; chain {@code arg(...)},
 * {@code global(...)}, optionally {@code local(...)}, then call
 * {@link #run()} (or one of the convenience {@code run(...)} overloads) to
 * enqueue the kernel.
 *
 * <p>Args are positional — each {@code arg(...)} call corresponds to one
 * {@code clSetKernelArg} on the next index. Buffer handles are passed as
 * {@code long}; primitive scalars use the typed overloads;
 * {@link #argLocalMemory(long)} reserves {@code __local} memory.
 *
 * <p>Each KernelCall borrows a fresh kernel handle from the manager pool
 * and hands it to the surrounding {@link GpuOp}, which keeps it out of the
 * pool until {@code clFinish} runs. This is required because OpenCL stores
 * kernel arguments on the kernel object and reads them at execution time,
 * not at enqueue time — returning the handle to the pool too early would
 * let another op set new args on it before the previous enqueue actually
 * runs on the device.
 */
public final class KernelCall {

    private final GpuOp op;
    private final OpenCLContext context;
    private final String programName;
    private final String kernelName;
    private final long kernel;
    private int nextArg;
    private long[] globalWorkSize;
    private long[] localWorkSize;
    private boolean done;

    KernelCall(GpuOp op, String programName, String kernelName) {
        this.op = op;
        this.context = op.getContext();
        this.programName = programName;
        this.kernelName = kernelName;
        this.kernel = context.getKernelManager().borrow(programName, kernelName);
        op.trackKernel(programName, kernelName, this.kernel);
    }

    /**
     * Sets the next positional arg as a buffer handle (pointer).
     */
    public KernelCall arg(long buffer) {
        ensureOpen();
        check(clSetKernelArg1p(kernel, nextArg++, buffer), "clSetKernelArg pointer");
        return this;
    }

    /**
     * Sets the next positional arg as an int scalar.
     */
    public KernelCall arg(int value) {
        ensureOpen();
        check(clSetKernelArg1i(kernel, nextArg++, value), "clSetKernelArg int");
        return this;
    }

    /**
     * Sets the next positional arg as a float scalar.
     */
    public KernelCall arg(float value) {
        ensureOpen();
        check(clSetKernelArg1f(kernel, nextArg++, value), "clSetKernelArg float");
        return this;
    }

    /**
     * Allocates {@code sizeBytes} of {@code __local} memory at the next
     * positional arg. OpenCL: {@code clSetKernelArg(kernel, idx, sizeBytes)}
     * with a {@code long} third argument and no data pointer.
     */
    public KernelCall argLocalMemory(long sizeBytes) {
        ensureOpen();
        check(clSetKernelArg(kernel, nextArg++, sizeBytes), "clSetKernelArg local memory");
        return this;
    }

    /**
     * Sets the global work size. Vararg supports 1D, 2D, 3D dispatches.
     */
    public KernelCall global(long... dims) {
        ensureOpen();
        if (dims.length < 1 || dims.length > 3) {
            throw new IllegalArgumentException("global work size must have 1-3 dimensions");
        }
        this.globalWorkSize = dims;
        return this;
    }

    /**
     * Sets the local work size. Must match the dimensionality of {@link #global}.
     */
    public KernelCall local(long... dims) {
        ensureOpen();
        if (dims.length < 1 || dims.length > 3) {
            throw new IllegalArgumentException("local work size must have 1-3 dimensions");
        }
        this.localWorkSize = dims;
        return this;
    }

    /**
     * Enqueues the kernel with the previously-configured work sizes. Does
     * <em>not</em> call {@code clFinish} — the in-order command queue
     * guarantees that the next enqueued kernel won't start until this one
     * completes, and any subsequent {@link GpuOp#read} (or
     * {@link GpuOp#close}) provides the host-visible synchronization point.
     * The borrowed kernel handle stays out of the pool until the surrounding
     * {@link GpuOp#close} drains the queue.
     */
    public void run() {
        ensureOpen();
        if (globalWorkSize == null) {
            throw new IllegalStateException("global work size not set");
        }
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer globalPtr = stack.mallocPointer(globalWorkSize.length);
            for (int i = 0; i < globalWorkSize.length; i++) {
                globalPtr.put(i, globalWorkSize[i]);
            }
            PointerBuffer localPtr = null;
            if (localWorkSize != null) {
                if (localWorkSize.length != globalWorkSize.length) {
                    throw new IllegalStateException(
                            "local work size dimensionality (" + localWorkSize.length
                                    + ") must match global (" + globalWorkSize.length + ")");
                }
                localPtr = stack.mallocPointer(localWorkSize.length);
                for (int i = 0; i < localWorkSize.length; i++) {
                    localPtr.put(i, localWorkSize[i]);
                }
            }
            int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel,
                    globalWorkSize.length, null, globalPtr, localPtr, null, null);
            check(err, "clEnqueueNDRangeKernel");
        }
        done = true;
        op.recordKernelLaunch(programName, kernelName);
    }

    /**
     * Convenience: 1D dispatch over {@code n} work-items.
     */
    public void run(long n) {
        global(n).run();
    }

    /**
     * Convenience: 2D dispatch.
     */
    public void run(long width, long height) {
        global(width, height).run();
    }

    private void ensureOpen() {
        if (done) {
            throw new IllegalStateException("kernel call already run");
        }
    }

    private static void check(int err, String op) {
        if (err != CL_SUCCESS) {
            throw new OpenCLException(op + " failed: " + err);
        }
    }
}
