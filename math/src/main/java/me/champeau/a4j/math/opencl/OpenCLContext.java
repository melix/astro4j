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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.lwjgl.opencl.CL10.*;

/**
 * Manages the OpenCL context, device, and command queue lifecycle.
 * Provides device selection prioritizing discrete GPUs over integrated GPUs and CPUs.
 * <p>
 * Thread-safety: OpenCL kernel objects cache their arguments, which can cause race conditions
 * if multiple threads set arguments on the same kernel simultaneously. All GPU operations
 * must be synchronized using {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
 */
public class OpenCLContext implements AutoCloseable {
    // Global lock for all GPU operations to prevent kernel argument race conditions.
    // This lock must be used by all code that sets kernel arguments and executes kernels.
    private final ReentrantLock gpuLock = new ReentrantLock();

    private final long platform;
    private final long device;
    private final long context;
    private final long commandQueue;
    private final DeviceCapabilities capabilities;
    private final OpenCLKernelManager kernelManager;

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

    // Cache for writeBuffer operations - reused across calls
    // Safe because gpuLock ensures sequential access
    private FloatBuffer writeCache;

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
            System.err.println("[OpenCLContext] Failed to create OpenCL context");
            e.printStackTrace();
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
     * Executes a GPU operation while holding the GPU lock.
     * This method properly acquires and releases the lock to ensure thread-safe
     * kernel argument setting and execution.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the result of the operation
     */
    public <T> T executeWithLock(Supplier<T> operation) {
        gpuLock.lock();
        try {
            return operation.get();
        } finally {
            gpuLock.unlock();
        }
    }

    /**
     * Executes a GPU operation while holding the GPU lock.
     * This method properly acquires and releases the lock to ensure thread-safe
     * kernel argument setting and execution.
     *
     * @param operation the operation to execute
     */
    public void executeWithLock(Runnable operation) {
        gpuLock.lock();
        try {
            operation.run();
        } finally {
            gpuLock.unlock();
        }
    }

    /**
     * Verifies that the current thread holds the GPU lock.
     * All GPU operations must be performed while holding the lock.
     *
     * @throws IllegalStateException if the current thread does not hold the lock
     */
    private void requireLock() {
        if (!gpuLock.isHeldByCurrentThread()) {
            throw new IllegalStateException(
                    "GPU operations must be performed within executeWithLock(). " +
                    "Current thread does not hold the GPU lock.");
        }
    }

    /**
     * Allocates a buffer on the device.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param sizeInBytes the size in bytes
     * @param flags       memory flags (e.g., CL_MEM_READ_WRITE)
     * @return the buffer handle
     * @throws IllegalStateException if not called within executeWithLock
     */
    public long allocateBuffer(int sizeInBytes, long flags) {
        requireLock();
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
                System.err.println("[OpenCLContext] " + ex.getMessage());
                throw ex;
            }
            // Track allocation
            allocatedBuffers.put(buffer, (long) sizeInBytes);
            long newTotal = totalAllocatedBytes.addAndGet(sizeInBytes);
            peakAllocatedBytes.updateAndGet(peak -> Math.max(peak, newTotal));
            allocationCount.incrementAndGet();
            return buffer;
        }
    }

    /**
     * Gets or grows the write cache to hold at least minCapacity floats.
     * The cache is reused across writeBuffer calls to avoid repeated allocations.
     */
    private FloatBuffer getOrGrowWriteCache(int minCapacity) {
        if (writeCache == null || writeCache.capacity() < minCapacity) {
            if (writeCache != null) {
                MemoryUtil.memFree(writeCache);
            }
            // Round up to power of 2 for efficient reuse
            int newCapacity = Integer.highestOneBit(minCapacity - 1) << 1;
            newCapacity = Math.max(newCapacity, 64 * 1024); // Min 64K floats (~256KB)
            writeCache = MemoryUtil.memAllocFloat(newCapacity);
        }
        return writeCache;
    }

    /**
     * Writes float data to a buffer.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param buffer the buffer handle
     * @param data the data to write
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void writeBuffer(long buffer, float[] data) {
        requireLock();
        var floatBuffer = getOrGrowWriteCache(data.length);
        floatBuffer.clear();
        floatBuffer.put(data).flip();
        // blocking=true ensures the write completes before returning
        int err = clEnqueueWriteBuffer(commandQueue, buffer, true, 0, floatBuffer, null, null);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("Failed to write buffer: " + err + " | " + getMemoryStats());
            recordError("writeBuffer", ex);
            throw ex;
        }
        // Don't free - keep for reuse
    }

    /**
     * Writes int data to a buffer.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param buffer the buffer handle
     * @param data the data to write
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void writeBufferInt(long buffer, int[] data) {
        requireLock();
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
     * Reads float data from a buffer, ensuring all pending operations complete first.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param buffer the buffer handle
     * @param data the array to read into
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void readBuffer(long buffer, float[] data) {
        requireLock();
        // Ensure all pending kernel operations complete before reading
        int finishErr = clFinish(commandQueue);
        if (finishErr != CL_SUCCESS) {
            var ex = new OpenCLException("clFinish failed in readBuffer with error: " + finishErr + " | " + getMemoryStats());
            recordError("readBuffer.clFinish", ex);
            System.err.println("[OpenCLContext] " + ex.getMessage());
        }
        readBufferNoSync(buffer, data);
    }

    /**
     * Reads float data from a buffer without synchronizing first.
     * Caller must ensure all kernel operations affecting this buffer have completed
     * (typically by calling {@link #finish()} once before multiple reads).
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param buffer the buffer handle
     * @param data the array to read into
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void readBufferNoSync(long buffer, float[] data) {
        requireLock();
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
     * Releases a buffer.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @param buffer the buffer handle
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void releaseBuffer(long buffer) {
        requireLock();
        // Track release
        Long size = allocatedBuffers.remove(buffer);
        if (size != null) {
            totalAllocatedBytes.addAndGet(-size);
            releaseCount.incrementAndGet();
        } else {
            System.err.println("[OpenCLContext] Releasing unknown buffer: " + buffer);
        }
        int err = clReleaseMemObject(buffer);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clReleaseMemObject failed with error: " + err + " | " + getMemoryStats());
            recordError("releaseBuffer", ex);
            System.err.println("[OpenCLContext] " + ex.getMessage());
        }
    }

    /**
     * Flushes the command queue to ensure all commands are submitted.
     * This is lighter than finish() - it doesn't wait for completion.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void flush() {
        requireLock();
        int err = clFlush(commandQueue);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clFlush failed with error: " + err + " | " + getMemoryStats());
            recordError("flush", ex);
            System.err.println("[OpenCLContext] " + ex.getMessage());
        }
    }

    /**
     * Waits for all enqueued operations to complete.
     * Must be called within {@link #executeWithLock(Supplier)} or {@link #executeWithLock(Runnable)}.
     *
     * @throws IllegalStateException if not called within executeWithLock
     */
    public void finish() {
        requireLock();
        int err = clFinish(commandQueue);
        if (err != CL_SUCCESS) {
            var ex = new OpenCLException("clFinish failed with error: " + err + " | " + getMemoryStats());
            recordError("finish", ex);
            System.err.println("[OpenCLContext] " + ex.getMessage());
        }
    }

    /**
     * Returns the current GPU memory allocation statistics for debugging.
     * Can be called without holding the lock.
     *
     * @return a string describing current memory usage
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
        return executeWithLock(() -> {
            int testSize = 16;
            var output = new float[testSize];
            long outputBuffer = 0;

            try {
                var kernel = kernelManager.getKernel("selftest", "selftest");

                outputBuffer = allocateBuffer(testSize * Float.BYTES, CL_MEM_WRITE_ONLY);

                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(kernel, 0, stack.pointers(outputBuffer));

                    var globalSize = stack.pointers(testSize);
                    int err = clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalSize, null, null, null);
                    if (err != CL_SUCCESS) {
                        return false;
                    }
                }

                finish();
                readBuffer(outputBuffer, output);

                for (int i = 0; i < testSize; i++) {
                    float expected = i * 2 + 1;
                    if (Math.abs(output[i] - expected) > 0.001f) {
                        return false;
                    }
                }

                return true;
            } finally {
                if (outputBuffer != 0) {
                    releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public void close() {
        if (writeCache != null) {
            MemoryUtil.memFree(writeCache);
            writeCache = null;
        }
        kernelManager.close();
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
    }

    /**
     * Capabilities of an OpenCL device.
     *
     * @param deviceName the device name
     * @param maxWorkGroupSize maximum work group size
     * @param maxMemAllocSize maximum memory allocation size for a single buffer
     * @param globalMemSize total global memory size on the device
     * @param maxComputeUnits number of compute units
     * @param supportsDouble whether double precision is supported
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
