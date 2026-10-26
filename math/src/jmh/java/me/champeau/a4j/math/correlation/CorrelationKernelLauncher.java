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
package me.champeau.a4j.math.correlation;

import me.champeau.a4j.math.opencl.GpuOp;
import me.champeau.a4j.math.opencl.OpenCLContext;

/**
 * Exposes the package-private correlation pipeline steps to the kernel benchmark.
 */
public final class CorrelationKernelLauncher {
    private CorrelationKernelLauncher() {
    }

    public static float[][] residentNcc(OpenCLContext context, long refImage, long targetImage,
                                        int width, int height, int[][] positions, int tileSize) {
        return CorrelationTools.getInstance().correlateResidentImagesNCC(context, refImage, targetImage, width, height, positions, tileSize);
    }

    public static void fft2D(GpuOp op, long data, int tileSize, int logTileSize, int numTiles, int direction) {
        CorrelationTools.fft2D(op, data, tileSize, logTileSize, numTiles, direction);
    }

    /**
     * Phase correlation on tiles already resident on the GPU, same kernel
     * sequence as the batched large-tile path without the uploads.
     */
    public static void phasePipeline(GpuOp op, long refTiles, long targetTiles,
                                     long refComplex, long targetComplex, long real, long results,
                                     int tileSize, int numTiles) {
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        CorrelationTools.applyHann(op, refTiles, refComplex, tileSize, numTiles);
        CorrelationTools.applyHann(op, targetTiles, targetComplex, tileSize, numTiles);
        CorrelationTools.fft2D(op, refComplex, tileSize, logTileSize, numTiles, -1);
        CorrelationTools.fft2D(op, targetComplex, tileSize, logTileSize, numTiles, -1);
        op.kernel("correlation", "cross_power_spectrum")
                .arg(refComplex).arg(targetComplex).arg(tileSize).arg(numTiles).arg(1)
                .global(tileSize, tileSize, numTiles).run();
        CorrelationTools.fft2D(op, refComplex, tileSize, logTileSize, numTiles, +1);
        op.kernel("correlation", "fft_shift_real")
                .arg(refComplex).arg(real).arg(tileSize).arg(numTiles).arg(1.0f / (float) (tileSize * tileSize))
                .global(tileSize, tileSize, numTiles).run();
        op.kernel("correlation", "find_peaks")
                .arg(real).arg(results).arg(tileSize).arg(numTiles)
                .global(numTiles * 256L).local(256).run();
    }
}
