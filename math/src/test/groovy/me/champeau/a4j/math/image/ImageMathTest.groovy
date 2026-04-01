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

    static Image newImage(int width, int height) {
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = y + x
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
