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
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Evaluation only: scores every average image fixture against the reference
 * spectrum at the wavelength its directory claims. A high correlation there is
 * independent evidence that the directory label is correct, because it does not
 * involve choosing between candidate lines. Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class BroadLineLocatorCheck extends Specification {

    def "scores every fixture at the wavelength its directory claims"() {
        given:
        def fixtures = []
        new File(BroadLineLocatorCheck.getResource('/average').toURI()).listFiles().each { dir ->
            def ray = rayFor(dir.name)
            if (dir.directory && ray != null) {
                dir.listFiles().each { f ->
                    if (f.name.endsWith('.fits')) {
                        fixtures << [file: f, dir: dir.name, ray: ray]
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
                return [fixture.file.name, fixture.dir, 0, -1d, 0d, 'NO POLYNOMIAL']
            }
            def ray = fixture.ray as SpectralRay
            def details = new SpectrumAnalyzer.QueryDetails(ray, 2.4d, 1, SpectroHeliograph.SOLEX)
            def dispersion = SpectrumAnalyzer.computeSpectralDispersion(SpectroHeliograph.SOLEX, ray.wavelength(), 2.4d)
            def points = SpectrumAnalyzer.computeDataPoints(details, polynomial,
                    analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(width), width, height, data)
            def located = SpectralLineLocator.locate(points, ray.wavelength(), dispersion, height / 2d)
            return located
                    .map { [fixture.file.name, fixture.dir, points.size(), it.score(), it.pixelOffset(), ''] }
                    .orElse([fixture.file.name, fixture.dir, points.size(), -1d, 0d, 'DECLINED'])
        }

        then:
        println "\n=== correlation at the labelled wavelength (${rows.size()} fixtures) ==="
        println String.format('%-44s %-10s %6s %7s %8s  %s', 'fixture', 'directory', 'points', 'score', 'offset', 'note')
        rows.sort { it[3] }.each { r ->
            println String.format(Locale.US, '%-44s %-10s %6d %7s %8s  %s',
                    r[0].take(44), r[1], r[2] as int,
                    (r[3] as double) < 0 ? '-' : String.format(Locale.US, '%.3f', r[3] as double),
                    (r[3] as double) < 0 ? '-' : String.format(Locale.US, '%+.2f', r[4] as double),
                    r[5])
        }
        def strong = rows.count { (it[3] as double) >= 0.9 }
        def good = rows.count { (it[3] as double) >= 0.75 }
        def declined = rows.count { it[5] == 'DECLINED' }
        println "\nscore >= 0.90 : ${strong}/${rows.size()}  (label strongly confirmed)"
        println "score >= 0.75 : ${good}/${rows.size()}"
        println "declined      : ${declined}/${rows.size()}"
        true
    }

    private static SpectralRay rayFor(String dir) {
        return switch (dir) {
            case 'Ha' -> SpectralRay.H_ALPHA
            case 'Hb' -> SpectralRay.H_BETA
            case 'Mag' -> SpectralRay.MAGNESIUM_b1
            case 'caK' -> SpectralRay.CALCIUM_K
            case 'caH' -> SpectralRay.CALCIUM_H
            case 'Iron_Fe1' -> SpectralRay.IRON_FE1
            default -> null
        }
    }
}
