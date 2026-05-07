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
package me.champeau.a4j.math.regression;

import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opencl.CL10.*;

/**
 * GPU-accelerated distortion grid filtering using MAD-based outlier removal
 * and Gaussian smoothing.
 * <p>
 * This class provides both CPU and GPU implementations of:
 * <ul>
 *   <li>MAD (Median Absolute Deviation) filtering for outlier removal</li>
 *   <li>Inverse-distance-squared interpolation for unsampled points</li>
 *   <li>Separable Gaussian smoothing</li>
 * </ul>
 */
public class DistortionGridFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistortionGridFilter.class);

    private static final int MIN_GRID_SIZE_FOR_GPU = 1000;
    // Maximum half-window size supported by GPU kernel (11x11 window = 120 neighbors)
    private static final int MAX_GPU_HALF_WINDOW = 5;
    private static final int HISTOGRAM_BINS = 512;
    private static final DistortionGridFilter INSTANCE = new DistortionGridFilter();

    private DistortionGridFilter() {
    }

    public static DistortionGridFilter getInstance() {
        return INSTANCE;
    }

    /**
     * Fused filter pipeline: interpolate + MAD filter + Gaussian smooth in single GPU lock.
     * Reduces buffer allocation/deallocation overhead and data transfers.
     *
     * @param gridDx       displacement X values [gridHeight][gridWidth]
     * @param gridDy       displacement Y values [gridHeight][gridWidth]
     * @param sampled      boolean array indicating which points are sampled
     * @param gridWidth    width of the grid
     * @param gridHeight   height of the grid
     * @param searchRadius radius for interpolation neighbor search
     * @param halfWindow   half-size of the MAD filtering window
     * @param madThreshold MAD threshold for outlier detection
     * @param sigma        Gaussian sigma parameter
     */
    public void filterAndSmoothFused(float[][] gridDx, float[][] gridDy, boolean[][] sampled,
                                     int gridWidth, int gridHeight, int searchRadius,
                                     int halfWindow, float madThreshold, float sigma) {
        int totalPoints = gridWidth * gridHeight;
        var context = OpenCLSupport.getContext();

        if (context != null && OpenCLSupport.isEnabled() && totalPoints >= MIN_GRID_SIZE_FOR_GPU && halfWindow <= MAX_GPU_HALF_WINDOW) {
            try {
                filterAndSmoothFusedGPU(context, gridDx, gridDy, sampled, gridWidth, gridHeight,
                        searchRadius, halfWindow, madThreshold, sigma);
                return;
            } catch (Exception e) {
                context.recordError("DistortionGridFilter.filterAndSmoothFused", e);
            }
        }

        interpolateUnsampledCPU(gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius);
        madFilterCPU(gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold);
        gaussianSmoothCPU(gridDx, gridDy, gridWidth, gridHeight, sigma);
    }

    private void filterAndSmoothFusedGPU(OpenCLContext context, float[][] gridDx, float[][] gridDy,
                                         boolean[][] sampled, int gridWidth, int gridHeight,
                                         int searchRadius, int halfWindow, float madThreshold, float sigma) {
        var flatDx = flatten2D(gridDx);
        var flatDy = flatten2D(gridDy);
        var flatSampled = flattenBooleanToInt(sampled);

        int kernelRadius = (int) Math.ceil(3 * sigma);
        var gaussianKernel = createGaussianKernel1D(sigma, kernelRadius);

        int floatSize = gridWidth * gridHeight * Float.BYTES;
        int intSize = gridWidth * gridHeight * Integer.BYTES;
        int kernelSize = gaussianKernel.length * Float.BYTES;

        context.runOp(op -> {
            var dxBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var dyBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var sampledBuf = op.allocateBuffer(intSize, CL_MEM_READ_ONLY);
            var tempDxBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var tempDyBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var kernelBuf = op.allocateBuffer(kernelSize, CL_MEM_READ_ONLY);

            op.write(dxBuf, flatDx);
            op.write(dyBuf, flatDy);
            op.writeInts(sampledBuf, flatSampled);
            op.write(kernelBuf, gaussianKernel);

            // Step 1: Interpolate unsampled points
            int localX = Math.min(16, gridWidth);
            int localY = Math.min(16, gridHeight);
            int globalX = ((gridWidth + localX - 1) / localX) * localX;
            int globalY = ((gridHeight + localY - 1) / localY) * localY;
            op.kernel("distortion_filter", "interpolate_unsampled")
                    .arg(dxBuf).arg(dyBuf).arg(sampledBuf)
                    .arg(gridWidth).arg(gridHeight).arg(searchRadius)
                    .global(globalX, globalY).local(localX, localY)
                    .run();

            // Step 2: MAD filter (dx -> tempDx, dy -> tempDy)
            int madLocalX = Math.min(8, gridWidth);
            int madLocalY = Math.min(8, gridHeight);
            int madGlobalX = ((gridWidth + madLocalX - 1) / madLocalX) * madLocalX;
            int madGlobalY = ((gridHeight + madLocalY - 1) / madLocalY) * madLocalY;
            op.kernel("distortion_filter", "mad_filter_grid_histogram")
                    .arg(dxBuf).arg(dyBuf).arg(tempDxBuf).arg(tempDyBuf)
                    .arg(gridWidth).arg(gridHeight).arg(halfWindow).arg(madThreshold)
                    .global(madGlobalX, madGlobalY).local(madLocalX, madLocalY)
                    .run();

            // Step 3: Gaussian smooth horizontal (tempDx -> dxBuf, tempDy -> dyBuf)
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(tempDxBuf).arg(dxBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(tempDyBuf).arg(dyBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();

            // Step 4: Gaussian smooth vertical (dxBuf -> tempDx, dyBuf -> tempDy)
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(dxBuf).arg(tempDxBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(dyBuf).arg(tempDyBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();

            op.read(tempDxBuf, flatDx);
            op.read(tempDyBuf, flatDy);
        });

        unflatten2D(flatDx, gridDx);
        unflatten2D(flatDy, gridDy);
    }

    /**
     * Applies MAD-based outlier filtering to a distortion grid.
     * Uses GPU if available and grid is large enough.
     *
     * @param gridDx       displacement X values [gridHeight][gridWidth]
     * @param gridDy       displacement Y values [gridHeight][gridWidth]
     * @param gridWidth    width of the grid
     * @param gridHeight   height of the grid
     * @param halfWindow   half-size of the filtering window (e.g., 2 for 5x5)
     * @param madThreshold MAD threshold for outlier detection (typically 3.0)
     */
    public void madFilter(float[][] gridDx, float[][] gridDy,
                          int gridWidth, int gridHeight,
                          int halfWindow, float madThreshold) {
        int totalPoints = gridWidth * gridHeight;
        var context = OpenCLSupport.getContext();

        // GPU kernel supports max 5x5 window (halfWindow=2). Larger windows use CPU.
        if (context != null && OpenCLSupport.isEnabled() && totalPoints >= MIN_GRID_SIZE_FOR_GPU && halfWindow <= MAX_GPU_HALF_WINDOW) {
            try {
                madFilterGPU(context, gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold);
                return;
            } catch (Exception e) {
                context.recordError("DistortionGridFilter.madFilter", e);
            }
        }
        madFilterCPU(gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold);
    }

    /**
     * Interpolates unsampled grid points using inverse-distance-squared weighting.
     * Uses GPU if available and grid is large enough.
     *
     * @param gridDx       displacement X values [gridHeight][gridWidth]
     * @param gridDy       displacement Y values [gridHeight][gridWidth]
     * @param sampled      boolean array indicating which points are sampled [gridHeight][gridWidth]
     * @param gridWidth    width of the grid
     * @param gridHeight   height of the grid
     * @param searchRadius radius for neighbor search
     */
    public void interpolateUnsampled(float[][] gridDx, float[][] gridDy, boolean[][] sampled,
                                     int gridWidth, int gridHeight, int searchRadius) {
        int totalPoints = gridWidth * gridHeight;
        var context = OpenCLSupport.getContext();

        if (context != null && OpenCLSupport.isEnabled() && totalPoints >= MIN_GRID_SIZE_FOR_GPU) {
            try {
                interpolateUnsampledGPU(context, gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius);
                return;
            } catch (Exception e) {
                context.recordError("DistortionGridFilter.interpolateUnsampled", e);
            }
        }
        interpolateUnsampledCPU(gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius);
    }

    /**
     * Applies separable Gaussian smoothing to a distortion grid.
     * Uses GPU if available and grid is large enough.
     *
     * @param gridDx     displacement X values [gridHeight][gridWidth]
     * @param gridDy     displacement Y values [gridHeight][gridWidth]
     * @param gridWidth  width of the grid
     * @param gridHeight height of the grid
     * @param sigma      Gaussian sigma parameter
     */
    public void gaussianSmooth(float[][] gridDx, float[][] gridDy,
                               int gridWidth, int gridHeight, float sigma) {
        int totalPoints = gridWidth * gridHeight;
        var context = OpenCLSupport.getContext();

        if (context != null && OpenCLSupport.isEnabled() && totalPoints >= MIN_GRID_SIZE_FOR_GPU) {
            try {
                gaussianSmoothGPU(context, gridDx, gridDy, gridWidth, gridHeight, sigma);
                return;
            } catch (Exception e) {
                LOGGER.error("GPU gaussianSmooth failed, falling back to CPU", e);
                context.recordError("DistortionGridFilter.gaussianSmooth", e);
            }
        }
        gaussianSmoothCPU(gridDx, gridDy, gridWidth, gridHeight, sigma);
    }

    // ========== GPU Implementations ==========

    private void madFilterGPU(OpenCLContext context, float[][] gridDx, float[][] gridDy,
                              int gridWidth, int gridHeight, int halfWindow, float madThreshold) {
        var inputDx = flatten2D(gridDx);
        var inputDy = flatten2D(gridDy);
        var outputDx = new float[gridWidth * gridHeight];
        var outputDy = new float[gridWidth * gridHeight];

        int size = gridWidth * gridHeight * Float.BYTES;

        context.runOp(op -> {
            var inputDxBuf = op.allocateBuffer(size, CL_MEM_READ_ONLY);
            var inputDyBuf = op.allocateBuffer(size, CL_MEM_READ_ONLY);
            var outputDxBuf = op.allocateBuffer(size, CL_MEM_WRITE_ONLY);
            var outputDyBuf = op.allocateBuffer(size, CL_MEM_WRITE_ONLY);

            op.write(inputDxBuf, inputDx);
            op.write(inputDyBuf, inputDy);

            int localX = Math.min(8, gridWidth);
            int localY = Math.min(8, gridHeight);
            int globalX = ((gridWidth + localX - 1) / localX) * localX;
            int globalY = ((gridHeight + localY - 1) / localY) * localY;
            op.kernel("distortion_filter", "mad_filter_grid_histogram")
                    .arg(inputDxBuf).arg(inputDyBuf).arg(outputDxBuf).arg(outputDyBuf)
                    .arg(gridWidth).arg(gridHeight).arg(halfWindow).arg(madThreshold)
                    .global(globalX, globalY).local(localX, localY)
                    .run();

            op.read(outputDxBuf, outputDx);
            op.read(outputDyBuf, outputDy);
        });

        unflatten2D(outputDx, gridDx);
        unflatten2D(outputDy, gridDy);
    }

    private void interpolateUnsampledGPU(OpenCLContext context, float[][] gridDx, float[][] gridDy,
                                         boolean[][] sampled, int gridWidth, int gridHeight, int searchRadius) {
        var flatDx = flatten2D(gridDx);
        var flatDy = flatten2D(gridDy);
        var flatSampled = flattenBooleanToInt(sampled);

        int floatSize = gridWidth * gridHeight * Float.BYTES;
        int intSize = gridWidth * gridHeight * Integer.BYTES;

        context.runOp(op -> {
            var dxBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var dyBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var sampledBuf = op.allocateBuffer(intSize, CL_MEM_READ_ONLY);

            op.write(dxBuf, flatDx);
            op.write(dyBuf, flatDy);
            op.writeInts(sampledBuf, flatSampled);

            int localX = Math.min(16, gridWidth);
            int localY = Math.min(16, gridHeight);
            int globalX = ((gridWidth + localX - 1) / localX) * localX;
            int globalY = ((gridHeight + localY - 1) / localY) * localY;
            op.kernel("distortion_filter", "interpolate_unsampled")
                    .arg(dxBuf).arg(dyBuf).arg(sampledBuf)
                    .arg(gridWidth).arg(gridHeight).arg(searchRadius)
                    .global(globalX, globalY).local(localX, localY)
                    .run();

            op.read(dxBuf, flatDx);
            op.read(dyBuf, flatDy);
        });

        unflatten2D(flatDx, gridDx);
        unflatten2D(flatDy, gridDy);
    }

    private void gaussianSmoothGPU(OpenCLContext context, float[][] gridDx, float[][] gridDy,
                                   int gridWidth, int gridHeight, float sigma) {
        int kernelRadius = (int) Math.ceil(3 * sigma);
        var gaussianKernel = createGaussianKernel1D(sigma, kernelRadius);

        var flatDx = flatten2D(gridDx);
        var flatDy = flatten2D(gridDy);

        int floatSize = gridWidth * gridHeight * Float.BYTES;
        int kernelSize = gaussianKernel.length * Float.BYTES;

        context.runOp(op -> {
            var dxBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var dyBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var tempDxBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var tempDyBuf = op.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
            var kernelBuf = op.allocateBuffer(kernelSize, CL_MEM_READ_ONLY);

            op.write(dxBuf, flatDx);
            op.write(dyBuf, flatDy);
            op.write(kernelBuf, gaussianKernel);

            // Horizontal pass for Dx and Dy
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(dxBuf).arg(tempDxBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(dyBuf).arg(tempDyBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();

            // Vertical pass for Dx and Dy
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(tempDxBuf).arg(dxBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(tempDyBuf).arg(dyBuf).arg(kernelBuf)
                    .arg(gridWidth).arg(gridHeight).arg(kernelRadius)
                    .global(gridWidth, gridHeight).local(1, 1)
                    .run();

            op.read(dxBuf, flatDx);
            op.read(dyBuf, flatDy);
        });

        unflatten2D(flatDx, gridDx);
        unflatten2D(flatDy, gridDy);
    }

    // ========== CPU Implementations ==========

    private void madFilterCPU(float[][] gridDx, float[][] gridDy,
                              int gridWidth, int gridHeight, int halfWindow, float madThreshold) {
        var outputDx = new float[gridHeight][gridWidth];
        var outputDy = new float[gridHeight][gridWidth];
        int maxNeighbors = (2 * halfWindow + 1) * (2 * halfWindow + 1) - 1;
        var neighborsDx = new float[maxNeighbors];
        var neighborsDy = new float[maxNeighbors];
        var absDiffDx = new float[maxNeighbors];
        var absDiffDy = new float[maxNeighbors];

        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                float valueDx = gridDx[gy][gx];
                float valueDy = gridDy[gy][gx];

                float minDx = Float.MAX_VALUE, maxDx = -Float.MAX_VALUE;
                float minDy = Float.MAX_VALUE, maxDy = -Float.MAX_VALUE;

                int count = 0;
                for (int ny = gy - halfWindow; ny <= gy + halfWindow; ny++) {
                    if (ny < 0 || ny >= gridHeight) {
                        continue;
                    }
                    for (int nx = gx - halfWindow; nx <= gx + halfWindow; nx++) {
                        if (nx < 0 || nx >= gridWidth || (nx == gx && ny == gy)) {
                            continue;
                        }
                        float dx = gridDx[ny][nx];
                        float dy = gridDy[ny][nx];
                        neighborsDx[count] = dx;
                        neighborsDy[count] = dy;
                        minDx = Math.min(minDx, dx);
                        maxDx = Math.max(maxDx, dx);
                        minDy = Math.min(minDy, dy);
                        maxDy = Math.max(maxDy, dy);
                        count++;
                    }
                }

                if (count == 0) {
                    outputDx[gy][gx] = valueDx;
                    outputDy[gy][gx] = valueDy;
                    continue;
                }

                float medianDx = histogramMedian(neighborsDx, count, minDx, maxDx);
                float medianDy = histogramMedian(neighborsDy, count, minDy, maxDy);

                float minAbsDx = Float.MAX_VALUE, maxAbsDx = -Float.MAX_VALUE;
                float minAbsDy = Float.MAX_VALUE, maxAbsDy = -Float.MAX_VALUE;

                for (int i = 0; i < count; i++) {
                    float adx = Math.abs(neighborsDx[i] - medianDx);
                    float ady = Math.abs(neighborsDy[i] - medianDy);
                    absDiffDx[i] = adx;
                    absDiffDy[i] = ady;
                    minAbsDx = Math.min(minAbsDx, adx);
                    maxAbsDx = Math.max(maxAbsDx, adx);
                    minAbsDy = Math.min(minAbsDy, ady);
                    maxAbsDy = Math.max(maxAbsDy, ady);
                }

                float madDx = Math.max(histogramMedian(absDiffDx, count, minAbsDx, maxAbsDx) * 1.4826f, 0.1f);
                float madDy = Math.max(histogramMedian(absDiffDy, count, minAbsDy, maxAbsDy) * 1.4826f, 0.1f);

                outputDx[gy][gx] = Math.abs(valueDx - medianDx) > madThreshold * madDx ? medianDx : valueDx;
                outputDy[gy][gx] = Math.abs(valueDy - medianDy) > madThreshold * madDy ? medianDy : valueDy;
            }
        }

        for (int gy = 0; gy < gridHeight; gy++) {
            System.arraycopy(outputDx[gy], 0, gridDx[gy], 0, gridWidth);
            System.arraycopy(outputDy[gy], 0, gridDy[gy], 0, gridWidth);
        }
    }

    private static float histogramMedian(float[] data, int n, float minVal, float maxVal) {
        if (n == 0) {
            return 0.0f;
        }
        if (n == 1) {
            return data[0];
        }

        float range = maxVal - minVal;
        if (range < 1e-10f) {
            return minVal;
        }

        int[] histogram = new int[HISTOGRAM_BINS];
        float binWidth = range / HISTOGRAM_BINS;
        float invBinWidth = 1.0f / binWidth;

        for (int i = 0; i < n; i++) {
            int bin = (int) ((data[i] - minVal) * invBinWidth);
            bin = Math.max(0, Math.min(HISTOGRAM_BINS - 1, bin));
            histogram[bin]++;
        }

        int target = n / 2;
        int cumsum = 0;
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            cumsum += histogram[i];
            if (cumsum > target) {
                return minVal + (i + 0.5f) * binWidth;
            }
        }
        return (minVal + maxVal) * 0.5f;
    }

    private void interpolateUnsampledCPU(float[][] gridDx, float[][] gridDy, boolean[][] sampled,
                                         int gridWidth, int gridHeight, int searchRadius) {
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                if (sampled[gy][gx]) {
                    continue;
                }

                float sumDx = 0, sumDy = 0, weightSum = 0;
                for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                    int ny = gy + dy;
                    if (ny < 0 || ny >= gridHeight) {
                        continue;
                    }
                    for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = gx + dx;
                        if (nx < 0 || nx >= gridWidth || !sampled[ny][nx]) {
                            continue;
                        }
                        float distSq = dx * dx + dy * dy;
                        float weight = 1.0f / distSq;
                        sumDx += gridDx[ny][nx] * weight;
                        sumDy += gridDy[ny][nx] * weight;
                        weightSum += weight;
                    }
                }

                if (weightSum > 0) {
                    gridDx[gy][gx] = sumDx / weightSum;
                    gridDy[gy][gx] = sumDy / weightSum;
                }
            }
        }
    }

    private void gaussianSmoothCPU(float[][] gridDx, float[][] gridDy,
                                   int gridWidth, int gridHeight, float sigma) {
        int kernelRadius = (int) Math.ceil(3 * sigma);
        var kernel = createGaussianKernel1D(sigma, kernelRadius);

        var tempDx = new float[gridHeight][gridWidth];
        var tempDy = new float[gridHeight][gridWidth];
        // Horizontal pass
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                float sumDx = 0, sumDy = 0, weightSum = 0;
                for (int k = -kernelRadius; k <= kernelRadius; k++) {
                    int nx = gx + k;
                    if (nx >= 0 && nx < gridWidth) {
                        float w = kernel[k + kernelRadius];
                        sumDx += gridDx[gy][nx] * w;
                        sumDy += gridDy[gy][nx] * w;
                        weightSum += w;
                    }
                }
                tempDx[gy][gx] = sumDx / weightSum;
                tempDy[gy][gx] = sumDy / weightSum;
            }
        }

        // Vertical pass
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                float sumDx = 0, sumDy = 0, weightSum = 0;
                for (int k = -kernelRadius; k <= kernelRadius; k++) {
                    int ny = gy + k;
                    if (ny >= 0 && ny < gridHeight) {
                        float w = kernel[k + kernelRadius];
                        sumDx += tempDx[ny][gx] * w;
                        sumDy += tempDy[ny][gx] * w;
                        weightSum += w;
                    }
                }
                gridDx[gy][gx] = sumDx / weightSum;
                gridDy[gy][gx] = sumDy / weightSum;
            }
        }
    }

    // ========== Utility Methods ==========

    private static void safeRelease(OpenCLContext context, long buffer) {
        if (buffer != 0) {
            try {
                context.releaseBuffer(buffer);
            } catch (Exception e) {
                LOGGER.error("Failed to release GPU buffer", e);
            }
        }
    }

    private static float[] flatten2D(float[][] data) {
        int height = data.length;
        int width = data[0].length;
        var flat = new float[height * width];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, flat, idx, width);
            idx += width;
        }
        return flat;
    }

    private static void unflatten2D(float[] flat, float[][] data) {
        int height = data.length;
        int width = data[0].length;
        int idx = 0;
        for (int y = 0; y < height; y++) {
            System.arraycopy(flat, idx, data[y], 0, width);
            idx += width;
        }
    }

    private static int[] flattenBooleanToInt(boolean[][] data) {
        int height = data.length;
        int width = data[0].length;
        var flat = new int[height * width];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flat[idx++] = data[y][x] ? 1 : 0;
            }
        }
        return flat;
    }

    private static float[] createGaussianKernel1D(float sigma, int radius) {
        int size = 2 * radius + 1;
        var kernel = new float[size];
        float sum = 0;
        float twoSigmaSq = 2 * sigma * sigma;
        for (int i = -radius; i <= radius; i++) {
            float val = (float) Math.exp(-(i * i) / twoSigmaSq);
            kernel[i + radius] = val;
            sum += val;
        }
        // Normalize
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }
}
