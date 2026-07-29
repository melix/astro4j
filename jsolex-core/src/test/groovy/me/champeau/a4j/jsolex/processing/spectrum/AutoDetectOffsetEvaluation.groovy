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
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.util.stream.Collectors

/**
 * Evaluation only: compares the current AUTO line detection against a variant
 * which lets each candidate line slide inside the window before being scored.
 * Prints an accuracy table; asserts nothing. Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class AutoDetectOffsetEvaluation extends Specification {

    private static final double OFFSET_STEP = 1d

    def "compares fixed and offset aware AUTO detection"() {
        given:
        def fixtures = []
        new File(AutoDetectOffsetEvaluation.getResource('/average').toURI()).listFiles().each { dir ->
            if (dir.directory) {
                dir.listFiles().each { f ->
                    if (f.name.endsWith('.fits')) {
                        fixtures << [file: f, expected: lineName(dir.name)]
                    }
                }
            }
        }

        when:
        var fixedOk = 0
        var offsetOk = 0
        var rows = []
        fixtures.each { fixture ->
            def image = (ImageWrapper32) FitsUtils.readFitsFile(fixture.file)
            def width = image.width()
            def height = image.height()
            def data = image.data()
            def analysis = new SpectrumFrameAnalyzer(width, height, false, null).analyze(data)
            def polynomial = analysis.distortionPolynomial().orElse(null)
            if (polynomial == null) {
                rows << [fixture.file.name, fixture.expected, 'NO POLYNOMIAL', '', 0d, 0d]
                return
            }
            int left = analysis.leftBorder().orElse(0)
            int right = analysis.rightBorder().orElse(width - 1)

            def candidates = []
            for (var line : SpectralRay.predefined()) {
                if (line.wavelength().angstroms() > 0 && !line.emission()) {
                    candidates << new SpectrumAnalyzer.QueryDetails(line, 2.4d, 1, SpectroHeliograph.SOLEX)
                    candidates << new SpectrumAnalyzer.QueryDetails(line, 2.4d, 2, SpectroHeliograph.SOLEX)
                }
            }
            def map = candidates.stream().collect(Collectors.toMap(d -> d,
                    d -> SpectrumAnalyzer.computeDataPoints(d, polynomial, left, right, width, height, data),
                    (a, b) -> a, LinkedHashMap::new))

            def fixedBest = SpectrumAnalyzer.findBestMatch(map)
            def offsetBest = bestWithOffset(map)
            def zeroBest = bestAtZero(map)

            if (fixedBest?.line()?.label() == fixture.expected) {
                fixedOk++
            }
            if (offsetBest.query?.line()?.label() == fixture.expected) {
                offsetOk++
            }
            rows << [fixture.file.name, fixture.expected, fixedBest?.line()?.label(),
                     offsetBest.query?.line()?.label(), offsetBest.offset, offsetBest.score, zeroBest.score]
        }

        then:
        println "\n=== AUTO detection: fixed vs offset aware (${fixtures.size()} fixtures) ==="
        println String.format('%-40s %-16s %-16s %-16s %7s %7s %7s', 'fixture', 'expected', 'current', 'offset aware', 'offset', 'score', 'at zero')
        rows.each { r ->
            def flagFixed = r[2] == r[1] ? ' ' : 'x'
            def flagOffset = r[3] == r[1] ? ' ' : 'x'
            println String.format(Locale.US, '%-40s %-16s %s%-15s %s%-15s %7.1f %7.3f %7.3f',
                    r[0].take(40), r[1], flagFixed, String.valueOf(r[2]), flagOffset, String.valueOf(r[3]), r[4] as double, r[5] as double, r[6] as double)
        }
        println "\ncurrent      : ${fixedOk}/${fixtures.size()}"
        println "offset aware : ${offsetOk}/${fixtures.size()}"

        and: "the misidentified Fe 5302 scan, with its own instrument"
        def f = new File(AutoDetectOffsetEvaluation.getResource('/lineid/fe5302-misidentified.fits').toURI())
        def img = (ImageWrapper32) FitsUtils.readFitsFile(f)
        def a = new SpectrumFrameAnalyzer(img.width(), img.height(), false, null).analyze(img.data())
        def poly = a.distortionPolynomial().get()
        def cands = []
        for (var line : SpectralRay.predefined()) {
            if (line.wavelength().angstroms() > 0 && !line.emission()) {
                cands << new SpectrumAnalyzer.QueryDetails(line, 2.0d, 1, SpectroHeliograph.MLASTRO_SHG_700)
            }
        }
        def m = cands.stream().collect(Collectors.toMap(d -> d,
                d -> SpectrumAnalyzer.computeDataPoints(d, poly, a.leftBorder().orElse(0), a.rightBorder().orElse(img.width()), img.width(), img.height(), img.data()),
                (x, y) -> x, LinkedHashMap::new))
        def fixedFe = SpectrumAnalyzer.findBestMatch(m)
        def offsetFe = bestWithOffset(m)
        def zeroFe = bestAtZero(m)
        println "\n=== Fe 5302 fixture (expected: Iron (Fe I 5302)) ==="
        println "  current      : ${fixedFe?.line()?.label()}"
        println String.format(Locale.US, "  offset aware : %s at %+.1f px (score %.3f)",
                offsetFe.query?.line()?.label(), offsetFe.offset as double, offsetFe.score as double)
        println String.format(Locale.US, "  best at zero : %s (score %.3f)", zeroFe.query?.line()?.label(), zeroFe.score as double)
        true
    }

    /** Best score achievable with the offset pinned at zero, as the current detection does. */
    private static Map bestAtZero(Map<SpectrumAnalyzer.QueryDetails, List<SpectrumAnalyzer.DataPoint>> map) {
        def best = [query: null, score: -2d]
        map.each { query, points ->
            if (points.size() < 16) {
                return
            }
            def dispersion = SpectrumAnalyzer.computeSpectralDispersion(query.instrument(), query.line().wavelength(), query.pixelSize() * query.binning())
            def win = window(dispersion.angstromsPerPixel(), points.size())
            double[] shifts = points.collect { it.pixelShift() } as double[]
            double[] observed = normalize(points.collect { it.intensity() } as double[], win)
            double[] ref = new double[shifts.length]
            for (int i = 0; i < shifts.length; i++) {
                def wl = query.line().wavelength().angstroms() + shifts[i] * dispersion.angstromsPerPixel()
                ref[i] = ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl))
            }
            def score = pearson(observed, normalize(ref, win))
            if (score > best.score) {
                best = [query: query, score: score]
            }
        }
        return best
    }

    /** Best (line, offset) over all candidates, scoring with the locator's metric. */
    private static Map bestWithOffset(Map<SpectrumAnalyzer.QueryDetails, List<SpectrumAnalyzer.DataPoint>> map) {
        def best = [query: null, offset: 0d, score: -2d]
        map.each { query, points ->
            if (points.size() < 16) {
                return
            }
            def dispersion = SpectrumAnalyzer.computeSpectralDispersion(query.instrument(), query.line().wavelength(), query.pixelSize() * query.binning())
            double[] shifts = points.collect { it.pixelShift() } as double[]
            double[] observed = normalize(points.collect { it.intensity() } as double[], window(dispersion.angstromsPerPixel(), points.size()))
            def span = (shifts.max() - shifts.min()) / 2d
            for (double offset = -span; offset <= span; offset += OFFSET_STEP) {
                double[] ref = new double[shifts.length]
                for (int i = 0; i < shifts.length; i++) {
                    def wl = query.line().wavelength().angstroms() + (shifts[i] - offset) * dispersion.angstromsPerPixel()
                    ref[i] = ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wl))
                }
                def score = pearson(observed, normalize(ref, window(dispersion.angstromsPerPixel(), points.size())))
                if (score > best.score) {
                    best = [query: query, offset: offset, score: score]
                }
            }
        }
        return best
    }

    private static int window(double angstromsPerPixel, int length) {
        int upper = Math.max(9, (int) (length / 2))
        int ideal = (int) Math.round(2 / angstromsPerPixel)
        return Math.max(9, Math.min(ideal, upper)) | 1
    }

    private static double[] normalize(double[] values, int window) {
        def result = new double[values.length]
        def half = window / 2 as int
        for (int i = 0; i < values.length; i++) {
            def slice = Arrays.copyOfRange(values, Math.max(0, i - half), Math.min(values.length, i + half + 1))
            Arrays.sort(slice)
            def continuum = slice[(int) (slice.length * 0.75d)]
            result[i] = continuum > 1e-9 ? values[i] / continuum : 1d
        }
        return result
    }

    private static double pearson(double[] a, double[] b) {
        double ma = 0, mb = 0
        for (int i = 0; i < a.length; i++) {
            ma += a[i]; mb += b[i]
        }
        ma /= a.length; mb /= b.length
        double num = 0, da = 0, db = 0
        for (int i = 0; i < a.length; i++) {
            double va = a[i] - ma, vb = b[i] - mb
            num += va * vb; da += va * va; db += vb * vb
        }
        return num / Math.max(1e-12, Math.sqrt(da * db))
    }

    private static String lineName(String dir) {
        return switch (dir) {
            case 'Ha' -> 'H-alpha'
            case 'Hb' -> 'H-beta'
            case 'Mag' -> 'Magnesium (b1)'
            case 'caK' -> 'Calcium (K)'
            case 'caH' -> 'Calcium (H)'
            case 'Iron_Fe1' -> 'Iron (Fe I)'
            default -> dir
        }
    }
}
