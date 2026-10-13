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

import me.champeau.a4j.jsolex.processing.expr.impl.Wavelets
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RangeMask
import spock.lang.Specification
import spock.lang.Subject

class WaveletsTest extends Specification {

    private static final int SIZE = 64

    @Subject
    Wavelets wavelets = new Wavelets([:], Broadcaster.NO_OP)

    def "a_trous returns the scales followed by the residual"() {
        given:
        var image = noisyImage()

        when:
        var scales = wavelets.aTrous([img: image, levels: 3]) as List<ImageWrapper32>

        then:
        scales.size() == 4
        Math.abs(scales.sum { it.data()[20][30] } - image.data()[20][30]) < 1e-3
    }

    def "denoise keeps the pixels outside the mask"() {
        given:
        var image = noisyImage()
        image.data()[5][5] = 0

        when:
        var denoised = wavelets.denoise([img: image, mask: new RangeMask(1, 65535)]) as ImageWrapper32

        then:
        denoised.data()[5][5] == 0f
        denoised.data()[20][30] != image.data()[20][30]
    }

    def "grain must be a fraction"() {
        when:
        wavelets.denoise([img: noisyImage(), grain: 2])

        then:
        thrown(IllegalArgumentException)
    }

    private static ImageWrapper32 noisyImage() {
        var random = new Random(1)
        var data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) (1000 + 10 * random.nextGaussian())
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, [:])
    }
}
