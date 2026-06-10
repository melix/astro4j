/*
 * Copyright 2023-2023 the original author or authors.
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
package me.champeau.a4j.math.correlation

import me.champeau.a4j.math.opencl.GPUImageCache
import me.champeau.a4j.math.opencl.OpenCLContext
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Specification

class CorrelationToolsTest extends Specification {

    def setup() {
        // GPU paths are gated on this property: without it the GPU tests
        // would silently compare CPU against CPU
        System.setProperty("opencl.enabled", "true")
    }

    def cleanup() {
        System.clearProperty("opencl.enabled")
    }

    def "CPU phase correlation detects known shift"() {
        given:
        def tileSize = 32
        def ref = createCenteredGaussianTile(tileSize)
        def target = createShiftedGaussianTile(tileSize, shiftX, shiftY)

        when:
        def result = CorrelationTools.correlationShiftFFTWithConfidence(ref, target, true)

        then:
        // Phase correlation returns the shift needed to align target with reference,
        // which is the negative of the shift we applied to create the target
        Math.abs(result.dy() + shiftY) < 1.5
        Math.abs(result.dx() + shiftX) < 1.5

        where:
        shiftX | shiftY
        0      | 0
        2      | 0
        0      | 2
        2      | 2
        -2     | 0
        0      | -2
        -2     | -2
        3      | 4
    }

    def "batched CPU correlation produces consistent results"() {
        given:
        def numTiles = 10
        def tileSize = 32
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def expectedShifts = []

        for (int i = 0; i < numTiles; i++) {
            def shiftX = (i % 5) - 2
            def shiftY = (i / 2).intValue() - 2
            refTiles[i] = createCenteredGaussianTile(tileSize)
            targetTiles[i] = createShiftedGaussianTile(tileSize, shiftX, shiftY)
            expectedShifts << [shiftX, shiftY]
        }

        when:
        def results = CorrelationTools.getInstance().batchedCorrelation(refTiles, targetTiles, true)

        then:
        results.length == numTiles
        for (int i = 0; i < numTiles; i++) {
            def dx = results[i][0]
            def dy = results[i][1]
            def expectedX = expectedShifts[i][0]
            def expectedY = expectedShifts[i][1]
            assert Math.abs(dx - expectedX) < 1.5, "Tile $i: expected dx=$expectedX, got dx=$dx"
            assert Math.abs(dy - expectedY) < 1.5, "Tile $i: expected dy=$expectedY, got dy=$dy"
        }
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU and CPU phase correlation produce matching results"() {
        given:
        def numTiles = 200  // Above MIN_TILES_FOR_GPU threshold
        def tileSize = 32
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def random = new Random(42)

        for (int i = 0; i < numTiles; i++) {
            def shiftX = random.nextInt(7) - 3  // Smaller shifts for reliability
            def shiftY = random.nextInt(7) - 3
            refTiles[i] = createCenteredGaussianTile(tileSize)
            targetTiles[i] = createShiftedGaussianTile(tileSize, shiftX, shiftY)
        }

        when:
        def cpuResults = computeCPU(refTiles, targetTiles)
        def gpuResults = computeGPU(refTiles, targetTiles)

        then:
        if (gpuResults == null) {
            // device exists but cannot run any of the GPU pipelines
            assert OpenCLSupport.getContext() == null
        } else {
            assert cpuResults.length == gpuResults.length

            int mismatches = 0
            for (int i = 0; i < numTiles; i++) {
                def cpuDx = cpuResults[i][0]
                def cpuDy = cpuResults[i][1]
                def cpuConfidence = cpuResults[i][2]
                def gpuDx = gpuResults[i][0]
                def gpuDy = gpuResults[i][1]
                def gpuConfidence = gpuResults[i][2]

                def diffX = Math.abs(cpuDx - gpuDx)
                def diffY = Math.abs(cpuDy - gpuDy)
                def diffConfidence = Math.abs(cpuConfidence - gpuConfidence)

                if (diffX > 0.5 || diffY > 0.5 || diffConfidence > 0.05) {
                    if (mismatches < 10) {
                        System.err.println("Tile $i mismatch: CPU=($cpuDx, $cpuDy, $cpuConfidence), GPU=($gpuDx, $gpuDy, $gpuConfidence)")
                    }
                    mismatches++
                }
            }

            if (mismatches > 0) {
                System.err.println("Total mismatches: $mismatches out of $numTiles tiles")
            }

            assert mismatches == 0, "GPU and CPU results differ in $mismatches out of $numTiles tiles"
        }
        true

        where:
        _ | _
        1 | 1  // Single test case, parameterized for clarity
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU phase correlation detects shifts correctly for 32x32 tiles"() {
        given:
        def numTiles = 200
        def tileSize = 32
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def expectedShifts = []

        for (int i = 0; i < numTiles; i++) {
            def shiftX = (i % 7) - 3
            def shiftY = ((i / 7).intValue() % 7) - 3
            refTiles[i] = createCenteredGaussianTile(tileSize)
            targetTiles[i] = createShiftedGaussianTile(tileSize, shiftX, shiftY)
            expectedShifts << [shiftX, shiftY]
        }

        when:
        def results = computeGPU(refTiles, targetTiles)

        then:
        if (results == null) {
            assert OpenCLSupport.getContext() == null
        } else {
            assert results.length == numTiles

            int errors = 0
            for (int i = 0; i < numTiles; i++) {
                def dx = results[i][0]
                def dy = results[i][1]
                def expectedX = expectedShifts[i][0]
                def expectedY = expectedShifts[i][1]

                if (Math.abs(dx - expectedX) > 1.0 || Math.abs(dy - expectedY) > 1.0) {
                    if (errors < 10) {
                        System.err.println("Tile $i: expected ($expectedX, $expectedY), got ($dx, $dy)")
                    }
                    errors++
                }
            }

            if (errors > 0) {
                System.err.println("Total errors: $errors out of $numTiles")
            }

            def maxAllowedErrors = (int) (numTiles * 0.05)
            assert errors <= maxAllowedErrors, "GPU phase correlation has $errors errors, max allowed is $maxAllowedErrors (5%)"
        }
        true
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU phase correlation detects shifts correctly for 64x64 tiles"() {
        given:
        def numTiles = 200
        def tileSize = 64
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def expectedShifts = []

        for (int i = 0; i < numTiles; i++) {
            def shiftX = (i % 7) - 3
            def shiftY = ((i / 7).intValue() % 7) - 3
            refTiles[i] = createCenteredGaussianTile(tileSize)
            targetTiles[i] = createShiftedGaussianTile(tileSize, shiftX, shiftY)
            expectedShifts << [shiftX, shiftY]
        }

        when:
        def cpuResults = computeCPU(refTiles, targetTiles)
        def gpuResults = computeGPU(refTiles, targetTiles)

        then:
        if (gpuResults == null) {
            assert OpenCLSupport.getContext() == null
        } else {
            assert gpuResults.length == numTiles

            int errors = 0
            for (int i = 0; i < numTiles; i++) {
                def gpuDx = gpuResults[i][0]
                def gpuDy = gpuResults[i][1]
                def gpuConfidence = gpuResults[i][2]
                def cpuDx = cpuResults[i][0]
                def cpuDy = cpuResults[i][1]
                def cpuConfidence = cpuResults[i][2]

                if (Math.abs(gpuDx - cpuDx) > 1.0 || Math.abs(gpuDy - cpuDy) > 1.0
                        || Math.abs(gpuConfidence - cpuConfidence) > 0.05) {
                    if (errors < 10) {
                        System.err.println("Tile $i: CPU=($cpuDx, $cpuDy, $cpuConfidence), GPU=($gpuDx, $gpuDy, $gpuConfidence)")
                    }
                    errors++
                }
            }

            if (errors > 0) {
                System.err.println("Total errors: $errors out of $numTiles")
            }

            def maxAllowedErrors = (int) (numTiles * 0.05)
            assert errors <= maxAllowedErrors, "GPU 64x64 phase correlation has $errors errors, max allowed is $maxAllowedErrors (5%)"
        }
        true
    }


    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU and CPU NCC produce matching results for 32x32 tiles"() {
        given:
        def numTiles = 200
        def tileSize = 32
        def refTiles = new float[numTiles][tileSize][tileSize]
        def targetTiles = new float[numTiles][tileSize][tileSize]
        def random = new Random(42)

        for (int i = 0; i < numTiles; i++) {
            def shiftX = random.nextInt(7) - 3
            def shiftY = random.nextInt(7) - 3
            refTiles[i] = createCenteredGaussianTile(tileSize)
            targetTiles[i] = createShiftedGaussianTile(tileSize, shiftX, shiftY)
        }

        when:
        def cpuResults = computeNCCCPU(refTiles, targetTiles)
        def gpuResults = computeNCCGPU(refTiles, targetTiles)

        then:
        if (gpuResults == null) {
            assert OpenCLSupport.getContext() == null
        } else {
            assert compareDisplacements("NCC", cpuResults, gpuResults, numTiles) == 0
        }
        true
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "resident NCC matches batched NCC for 32x32 tiles"() {
        given:
        def context = OpenCLSupport.getContext()
        def tileSize = 32
        def width = 512
        def height = 512
        def refImage = createTestImageData(width, height, 0, 0)
        def targetImage = createTestImageData(width, height, 2, -1)

        def positions = []
        for (int cy = 32; cy <= height - 32; cy += 32) {
            for (int cx = 32; cx <= width - 32; cx += 32) {
                positions << ([cx, cy] as int[])
            }
        }
        def tilePositions = positions as int[][]
        def numTiles = tilePositions.length

        def refTiles = extractTiles(refImage, width, tilePositions, tileSize)
        def targetTiles = extractTiles(targetImage, width, tilePositions, tileSize)

        when:
        def residentResults = null
        def images = [refImage, targetImage]
        def cache = new GPUImageCache(context, 2, width, height, { idx -> images[idx] })
        try {
            def refBuffer = cache.getImage(0)
            def targetBuffer = cache.getImage(1)
            residentResults = CorrelationTools.getInstance().correlateResidentImagesNCC(
                    context, refBuffer, targetBuffer, width, height, tilePositions, tileSize)
        } finally {
            cache.close()
        }
        def cpuResults = computeNCCCPU(refTiles, targetTiles)

        then:
        compareDisplacements("resident NCC", cpuResults, residentResults, numTiles) == 0
    }

    private static float[][] createBaseTile(int size) {
        def tile = new float[size][size]
        def center = size / 2.0
        def random = new Random(12345)

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                def dx = x - center
                def dy = y - center
                def dist = Math.sqrt(dx * dx + dy * dy)
                tile[y][x] = (float) (1000 * Math.exp(-dist * dist / 25.0) +
                        200 * Math.sin(x * 0.3) * Math.cos(y * 0.3) +
                        100 * random.nextGaussian())
            }
        }
        return tile
    }

    private static float[][] createCenteredGaussianTile(int size) {
        return createBaseTile(size)
    }

    private static float[][] createShiftedGaussianTile(int size, int shiftX, int shiftY) {
        def base = createBaseTile(size)
        def tile = new float[size][size]

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                def srcX = x - shiftX
                def srcY = y - shiftY
                if (srcX >= 0 && srcX < size && srcY >= 0 && srcY < size) {
                    tile[y][x] = base[srcY][srcX]
                } else {
                    tile[y][x] = 0.0f
                }
            }
        }
        return tile
    }

    private static float[][] createTestTile(int size, int shiftX, int shiftY) {
        if (shiftX == 0 && shiftY == 0) {
            return createCenteredGaussianTile(size)
        }
        return createShiftedGaussianTile(size, shiftX, shiftY)
    }

    private static float[][] computeCPU(float[][][] refTiles, float[][][] targetTiles) {
        int n = refTiles.length
        def results = new float[n][3]
        for (int i = 0; i < n; i++) {
            def shift = CorrelationTools.correlationShiftFFTWithConfidence(refTiles[i], targetTiles[i], true)
            results[i][0] = (float) -shift.dx()
            results[i][1] = (float) -shift.dy()
            results[i][2] = (float) shift.confidence()
        }
        return results
    }

    private static float[] createTestImageData(int width, int height, int shiftX, int shiftY) {
        def data = new float[width * height]
        def random = new Random(98765)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def sx = x - shiftX
                def sy = y - shiftY
                data[y * width + x] = (float) (1000 * Math.sin(sx * 0.13) * Math.cos(sy * 0.11) +
                        500 * Math.sin(sx * 0.31 + sy * 0.17) +
                        100 * random.nextGaussian())
            }
        }
        return data
    }

    private static float[][][] extractTiles(float[] image, int width, int[][] positions, int tileSize) {
        def half = tileSize.intdiv(2)
        def tiles = new float[positions.length][tileSize][tileSize]
        for (int i = 0; i < positions.length; i++) {
            int cornerX = positions[i][0] - half
            int cornerY = positions[i][1] - half
            for (int y = 0; y < tileSize; y++) {
                for (int x = 0; x < tileSize; x++) {
                    tiles[i][y][x] = image[(cornerY + y) * width + cornerX + x]
                }
            }
        }
        return tiles
    }

    private static int compareDisplacements(String label, float[][] cpuResults, float[][] gpuResults, int numTiles) {
        assert gpuResults.length == numTiles
        int mismatches = 0
        for (int i = 0; i < numTiles; i++) {
            def diffX = Math.abs(cpuResults[i][0] - gpuResults[i][0])
            def diffY = Math.abs(cpuResults[i][1] - gpuResults[i][1])
            def diffConfidence = Math.abs(cpuResults[i][2] - gpuResults[i][2])
            if (diffX > 0.5 || diffY > 0.5 || diffConfidence > 0.05) {
                if (mismatches < 10) {
                    System.err.println("$label tile $i mismatch: CPU=(${cpuResults[i][0]}, ${cpuResults[i][1]}, ${cpuResults[i][2]}), GPU=(${gpuResults[i][0]}, ${gpuResults[i][1]}, ${gpuResults[i][2]})")
                }
                mismatches++
            }
        }
        return mismatches
    }

    private static float[][] computeNCCCPU(float[][][] refTiles, float[][][] targetTiles) {
        def method = CorrelationTools.class.getDeclaredMethod("batchedNCCCPU",
                float[][][].class, float[][][].class)
        method.setAccessible(true)
        return (float[][]) method.invoke(CorrelationTools.getInstance(), refTiles, targetTiles)
    }

    private static float[][] computeNCCGPU(float[][][] refTiles, float[][][] targetTiles) {
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            return null
        }
        def method = CorrelationTools.class.getDeclaredMethod("batchedNCCGPU",
                OpenCLContext, float[][][].class, float[][][].class)
        method.setAccessible(true)
        return (float[][]) method.invoke(CorrelationTools.getInstance(), context, refTiles, targetTiles)
    }

    private static float[][] computeGPU(float[][][] refTiles, float[][][] targetTiles) {
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            return null
        }

        def instance = CorrelationTools.getInstance()

        // Force GPU path by directly calling GPU method via reflection
        try {
            def method = CorrelationTools.class.getDeclaredMethod("batchedCorrelationGPU",
                    OpenCLContext, float[][][].class, float[][][].class, boolean.class)
            method.setAccessible(true)
            return (float[][]) method.invoke(instance, context, refTiles, targetTiles, true)
        } catch (Exception e) {
            System.err.println("Failed to invoke GPU method: " + e.getMessage())
            e.printStackTrace()
            return null
        }
    }
}
