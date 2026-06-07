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

import spock.lang.Specification

class ImageMathTest extends Specification {
    def "tests integral image (#label)"() {
        var image = new Image(5, 4, new float[][]{
                new float[]{4, 1, 2, 2, 1},
                new float[]{0, 4, 1, 3, 5},
                new float[]{3, 1, 0, 4, 2},
                new float[]{2, 1, 3, 2, 1}
        })

        when:
        def integral = imageMath.integralImage(image)

        then:
        integral == new Image(5, 4, new float[][]{
                new float[]{4, 5, 7, 9, 10},
                new float[]{4, 9, 12, 17, 23},
                new float[]{7, 13, 16, 25, 33},
                new float[]{9, 16, 22, 33, 42}
        })

        when:
        def sum1 = imageMath.areaSum(integral, 0, 0, 2, 2)
        def sum2 = imageMath.areaSum(integral, 1, 2, 3, 2)
        def sum3 = imageMath.areaSum(integral, 2, 0, 2, 2)
        def sum4 = imageMath.areaSum(integral, 0, 1, 3, 2)
        def avg = imageMath.areaAverage(integral, 0, 1, 3, 2)

        then:
        sum1 == 9
        sum2 == 11
        sum3 == 8
        sum4 == 9
        avg == 1.5f

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()

    }

    def "computes average of line (#label)"() {
        def image = newImage(851, 344)

        expect:
        imageMath.averageOf(image, 343) == 768

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    def "computes average of double array (#label)"() {
        double[] array = new double[851]
        for (int i = 0; i < array.length; i++) {
            array[i] = 2d * i;
        }

        expect:
        imageMath.averageOf(array) == 850d

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    def "computes incremental average (#label)"() {
        float[][] current = new float[1][333]
        for (int i = 0; i < 333; i++) {
            current[0][i] = i
        }
        float[][] average = new float[1][333]

        when:
        imageMath.incrementalAverage(current, average, 10)

        then:
        average[0][0] == 0
        average[0][1] == 0.1f
        average[0][2] == 0.2f
        average[0][120] == 12f
        average[0][332] == 33.2f

        when:
        imageMath.incrementalAverage(current, average, 11)

        then:
        average[0][0] == 0
        average[0][1] == 0.18181819f
        average[0][120] == 21.818182f
        average[0][332] == 60.363636f

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    def "vectorized incremental average matches fallback for large arrays"() {
        def fallback = new FallbackImageMath()
        def vectorized = new VectorApiImageMath()
        int width = 5320
        int height = 100
        float[][] data = new float[height][width]
        float[][] avgFallback = new float[height][width]
        float[][] avgVectorized = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * 65535
            }
        }

        when:
        for (int n = 1; n <= 50; n++) {
            fallback.incrementalAverage(data, avgFallback, n)
            vectorized.incrementalAverage(data, avgVectorized, n)
        }

        then:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assert Math.abs(avgFallback[y][x] - avgVectorized[y][x]) < 0.01f
            }
        }
    }

    def "boxBlur matches naive convolve with BlurKernel (#label, size=#kernelSize)"() {
        var image = newImage(width, height)

        when:
        def boxResult = imageMath.boxBlur(image, kernelSize)
        def naiveResult = imageMath.convolve(image, BlurKernel.of(kernelSize))

        then:
        boxResult.width() == naiveResult.width()
        boxResult.height() == naiveResult.height()
        compareImages(boxResult, naiveResult, 0.5f)

        where:
        label        | imageMath                | width | height | kernelSize
        'fallback'   | new FallbackImageMath()  | 64    | 64     | 3
        'fallback'   | new FallbackImageMath()  | 64    | 64     | 5
        'fallback'   | new FallbackImageMath()  | 64    | 64     | 15
        'fallback'   | new FallbackImageMath()  | 100   | 80     | 31
        'vectorized' | new VectorApiImageMath() | 64    | 64     | 3
        'vectorized' | new VectorApiImageMath() | 64    | 64     | 5
        'vectorized' | new VectorApiImageMath() | 64    | 64     | 15
        'vectorized' | new VectorApiImageMath() | 100   | 80     | 31
    }

    def "boxBlur with kernel size 1 returns original values (#label)"() {
        var image = newImage(32, 32)

        when:
        def result = imageMath.boxBlur(image, 1)

        then:
        compareImages(result, image, 0.001f)

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    def "boxBlur preserves image dimensions (#label)"() {
        var image = newImage(width, height)

        when:
        def result = imageMath.boxBlur(image, 7)

        then:
        result.width() == width
        result.height() == height

        where:
        label        | imageMath                | width | height
        'fallback'   | new FallbackImageMath()  | 64    | 64
        'fallback'   | new FallbackImageMath()  | 100   | 50
        'vectorized' | new VectorApiImageMath() | 64    | 64
        'vectorized' | new VectorApiImageMath() | 100   | 50
    }

    def "boxBlur on uniform image returns same values (#label)"() {
        var data = new float[32][32]
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                data[y][x] = 1000f
            }
        }
        var image = new Image(32, 32, data)

        when:
        def result = imageMath.boxBlur(image, 11)

        then:
        compareImages(result, image, 0.01f)

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    def "vectorized boxBlur matches fallback for large images"() {
        var fallback = new FallbackImageMath()
        var vectorized = new VectorApiImageMath()
        var image = newImage(200, 150)

        when:
        def fallbackResult = fallback.boxBlur(image, 51)
        def vectorizedResult = vectorized.boxBlur(image, 51)

        then:
        compareImages(fallbackResult, vectorizedResult, 0.001f)
    }

    def "dedistort scale=2 produces 2x output dimensions"() {
        var imageMath = ImageMath.newInstance()
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]

        when:
        var result = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 2)

        then:
        result.width() == 64
        result.height() == 48
    }

    def "dedistort scale=4 samples at quarter-pixel offsets on a 4x grid"() {
        var imageMath = ImageMath.newInstance()
        // Linear gradient: value = y + x. Output (X, Y) maps to source (X/4, Y/4).
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]

        when:
        var hires = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 4.0)

        then:
        hires.width() == 128
        hires.height() == 96
        // Output (40, 32) maps to source (10, 8) => value 18
        Math.abs(hires.data()[32][40] - 18.0f) < 1e-3f
        // Output (41, 33) maps to source (10.25, 8.25) => value 18.5 on linear gradient
        // Lanczos isn't exact on a linear gradient at fractional positions; allow ~0.1 slack.
        Math.abs(hires.data()[33][41] - 18.5f) < 0.1f
    }

    def "dedistort scale=1.5 produces rounded fractional output dimensions"() {
        var imageMath = ImageMath.newInstance()
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]

        when:
        var result = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 1.5)

        then:
        result.width() == 48
        result.height() == 36
        // Output (15, 12) maps to source (10, 8) => value 18 (integer source pixel)
        Math.abs(result.data()[12][15] - 18.0f) < 1e-3f
    }

    def "dedistort scale=1 matches default overload"() {
        var imageMath = ImageMath.newInstance()
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]

        when:
        var explicit = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 1)
        var implicit = imageMath.dedistort(image, gridDx, gridDy, gridStep, true)

        then:
        compareImages(explicit, implicit, 0.0f)
    }

    def "dedistort scale=2 with identity displacement samples on a 2x grid"() {
        var imageMath = ImageMath.newInstance()
        // Linear gradient image: value = y + x. Interior sampling avoids Lanczos boundary bias.
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]

        when:
        var hires = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 2)

        then:
        // Output pixel (10, 8) maps to source coord (5, 4) => integer, value 9
        Math.abs(hires.data()[8][10] - 9.0f) < 1e-3f
        // Output pixel (16, 12) maps to source coord (8, 6) => integer, value 14
        Math.abs(hires.data()[12][16] - 14.0f) < 1e-3f
        // Output pixel (11, 9) maps to source coord (5.5, 4.5) => value 10 on a linear gradient
        Math.abs(hires.data()[9][11] - 10.0f) < 1e-2f
    }

    def "dedistort scale=2 with constant sub-pixel displacement shifts source coords"() {
        var imageMath = ImageMath.newInstance()
        // Linear gradient: value = y + x. A constant displacement dx=2 must shift the
        // sampled source coord by 2 (in source pixels), not 4: srcX = sx + dx.
        var image = newImage(32, 24)
        var gridStep = 8
        var gridW = 32 / gridStep + 2
        var gridH = 24 / gridStep + 2
        var gridDx = new float[gridH][gridW]
        var gridDy = new float[gridH][gridW]
        for (int gy = 0; gy < gridH; gy++) {
            for (int gx = 0; gx < gridW; gx++) {
                gridDx[gy][gx] = 2.0f
                gridDy[gy][gx] = 0.0f
            }
        }

        when:
        var hires = imageMath.dedistort(image, gridDx, gridDy, gridStep, true, 2)

        then:
        // Output (8, 10) maps to sx=4, sy=5; srcX=6, srcY=5 => value 11
        Math.abs(hires.data()[10][8] - 11.0f) < 1e-3f
    }

    def "gaussianBlur is bit-for-bit identical to convolve with GAUSSIAN_BLUR on integer inputs (#label, #width x #height)"() {
        // With integer-valued inputs every intermediate (weighted sums, /16 by a power of two)
        // is exactly representable as a float, so separable blur must match the 2D convolution exactly.
        var image = randomIntegerImage(width, height, 65535, 123)
        var expected = new float[height][width]
        var actual = new float[height][width]

        when:
        imageMath.convolve(image, Kernel33.GAUSSIAN_BLUR, expected)
        imageMath.gaussianBlur(image, actual)

        then:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assert actual[y][x] == expected[y][x]
            }
        }

        where:
        label        | imageMath                | width | height
        'fallback'   | new FallbackImageMath()  | 1     | 1
        'fallback'   | new FallbackImageMath()  | 1     | 7
        'fallback'   | new FallbackImageMath()  | 7     | 1
        'fallback'   | new FallbackImageMath()  | 2     | 2
        'fallback'   | new FallbackImageMath()  | 3     | 3
        'fallback'   | new FallbackImageMath()  | 64    | 64
        'fallback'   | new FallbackImageMath()  | 257   | 131
        'vectorized' | new VectorApiImageMath() | 1     | 1
        'vectorized' | new VectorApiImageMath() | 1     | 7
        'vectorized' | new VectorApiImageMath() | 7     | 1
        'vectorized' | new VectorApiImageMath() | 2     | 2
        'vectorized' | new VectorApiImageMath() | 3     | 3
        'vectorized' | new VectorApiImageMath() | 64    | 64
        'vectorized' | new VectorApiImageMath() | 257   | 131
    }

    def "gaussianBlur matches convolve with GAUSSIAN_BLUR on float inputs within float-reassociation noise (#label)"() {
        var image = randomFloatImage(width, height, 65535, 7)
        var expected = new float[height][width]
        var actual = new float[height][width]

        when:
        imageMath.convolve(image, Kernel33.GAUSSIAN_BLUR, expected)
        imageMath.gaussianBlur(image, actual)

        then:
        // values span [0, 65535]; reassociation of float adds yields sub-0.01 differences
        compareImages(new Image(width, height, actual), new Image(width, height, expected), 0.01f)

        where:
        label        | imageMath                | width | height
        'fallback'   | new FallbackImageMath()  | 333   | 211
        'vectorized' | new VectorApiImageMath() | 333   | 211
    }

    def "vectorized gaussianBlur is bit-for-bit identical to fallback gaussianBlur (float inputs)"() {
        var image = randomFloatImage(333, 211, 65535, 99)
        var fallback = new float[211][333]
        var vectorized = new float[211][333]

        when:
        new FallbackImageMath().gaussianBlur(image, fallback)
        new VectorApiImageMath().gaussianBlur(image, vectorized)

        then:
        for (int y = 0; y < 211; y++) {
            for (int x = 0; x < 333; x++) {
                assert vectorized[y][x] == fallback[y][x]
            }
        }
    }

    def "column-ranged gaussianBlur is bit-for-bit identical to full blur over the range (#label, [#from,#to))"() {
        var image = randomFloatImage(width, height, 65535, 21)
        var full = new float[height][width]
        var ranged = new float[height][width]

        when:
        imageMath.gaussianBlur(image, full)
        imageMath.gaussianBlur(image, ranged, from, to)

        then:
        for (int y = 0; y < height; y++) {
            for (int x = from; x < to; x++) {
                assert Float.floatToRawIntBits(ranged[y][x]) == Float.floatToRawIntBits(full[y][x])
            }
        }

        where:
        label        | imageMath                | width | height | from | to
        'fallback'   | new FallbackImageMath()  | 333   | 211    | 40   | 300
        'fallback'   | new FallbackImageMath()  | 333   | 211    | 0    | 333
        'fallback'   | new FallbackImageMath()  | 333   | 211    | 1    | 332
        'fallback'   | new FallbackImageMath()  | 64    | 32     | 0    | 1
        'vectorized' | new VectorApiImageMath() | 333   | 211    | 40   | 300
        'vectorized' | new VectorApiImageMath() | 333   | 211    | 0    | 333
        'vectorized' | new VectorApiImageMath() | 333   | 211    | 1    | 332
        'vectorized' | new VectorApiImageMath() | 1920  | 200    | 247  | 1666
    }

    def "scratch-buffer gaussianBlur is bit-for-bit identical to the allocating blur even with a dirty tmp (#label, [#from,#to))"() {
        var image = randomFloatImage(width, height, 65535, 31)
        var full = new float[height][width]
        var reused = new float[height][width]
        // Pre-fill tmp with garbage to prove the blur never reads stale scratch values.
        var tmp = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tmp[y][x] = 123456.0f
            }
        }

        when:
        imageMath.gaussianBlur(image, full, from, to)
        imageMath.gaussianBlur(image, reused, tmp, from, to)

        then:
        for (int y = 0; y < height; y++) {
            for (int x = from; x < to; x++) {
                assert Float.floatToRawIntBits(reused[y][x]) == Float.floatToRawIntBits(full[y][x])
            }
        }

        where:
        label        | imageMath                | width | height | from | to
        'fallback'   | new FallbackImageMath()  | 333   | 211    | 40   | 300
        'fallback'   | new FallbackImageMath()  | 333   | 211    | 0    | 333
        'vectorized' | new VectorApiImageMath() | 333   | 211    | 40   | 300
        'vectorized' | new VectorApiImageMath() | 1920  | 200    | 247  | 1666
    }

    static Image newImage(int width, int height) {
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = y + x
            }
        }
        return new Image(width, height, data)
    }

    static Image randomIntegerImage(int width, int height, int maxValue, long seed) {
        var random = new Random(seed)
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextInt(maxValue + 1)
            }
        }
        return new Image(width, height, data)
    }

    static Image randomFloatImage(int width, int height, float maxValue, long seed) {
        var random = new Random(seed)
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * maxValue
            }
        }
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
                        System.err.println("Mismatch at ($x, $y): a=${dataA[y][x]}, b=${dataB[y][x]}, diff=$diff")
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
}
