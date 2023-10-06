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
        var image = new Image(5, 4, new float[]{
                4, 1, 2, 2, 1,
                0, 4, 1, 3, 5,
                3, 1, 0, 4, 2,
                2, 1, 3, 2, 1
        })

        when:
        def integral = imageMath.integralImage(image)

        then:
        integral == new Image(5, 4, new float[]{
                4, 5, 7, 9, 10,
                4, 9, 12, 17, 23,
                7, 13, 16, 25, 33,
                9, 16, 22, 33, 42
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
        float[] current = new float[333]
        for (int i = 0; i < current.length; i++) {
            current[i] = i
        }
        float[] average = new float[333]

        when:
        imageMath.incrementalAverage(current, average, 10)

        then:
        average[0] == 0
        average[1] == 0.1f
        average[2] == 0.2f
        average[120] == 12f
        average[332] == 33.2f

        when:
        imageMath.incrementalAverage(current, average, 11)

        then:
        average[0] == 0
        average[1] == 0.18181819f
        average[120] == 21.818182f
        average[332] == 60.363636f

        where:
        label        | imageMath
        'fallback'   | new FallbackImageMath()
        'vectorized' | new VectorApiImageMath()
    }

    static Image newImage(int width, int height) {
        var size = width * height
        var data = new float[size]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y * width + x] = y + x
            }
        }
        return new Image(width, height, data)
    }
}
