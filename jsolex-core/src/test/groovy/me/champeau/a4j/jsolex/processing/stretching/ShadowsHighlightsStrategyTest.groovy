/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.expr.impl.Stretching
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import spock.lang.Specification

class ShadowsHighlightsStrategyTest extends Specification {
    private static final int SIZE = 200
    private static final int RADIUS = 10

    def "zero amounts leave the image unchanged"() {
        given:
        def image = noise(1)
        def expected = image.copy()

        when:
        new ShadowsHighlightsStrategy(0, 0, RADIUS).stretch(image)

        then:
        maxDifference(image.data(), expected.data()) < 1e-3
    }

    def "lifting the shadows brightens dark areas more than bright ones"() {
        given:
        def image = halves(5000, 50000)
        def original = image.copy()

        when:
        new ShadowsHighlightsStrategy(50, 0, RADIUS).stretch(image)

        then:
        def darkGain = image.data()[SIZE / 2 as int][20] - original.data()[SIZE / 2 as int][20]
        def brightGain = image.data()[SIZE / 2 as int][SIZE - 20] - original.data()[SIZE / 2 as int][SIZE - 20]
        darkGain > 1000
        darkGain > 5 * brightGain
        brightGain >= 0
    }

    def "darkening the highlights darkens bright areas"() {
        given:
        def image = halves(5000, 50000)
        def original = image.copy()

        when:
        new ShadowsHighlightsStrategy(0, 50, RADIUS).stretch(image)

        then:
        def darkChange = image.data()[SIZE / 2 as int][20] - original.data()[SIZE / 2 as int][20]
        def brightChange = image.data()[SIZE / 2 as int][SIZE - 20] - original.data()[SIZE / 2 as int][SIZE - 20]
        brightChange < -1000
        Math.abs(darkChange) < Math.abs(brightChange) / 5
    }

    def "no halo appears next to a sharp limb"() {
        given:
        def image = disk(40000, 3000)
        def original = image.copy()
        def center = SIZE / 2 as int
        def diskRadius = SIZE / 4 as int

        when:
        new ShadowsHighlightsStrategy(50, 30, RADIUS).stretch(image)

        then:
        def change = { int x -> image.data()[center][x] - original.data()[center][x] }
        def farInside = change(center)
        def nearInside = change(center + diskRadius - 3 * RADIUS)
        def farOutside = change(SIZE - 2)
        def nearOutside = change(center + diskRadius + 3 * RADIUS)
        Math.abs(nearInside - farInside) < 50
        Math.abs(nearOutside - farOutside) < 50
        farOutside > 1000
        farInside < 0
    }

    def "non finite pixels become black without affecting their neighbours"() {
        given:
        def image = noise(7)
        def reference = image.copy()
        image.data()[32][32] = Float.NaN
        reference.data()[32][32] = 0f
        def strategy = new ShadowsHighlightsStrategy(40, 10, 5)

        when:
        strategy.stretch(image)
        strategy.stretch(reference)

        then:
        image.data() == reference.data()
    }

    def "zero pixels stay at zero"() {
        given:
        def image = noise(2)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE / 3; x++) {
                image.data()[y][x] = 0
            }
        }

        when:
        new ShadowsHighlightsStrategy(80, -20, RADIUS).stretch(image)

        then:
        (0..<SIZE).every { y -> (0..<(SIZE / 3 as int)).every { x -> image.data()[y][x] == 0f } }
        image.data()[SIZE / 2 as int][SIZE - 1] > 0
    }

    def "color images keep their hue"() {
        given:
        def mono = halves(4000, 30000)
        def r = scaled(mono.data(), 1.0f)
        def g = scaled(mono.data(), 0.6f)
        def b = scaled(mono.data(), 0.3f)
        def image = new RGBImage(SIZE, SIZE, r, g, b, [:])
        def originalGreen = g[SIZE / 2 as int][20]

        when:
        new ShadowsHighlightsStrategy(50, 20, RADIUS).stretch(image)

        then:
        for (y in [10, SIZE / 2 as int, SIZE - 10]) {
            for (x in [5, 20, SIZE - 20, SIZE - 5]) {
                assert Math.abs(g[y][x] / r[y][x] - 0.6f) < 1e-4
                assert Math.abs(b[y][x] / r[y][x] - 0.3f) < 1e-4
            }
        }
        g[SIZE / 2 as int][20] > originalGreen
    }

    def "rejects out of range amounts"() {
        when:
        new ShadowsHighlightsStrategy(shadows, highlights, radius)

        then:
        thrown(IllegalArgumentException)

        where:
        shadows | highlights | radius
        101     | 0          | 10
        0       | -101       | 10
        0       | 0          | 0
    }

    def "shadows_highlights function spreads the brightness range then applies the strategy"() {
        given:
        def image = noise(3)
        def expected = image.copy()
        RangeExpansionStrategy.DEFAULT.stretch(expected)
        new ShadowsHighlightsStrategy(40, 10, 20).stretch(expected)

        when:
        def result = stretching().shadowsHighlights(img: image, shadows: 40, highlights: 10, radius: 20) as ImageWrapper32

        then:
        result.data() == expected.data()
        maxDifference(image.data(), expected.data()) > 0
    }

    def "shadows_highlights function uses default amounts"() {
        given:
        def image = noise(4)
        def expected = image.copy()
        RangeExpansionStrategy.DEFAULT.stretch(expected)
        new ShadowsHighlightsStrategy(ShadowsHighlightsStrategy.DEFAULT_SHADOWS, ShadowsHighlightsStrategy.DEFAULT_HIGHLIGHTS, ShadowsHighlightsStrategy.DEFAULT_RADIUS).stretch(expected)

        when:
        def result = stretching().shadowsHighlights(img: image) as ImageWrapper32

        then:
        result.data() == expected.data()
    }

    def "the stretching step spreads the brightness range before adjusting it"() {
        given:
        def image = noise(6)
        def expected = image.copy()
        RangeExpansionStrategy.DEFAULT.stretch(expected)
        new ShadowsHighlightsStrategy(ShadowsHighlightsStrategy.DEFAULT_SHADOWS, ShadowsHighlightsStrategy.DEFAULT_HIGHLIGHTS, ShadowsHighlightsStrategy.DEFAULT_RADIUS).stretch(expected)

        when:
        StretchStep.defaultsOf(StretchStepKind.SHADOWS_HIGHLIGHTS).toStrategy().stretch(image)

        then:
        image.data() == expected.data()
    }

    def "describes the step with its parameters"() {
        given:
        def image = noise(5)

        when:
        StretchRecipe.of(StretchStep.defaultsOf(StretchStepKind.SHADOWS_HIGHLIGHTS)).applyTo(image)

        then:
        image.findMetadata(TransformationHistory).get().transforms() == [
                "Shadows/highlights (shadows: 25, highlights: 0, radius: 30)"
        ]
    }

    private static Stretching stretching() {
        new Stretching([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    private static ImageWrapper32 noise(long seed) {
        def random = new Random(seed)
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) (1000 + 50000 * random.nextDouble())
            }
        }
        image(data)
    }

    private static ImageWrapper32 halves(float left, float right) {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = x < SIZE / 2 ? left : right
            }
        }
        image(data)
    }

    private static ImageWrapper32 disk(float inside, float outside) {
        def data = new float[SIZE][SIZE]
        double center = SIZE / 2
        double radius = SIZE / 4
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = Math.hypot(x - center, y - center) <= radius ? inside : outside
            }
        }
        image(data)
    }

    private static float[][] scaled(float[][] data, float factor) {
        def result = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                result[y][x] = data[y][x] * factor
            }
        }
        result
    }

    private static ImageWrapper32 image(float[][] data) {
        new ImageWrapper32(SIZE, SIZE, data, [:] as Map<Class<?>, Object>)
    }

    private static double maxDifference(float[][] a, float[][] b) {
        double max = 0
        for (int y = 0; y < a.length; y++) {
            for (int x = 0; x < a[y].length; x++) {
                max = Math.max(max, Math.abs(a[y][x] - b[y][x]))
            }
        }
        max
    }
}
