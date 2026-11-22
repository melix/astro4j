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
package me.champeau.a4j.jsolex.processing.sun.detection

import me.champeau.a4j.jsolex.processing.util.Charts
import me.champeau.a4j.jsolex.processing.util.Dispersion
import spock.lang.Specification

import java.util.function.DoubleUnaryOperator

class EllermanBombProfileTest extends Specification {
    private static final int WIDTH = 200
    private static final int HEIGHT = 120
    private static final double ANGSTROMS_PER_PIXEL = 0.1d
    private static final double CONTINUUM = 30000d
    private static final int COLUMN = 100

    def "compares the profile of a brightening with the profile of its neighbours"() {
        when:
        def profile = extract { x, angstroms -> line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x) }

        then:
        profile.offsets().first() == -profile.offsets().last()
        ratioAt(profile, -1.2d) > 1.1d
        ratioAt(profile, 1.2d) > 1.1d
        Math.abs(ratioAt(profile, 0d) - 1d) < 0.05d
        Math.abs(ratioAt(profile, 4.5d) - 1d) < 0.01d
        Math.abs(valueAt(profile, profile.neighbours(), 4.5d) - 1d) < 0.01d
        profile.scatter().every { it < 1e-3d }
    }

    def "orders the wavelengths from blue to red when the wavelength #direction with the row"() {
        when:
        def profile = extract(grows) { x, angstroms -> line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x) * (angstroms > 0 ? 1d : 0d) }.orElseThrow()

        then:
        ratioAt(profile, 1.2d) > 1.1d
        Math.abs(ratioAt(profile, -1.2d) - 1d) < 0.01d

        where:
        direction   | grows
        "grows"     | true
        "decreases" | false
    }

    def "doesn't extract a profile when the frame doesn't contain the far wings"() {
        expect:
        !extract(true, 50) { x, angstroms -> line(angstroms) }.isPresent()
    }

    def "doesn't extract a profile too close to the edge of the frame"() {
        expect:
        !extract(true, HEIGHT, 5) { x, angstroms -> line(angstroms) }.isPresent()
    }

    def "renders the profile as a chart"() {
        given:
        def profile = extract { x, angstroms -> line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x) }

        when:
        def chart = EllermanBombCharts.profile(profile, "Ellerman bomb 1: frame 5, column 100, confidence 99 %")

        then:
        chart.length == 3
        chart[0].length == Charts.HEIGHT
        chart[0][0].length == Charts.WIDTH
        chart[0].any { row -> row.any { it < 10000f } }
    }

    private static double ratioAt(EllermanBombProfile profile, double angstroms) {
        valueAt(profile, profile.detection(), angstroms) / valueAt(profile, profile.neighbours(), angstroms)
    }

    private static double valueAt(EllermanBombProfile profile, double[] values, double angstroms) {
        def offsets = profile.offsets()
        def index = (0..<offsets.length).min { Math.abs(offsets[it] - angstroms) }
        values[index]
    }

    private static double line(double angstroms) {
        CONTINUUM * (1 - 0.8d * Math.exp(-(angstroms / 0.5d) ** 2))
    }

    private static double wings(double angstroms) {
        Math.exp(-((Math.abs(angstroms) - 1.2d) / 0.5d) ** 2)
    }

    private static double spot(int x) {
        Math.exp(-((x - COLUMN) ** 2) / 2d)
    }

    private static EllermanBombProfile extract(Closure<Double> intensity) {
        extract(true, intensity).orElseThrow()
    }

    private static Optional<EllermanBombProfile> extract(boolean wavelengthGrowsWithRow, int height = HEIGHT, int column = COLUMN, Closure<Double> intensity) {
        def center = height / 2d
        def polynomial = { double x -> center } as DoubleUnaryOperator
        def direction = wavelengthGrowsWithRow ? 1 : -1
        def frame = new float[height][WIDTH]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < WIDTH; x++) {
                frame[y][x] = (float) intensity(x, direction * (y - center) * ANGSTROMS_PER_PIXEL)
            }
        }
        EllermanBombProfile.extract(frame, column, polynomial, Dispersion.ofAngstromsPerPixel(ANGSTROMS_PER_PIXEL), wavelengthGrowsWithRow)
    }
}
