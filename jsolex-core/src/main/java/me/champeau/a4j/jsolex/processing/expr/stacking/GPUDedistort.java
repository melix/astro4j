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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLException;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;

/**
 * Performs GPU-accelerated image dedistortion using displacement grids.
 * <p>
 * This class provides a GPU-to-GPU dedistortion operation that keeps data
 * on the GPU, avoiding CPU transfers during multi-step processing.
 * <p>
 * All methods must be called within {@link OpenCLContext#executeWithLock}.
 */
public final class GPUDedistort {

    private GPUDedistort() {
    }

    /**
     * Applies dedistortion to a GPU-resident image using a GPU-resident displacement grid.
     * <p>
     * The operation reads from the input image, applies the displacement correction
     * specified by the grid, and writes to a new output buffer. The input image
     * and grid are not modified.
     * <p>
     * Must be called within executeWithLock.
     *
     * @param gpuImage   GPU-resident input image
     * @param gpuGrid    GPU-resident distortion grid
     * @param useLanczos true for Lanczos interpolation (sharper), false for bilinear (faster)
     * @return new GPU-resident image with dedistortion applied (caller must close)
     */
    public static GPUImage dedistort(GPUImage gpuImage, GPUDistortionGrid gpuGrid, boolean useLanczos) {
        var context = gpuImage.getContext();
        int width = gpuImage.getWidth();
        int height = gpuImage.getHeight();
        int n = width * height;

        long outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);

        try {
            var kernelName = useLanczos ? "dedistort_sparse_lanczos" : "dedistort_sparse_bilinear";
            var kernel = context.getKernelManager().getKernel("dedistort", kernelName);

            clSetKernelArg1p(kernel, 0, gpuImage.getBuffer());
            clSetKernelArg1p(kernel, 1, gpuGrid.getDxBuffer());
            clSetKernelArg1p(kernel, 2, gpuGrid.getDyBuffer());
            clSetKernelArg1p(kernel, 3, outputBuffer);
            clSetKernelArg1i(kernel, 4, width);
            clSetKernelArg1i(kernel, 5, height);
            clSetKernelArg1i(kernel, 6, gpuGrid.getGridWidth());
            clSetKernelArg1i(kernel, 7, gpuGrid.getGridHeight());
            clSetKernelArg1i(kernel, 8, gpuGrid.getGridStep());

            executeKernel2D(context, kernel, width, height);

            return GPUImage.fromBuffer(outputBuffer, width, height, context);
        } catch (Exception e) {
            context.releaseBuffer(outputBuffer);
            throw e;
        }
    }

    private static void executeKernel2D(OpenCLContext context, long kernel, int width, int height) {
        var globalWorkSize = BufferUtils.createPointerBuffer(2);
        globalWorkSize.put(0, width);
        globalWorkSize.put(1, height);

        int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 2,
                null, globalWorkSize, null, null, null);
        if (err != CL_SUCCESS) {
            throw new OpenCLException("Failed to execute kernel: " + err);
        }
        context.finish();
    }
}
