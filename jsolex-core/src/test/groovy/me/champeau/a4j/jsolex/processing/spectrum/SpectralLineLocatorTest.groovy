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

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.sun.SpectralLineRealigner
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer
import me.champeau.a4j.jsolex.processing.util.Dispersion
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class SpectralLineLocatorTest extends Specification {

    // Fe I 5302.29, used as the reference line for corona imaging. In this scan
    // the darkest line of the crop window is a different one, 38.5px away.
    private static final SpectralRay FE_5302_RAY = SpectralRay.IRON_FE1_5302
    private static final Wavelen FE_5302 = FE_5302_RAY.wavelength()
    private static final double PIXEL_SIZE = 2.0d
    private static final SpectroHeliograph INSTRUMENT = SpectroHeliograph.MLASTRO_SHG_700

    def "locates the requested line when the darkest one is a different line"() {
        given:
        def fixture = load('/lineid/fe5302-misidentified.fits')
        def analysis = analyze(fixture)
        def dispersion = SpectrumAnalyzer.computeSpectralDispersion(INSTRUMENT, FE_5302, PIXEL_SIZE)

        when:
        def located = SpectralLineLocator.locate(profileOf(fixture, analysis), FE_5302, dispersion, fixture.height / 2d)

        then:
        located.present
        // the target sits 38.5px away from the line the detector locked onto
        Math.abs(located.get().pixelOffset() - 38.5d) <= 1d
        located.get().score() > 0.95d
        located.get().margin() > 0.1d
    }

    def "realigns the polynomial onto the requested line"() {
        given:
        def fixture = load('/lineid/fe5302-misidentified.fits')
        def analysis = analyze(fixture)
        def initial = analysis.distortionPolynomial().get()
        def mid = fixture.width / 2 as int

        when:
        def realigned = SpectralLineRealigner.realign(fixture.data, fixture.width, fixture.height, false, analysis, details())

        then:
        realigned.distortionPolynomial().present
        def corrected = realigned.distortionPolynomial().get()
        // the refitted line tracks the target, ~38.5px below the initial one
        Math.abs(corrected.applyAsDouble(mid) - initial.applyAsDouble(mid) - 38.5d) <= 2d

        and: "the refitted line still follows the smile"
        def deltas = (500..3000).step(100).collect {
            corrected.applyAsDouble(it) - initial.applyAsDouble(it)
        }
        (deltas.max() - deltas.min()) < 6d
    }

    def "leaves the analysis untouched when no line is explicitly requested"() {
        given:
        def fixture = load('/lineid/fe5302-misidentified.fits')
        def analysis = analyze(fixture)

        when:
        def result = SpectralLineRealigner.realign(fixture.data, fixture.width, fixture.height, false, analysis, autoDetectDetails)

        then:
        result.is(analysis)

        where:
        autoDetectDetails << [
                null,
                new SpectrumAnalyzer.QueryDetails(FE_5302_RAY, 0d, 1, INSTRUMENT),
                new SpectrumAnalyzer.QueryDetails(FE_5302_RAY, PIXEL_SIZE, 0, INSTRUMENT)
        ]
    }

    /**
     * Saturated disk mode reuses the polynomial of a reference scan for every
     * saturated scan, so a misidentified line there propagates to the whole
     * session. This exercises the exact call ReferencePolynomialProvider makes.
     */
    def "realigns the polynomial of a saturated disk mode reference scan"() {
        given: "the average image of a reference scan, as computed by ReferencePolynomialProvider"
        def fixture = load('/lineid/fe5302-misidentified.fits')
        def analyzer = new SpectrumFrameAnalyzer(fixture.width, fixture.height, false, null)
        def analysis = analyzer.analyze(fixture.data)

        when:
        def realigned = SpectralLineRealigner.realign(fixture.data, fixture.width, fixture.height, false, analysis, details())
        def quadruplet = realigned.distortionQuadruplet()

        then: "a quadruplet is produced, since that is what gets cached and reused"
        quadruplet.present

        and: "it describes the requested line, not the darkest one"
        def mid = fixture.width / 2 as int
        def corrected = quadruplet.get().asPolynomial()
        Math.abs(corrected.applyAsDouble(mid) - analysis.distortionPolynomial().get().applyAsDouble(mid) - 38.5d) <= 2d
    }

    def "declines when the profile carries no usable line structure"() {
        given: "a featureless profile: nothing can be matched against the atlas"
        def profile = (0..<120).collect {
            new SpectrumAnalyzer.DataPoint(FE_5302, it - 60d, 1000d + ((it * 37) % 5))
        }

        when:
        def located = SpectralLineLocator.locate(profile, FE_5302, Dispersion.ofAngstromsPerPixel(0.105d), 60d)

        then:
        !located.present
    }

    /**
     * The continuum normalization window is derived from the dispersion and from
     * the profile length, so it takes any size and parity. Narrow cropping windows
     * are the common case for reference lines.
     */
    def "never fails on any combination of profile length and dispersion (#length px, #angstromsPerPixel A/px)"() {
        given:
        def dispersion = Dispersion.ofAngstromsPerPixel(angstromsPerPixel)
        def profile = (0..<length).collect { i ->
            def shift = i - length / 2d
            def wl = FE_5302.angstroms() + shift * angstromsPerPixel
            new SpectrumAnalyzer.DataPoint(FE_5302, shift, ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl)))
        }

        when:
        def located = SpectralLineLocator.locate(profile, FE_5302, dispersion, length / 2d)

        then:
        noExceptionThrown()
        located != null

        where:
        [length, angstromsPerPixel] << [
                (16..64).toList() + [80, 104, 136, 200],
                [0.03d, 0.0606d, 0.0909d, 0.105d, 0.126d, 0.2d]
        ].combinations()
    }

    /**
     * With a search range too narrow to hold any alternative, the margin cannot be
     * measured and the match must not be reported as unambiguous.
     */
    def "declines when the search range admits no competing offset"() {
        given:
        def dispersion = Dispersion.ofAngstromsPerPixel(0.105d)
        def profile = (0..<120).collect { i ->
            def shift = i - 60d
            def wl = FE_5302.angstroms() + shift * dispersion.angstromsPerPixel()
            new SpectrumAnalyzer.DataPoint(FE_5302, shift, ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl)))
        }

        expect: "the perfectly matching profile is still refused, for lack of a comparison"
        !SpectralLineLocator.locate(profile, FE_5302, dispersion, 2d).present

        and: "a range wide enough to hold competitors resolves it"
        SpectralLineLocator.locate(profile, FE_5302, dispersion, 40d).present
    }

    def "completes quickly on a full height window"() {
        given: "an uncropped sensor height, the worst case for the search"
        def dispersion = Dispersion.ofAngstromsPerPixel(0.06d)
        def profile = (0..<1000).collect { i ->
            def shift = i - 500d
            def wl = FE_5302.angstroms() + (shift - 120d) * dispersion.angstromsPerPixel()
            new SpectrumAnalyzer.DataPoint(FE_5302, shift, ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl)))
        }

        when:
        def start = System.nanoTime()
        def located = SpectralLineLocator.locate(profile, FE_5302, dispersion, 500d)
        def elapsedMillis = (System.nanoTime() - start) / 1_000_000d

        then:
        located.present
        Math.abs(located.get().pixelOffset() - 120d) <= 1d

        and:
        elapsedMillis < 20_000
    }

    def "exposes the corona reference line as a predefined line"() {
        expect:
        SpectralRay.predefined().contains(SpectralRay.IRON_FE1_5302)
        Math.abs(SpectralRay.IRON_FE1_5302.wavelength().angstroms() - 5302.29d) < 1e-6
        !SpectralRay.IRON_FE1_5302.emission()
    }

    def "declines when the profile is too short to be conclusive"() {
        given:
        def profile = (0..<8).collect { new SpectrumAnalyzer.DataPoint(FE_5302, it - 4d, 1000d) }

        when:
        def located = SpectralLineLocator.locate(profile, FE_5302, Dispersion.ofAngstromsPerPixel(0.105d), 4d)

        then:
        !located.present
    }

    def "recovers a synthetic offset built from the reference spectrum itself"() {
        given: "a profile sampled from the atlas, offset by a known amount"
        def dispersion = Dispersion.ofAngstromsPerPixel(0.105d)
        def profile = (0..<120).collect { i ->
            def shift = i - 60d
            def wl = FE_5302.angstroms() + (shift - expectedOffset) * dispersion.angstromsPerPixel()
            new SpectrumAnalyzer.DataPoint(FE_5302, shift, ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl)))
        }

        when:
        def located = SpectralLineLocator.locate(profile, FE_5302, dispersion, 60d)

        then:
        located.present
        Math.abs(located.get().pixelOffset() - expectedOffset) <= 1d

        where:
        expectedOffset << [-25d, -10d, 0d, 12d, 30d]
    }


    private static Fixture load(String resource) {
        def file = new File(SpectralLineLocatorTest.getResource(resource).toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        return new Fixture(image.width(), image.height(), image.data())
    }

    private static SpectrumFrameAnalyzer.Result analyze(Fixture fixture) {
        return new SpectrumFrameAnalyzer(fixture.width, fixture.height, false, null).analyze(fixture.data)
    }

    private static List<SpectrumAnalyzer.DataPoint> profileOf(Fixture fixture, SpectrumFrameAnalyzer.Result analysis) {
        return SpectrumAnalyzer.computeDataPoints(details(),
                analysis.distortionPolynomial().get(),
                analysis.leftBorder().orElse(0),
                analysis.rightBorder().orElse(fixture.width),
                fixture.width,
                fixture.height,
                fixture.data)
    }

    private static SpectrumAnalyzer.QueryDetails details() {
        return new SpectrumAnalyzer.QueryDetails(FE_5302_RAY, PIXEL_SIZE, 1, INSTRUMENT)
    }

    private record Fixture(int width, int height, float[][] data) {
    }
}
