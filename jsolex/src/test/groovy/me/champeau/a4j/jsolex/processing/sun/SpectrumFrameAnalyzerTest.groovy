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
package me.champeau.a4j.jsolex.processing.sun


import spock.lang.Specification
import spock.lang.TempDir

import javax.imageio.ImageIO

class SpectrumFrameAnalyzerTest extends Specification {

    @TempDir
    File tempDir

    def "analyzes spectrum frame"() {
        given:
        def image = ImageIO.read(getClass().getResourceAsStream("/spectrum.tif"))
        double[] data = new double[image.width * image.height]
        for (int x = 0; x < image.width; x++) {
            for (int y = 0; y < image.height; y++) {
                data[x + y * image.width] = image.getRGB(x, y) & 0xFF
            }
        }
        def analyzer = new SpectrumFrameAnalyzer(image.width, image.height, .85d, 50d)

        when:
        analyzer.analyze(data)

        then:
        analyzer.leftSunBorder().present
        analyzer.leftSunBorder().get() == 210
        analyzer.rightSunBorder().present
        analyzer.rightSunBorder().get() == 1703

        and:
        def spectrumLines = analyzer.spectrumLines()
//        spectrumLines.size() > 3
//        spectrumLines[0].x() == 210
//        spectrumLines[0].middle() == 39
//        spectrumLines[-1].x() == 1704
//        spectrumLines[-1].middle() == 29

        and:
        def polynomial = analyzer.findDistortionPolynomial()
        polynomial.present
        def p = polynomial.get()
        for (int x = 0; x < image.width; x++) {
            def y = p.a() * x * x + p.b() * x + p.c()
            image.setRGB(x, y as int, 0xFF0000)
        }
        ImageIO.write(image, "png", new File(tempDir, "spectrum.png"))
        def imageCorrector = new DistortionCorrection(data, image.width, image.height)
        def corrected = imageCorrector.secondOrderPolynomialCorrection(p)
        for (int x = 0; x < image.width; x++) {
            for (int y = 0; y < image.height; y++) {
                int correctedValue = (corrected[x + y * image.width] as int) & 0xFF
                int greyScale = (correctedValue << 16) | (correctedValue << 8) | correctedValue
                image.setRGB(x, y, greyScale)
            }
        }
        ImageIO.write(image, "png", new File(tempDir, "corrected.png"))
        1
    }
}
