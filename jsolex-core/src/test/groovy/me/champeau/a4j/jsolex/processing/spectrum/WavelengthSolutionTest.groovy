/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class WavelengthSolutionTest extends Specification {
    private static final SpectroHeliograph INSTRUMENT = SpectroHeliograph.MLASTRO_SHG_700
    private static final double PIXEL_SIZE = 2.0d

    def "follows the integrated dispersion far from the centre of the window"() {
        given: "the exact solution, integrated pixel by pixel"
        def solution = WavelengthSolution.around(INSTRUMENT, anchor, PIXEL_SIZE, 1d)
        def dispersion = dispersionAt(anchor)

        expect:
        Math.abs(solution.wavelengthAt(shift) - integrate(anchor, shift)) / dispersion < 0.01d

        where:
        anchor  | shift
        6562.81 | 0
        6562.81 | 200
        6562.81 | -200
        6562.81 | 1090
        6562.81 | -1090
        5895.92 | 1090
        5895.92 | -1090
        4340.47 | 1090
        4340.47 | -1090
    }

    def "assuming a constant dispersion is what it departs from"() {
        given:
        def solution = WavelengthSolution.around(INSTRUMENT, 6562.81d, PIXEL_SIZE, 1d)
        def dispersion = dispersionAt(6562.81d)

        when: "the window spans the height of a sensor"
        def constant = 6562.81d + 1090 * dispersion
        def error = Math.abs(constant - solution.wavelengthAt(1090)) / dispersion

        then: "a constant dispersion is several pixels out, which is more than a line is wide"
        error > 5d
    }

    def "the dispersion at the centre of the window is the one of the instrument"() {
        given:
        def solution = WavelengthSolution.around(INSTRUMENT, 6562.81d, PIXEL_SIZE, scale)

        expect:
        Math.abs(solution.angstromsPerPixel() - scale * dispersionAt(6562.81d)) < 1e-9d

        where:
        scale << [1d, 0.97d, 1.03d]
    }

    def "scaling the dispersion walks the same solution faster"() {
        given:
        def scaled = WavelengthSolution.around(INSTRUMENT, 6562.81d, PIXEL_SIZE, 1.02d)
        def unscaled = WavelengthSolution.around(INSTRUMENT, 6562.81d, PIXEL_SIZE, 1d)

        expect:
        Math.abs(scaled.wavelengthAt(500) - unscaled.wavelengthAt(510)) < 1e-9d
    }

    private static double dispersionAt(double angstroms) {
        SpectrumAnalyzer.computeSpectralDispersion(INSTRUMENT, Wavelen.ofAngstroms(angstroms), PIXEL_SIZE).angstromsPerPixel()
    }

    /** Solves dlambda/dn = dispersion(lambda) by Runge-Kutta, one pixel at a time. */
    private static double integrate(double anchor, int shift) {
        double wavelength = anchor
        int direction = shift < 0 ? -1 : 1
        for (int i = 0; i < Math.abs(shift); i++) {
            double k1 = dispersionAt(wavelength)
            double k2 = dispersionAt(wavelength + direction * 0.5 * k1)
            double k3 = dispersionAt(wavelength + direction * 0.5 * k2)
            double k4 = dispersionAt(wavelength + direction * k3)
            wavelength += direction * (k1 + 2 * k2 + 2 * k3 + k4) / 6
        }
        wavelength
    }
}
