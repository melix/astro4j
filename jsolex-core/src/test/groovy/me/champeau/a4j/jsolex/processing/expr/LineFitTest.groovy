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
package me.champeau.a4j.jsolex.processing.expr

import me.champeau.a4j.jsolex.processing.expr.impl.LineFit
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RangeMask
import spock.lang.Specification
import spock.lang.Subject

class LineFitTest extends Specification {

    private static final int SIZE = 40
    private static final List<Double> SHIFTS = (-4..8).collect { it as double }

    @Subject
    LineFit lineFit = new LineFit([:], Broadcaster.NO_OP)

    def "fits the line using the pixel shifts of the images"() {
        given: "a bright background on the left, emission over a fainter background on the right"
        var images = SHIFTS.reverse().collect { spectrumImage(it) }

        when:
        var result = lineFit.lineFit([
                img     : images,
                shift   : 2.7d,
                fwhm    : 3.3d,
                template: new RangeMask(3000, 65535)
        ]) as ImageWrapper32

        then:
        Math.abs(result.data()[10][5]) < 0.05
        Math.abs(result.data()[10][35] - 30) < 0.05

        and: "the result is the image of the line"
        result.findMetadata(PixelShift).get().pixelShift() == 2.7d
    }

    def "a mask restricts the measured pixels"() {
        given:
        var images = SHIFTS.collect { spectrumImage(it) }

        when:
        var result = lineFit.lineFit([
                img     : images,
                shift   : 2.7d,
                fwhm    : 3.3d,
                template: new RangeMask(3000, 65535),
                mask    : new RangeMask(3000, 65535)
        ]) as ImageWrapper32

        then:
        result.data()[10][35] == 0f
    }

    def "requires images with a pixel shift"() {
        when:
        lineFit.lineFit([
                img     : SHIFTS.collect { new ImageWrapper32(SIZE, SIZE, new float[SIZE][SIZE], [:]) },
                shift   : 2.7d,
                fwhm    : 3.3d,
                template: new RangeMask(0, 65535)
        ])

        then:
        thrown(IllegalArgumentException)
    }

    private static ImageWrapper32 spectrumImage(double shift) {
        var data = new float[SIZE][SIZE]
        var background = 1 - 0.6 * Math.exp(-0.5 * (shift / 1.2)**2)
        var sigma = 3.3 / (2 * Math.sqrt(2 * Math.log(2)))
        var line = Math.exp(-0.5 * ((shift - 2.7) / sigma)**2)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = x < SIZE.intdiv(2) ? (float) (8000 * background) : (float) (1000 * background + 30 * line)
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, [(PixelShift): new PixelShift(shift)])
    }
}
