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
 * Evaluation only: compares the absolute lead of the winning hypothesis with a lead
 * expressed as a fraction of the headroom left above the runner up. Correlations
 * saturate close to one on windows dominated by a single broad line, which makes the
 * absolute lead tiny even when the winner is unambiguous.
 * Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class MarginStatisticEvaluation extends Specification {

    private static final double SEPARATION = 3

    def "compares absolute and relative margins over the corpus"() {
        given:
        def fixtures = []
        fixtures << [file: new File(MarginStatisticEvaluation.getResource('/lineid/fe5302-misidentified.fits').toURI()),
                     label: 'CONTROL', instrument: SpectroHeliograph.MLASTRO_SHG_700, pixelSize: 2.0d,
                     binnings: [1] as int[], truth: 5298.26d]
        new File(MarginStatisticEvaluation.getResource('/average').toURI()).listFiles().each { dir ->
            def truth = expected(dir.name)
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
            def analysis = new SpectrumFrameAnalyzer(image.width(), image.height(), false, null).analyze(image.data())
            def polynomial = analysis.distortionPolynomial().orElse(null)
            if (polynomial == null) {
                return null
            }
            def probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, fixture.pixelSize as double,
                    (fixture.binnings as int[])[0], fixture.instrument)
            def profile = SpectrumAnalyzer.computeDataPoints(probe, polynomial,
                    analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(image.width()),
                    image.width(), image.height(), image.data())
            def ranked = DeepLineIdentifier.rank(profile, fixture.instrument, fixture.pixelSize as double, fixture.binnings as int[])
            if (ranked.isEmpty()) {
                return null
            }
            var winner = ranked[0]
            var runnerUp = ranked.find { Math.abs(it.wavelength().angstroms() - winner.wavelength().angstroms()) >= SEPARATION }
            if (runnerUp == null) {
                return null
            }
            var absolute = winner.score() - runnerUp.score()
            var relative = absolute / Math.max(1e-9, 1 - runnerUp.score())
            return [name: fixture.file.name, label: fixture.label, points: profile.size(),
                    correct: Math.abs(winner.wavelength().angstroms() - (fixture.truth as double)) < 1.5,
                    score: winner.score(), absolute: absolute, relative: relative]
        }.findAll { it != null }

        then:
        println "\n=== winner statistics over ${rows.size()} fixtures ==="
        println String.format('%-42s %-9s %6s %7s %9s %9s %s', 'fixture', 'directory', 'points', 'score', 'absolute', 'relative', 'right')
        rows.sort { -it.relative }.each { r ->
            println String.format(Locale.US, '%-42s %-9s %6d %7.3f %9.3f %9.3f %s',
                    r.name.take(42), r.label, r.points as int, r.score as double,
                    r.absolute as double, r.relative as double, r.correct ? 'yes' : 'NO')
        }

        and:
        println '\n=== gate on the absolute lead (score >= 0.70) ==='
        sweep(rows, 'absolute', [0.010d, 0.015d, 0.020d, 0.030d, 0.050d])
        println '\n=== gate on the relative lead (score >= 0.70) ==='
        sweep(rows, 'relative', [0.05d, 0.10d, 0.15d, 0.20d, 0.25d, 0.30d, 0.35d, 0.40d, 0.50d])
        true
    }

    private static void sweep(List rows, String key, List<Double> thresholds) {
        println String.format('%10s %9s %8s %10s %8s', 'threshold', 'answered', 'correct', 'precision', 'recall')
        thresholds.each { threshold ->
            var answered = rows.findAll { (it.score as double) >= 0.70d && (it[key] as double) >= threshold }
            var correct = answered.count { it.correct }
            println String.format(Locale.US, '%10.3f %9d %8d %9.0f%% %7.0f%%',
                    threshold, answered.size(), correct,
                    answered.isEmpty() ? 0d : 100d * correct / answered.size(),
                    100d * correct / rows.size())
        }
    }

    private static double expected(String dir) {
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
