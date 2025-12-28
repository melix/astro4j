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
import org.lwjgl.system.MemoryStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opencl.CL10.*;

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
                System.err.println("[Correlation] GPU failed, falling back to CPU | " + context.getMemoryStats());
                e.printStackTrace();
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

        // Check if device supports required work group sizes before attempting GPU path
        if (tileSize == 32 && maxWorkGroupSize >= 1024) {
            return batchedCorrelationGPU32(context, refTiles, targetTiles, normalize);
        } else if ((tileSize == 64 || tileSize == 128) && maxWorkGroupSize >= 256) {
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

        return context.executeWithLock(() -> {
            long refBuffer = 0;
            long targetBuffer = 0;
            long resultBuffer = 0;
            try {
                refBuffer = context.allocateBuffer(refFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                targetBuffer = context.allocateBuffer(targetFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(refBuffer, refFlat);
                context.writeBuffer(targetBuffer, targetFlat);

                var kernel = context.getKernelManager().getKernel("correlation", "batched_correlation_32");

                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(kernel, 0, stack.pointers(refBuffer));
                    clSetKernelArg(kernel, 1, stack.pointers(targetBuffer));
                    clSetKernelArg(kernel, 2, stack.pointers(resultBuffer));
                    clSetKernelArg(kernel, 3, stack.ints(numTiles));
                    clSetKernelArg(kernel, 4, stack.ints(normalize ? 1 : 0));

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
                context.readBufferNoSync(resultBuffer, gpuResults);

                return unpackGpuResults(gpuResults, numTiles);
            } finally {
                safeRelease(context, refBuffer, targetBuffer, resultBuffer);
            }
        });
    }

    private float[][] batchedCorrelationGPULarge(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, int tileSize, boolean normalize) {
        int numTiles = refTiles.length;
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

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
                resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(refInputBuffer, refFlat);
                context.writeBuffer(targetInputBuffer, targetFlat);

                var km = context.getKernelManager();
                var applyHannKernel = km.getKernel("correlation", "apply_hann_window");
                var fftRowsKernel = km.getKernel("correlation", "fft_rows");
                var transposeKernel = km.getKernel("correlation", "transpose");
                var crossPowerKernel = km.getKernel("correlation", "cross_power_spectrum");
                var scaleIfftKernel = km.getKernel("correlation", "scale_ifft");
                var fftShiftKernel = km.getKernel("correlation", "fft_shift_real");
                var findPeaksKernel = km.getKernel("correlation", "find_peaks");

                try (var stack = MemoryStack.stackPush()) {
                    var queue = context.getCommandQueue();

                    // 1. Apply Hann window to both ref and target
                    clSetKernelArg(applyHannKernel, 0, stack.pointers(refInputBuffer));
                    clSetKernelArg(applyHannKernel, 1, stack.pointers(refComplexBuffer));
                    clSetKernelArg(applyHannKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(applyHannKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, applyHannKernel, tileSize, tileSize, numTiles, stack);

                    clSetKernelArg(applyHannKernel, 0, stack.pointers(targetInputBuffer));
                    clSetKernelArg(applyHannKernel, 1, stack.pointers(targetComplexBuffer));
                    enqueue3D(queue, applyHannKernel, tileSize, tileSize, numTiles, stack);

                    // 2. Forward 2D FFT for reference: rows -> transpose -> rows -> transpose
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 3. Forward 2D FFT for target
                    fft2D(queue, fftRowsKernel, transposeKernel, targetComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 4. Cross-power spectrum (with optional normalization)
                    clSetKernelArg(crossPowerKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 1, stack.pointers(targetComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(crossPowerKernel, 3, stack.ints(numTiles));
                    clSetKernelArg(crossPowerKernel, 4, stack.ints(normalize ? 1 : 0));
                    enqueue3D(queue, crossPowerKernel, tileSize, tileSize, numTiles, stack);

                    // 5. Inverse 2D FFT
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, +1, stack);

                    // 6. Scale by 1/N^2
                    clSetKernelArg(scaleIfftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(scaleIfftKernel, 1, stack.ints(tileSize));
                    clSetKernelArg(scaleIfftKernel, 2, stack.ints(numTiles));
                    enqueue3D(queue, scaleIfftKernel, tileSize, tileSize, numTiles, stack);

                    // 7. FFT shift and extract real part
                    clSetKernelArg(fftShiftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(fftShiftKernel, 1, stack.pointers(realBuffer));
                    clSetKernelArg(fftShiftKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(fftShiftKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, fftShiftKernel, tileSize, tileSize, numTiles, stack);

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
                context.readBufferNoSync(resultBuffer, gpuResults);

                return unpackGpuResults(gpuResults, numTiles);
            } finally {
                safeRelease(context, refInputBuffer, targetInputBuffer, refComplexBuffer,
                        targetComplexBuffer, tempBuffer, realBuffer, resultBuffer);
            }
        });
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

    private static void safeRelease(OpenCLContext context, long... buffers) {
        for (long buffer : buffers) {
            if (buffer != 0) {
                try {
                    context.releaseBuffer(buffer);
                } catch (Exception e) {
                    System.err.println("[PhaseCorrelation] Failed to release GPU buffer");
                    e.printStackTrace();
                }
            }
        }
    }

    private void fft2D(long queue,
                       long fftRowsKernel,
                       long transposeKernel,
                       long dataBuffer,
                       long tempBuffer,
                       int tileSize,
                       int logTileSize,
                       int numTiles,
                       int direction,
                       MemoryStack stack) {
        enqueueFFTRows(queue, fftRowsKernel, dataBuffer, tileSize, logTileSize, numTiles, direction, stack);
        enqueueTranspose(queue, transposeKernel, dataBuffer, tempBuffer, tileSize, numTiles, stack);
        enqueueFFTRows(queue, fftRowsKernel, tempBuffer, tileSize, logTileSize, numTiles, direction, stack);
        enqueueTranspose(queue, transposeKernel, tempBuffer, dataBuffer, tileSize, numTiles, stack);
    }

    private static void enqueueFFTRows(long queue,
                                       long kernel,
                                       long buffer,
                                       int tileSize,
                                       int logTileSize,
                                       int numTiles,
                                       int direction,
                                       MemoryStack stack) {
        clSetKernelArg(kernel, 0, stack.pointers(buffer));
        clSetKernelArg(kernel, 1, stack.ints(tileSize));
        clSetKernelArg(kernel, 2, stack.ints(logTileSize));
        clSetKernelArg(kernel, 3, stack.ints(direction));

        var globalSize = stack.pointers(tileSize, (long) numTiles * tileSize);
        var localSize = stack.pointers(tileSize, 1);
        int err = clEnqueueNDRangeKernel(queue, kernel, 2, null, globalSize, localSize, null, null);
        if (err != 0) {
            throw new RuntimeException("Failed to enqueue fft_rows kernel: " + err);
        }
    }

    private static final int TRANSPOSE_BLOCK_SIZE = 16;

    private static void enqueueTranspose(long queue,
                                         long kernel,
                                         long src,
                                         long dst,
                                         int tileSize,
                                         int numTiles,
                                         MemoryStack stack) {
        clSetKernelArg(kernel, 0, stack.pointers(src));
        clSetKernelArg(kernel, 1, stack.pointers(dst));
        clSetKernelArg(kernel, 2, stack.ints(tileSize));
        clSetKernelArg(kernel, 3, stack.ints(numTiles));

        // Round up global size to multiple of block size for local memory blocking
        int globalX = ((tileSize + TRANSPOSE_BLOCK_SIZE - 1) / TRANSPOSE_BLOCK_SIZE) * TRANSPOSE_BLOCK_SIZE;
        int globalY = ((tileSize + TRANSPOSE_BLOCK_SIZE - 1) / TRANSPOSE_BLOCK_SIZE) * TRANSPOSE_BLOCK_SIZE;

        var globalSize = stack.pointers(globalX, globalY, numTiles);
        var localSize = stack.pointers(TRANSPOSE_BLOCK_SIZE, TRANSPOSE_BLOCK_SIZE, 1);
        int err = clEnqueueNDRangeKernel(queue, kernel, 3, null, globalSize, localSize, null, null);
        if (err != 0) {
            throw new RuntimeException("Failed to enqueue transpose kernel: " + err);
        }
    }

    private static void enqueue3D(long queue,
                                  long kernel,
                                  int sizeX,
                                  int sizeY,
                                  int sizeZ,
                                  MemoryStack stack) {
        var globalSize = stack.pointers(sizeX, sizeY, sizeZ);
        int err = clEnqueueNDRangeKernel(queue, kernel, 3, null, globalSize, null, null, null);
        if (err != 0) {
            throw new RuntimeException("Failed to enqueue 3D kernel: " + err);
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
                System.err.println("[Correlation] GPU NCC failed, falling back to CPU | " + context.getMemoryStats());
                e.printStackTrace();
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

        if (tileSize == 64 || tileSize == 128) {
            return batchedNCCGPULarge(context, refTiles, targetTiles, tileSize);
        } else if (tileSize == 32) {
            return batchedNCCGPU32(context, refTiles, targetTiles);
        } else {
            return batchedNCCCPU(refTiles, targetTiles);
        }
    }

    private float[][] batchedNCCGPU32(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles) {
        int numTiles = refTiles.length;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        return context.executeWithLock(() -> {
            long refBuffer = 0;
            long targetBuffer = 0;
            long resultBuffer = 0;
            try {
                refBuffer = context.allocateBuffer(refFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                targetBuffer = context.allocateBuffer(targetFlat.length * Float.BYTES, CL_MEM_READ_ONLY);
                resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(refBuffer, refFlat);
                context.writeBuffer(targetBuffer, targetFlat);

                var kernel = context.getKernelManager().getKernel("correlation", "batched_ncc_32");

                try (var stack = MemoryStack.stackPush()) {
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
                        throw new RuntimeException("Failed to enqueue NCC kernel: " + err);
                    }
                }

                context.finish();
                context.readBufferNoSync(resultBuffer, gpuResults);

                return unpackGpuResults(gpuResults, numTiles);
            } finally {
                safeRelease(context, refBuffer, targetBuffer, resultBuffer);
            }
        });
    }

    private float[][] batchedNCCGPULarge(OpenCLContext context, float[][][] refTiles, float[][][] targetTiles, int tileSize) {
        int numTiles = refTiles.length;
        int logTileSize = Integer.numberOfTrailingZeros(tileSize);
        int tileElements = tileSize * tileSize;

        var refFlat = flatten3D(refTiles);
        var targetFlat = flatten3D(targetTiles);
        var gpuResults = new float[numTiles * 3];

        return context.executeWithLock(() -> {
            long refInputBuffer = 0;
            long targetInputBuffer = 0;
            long refComplexBuffer = 0;
            long targetComplexBuffer = 0;
            long tempBuffer = 0;
            long realBuffer = 0;
            long resultBuffer = 0;
            long statsBuffer = 0;

            try {
                int complexSize = numTiles * tileElements * 2 * Float.BYTES;
                int realSize = numTiles * tileElements * Float.BYTES;
                int statsSize = numTiles * 3 * Float.BYTES;  // normFactor, meanRef, meanTarget per tile

                refInputBuffer = context.allocateBuffer(realSize, CL_MEM_READ_ONLY);
                targetInputBuffer = context.allocateBuffer(realSize, CL_MEM_READ_ONLY);
                refComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                targetComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                tempBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
                realBuffer = context.allocateBuffer(realSize, CL_MEM_READ_WRITE);
                resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
                statsBuffer = context.allocateBuffer(statsSize, CL_MEM_READ_WRITE);

                context.writeBuffer(refInputBuffer, refFlat);
                context.writeBuffer(targetInputBuffer, targetFlat);

                var km = context.getKernelManager();
                var computeStatsKernel = km.getKernel("correlation", "compute_tile_stats");
                var zeroMeanAndHannKernel = km.getKernel("correlation", "zero_mean_and_hann");
                var fftRowsKernel = km.getKernel("correlation", "fft_rows");
                var transposeKernel = km.getKernel("correlation", "transpose");
                var crossPowerKernel = km.getKernel("correlation", "cross_power_spectrum");
                var scaleIfftKernel = km.getKernel("correlation", "scale_ifft");
                var fftShiftKernel = km.getKernel("correlation", "fft_shift_real");
                var findPeaksNCCKernel = km.getKernel("correlation", "find_peaks_ncc");

                try (var stack = MemoryStack.stackPush()) {
                    var queue = context.getCommandQueue();

                    // 1. Compute statistics (mean and sum of squared deviations) for both tiles
                    clSetKernelArg(computeStatsKernel, 0, stack.pointers(refInputBuffer));
                    clSetKernelArg(computeStatsKernel, 1, stack.pointers(targetInputBuffer));
                    clSetKernelArg(computeStatsKernel, 2, stack.pointers(statsBuffer));
                    clSetKernelArg(computeStatsKernel, 3, stack.ints(tileSize));
                    clSetKernelArg(computeStatsKernel, 4, stack.ints(numTiles));

                    var globalSizeStats = stack.pointers(numTiles * 256L);
                    var localSizeStats = stack.pointers(256);
                    int err = clEnqueueNDRangeKernel(queue, computeStatsKernel, 1, null, globalSizeStats, localSizeStats, null, null);
                    if (err != 0) {
                        throw new RuntimeException("Failed to enqueue compute_tile_stats kernel: " + err);
                    }

                    // 2. Apply zero-mean and Hann window to both ref and target
                    clSetKernelArg(zeroMeanAndHannKernel, 0, stack.pointers(refInputBuffer));
                    clSetKernelArg(zeroMeanAndHannKernel, 1, stack.pointers(refComplexBuffer));
                    clSetKernelArg(zeroMeanAndHannKernel, 2, stack.pointers(statsBuffer));
                    clSetKernelArg(zeroMeanAndHannKernel, 3, stack.ints(tileSize));
                    clSetKernelArg(zeroMeanAndHannKernel, 4, stack.ints(numTiles));
                    clSetKernelArg(zeroMeanAndHannKernel, 5, stack.ints(0));
                    enqueue3D(queue, zeroMeanAndHannKernel, tileSize, tileSize, numTiles, stack);

                    clSetKernelArg(zeroMeanAndHannKernel, 0, stack.pointers(targetInputBuffer));
                    clSetKernelArg(zeroMeanAndHannKernel, 1, stack.pointers(targetComplexBuffer));
                    clSetKernelArg(zeroMeanAndHannKernel, 5, stack.ints(1));
                    enqueue3D(queue, zeroMeanAndHannKernel, tileSize, tileSize, numTiles, stack);

                    // 3. Forward 2D FFT for reference
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 4. Forward 2D FFT for target
                    fft2D(queue, fftRowsKernel, transposeKernel, targetComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, -1, stack);

                    // 5. Cross-power spectrum (no normalization for NCC)
                    clSetKernelArg(crossPowerKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 1, stack.pointers(targetComplexBuffer));
                    clSetKernelArg(crossPowerKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(crossPowerKernel, 3, stack.ints(numTiles));
                    clSetKernelArg(crossPowerKernel, 4, stack.ints(0));
                    enqueue3D(queue, crossPowerKernel, tileSize, tileSize, numTiles, stack);

                    // 6. Inverse 2D FFT
                    fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                            tileSize, logTileSize, numTiles, +1, stack);

                    // 7. Scale by 1/N^2
                    clSetKernelArg(scaleIfftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(scaleIfftKernel, 1, stack.ints(tileSize));
                    clSetKernelArg(scaleIfftKernel, 2, stack.ints(numTiles));
                    enqueue3D(queue, scaleIfftKernel, tileSize, tileSize, numTiles, stack);

                    // 8. FFT shift and extract real part
                    clSetKernelArg(fftShiftKernel, 0, stack.pointers(refComplexBuffer));
                    clSetKernelArg(fftShiftKernel, 1, stack.pointers(realBuffer));
                    clSetKernelArg(fftShiftKernel, 2, stack.ints(tileSize));
                    clSetKernelArg(fftShiftKernel, 3, stack.ints(numTiles));
                    enqueue3D(queue, fftShiftKernel, tileSize, tileSize, numTiles, stack);

                    // 9. Find peaks with NCC normalization
                    clSetKernelArg(findPeaksNCCKernel, 0, stack.pointers(realBuffer));
                    clSetKernelArg(findPeaksNCCKernel, 1, stack.pointers(statsBuffer));
                    clSetKernelArg(findPeaksNCCKernel, 2, stack.pointers(resultBuffer));
                    clSetKernelArg(findPeaksNCCKernel, 3, stack.ints(tileSize));
                    clSetKernelArg(findPeaksNCCKernel, 4, stack.ints(numTiles));

                    var globalSize = stack.pointers(numTiles * 256L);
                    var localSize = stack.pointers(256);
                    err = clEnqueueNDRangeKernel(queue, findPeaksNCCKernel, 1, null, globalSize, localSize, null, null);
                    if (err != 0) {
                        throw new RuntimeException("Failed to enqueue find_peaks_ncc kernel: " + err);
                    }
                }

                context.finish();
                context.readBufferNoSync(resultBuffer, gpuResults);

                return unpackGpuResults(gpuResults, numTiles);
            } finally {
                safeRelease(context, refInputBuffer, targetInputBuffer, refComplexBuffer,
                        targetComplexBuffer, tempBuffer, realBuffer, resultBuffer, statsBuffer);
            }
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

        // 2. Zero-mean and compute variances
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

        // 3. Normalization factor
        double normFactor = Math.sqrt(sumSqRef * sumSqDef);
        if (normFactor < 1e-10) {
            return new ShiftResult(0, 0, 0);
        }

        // 4. Apply Hann window
        var window = getOrCreateHannWindow(size);
        var windowedRef = applyWindowCopy(zeroMeanRef, window);
        var windowedDef = applyWindowCopy(zeroMeanDef, window);

        // 5. FFT cross-correlation (no normalization)
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

        // 6. Find peak and compute NCC value
        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.real.length / 2d, crossCorr.real[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        var subpixelOffset = fitGaussian2D(crossCorr.real, maxIdx[0], maxIdx[1]);
        shifts[0] += subpixelOffset[0];
        shifts[1] += subpixelOffset[1];

        // 7. NCC confidence = peak value / normalization factor
        double peakValue = crossCorr.real[maxIdx[0]][maxIdx[1]];
        double nccValue = peakValue / normFactor;

        // Clamp to [0, 1] - negative correlations indicate mismatch
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
     * <p>
     * Must be called within {@link OpenCLContext#executeWithLock}.
     *
     * @param context           the OpenCL context (must hold GPU lock)
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

        long positionsBuffer = 0;
        long resultBuffer = 0;
        try {
            positionsBuffer = context.allocateBuffer(positionsFlat.length * Integer.BYTES, CL_MEM_READ_ONLY);
            resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);

            context.writeBufferInt(positionsBuffer, positionsFlat);

            var kernel = context.getKernelManager().getKernel("correlation", "correlate_resident_ncc_32");

            try (var stack = MemoryStack.stackPush()) {
                clSetKernelArg(kernel, 0, stack.pointers(refImageBuffer));
                clSetKernelArg(kernel, 1, stack.pointers(targetImageBuffer));
                clSetKernelArg(kernel, 2, stack.ints(imageWidth));
                clSetKernelArg(kernel, 3, stack.ints(imageHeight));
                clSetKernelArg(kernel, 4, stack.pointers(positionsBuffer));
                clSetKernelArg(kernel, 5, stack.pointers(resultBuffer));
                clSetKernelArg(kernel, 6, stack.ints(numTiles));

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
                    throw new RuntimeException("Failed to enqueue correlate_resident_ncc_32 kernel: " + err);
                }
            }

            context.finish();
            context.readBufferNoSync(resultBuffer, gpuResults);

            return unpackGpuResults(gpuResults, numTiles);
        } finally {
            safeRelease(context, positionsBuffer, resultBuffer);
        }
    }

    /**
     * Performs NCC correlation directly from GPU-resident images for 64x64 or 128x128 tiles.
     * <p>
     * This method extracts tiles directly from GPU-resident images, eliminating
     * the overhead of CPU-GPU tile transfers. It uses the same FFT pipeline as
     * the regular batch NCC method.
     * <p>
     * Must be called within {@link OpenCLContext#executeWithLock}.
     *
     * @param context           the OpenCL context (must hold GPU lock)
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

        long positionsBuffer = 0;
        long refMeansBuffer = 0;
        long targetMeansBuffer = 0;
        long refSumSqBuffer = 0;
        long targetSumSqBuffer = 0;
        long refComplexBuffer = 0;
        long targetComplexBuffer = 0;
        long tempBuffer = 0;
        long realBuffer = 0;
        long resultBuffer = 0;
        long statsBuffer = 0;

        try {
            int complexSize = numTiles * tileElements * 2 * Float.BYTES;
            int realSize = numTiles * tileElements * Float.BYTES;
            int statsSize = numTiles * 3 * Float.BYTES;  // normFactor per tile (plus padding)
            int meansSize = numTiles * Float.BYTES;

            positionsBuffer = context.allocateBuffer(positionsFlat.length * Integer.BYTES, CL_MEM_READ_ONLY);
            refMeansBuffer = context.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            targetMeansBuffer = context.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            refSumSqBuffer = context.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            targetSumSqBuffer = context.allocateBuffer(meansSize, CL_MEM_READ_WRITE);
            refComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            targetComplexBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            tempBuffer = context.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            realBuffer = context.allocateBuffer(realSize, CL_MEM_READ_WRITE);
            resultBuffer = context.allocateBuffer(gpuResults.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            statsBuffer = context.allocateBuffer(statsSize, CL_MEM_READ_WRITE);

            context.writeBufferInt(positionsBuffer, positionsFlat);

            var km = context.getKernelManager();
            var computeStatsFromImagesKernel = km.getKernel("correlation", "compute_tile_stats_from_images");
            var extractTilesZeroMeanKernel = km.getKernel("correlation", "extract_tiles_zero_mean");
            var fftRowsKernel = km.getKernel("correlation", "fft_rows");
            var transposeKernel = km.getKernel("correlation", "transpose");
            var crossPowerKernel = km.getKernel("correlation", "cross_power_spectrum");
            var scaleIfftKernel = km.getKernel("correlation", "scale_ifft");
            var fftShiftKernel = km.getKernel("correlation", "fft_shift_real");
            var findPeaksNCCKernel = km.getKernel("correlation", "find_peaks_ncc");

            try (var stack = MemoryStack.stackPush()) {
                var queue = context.getCommandQueue();

                // 1. Compute statistics (mean and sumSqDev) for ref image tiles
                clSetKernelArg(computeStatsFromImagesKernel, 0, stack.pointers(refImageBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 1, stack.ints(imageWidth));
                clSetKernelArg(computeStatsFromImagesKernel, 2, stack.ints(imageHeight));
                clSetKernelArg(computeStatsFromImagesKernel, 3, stack.pointers(positionsBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 4, stack.pointers(refMeansBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 5, stack.pointers(refSumSqBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 6, stack.ints(tileSize));
                clSetKernelArg(computeStatsFromImagesKernel, 7, stack.ints(numTiles));

                var globalSizeStats = stack.pointers(256, numTiles);
                var localSizeStats = stack.pointers(256, 1);
                int err = clEnqueueNDRangeKernel(queue, computeStatsFromImagesKernel, 2, null, globalSizeStats, localSizeStats, null, null);
                if (err != 0) {
                    throw new RuntimeException("Failed to enqueue compute_tile_stats_from_images kernel (ref): " + err);
                }

                // 2. Compute statistics for target image tiles
                clSetKernelArg(computeStatsFromImagesKernel, 0, stack.pointers(targetImageBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 4, stack.pointers(targetMeansBuffer));
                clSetKernelArg(computeStatsFromImagesKernel, 5, stack.pointers(targetSumSqBuffer));
                err = clEnqueueNDRangeKernel(queue, computeStatsFromImagesKernel, 2, null, globalSizeStats, localSizeStats, null, null);
                if (err != 0) {
                    throw new RuntimeException("Failed to enqueue compute_tile_stats_from_images kernel (target): " + err);
                }

                // 3. Extract tiles with zero-mean and Hann window from images
                clSetKernelArg(extractTilesZeroMeanKernel, 0, stack.pointers(refImageBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 1, stack.pointers(targetImageBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 2, stack.ints(imageWidth));
                clSetKernelArg(extractTilesZeroMeanKernel, 3, stack.ints(imageHeight));
                clSetKernelArg(extractTilesZeroMeanKernel, 4, stack.pointers(positionsBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 5, stack.pointers(refMeansBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 6, stack.pointers(targetMeansBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 7, stack.pointers(refComplexBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 8, stack.pointers(targetComplexBuffer));
                clSetKernelArg(extractTilesZeroMeanKernel, 9, stack.ints(tileSize));
                clSetKernelArg(extractTilesZeroMeanKernel, 10, stack.ints(numTiles));
                enqueue3D(queue, extractTilesZeroMeanKernel, tileSize, tileSize, numTiles, stack);

                // 4. Compute normalization factors on GPU: normFactor = sqrt(refSumSq * targetSumSq)
                var computeNormFactorsKernel = km.getKernel("correlation", "compute_ncc_norm_factors");
                clSetKernelArg(computeNormFactorsKernel, 0, stack.pointers(refSumSqBuffer));
                clSetKernelArg(computeNormFactorsKernel, 1, stack.pointers(targetSumSqBuffer));
                clSetKernelArg(computeNormFactorsKernel, 2, stack.pointers(statsBuffer));
                clSetKernelArg(computeNormFactorsKernel, 3, stack.ints(numTiles));

                var globalSizeNorm = stack.pointers(numTiles);
                err = clEnqueueNDRangeKernel(queue, computeNormFactorsKernel, 1, null, globalSizeNorm, null, null, null);
                if (err != 0) {
                    throw new RuntimeException("Failed to enqueue compute_ncc_norm_factors kernel: " + err);
                }

                // 5. Forward 2D FFT for reference
                fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                        tileSize, logTileSize, numTiles, -1, stack);

                // 6. Forward 2D FFT for target
                fft2D(queue, fftRowsKernel, transposeKernel, targetComplexBuffer, tempBuffer,
                        tileSize, logTileSize, numTiles, -1, stack);

                // 7. Cross-power spectrum (no normalization for NCC)
                clSetKernelArg(crossPowerKernel, 0, stack.pointers(refComplexBuffer));
                clSetKernelArg(crossPowerKernel, 1, stack.pointers(targetComplexBuffer));
                clSetKernelArg(crossPowerKernel, 2, stack.ints(tileSize));
                clSetKernelArg(crossPowerKernel, 3, stack.ints(numTiles));
                clSetKernelArg(crossPowerKernel, 4, stack.ints(0));
                enqueue3D(queue, crossPowerKernel, tileSize, tileSize, numTiles, stack);

                // 8. Inverse 2D FFT
                fft2D(queue, fftRowsKernel, transposeKernel, refComplexBuffer, tempBuffer,
                        tileSize, logTileSize, numTiles, +1, stack);

                // 9. Scale by 1/N^2
                clSetKernelArg(scaleIfftKernel, 0, stack.pointers(refComplexBuffer));
                clSetKernelArg(scaleIfftKernel, 1, stack.ints(tileSize));
                clSetKernelArg(scaleIfftKernel, 2, stack.ints(numTiles));
                enqueue3D(queue, scaleIfftKernel, tileSize, tileSize, numTiles, stack);

                // 10. FFT shift and extract real part
                clSetKernelArg(fftShiftKernel, 0, stack.pointers(refComplexBuffer));
                clSetKernelArg(fftShiftKernel, 1, stack.pointers(realBuffer));
                clSetKernelArg(fftShiftKernel, 2, stack.ints(tileSize));
                clSetKernelArg(fftShiftKernel, 3, stack.ints(numTiles));
                enqueue3D(queue, fftShiftKernel, tileSize, tileSize, numTiles, stack);

                // 11. Find peaks with NCC normalization
                clSetKernelArg(findPeaksNCCKernel, 0, stack.pointers(realBuffer));
                clSetKernelArg(findPeaksNCCKernel, 1, stack.pointers(statsBuffer));
                clSetKernelArg(findPeaksNCCKernel, 2, stack.pointers(resultBuffer));
                clSetKernelArg(findPeaksNCCKernel, 3, stack.ints(tileSize));
                clSetKernelArg(findPeaksNCCKernel, 4, stack.ints(numTiles));

                var globalSize = stack.pointers(numTiles * 256L);
                var localSize = stack.pointers(256);
                err = clEnqueueNDRangeKernel(queue, findPeaksNCCKernel, 1, null, globalSize, localSize, null, null);
                if (err != 0) {
                    throw new RuntimeException("Failed to enqueue find_peaks_ncc kernel: " + err);
                }
            }

            context.finish();
            context.readBufferNoSync(resultBuffer, gpuResults);

            return unpackGpuResults(gpuResults, numTiles);
        } finally {
            safeRelease(context, positionsBuffer, refMeansBuffer, targetMeansBuffer,
                    refSumSqBuffer, targetSumSqBuffer, refComplexBuffer, targetComplexBuffer,
                    tempBuffer, realBuffer, resultBuffer, statsBuffer);
        }
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
        long maxWorkGroupSize = context.getCapabilities().maxWorkGroupSize();
        // NCC32 uses 32x32 work groups = 1024 work items
        // NCC64/128 use smaller work groups (typically 256 or less for statistics passes)
        long requiredWorkGroupSize = (tileSize == 32) ? 1024 : 256;
        return maxWorkGroupSize >= requiredWorkGroupSize;
    }

    /**
     * Performs NCC correlation directly from GPU-resident images.
     * Automatically selects the appropriate implementation based on tile size.
     * <p>
     * Must be called within {@link OpenCLContext#executeWithLock}.
     *
     * @param context           the OpenCL context (must hold GPU lock)
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
            long required = (tileSize == 32) ? 1024 : 256;
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
