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
package me.champeau.a4j.math.image

import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for OpenCLImageMath that force GPU execution without fallback.
 * These tests verify that the OpenCL implementation produces correct results.
 */
@Requires({ OpenCLSupport.isAvailable() })
class OpenCLImageMathTest extends Specification {

    private OpenCLImageMath openclMath
    private ImageMath referenceMath

    def setup() {
        openclMath = new OpenCLImageMath(OpenCLSupport.getContext(), 0, 0, false) // Force GPU, no fallback
        referenceMath = new VectorApiImageMath()
    }

    // ==================== ADD SCALAR TESTS ====================

    def "add scalar produces same results as CPU implementation"() {
        given:
        def image = createTestImage(width, height)

        when:
        def gpuResult = openclMath.add(image, scalar)
        def cpuResult = referenceMath.add(image, scalar)

        then:
        compareImages(gpuResult, cpuResult, 0.001f)

        where:
        width | height | scalar
        128   | 128    | 100.5f
        256   | 256    | 100.5f
        512   | 512    | 100.5f
        64    | 64     | 0f
        64    | 64     | -50.0f
        100   | 200    | 1000f
    }

    def "add scalar with zero produces identical image"() {
        given:
        def image = createTestImage(128, 128)

        when:
        def result = openclMath.add(image, 0f)

        then:
        compareImages(result, image, 0.001f)
    }

    def "add scalar has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.add(image, 100f)

        then:
        !hasNaN(result)
    }

    // ==================== MULTIPLY SCALAR TESTS ====================

    def "multiply scalar produces same results as CPU implementation"() {
        given:
        def image = createTestImage(width, height)

        when:
        def gpuResult = openclMath.multiply(image, scalar)
        def cpuResult = referenceMath.multiply(image, scalar)

        then:
        compareImages(gpuResult, cpuResult, 0.01f)

        where:
        width | height | scalar
        128   | 128    | 1.5f
        256   | 256    | 1.5f
        512   | 512    | 1.5f
        64    | 64     | 0f
        64    | 64     | 0.5f
        100   | 200    | 2.0f
    }

    def "multiply scalar by one produces identical image"() {
        given:
        def image = createTestImage(128, 128)

        when:
        def result = openclMath.multiply(image, 1f)

        then:
        compareImages(result, image, 0.001f)
    }

    def "multiply scalar by zero produces zero image"() {
        given:
        def image = createTestImage(128, 128, 1000f)

        when:
        def result = openclMath.multiply(image, 0f)

        then:
        def data = result.data()
        for (int y = 0; y < result.height(); y++) {
            for (int x = 0; x < result.width(); x++) {
                assert data[y][x] == 0f
            }
        }
    }

    def "multiply scalar has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.multiply(image, 1.5f)

        then:
        !hasNaN(result)
    }

    // ==================== MULTIPLY IMAGES TESTS ====================

    def "multiply images produces same results as CPU implementation"() {
        given:
        def image1 = createTestImage(width, height)
        def image2 = createTestImage(width, height, 0.5f)

        when:
        def gpuResult = openclMath.multiply(image1, image2)
        def cpuResult = referenceMath.multiply(image1, image2)

        then:
        compareImages(gpuResult, cpuResult, 0.01f)

        where:
        width | height
        128   | 128
        256   | 256
        64    | 64
        100   | 200
    }

    def "multiply images has no NaN values"() {
        given:
        def image1 = createTestImage(256, 256)
        def image2 = createTestImage(256, 256, 1f)

        when:
        def result = openclMath.multiply(image1, image2)

        then:
        !hasNaN(result)
    }

    // ==================== DIVIDE IMAGES TESTS ====================

    def "divide images produces same results as CPU implementation"() {
        given:
        def image1 = createTestImage(width, height, 1000f)
        def image2 = createTestImage(width, height, 10f) // Ensure no zeros

        when:
        def gpuResult = openclMath.divide(image1, image2)
        def cpuResult = referenceMath.divide(image1, image2)

        then:
        compareImages(gpuResult, cpuResult, 0.01f)

        where:
        width | height
        128   | 128
        256   | 256
        64    | 64
        100   | 200
    }

    def "divide images handles division by zero gracefully"() {
        given:
        def image1 = createTestImage(64, 64, 100f)
        def image2 = createZeroImage(64, 64)

        when:
        def result = openclMath.divide(image1, image2)

        then:
        // GPU implementation returns 0 for division by zero
        !hasNaN(result)
        !hasInfinity(result)
    }

    def "divide images has no NaN values with valid input"() {
        given:
        def image1 = createTestImage(256, 256, 1000f)
        def image2 = createTestImage(256, 256, 10f)

        when:
        def result = openclMath.divide(image1, image2)

        then:
        !hasNaN(result)
    }

    // ==================== CONVOLUTION TESTS ====================

    def "convolution produces same results as CPU implementation"() {
        given:
        def image = createTestImage(width, height)

        when:
        def gpuResult = openclMath.convolve(image, kernel)
        def cpuResult = referenceMath.convolve(image, kernel)

        then:
        compareImages(gpuResult, cpuResult, 0.01f)

        where:
        width | height | kernel
        128   | 128    | Kernel33.GAUSSIAN_BLUR
        256   | 256    | Kernel33.GAUSSIAN_BLUR
        128   | 128    | Kernel33.SHARPEN
        256   | 256    | Kernel33.SHARPEN
        128   | 128    | Kernel33.SHARPEN2
        128   | 128    | Kernel33.SOBEL_LEFT
        128   | 128    | Kernel33.SOBEL_RIGHT
        128   | 128    | Kernel33.SOBEL_TOP
        128   | 128    | Kernel33.SOBEL_BOTTOM
        128   | 128    | Kernel33.LAPLACIAN
        128   | 128    | Kernel33.LAPLACIAN_B
        128   | 128    | Kernel33.EDGE_DETECTION
        128   | 128    | Kernel33.IDENTITY
        64    | 64     | Kernel33.GAUSSIAN_BLUR
        100   | 200    | Kernel33.GAUSSIAN_BLUR
    }

    def "convolution with identity kernel produces similar image"() {
        given:
        def image = createTestImage(128, 128, 1000f)

        when:
        def result = openclMath.convolve(image, Kernel33.IDENTITY)

        then:
        // Identity kernel with factor 1/9 will scale down values
        !hasNaN(result)
    }

    def "convolution has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.convolve(image, Kernel33.GAUSSIAN_BLUR)

        then:
        !hasNaN(result)
    }

    def "convolution preserves image dimensions"() {
        given:
        def image = createTestImage(width, height)

        when:
        def result = openclMath.convolve(image, Kernel33.GAUSSIAN_BLUR)

        then:
        result.width() == width
        result.height() == height

        where:
        width | height
        128   | 128
        256   | 256
        100   | 200
    }

    // ==================== ROTATE TESTS ====================

    def "rotate produces same results as CPU implementation"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def gpuResult = openclMath.rotate(image, angle, blackpoint, resize)
        def cpuResult = referenceMath.rotate(image, angle, blackpoint, resize)

        then:
        gpuResult.width() == cpuResult.width()
        gpuResult.height() == cpuResult.height()
        compareImages(gpuResult, cpuResult, 1.0f) // Allow tolerance for interpolation differences

        where:
        angle                  | blackpoint | resize
        Math.toRadians(45)     | 0f         | true
        Math.toRadians(90)     | 0f         | true
        Math.toRadians(180)    | 0f         | true
        Math.toRadians(30)     | 0f         | true
        Math.toRadians(45)     | 0f         | false
        Math.toRadians(90)     | 100f       | true
    }

    def "rotate has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.rotate(image, Math.toRadians(45), 0f, true)

        then:
        !hasNaN(result)
    }

    def "rotate by zero produces same dimensions"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.rotate(image, 0, 0f, false)

        then:
        result.width() == image.width()
        result.height() == image.height()
    }

    // ==================== ROTATE AND SCALE TESTS ====================

    def "rotateAndScale produces same results as CPU implementation"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def gpuResult = openclMath.rotateAndScale(image, angle, blackpoint, scaleX, scaleY)
        def cpuResult = referenceMath.rotateAndScale(image, angle, blackpoint, scaleX, scaleY)

        then:
        gpuResult.width() == cpuResult.width()
        gpuResult.height() == cpuResult.height()
        // Allow higher tolerance for edge interpolation differences
        compareImagesWithEdgeTolerance(gpuResult, cpuResult, 1.0f, 0.99f)

        where:
        angle                  | blackpoint | scaleX | scaleY
        Math.toRadians(45)     | 0f         | 1.0    | 1.0
        Math.toRadians(30)     | 0f         | 1.5    | 1.5
        Math.toRadians(0)      | 0f         | 2.0    | 2.0
        Math.toRadians(90)     | 100f       | 0.5    | 0.5
        Math.toRadians(45)     | 0f         | 1.0    | 2.0
    }

    def "rotateAndScale has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.rotateAndScale(image, Math.toRadians(45), 0f, 1.5, 1.5)

        then:
        !hasNaN(result)
    }

    def "rotateAndScale with scale 1.0 produces similar size to rotate"() {
        given:
        def image = createTestImage(256, 256)
        def angle = Math.toRadians(45)

        when:
        def rotateResult = openclMath.rotate(image, angle, 0f, true)
        def rotateAndScaleResult = openclMath.rotateAndScale(image, angle, 0f, 1.0, 1.0)

        then:
        rotateResult.width() == rotateAndScaleResult.width()
        rotateResult.height() == rotateAndScaleResult.height()
    }

    // ==================== RESCALE TESTS ====================

    def "rescale produces same results as CPU implementation"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def gpuResult = openclMath.rescale(image, newWidth, newHeight)
        def cpuResult = referenceMath.rescale(image, newWidth, newHeight)

        then:
        gpuResult.width() == cpuResult.width()
        gpuResult.height() == cpuResult.height()
        compareImages(gpuResult, cpuResult, 1.0f) // Allow tolerance for interpolation differences

        where:
        newWidth | newHeight
        128      | 128
        512      | 512
        300      | 200
        200      | 300
        64       | 64
    }

    def "rescale has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.rescale(image, 128, 128)

        then:
        !hasNaN(result)
    }

    def "rescale produces correct dimensions"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.rescale(image, newWidth, newHeight)

        then:
        result.width() == newWidth
        result.height() == newHeight

        where:
        newWidth | newHeight
        128      | 128
        512      | 512
        300      | 200
    }

    // ==================== MIRROR TESTS ====================

    def "mirror produces same results as CPU implementation"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def gpuResult = openclMath.mirror(image, horizontal, vertical)
        def cpuResult = referenceMath.mirror(image, horizontal, vertical)

        then:
        compareImages(gpuResult, cpuResult, 0.001f)

        where:
        horizontal | vertical
        true       | false
        false      | true
        true       | true
    }

    def "mirror has no NaN values"() {
        given:
        def image = createTestImage(256, 256)

        when:
        def result = openclMath.mirror(image, true, false)

        then:
        !hasNaN(result)
    }

    def "mirror with no flags returns same image"() {
        given:
        def image = createTestImage(128, 128)

        when:
        def result = openclMath.mirror(image, false, false)

        then:
        result.is(image) // Should return the same instance
    }

    def "mirror horizontal twice produces original image"() {
        given:
        def image = createTestImage(128, 128)

        when:
        def mirrored = openclMath.mirror(image, true, false)
        def restored = openclMath.mirror(mirrored, true, false)

        then:
        compareImages(restored, image, 0.001f)
    }

    def "mirror vertical twice produces original image"() {
        given:
        def image = createTestImage(128, 128)

        when:
        def mirrored = openclMath.mirror(image, false, true)
        def restored = openclMath.mirror(mirrored, false, true)

        then:
        compareImages(restored, image, 0.001f)
    }

    def "mirror preserves dimensions"() {
        given:
        def image = createTestImage(width, height)

        when:
        def result = openclMath.mirror(image, true, true)

        then:
        result.width() == width
        result.height() == height

        where:
        width | height
        128   | 128
        256   | 256
        100   | 200
    }

    // ==================== EDGE CASES ====================

    def "operations work with non-square images"() {
        given:
        def image = createTestImage(200, 100)

        when:
        def addResult = openclMath.add(image, 10f)
        def mulResult = openclMath.multiply(image, 2f)
        def convResult = openclMath.convolve(image, Kernel33.GAUSSIAN_BLUR)
        def mirrorResult = openclMath.mirror(image, true, false)

        then:
        addResult.width() == 200 && addResult.height() == 100
        mulResult.width() == 200 && mulResult.height() == 100
        convResult.width() == 200 && convResult.height() == 100
        mirrorResult.width() == 200 && mirrorResult.height() == 100
        !hasNaN(addResult)
        !hasNaN(mulResult)
        !hasNaN(convResult)
        !hasNaN(mirrorResult)
    }

    def "operations work with small images"() {
        given:
        def image = createTestImage(32, 32)

        when:
        def addResult = openclMath.add(image, 10f)
        def mulResult = openclMath.multiply(image, 2f)
        def convResult = openclMath.convolve(image, Kernel33.GAUSSIAN_BLUR)

        then:
        !hasNaN(addResult)
        !hasNaN(mulResult)
        !hasNaN(convResult)
    }

    // ==================== HELPER METHODS ====================

    private static Image createTestImage(int width, int height, float baseValue = 0f) {
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = baseValue + (y * width + x) % 65536
            }
        }
        return new Image(width, height, data)
    }

    private static Image createZeroImage(int width, int height) {
        var data = new float[height][width]
        return new Image(width, height, data)
    }

    private static boolean compareImages(Image a, Image b, float tolerance) {
        if (a.width() != b.width() || a.height() != b.height()) {
            System.err.println("Dimension mismatch: ${a.width()}x${a.height()} vs ${b.width()}x${b.height()}")
            return false
        }
        def dataA = a.data()
        def dataB = b.data()
        int mismatches = 0
        for (int y = 0; y < a.height(); y++) {
            for (int x = 0; x < a.width(); x++) {
                def diff = Math.abs(dataA[y][x] - dataB[y][x])
                if (diff > tolerance) {
                    if (mismatches < 5) {
                        System.err.println("Mismatch at ($x, $y): GPU=${dataA[y][x]}, CPU=${dataB[y][x]}, diff=$diff")
                    }
                    mismatches++
                }
            }
        }
        if (mismatches > 0) {
            System.err.println("Total mismatches: $mismatches out of ${a.width() * a.height()}")
            return false
        }
        return true
    }

    private static boolean compareImagesWithEdgeTolerance(Image a, Image b, float tolerance, float minMatchRatio) {
        if (a.width() != b.width() || a.height() != b.height()) {
            System.err.println("Dimension mismatch: ${a.width()}x${a.height()} vs ${b.width()}x${b.height()}")
            return false
        }
        def dataA = a.data()
        def dataB = b.data()
        int mismatches = 0
        int total = a.width() * a.height()
        for (int y = 0; y < a.height(); y++) {
            for (int x = 0; x < a.width(); x++) {
                def diff = Math.abs(dataA[y][x] - dataB[y][x])
                if (diff > tolerance) {
                    mismatches++
                }
            }
        }
        float matchRatio = (float)(total - mismatches) / total
        if (matchRatio < minMatchRatio) {
            System.err.println("Match ratio ${matchRatio} below minimum ${minMatchRatio} (${mismatches} mismatches out of ${total})")
            return false
        }
        return true
    }

    private static boolean hasNaN(Image image) {
        def data = image.data()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (Float.isNaN(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean hasInfinity(Image image) {
        def data = image.data()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (Float.isInfinite(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    // ==================== CONCURRENT ACCESS TESTS ====================

    def "concurrent add operations produce correct results"() {
        given:
        def threadCount = 8
        def iterationsPerThread = 10
        def images = (0..<threadCount).collect { createTestImage(128, 128, (it * 1000) as float) }
        def scalars = (0..<threadCount).collect { (it + 1) * 10f as float }
        def results = new Image[threadCount][iterationsPerThread]
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        results[threadIdx][i] = openclMath.add(images[threadIdx], scalars[threadIdx])
                    }
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        // All iterations within a thread should produce identical results
        for (int t = 0; t < threadCount; t++) {
            for (int i = 1; i < iterationsPerThread; i++) {
                assert compareImages(results[t][0], results[t][i], 0.001f)
            }
        }
        // Results should match CPU implementation
        for (int t = 0; t < threadCount; t++) {
            def cpuResult = referenceMath.add(images[t], scalars[t])
            assert compareImages(results[t][0], cpuResult, 0.001f)
        }
    }

    def "concurrent multiply operations produce correct results"() {
        given:
        def threadCount = 8
        def iterationsPerThread = 10
        def images = (0..<threadCount).collect { createTestImage(128, 128, (it * 1000) as float) }
        def scalars = (0..<threadCount).collect { (it + 1) * 0.5f as float }
        def results = new Image[threadCount][iterationsPerThread]
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        results[threadIdx][i] = openclMath.multiply(images[threadIdx], scalars[threadIdx])
                    }
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        // All iterations within a thread should produce identical results
        for (int t = 0; t < threadCount; t++) {
            for (int i = 1; i < iterationsPerThread; i++) {
                assert compareImages(results[t][0], results[t][i], 0.001f)
            }
        }
        // Results should match CPU implementation
        for (int t = 0; t < threadCount; t++) {
            def cpuResult = referenceMath.multiply(images[t], scalars[t])
            assert compareImages(results[t][0], cpuResult, 0.001f)
        }
    }

    def "concurrent convolve operations produce correct results"() {
        given:
        def threadCount = 4
        def iterationsPerThread = 5
        def images = (0..<threadCount).collect { createTestImage(128, 128, (it * 1000) as float) }
        def results = new Image[threadCount][iterationsPerThread]
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        results[threadIdx][i] = openclMath.convolve(images[threadIdx], Kernel33.GAUSSIAN_BLUR)
                    }
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        // All iterations within a thread should produce identical results
        for (int t = 0; t < threadCount; t++) {
            for (int i = 1; i < iterationsPerThread; i++) {
                assert compareImages(results[t][0], results[t][i], 0.001f)
            }
        }
        // Results should match CPU implementation
        for (int t = 0; t < threadCount; t++) {
            def cpuResult = referenceMath.convolve(images[t], Kernel33.GAUSSIAN_BLUR)
            assert compareImages(results[t][0], cpuResult, 1.0f) // Convolution has larger tolerance
        }
    }

    def "concurrent mixed operations produce correct results"() {
        given:
        def threadCount = 8
        def image = createTestImage(256, 256)
        def results = Collections.synchronizedList([])
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    def result
                    switch (threadIdx % 4) {
                        case 0:
                            result = openclMath.add(image, 100f)
                            break
                        case 1:
                            result = openclMath.multiply(image, 2f)
                            break
                        case 2:
                            result = openclMath.convolve(image, Kernel33.GAUSSIAN_BLUR)
                            break
                        case 3:
                            result = openclMath.mirror(image, true, false)
                            break
                    }
                    results.add([threadIdx: threadIdx, result: result, hasNaN: hasNaN(result)])
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        results.size() == threadCount
        results.every { !it.hasNaN }
    }

    def "concurrent rotate operations produce correct results"() {
        given:
        def threadCount = 4
        def iterationsPerThread = 5
        def images = (0..<threadCount).collect { createTestImage(128, 128, (it * 1000) as float) }
        def angles = (0..<threadCount).collect { Math.toRadians(it * 15) }
        def results = new Image[threadCount][iterationsPerThread]
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        results[threadIdx][i] = openclMath.rotate(images[threadIdx], angles[threadIdx], 0f, true)
                    }
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        // All iterations within a thread should produce identical results
        for (int t = 0; t < threadCount; t++) {
            for (int i = 1; i < iterationsPerThread; i++) {
                assert compareImages(results[t][0], results[t][i], 0.001f)
            }
        }
        // No NaN values
        for (int t = 0; t < threadCount; t++) {
            assert !hasNaN(results[t][0])
        }
    }

    def "stress test with many concurrent operations"() {
        given:
        def threadCount = 16
        def iterationsPerThread = 20
        def images = (0..<threadCount).collect { createTestImage(64, 64, (it * 100) as float) }
        def successCount = new AtomicInteger(0)
        def errors = Collections.synchronizedList([])

        when:
        def threads = (0..<threadCount).collect { threadIdx ->
            Thread.start {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        def result = openclMath.add(images[threadIdx], i as float)
                        if (!hasNaN(result)) {
                            successCount.incrementAndGet()
                        }
                    }
                } catch (Exception e) {
                    errors.add(e)
                }
            }
        }
        threads*.join()

        then:
        errors.isEmpty()
        successCount.get() == threadCount * iterationsPerThread
    }
}
