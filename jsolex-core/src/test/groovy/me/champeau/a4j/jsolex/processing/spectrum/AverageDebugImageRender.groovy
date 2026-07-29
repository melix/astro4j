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
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Renders the average image debug picture as JSol'Ex produces it, for visual
 * inspection. Development aid, skipped unless LINE_ID_OUT is set.
 */
@IgnoreIf({ System.getenv('LINE_ID_OUT') == null })
class AverageDebugImageRender extends Specification {

    def "renders the average debug image with both lines"() {
        given:
        def out = new File(System.getenv('LINE_ID_OUT'))
        out.mkdirs()
        def file = new File(AverageDebugImageRender.getResource('/lineid/fe5302-misidentified.fits').toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        int width = image.width()
        int height = image.height()
        def data = image.data()
        def details = new SpectrumAnalyzer.QueryDetails(
                new SpectralRay('Fe 5302', null, Wavelen.ofAngstroms(5302.3d), false, []),
                2.0d, 1, SpectroHeliograph.MLASTRO_SHG_700)

        when:
        def analyzer = new SpectrumFrameAnalyzer(width, height, false, null)
        def detectedResult = analyzer.analyze(data)
        def detectedPolynomial = detectedResult.distortionPolynomial().get()
        def used = SpectralLineRealigner.realign(data, width, height, false, detectedResult, details)
                .distortionPolynomial().get()

        def creator = new SpectralLineFrameImageCreator(analyzer, data, width, height)
        write(creator.generateDebugImage(used, detectedPolynomial), new File(out, '06-average-debug-realigned.png'))

        def plainAnalyzer = new SpectrumFrameAnalyzer(width, height, false, null)
        plainAnalyzer.analyze(data)
        write(new SpectralLineFrameImageCreator(plainAnalyzer, data, width, height).generateDebugImage(),
                new File(out, '07-average-debug-before.png'))

        then:
        new File(out, '06-average-debug-realigned.png').exists()
    }

    private static void write(rgb, File target) {
        int w = rgb.width()
        int h = rgb.height()
        def r = rgb.r()
        def g = rgb.g()
        def b = rgb.b()
        def max = 0f
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                max = Math.max(max, Math.max(r[y][x], Math.max(g[y][x], b[y][x])))
            }
        }
        def scale = max > 0 ? 255f / max : 1f
        def img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rv = Math.min(255, (int) (r[y][x] * scale))
                int gv = Math.min(255, (int) (g[y][x] * scale))
                int bv = Math.min(255, (int) (b[y][x] * scale))
                img.setRGB(x, y, (rv << 16) | (gv << 8) | bv)
            }
        }
        ImageIO.write(img, 'png', target)
    }
}
