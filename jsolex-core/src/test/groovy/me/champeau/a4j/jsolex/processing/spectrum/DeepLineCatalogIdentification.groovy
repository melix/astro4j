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
 * Evaluation only: runs the shipped free search over every average image fixture and
 * reports how often it answers and how often it is right. It calls
 * {@link DeepLineIdentifier} itself, so the figures always describe the code which
 * actually runs. Asserts nothing. Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class DeepLineCatalogIdentification extends Specification {

    def "measures the free search over the whole fixture corpus"() {
        given:
        def fixtures = []
        fixtures << [file: new File(DeepLineCatalogIdentification.getResource('/lineid/fe5302-misidentified.fits').toURI()),
                     label: 'CONTROL', instrument: SpectroHeliograph.MLASTRO_SHG_700, pixelSize: 2.0d,
                     binnings: [1] as int[], truth: 5298.26d]
        new File(DeepLineCatalogIdentification.getResource('/average').toURI()).listFiles().each { dir ->
            def truth = expectedWavelength(dir.name)
            if (dir.directory && truth > 0) {
                dir.listFiles().each { f ->
                    if (f.name.endsWith('.fits')) {
                        fixtures << [file: f, label: dir.name, instrument: SpectroHeliograph.SOLEX,
                                     pixelSize: 2.4d, binnings: [1, 2] as int[], truth: truth]
                    }
                }
            }
        }

        when:
        def rows = fixtures.collect { fixture ->
            def image = (ImageWrapper32) FitsUtils.readFitsFile(fixture.file)
            int width = image.width()
            int height = image.height()
            def data = image.data()
            def analysis = new SpectrumFrameAnalyzer(width, height, false, null).analyze(data)
            def polynomial = analysis.distortionPolynomial().orElse(null)
            if (polynomial == null) {
                return [name: fixture.file.name, label: fixture.label, truth: fixture.truth, points: 0, found: null]
            }
            def probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, fixture.pixelSize as double,
                    (fixture.binnings as int[])[0], fixture.instrument)
            def profile = SpectrumAnalyzer.computeDataPoints(probe, polynomial,
                    analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(width), width, height, data)
            def identified = DeepLineIdentifier.identify(profile, fixture.instrument,
                    fixture.pixelSize as double, fixture.binnings as int[])
            return [name: fixture.file.name, label: fixture.label, truth: fixture.truth,
                    points: profile.size(), found: identified.orElse(null)]
        }

        then:
        println "\n=== free search over ${rows.size()} fixtures ==="
        println String.format('%-42s %-10s %7s %10s %10s %7s %7s', 'fixture', 'directory', 'points', 'expected', 'found', 'score', 'margin')
        rows.sort { it.points }.each { r ->
            var ok = r.found != null && Math.abs(r.found.wavelength().angstroms() - (r.truth as double)) < 1.5
            println String.format(Locale.US, '%s%-41s %-10s %7d %10.2f %10s %7s %7s',
                    r.found == null ? '.' : (ok ? ' ' : 'x'),
                    r.name.take(41), r.label, r.points as int, r.truth as double,
                    r.found == null ? 'declined' : String.format(Locale.US, '%.2f', r.found.wavelength().angstroms()),
                    r.found == null ? '-' : String.format(Locale.US, '%.3f', r.found.score()),
                    r.found == null ? '-' : String.format(Locale.US, '%.3f', r.found.margin()))
        }

        and:
        report('all fixtures', rows)
        [[0, 50, 'under 50 px'], [50, 100, '50 to 100 px'], [100, 100000, 'over 100 px']].each { bucket ->
            report(bucket[2] as String, rows.findAll { (it.points as int) >= (bucket[0] as int) && (it.points as int) < (bucket[1] as int) })
        }
        true
    }

    private static void report(String title, List rows) {
        if (rows.isEmpty()) {
            return
        }
        var answered = rows.findAll { it.found != null }
        var correct = answered.count { Math.abs(it.found.wavelength().angstroms() - (it.truth as double)) < 1.5 }
        println String.format(Locale.US, '%-16s %2d fixtures, %2d answered, %2d correct -> precision %3.0f%%, recall %3.0f%%',
                title, rows.size(), answered.size(), correct,
                answered.isEmpty() ? 0d : 100d * correct / answered.size(),
                100d * correct / rows.size())
    }

    private static double expectedWavelength(String dir) {
        return switch (dir) {
            case 'Ha' -> 6562.81d
            case 'Hb' -> 4861.34d
            case 'Mag' -> 5183.62d
            case 'caK' -> 3933.66d
            case 'caH' -> 3968.47d
            case 'Iron_Fe1' -> 5883.82d
            default -> 0d
        }
    }
}
