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

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static org.lwjgl.opencl.CL10.*;

/**
 * Manages OpenCL kernel loading, compilation, and caching.
 * Kernels are loaded from classpath resources.
 */
public class OpenCLKernelManager implements AutoCloseable {
    private static final String KERNEL_PATH = "/me/champeau/a4j/math/opencl/kernels/";

    private final OpenCLContext context;
    private final Map<String, Long> compiledPrograms = new ConcurrentHashMap<>();
    /**
     * Per-(program, kernel) pool of free kernel handles. Used by {@link #borrow}/{@link #release}.
     */
    private final Map<String, ConcurrentLinkedDeque<Long>> kernelPool = new ConcurrentHashMap<>();
    /**
     * Tracks every kernel handle ever created via this manager so {@link #close} can release them all.
     */
    private final ConcurrentLinkedDeque<Long> allKernels = new ConcurrentLinkedDeque<>();

    OpenCLKernelManager(OpenCLContext context) {
        this.context = context;
    }

    /**
     * Borrows a kernel handle private to the calling thread for the duration
     * of one operation. The caller must {@link #release} the handle when
     * done; while held, only the borrowing thread should call
     * {@code clSetKernelArg} on it. Multiple concurrent borrows of the same
     * (program, kernel) name return distinct handles, removing the need for
     * a global lock around kernel argument setup.
     */
    public long borrow(String programName, String kernelName) {
        var key = programName + ":" + kernelName;
        var pool = kernelPool.get(key);
        if (pool != null) {
            Long existing = pool.pollFirst();
            if (existing != null) {
                return existing;
            }
        }
        // Miss: create a fresh kernel against the (already-compiled) program.
        var program = getOrCompileProgram(programName);
        long kernel = createKernel(program, kernelName);
        allKernels.add(kernel);
        return kernel;
    }

    /**
     * Returns a previously-borrowed kernel handle to the pool for reuse.
     */
    public void release(String programName, String kernelName, long kernel) {
        var key = programName + ":" + kernelName;
        kernelPool.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).offerFirst(kernel);
    }

    /**
     * Returns the number of free kernel handles currently in the pool for {@code (programName, kernelName)}.
     */
    public int poolSize(String programName, String kernelName) {
        var pool = kernelPool.get(programName + ":" + kernelName);
        return pool == null ? 0 : pool.size();
    }

    private long getOrCompileProgram(String programName) {
        return compiledPrograms.computeIfAbsent(programName, this::compileProgram);
    }

    private long compileProgram(String programName) {
        var source = loadKernelSource(programName);
        try (var stack = MemoryStack.stackPush()) {
            var errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), source, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                throw new OpenCLException("Failed to create program: " + errBuf.get(0));
            }

            int err = clBuildProgram(program, context.getDevice(), "", null, 0);
            if (err != CL_SUCCESS) {
                var buildLog = getBuildLog(program, context.getDevice());
                throw new OpenCLException("Failed to build program " + programName + ": " + buildLog);
            }

            return program;
        }
    }

    private String loadKernelSource(String programName) {
        var resourcePath = KERNEL_PATH + programName + ".cl";
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new OpenCLException("Kernel resource not found: " + resourcePath);
            }
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new OpenCLException("Failed to load kernel: " + resourcePath, e);
        }
    }

    private String getBuildLog(long program, long device) {
        try (var stack = MemoryStack.stackPush()) {
            var sizeBuf = stack.mallocPointer(1);
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, sizeBuf);
            var logBuf = stack.malloc((int) sizeBuf.get(0));
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, logBuf, null);
            return MemoryUtil.memUTF8(logBuf);
        }
    }

    private long createKernel(long program, String kernelName) {
        try (var stack = MemoryStack.stackPush()) {
            var errBuf = stack.mallocInt(1);
            long kernel = clCreateKernel(program, kernelName, errBuf);
            if (errBuf.get(0) != CL_SUCCESS) {
                throw new OpenCLException("Failed to create kernel " + kernelName + ": " + errBuf.get(0));
            }
            return kernel;
        }
    }

    @Override
    public void close() {
        for (var kernel : allKernels) {
            clReleaseKernel(kernel);
        }
        allKernels.clear();
        kernelPool.clear();
        for (var program : compiledPrograms.values()) {
            clReleaseProgram(program);
        }
        compiledPrograms.clear();
    }
}
