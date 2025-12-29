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
package me.champeau.a4j.jsolex.app.listeners

import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer
import me.champeau.a4j.jsolex.processing.util.Dispersion
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class SpectralProfileHelperTest extends Specification {

    def "computeWavelength returns lambda when dispersion is null"() {
        given:
        def lambda = Wavelen.ofNanos(656280000)
        def pixelShift = 5.0d

        when:
        def result = SpectralProfileHelper.computeWavelength(pixelShift, lambda, null)

        then:
        result == lambda
    }

    def "computeWavelength applies dispersion correctly"() {
        given:
        def lambda = Wavelen.ofNanos(656280000)
        def dispersion = Dispersion.ofNanosPerPixel(10000)
        def pixelShift = 5.0d

        when:
        def result = SpectralProfileHelper.computeWavelength(pixelShift, lambda, dispersion)

        then:
        result.nanos() == 656280000 + (5 * 10000)
    }

    def "normalizeDatapoints normalizes to percentage scale"() {
        given:
        def dataPoints = [
            new SpectrumAnalyzer.DataPoint(Wavelen.ofNanos(656280000), 0, 50.0d),
            new SpectrumAnalyzer.DataPoint(Wavelen.ofNanos(656290000), 1, 100.0d),
            new SpectrumAnalyzer.DataPoint(Wavelen.ofNanos(656300000), 2, 75.0d)
        ]

        when:
        def result = SpectralProfileHelper.normalizeDatapoints(dataPoints, null)

        then:
        result.maxIntensity() == 100.0d
        result.dataPoints().size() == 3
        result.dataPoints()[0].intensity() == 50.0d
        result.dataPoints()[1].intensity() == 100.0d
        result.dataPoints()[2].intensity() == 75.0d
    }

    def "normalizeDatapoints uses provided maxIntensity"() {
        given:
        def dataPoints = [
            new SpectrumAnalyzer.DataPoint(Wavelen.ofNanos(656280000), 0, 50.0d),
            new SpectrumAnalyzer.DataPoint(Wavelen.ofNanos(656290000), 1, 100.0d)
        ]

        when:
        def result = SpectralProfileHelper.normalizeDatapoints(dataPoints, 200.0d)

        then:
        result.maxIntensity() == 100.0d
        result.dataPoints()[0].intensity() == 25.0d
        result.dataPoints()[1].intensity() == 50.0d
    }

    def "formatWavelength returns wavelength in angstroms when positive"() {
        given:
        def wavelength = Wavelen.ofAngstroms(6562.8d)

        when:
        def result = SpectralProfileHelper.formatWavelength(0, wavelength)

        then:
        result == "6562.80"
    }

    def "formatWavelength returns pixel shift when wavelength is zero"() {
        given:
        def wavelength = Wavelen.ofAngstroms(0)
        def pixelShift = 3.5d

        when:
        def result = SpectralProfileHelper.formatWavelength(pixelShift, wavelength)

        then:
        result == "3.50px"
    }
}
