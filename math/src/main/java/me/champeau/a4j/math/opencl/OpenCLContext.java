/*
 * Copyright 2023-2023 the original author or authors.
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
import java.nio.IntBuffer;
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
            String deviceName = org.lwjgl.system.MemoryUtil.memUTF8(nameBuf).trim();

            var maxWorkGroupSize = stack.mallocPointer(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, maxWorkGroupSize, null);

            var maxMemAlloc = stack.mallocLong(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE, maxMemAlloc, null);

            var maxComputeUnits = stack.mallocInt(1);
            clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, maxComputeUnits, null);

            clGetDeviceInfo(device, CL_DEVICE_EXTENSIONS, (ByteBuffer) null, sizeBuf);
            var extBuf = stack.malloc((int) sizeBuf.get(0));
            clGetDeviceInfo(device, CL_DEVICE_EXTENSIONS, extBuf, null);
            String extensions = org.lwjgl.system.MemoryUtil.memUTF8(extBuf);
            boolean supportsDouble = extensions.contains("cl_khr_fp64");

            return new DeviceCapabilities(
                    deviceName,
                    maxWorkGroupSize.get(0),
                    maxMemAlloc.get(0),
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
     * Allocates a buffer on the device.
     *
     * @param sizeInBytes the size in bytes
     * @param flags       memory flags (e.g., CL_MEM_READ_WRITE)
     * @return the buffer handle
     */
    public long allocateBuffer(int sizeInBytes, int flags) {
        try (var stack = MemoryStack.stackPush()) {
            var errBuf = stack.mallocInt(1);
            long buffer = clCreateBuffer(context, flags, sizeInBytes, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                throw new OpenCLException("Failed to allocate buffer: " + errBuf.get(0));
            }
            return buffer;
        }
    }

    /**
     * Writes float data to a buffer.
     *
     * @param buffer the buffer handle
     * @param data the data to write
     */
    public void writeBuffer(long buffer, float[] data) {
        // Use direct buffer allocation for OpenCL compatibility
        var floatBuffer = MemoryUtil.memAllocFloat(data.length);
        try {
            floatBuffer.put(data).flip();
            // blocking=true ensures the write completes before returning
            int err = clEnqueueWriteBuffer(commandQueue, buffer, true, 0, floatBuffer, null, null);
            if (err != CL_SUCCESS) {
                throw new OpenCLException("Failed to write buffer: " + err);
            }
        } finally {
            MemoryUtil.memFree(floatBuffer);
        }
    }

    /**
     * Reads float data from a buffer.
     *
     * @param buffer the buffer handle
     * @param data the array to read into
     */
    public void readBuffer(long buffer, float[] data) {
        // Ensure all pending kernel operations complete before reading
        clFinish(commandQueue);
        var floatBuffer = MemoryUtil.memAllocFloat(data.length);
        try {
            // blocking=true ensures the read completes before returning
            int err = clEnqueueReadBuffer(commandQueue, buffer, true, 0, floatBuffer, null, null);
            if (err != CL_SUCCESS) {
                throw new OpenCLException("Failed to read buffer: " + err);
            }
            floatBuffer.rewind();
            floatBuffer.get(data);
        } finally {
            MemoryUtil.memFree(floatBuffer);
        }
    }

    /**
     * Releases a buffer.
     *
     * @param buffer the buffer handle
     */
    public void releaseBuffer(long buffer) {
        clReleaseMemObject(buffer);
    }

    /**
     * Waits for all enqueued operations to complete.
     */
    public void finish() {
        clFinish(commandQueue);
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
        kernelManager.close();
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
    }

    /**
     * Capabilities of an OpenCL device.
     *
     * @param deviceName the device name
     * @param maxWorkGroupSize maximum work group size
     * @param maxMemAllocSize maximum memory allocation size
     * @param maxComputeUnits number of compute units
     * @param supportsDouble whether double precision is supported
     */
    public record DeviceCapabilities(
            String deviceName,
            long maxWorkGroupSize,
            long maxMemAllocSize,
            int maxComputeUnits,
            boolean supportsDouble
    ) {
    }
}
