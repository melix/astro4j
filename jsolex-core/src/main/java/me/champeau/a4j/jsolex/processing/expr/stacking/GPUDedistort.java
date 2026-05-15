/*
 * Copyright 2025-2026 the original author or authors.
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

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;

/**
 * Performs GPU-accelerated image dedistortion using displacement grids.
 * <p>
 * Keeps data on the GPU, avoiding CPU transfers during multi-step processing.
 * Per-call kernel borrow makes this safe to invoke from concurrent threads.
 */
public final class GPUDedistort {

    private GPUDedistort() {
    }

    /**
     * Applies dedistortion to a GPU-resident image using a GPU-resident displacement grid.
     * The output is allocated as a fresh buffer wrapped in a new {@link GPUImage}; the
     * caller owns it and must close.
     */
    public static GPUImage dedistort(GPUImage gpuImage, GPUDistortionGrid gpuGrid, boolean useLanczos) {
        return dedistort(gpuImage, gpuGrid, useLanczos, 1.0);
    }

    /**
     * Applies dedistortion to a GPU-resident image, optionally producing a super-resolved
     * output. When {@code scale} is greater than 1, the result is allocated at
     * {@code round(scale*width)} by {@code round(scale*height)} and each output pixel is
     * sampled from the source at sub-pixel coordinates (inverse-warp drizzle).
     * The output is a fresh buffer wrapped in a new {@link GPUImage}; the caller owns it
     * and must close.
     */
    public static GPUImage dedistort(GPUImage gpuImage, GPUDistortionGrid gpuGrid, boolean useLanczos, double scale) {
        var context = gpuImage.getContext();
        int width = gpuImage.getWidth();
        int height = gpuImage.getHeight();
        int outWidth = (int) Math.round(width * scale);
        int outHeight = (int) Math.round(height * scale);
        int n = outWidth * outHeight;

        // Output buffer's lifetime escapes this call (returned via GPUImage), so it is
        // allocated directly through the context and not via op.allocateBuffer.
        long outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
        try {
            var kernelName = useLanczos ? "dedistort_sparse_lanczos" : "dedistort_sparse_bilinear";
            context.runOp(op -> {
                op.kernel("dedistort", kernelName)
                        .arg(gpuImage.getBuffer())
                        .arg(gpuGrid.getDxBuffer())
                        .arg(gpuGrid.getDyBuffer())
                        .arg(outputBuffer)
                        .arg(width).arg(height)
                        .arg(gpuGrid.getGridWidth()).arg(gpuGrid.getGridHeight()).arg(gpuGrid.getGridStep())
                        .arg(outWidth).arg(outHeight).arg((float) scale)
                        .run(outWidth, outHeight);
            });
            return GPUImage.fromBuffer(outputBuffer, outWidth, outHeight, context);
        } catch (Exception e) {
            context.releaseBuffer(outputBuffer);
            throw e;
        }
    }
}
