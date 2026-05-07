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
import me.champeau.a4j.math.opencl.GpuOp;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;

/**
 * Batched correlation for tile-based image alignment.
 * <p>
 * This class provides both CPU and GPU implementations of correlation algorithms,
 * using the shared OpenCLContext for GPU operations to ensure proper locking.
 * The GPU implementation batches all tile correlations into a single GPU call,
 * which amortizes the GPU invocation overhead across thousands of tiles.
 * <p>
 * Supports both phase correlation (magnitude-normalized) and cross-correlation.
 */
public class CorrelationTools {
    private static final int MIN_TILES_FOR_GPU = 100;
    private static final Map<Integer, float[][]> HANN_WINDOW_CACHE = new ConcurrentHashMap<>();
    private static final CorrelationTools INSTANCE = new CorrelationTools();

    private CorrelationTools() {
    }

    /**
     * Returns the singleton instance.
     *
     * @return the phase correlation instance
     */
    public static CorrelationTools getInstance() {
        return INSTANCE;
    }

    /**
     * Compute correlation for multiple tile pairs.
     * Uses GPU if available and tile count is large enough, otherwise falls back to CPU.
     *
     * @param refTiles    Reference tiles [N][tileSize][tileSize]
     * @param targetTiles Target tiles [N][tileSize][tileSize]
     * @param normalize   if true, normalize by magnitude (phase correlation);
     *                    if false, skip normalization (cross-correlation)
     * @return Displacements [N][3] containing (dx, dy, confidence) per tile
     */
    float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles, boolean normalize) {
        if (refTiles.length == 0) {
            return new float[0][3];
        }
        int numTiles = refTiles.length;
        var context = OpenCLSupport.getContext();
        if (context != null && OpenCLSupport.isEnabled() && numTiles >= MIN_TILES_FOR_GPU) {
            try {
                return batchedCorrelationGPU(context, refTiles, targetTiles, normalize);
            } catch (Exception e) {
                context.recordError("Correlation.batchedCorrelation", e);
            }
        }
        return batchedCorrelationCPU(refTiles, targetTiles, normalize);
    }

    private float[][] batchedCorrelationCPU(float[][][] refTiles, float[][][] targetTiles, boolean normalize) {
        int n = refTiles.length;
        var results = new float[n][3];
        for (int i = 0; i < n; i++) {
            var shift = correlationShiftFFTWithConfidence(refTiles[i], targetTiles[i], normalize);
            results[i][0] = (float) -shift.dx();
            results[i][1] = (float) -shift.dy();
            results[i][2] = (float) shift.confidence();
        }
        return results;
    }

    private float[][] batchedCorrelationGPU(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, boolean normalize) {
        int tileSize = refTiles[0].length;
        long maxWorkGroupSize = context.getCapabilities().maxWorkGroupSize();

        if (isSupported(tileSize, maxWorkGroupSize, 32)) {
            return batchedCorrelationGPU32(context, refTiles, targetTiles, normalize);
        } else if (isSupported(tileSize, maxWorkGroupSize, 64, 128)) {
            return batchedCorrelationGPULarge(context, refTiles, targetTiles, tileSize, normalize);
        } else {
            return batchedCorrelationCPU(refTiles, targetTiles, normalize);
        }
    }

    private float[][] batchedCorrelationGPU32(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, boolean normalize) {
        int numTiles = refTiles.length;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        return context.runOp(op -> {
            var refBuffer = op.allocateBuffer(refFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
            var targetBuffer = op.allocateBuffer(targetFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(refBuffer, refFlat);
            op.write(targetBuffer, targetFlat);

            op.kernel("correlation", "batched_correlation_32")
                    .arg(refBuffer)
                    .arg(targetBuffer)
                    .arg(resultBuffer)
                    .arg(numTiles)
                    .arg(normalize ? 1 : 0)
                    .global((long) numTiles * 32, 32)
                    .local(32, 32)
                    .run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    private float[][] batchedCorrelationGPULarge(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, int tileSize, boolean normalize) {
        int numTiles = refTiles.length;
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        int complexSize = numTiles * tileElements * 2 * Float.BYTES;
        int realSize = numTiles * tileElements * Float.BYTES;

        return context.runOp(op -> {
            var refInputBuffer = op.allocateBuffer(realSize, CL_MEM_READ_ONLY);
            var targetInputBuffer = op.allocateBuffer(realSize, CL_MEM_READ_ONLY);
            var refComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var targetComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var tempBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var realBuffer = op.allocateBuffer(realSize, CL_MEM_READ_WRITE);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);

            op.write(refInputBuffer, refFlat);
            op.write(targetInputBuffer, targetFlat);

            // 1. Hann window for both ref and target
            applyHann(op, refInputBuffer, refComplexBuffer, tileSize, numTiles);
            applyHann(op, targetInputBuffer, targetComplexBuffer, tileSize, numTiles);

            // 2-3. Forward 2D FFT for reference and target
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);
            fft2D(op, targetComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);

            // 4. Cross-power spectrum
            op.kernel("correlation", "cross_power_spectrum")
                    .arg(refComplexBuffer)
                    .arg(targetComplexBuffer)
                    .arg(tileSize)
                    .arg(numTiles)
                    .arg(normalize ? 1 : 0)
                    .global(tileSize, tileSize, numTiles)
                    .run();

            // 5. Inverse 2D FFT
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, +1);

            // 6. Scale by 1/N^2
            op.kernel("correlation", "scale_ifft")
                    .arg(refComplexBuffer)
                    .arg(tileSize)
                    .arg(numTiles)
                    .global(tileSize, tileSize, numTiles)
                    .run();

            // 7. FFT shift + extract real part
            op.kernel("correlation", "fft_shift_real")
                    .arg(refComplexBuffer)
                    .arg(realBuffer)
                    .arg(tileSize)
                    .arg(numTiles)
                    .global(tileSize, tileSize, numTiles)
                    .run();

            // 8. Find peaks
            op.kernel("correlation", "find_peaks")
                    .arg(realBuffer)
                    .arg(resultBuffer)
                    .arg(tileSize)
                    .arg(numTiles)
                    .global(numTiles * 256L)
                    .local(256)
                    .run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    private static void applyHann(GpuOp op, long inputBuffer, long outputComplex, int tileSize, int numTiles) {
        op.kernel("correlation", "apply_hann_window")
                .arg(inputBuffer)
                .arg(outputComplex)
                .arg(tileSize)
                .arg(numTiles)
                .global(tileSize, tileSize, numTiles)
                .run();
    }

    private static float[][] unpackGpuResults(float[] gpuResults, int numTiles) {
        var results = new float[numTiles][3];
        for (int i = 0; i < numTiles; i++) {
            int offset = i * 3;
            results[i][0] = gpuResults[offset];
            results[i][1] = gpuResults[offset + 1];
            results[i][2] = gpuResults[offset + 2];
        }
        return results;
    }

    private static void fft2D(GpuOp op,
                              long dataBuffer,
                              long tempBuffer,
                              int tileSize,
                              int logTileSize,
                              int numTiles,
                              int direction) {
        enqueueFFTRows(op, dataBuffer, tileSize, logTileSize, numTiles, direction);
        enqueueTranspose(op, dataBuffer, tempBuffer, tileSize, numTiles);
        enqueueFFTRows(op, tempBuffer, tileSize, logTileSize, numTiles, direction);
        enqueueTranspose(op, tempBuffer, dataBuffer, tileSize, numTiles);
    }

    private static void enqueueFFTRows(GpuOp op, long buffer, int tileSize, int logTileSize, int numTiles, int direction) {
        op.kernel("correlation", "fft_rows")
                .arg(buffer)
                .arg(tileSize)
                .arg(logTileSize)
                .arg(direction)
                .global(tileSize, (long) numTiles * tileSize)
                .local(tileSize, 1)
                .run();
    }

    private static final int TRANSPOSE_BLOCK_SIZE = 16;

    private static void enqueueTranspose(GpuOp op, long src, long dst, int tileSize, int numTiles) {
        // Round up global size to multiple of block size for local memory blocking
        int globalXY = ((tileSize + TRANSPOSE_BLOCK_SIZE - 1) / TRANSPOSE_BLOCK_SIZE) * TRANSPOSE_BLOCK_SIZE;
        op.kernel("correlation", "transpose")
                .arg(src)
                .arg(dst)
                .arg(tileSize)
                .arg(numTiles)
                .global(globalXY, globalXY, numTiles)
                .local(TRANSPOSE_BLOCK_SIZE, TRANSPOSE_BLOCK_SIZE, 1)
                .run();
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

    private static float[][] getOrCreateHannWindow(int size) {
        return HANN_WINDOW_CACHE.computeIfAbsent(size, CorrelationTools::createHannWindow);
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
     * Result of phase correlation including displacement and confidence.
     *
     * @param dy         vertical displacement
     * @param dx         horizontal displacement
     * @param confidence confidence in the result (0-1), based on peak sharpness
     */
    public record ShiftResult(double dy, double dx, double confidence) {
    }

    /**
     * Compute NCC (Normalized Cross-Correlation) for multiple tile pairs.
     * Uses GPU if available and tile count is large enough, otherwise falls back to CPU.
     * <p>
     * NCC normalizes by local variance, making confidence values directly comparable
     * and invariant to brightness/contrast changes. Peak values are in [-1, 1].
     *
     * @param refTiles    Reference tiles [N][tileSize][tileSize]
     * @param targetTiles Target tiles [N][tileSize][tileSize]
     * @return Displacements [N][3] containing (dx, dy, confidence) per tile
     */
    float[][] batchedNCC(float[][][] refTiles, float[][][] targetTiles) {
        if (refTiles.length == 0) {
            return new float[0][3];
        }
        int numTiles = refTiles.length;
        var context = OpenCLSupport.getContext();
        if (context != null && OpenCLSupport.isEnabled() && numTiles >= MIN_TILES_FOR_GPU) {
            try {
                return batchedNCCGPU(context, refTiles, targetTiles);
            } catch (Exception e) {
                context.recordError("Correlation.batchedNCC", e);
            }
        }
        return batchedNCCCPU(refTiles, targetTiles);
    }

    private float[][] batchedNCCCPU(float[][][] refTiles, float[][][] targetTiles) {
        int n = refTiles.length;
        var results = new float[n][3];
        for (int i = 0; i < n; i++) {
            var shift = nccShiftWithConfidence(refTiles[i], targetTiles[i]);
            results[i][0] = (float) -shift.dx();
            results[i][1] = (float) -shift.dy();
            results[i][2] = (float) shift.confidence();
        }
        return results;
    }

    private float[][] batchedNCCGPU(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles) {
        int tileSize = refTiles[0].length;
        long maxWorkGroupSize = context.getCapabilities().maxWorkGroupSize();

        if (isSupported(tileSize, maxWorkGroupSize, 32)) {
            return batchedNCCGPU32(context, refTiles, targetTiles);
        } else if (isSupported(tileSize, maxWorkGroupSize, 64, 128)) {
            return batchedNCCGPULarge(context, refTiles, targetTiles, tileSize);
        } else {
            return batchedNCCCPU(refTiles, targetTiles);
        }
    }

    private float[][] batchedNCCGPU32(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles) {
        int numTiles = refTiles.length;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        return context.runOp(op -> {
            var refBuffer = op.allocateBuffer(refFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
            var targetBuffer = op.allocateBuffer(targetFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(refBuffer, refFlat);
            op.write(targetBuffer, targetFlat);

            op.kernel("correlation", "batched_ncc_32")
                    .arg(refBuffer).arg(targetBuffer).arg(resultBuffer).arg(numTiles)
                    .global((long) numTiles * 32, 32)
                    .local(32, 32)
                    .run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    private float[][] batchedNCCGPULarge(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, int tileSize) {
        int numTiles = refTiles.length;
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        int complexSize = numTiles * tileElements * 2 * Float.BYTES;
        int realSize = numTiles * tileElements * Float.BYTES;
        int statsSize = numTiles * 3 * Float.BYTES;  // normFactor, meanRef, meanTarget per tile

        return context.runOp(op -> {
            var refInputBuffer = op.allocateBuffer(realSize, CL_MEM_READ_ONLY);
            var targetInputBuffer = op.allocateBuffer(realSize, CL_MEM_READ_ONLY);
            var refComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var targetComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var tempBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var realBuffer = op.allocateBuffer(realSize, CL_MEM_READ_WRITE);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            var statsBuffer = op.allocateBuffer(statsSize, CL_MEM_READ_WRITE);

            op.write(refInputBuffer, refFlat);
            op.write(targetInputBuffer, targetFlat);

            // 1. Compute per-tile statistics
            op.kernel("correlation", "compute_tile_stats")
                    .arg(refInputBuffer).arg(targetInputBuffer).arg(statsBuffer)
                    .arg(tileSize).arg(numTiles)
                    .global(numTiles * 256L).local(256)
                    .run();

            // 2. Zero-mean + Hann window for ref and target
            op.kernel("correlation", "zero_mean_and_hann")
                    .arg(refInputBuffer).arg(refComplexBuffer).arg(statsBuffer)
                    .arg(tileSize).arg(numTiles).arg(0)
                    .global(tileSize, tileSize, numTiles).run();
            op.kernel("correlation", "zero_mean_and_hann")
                    .arg(targetInputBuffer).arg(targetComplexBuffer).arg(statsBuffer)
                    .arg(tileSize).arg(numTiles).arg(1)
                    .global(tileSize, tileSize, numTiles).run();

            // 3-4. Forward 2D FFT for ref and target
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);
            fft2D(op, targetComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);

            // 5. Cross-power spectrum (no normalization for NCC)
            op.kernel("correlation", "cross_power_spectrum")
                    .arg(refComplexBuffer).arg(targetComplexBuffer)
                    .arg(tileSize).arg(numTiles).arg(0)
                    .global(tileSize, tileSize, numTiles).run();

            // 6. Inverse 2D FFT
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, +1);

            // 7. Scale by 1/N^2
            op.kernel("correlation", "scale_ifft")
                    .arg(refComplexBuffer).arg(tileSize).arg(numTiles)
                    .global(tileSize, tileSize, numTiles).run();

            // 8. FFT shift + extract real part
            op.kernel("correlation", "fft_shift_real")
                    .arg(refComplexBuffer).arg(realBuffer).arg(tileSize).arg(numTiles)
                    .global(tileSize, tileSize, numTiles).run();

            // 9. Find peaks with NCC normalization
            op.kernel("correlation", "find_peaks_ncc")
                    .arg(realBuffer).arg(statsBuffer).arg(resultBuffer)
                    .arg(tileSize).arg(numTiles)
                    .global(numTiles * 256L).local(256).run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    /**
     * Computes NCC shift between two patches using FFT.
     * <p>
     * NCC formula: NCC = Σ[(f-f̄)(t-t̄)] / sqrt(Σ(f-f̄)² × Σ(t-t̄)²)
     * <p>
     * The peak value is in [-1, 1] and can be used directly as confidence.
     *
     * @param patchRef the reference patch
     * @param patchDef the displaced patch
     * @return the displacement with confidence (peak NCC value)
     */
    static ShiftResult nccShiftWithConfidence(float[][] patchRef, float[][] patchDef) {
        var size = patchRef.length;

        // 1. Compute means
        double sumRef = 0, sumDef = 0;
        int count = size * size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sumRef += patchRef[y][x];
                sumDef += patchDef[y][x];
            }
        }
        float meanRef = (float) (sumRef / count);
        float meanDef = (float) (sumDef / count);

        var zeroMeanRef = new float[size][size];
        var zeroMeanDef = new float[size][size];
        double sumSqRef = 0, sumSqDef = 0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float r = patchRef[y][x] - meanRef;
                float d = patchDef[y][x] - meanDef;
                zeroMeanRef[y][x] = r;
                zeroMeanDef[y][x] = d;
                sumSqRef += r * r;
                sumSqDef += d * d;
            }
        }

        double normFactor = Math.sqrt(sumSqRef * sumSqDef);
        if (normFactor < 1e-10) {
            return new ShiftResult(0, 0, 0);
        }

        var window = getOrCreateHannWindow(size);
        var windowedRef = applyWindowCopy(zeroMeanRef, window);
        var windowedDef = applyWindowCopy(zeroMeanDef, window);

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

                realResult[i][j] = refR * defR + refI * defI;
                imagResult[i][j] = refI * defR - refR * defI;
            }
        }
        var crossCorr = fftShift(FFTSupport.ifft2Float(new FFTSupport.FloatFFT2DResult(realResult, imagResult)));

        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.real.length / 2d, crossCorr.real[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        var subpixelOffset = fitGaussian2D(crossCorr.real, maxIdx[0], maxIdx[1]);
        shifts[0] += subpixelOffset[0];
        shifts[1] += subpixelOffset[1];

        double peakValue = crossCorr.real[maxIdx[0]][maxIdx[1]];
        double nccValue = peakValue / normFactor;
        double confidence = Math.max(0, Math.min(1, nccValue));

        return new ShiftResult(shifts[0], shifts[1], confidence);
    }

    /**
     * Computes the correlation shift between two patches using FFT.
     *
     * @param patchRef  the reference patch
     * @param patchDef  the displaced patch
     * @param normalize if true, normalize by magnitude (phase correlation);
     *                  if false, skip normalization (cross-correlation)
     * @return the displacement with confidence
     */
    static ShiftResult correlationShiftFFTWithConfidence(float[][] patchRef, float[][] patchDef, boolean normalize) {
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

                if (normalize) {
                    var magSq = crossR * crossR + crossI * crossI;
                    if (magSq > 1e-20f) {
                        var mag = (float) Math.sqrt(magSq);
                        realResult[i][j] = crossR / mag;
                        imagResult[i][j] = crossI / mag;
                    }
                } else {
                    realResult[i][j] = crossR;
                    imagResult[i][j] = crossI;
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

        var confidence = computePeakConfidence(crossCorr.real, maxIdx[0], maxIdx[1]);

        return new ShiftResult(shifts[0], shifts[1], confidence);
    }

    /**
     * Computes confidence based on peak sharpness.
     * Uses the ratio of peak value to the mean of the correlation surface,
     * normalized to 0-1 range.
     */
    private static double computePeakConfidence(float[][] crossCorr, int peakRow, int peakCol) {
        int rows = crossCorr.length;
        int cols = crossCorr[0].length;

        float peakValue = crossCorr[peakRow][peakCol];

        // Compute mean and second highest value
        double sum = 0;
        float secondMax = Float.NEGATIVE_INFINITY;
        int count = 0;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float val = crossCorr[y][x];
                sum += val;
                count++;

                // Track second highest (excluding 3x3 around peak)
                if (Math.abs(y - peakRow) > 1 || Math.abs(x - peakCol) > 1) {
                    secondMax = Math.max(secondMax, val);
                }
            }
        }

        double mean = sum / count;

        // Peak-to-sidelobe ratio: how much higher is peak than second highest
        // This is more robust than peak-to-mean for detecting sharp peaks
        double psr = (peakValue - mean) / (secondMax - mean + 1e-10);

        // Normalize to 0-1 range using sigmoid-like function
        // PSR of ~2 gives confidence ~0.5, PSR of ~5+ gives confidence ~0.9+
        double confidence = 1.0 - 1.0 / (1.0 + psr * 0.5);

        return Math.max(0, Math.min(1, confidence));
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

    /**
     * Performs NCC correlation directly from GPU-resident images.
     * <p>
     * This method eliminates tile transfer overhead by extracting tiles directly
     * from images that are already on the GPU. This is significantly faster when
     * processing multiple image pairs, as images only need to be uploaded once.
     *
     * @param context           the OpenCL context
     * @param refImageBuffer    GPU buffer handle for reference image
     * @param targetImageBuffer GPU buffer handle for target image
     * @param imageWidth        image width in pixels
     * @param imageHeight       image height in pixels
     * @param tilePositions     tile center positions as [N][2] array of (x, y) pairs
     * @return displacements [N][3] containing (dx, dy, confidence) per tile
     */
    public float[][] correlateResidentImagesNCC32(OpenCLContext context,
                                                  long refImageBuffer,
                                                  long targetImageBuffer,
                                                  int imageWidth,
                                                  int imageHeight,
                                                  int[][] tilePositions) {
        int numTiles = tilePositions.length;
        if (numTiles == 0) {
            return new float[0][3];
        }

        // Flatten positions to [x0, y0, x1, y1, ...]
        var positionsFlat = new int[numTiles * 2];
        for (int i = 0; i < numTiles; i++) {
            positionsFlat[i * 2] = tilePositions[i][0];
            positionsFlat[i * 2 + 1] = tilePositions[i][1];
        }

        var gpuResults = new float[numTiles * 3];

        return context.runOp(op -> {
            var positionsBuffer = op.allocateBuffer(positionsFlat.length * Integer.BYTES, CL_MEM_READ_ONLY);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.writeInts(positionsBuffer, positionsFlat);

            op.kernel("correlation", "correlate_resident_ncc_32")
                    .arg(refImageBuffer).arg(targetImageBuffer)
                    .arg(imageWidth).arg(imageHeight)
                    .arg(positionsBuffer).arg(resultBuffer).arg(numTiles)
                    .global((long) numTiles * 32, 32)
                    .local(32, 32)
                    .run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    /**
     * Performs NCC correlation directly from GPU-resident images for 64x64 or 128x128 tiles.
     * <p>
     * This method extracts tiles directly from GPU-resident images, eliminating
     * the overhead of CPU-GPU tile transfers. It uses the same FFT pipeline as
     * the regular batch NCC method.
     *
     * @param context           the OpenCL context
     * @param refImageBuffer    GPU buffer handle for reference image
     * @param targetImageBuffer GPU buffer handle for target image
     * @param imageWidth        image width in pixels
     * @param imageHeight       image height in pixels
     * @param tilePositions     tile center positions as [N][2] array of (x, y) pairs
     * @param tileSize          tile size (64 or 128)
     * @return displacements [N][3] containing (dx, dy, confidence) per tile
     */
    public float[][] correlateResidentImagesNCCLarge(OpenCLContext context,
                                                     long refImageBuffer,
                                                     long targetImageBuffer,
                                                     int imageWidth,
                                                     int imageHeight,
                                                     int[][] tilePositions,
                                                     int tileSize) {
        int numTiles = tilePositions.length;
        if (numTiles == 0) {
            return new float[0][3];
        }

        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        // Flatten positions to [x0, y0, x1, y1, ...]
        var positionsFlat = new int[numTiles * 2];
        for (int i = 0; i < numTiles; i++) {
            positionsFlat[i * 2] = tilePositions[i][0];
            positionsFlat[i * 2 + 1] = tilePositions[i][1];
        }

        var gpuResults = new float[numTiles * 3];

        int complexSize = numTiles * tileElements * 2 * Float.BYTES;
        int realSize = numTiles * tileElements * Float.BYTES;
        int statsSize = numTiles * 3 * Float.BYTES;
        int meansSize = numTiles * Float.BYTES;

        return context.runOp(op -> {
            var positionsBuffer = op.allocateBuffer(positionsFlat.length * Integer.BYTES, CL_MEM_READ_ONLY);
            var refMeansBuffer = op.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            var targetMeansBuffer = op.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            var refSumSqBuffer = op.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            var targetSumSqBuffer = op.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            var refComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var targetComplexBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var tempBuffer = op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            var realBuffer = op.allocateBuffer(realSize, CL_MEM_READ_WRITE);
            var resultBuffer = op.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            var statsBuffer = op.allocateBuffer(statsSize, CL_MEM_READ_WRITE);

            op.writeInts(positionsBuffer, positionsFlat);

            // 1. Per-tile stats for ref image
            op.kernel("correlation", "compute_tile_stats_from_images")
                    .arg(refImageBuffer).arg(imageWidth).arg(imageHeight).arg(positionsBuffer)
                    .arg(refMeansBuffer).arg(refSumSqBuffer).arg(tileSize).arg(numTiles)
                    .global(256, numTiles).local(256, 1).run();

            // 2. Per-tile stats for target image
            op.kernel("correlation", "compute_tile_stats_from_images")
                    .arg(targetImageBuffer).arg(imageWidth).arg(imageHeight).arg(positionsBuffer)
                    .arg(targetMeansBuffer).arg(targetSumSqBuffer).arg(tileSize).arg(numTiles)
                    .global(256, numTiles).local(256, 1).run();

            // 3. Extract tiles with zero-mean and Hann window
            op.kernel("correlation", "extract_tiles_zero_mean")
                    .arg(refImageBuffer).arg(targetImageBuffer)
                    .arg(imageWidth).arg(imageHeight).arg(positionsBuffer)
                    .arg(refMeansBuffer).arg(targetMeansBuffer)
                    .arg(refComplexBuffer).arg(targetComplexBuffer)
                    .arg(tileSize).arg(numTiles)
                    .global(tileSize, tileSize, numTiles).run();

            // 4. NCC normalization factors
            op.kernel("correlation", "compute_ncc_norm_factors")
                    .arg(refSumSqBuffer).arg(targetSumSqBuffer).arg(statsBuffer).arg(numTiles)
                    .global(numTiles).run();

            // 5-6. Forward 2D FFT for ref and target
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);
            fft2D(op, targetComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, -1);

            // 7. Cross-power spectrum (no normalization for NCC)
            op.kernel("correlation", "cross_power_spectrum")
                    .arg(refComplexBuffer).arg(targetComplexBuffer)
                    .arg(tileSize).arg(numTiles).arg(0)
                    .global(tileSize, tileSize, numTiles).run();

            // 8. Inverse 2D FFT
            fft2D(op, refComplexBuffer, tempBuffer, tileSize, logTileSize, numTiles, +1);

            // 9. Scale by 1/N^2
            op.kernel("correlation", "scale_ifft")
                    .arg(refComplexBuffer).arg(tileSize).arg(numTiles)
                    .global(tileSize, tileSize, numTiles).run();

            // 10. FFT shift + extract real part
            op.kernel("correlation", "fft_shift_real")
                    .arg(refComplexBuffer).arg(realBuffer).arg(tileSize).arg(numTiles)
                    .global(tileSize, tileSize, numTiles).run();

            // 11. Find peaks with NCC normalization
            op.kernel("correlation", "find_peaks_ncc")
                    .arg(realBuffer).arg(statsBuffer).arg(resultBuffer)
                    .arg(tileSize).arg(numTiles)
                    .global(numTiles * 256L).local(256).run();

            op.read(resultBuffer, gpuResults);
            return unpackGpuResults(gpuResults, numTiles);
        });
    }

    /**
     * Computes the 2D phase correlation between two square patches.
     * Returns the displacement (dy, dx) of the target relative to
     * the reference, with sub-pixel accuracy via Gaussian fitting.
     *
     * @param patchRef  reference patch [size][size]
     * @param patchDef  target patch [size][size]
     * @param normalize if true, use phase correlation (magnitude-normalized);
     *                  if false, use cross-correlation
     * @return the displacement with confidence
     */
    public static ShiftResult phaseCorrelation2D(float[][] patchRef, float[][] patchDef, boolean normalize) {
        return correlationShiftFFTWithConfidence(patchRef, patchDef, normalize);
    }

    /**
     * Computes the sub-pixel cross-correlation shift between two 1D signals.
     * Uses mean-subtracted cross-correlation with parabolic sub-pixel interpolation.
     * <p>
     * The shift is measured as the displacement of the target signal relative to the
     * reference signal. A positive shift means the target is shifted to the right.
     * The confidence is the peak normalized cross-correlation value, clamped to [0,1].
     *
     * @param reference the reference signal
     * @param target    the target signal (must have the same length as reference)
     * @return the sub-pixel shift (in dx field) and confidence in [0,1]
     */
    public static ShiftResult crossCorrelation1D(float[] reference, float[] target) {
        return crossCorrelation1D(reference, target, reference.length / 4);
    }

    public static ShiftResult crossCorrelation1D(float[] reference, float[] target, int maxLag) {
        int n = reference.length;
        if (n != target.length) {
            throw new IllegalArgumentException("Signals must have the same length");
        }
        if (n < 4) {
            return new ShiftResult(0, 0, 0);
        }

        double meanRef = 0;
        double meanTarget = 0;
        for (int i = 0; i < n; i++) {
            meanRef += reference[i];
            meanTarget += target[i];
        }
        meanRef /= n;
        meanTarget /= n;

        double sumSqRef = 0;
        double sumSqTarget = 0;
        for (int i = 0; i < n; i++) {
            double r = reference[i] - meanRef;
            double t = target[i] - meanTarget;
            sumSqRef += r * r;
            sumSqTarget += t * t;
        }
        double globalNorm = Math.sqrt(sumSqRef * sumSqTarget);
        if (globalNorm < 1e-10) {
            return new ShiftResult(0, 0, 0);
        }

        maxLag = Math.min(maxLag, n / 4);
        var corr = new double[2 * maxLag + 1];

        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double sum = 0;
            int start = Math.max(0, -lag);
            int end = Math.min(n, n - lag);
            for (int i = start; i < end; i++) {
                sum += (reference[i] - meanRef) * (target[i + lag] - meanTarget);
            }
            corr[lag + maxLag] = sum / globalNorm;
        }

        int bestIdx = 0;
        double maxCorr = corr[0];
        for (int i = 1; i < corr.length; i++) {
            if (corr[i] > maxCorr) {
                maxCorr = corr[i];
                bestIdx = i;
            }
        }

        double subPixelOffset = 0;
        if (bestIdx > 0 && bestIdx < corr.length - 1) {
            double left = corr[bestIdx - 1];
            double center = corr[bestIdx];
            double right = corr[bestIdx + 1];
            double denom = 2 * center - left - right;
            if (Math.abs(denom) > 1e-10) {
                subPixelOffset = 0.5 * (left - right) / denom;
                subPixelOffset = Math.max(-1, Math.min(1, subPixelOffset));
            }
        }

        double shift = (bestIdx - maxLag) + subPixelOffset;
        double confidence = Math.max(0, Math.min(1, maxCorr));
        return new ShiftResult(0, shift, confidence);
    }

    /**
     * Checks if GPU-resident correlation is supported for the given tile size.
     * <p>
     * This checks both whether the tile size is supported algorithmically and whether
     * the device has sufficient resources (e.g., work group size) to execute the kernel.
     *
     * @param context  the OpenCL context
     * @param tileSize tile size to check (32, 64, or 128)
     * @return true if GPU-resident correlation is supported for this tile size
     */
    public boolean isGpuResidentCorrelationSupported(OpenCLContext context, int tileSize) {
        if (tileSize != 32 && tileSize != 64 && tileSize != 128) {
            return false;
        }
        return context.getCapabilities().maxWorkGroupSize() >= requiredWorkGroupSize(tileSize);
    }

    private static long requiredWorkGroupSize(int tileSize) {
        return switch (tileSize) {
            // 32x32 tiles use 32x32 work groups = 1024 work items
            case 32 -> 1024;
            // 64/128 tiles use smaller work groups (256 for statistics passes)
            case 64, 128 -> 256;
            default -> throw new IllegalArgumentException("Unsupported tile size: " + tileSize);
        };
    }

    private static boolean isSupported(int tileSize, long maxWorkGroupSize, int... supportedTileSizes) {
        for (int supported : supportedTileSizes) {
            if (tileSize == supported) {
                return maxWorkGroupSize >= requiredWorkGroupSize(tileSize);
            }
        }
        return false;
    }

    /**
     * Performs NCC correlation directly from GPU-resident images.
     * Automatically selects the appropriate implementation based on tile size.
     *
     * @param context           the OpenCL context
     * @param refImageBuffer    GPU buffer handle for reference image
     * @param targetImageBuffer GPU buffer handle for target image
     * @param imageWidth        image width in pixels
     * @param imageHeight       image height in pixels
     * @param tilePositions     tile center positions as [N][2] array of (x, y) pairs
     * @param tileSize          tile size (32, 64, or 128)
     * @return displacements [N][3] containing (dx, dy, confidence) per tile
     * @throws UnsupportedOperationException if the device does not support the required work group size
     */
    public float[][] correlateResidentImagesNCC(OpenCLContext context,
                                                long refImageBuffer,
                                                long targetImageBuffer,
                                                int imageWidth,
                                                int imageHeight,
                                                int[][] tilePositions,
                                                int tileSize) {
        if (!isGpuResidentCorrelationSupported(context, tileSize)) {
            long maxWorkGroupSize = context.getCapabilities().maxWorkGroupSize();
            long required = requiredWorkGroupSize(tileSize);
            throw new UnsupportedOperationException(
                    String.format("GPU does not support tile size %d: requires work group size %d but device supports max %d",
                            tileSize, required, maxWorkGroupSize));
        }
        if (tileSize == 32) {
            return correlateResidentImagesNCC32(context, refImageBuffer, targetImageBuffer,
                    imageWidth, imageHeight, tilePositions);
        } else {
            return correlateResidentImagesNCCLarge(context, refImageBuffer, targetImageBuffer,
                    imageWidth, imageHeight, tilePositions, tileSize);
        }
    }
}
