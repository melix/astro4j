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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.LegacyImageAlgorithms
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.assertSameBits
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class ColorizeEquivalenceTest extends Specification {

    def "colorizing with a color is identical to the full-copy reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 103L * width + height)
        def original = LegacyImageAlgorithms.copy(data)
        int[] rgb = [255, 120, 30]
        def expected = LegacyImageAlgorithms.colorize(width, height, data, rgb)
        def mono = new ImageWrapper32(width, height, data, [:])

        when:
        def fromArray = Colorize.doColorize(width, height, data, rgb)
        def fromImage = Colorize.doColorize(mono, rgb)

        then:
        assertSameBits(expected, fromArray)
        assertSameBits(expected, [fromImage.red().toArray(), fromImage.green().toArray(), fromImage.blue().toArray()] as float[][][])
        assertSameBits(original, data)
        assertSameBits(original, mono.pixels().toArray())

        where:
        [width, height] << SIZES
    }
}
