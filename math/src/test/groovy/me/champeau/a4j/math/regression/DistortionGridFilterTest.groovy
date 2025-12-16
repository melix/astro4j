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
package me.champeau.a4j.math.regression

import me.champeau.a4j.math.opencl.OpenCLContext
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Specification

class DistortionGridFilterTest extends Specification {

    def "MAD filter removes outliers correctly on CPU"() {
        given: "a grid with known outliers"
        int gridWidth = 10
        int gridHeight = 10
        def gridDx = createGridWithOutliers(gridWidth, gridHeight, 2.0f, 42)
        def gridDy = createGridWithOutliers(gridWidth, gridHeight, 1.5f, 123)

        when: "applying MAD filter"
        DistortionGridFilter.getInstance().madFilter(gridDx, gridDy, gridWidth, gridHeight, 2, 3.0f)

        then: "outliers should be replaced with median values"
        // After filtering, values should be more uniform (close to base value)
        def maxDx = 0.0f
        def maxDy = 0.0f
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                maxDx = Math.max(maxDx, Math.abs(gridDx[y][x] - 2.0f))
                maxDy = Math.max(maxDy, Math.abs(gridDy[y][x] - 1.5f))
            }
        }
        // Outliers (which were at 100.0) should have been removed
        maxDx < 10.0f
        maxDy < 10.0f
    }

    def "Gaussian smoothing reduces noise on CPU"() {
        given: "a noisy grid"
        int gridWidth = 20
        int gridHeight = 20
        def gridDx = createNoisyGrid(gridWidth, gridHeight, 5.0f, 1.0f, 42)
        def gridDy = createNoisyGrid(gridWidth, gridHeight, 3.0f, 0.5f, 123)

        // Compute initial variance
        def initialVarDx = computeVariance(gridDx, gridWidth, gridHeight)
        def initialVarDy = computeVariance(gridDy, gridWidth, gridHeight)

        when: "applying Gaussian smoothing"
        DistortionGridFilter.getInstance().gaussianSmooth(gridDx, gridDy, gridWidth, gridHeight, 1.5f)

        then: "variance should be reduced"
        def finalVarDx = computeVariance(gridDx, gridWidth, gridHeight)
        def finalVarDy = computeVariance(gridDy, gridWidth, gridHeight)
        finalVarDx < initialVarDx
        finalVarDy < initialVarDy
    }

    def "Interpolation fills unsampled points on CPU"() {
        given: "a grid with unsampled points"
        int gridWidth = 10
        int gridHeight = 10
        def gridDx = new float[gridHeight][gridWidth]
        def gridDy = new float[gridHeight][gridWidth]
        def sampled = new boolean[gridHeight][gridWidth]

        // Create a pattern where only some points are sampled
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if ((x + y) % 2 == 0) {
                    gridDx[y][x] = 2.0f
                    gridDy[y][x] = 1.0f
                    sampled[y][x] = true
                } else {
                    gridDx[y][x] = 0.0f
                    gridDy[y][x] = 0.0f
                    sampled[y][x] = false
                }
            }
        }

        when: "interpolating unsampled points"
        DistortionGridFilter.getInstance().interpolateUnsampled(gridDx, gridDy, sampled, gridWidth, gridHeight, 3)

        then: "unsampled points should have interpolated values close to neighbors"
        for (int y = 1; y < gridHeight - 1; y++) {
            for (int x = 1; x < gridWidth - 1; x++) {
                if (!sampled[y][x]) {
                    // Unsampled points should now have values close to 2.0 and 1.0
                    assert Math.abs(gridDx[y][x] - 2.0f) < 0.1f, "Dx at ($x, $y) = ${gridDx[y][x]}"
                    assert Math.abs(gridDy[y][x] - 1.0f) < 0.1f, "Dy at ($x, $y) = ${gridDy[y][x]}"
                }
            }
        }
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU and CPU MAD filter produce matching results"() {
        given: "a grid with outliers"
        int gridWidth = 50
        int gridHeight = 50
        def gridDxCpu = createGridWithOutliers(gridWidth, gridHeight, 2.0f, 42)
        def gridDyCpu = createGridWithOutliers(gridWidth, gridHeight, 1.5f, 123)
        def gridDxGpu = copyGrid(gridDxCpu)
        def gridDyGpu = copyGrid(gridDyCpu)

        when: "applying MAD filter on CPU and GPU"
        def filter = DistortionGridFilter.getInstance()

        // Force CPU
        madFilterCPU(filter, gridDxCpu, gridDyCpu, gridWidth, gridHeight, 2, 3.0f)

        // Force GPU (if available and grid is large enough)
        madFilterGPU(filter, gridDxGpu, gridDyGpu, gridWidth, gridHeight, 2, 3.0f)

        then: "results should match"
        int mismatches = 0
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (Math.abs(gridDxCpu[y][x] - gridDxGpu[y][x]) > 0.01f ||
                    Math.abs(gridDyCpu[y][x] - gridDyGpu[y][x]) > 0.01f) {
                    if (mismatches < 5) {
                        System.err.println("Mismatch at ($x, $y): CPU=(${gridDxCpu[y][x]}, ${gridDyCpu[y][x]}), GPU=(${gridDxGpu[y][x]}, ${gridDyGpu[y][x]})")
                    }
                    mismatches++
                }
            }
        }
        mismatches == 0
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU and CPU Gaussian smoothing produce matching results"() {
        given: "a noisy grid"
        int gridWidth = 50
        int gridHeight = 50
        def gridDxCpu = createNoisyGrid(gridWidth, gridHeight, 5.0f, 2.0f, 42)
        def gridDyCpu = createNoisyGrid(gridWidth, gridHeight, 3.0f, 1.0f, 123)
        def gridDxGpu = copyGrid(gridDxCpu)
        def gridDyGpu = copyGrid(gridDyCpu)

        when: "applying Gaussian smoothing on CPU and GPU"
        def filter = DistortionGridFilter.getInstance()

        // Force CPU
        gaussianSmoothCPU(filter, gridDxCpu, gridDyCpu, gridWidth, gridHeight, 1.5f)

        // Force GPU
        gaussianSmoothGPU(filter, gridDxGpu, gridDyGpu, gridWidth, gridHeight, 1.5f)

        then: "results should match within tolerance"
        int mismatches = 0
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (Math.abs(gridDxCpu[y][x] - gridDxGpu[y][x]) > 0.1f ||
                    Math.abs(gridDyCpu[y][x] - gridDyGpu[y][x]) > 0.1f) {
                    if (mismatches < 5) {
                        System.err.println("Mismatch at ($x, $y): CPU=(${gridDxCpu[y][x]}, ${gridDyCpu[y][x]}), GPU=(${gridDxGpu[y][x]}, ${gridDyGpu[y][x]})")
                    }
                    mismatches++
                }
            }
        }
        // Allow small number of edge mismatches due to boundary handling differences
        mismatches < (gridWidth * gridHeight * 0.02)
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU and CPU interpolation produce matching results"() {
        given: "a grid with unsampled points"
        int gridWidth = 50
        int gridHeight = 50
        def gridDxCpu = new float[gridHeight][gridWidth]
        def gridDyCpu = new float[gridHeight][gridWidth]
        def sampled = new boolean[gridHeight][gridWidth]

        def rng = new Random(42)
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (rng.nextFloat() > 0.3f) {
                    gridDxCpu[y][x] = 2.0f + rng.nextFloat() * 0.5f
                    gridDyCpu[y][x] = 1.0f + rng.nextFloat() * 0.3f
                    sampled[y][x] = true
                } else {
                    gridDxCpu[y][x] = 0.0f
                    gridDyCpu[y][x] = 0.0f
                    sampled[y][x] = false
                }
            }
        }

        def gridDxGpu = copyGrid(gridDxCpu)
        def gridDyGpu = copyGrid(gridDyCpu)

        when: "interpolating on CPU and GPU"
        def filter = DistortionGridFilter.getInstance()

        // Force CPU
        interpolateUnsampledCPU(filter, gridDxCpu, gridDyCpu, sampled, gridWidth, gridHeight, 3)

        // Force GPU
        interpolateUnsampledGPU(filter, gridDxGpu, gridDyGpu, sampled, gridWidth, gridHeight, 3)

        then: "results should match"
        int mismatches = 0
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (!sampled[y][x]) {
                    if (Math.abs(gridDxCpu[y][x] - gridDxGpu[y][x]) > 0.01f ||
                        Math.abs(gridDyCpu[y][x] - gridDyGpu[y][x]) > 0.01f) {
                        if (mismatches < 5) {
                            System.err.println("Mismatch at ($x, $y): CPU=(${gridDxCpu[y][x]}, ${gridDyCpu[y][x]}), GPU=(${gridDxGpu[y][x]}, ${gridDyGpu[y][x]})")
                        }
                        mismatches++
                    }
                }
            }
        }
        mismatches == 0
    }

    // Helper methods

    private static float[][] createGridWithOutliers(int width, int height, float baseValue, long seed) {
        def grid = new float[height][width]
        def rng = new Random(seed)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = baseValue + (rng.nextFloat() - 0.5f) * 0.5f
                // Add occasional outliers
                if (rng.nextFloat() < 0.05f) {
                    grid[y][x] = 100.0f * (rng.nextBoolean() ? 1 : -1)
                }
            }
        }
        return grid
    }

    private static float[][] createNoisyGrid(int width, int height, float baseValue, float noiseLevel, long seed) {
        def grid = new float[height][width]
        def rng = new Random(seed)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = baseValue + (float) (rng.nextGaussian() * noiseLevel)
            }
        }
        return grid
    }

    private static float[][] copyGrid(float[][] grid) {
        int height = grid.length
        int width = grid[0].length
        def copy = new float[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(grid[y], 0, copy[y], 0, width)
        }
        return copy
    }

    private static float computeVariance(float[][] grid, int width, int height) {
        float sum = 0, sumSq = 0
        int n = width * height
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sum += grid[y][x]
                sumSq += grid[y][x] * grid[y][x]
            }
        }
        float mean = sum / n
        return sumSq / n - mean * mean
    }

    // Methods to force CPU/GPU execution via reflection

    private static void madFilterCPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                      int gridWidth, int gridHeight, int halfWindow, float madThreshold) {
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("madFilterCPU",
                float[][].class, float[][].class, int.class, int.class, int.class, float.class)
            method.setAccessible(true)
            method.invoke(filter, gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke CPU method", e)
        }
    }

    private static void madFilterGPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                      int gridWidth, int gridHeight, int halfWindow, float madThreshold) {
        def context = OpenCLSupport.getContext()
        if (context == null) {
            // Fall back to CPU if no GPU
            madFilterCPU(filter, gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold)
            return
        }
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("madFilterGPU",
                    OpenCLContext.class, float[][].class, float[][].class,
                int.class, int.class, int.class, float.class)
            method.setAccessible(true)
            method.invoke(filter, context, gridDx, gridDy, gridWidth, gridHeight, halfWindow, madThreshold)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke GPU method", e)
        }
    }

    private static void gaussianSmoothCPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                          int gridWidth, int gridHeight, float sigma) {
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("gaussianSmoothCPU",
                float[][].class, float[][].class, int.class, int.class, float.class)
            method.setAccessible(true)
            method.invoke(filter, gridDx, gridDy, gridWidth, gridHeight, sigma)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke CPU method", e)
        }
    }

    private static void gaussianSmoothGPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                          int gridWidth, int gridHeight, float sigma) {
        def context = OpenCLSupport.getContext()
        if (context == null) {
            gaussianSmoothCPU(filter, gridDx, gridDy, gridWidth, gridHeight, sigma)
            return
        }
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("gaussianSmoothGPU",
                    OpenCLContext.class, float[][].class, float[][].class,
                int.class, int.class, float.class)
            method.setAccessible(true)
            method.invoke(filter, context, gridDx, gridDy, gridWidth, gridHeight, sigma)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke GPU method", e)
        }
    }

    private static void interpolateUnsampledCPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                                 boolean[][] sampled, int gridWidth, int gridHeight, int searchRadius) {
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("interpolateUnsampledCPU",
                float[][].class, float[][].class, boolean[][].class, int.class, int.class, int.class)
            method.setAccessible(true)
            method.invoke(filter, gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke CPU method", e)
        }
    }

    private static void interpolateUnsampledGPU(DistortionGridFilter filter, float[][] gridDx, float[][] gridDy,
                                                 boolean[][] sampled, int gridWidth, int gridHeight, int searchRadius) {
        def context = OpenCLSupport.getContext()
        if (context == null) {
            interpolateUnsampledCPU(filter, gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius)
            return
        }
        try {
            def method = DistortionGridFilter.class.getDeclaredMethod("interpolateUnsampledGPU",
                    OpenCLContext, float[][].class, float[][].class,
                boolean[][].class, int.class, int.class, int.class)
            method.setAccessible(true)
            method.invoke(filter, context, gridDx, gridDy, sampled, gridWidth, gridHeight, searchRadius)
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke GPU method", e)
        }
    }
}
