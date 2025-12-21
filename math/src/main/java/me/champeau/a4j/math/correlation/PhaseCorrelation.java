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
package me.champeau.a4j.math.correlation;

import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.tuples.DoublePair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opencl.CL10.*;

/**
 * Batched phase correlation for tile-based image alignment.
 * <p>
 * This class provides both CPU and GPU implementations of phase correlation,
 * using the shared OpenCLContext for GPU operations to ensure proper locking.
 * The GPU implementation batches all tile correlations into a single GPU call,
 * which amortizes the GPU invocation overhead across thousands of tiles.
 */
public class PhaseCorrelation {
    private static final int MIN_TILES_FOR_GPU = 100;
    private static final Map<Integer, float[][]> HANN_WINDOW_CACHE = new ConcurrentHashMap<>();
    private static final PhaseCorrelation INSTANCE = new PhaseCorrelation();

    private PhaseCorrelation() {
    }

    /**
     * Returns the singleton instance.
     *
     * @return the phase correlation instance
     */
    public static PhaseCorrelation getInstance() {
        return INSTANCE;
    }

    /**
     * Compute phase correlation for multiple tile pairs.
     * Uses GPU if available and tile count is large enough, otherwise falls back to CPU.
     *
     * @param refTiles    Reference tiles [N][tileSize][tileSize]
     * @param targetTiles Target tiles [N][tileSize][tileSize]
     * @return Displacements [N][2] containing (dx, dy) per tile
     */
    public float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles) {
        if (refTiles.length == 0) {
            return new float[0][2];
        }
        int numTiles = refTiles.length;
        var context = OpenCLSupport.getContext();
        if (context != null && OpenCLSupport.isEnabled() && numTiles >= MIN_TILES_FOR_GPU) {
            try {
                var result = batchedCorrelationGPU(context, refTiles, targetTiles);
                // System.err.println("[PhaseCorrelation] GPU completed | " + context.getMemoryStats());
                return result;
            } catch (Exception e) {
                System.err.println("[PhaseCorrelation] GPU failed, falling back to CPU | " + context.getMemoryStats());
                e.printStackTrace();
                context.recordError("PhaseCorrelation.batchedCorrelation", e);
            }
        }
        return batchedCorrelationCPU(refTiles, targetTiles);
    }

    private float[][] batchedCorrelationCPU(float[][][] refTiles, float[][][] targetTiles) {
        int n = refTiles.length;
        var results = new float[n][2];
        for (int i = 0; i < n; i++) {
            var shift = phaseCorrelationShiftFFT(refTiles[i], targetTiles[i]);
            results[i][0] = (float) -shift.b();
            results[i][1] = (float) -shift.a();
        }
        return results;
    }

    private float[][] batchedCorrelationGPU(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles) {
        int tileSize = refTiles[0].length;

        if (tileSize == 32) {
            return batchedCorrelationGPU32(context, refTiles, targetTiles);
        } else if (tileSize == 64 || tileSize == 128) {
            return batchedCorrelationGPULarge(context, refTiles, targetTiles, tileSize);
        } else {
            return batchedCorrelationCPU(refTiles, targetTiles);
        }
    }

    private float[][] batchedCorrelationGPU32(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles) {
        int numTiles = refTiles.length;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var results = new float[numTiles * 2];

        return context.executeWithLock(() -> {
            long refBuffer = 0;
            long targetBuffer = 0;
            long resultBuffer = 0;
            try {
                refBuffer = context.allocateBuffer(refFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                targetBuffer = context.allocateBuffer(targetFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                resultBuffer = context.allocateBuffer(results.length * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(refBuffer, refFlat);
                context.writeBuffer(targetBuffer, targetFlat);

                var kernel = context.getKernelManager().getKernel("phase_correlation", "batched_phase_correlation_32");

                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    clSetKernelArg(kernel, 0, stack.pointers(refBuffer));
                    clSetKernelArg(kernel, 1, stack.pointers(targetBuffer));
                    clSetKernelArg(kernel, 2, stack.pointers(resultBuffer));
                    clSetKernelArg(kernel, 3, stack.ints(numTiles));

                    var globalSize = stack.pointers((long) numTiles * 32, 32);
                    var localSize = stack.pointers(32, 32);

                    int err = clEnqueueNDRangeKernel(
                            context.getCommandQueue(),
                            kernel,
                            2,
                            null,
                            globalSize,
                            localSize,
                            null,
                            null
                    );
                    if (err != 0) {
                        throw new RuntimeException("Failed to enqueue kernel: " + err);
                    }
                }

                context.finish();
                context.readBufferNoSync(resultBuffer, results);

                return unflatten2D(results, numTiles, 2);
            } finally {
                // Release each buffer in its own try-catch to ensure all are released
                safeRelease(context, refBuffer);
                safeRelease(context, targetBuffer);
                safeRelease(context, resultBuffer);
            }
        });
    }

    private float[][] batchedCorrelationGPULarge(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, int tileSize) {
        int numTiles = refTiles.length;
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var results = new float[numTiles * 2];

        return context.executeWithLock(() -> {
            long refInputBuffer = 0;
            long targetInputBuffer = 0;
            long refComplexBuffer = 0;
            long targetComplexBuffer = 0;
            long tempBuffer = 0;
            long realBuffer = 0;
            long resultBuffer = 0;

            try {
                int complexSize = numTiles * tileElements * 2 * Float.BYTES;
                int realSize = numTiles * tileElements * Float.BYTES;

                refInputBuffer = context.allocateBuffer(realSize, CL_MEM_READ_ONLY);
                targetInputBuffer = context.allocateBuffer(realSize, CL_MEM_READ_ONLY);
                refComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                targetComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                tempBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                realBuffer = context.allocateBuffer(realSize, CL_MEM_READ_WRITE);
                resultBuffer = context.allocateBuffer(results.length * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(refInputBuffer, refFlat);
                context.writeBuffer(targetInputBuffer, targetFlat);

                var km = context.getKernelManager();
                var applyHannKernel = km.getKernel("phase_correlation", "apply_hann_window");
                var fftRowsKernel = km.getKernel("phase_correlation", "fft_rows");
                var transposeKernel = km.getKernel("phase_correlation", "transpose");
                var crossPowerKernel = km.getKernel("phase_correlation", "cross_power_spectrum");
                var scaleIfftKernel = km.getKernel("phase_correlation", "scale_ifft");
                var fftShiftKernel = km.getKernel("phase_correlation", "fft_shift_real");
                var findPeaksKernel = km.getKernel("phase_correlation", "find_peaks");

                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var queue = context.getCommandQueue();

                    // 1. Apply Hann window to both ref and target
                    clSetKernelArg(applyHannKernel, 0, stack.pointers(refInputBuffer));
                    clSetKernelArg(applyHannKernel, 1, stack.pointers(refComplexBuffer));
                    clSetKernelArg(applyHannKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(applyHannKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, applyHannKernel, tileSize, tileSize, numTiles);

                    clSetKernelArg(applyHannKernel, 0, stack.pointers(targetInputBuffer));
                    clSetKernelArg(applyHannKernel, 1, stack.pointers(targetComplexBuffer));
                    enqueue3D(queue, applyHannKernel, tileSize, tileSize, numTiles);

                    // 2. Forward 2D FFT for reference: rows -> transpose -> rows -> transpose
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 3. Forward 2D FFT for target
                    fft2D(queue, fftRowsKernel, transposeKernel, targetComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 4. Cross-power spectrum
                    clSetKernelArg(crossPowerKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 1, stack.pointers(targetComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(crossPowerKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, crossPowerKernel, tileSize, tileSize, numTiles);

                    // 5. Inverse 2D FFT
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, +1, stack);

                    // 6. Scale by 1/N^2
                    clSetKernelArg(scaleIfftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(scaleIfftKernel, 1, stack.ints(tileSize));
                    clSetKernelArg(scaleIfftKernel, 2, stack.ints(numTiles));
                    enqueue3D(queue, scaleIfftKernel, tileSize, tileSize, numTiles);

                    // 7. FFT shift and extract real part
                    clSetKernelArg(fftShiftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(fftShiftKernel, 1, stack.pointers(realBuffer));
                    clSetKernelArg(fftShiftKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(fftShiftKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, fftShiftKernel, tileSize, tileSize, numTiles);

                    // 8. Find peaks
                    clSetKernelArg(findPeaksKernel, 0, stack.pointers(realBuffer));
                    clSetKernelArg(findPeaksKernel, 1, stack.pointers(resultBuffer));
                    clSetKernelArg(findPeaksKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(findPeaksKernel, 3, stack.ints(numTiles));

                    var globalSize = stack.pointers(numTiles * 256L);
                    var localSize = stack.pointers(256);
                    int err = clEnqueueNDRangeKernel(queue, findPeaksKernel, 1, null, globalSize, localSize, null, null);
                    if (err != 0) {
                        throw new RuntimeException("Failed to enqueue find_peaks kernel: " + err);
                    }
                }

                context.finish();
                context.readBufferNoSync(resultBuffer, results);

                return unflatten2D(results, numTiles, 2);
            } finally {
                // Release each buffer in its own try-catch to ensure all are released
                safeRelease(context, refInputBuffer);
                safeRelease(context, targetInputBuffer);
                safeRelease(context, refComplexBuffer);
                safeRelease(context, targetComplexBuffer);
                safeRelease(context, tempBuffer);
                safeRelease(context, realBuffer);
                safeRelease(context, resultBuffer);
            }
        });
    }

    private static void safeRelease(OpenCLContext context, long buffer) {
        if (buffer != 0) {
            try {
                context.releaseBuffer(buffer);
            } catch (Exception e) {
                System.err.println("[PhaseCorrelation] Failed to release GPU buffer");
                e.printStackTrace();
            }
        }
    }

    private void fft2D(long queue, long fftRowsKernel, long transposeKernel,
                       long dataBuffer, long tempBuffer,
                       int tileSize, int logTileSize, int numTiles, int direction,
                       org.lwjgl.system.MemoryStack stack) {
        // Row FFT
        clSetKernelArg(fftRowsKernel, 0, stack.pointers(dataBuffer));
        clSetKernelArg(fftRowsKernel, 1, stack.ints(tileSize));
        clSetKernelArg(fftRowsKernel, 2, stack.ints(logTileSize));
        clSetKernelArg(fftRowsKernel, 3, stack.ints(direction));

        var globalSize = stack.pointers(tileSize, (long) numTiles * tileSize);
        var localSize = stack.pointers(tileSize, 1);
        int err = clEnqueueNDRangeKernel(queue, fftRowsKernel, 2, null, globalSize, localSize, null, null);
        if (err != 0) {
            throw new RuntimeException("Failed to enqueue fft_rows kernel: " + err);
        }

        // Transpose
        clSetKernelArg(transposeKernel, 0, stack.pointers(dataBuffer));
        clSetKernelArg(transposeKernel, 1, stack.pointers(tempBuffer));
        clSetKernelArg(transposeKernel, 2, stack.ints(tileSize));
        clSetKernelArg(transposeKernel, 3, stack.ints(numTiles));
        enqueue3D(queue, transposeKernel, tileSize, tileSize, numTiles);

        // Column FFT (on transposed data)
        clSetKernelArg(fftRowsKernel, 0, stack.pointers(tempBuffer));
        globalSize = stack.pointers(tileSize, (long) numTiles * tileSize);
        localSize = stack.pointers(tileSize, 1);
        err = clEnqueueNDRangeKernel(queue, fftRowsKernel, 2, null, globalSize, localSize, null, null);
        if (err != 0) {
            throw new RuntimeException("Failed to enqueue fft_rows kernel (columns): " + err);
        }

        // Transpose back
        clSetKernelArg(transposeKernel, 0, stack.pointers(tempBuffer));
        clSetKernelArg(transposeKernel, 1, stack.pointers(dataBuffer));
        enqueue3D(queue, transposeKernel, tileSize, tileSize, numTiles);
    }

    private void enqueue3D(long queue, long kernel, int sizeX, int sizeY, int sizeZ) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var globalSize = stack.pointers(sizeX, sizeY, sizeZ);
            int err = clEnqueueNDRangeKernel(queue, kernel, 3, null, globalSize, null, null, null);
            if (err != 0) {
                throw new RuntimeException("Failed to enqueue 3D kernel: " + err);
            }
        }
    }

    private static float[] flatten3D(float[][][] data) {
        int n = data.length;
        int size = data[0].length;
        var flat = new float[n * size * size];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            for (int y = 0; y < size; y++) {
                System.arraycopy(data[i][y], 0, flat, idx, size);
                idx += size;
            }
        }
        return flat;
    }

    private static float[][] unflatten2D(float[] flat, int rows, int cols) {
        var result = new float[rows][cols];
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            System.arraycopy(flat, idx, result[i], 0, cols);
            idx += cols;
        }
        return result;
    }

    private static float[][] getOrCreateHannWindow(int size) {
        return HANN_WINDOW_CACHE.computeIfAbsent(size, PhaseCorrelation::createHannWindow);
    }

    private static float[][] createHannWindow(int size) {
        var window = new float[size][size];
        var weights1D = new float[size];
        var scale = 2 * Math.PI / (size - 1);
        for (var i = 0; i < size; i++) {
            weights1D[i] = 0.5f * (1 - (float) Math.cos(scale * i));
        }
        for (var y = 0; y < size; y++) {
            var wy = weights1D[y];
            for (var x = 0; x < size; x++) {
                window[y][x] = weights1D[x] * wy;
            }
        }
        return window;
    }

    private static float[][] applyWindowCopy(float[][] tile, float[][] window) {
        var size = tile.length;
        var result = new float[size][size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                result[y][x] = tile[y][x] * window[y][x];
            }
        }
        return result;
    }

    /**
     * Computes the phase correlation shift between two patches using FFT.
     *
     * @param patchRef the reference patch
     * @param patchDef the displaced patch
     * @return the displacement as (dy, dx)
     */
    public static DoublePair phaseCorrelationShiftFFT(float[][] patchRef, float[][] patchDef) {
        var size = patchRef.length;

        var window = getOrCreateHannWindow(size);
        var windowedRef = applyWindowCopy(patchRef, window);
        var windowedDef = applyWindowCopy(patchDef, window);

        var fftRef = FFTSupport.fft2Float(windowedRef);
        var fftDef = FFTSupport.fft2Float(windowedDef);

        var rows = fftRef.real.length;
        var cols = fftRef.real[0].length;
        var realResult = new float[rows][cols];
        var imagResult = new float[rows][cols];

        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var refR = fftRef.real[i][j];
                var refI = fftRef.imaginary[i][j];
                var defR = fftDef.real[i][j];
                var defI = fftDef.imaginary[i][j];

                var crossR = refR * defR + refI * defI;
                var crossI = refI * defR - refR * defI;

                var magSq = crossR * crossR + crossI * crossI;
                if (magSq > 1e-20f) {
                    var mag = (float) Math.sqrt(magSq);
                    realResult[i][j] = crossR / mag;
                    imagResult[i][j] = crossI / mag;
                }
            }
        }

        var crossCorr = fftShift(FFTSupport.ifft2Float(new FFTSupport.FloatFFT2DResult(realResult, imagResult)));

        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.real.length / 2d, crossCorr.real[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        var subpixelOffset = fitGaussian2D(crossCorr.real, maxIdx[0], maxIdx[1]);
        shifts[0] += subpixelOffset[0];
        shifts[1] += subpixelOffset[1];

        return new DoublePair(shifts[0], shifts[1]);
    }

    private static int[] findMaxIndex(FFTSupport.FloatFFT2DResult crossCorr) {
        var maxRow = 0;
        var maxCol = 0;
        var maxValue = Float.NEGATIVE_INFINITY;
        var centerRow = crossCorr.real.length / 2;
        var centerCol = crossCorr.real[0].length / 2;
        for (var y = 0; y < crossCorr.real.length; y++) {
            for (var x = 0; x < crossCorr.real[y].length; x++) {
                var value = crossCorr.real[y][x];
                if (value > maxValue) {
                    maxValue = value;
                    maxRow = y;
                    maxCol = x;
                } else if (value == maxValue) {
                    var dy = y - centerRow;
                    var dx = x - centerCol;
                    var currentDistSq = dy * dy + dx * dx;
                    var bestDy = maxRow - centerRow;
                    var bestDx = maxCol - centerCol;
                    var bestDistSq = bestDy * bestDy + bestDx * bestDx;
                    if (currentDistSq < bestDistSq) {
                        maxRow = y;
                        maxCol = x;
                    }
                }
            }
        }
        return new int[]{maxRow, maxCol};
    }

    private static FFTSupport.FloatFFT2DResult fftShift(FFTSupport.FloatFFT2DResult data) {
        var rows = data.real.length;
        var cols = data.real[0].length;
        var shiftedReal = new float[rows][cols];
        var shiftedImag = new float[rows][cols];
        var halfRows = rows / 2;
        var halfCols = cols / 2;
        for (var i = 0; i < rows; i++) {
            var newI = (i < halfRows) ? i + halfRows : i - halfRows;
            for (var j = 0; j < cols; j++) {
                var newJ = (j < halfCols) ? j + halfCols : j - halfCols;
                shiftedReal[newI][newJ] = data.real[i][j];
                shiftedImag[newI][newJ] = data.imaginary[i][j];
            }
        }
        return new FFTSupport.FloatFFT2DResult(shiftedReal, shiftedImag);
    }

    private static double[] fitGaussian2D(float[][] data, int centerX, int centerY) {
        int rows = data.length;
        int cols = data[0].length;

        int x0 = Math.max(1, centerX - 1);
        int x1 = Math.min(rows - 2, centerX + 1);
        int y0 = Math.max(1, centerY - 1);
        int y1 = Math.min(cols - 2, centerY + 1);

        if (x1 <= x0 || y1 <= y0) {
            return new double[]{0, 0};
        }

        double c = Math.max(data[centerX][centerY], 1e-10);
        double l = Math.max(data[centerX][y0], 1e-10);
        double r = Math.max(data[centerX][y1], 1e-10);
        double t = Math.max(data[x0][centerY], 1e-10);
        double b = Math.max(data[x1][centerY], 1e-10);

        double logC = Math.log(c);
        double logL = Math.log(l);
        double logR = Math.log(r);
        double logT = Math.log(t);
        double logB = Math.log(b);

        double denomX = 2 * logC - logT - logB;
        double denomY = 2 * logC - logL - logR;

        double offsetX = 0;
        double offsetY = 0;

        if (Math.abs(denomX) > 1e-10) {
            offsetX = 0.5 * (logT - logB) / denomX;
            offsetX = Math.max(-1, Math.min(1, offsetX));
        }
        if (Math.abs(denomY) > 1e-10) {
            offsetY = 0.5 * (logL - logR) / denomY;
            offsetY = Math.max(-1, Math.min(1, offsetY));
        }

        return new double[]{offsetX, offsetY};
    }
}
