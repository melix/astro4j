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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Evaluation only: prints the hypotheses the shipped identification actually
 * considered, so a failure to identify a line can be explained instead of guessed at.
 * Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class HalphaAmbiguityDiagnostic extends Specification {

    def "prints the ranking the identification produces on #resource"() {
        given:
        def fixture = load(resource)
        def analysis = new SpectrumFrameAnalyzer(fixture.width, fixture.height, false, null).analyze(fixture.data)
        def probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, pixelSize, 1, instrument)
        def profile = SpectrumAnalyzer.computeDataPoints(probe, analysis.distortionPolynomial().get(),
                analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(fixture.width),
                fixture.width, fixture.height, fixture.data)
        def dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument,
                SpectralRay.H_ALPHA.wavelength(), pixelSize).angstromsPerPixel()

        when:
        def ranked = DeepLineIdentifier.rank(profile, instrument, pixelSize, binnings as int[])

        then:
        println String.format(Locale.US, '\n=== %s: %d points, %.2f A wide, truth %.2f ===',
                resource.substring(resource.lastIndexOf('/') + 1), profile.size(),
                profile.size() * dispersion, truth)
        println 'rank  wavelength   score  binning   distance to truth'
        ranked.take(12).eachWithIndex { r, i ->
            println String.format(Locale.US, '%4d %11.2f %7.3f %8d %10.2f A',
                    i + 1, r.wavelength().angstroms(), r.score(), r.binning(),
                    r.wavelength().angstroms() - truth)
        }
        var atTruth = ranked.findIndexOf { Math.abs(it.wavelength().angstroms() - truth) < 1.5 }
        if (atTruth >= 0) {
            println String.format(Locale.US, 'truth is ranked %d with score %.3f',
                    atTruth + 1, ranked[atTruth].score())
        } else {
            println 'truth is not among the candidates at all'
        }
        true

        where:
        resource                                    | instrument                          | pixelSize | binnings | truth
        '/average/Ha/12_39_10.ser-average.fits'     | SpectroHeliograph.SOLEX             | 2.4d      | [1, 2]   | 6562.81d
        '/average/Ha/14_39_41.ser-average.fits'     | SpectroHeliograph.SOLEX             | 2.4d      | [1, 2]   | 6562.81d
        '/lineid/fe5302-misidentified.fits'         | SpectroHeliograph.MLASTRO_SHG_700   | 2.0d      | [1]      | 5298.26d
        '/average/Iron_Fe1/09_50_33.ser-average.fits' | SpectroHeliograph.SOLEX           | 2.4d      | [1, 2]   | 5883.82d
    }

    private static Fixture load(String resource) {
        def file = new File(HalphaAmbiguityDiagnostic.getResource(resource).toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        return new Fixture(image.width(), image.height(), image.data())
    }

    private record Fixture(int width, int height, float[][] data) {
    }
}
