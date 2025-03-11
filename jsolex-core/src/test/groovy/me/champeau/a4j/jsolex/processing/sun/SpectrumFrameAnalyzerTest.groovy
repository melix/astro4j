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

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import javax.imageio.ImageIO
import java.util.stream.Collectors

import static me.champeau.a4j.jsolex.processing.params.SpectroHeliograph.SOLEX

class SpectrumFrameAnalyzerTest extends Specification {

    @TempDir
    File tempDir

    def "analyzes spectrum frame"() {
        given:
        def image = ImageIO.read(getClass().getResourceAsStream("/spectrum.tif"))
        float[][] data = new float[image.height][image.width]
        for (int x = 0; x < image.width; x++) {
            for (int y = 0; y < image.height; y++) {
                data[y][x] = image.getRGB(x, y) & 0xFF
            }
        }
        def analyzer = new SpectrumFrameAnalyzer(image.width, image.height, false, 50d)

        when:
        def result = analyzer.analyze(data)

        then:
        result.leftBorder().present
        result.leftBorder().get() == 210
        result.rightBorder().present
        result.rightBorder().get() == 1703

        and:
        def polynomial = result.distortionPolynomial()
        polynomial.present
        def samples = result.samplePoints
        samples.size() == 204
        samples[120].x() == 1170
        samples[120].y() == 17

        def p = polynomial.get()
        for (int x = 0; x < image.width; x++) {
            def y = p.applyAsDouble(x)
            image.setRGB(x, y as int, 0xFF0000)
        }
        ImageIO.write(image, "png", new File(tempDir, "spectrum.png"))
        def imageCorrector = new DistortionCorrection(data, image.width, image.height)
        def corrected = imageCorrector.polynomialCorrection(p)
        for (int x = 0; x < image.width; x++) {
            for (int y = 0; y < image.height; y++) {
                int correctedValue = (corrected[y][x] as int) & 0xFF
                int greyScale = (correctedValue << 16) | (correctedValue << 8) | correctedValue
                image.setRGB(x, y, greyScale)
            }
        }
        ImageIO.write(image, "png", new File(tempDir, "corrected.png"))
        1
    }

    @Unroll
    def "identifies spectral lines (#file.name = #expectedLine)"() {
        given:
        def image = FitsUtils.readFitsFile(file)
        def width = image.width()
        def height = image.height()
        def data = ((ImageWrapper32)image).data()
        def analyzer = new SpectrumFrameAnalyzer(width, height, false, null)
        def result = analyzer.analyze(data)
        def polynomial = result.distortionPolynomial().get()
        int leftBorder = result.leftBorder().orElse(0)
        int rightBorder = result.rightBorder().orElse(width - 1)
        var candidates = new ArrayList<SpectrumAnalyzer.QueryDetails>();
        for (var line : SpectralRay.predefined()) {
            if (line.wavelength().angstroms() > 0 && !line.emission()) {
                candidates.add(new SpectrumAnalyzer.QueryDetails(line, 2.4, 1, SOLEX))
                candidates.add(new SpectrumAnalyzer.QueryDetails(line, 2.4, 2, SOLEX))
            }
        }
        var map = candidates
                .stream()
                .collect(Collectors.toMap(d -> d, details -> SpectrumAnalyzer.computeDataPoints(details, polynomial, leftBorder, rightBorder, width, height, data)));

        when:
        var bestMatch = SpectrumAnalyzer.findBestMatch(map)

        then:
        bestMatch.line().label() == expectedLine

        where:
        entry << averageImagesToLine(new File(SpectrumFrameAnalyzerTest.getResource("/average").toURI())).entrySet()
        file = entry.key
        expectedLine = entry.value
    }

    private static Map<File, String> averageImagesToLine(File baseDir) {
        var result = [:]
        baseDir.listFiles().each { d ->
            if (d.directory) {
                String name = directoryToLineName(d.name)
                d.listFiles().each { f ->
                    if (f.name.endsWith(".fits")) {
                        result[f] = name
                    }
                }
            }
        }
        result
    }

    private static String directoryToLineName(String name) {
        return switch (name) {
            case "Ha" -> "H-alpha"
            case "Hb" -> "H-beta"
            case "Mag" -> "Magnesium (b1)"
            case "caK" -> "Calcium (K)"
            case "caH" -> "Calcium (H)"
            case "Iron_Fe1" -> "Iron (Fe I)"
            case "Sodium (D2)" -> "Sodium (D2)"
            default -> name
        }
    }
}
