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

        context.executeWithLock(() -> {
            long dxBuf = 0, dyBuf = 0, sampledBuf = 0;
            long tempDxBuf = 0, tempDyBuf = 0, kernelBuf = 0;
            try {
                int floatSize = gridWidth * gridHeight * Float.BYTES;
                int intSize = gridWidth * gridHeight * Integer.BYTES;
                int kernelSize = gaussianKernel.length * Float.BYTES;

                dxBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                dyBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                sampledBuf = context.allocateBuffer(intSize, CL_MEM_READ_ONLY);
                tempDxBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                tempDyBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                kernelBuf = context.allocateBuffer(kernelSize, CL_MEM_READ_ONLY);

                context.writeBuffer(dxBuf, flatDx);
                context.writeBuffer(dyBuf, flatDy);
                context.writeBufferInt(sampledBuf, flatSampled);
                context.writeBuffer(kernelBuf, gaussianKernel);

                try (var stack = MemoryStack.stackPush()) {
                    // Step 1: Interpolate unsampled points
                    var interpKernel = context.getKernelManager().getKernel("distortion_filter", "interpolate_unsampled");
                    clSetKernelArg(interpKernel, 0, stack.pointers(dxBuf));
                    clSetKernelArg(interpKernel, 1, stack.pointers(dyBuf));
                    clSetKernelArg(interpKernel, 2, stack.pointers(sampledBuf));
                    clSetKernelArg(interpKernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(interpKernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(interpKernel, 5, stack.ints(searchRadius));

                    int localX = Math.min(16, gridWidth);
                    int localY = Math.min(16, gridHeight);
                    int globalX = ((gridWidth + localX - 1) / localX) * localX;
                    int globalY = ((gridHeight + localY - 1) / localY) * localY;
                    var globalSize = stack.pointers(globalX, globalY);
                    var localSize = stack.pointers(localX, localY);

                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), interpKernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue interpolate_unsampled kernel: " + err);
                    }

                    // Step 2: MAD filter (dx -> tempDx, dy -> tempDy)
                    var madKernel = context.getKernelManager().getKernel("distortion_filter", "mad_filter_grid_histogram");
                    clSetKernelArg(madKernel, 0, stack.pointers(dxBuf));
                    clSetKernelArg(madKernel, 1, stack.pointers(dyBuf));
                    clSetKernelArg(madKernel, 2, stack.pointers(tempDxBuf));
                    clSetKernelArg(madKernel, 3, stack.pointers(tempDyBuf));
                    clSetKernelArg(madKernel, 4, stack.ints(gridWidth));
                    clSetKernelArg(madKernel, 5, stack.ints(gridHeight));
                    clSetKernelArg(madKernel, 6, stack.ints(halfWindow));
                    clSetKernelArg(madKernel, 7, stack.floats(madThreshold));

                    // Histogram-based kernel with 512 bins, use 8x8 work groups
                    int madLocalX = Math.min(8, gridWidth);
                    int madLocalY = Math.min(8, gridHeight);
                    int madGlobalX = ((gridWidth + madLocalX - 1) / madLocalX) * madLocalX;
                    int madGlobalY = ((gridHeight + madLocalY - 1) / madLocalY) * madLocalY;
                    var madGlobalSize = stack.pointers(madGlobalX, madGlobalY);
                    var madLocalSize = stack.pointers(madLocalX, madLocalY);

                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), madKernel, 2, null, madGlobalSize, madLocalSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue mad_filter_grid_histogram kernel: " + err);
                    }

                    // Step 3: Gaussian smooth horizontal (tempDx -> dxBuf, tempDy -> dyBuf)
                    var hKernel = context.getKernelManager().getKernel("distortion_filter", "gaussian_smooth_horizontal");
                    var vKernel = context.getKernelManager().getKernel("distortion_filter", "gaussian_smooth_vertical");

                    var gaussGlobalSize = stack.pointers(gridWidth, gridHeight);
                    var gaussLocalSize = stack.pointers(1, 1);

                    clSetKernelArg(hKernel, 0, stack.pointers(tempDxBuf));
                    clSetKernelArg(hKernel, 1, stack.pointers(dxBuf));
                    clSetKernelArg(hKernel, 2, stack.pointers(kernelBuf));
                    clSetKernelArg(hKernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(hKernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(hKernel, 5, stack.ints(kernelRadius));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), hKernel, 2, null, gaussGlobalSize, gaussLocalSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_horizontal (Dx): " + err);
                    }

                    clSetKernelArg(hKernel, 0, stack.pointers(tempDyBuf));
                    clSetKernelArg(hKernel, 1, stack.pointers(dyBuf));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), hKernel, 2, null, gaussGlobalSize, gaussLocalSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_horizontal (Dy): " + err);
                    }

                    // Step 4: Gaussian smooth vertical (dxBuf -> tempDx -> dxBuf, dyBuf -> tempDy -> dyBuf)
                    clSetKernelArg(vKernel, 0, stack.pointers(dxBuf));
                    clSetKernelArg(vKernel, 1, stack.pointers(tempDxBuf));
                    clSetKernelArg(vKernel, 2, stack.pointers(kernelBuf));
                    clSetKernelArg(vKernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(vKernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(vKernel, 5, stack.ints(kernelRadius));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), vKernel, 2, null, gaussGlobalSize, gaussLocalSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_vertical (Dx): " + err);
                    }

                    clSetKernelArg(vKernel, 0, stack.pointers(dyBuf));
                    clSetKernelArg(vKernel, 1, stack.pointers(tempDyBuf));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), vKernel, 2, null, gaussGlobalSize, gaussLocalSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_vertical (Dy): " + err);
                    }
                }

                context.finish();
                context.readBuffer(tempDxBuf, flatDx);
                context.readBuffer(tempDyBuf, flatDy);
            } finally {
                try {
                    context.finish();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] finish() failed in filterAndSmoothFusedGPU");
                    e.printStackTrace();
                }
                safeRelease(context, dxBuf);
                safeRelease(context, dyBuf);
                safeRelease(context, sampledBuf);
                safeRelease(context, tempDxBuf);
                safeRelease(context, tempDyBuf);
                safeRelease(context, kernelBuf);
                try {
                    context.flush();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] flush() failed in filterAndSmoothFusedGPU");
                    e.printStackTrace();
                }
            }
            return null;
        });

        unflatten2D(flatDx, gridDx);
        unflatten2D(flatDy, gridDy);
    }

    /**
     * Applies MAD-based outlier filtering to a distortion grid.
     * Uses GPU if available and grid is large enough.
     *
     * @param gridDx      displacement X values [gridHeight][gridWidth]
     * @param gridDy      displacement Y values [gridHeight][gridWidth]
     * @param gridWidth   width of the grid
     * @param gridHeight  height of the grid
     * @param halfWindow  half-size of the filtering window (e.g., 2 for 5x5)
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
     * @param gridDx      displacement X values [gridHeight][gridWidth]
     * @param gridDy      displacement Y values [gridHeight][gridWidth]
     * @param gridWidth   width of the grid
     * @param gridHeight  height of the grid
     * @param sigma       Gaussian sigma parameter
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
                System.err.println("[DistortionGridFilter] GPU gaussianSmooth failed, falling back to CPU");
                e.printStackTrace();
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

        context.executeWithLock(() -> {
            long inputDxBuf = 0, inputDyBuf = 0, outputDxBuf = 0, outputDyBuf = 0;
            try {
                int size = gridWidth * gridHeight * Float.BYTES;
                inputDxBuf = context.allocateBuffer(size, CL_MEM_READ_ONLY);
                inputDyBuf = context.allocateBuffer(size, CL_MEM_READ_ONLY);
                outputDxBuf = context.allocateBuffer(size, CL_MEM_WRITE_ONLY);
                outputDyBuf = context.allocateBuffer(size, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputDxBuf, inputDx);
                context.writeBuffer(inputDyBuf, inputDy);

                var kernel = context.getKernelManager().getKernel("distortion_filter", "mad_filter_grid_histogram");

                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(kernel, 0, stack.pointers(inputDxBuf));
                    clSetKernelArg(kernel, 1, stack.pointers(inputDyBuf));
                    clSetKernelArg(kernel, 2, stack.pointers(outputDxBuf));
                    clSetKernelArg(kernel, 3, stack.pointers(outputDyBuf));
                    clSetKernelArg(kernel, 4, stack.ints(gridWidth));
                    clSetKernelArg(kernel, 5, stack.ints(gridHeight));
                    clSetKernelArg(kernel, 6, stack.ints(halfWindow));
                    clSetKernelArg(kernel, 7, stack.floats(madThreshold));

                    // Histogram-based kernel with 512 bins uses ~2KB for histogram
                    // Can use 8x8 work groups
                    int localX = Math.min(8, gridWidth);
                    int localY = Math.min(8, gridHeight);
                    int globalX = ((gridWidth + localX - 1) / localX) * localX;
                    int globalY = ((gridHeight + localY - 1) / localY) * localY;
                    var globalSize = stack.pointers(globalX, globalY);
                    var localSize = stack.pointers(localX, localY);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue mad_filter_grid_histogram kernel: " + err);
                    }
                }

                context.finish();
                context.readBuffer(outputDxBuf, outputDx);
                context.readBuffer(outputDyBuf, outputDy);
            } finally {
                try {
                    context.finish();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] finish() failed in madFilterGPU");
                    e.printStackTrace();
                }
                safeRelease(context, inputDxBuf);
                safeRelease(context, inputDyBuf);
                safeRelease(context, outputDxBuf);
                safeRelease(context, outputDyBuf);
                try {
                    context.flush();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] flush() failed in madFilterGPU");
                    e.printStackTrace();
                }
            }
            return null;
        });

        unflatten2D(outputDx, gridDx);
        unflatten2D(outputDy, gridDy);
    }

    private void interpolateUnsampledGPU(OpenCLContext context, float[][] gridDx, float[][] gridDy,
                                         boolean[][] sampled, int gridWidth, int gridHeight, int searchRadius) {
        var flatDx = flatten2D(gridDx);
        var flatDy = flatten2D(gridDy);
        var flatSampled = flattenBooleanToInt(sampled);

        context.executeWithLock(() -> {
            long dxBuf = 0, dyBuf = 0, sampledBuf = 0;
            try {
                int floatSize = gridWidth * gridHeight * Float.BYTES;
                int intSize = gridWidth * gridHeight * Integer.BYTES;

                dxBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                dyBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                sampledBuf = context.allocateBuffer(intSize, CL_MEM_READ_ONLY);

                context.writeBuffer(dxBuf, flatDx);
                context.writeBuffer(dyBuf, flatDy);
                context.writeBufferInt(sampledBuf, flatSampled);

                var kernel = context.getKernelManager().getKernel("distortion_filter", "interpolate_unsampled");

                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(kernel, 0, stack.pointers(dxBuf));
                    clSetKernelArg(kernel, 1, stack.pointers(dyBuf));
                    clSetKernelArg(kernel, 2, stack.pointers(sampledBuf));
                    clSetKernelArg(kernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(kernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(kernel, 5, stack.ints(searchRadius));

                    // Use explicit local work size for consistency and to avoid driver issues
                    int localX = Math.min(16, gridWidth);
                    int localY = Math.min(16, gridHeight);
                    int globalX = ((gridWidth + localX - 1) / localX) * localX;
                    int globalY = ((gridHeight + localY - 1) / localY) * localY;

                    var globalSize = stack.pointers(globalX, globalY);
                    var localSize = stack.pointers(localX, localY);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue interpolate_unsampled kernel: " + err);
                    }
                }

                context.finish();
                context.readBuffer(dxBuf, flatDx);
                context.readBuffer(dyBuf, flatDy);
            } finally {
                try {
                    context.finish();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] finish() failed in interpolateUnsampledGPU");
                    e.printStackTrace();
                }
                safeRelease(context, dxBuf);
                safeRelease(context, dyBuf);
                safeRelease(context, sampledBuf);
                try {
                    context.flush();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] flush() failed in interpolateUnsampledGPU");
                    e.printStackTrace();
                }
            }
            return null;
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

        context.executeWithLock(() -> {
            long dxBuf = 0, dyBuf = 0, tempDxBuf = 0, tempDyBuf = 0, kernelBuf = 0;
            try {
                int floatSize = gridWidth * gridHeight * Float.BYTES;
                int kernelSize = gaussianKernel.length * Float.BYTES;

                dxBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                dyBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                tempDxBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                tempDyBuf = context.allocateBuffer(floatSize, CL_MEM_READ_WRITE);
                kernelBuf = context.allocateBuffer(kernelSize, CL_MEM_READ_ONLY);

                context.writeBuffer(dxBuf, flatDx);
                context.writeBuffer(dyBuf, flatDy);
                context.writeBuffer(kernelBuf, gaussianKernel);

                var hKernel = context.getKernelManager().getKernel("distortion_filter", "gaussian_smooth_horizontal");
                var vKernel = context.getKernelManager().getKernel("distortion_filter", "gaussian_smooth_vertical");

                try (var stack = MemoryStack.stackPush()) {
                    // Use explicit (1, 1) local size to avoid driver issues
                    var globalSize = stack.pointers(gridWidth, gridHeight);
                    var localSize = stack.pointers(1, 1);

                    // Horizontal pass for Dx
                    clSetKernelArg(hKernel, 0, stack.pointers(dxBuf));
                    clSetKernelArg(hKernel, 1, stack.pointers(tempDxBuf));
                    clSetKernelArg(hKernel, 2, stack.pointers(kernelBuf));
                    clSetKernelArg(hKernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(hKernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(hKernel, 5, stack.ints(kernelRadius));
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), hKernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_horizontal (Dx): " + err);
                    }

                    // Horizontal pass for Dy
                    clSetKernelArg(hKernel, 0, stack.pointers(dyBuf));
                    clSetKernelArg(hKernel, 1, stack.pointers(tempDyBuf));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), hKernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_horizontal (Dy): " + err);
                    }

                    // Vertical pass for Dx
                    clSetKernelArg(vKernel, 0, stack.pointers(tempDxBuf));
                    clSetKernelArg(vKernel, 1, stack.pointers(dxBuf));
                    clSetKernelArg(vKernel, 2, stack.pointers(kernelBuf));
                    clSetKernelArg(vKernel, 3, stack.ints(gridWidth));
                    clSetKernelArg(vKernel, 4, stack.ints(gridHeight));
                    clSetKernelArg(vKernel, 5, stack.ints(kernelRadius));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), vKernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_vertical (Dx): " + err);
                    }

                    // Vertical pass for Dy
                    clSetKernelArg(vKernel, 0, stack.pointers(tempDyBuf));
                    clSetKernelArg(vKernel, 1, stack.pointers(dyBuf));
                    err = clEnqueueNDRangeKernel(context.getCommandQueue(), vKernel, 2, null, globalSize, localSize, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue gaussian_smooth_vertical (Dy): " + err);
                    }
                }

                context.finish();
                context.readBuffer(dxBuf, flatDx);
                context.readBuffer(dyBuf, flatDy);
            } finally {
                try {
                    context.finish();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] finish() failed in gaussianSmoothGPU");
                    e.printStackTrace();
                }
                safeRelease(context, dxBuf);
                safeRelease(context, dyBuf);
                safeRelease(context, tempDxBuf);
                safeRelease(context, tempDyBuf);
                safeRelease(context, kernelBuf);
                try {
                    context.flush();
                } catch (Exception e) {
                    System.err.println("[DistortionGridFilter] flush() failed in gaussianSmoothGPU");
                    e.printStackTrace();
                }
            }
            return null;
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
                System.err.println("[DistortionGridFilter] Failed to release GPU buffer");
                e.printStackTrace();
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
