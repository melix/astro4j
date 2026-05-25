/*
 * Copyright 2025-2025 the original author or authors.
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
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.lwjgl.opencl.CL10.*;

/**
 * Manages the OpenCL context, device, and command queue lifecycle.
 * Provides device selection prioritizing discrete GPUs over integrated GPUs and CPUs.
 * <p>
 * Thread-safety: OpenCL kernel objects cache their arguments, so multiple threads
 * cannot set arguments on the same kernel handle concurrently. To run GPU work, use
 * {@link #runOp(Consumer)} or {@link #runOp(Function)} — the {@link GpuOp} body
 * borrows private kernel handles per call, so concurrent ops on this context run in
 * parallel without any global lock. Buffer allocations are gated by
 * {@link #memoryBudget()} which blocks rather than failing when the GPU is full.
 */
public class OpenCLContext implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLContext.class);

    private final long platform;
    private final long device;
    private final long context;
    private final long commandQueue;
    private final DeviceCapabilities capabilities;
    private final OpenCLKernelManager kernelManager;
    private final GpuMemoryBudget memoryBudget;

    // Memory tracking for debugging GPU memory leaks
    private final Map<Long, Long> allocatedBuffers = new ConcurrentHashMap<>();
    private final AtomicLong totalAllocatedBytes = new AtomicLong(0);
    private final AtomicLong peakAllocatedBytes = new AtomicLong(0);
    private final AtomicInteger allocationCount = new AtomicInteger(0);
    private final AtomicInteger releaseCount = new AtomicInteger(0);

    // Error tracking for detecting persistent GPU issues
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    private volatile GPUError lastError;

    // Bounded pool of reusable scratch FloatBuffers for writeBuffer. Capped at
    // WRITE_BUFFER_POOL_SIZE entries, each at most WRITE_BUFFER_MAX_CAPACITY floats,
    // so the total native footprint is bounded regardless of the largest upload
    // ever issued. Uploads larger than WRITE_BUFFER_MAX_CAPACITY bypass the pool
    // entirely — the per-call malloc cost is negligible relative to the PCIe
    // transfer those payloads will incur anyway.
    private static final int WRITE_BUFFER_POOL_SIZE = 16;
    private static final int WRITE_BUFFER_MIN_CAPACITY = 64 * 1024; // 64K floats = 256KB
    private static final int WRITE_BUFFER_MAX_CAPACITY = 256 * 1024; // 256K floats = 1MB
    private final ArrayBlockingQueue<FloatBuffer> writeBufferPool =
            new ArrayBlockingQueue<>(WRITE_BUFFER_POOL_SIZE);

    /**
     * Records a GPU error with the given message and cause.
     * This should be called from catch blocks that handle GPU fallback.
     */
    public void recordError(String message, Throwable cause) {
        consecutiveErrors.incrementAndGet();
        totalErrors.incrementAndGet();
        lastError = new GPUError(message, cause, System.currentTimeMillis());
    }

    /**
     * Resets consecutive error count (called on successful operation).
     */
    public void recordSuccess() {
        consecutiveErrors.set(0);
    }

    /**
     * Returns the total number of GPU errors recorded.
     */
    public int getTotalErrors() {
        return totalErrors.get();
    }

    /**
     * Returns the last GPU error, or null if none.
     */
    public GPUError getLastError() {
        return lastError;
    }

    /**
     * Resets all error tracking state. Useful for testing.
     */
    public void resetErrorTracking() {
        consecutiveErrors.set(0);
        totalErrors.set(0);
        lastError = null;
    }

    /**
     * Record of a GPU error for diagnosis.
     */
    public record GPUError(String message, Throwable cause, long timestamp) {
        @Override
        public String toString() {
            return String.format("[%d] %s: %s", timestamp, message,
                    cause != null ? cause.getMessage() : "no cause");
        }
    }

    private OpenCLContext(long platform, long device, long context, long commandQueue, DeviceCapabilities capabilities) {
        this.platform = platform;
        this.device = device;
        this.context = context;
        this.commandQueue = commandQueue;
        this.capabilities = capabilities;
        this.kernelManager = new OpenCLKernelManager(this);
        // Reserve 25% of device global memory as headroom for the OpenCL
        // runtime, the OS, the display compositor, etc. The remaining 75% is
        // exposed as the budget concurrent runOp callers compete on.
        long budget = (long) (capabilities.globalMemSize() * 0.75);
        this.memoryBudget = new GpuMemoryBudget(Math.max(64L * 1024 * 1024, budget));
    }

    /**
     * Attempts to create an OpenCL context.
     * Returns null if creation fails for any reason.
     *
     * @return an OpenCL context or null if creation fails
     */
    public static OpenCLContext tryCreate() {
        try {
            return create();
        } catch (Exception | UnsatisfiedLinkError e) {
            LOGGER.error("Failed to create OpenCL context", e);
            return null;
        }
    }

    private static OpenCLContext create() {
        try (var stack = MemoryStack.stackPush()) {
            var platforms = getPlatforms(stack);
            if (platforms == null || platforms.capacity() == 0) {
                return null;
            }

            long bestPlatform = 0;
            long bestDevice = 0;
            int bestScore = -1;
            DeviceCapabilities bestCapabilities = null;

            for (int i = 0; i < platforms.capacity(); i++) {
                long platform = platforms.get(i);
                var platformCaps = CL.createPlatformCapabilities(platform);

                var devices = getDevices(stack, platform);
                if (devices == null) {
                    continue;
                }

                for (int j = 0; j < devices.capacity(); j++) {
                    long device = devices.get(j);
                    int score = scoreDevice(device);
                    if (score > bestScore) {
                        bestScore = score;
                        bestPlatform = platform;
                        bestDevice = device;
                        bestCapabilities = queryCapabilities(device);
                    }
                }
            }

            if (bestDevice == 0) {
                return null;
            }

            var errBuf = stack.mallocInt(1);
            var ctxProps = stack.mallocPointer(3);
            ctxProps.put(0, CL_CONTEXT_PLATFORM);
            ctxProps.put(1, bestPlatform);
            ctxProps.put(2, 0);

            long context = clCreateContext(ctxProps, bestDevice, null, 0, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                return null;
            }

            long commandQueue = clCreateCommandQueue(context, bestDevice, 0, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                clReleaseContext(context);
                return null;
            }

            return new OpenCLContext(bestPlatform, bestDevice, context, commandQueue, bestCapabilities);
        }
    }

    private static PointerBuffer getPlatforms(MemoryStack stack) {
        var numPlatforms = stack.mallocInt(1);
        int err = clGetPlatformIDs(null, numPlatforms);
        if (err != CL_SUCCESS || numPlatforms.get(0) == 0) {
            return null;
        }
        var platforms = stack.mallocPointer(numPlatforms.get(0));
        clGetPlatformIDs(platforms, (IntBuffer) null);
        return platforms;
    }

    private static PointerBuffer getDevices(MemoryStack stack, long platform) {
        var numDevices = stack.mallocInt(1);
        int err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, null, numDevices);
        if (err != CL_SUCCESS || numDevices.get(0) == 0) {
            return null;
        }
        var devices = stack.mallocPointer(numDevices.get(0));
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, devices, (IntBuffer) null);
        return devices;
    }

    private static int scoreDevice(long device) {
        int score = 0;
        try (var stack = MemoryStack.stackPush()) {
            var typeBuf = stack.mallocLong(1);
            clGetDeviceInfo(device, CL_DEVICE_TYPE, typeBuf, null);
            long deviceType = typeBuf.get(0);

            if ((deviceType & CL_DEVICE_TYPE_GPU) != 0) {
                score += 1000;
                var unifiedBuf = stack.mallocInt(1);
                clGetDeviceInfo(device, CL11.CL_DEVICE_HOST_UNIFIED_MEMORY, unifiedBuf, null);
                if (unifiedBuf.get(0) == 0) {
                    score += 500;
                }
            } else if ((deviceType & CL_DEVICE_TYPE_CPU) != 0) {
                score += 100;
            }

            var computeUnits = stack.mallocInt(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, computeUnits, null);
            score += computeUnits.get(0);

            return score;
        }
    }

    private static DeviceCapabilities queryCapabilities(long device) {
        try (var stack = MemoryStack.stackPush()) {
            var sizeBuf = stack.mallocPointer(1);

            clGetDeviceInfo(device, CL_DEVICE_NAME, (ByteBuffer) null, sizeBuf);
            var nameBuf = stack.malloc((int) sizeBuf.get(0));
            clGetDeviceInfo(device, CL_DEVICE_NAME, nameBuf, null);
            String deviceName = MemoryUtil.memUTF8(nameBuf).trim();

            var maxWorkGroupSize = stack.mallocPointer(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, maxWorkGroupSize, null);

            var maxMemAlloc = stack.mallocLong(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE, maxMemAlloc, null);

            var globalMemSize = stack.mallocLong(1);
            clGetDeviceInfo(device, CL_DEVICE_GLOBAL_MEM_SIZE, globalMemSize, null);

            var maxComputeUnits = stack.mallocInt(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, maxComputeUnits, null);

            clGetDeviceInfo(device, CL_DEVICE_EXTENSIONS, (ByteBuffer) null, sizeBuf);
            var extBuf = stack.malloc((int) sizeBuf.get(0));
            clGetDeviceInfo(device, CL_DEVICE_EXTENSIONS, extBuf, null);
            String extensions = MemoryUtil.memUTF8(extBuf);
            boolean supportsDouble = extensions.contains("cl_khr_fp64");

            return new DeviceCapabilities(
                    deviceName,
                    maxWorkGroupSize.get(0),
                    maxMemAlloc.get(0),
                    globalMemSize.get(0),
                    maxComputeUnits.get(0),
                    supportsDouble
            );
        }
    }

    /**
     * Returns the OpenCL platform handle.
     *
     * @return the platform handle
     */
    public long getPlatform() {
        return platform;
    }

    /**
     * Returns the OpenCL device handle.
     *
     * @return the device handle
     */
    public long getDevice() {
        return device;
    }

    /**
     * Returns the OpenCL context handle.
     *
     * @return the context handle
     */
    public long getContext() {
        return context;
    }

    /**
     * Returns the OpenCL command queue handle.
     *
     * @return the command queue handle
     */
    public long getCommandQueue() {
        return commandQueue;
    }

    /**
     * Returns the capabilities of the selected OpenCL device.
     *
     * @return the device capabilities
     */
    public DeviceCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Returns the kernel manager for this context.
     *
     * @return the kernel manager
     */
    public OpenCLKernelManager getKernelManager() {
        return kernelManager;
    }

    /**
     * Returns the GPU memory budget. Used by {@link GpuOp} to gate buffer
     * allocations against the device's physical memory size; concurrent ops
     * proceed in parallel as long as their combined buffers fit, and block
     * (rather than fail) if they would exceed the budget.
     */
    public GpuMemoryBudget memoryBudget() {
        return memoryBudget;
    }

    /**
     * Runs a GPU operation in a scoped {@link GpuOp}. Buffers allocated via
     * the op are automatically released when the body returns; kernels used
     * via {@code op.kernel(...)} are borrowed from the per-thread pool and
     * returned automatically. No global lock is held — concurrent calls run
     * in parallel up to the GPU memory budget.
     */
    public void runOp(Consumer<? super GpuOp> body) {
        try (var op = new GpuOp(this)) {
            body.accept(op);
        }
    }

    /**
     * {@link #runOp(Consumer)} variant that returns a value.
     */
    public <T> T runOp(Function<? super GpuOp, ? extends T> body) {
        try (var op = new GpuOp(this)) {
            return body.apply(op);
        }
    }

    /**
     * Allocates a buffer on the device. Prefer {@link GpuOp#allocateBuffer}
     * for normal use — it ties the buffer's lifetime to the surrounding
     * {@link #runOp} call. This method exists for buffers whose lifetime
     * escapes the op (e.g. wrapped in a {@code GPUImage} returned to the
     * caller); such buffers must be freed explicitly via {@link #releaseBuffer}.
     *
     * @param sizeInBytes the size in bytes
     * @param flags       memory flags (e.g., CL_MEM_READ_WRITE)
     * @return the buffer handle
     */
    public long allocateBuffer(int sizeInBytes, long flags) {
        // Reserve space in the device-level budget *before* asking OpenCL.
        // If another op is currently holding the memory we need, this blocks
        // here rather than letting clCreateBuffer fail with a hard OOM.
        memoryBudget.acquire(sizeInBytes);
        boolean acquired = true;
        try (var stack = MemoryStack.stackPush()) {
            var errBuf = stack.mallocInt(1);
            long buffer = clCreateBuffer(context, flags, sizeInBytes, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                long currentMB = totalAllocatedBytes.get() / (1024 * 1024);
                long peakMB = peakAllocatedBytes.get() / (1024 * 1024);
                int outstanding = allocationCount.get() - releaseCount.get();
                var ex = new OpenCLException("Failed to allocate buffer: error=" + errBuf.get(0) +
                        ", requested=" + (sizeInBytes / 1024) + " KB" +
                        ", currentlyAllocated=" + currentMB + " MB" +
                        ", peak=" + peakMB + " MB" +
                        ", outstandingBuffers=" + outstanding);
                recordError("allocateBuffer", ex);
                LOGGER.error("{}", ex.getMessage());
                throw ex;
            }
            // Track allocation
            allocatedBuffers.put(buffer, (long) sizeInBytes);
            long newTotal = totalAllocatedBytes.addAndGet(sizeInBytes);
            peakAllocatedBytes.updateAndGet(peak -> Math.max(peak, newTotal));
            allocationCount.incrementAndGet();
            acquired = false;   // ownership transferred to the live buffer; releaseBuffer will free the budget
            return buffer;
        } finally {
            if (acquired) {
                memoryBudget.release(sizeInBytes);
            }
        }
    }

    /**
     * Writes float data to {@code buffer}. Blocking — returns once the write
     * is complete on the device.
     */
    public void writeBuffer(long buffer, float[] data) {
        var floatBuffer = acquireWriteBuffer(data.length);
        try {
            floatBuffer.clear();
            floatBuffer.put(data).flip();
            int err = clEnqueueWriteBuffer(commandQueue, buffer, true, 0, floatBuffer, null, null);
            if (err != CL_SUCCESS) {
                var ex = new OpenCLException("Failed to write buffer: " + err + " | " + getMemoryStats());
                recordError("writeBuffer", ex);
                throw ex;
            }
        } finally {
            releaseWriteBuffer(floatBuffer);
        }
    }

    private FloatBuffer acquireWriteBuffer(int minCapacity) {
        if (minCapacity > WRITE_BUFFER_MAX_CAPACITY) {
            return MemoryUtil.memAllocFloat(minCapacity);
        }
        FloatBuffer candidate;
        while ((candidate = writeBufferPool.poll()) != null) {
            if (candidate.capacity() >= minCapacity) {
                return candidate;
            }
            MemoryUtil.memFree(candidate);
        }
        return MemoryUtil.memAllocFloat(Math.max(minCapacity, WRITE_BUFFER_MIN_CAPACITY));
    }

    private void releaseWriteBuffer(FloatBuffer buffer) {
        if (buffer.capacity() > WRITE_BUFFER_MAX_CAPACITY || !writeBufferPool.offer(buffer)) {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Writes int data to {@code buffer}. Blocking — returns once the write
     * is complete on the device.
     */
    public void writeBufferInt(long buffer, int[] data) {
        var intBuffer = MemoryUtil.memAllocInt(data.length);
        try {
            intBuffer.put(data).flip();
            int err = clEnqueueWriteBuffer(commandQueue, buffer, true, 0, intBuffer, null, null);
            if (err != CL_SUCCESS) {
                var ex = new OpenCLException("Failed to write int buffer: " + err + " | " + getMemoryStats());
                recordError("writeBufferInt", ex);
                throw ex;
            }
        } finally {
            MemoryUtil.memFree(intBuffer);
        }
    }

    /**
     * Reads float data from {@code buffer}, finishing pending kernel work
     * first so the read sees the post-kernel contents.
     */
    public void readBuffer(long buffer, float[] data) {
        // Ensure all pending kernel operations complete before reading
        int finishErr = clFinish(commandQueue);
        if (finishErr != CL_SUCCESS) {
            var ex = new OpenCLException("clFinish failed in readBuffer with error: " + finishErr + " | " + getMemoryStats());
            recordError("readBuffer.clFinish", ex);
            LOGGER.error("{}", ex.getMessage());
        }
        readBufferNoSync(buffer, data);
    }

    /**
     * Reads float data from {@code buffer} without finishing pending kernel
     * work first. The caller must guarantee any kernels writing this buffer
     * have completed (typically by a single {@link #finish()} before a batch
     * of reads).
     */
    public void readBufferNoSync(long buffer, float[] data) {
        var floatBuffer = MemoryUtil.memAllocFloat(data.length);
        try {
            // blocking=true ensures the read completes before returning
            int err = clEnqueueReadBuffer(commandQueue, buffer, true, 0, floatBuffer, null, null);
            if (err != CL_SUCCESS) {
                var ex = new OpenCLException("Failed to read buffer: " + err + " | " + getMemoryStats());
                recordError("readBuffer", ex);
                throw ex;
            }
            floatBuffer.rewind();
            floatBuffer.get(data);
        } finally {
            MemoryUtil.memFree(floatBuffer);
        }
    }

    /**
     * Reads int data from a buffer, finishing pending kernel work first.
     */
    public void readBufferInt(long buffer, int[] data) {
        int finishErr = clFinish(commandQueue);
        if (finishErr != CL_SUCCESS) {
            recordError("readBufferInt.clFinish", new OpenCLException("clFinish failed: " + finishErr));
        }
        var intBuffer = MemoryUtil.memAllocInt(data.length);
        try {
            int err = clEnqueueReadBuffer(commandQueue, buffer, true, 0, intBuffer, null, null);
            if (err != CL_SUCCESS) {
                var ex = new OpenCLException("Failed to read int buffer: " + err + " | " + getMemoryStats());
                recordError("readBufferInt", ex);
                throw ex;
            }
            intBuffer.rewind();
            intBuffer.get(data);
        } finally {
            MemoryUtil.memFree(intBuffer);
        }
    }

    /**
     * Releases a buffer that was allocated directly via
     * {@link #allocateBuffer}. Buffers owned by a {@link GpuOp} are released
     * automatically and must not be passed here.
     */
    public void releaseBuffer(long buffer) {
        // Track release
        Long size = allocatedBuffers.remove(buffer);
        if (size != null) {
            totalAllocatedBytes.addAndGet(-size);
            releaseCount.incrementAndGet();
            memoryBudget.release(size);
        } else {
            LOGGER.error("Releasing unknown buffer: {}", buffer);
        }
        int err = clReleaseMemObject(buffer);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clReleaseMemObject failed with error: " + err + " | " + getMemoryStats());
            recordError("releaseBuffer", ex);
            LOGGER.error("{}", ex.getMessage());
        }
    }

    /**
     * Flushes the command queue. Lighter than {@link #finish()} — it submits
     * pending commands but does not wait for them to complete.
     */
    public void flush() {
        int err = clFlush(commandQueue);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clFlush failed with error: " + err + " | " + getMemoryStats());
            recordError("flush", ex);
            LOGGER.error("{}", ex.getMessage());
        }
    }

    /**
     * Waits for all enqueued commands on this context's queue to complete.
     */
    public void finish() {
        int err = clFinish(commandQueue);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clFinish failed with error: " + err + " | " + getMemoryStats());
            recordError("finish", ex);
            LOGGER.error("{}", ex.getMessage());
        }
    }

    /**
     * Returns a snapshot of the current GPU memory allocation statistics
     * (current/peak usage, outstanding buffer count) for diagnostics.
     */
    public String getMemoryStats() {
        long currentBytes = totalAllocatedBytes.get();
        long peakBytes = peakAllocatedBytes.get();
        int allocs = allocationCount.get();
        int releases = releaseCount.get();
        int outstanding = allocs - releases;
        return String.format("GPU Memory: current=%d MB, peak=%d MB, outstanding=%d buffers (allocs=%d, releases=%d)",
                currentBytes / (1024 * 1024), peakBytes / (1024 * 1024), outstanding, allocs, releases);
    }

    /**
     * Runs a simple self-test to verify the OpenCL pipeline works correctly.
     * This compiles a kernel, allocates buffers, executes the kernel, and verifies the results.
     *
     * @return true if the self-test passes
     */
    public boolean runSelfTest() {
        return runOp(op -> {
            int testSize = 16;
            var outputBuffer = op.allocateBuffer(testSize * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.kernel("selftest", "selftest")
                    .arg(outputBuffer)
                    .run(testSize);
            var output = new float[testSize];
            op.read(outputBuffer, output);
            for (int i = 0; i < testSize; i++) {
                float expected = i * 2 + 1;
                if (Math.abs(output[i] - expected) > 0.001f) {
                    return false;
                }
            }
            return true;
        });
    }

    @Override
    public void close() {
        FloatBuffer pooled;
        while ((pooled = writeBufferPool.poll()) != null) {
            MemoryUtil.memFree(pooled);
        }
        kernelManager.close();
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
    }

    /**
     * Capabilities of an OpenCL device.
     *
     * @param deviceName       the device name
     * @param maxWorkGroupSize maximum work group size
     * @param maxMemAllocSize  maximum memory allocation size for a single buffer
     * @param globalMemSize    total global memory size on the device
     * @param maxComputeUnits  number of compute units
     * @param supportsDouble   whether double precision is supported
     */
    public record DeviceCapabilities(
            String deviceName,
            long maxWorkGroupSize,
            long maxMemAllocSize,
            long globalMemSize,
            int maxComputeUnits,
            boolean supportsDouble
    ) {
    }
}
