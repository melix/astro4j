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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import java.util.function.BiPredicate

class BackgroundRemovalTest extends Specification {

    def "background model produces valid results without ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def image = new ImageWrapper32(width, height, data, [:])

        when:
        def result = BackgroundRemoval.backgroundModel(image, degree, 2.0)

        then:
        result.isPresent()
        def bgData = result.get().data()
        !hasNaN(bgData, width, height)
        !hasInfinity(bgData, width, height)

        where:
        degree << [0, 1, 2, 3]
    }

    def "background model produces valid results with ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def ellipse = createTestEllipse(width, height)
        def image = new ImageWrapper32(width, height, data, [(Ellipse): ellipse])

        when:
        def result = BackgroundRemoval.backgroundModel(image, degree, 2.0)

        then:
        result.isPresent()
        def bgData = result.get().data()
        !hasNaN(bgData, width, height)
        !hasInfinity(bgData, width, height)

        where:
        degree << [0, 1, 2, 3]
    }

    def "background model under a mask ignores the pixels outside it"() {
        given:
        def width = 256
        def height = 256
        def data = createRamp(width, height, 100f, 300f)
        // A bright signal covering the right half, which the model must not follow
        for (int y = 0; y < height; y++) {
            for (int x = width / 2; x < width; x++) {
                data[y][x] = 5000f
            }
        }
        def image = new ImageWrapper32(width, height, data, [:])
        def mask = { Integer x, Integer y -> x < width / 2 } as BiPredicate

        when:
        def masked = BackgroundRemoval.backgroundModel(image, 1, 2.0d, new float[height][width], mask).get().data()
        def unmasked = BackgroundRemoval.backgroundModel(image, 1, 2.0d).get().data()

        then: "the masked model extrapolates the ramp over the bright half"
        Math.abs(masked[height / 2 as int][width - 1] - 300f) < 5f

        and: "while the blind model is dragged up by it"
        unmasked[height / 2 as int][width - 1] > 1000f
    }

    def "background model under a mask keeps negative samples"() {
        given:
        def width = 256
        def height = 256
        def data = createNoise(width, height, 100f)
        def image = new ImageWrapper32(width, height, data, [:])
        def mask = { Integer x, Integer y -> true } as BiPredicate

        when:
        def masked = BackgroundRemoval.backgroundModel(image, 1, 2.0d, new float[height][width], mask).get().data()
        def unmasked = BackgroundRemoval.backgroundModel(image, 1, 2.0d).get().data()

        then: "the model follows a background centred on zero"
        Math.abs(masked[height / 2 as int][width / 2 as int]) < 10f

        and: "whereas dropping the negative samples biases it upwards"
        unmasked[height / 2 as int][width / 2 as int] > 20f
    }

    private static float[][] createNoise(int width, int height, float amplitude) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (2 * amplitude * (random.nextFloat() - 0.5f))
            }
        }
        return data
    }

    private static float[][] createRamp(int width, int height, float from, float to) {
        var data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = from + (to - from) * (x / (float) (width - 1))
            }
        }
        return data
    }

    private static float[][] createTestImage(int width, int height) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create a gradient background with some noise
                float bgGradient = (float) (1000 + 500 * (x / (double) width) + 300 * (y / (double) height))
                data[y][x] = bgGradient + random.nextFloat() * 100
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
