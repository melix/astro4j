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
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class SpectralWindowIdentifierTest extends Specification {
    private static final double PIXEL_SIZE = 2.0d
    private static final SpectroHeliograph INSTRUMENT = SpectroHeliograph.MLASTRO_SHG_700

    def "identifies the centre line and the other lines of the window"() {
        given:
        def frame = load('/lineid/fe5302-misidentified.fits')
        def identifier = new SpectralWindowIdentifier()

        when:
        def identification = identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)

        then:
        identification.spectrumFound()
        identification.identified()
        Math.abs(identification.anchor().wavelength().angstroms() - 5298.26d) < 1d
        identification.anchor().binning() == 1
        identification.streak() == 1
        identification.confidence() != SpectralWindowIdentifier.Confidence.NONE
        identification.angstromsPerPixel() > 0
        Math.abs(identification.dispersionScale() - 1) <= 0.03d

        and: "the centre line is among the lines found"
        identification.lines().any { Math.abs(it.pixelShift()) < 1.5d && Math.abs(it.wavelength().angstroms() - 5298.26d) < 1d }

        and: "the lines are within the window and sorted by position"
        identification.lines().size() > 1
        identification.lines().every { Math.abs(it.pixelShift()) < frame.height() }
        identification.lines().collect { it.pixelShift() } == identification.lines().collect { it.pixelShift() }.sort()
        identification.lines().every { it.depth() > 0 }

        and: "the positions of the lines follow the dispersion"
        identification.lines().every {
            Math.abs(it.wavelength().angstroms() - (identification.anchor().wavelength().angstroms() + it.pixelShift() * identification.angstromsPerPixel())) < 2 * identification.angstromsPerPixel()
        }
    }

    def "successive frames agreeing on the same line raise the confidence"() {
        given:
        def frame = load('/lineid/fe5302-misidentified.fits')
        def identifier = new SpectralWindowIdentifier()

        when:
        def identifications = (1..4).collect {
            identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)
        }

        then:
        identifications.collect { it.streak() } == [1, 2, 3, 4]
        identifications.last().confidence() == SpectralWindowIdentifier.Confidence.HIGH
        identifications.first().confidence() < identifications.last().confidence()

        when: "the identifier is reset"
        identifier.reset()
        def afterReset = identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)

        then:
        afterReset.streak() == 1
    }

    def "a frame without spectrum is reported as such and does not break the streak"() {
        given:
        def frame = load('/lineid/fe5302-misidentified.fits')
        def identifier = new SpectralWindowIdentifier()
        def empty = new float[frame.height()][frame.width()]

        when:
        identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)
        def blank = identifier.identify(empty, frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)
        def again = identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)

        then:
        !blank.spectrumFound()
        !blank.identified()
        blank.lines().isEmpty()
        blank.confidence() == SpectralWindowIdentifier.Confidence.NONE
        again.streak() == 2
    }

    def "a window too narrow to discriminate still proposes the best line, as a guess"() {
        given: "a pixel size which makes the observed window far too narrow to tell one part of the spectrum from another"
        def frame = load('/lineid/fe5302-misidentified.fits')
        def identifier = new SpectralWindowIdentifier()

        when:
        def guess = identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, 1.0d, 1, 1)

        then: "the best hypothesis is reported rather than nothing at all"
        guess.spectrumFound()
        guess.identified()
        guess.anchor() != null
        !guess.lines().isEmpty()

        and: "it is marked as a guess, the competition being too close to settle"
        guess.confidence() == SpectralWindowIdentifier.Confidence.NONE
        guess.anchor().margin() < 0.3d

        when: "the next frame is just as ambiguous"
        def tracked = identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, 1.0d, 1, 1)

        then: "the line of the previous frame is kept, which is worth a little more than a guess"
        tracked.anchor().wavelength() == guess.anchor().wavelength()
        tracked.confidence() == SpectralWindowIdentifier.Confidence.LOW
        tracked.streak() == 2
    }

    def "identifies on a running average of the frames"() {
        given:
        def frame = load('/lineid/fe5302-misidentified.fits')
        def identifier = new SpectralWindowIdentifier()

        when:
        def identifications = (1..3).collect {
            identifier.identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 4, 1)
        }

        then:
        identifications.every { Math.abs(it.anchor().wavelength().angstroms() - 5298.26d) < 1d }
        identifications.last().streak() == 3
    }

    def "a frame whose rows run the other way is turned over, and reported in its own coordinates"() {
        given: "the fixture upside down, as a capture software reading the sensor bottom-up delivers it"
        def frame = load('/lineid/fe5302-misidentified.fits')
        float[][] upsideDown = new float[frame.height()][]
        for (int y = 0; y < frame.height(); y++) {
            upsideDown[y] = frame.data()[frame.height() - 1 - y]
        }

        when:
        def straight = new SpectralWindowIdentifier().identify(frame.data(), frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)
        def turned = new SpectralWindowIdentifier().identify(upsideDown, frame.width(), frame.height(), INSTRUMENT, PIXEL_SIZE, 1, 1)

        then: "the orientation is detected from the curvature of the lines"
        !straight.flipped()
        turned.flipped()

        and: "the identification is the same"
        turned.anchor().wavelength() == straight.anchor().wavelength()
        Math.abs(turned.anchor().score() - straight.anchor().score()) < 1e-6d
        turned.lines().collect { it.wavelength().angstroms() }.sort() == straight.lines().collect { it.wavelength().angstroms() }.sort()

        and: "the polynomial and the lines are expressed in the coordinates of the frame as received, where the rows are counted from the other end"
        [0, frame.width().intdiv(2), frame.width() - 1].every { x ->
            Math.abs(turned.polynomial().asPolynomial().applyAsDouble(x) - (frame.height() - 1 - straight.polynomial().asPolynomial().applyAsDouble(x))) < 1e-6d
        }
        turned.lines().collect { -it.pixelShift() }.sort() == straight.lines().collect { it.pixelShift() }.sort()
        turned.lines().collect { it.pixelShift() } == turned.lines().collect { it.pixelShift() }.sort()
    }

    def "identifies a window as tall as a sensor, where the dispersion is no longer constant"() {
        given: "a frame built from the reference spectrum, each row carrying the wavelength the instrument puts there"
        int height = 1200
        int width = 160
        double anchor = 6562.81d
        double[] wavelengths = wavelengthPerRow(height, anchor)
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            float value = (float) (ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wavelengths[y])) * 40)
            Arrays.fill(data[y], value)
        }

        when:
        def identification = new SpectralWindowIdentifier().identify(data, width, height, INSTRUMENT, PIXEL_SIZE, 1, 1)

        then: "the centre line is identified, the whole height of the window backing it"
        identification.identified()
        Math.abs(identification.anchor().wavelength().angstroms() - anchor) < 0.5d
        identification.anchor().score() > 0.9d
        identification.confidence() != SpectralWindowIdentifier.Confidence.NONE

        and: "such a window carries far more lines than a narrow one"
        identification.lines().size() > 30

        and: "each line is where the dispersion puts it, including at the edges of the window, where assuming a constant dispersion would be several pixels out"
        identification.lines().every {
            Math.abs(it.wavelength().angstroms() - wavelengthAt(wavelengths, height, it.pixelShift())) < 2 * identification.angstromsPerPixel()
        }
    }

    /**
     * The wavelength of each row of a frame centred on a line, integrating the dispersion
     * of the instrument row by row rather than assuming it constant.
     */
    private static double[] wavelengthPerRow(int height, double anchor) {
        var wavelengths = new double[height]
        int centre = height.intdiv(2)
        wavelengths[centre] = anchor
        for (int y = centre + 1; y < height; y++) {
            wavelengths[y] = step(wavelengths[y - 1], 1)
        }
        for (int y = centre - 1; y >= 0; y--) {
            wavelengths[y] = step(wavelengths[y + 1], -1)
        }
        wavelengths
    }

    private static double step(double wavelength, int direction) {
        double k1 = dispersionAt(wavelength)
        double k2 = dispersionAt(wavelength + direction * 0.5 * k1)
        double k3 = dispersionAt(wavelength + direction * 0.5 * k2)
        double k4 = dispersionAt(wavelength + direction * k3)
        wavelength + direction * (k1 + 2 * k2 + 2 * k3 + k4) / 6
    }

    private static double wavelengthAt(double[] wavelengths, int height, double pixelShift) {
        double row = Math.clamp(height.intdiv(2) + pixelShift, 0d, (double) (wavelengths.length - 1))
        int low = (int) Math.floor(row)
        int high = Math.min(wavelengths.length - 1, low + 1)
        wavelengths[low] + (wavelengths[high] - wavelengths[low]) * (row - low)
    }

    private static double dispersionAt(double angstroms) {
        SpectrumAnalyzer.computeSpectralDispersion(INSTRUMENT, Wavelen.ofAngstroms(angstroms), PIXEL_SIZE).angstromsPerPixel()
    }

    private static Frame load(String resource) {
        def file = new File(SpectralWindowIdentifierTest.getResource(resource).toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        return new Frame(image.width(), image.height(), image.data())
    }

    private record Frame(int width, int height, float[][] data) {
    }
}
