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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class ScatteredLightTest extends Specification {
    private static final int SIZE = 400
    private static final double RADIUS = 130
    private static final double SKY = 2000
    private static final double DISK = 40000

    def "removes a background shaped like the disk chord"() {
        given: "a disk over a uniform sky, with a chord shaped background added outside it"
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def data = scene()
        addScatteredLight(data, flux, 6000)
        def outsideBefore = data[20][5]

        when:
        ScatteredLight.remove(SIZE, SIZE, data, ellipse, 1.0d, 1)

        then: "the sky is brought back to a uniform level"
        def deviation = maxSkyDeviation(data)
        deviation < 400

        and: "the correction is exactly zero where the disk never entered the slit"
        data[20][5] == outsideBefore
    }

    def "leaves the solar disk untouched"() {
        given:
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def data = scene()
        addScatteredLight(data, flux, 6000)
        def before = data.collect { it.clone() }

        when:
        ScatteredLight.remove(SIZE, SIZE, data, ellipse, 1.0d, 1)

        then: "every pixel inside the limb keeps its exact value"
        double worst = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def dx = (x - SIZE / 2.0d) / RADIUS
                def dy = (y - SIZE / 2.0d) / RADIUS
                if (Math.sqrt(dx * dx + dy * dy) <= 1) {
                    worst = Math.max(worst, Math.abs(data[y][x] - before[y][x]))
                }
            }
        }
        worst == 0
    }

    def "preserves a prominence sitting on the background"() {
        given: "a bright feature just outside the limb, on top of the scattered background"
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def data = scene()
        addScatteredLight(data, flux, 6000)
        def featureRow = (int) (SIZE / 2)
        def featureColumn = (int) (SIZE / 2 + RADIUS * 1.12)
        for (int y = featureRow - 6; y <= featureRow + 6; y++) {
            for (int x = featureColumn - 6; x <= featureColumn + 6; x++) {
                data[y][x] += 9000
            }
        }
        def featureBefore = data[featureRow][featureColumn] - data[featureRow - 40][featureColumn]

        when:
        ScatteredLight.remove(SIZE, SIZE, data, ellipse, 1.0d, 1)

        then: "the feature keeps its contrast against its surroundings"
        def featureAfter = data[featureRow][featureColumn] - data[featureRow - 40][featureColumn]
        featureAfter > 0.9 * featureBefore
    }

    def "does nothing when there are no iterations"() {
        given:
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def data = scene()
        addScatteredLight(data, flux, 6000)
        def before = data.collect { it.clone() }

        when:
        ScatteredLight.remove(SIZE, SIZE, data, ellipse, 1.0d, 0)

        then:
        for (int y = 0; y < SIZE; y++) {
            assert Arrays.equals(data[y], before[y])
        }
    }

    def "a second pass only removes what the first left behind"() {
        given:
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def once = scene()
        addScatteredLight(once, flux, 6000)
        def twice = once.collect { it.clone() } as float[][]

        when:
        ScatteredLight.remove(SIZE, SIZE, once, ellipse, 1.0d, 1)
        ScatteredLight.remove(SIZE, SIZE, twice, ellipse, 1.0d, 2)

        then: "the extra pass does not make the background worse"
        maxSkyDeviation(twice) <= 1.2 * maxSkyDeviation(once)
    }

    def "does nothing when strength is zero"() {
        given:
        def ellipse = ellipse()
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)
        def data = scene()
        addScatteredLight(data, flux, 6000)
        def before = data.collect { it.clone() }

        when:
        ScatteredLight.remove(SIZE, SIZE, data, ellipse, 0d, 1)

        then:
        for (int y = 0; y < SIZE; y++) {
            assert Arrays.equals(data[y], before[y])
        }
    }

    def "detects the padding added when fitting a canvas"() {
        given: "an image padded with a constant fill at the top and bottom"
        def data = scene()
        for (int y = 0; y < 30; y++) {
            Arrays.fill(data[y], 1234f)
            Arrays.fill(data[SIZE - 1 - y], 1234f)
        }

        when:
        def rows = ScatteredLight.validRows(SIZE, SIZE, data)

        then:
        rows[0] == 30
        rows[1] == SIZE - 31
    }

    def "treats every line as valid when there is no padding"() {
        when:
        def rows = ScatteredLight.validRows(SIZE, SIZE, scene())

        then:
        rows[0] == 0
        rows[1] == SIZE - 1
    }

    def "the chord flux vanishes outside the disk column range"() {
        when:
        def flux = ScatteredLight.chordFlux(SIZE, SIZE, SIZE / 2.0d, SIZE / 2.0d, RADIUS)

        then: "it peaks at the centre of the disk and is zero beyond its edges"
        flux[(int) (SIZE / 2)] == 1.0d
        flux[(int) (SIZE / 2 - RADIUS - 2)] == 0d
        flux[(int) (SIZE / 2 + RADIUS + 2)] == 0d
        flux[(int) (SIZE / 2 + RADIUS * 0.5)] > 0.5d
    }

    private static float[][] scene() {
        def data = new float[SIZE][SIZE]
        def random = new Random(42)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def dx = (x - SIZE / 2.0d) / RADIUS
                def dy = (y - SIZE / 2.0d) / RADIUS
                def level = Math.sqrt(dx * dx + dy * dy) <= 1 ? DISK : SKY
                // real images are never noiseless: without this a sky-only line would be
                // exactly constant and would look like the padding added around a canvas
                data[y][x] = (float) (level + random.nextGaussian() * 30)
            }
        }
        return data
    }

    private static void addScatteredLight(float[][] data, double[] flux, double amplitude) {
        for (int y = 0; y < SIZE; y++) {
            def dy = (y - SIZE / 2.0d) / RADIUS
            for (int x = 0; x < SIZE; x++) {
                def dx = (x - SIZE / 2.0d) / RADIUS
                if (Math.sqrt(dx * dx + dy * dy) > 1) {
                    data[y][x] += (float) (amplitude * flux[x])
                }
            }
        }
    }

    private static double maxSkyDeviation(float[][] data) {
        double worst = 0
        for (int y = 0; y < SIZE; y++) {
            def dy = (y - SIZE / 2.0d) / RADIUS
            for (int x = 0; x < SIZE; x++) {
                def dx = (x - SIZE / 2.0d) / RADIUS
                if (Math.sqrt(dx * dx + dy * dy) > 1.1) {
                    worst = Math.max(worst, Math.abs(data[y][x] - SKY))
                }
            }
        }
        return worst
    }

    private static Ellipse ellipse() {
        double c = SIZE / 2.0d
        double a = 1.0d / (RADIUS * RADIUS)
        double d = -2.0d * c / (RADIUS * RADIUS)
        double f = 2 * c * c / (RADIUS * RADIUS) - 1.0d
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, a, d, d, f))
    }
}
