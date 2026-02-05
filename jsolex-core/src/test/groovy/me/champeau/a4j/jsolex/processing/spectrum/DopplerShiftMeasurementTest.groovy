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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification
import spock.lang.Unroll

class DopplerShiftMeasurementTest extends Specification {

    static final double LAMBDA0 = 6562.8
    static final double CONTINUUM = 1000.0
    static final double AMPLITUDE = 500.0
    static final double SIGMA = 0.3
    static final double GAMMA = 0.15
    static final double WAVELENGTH_STEP = 0.04
    static final double PROFILE_HALF_RANGE = 5.0
    static final double WINDOW_HALF_WIDTH = 2.0

    @Unroll
    def "Voigt fit detects shift of #shiftAngstroms Angstroms without noise"() {
        given:
        def measurement = DopplerLineCenterMeasurement.voigtFit(WINDOW_HALF_WIDTH)
        def eastCenter = LAMBDA0 - shiftAngstroms / 2.0
        def westCenter = LAMBDA0 + shiftAngstroms / 2.0
        def eastProfile = generateProfile(eastCenter, 0.0)
        def westProfile = generateProfile(westCenter, 0.0)

        when:
        def eastResult = measurement.measureLineCenter(eastProfile)
        def westResult = measurement.measureLineCenter(westProfile)

        then:
        eastResult.isPresent()
        westResult.isPresent()

        and:
        def measuredShift = westResult.getAsDouble() - eastResult.getAsDouble()
        Math.abs(measuredShift - shiftAngstroms) < tolerance

        where:
        shiftAngstroms | tolerance
        0.20           | 0.02
        0.10           | 0.02
        0.04           | 0.01
        0.02           | 0.01
        0.01           | 0.005
    }

    @Unroll
    def "center of gravity detects shift of #shiftAngstroms Angstroms without noise"() {
        given:
        def measurement = DopplerLineCenterMeasurement.centerOfGravity(WINDOW_HALF_WIDTH)
        def eastCenter = LAMBDA0 - shiftAngstroms / 2.0
        def westCenter = LAMBDA0 + shiftAngstroms / 2.0
        def eastProfile = generateProfile(eastCenter, 0.0)
        def westProfile = generateProfile(westCenter, 0.0)

        when:
        def eastResult = measurement.measureLineCenter(eastProfile)
        def westResult = measurement.measureLineCenter(westProfile)

        then:
        eastResult.isPresent()
        westResult.isPresent()

        and:
        def measuredShift = westResult.getAsDouble() - eastResult.getAsDouble()
        Math.abs(measuredShift - shiftAngstroms) < tolerance

        where:
        shiftAngstroms | tolerance
        0.20           | 0.02
        0.10           | 0.02
        0.04           | 0.01
        0.02           | 0.01
        0.01           | 0.005
    }

    @Unroll
    def "Voigt fit detects shift of #shiftAngstroms Angstroms with noise level #noiseStdDev"() {
        given:
        def measurement = DopplerLineCenterMeasurement.voigtFit(WINDOW_HALF_WIDTH)
        def eastCenter = LAMBDA0 - shiftAngstroms / 2.0
        def westCenter = LAMBDA0 + shiftAngstroms / 2.0
        def eastProfile = generateProfile(eastCenter, noiseStdDev)
        def westProfile = generateProfile(westCenter, noiseStdDev)

        when:
        def eastResult = measurement.measureLineCenter(eastProfile)
        def westResult = measurement.measureLineCenter(westProfile)

        then:
        eastResult.isPresent()
        westResult.isPresent()

        and:
        def measuredShift = westResult.getAsDouble() - eastResult.getAsDouble()
        Math.abs(measuredShift - shiftAngstroms) < tolerance

        where:
        shiftAngstroms | noiseStdDev | tolerance
        0.10           | 5.0         | 0.03
        0.10           | 20.0        | 0.05
        0.04           | 5.0         | 0.02
        0.04           | 20.0        | 0.04
    }

    @Unroll
    def "center of gravity detects shift of #shiftAngstroms Angstroms with noise level #noiseStdDev"() {
        given:
        def measurement = DopplerLineCenterMeasurement.centerOfGravity(WINDOW_HALF_WIDTH)
        def eastCenter = LAMBDA0 - shiftAngstroms / 2.0
        def westCenter = LAMBDA0 + shiftAngstroms / 2.0
        def eastProfile = generateProfile(eastCenter, noiseStdDev)
        def westProfile = generateProfile(westCenter, noiseStdDev)

        when:
        def eastResult = measurement.measureLineCenter(eastProfile)
        def westResult = measurement.measureLineCenter(westProfile)

        then:
        eastResult.isPresent()
        westResult.isPresent()

        and:
        def measuredShift = westResult.getAsDouble() - eastResult.getAsDouble()
        Math.abs(measuredShift - shiftAngstroms) < tolerance

        where:
        shiftAngstroms | noiseStdDev | tolerance
        0.10           | 5.0         | 0.03
        0.10           | 20.0        | 0.05
        0.04           | 5.0         | 0.02
        0.04           | 20.0        | 0.04
    }

    def "both methods agree on the same profile pair"() {
        given:
        def shiftAngstroms = 0.08
        def eastCenter = LAMBDA0 - shiftAngstroms / 2.0
        def westCenter = LAMBDA0 + shiftAngstroms / 2.0
        def eastProfile = generateProfile(eastCenter, 10.0)
        def westProfile = generateProfile(westCenter, 10.0)

        when: "measure with Voigt fit"
        def voigtMeasurement = DopplerLineCenterMeasurement.voigtFit(WINDOW_HALF_WIDTH)
        def voigtEast = voigtMeasurement.measureLineCenter(eastProfile)
        def voigtWest = voigtMeasurement.measureLineCenter(westProfile)
        def voigtShift = voigtWest.getAsDouble() - voigtEast.getAsDouble()

        and: "measure with center of gravity"
        def cogMeasurement = DopplerLineCenterMeasurement.centerOfGravity(WINDOW_HALF_WIDTH)
        def cogEast = cogMeasurement.measureLineCenter(eastProfile)
        def cogWest = cogMeasurement.measureLineCenter(westProfile)
        def cogShift = cogWest.getAsDouble() - cogEast.getAsDouble()

        then:
        Math.abs(voigtShift - shiftAngstroms) < 0.03
        Math.abs(cogShift - shiftAngstroms) < 0.03
        Math.abs(voigtShift - cogShift) < 0.03
    }

    private static List<SpectrumAnalyzer.DataPoint> generateProfile(double lineCenter, double noiseStdDev) {
        def random = new Random(42)
        def peakVoigt = VoigtFitter.pseudoVoigt(0, Math.max(0.001, SIGMA), Math.max(0.001, GAMMA))
        def points = []
        for (double wl = LAMBDA0 - PROFILE_HALF_RANGE; wl <= LAMBDA0 + PROFILE_HALF_RANGE; wl += WAVELENGTH_STEP) {
            double x = wl - lineCenter
            double voigtValue = VoigtFitter.pseudoVoigt(x, Math.max(0.001, SIGMA), Math.max(0.001, GAMMA))
            double normalizedVoigt = voigtValue / peakVoigt
            double intensity = CONTINUUM - AMPLITUDE * normalizedVoigt
            if (noiseStdDev > 0) {
                intensity += random.nextGaussian() * noiseStdDev
            }
            points << new SpectrumAnalyzer.DataPoint(Wavelen.ofAngstroms(wl), 0, intensity)
        }
        return points
    }
}
