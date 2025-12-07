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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class BandingReductionTest extends Specification {

    def "banding reduction produces valid results without ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, BandingReduction.DEFAULT_BAND_SIZE, null)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)
    }

    def "banding reduction produces valid results with ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def ellipse = createTestEllipse(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, BandingReduction.DEFAULT_BAND_SIZE, ellipse)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)
    }

    def "banding reduction with different band sizes"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, bandSize, null)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)

        where:
        bandSize << [8, 16, 24, 32, 48]
    }

    private static float[][] createTestImage(int width, int height) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            float rowBias = (float) (1000 * Math.sin(y * 0.1))
            for (int x = 0; x < width; x++) {
                data[y][x] = 30000 + rowBias + random.nextFloat() * 5000
            }
        }
        return data
    }

    private static Ellipse createTestEllipse(int width, int height) {
        double cx = width / 2.0
        double cy = height / 2.0
        double rx = width * 0.4
        double ry = height * 0.4
        double a = 1.0 / (rx * rx)
        double c = 1.0 / (ry * ry)
        double d = -2.0 * cx / (rx * rx)
        double e = -2.0 * cy / (ry * ry)
        double f = cx * cx / (rx * rx) + cy * cy / (ry * ry) - 1.0
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static boolean hasNaN(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isNaN(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean hasInfinity(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isInfinite(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }
}
