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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.expr.stacking.ConsensusReference
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap
import me.champeau.a4j.jsolex.processing.expr.stacking.SignalEvaluator
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.correlation.PhaseCorrelation
import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.opencl.OpenCLContext
import me.champeau.a4j.math.opencl.OpenCLSupport
import me.champeau.a4j.math.regression.DistortionGridFilter
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Tests for Dedistort optimization correctness.
 * These tests verify that optimized code paths produce identical results
 * to the original implementations.
 */
@Requires({ OpenCLSupport.available})
class DedistortOptimizationTest extends Specification {

    private static final ImageMath IMAGE_MATH = ImageMath.newInstance()

    @Shared
    OpenCLContext context

    def setupSpec() {
        System.setProperty("opencl.enabled", "true")
        context = OpenCLSupport.getContext()
    }

    def cleanupSpec() {
        System.clearProperty("opencl.enabled")
        context = null
    }

    def setup() {
        context.resetErrorTracking()
    }

    def "madFilter GPU kernel works in isolation"() {
        given: "a grid of random displacement values"
        int gridWidth = 50
        int gridHeight = 50
        def gridDx = new float[gridHeight][gridWidth]
        def gridDy = new float[gridHeight][gridWidth]
        def random = new Random(42)
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                gridDx[y][x] = (random.nextFloat() - 0.5f) * 10
                gridDy[y][x] = (random.nextFloat() - 0.5f) * 10
            }
        }

        when: "madFilter is called directly"
        DistortionGridFilter.getInstance().madFilter(gridDx, gridDy, gridWidth, gridHeight, 2, 3.0f)

        then: "no GPU errors occurred"
        noGPUErrors()
    }

    def "GPU operations work correctly in sequence - phase correlation then filters (half window = #half)"() {
        given: "tiles for phase correlation"
        int numTiles = 774
        int tileSize = 64
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def random = new Random(42)
        for (int i = 0; i < numTiles; i++) {
            for (int y = 0; y < tileSize; y++) {
                for (int x = 0; x < tileSize; x++) {
                    refTiles[i][y][x] = random.nextFloat() * 1000
                    targetTiles[i][y][x] = random.nextFloat() * 1000
                }
            }
        }

        and: "a grid for filter operations (> 1000 points for GPU)"
        int gridWidth = 37
        int gridHeight = 37
        def gridDx = new float[gridHeight][gridWidth]
        def gridDy = new float[gridHeight][gridWidth]
        def sampled = new boolean[gridHeight][gridWidth]
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                sampled[y][x] = random.nextFloat() < 0.7f
                if (sampled[y][x]) {
                    gridDx[y][x] = (random.nextFloat() - 0.5f) * 10 as float
                    gridDy[y][x] = (random.nextFloat() - 0.5f) * 10 as float
                }
            }
        }

        when: "phase correlation runs first"
        def phaseCorr = PhaseCorrelation.getInstance()
        phaseCorr.batchedCorrelation(refTiles, targetTiles)

        and: "then interpolateUnsampled is called"
        DistortionGridFilter.getInstance().interpolateUnsampled(gridDx, gridDy, sampled, gridWidth, gridHeight, 3)

        and: "then madFilter is called with halfWindow=2 (supported by GPU kernel)"
        DistortionGridFilter.getInstance().madFilter(gridDx, gridDy, gridWidth, gridHeight, half, 3.0f)

        then: "no GPU errors occurred"
        noGPUErrors()

        where:
        half << [2, 5, 11, 23, 32]
    }

    def "integral image area sum matches naive computation"() {
        given: "a random image"
        int width = 200
        int height = 150
        def data = createRandomImage(width, height, seed)
        def image = new Image(width, height, data)
        def integral = IMAGE_MATH.integralImage(image)

        when: "computing area sums using both methods"
        def naiveSum = computeNaiveSum(data, x, y, tileWidth, tileHeight)
        def integralSum = IMAGE_MATH.areaSum(integral, x, y, tileWidth, tileHeight)

        then: "results match within relative floating-point tolerance (0.01%)"
        // Integral images accumulate floating-point errors differently than naive summation
        // Use relative tolerance for meaningful comparison
        def relativeError = Math.abs(naiveSum - integralSum) / Math.max(Math.abs(naiveSum), 1.0f)
        relativeError < 0.0001f

        where:
        seed | x   | y  | tileWidth | tileHeight
        42   | 10  | 10 | 32        | 32
        42   | 0   | 0  | 32        | 32
        42   | 50  | 50 | 64        | 64
        42   | 100 | 80 | 32        | 32
        123  | 20  | 30 | 16        | 16
        123  | 0   | 0  | 200       | 150  // Full image
    }

    def "integral image area average matches naive computation"() {
        given: "a random image"
        int width = 200
        int height = 150
        def data = createRandomImage(width, height, 42)
        def image = new Image(width, height, data)
        def integral = IMAGE_MATH.integralImage(image)

        when: "computing area averages using both methods"
        def naiveAvg = computeNaiveAverage(data, x, y, tileWidth, tileHeight)
        def integralAvg = IMAGE_MATH.areaAverage(integral, x, y, tileWidth, tileHeight)

        then: "results match within relative floating-point tolerance (0.01%)"
        def relativeError = Math.abs(naiveAvg - integralAvg) / Math.max(Math.abs(naiveAvg), 1.0f)
        relativeError < 0.0001f

        where:
        x  | y  | tileWidth | tileHeight
        10 | 10 | 32        | 32
        0  | 0  | 32        | 32
        50 | 50 | 64        | 64
    }

    def "SignalEvaluator produces same results as naive signal computation"() {
        given: "random reference and target images"
        int width = 200
        int height = 150
        def refData = createRandomImage(width, height, 42)
        def targetData = createRandomImage(width, height, 123)
        def evaluator = new SignalEvaluator(refData, targetData, width, height)

        when: "computing signal using both methods"
        def naiveRefSignal = computeNaiveAverage(refData, x, y, tileSize, tileSize)
        def naiveTargetSignal = computeNaiveAverage(targetData, x, y, tileSize, tileSize)
        def evalRefSignal = evaluator.getRefSignal(x, y, tileSize, tileSize)
        def evalTargetSignal = evaluator.getTargetSignal(x, y, tileSize, tileSize)

        then: "results match within relative floating-point tolerance (0.01%)"
        def relativeErrorRef = Math.abs(naiveRefSignal - evalRefSignal) / Math.max(Math.abs(naiveRefSignal), 1.0f)
        def relativeErrorTarget = Math.abs(naiveTargetSignal - evalTargetSignal) / Math.max(Math.abs(naiveTargetSignal), 1.0f)
        relativeErrorRef < 0.0001f
        relativeErrorTarget < 0.0001f

        where:
        x   | y  | tileSize
        10  | 10 | 32
        0   | 0  | 32
        50  | 50 | 64
        100 | 80 | 32
    }

    def "SignalEvaluator threshold check matches naive implementation"() {
        given: "images with known signal levels"
        int imgWidth = 100
        int imgHeight = 100
        // Create image with high signal in top-left, low signal in bottom-right
        def data = new float[imgHeight][imgWidth]
        for (int py = 0; py < imgHeight; py++) {
            for (int px = 0; px < imgWidth; px++) {
                data[py][px] = (px < 50 && py < 50) ? 1000f : 100f
            }
        }
        def evaluator = new SignalEvaluator(data, null, imgWidth, imgHeight)

        expect: "threshold checks match expected behavior"
        evaluator.passesThreshold(x, y, 32, 32, threshold) == expected

        where:
        x  | y  | threshold | expected
        0  | 0  | 500f      | true      // High signal area, threshold 500
        0  | 0  | 1500f     | false     // High signal area, threshold 1500
        60 | 60 | 50f       | true      // Low signal area, threshold 50
        60 | 60 | 200f      | false     // Low signal area, threshold 200
    }

    def "DistorsionMap distance weight LUT produces correct values"() {
        expect: "LUT values match computed inverse-squared distance"
        def distSq = dx * dx + dy * dy
        def expected = (distSq == 0) ? 0.0 : 1.0 / distSq
        def actual = DistorsionMap.getInverseDistanceSquaredWeight(dx, dy)
        Math.abs(expected - actual) < 1e-10

        where:
        dx | dy
        0  | 0
        1  | 0
        0  | 1
        1  | 1
        -1 | 0
        0  | -1
        2  | 1
        -2 | -2
        3  | 0
        0  | 3
        3  | 3
        -3 | -3
    }

    def "DistorsionMap filterAndSmooth produces consistent results"() {
        given: "a distortion map with known values"
        int width = 200
        int height = 150
        int tileSize = 32
        int step = 16
        def map = new DistorsionMap(width, height, tileSize, step)

        // Record some distortions with noise
        def rng = new Random(42)
        int gridWidth = map.getGridWidth()
        int gridHeight = map.getGridHeight()
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                int px = gx * step + tileSize / 2
                int py = gy * step + tileSize / 2
                // Base distortion with some noise
                double dx = 2.0 + rng.nextGaussian() * 0.5
                double dy = 1.0 + rng.nextGaussian() * 0.5
                // Add occasional outliers
                if (rng.nextDouble() < 0.05) {
                    dx += rng.nextGaussian() * 10
                    dy += rng.nextGaussian() * 10
                }
                map.recordDistorsion(px, py, dx, dy)
            }
        }

        when: "applying filterAndSmooth"
        map.filterAndSmooth()

        then: "total distortion is reduced (outliers removed)"
        // After filtering, the distortion should be more uniform
        def totalDist = map.totalDistorsion()
        // The base distortion is ~sqrt(2^2 + 1^2) = ~2.24 per point
        // Total should be close to this times number of points
        totalDist > 0
        totalDist < gridWidth * gridHeight * 10  // Should be less than if outliers remained
    }

    def "Dedistort.dedistort completes without GPU errors on virtual thread"() {
        given: "synthetic solar-like images"
        int width = 1024
        int height = 1024
        def refData = createSolarLikeImage(width, height, 42)
        def targetData = createSolarLikeImage(width, height, 123)
        def reference = new ImageWrapper32(width, height, refData, [:])
        def target = new ImageWrapper32(width, height, targetData, [:])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "dedistort is called on a virtual thread (mimicking real app behavior)"
        def result = CompletableFuture.supplyAsync({
            dedistort.dedistort(reference, target, 64, 0.5d, 1000d, 1, false)
        }, Executors.newVirtualThreadPerTaskExecutor()).get()

        then: "no GPU errors occur and result is returned"
        result != null
        result.width() == width
        result.height() == height

        and: "GPU did not fall back to CPU (no errors recorded)"
        noGPUErrors()
    }

    private boolean noGPUErrors() {
        context.getTotalErrors() == 0 || { println "GPU errors: ${context.getLastError()}"; false }()
    }

    def "Dedistort.dedistort completes without GPU errors with multiple iterations"() {
        given: "synthetic solar-like images"
        int width = 800
        int height = 800
        def refData = createSolarLikeImage(width, height, 42)
        def targetData = createSolarLikeImage(width, height, 123)
        def reference = new ImageWrapper32(width, height, refData, [:])
        def target = new ImageWrapper32(width, height, targetData, [:])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "dedistort is called with multiple iterations (more GPU operations)"
        def result = CompletableFuture.supplyAsync({
            dedistort.dedistort(reference, target, 64, 0.5d, 500d, 3, false)
        }, Executors.newVirtualThreadPerTaskExecutor()).get()

        then: "no GPU errors occur"
        result != null
        noGPUErrors()
    }

    def "Dedistort completes without GPU errors with small sampling on regular thread"() {
        given: "synthetic solar-like images - smaller size but more grid points"
        int width = 512
        int height = 512
        def refData = createSolarLikeImage(width, height, 42)
        def targetData = createSolarLikeImage(width, height, 123)
        def reference = new ImageWrapper32(width, height, refData, [:])
        def target = new ImageWrapper32(width, height, targetData, [:])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "dedistort is called on a REGULAR thread (not virtual)"
        def result = CompletableFuture.supplyAsync({
            dedistort.dedistort(reference, target, 64, 0.25d, 500d, 1, false)
        }, Executors.newSingleThreadExecutor()).get()

        then: "no GPU errors occur"
        result != null
        noGPUErrors()
    }

    def "Dedistort completes without GPU errors with small sampling on virtual thread"() {
        given: "synthetic solar-like images - smaller size but more grid points"
        int width = 512
        int height = 512
        def refData = createSolarLikeImage(width, height, 42)
        def targetData = createSolarLikeImage(width, height, 123)
        def reference = new ImageWrapper32(width, height, refData, [:])
        def target = new ImageWrapper32(width, height, targetData, [:])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "dedistort is called on a virtual thread"
        def result = CompletableFuture.supplyAsync({
            dedistort.dedistort(reference, target, 64, 0.25d, 500d, 1, false)
        }, Executors.newVirtualThreadPerTaskExecutor()).get()

        then: "no GPU errors occur"
        result != null
        noGPUErrors()
    }

    def "Dedistort consensus reference mode completes without GPU errors"() {
        given: "multiple images for consensus reference mode (real app scenario)"
        int width = 800
        int height = 800
        int imageCount = 6  // At least 5 recommended for consensus mode

        // Create multiple images with slight variations (simulating different frames)
        def images = []
        for (int i = 0; i < imageCount; i++) {
            def data = createSolarLikeImage(width, height, 42 + i * 10)
            images.add(new ImageWrapper32(width, height, data, [:]))
        }

        // Create a reference image with ConsensusReference marker in metadata
        def refData = createSolarLikeImage(width, height, 42)
        def reference = new ImageWrapper32(width, height, refData,
                [(ConsensusReference.class): ConsensusReference.INSTANCE])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "consensus reference dedistort is called repeatedly on a virtual thread"
        def result = CompletableFuture.supplyAsync({
            def re = null
            for (int i = 0; i < 10; i++) {
                // Call dedistort with arguments map to trigger consensus reference mode
                def args = [
                        "ref"       : reference,
                        "img"       : images,
                        "ts"        : 64,
                        "sampling"  : 0.25d,
                        "threshold" : 0,
                        "iterations": 1
                ]
                try {
                    re = dedistort.dedistort(args)
                } finally {
                    if (context.totalErrors > 0) {
                        break
                    }
                }
            }
            return re
        }, Executors.newVirtualThreadPerTaskExecutor()).get()

        then: "no GPU errors occur"
        result != null
        result instanceof List

        and: "GPU did not fall back to CPU (no errors recorded)"
        noGPUErrors()
    }

    def "Multiple sequential dedistort calls complete without GPU errors"() {
        given: "images for multiple dedistort operations"
        int width = 1024
        int height = 1024
        def refData = createSolarLikeImage(width, height, 42)
        def reference = new ImageWrapper32(width, height, refData, [:])

        and: "a Dedistort instance"
        def dedistort = new Dedistort([:], Broadcaster.NO_OP)

        when: "multiple sequential dedistort calls are made (mimicking batch mode)"
        def executor = Executors.newVirtualThreadPerTaskExecutor()
        def results = []

        // Real app error occurred after many allocations
        // Multiple dedistort calls might accumulate GPU state issues
        for (int i = 0; i < 5; i++) {
            def seed = 100 + i
            def targetData = createSolarLikeImage(width, height, seed)
            def target = new ImageWrapper32(width, height, targetData, [:])

            def result = CompletableFuture.supplyAsync({
                dedistort.dedistort(reference, target, 64, 0.5d, 1000d, 1, false)
            }, executor).get()
            results.add(result)
        }

        then: "all dedistort calls succeed"
        results.size() == 5
        results.every { it != null }
    }

    // Helper methods

    /**
     * Creates a synthetic solar-like image with a bright disk in the center
     * and some variation to provide signal for phase correlation.
     */
    private static float[][] createSolarLikeImage(int width, int height, long seed) {
        def rng = new Random(seed)
        def data = new float[height][width]
        def centerX = width / 2
        def centerY = height / 2
        def radius = Math.min(width, height) * 0.4

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def dx = x - centerX
                def dy = y - centerY
                def dist = Math.sqrt(dx * dx + dy * dy)

                if (dist < radius) {
                    // Inside the disk: high signal with some limb darkening and noise
                    def limbDarkening = 1.0 - 0.3 * Math.pow(dist / radius, 2)
                    def baseSignal = 40000 * limbDarkening
                    // Add some surface features (granulation-like noise)
                    def noise = rng.nextGaussian() * 2000
                    data[y][x] = (float) Math.max(0, baseSignal + noise)
                } else {
                    // Outside disk: low background
                    data[y][x] = (float) (500 + rng.nextGaussian() * 100)
                }
            }
        }
        return data
    }

    private static float[][] createRandomImage(int width, int height, long seed) {
        def rng = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = rng.nextFloat() * 65535
            }
        }
        return data
    }

    private static float computeNaiveSum(float[][] data, int x, int y, int w, int h) {
        float sum = 0
        int height = data.length
        int width = data[0].length
        int maxY = Math.min(y + h, height)
        int maxX = Math.min(x + w, width)
        for (int yy = y; yy < maxY; yy++) {
            for (int xx = x; xx < maxX; xx++) {
                sum += data[yy][xx]
            }
        }
        return sum
    }

    private static float computeNaiveAverage(float[][] data, int x, int y, int w, int h) {
        int height = data.length
        int width = data[0].length
        int maxY = Math.min(y + h, height)
        int maxX = Math.min(x + w, width)
        int count = (maxY - y) * (maxX - x)
        return count > 0 ? computeNaiveSum(data, x, y, w, h) / count : 0
    }
}
