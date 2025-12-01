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

class VoigtFitterTest extends Specification {

    def "Voigt function reduces to Gaussian when gamma is zero"() {
        given:
        double sigma = 0.5
        double gamma = 0.0

        when:
        double voigtAtCenter = VoigtFitter.voigt(0, sigma, gamma)
        double fwhm = 2.0 * sigma * Math.sqrt(2.0 * Math.log(2.0))
        double c = fwhm / (2.0 * Math.sqrt(2.0 * Math.log(2.0)))
        double gaussianAtCenter = 1.0 / (c * Math.sqrt(2 * Math.PI))

        then:
        Math.abs(voigtAtCenter - gaussianAtCenter) < 0.01 * gaussianAtCenter
    }

    def "Pseudo-Voigt function is symmetric"() {
        given:
        double sigma = 0.4
        double gamma = 0.2

        when:
        double vPlus = VoigtFitter.pseudoVoigt(0.5, sigma, gamma)
        double vMinus = VoigtFitter.pseudoVoigt(-0.5, sigma, gamma)

        then:
        Math.abs(vPlus - vMinus) < 1e-10
    }

    def "Pseudo-Voigt function has maximum at center"() {
        given:
        double sigma = 0.4
        double gamma = 0.2

        when:
        double vCenter = VoigtFitter.pseudoVoigt(0, sigma, gamma)
        double vOffCenter = VoigtFitter.pseudoVoigt(0.3, sigma, gamma)

        then:
        vCenter > vOffCenter
    }

    def "voigtFWHM matches pseudo-Voigt half-maximum when gamma is near zero"() {
        given:
        double sigma = 0.5
        double gamma = 0.001

        when:
        double fwhm = VoigtFitter.voigtFWHM(sigma, gamma)
        double peak = VoigtFitter.pseudoVoigt(0, sigma, gamma)
        double halfPeak = peak / 2.0
        double valueAtHalfFwhm = VoigtFitter.pseudoVoigt(fwhm / 2.0, sigma, gamma)

        then:
        Math.abs(valueAtHalfFwhm - halfPeak) < 0.001 * peak
    }

    def "voigtFWHM matches pseudo-Voigt half-maximum when sigma is near zero"() {
        given:
        double sigma = 0.001
        double gamma = 0.5

        when:
        double fwhm = VoigtFitter.voigtFWHM(sigma, gamma)
        double peak = VoigtFitter.pseudoVoigt(0, sigma, gamma)
        double halfPeak = peak / 2.0
        double valueAtHalfFwhm = VoigtFitter.pseudoVoigt(fwhm / 2.0, sigma, gamma)

        then:
        Math.abs(valueAtHalfFwhm - halfPeak) < 0.001 * peak
    }

    def "fit recovers Gaussian parameters from synthetic data"() {
        given:
        double trueContinuum = 1000.0
        double trueAmplitude = 600.0
        double trueCenter = 6562.8
        double trueSigma = 0.4
        double trueGamma = 0.0

        def profile = generateSyntheticProfile(
            trueContinuum, trueAmplitude, trueCenter, trueSigma, trueGamma,
            6560.0, 6566.0, 0.02, 0.0
        )

        when:
        def result = VoigtFitter.fit(profile, trueContinuum, trueCenter)

        then:
        result.converged()
        Math.abs(result.continuum() - trueContinuum) < 0.05 * trueContinuum
        Math.abs(result.amplitude() - trueAmplitude) < 0.1 * trueAmplitude
        Math.abs(result.center() - trueCenter) < 0.1
    }

    def "fit recovers Voigt parameters from synthetic data"() {
        given:
        double trueContinuum = 1000.0
        double trueAmplitude = 500.0
        double trueCenter = 3933.66
        double trueSigma = 0.3
        double trueGamma = 0.2

        def profile = generateSyntheticProfile(
            trueContinuum, trueAmplitude, trueCenter, trueSigma, trueGamma,
            3930.0, 3937.0, 0.02, 0.0
        )

        when:
        def result = VoigtFitter.fit(profile, trueContinuum, trueCenter)

        then:
        result.converged()
        Math.abs(result.continuum() - trueContinuum) < 0.05 * trueContinuum
        Math.abs(result.center() - trueCenter) < 0.1

        and:
        double expectedFWHM = VoigtFitter.voigtFWHM(trueSigma, trueGamma)
        Math.abs(result.fwhm() - expectedFWHM) < 0.2 * expectedFWHM
    }

    def "fit handles noisy data"() {
        given:
        double trueContinuum = 1000.0
        double trueAmplitude = 500.0
        double trueCenter = 6562.8
        double trueSigma = 0.4
        double trueGamma = 0.15
        double noiseLevel = 20.0

        def profile = generateSyntheticProfile(
            trueContinuum, trueAmplitude, trueCenter, trueSigma, trueGamma,
            6559.0, 6567.0, 0.02, noiseLevel
        )

        when:
        def result = VoigtFitter.fit(profile, trueContinuum, trueCenter)

        then:
        result.converged()
        Math.abs(result.center() - trueCenter) < 0.2
    }

    def "fit returns failed result for insufficient data"() {
        given:
        def profile = [
            new SpectrumAnalyzer.DataPoint(Wavelen.ofAngstroms(6562.0), 0, 900),
            new SpectrumAnalyzer.DataPoint(Wavelen.ofAngstroms(6563.0), 0, 800)
        ]

        when:
        def result = VoigtFitter.fit(profile, 1000, 6562.8)

        then:
        !result.converged()
        result.fwhm() == 0
    }

    def "fit returns failed result for null profile"() {
        when:
        def result = VoigtFitter.fit(null, 1000, 6562.8)

        then:
        !result.converged()
    }

    @Unroll
    def "FWHM matches actual half-maximum crossing for sigma=#sigma, gamma=#gamma"() {
        when:
        double effectiveSigma = Math.max(0.001, sigma)
        double effectiveGamma = Math.max(0.001, gamma)
        double peak = VoigtFitter.pseudoVoigt(0, effectiveSigma, effectiveGamma)
        double halfPeak = peak / 2.0

        // Find where pseudoVoigt crosses half its peak value by scanning
        double measuredHalfWidth = 0
        for (double x = 0.0001; x < 10; x += 0.0001) {
            double v = VoigtFitter.pseudoVoigt(x, effectiveSigma, effectiveGamma)
            if (v <= halfPeak) {
                measuredHalfWidth = x
                break
            }
        }
        double measuredFwhm = 2 * measuredHalfWidth

        double computedFwhm = VoigtFitter.voigtFWHM(effectiveSigma, effectiveGamma)

        then:
        Math.abs(measuredFwhm - computedFwhm) < 0.001

        where:
        sigma | gamma
        0.5   | 0.0
        0.0   | 0.5
        0.3   | 0.2
        0.4   | 0.3
    }

    def "VoigtParameters record provides correct FWHM components"() {
        given:
        double sigma = 0.4
        double gamma = 0.3

        when:
        def params = new VoigtFitter.VoigtParameters(1000, 500, 6562.8, sigma, gamma, 10.0, true)

        then:
        Math.abs(params.gaussianFWHM() - 2.0 * sigma * Math.sqrt(2.0 * Math.log(2.0))) < 1e-10
        Math.abs(params.lorentzianFWHM() - 2.0 * gamma) < 1e-10
    }

    def "fit works with broad Ca K-like profile"() {
        given:
        double trueContinuum = 800.0
        double trueAmplitude = 600.0
        double trueCenter = 3933.66
        double trueSigma = 0.5
        double trueGamma = 0.4

        def profile = generateSyntheticProfile(
            trueContinuum, trueAmplitude, trueCenter, trueSigma, trueGamma,
            3928.0, 3939.0, 0.03, 5.0
        )

        when:
        def result = VoigtFitter.fit(profile, trueContinuum, trueCenter)

        then:
        result.converged()
        result.fwhm() > 0

        and:
        double expectedFWHM = VoigtFitter.voigtFWHM(trueSigma, trueGamma)
        Math.abs(result.fwhm() - expectedFWHM) < 0.3 * expectedFWHM
    }

    private static List<SpectrumAnalyzer.DataPoint> generateSyntheticProfile(
        double continuum, double amplitude, double center,
        double sigma, double gamma,
        double startWl, double endWl, double step, double noiseStdDev
    ) {
        def random = new Random(42)
        def points = []

        double effectiveSigma = Math.max(0.001, sigma)
        double effectiveGamma = Math.max(0.001, gamma)
        double peakVoigt = VoigtFitter.pseudoVoigt(0, effectiveSigma, effectiveGamma)

        for (double wl = startWl; wl <= endWl; wl += step) {
            double x = wl - center
            double voigtValue = VoigtFitter.pseudoVoigt(x, effectiveSigma, effectiveGamma)
            double normalizedVoigt = voigtValue / peakVoigt

            double intensity = continuum - amplitude * normalizedVoigt
            if (noiseStdDev > 0) {
                intensity += random.nextGaussian() * noiseStdDev
            }

            points << new SpectrumAnalyzer.DataPoint(Wavelen.ofAngstroms(wl), 0, intensity)
        }

        return points
    }
}
