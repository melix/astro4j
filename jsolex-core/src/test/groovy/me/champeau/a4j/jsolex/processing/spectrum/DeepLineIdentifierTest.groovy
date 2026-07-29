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
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class DeepLineIdentifierTest extends Specification {

    def "identifies the line a scan is centred on, without a candidate list"() {
        given: "a scan whose window is centred on a line which is not in the known lines"
        def fixture = load('/lineid/fe5302-misidentified.fits')

        when:
        def identified = DeepLineIdentifier.identify(profileOf(fixture),
                SpectroHeliograph.MLASTRO_SHG_700, 2.0d, 1)

        then: "the detected line is the neighbour of Fe 5302, 38.5 px away from it"
        identified.present
        Math.abs(identified.get().wavelength().angstroms() - 5298.26d) < 1d

        and: "the margin is a fraction of the headroom above the runner up"
        identified.get().score() > 0.9d
        identified.get().margin() >= 0.30d
        identified.get().margin() <= 1d
    }

    def "identifies H-alpha, whose correlation saturates against many other deep lines"() {
        given:
        def fixture = load('/average/Ha/12_39_10.ser-average.fits')

        when:
        def identified = DeepLineIdentifier.identify(profileOf(fixture), SpectroHeliograph.SOLEX, 2.4d, 1, 2)

        then:
        identified.present
        Math.abs(identified.get().wavelength().angstroms() - 6562.81d) < 1.5d

        and: "the absolute lead is tiny, which is why it is measured relatively"
        def ranked = DeepLineIdentifier.rank(profileOf(fixture), SpectroHeliograph.SOLEX, 2.4d, 1, 2)
        def runnerUp = ranked.find { Math.abs(it.wavelength().angstroms() - 6562.81d) >= 3d }
        (ranked[0].score() - runnerUp.score()) < 0.03d
        identified.get().margin() > 0.5d
    }

    def "identifies known lines on real scans (#resource)"() {
        given:
        def fixture = load(resource)

        when:
        def identified = DeepLineIdentifier.identify(profileOf(fixture), SpectroHeliograph.SOLEX, 2.4d, 1, 2)

        then:
        identified.present
        Math.abs(identified.get().wavelength().angstroms() - expected) < 1.5d

        where:
        resource                                     | expected
        '/average/caK/12_17_12.ser-average.fits'     | 3933.66d
        '/average/caH/12_06_28.ser-average.fits'     | 3968.47d
        '/average/Hb/11_22_43.ser-average.fits'      | 4861.34d
        '/average/Mag/12_01_12.ser-average.fits'     | 5183.62d
    }

    def "declines rather than guessing on a window too narrow to identify"() {
        given: "an Ellipse capture, whose window spans about two angstroms"
        def fixture = load('/average/Ha/11_31_35_Ellipse_DECP_0deg.ser-average.fits')

        expect: "such a window reaches very high correlations for the wrong line"
        !DeepLineIdentifier.identify(profileOf(fixture), SpectroHeliograph.SOLEX, 2.4d, 1, 2).present
    }

    /**
     * The binning is only a hypothesis, and it scales the amount of spectrum a pixel is
     * assumed to cover. Reading the width optimistically lets narrow windows through,
     * which is precisely where wrong answers come from.
     */
    def "judges the window width on the narrowest binning hypothesis"() {
        given: "a window which is too narrow at binning 1 but would pass at binning 2"
        def fixture = load('/average/Ha/11_31_35_Ellipse_DECP_0deg.ser-average.fits')
        def profile = profileOf(fixture)

        expect:
        !DeepLineIdentifier.identify(profile, SpectroHeliograph.SOLEX, 2.4d, 1, 2).present
        !DeepLineIdentifier.identify(profile, SpectroHeliograph.SOLEX, 2.4d, 2, 1).present
        DeepLineIdentifier.rank(profile, SpectroHeliograph.SOLEX, 2.4d, 1, 2).isEmpty()
    }

    def "every known absorption line is deep enough to be a candidate"() {
        given: "a line which is not a candidate can never be identified, whatever the scan"
        def candidates = DeepLineIdentifier.candidateSet()

        expect:
        SpectralRay.predefined()
                .findAll { it.wavelength().angstroms() > 0 && !it.emission() }
                .findAll { ray -> candidates.every { Math.abs(it - ray.wavelength().angstroms()) > 1.5d } }
                .collect { it.label() } == []
    }

    def "declines when the instrument is not configured"() {
        given:
        def fixture = load('/lineid/fe5302-misidentified.fits')
        def profile = profileOf(fixture)

        expect:
        !DeepLineIdentifier.identify(profile, SpectroHeliograph.MLASTRO_SHG_700, 0d, 1).present
        !DeepLineIdentifier.identify(profile, null, 2.0d, 1).present
        !DeepLineIdentifier.identify(profile, SpectroHeliograph.MLASTRO_SHG_700, 2.0d).present
    }

    def "declines on a profile too short to be conclusive"() {
        given:
        def profile = (0..<8).collect {
            new SpectrumAnalyzer.DataPoint(Wavelen.ofAngstroms(5302.29), it - 4d, 1000d)
        }

        expect:
        !DeepLineIdentifier.identify(profile, SpectroHeliograph.SOLEX, 2.4d, 1).present
    }

    def "reuses a known line when the identified wavelength matches one"() {
        when:
        def ray = IdentifiedLineResolver.resolve(Wavelen.ofAngstroms(3933.68), SpectralRay.predefined())

        then: "the predefined line is returned, so its color curve and scripts still apply"
        ray.is(SpectralRay.CALCIUM_K)
    }

    /**
     * Features are gated on the label of the line, so a scan on a known line has to
     * resolve to that very line and never to a look-alike created on the fly.
     */
    def "never creates a look-alike of a line which is already known"() {
        expect:
        SpectralRay.predefined()
                .findAll { it.wavelength().angstroms() > 0 }
                .every { known ->
                    [-0.4d, -0.1d, 0d, 0.1d, 0.4d].every { offset ->
                        IdentifiedLineResolver.resolve(
                                Wavelen.ofAngstroms(known.wavelength().angstroms() + offset),
                                SpectralRay.predefined()).is(known)
                    }
                }
    }

    def "creates a ray on the fly when the wavelength matches no known line"() {
        when:
        def ray = IdentifiedLineResolver.resolve(Wavelen.ofAngstroms(5298.26), SpectralRay.predefined())

        then:
        !SpectralRay.predefined().contains(ray)
        Math.abs(ray.wavelength().angstroms() - 5298.26d) < 1e-6
        !ray.emission()

        and: "the name says the line was found automatically, and carries the wavelength"
        ray.label() == 'Auto 5298.26'
    }

    def "does not mark a ray as found automatically when the user entered its wavelength"() {
        when:
        def ray = IdentifiedLineResolver.resolveEntered(Wavelen.ofAngstroms(5298.26), SpectralRay.predefined())

        then:
        ray.label() == '5298.26'
        Math.abs(ray.wavelength().angstroms() - 5298.26d) < 1e-6

        and: "an entered wavelength which is a known line still resolves to that line"
        IdentifiedLineResolver.resolveEntered(Wavelen.ofAngstroms(6562.9), SpectralRay.predefined())
                .is(SpectralRay.H_ALPHA)
    }

    def "names a created ray after the catalog when the line is known there"() {
        when: "a wavelength which the interesting lines catalog knows but which is not a predefined ray"
        def ray = IdentifiedLineResolver.resolve(Wavelen.ofAngstroms(5324.18), SpectralRay.predefined())

        then:
        ray.label().startsWith('Fe')
        ray.label().contains('5324.18')
    }

    private static List<SpectrumAnalyzer.DataPoint> profileOf(Fixture fixture) {
        def analysis = new SpectrumFrameAnalyzer(fixture.width, fixture.height, false, null).analyze(fixture.data)
        def probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, 2.4d, 1, SpectroHeliograph.SOLEX)
        return SpectrumAnalyzer.computeDataPoints(probe, analysis.distortionPolynomial().get(),
                analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(fixture.width),
                fixture.width, fixture.height, fixture.data)
    }

    private static Fixture load(String resource) {
        def file = new File(DeepLineIdentifierTest.getResource(resource).toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        return new Fixture(image.width(), image.height(), image.data())
    }

    private record Fixture(int width, int height, float[][] data) {
    }
}
