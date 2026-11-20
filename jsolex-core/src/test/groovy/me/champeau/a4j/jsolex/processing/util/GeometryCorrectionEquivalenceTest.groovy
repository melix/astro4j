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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.jsolex.processing.LegacyImageAlgorithms
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.assertSameBits
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class GeometryCorrectionEquivalenceTest extends Specification {

    def "geometry correction is identical to the full-copy reference on #width x #height with sx=#sx sy=#sy shear=#shear"() {
        given:
        def data = randomImage(width, height, 41L * width + height)
        def shift = height * shear < 0 ? height * shear : 0d
        def transform = new GeometryTransform(0.1d, shear, shift, sx, sy, 1d, width, height)
        def image = new ImageWrapper32(width, height, data, [:])

        when:
        def corrected = GeometryUtils.applyGeometryCorrection(image, transform, 123f)

        then:
        corrected.width() == transform.outputWidth()
        corrected.height() == transform.outputHeight()
        assertSameBits(LegacyImageAlgorithms.geometryCorrection(data, transform, 123f), corrected.pixels().toArray())

        where:
        [[width, height], [sx, sy, shear]] << [SIZES, [
                [1d, 1d, 0d],
                [1d, 1.37d, 0.08d],
                [0.62d, 1d, -0.05d],
                [1d, 0.41d, 0.03d],
                [0.7d, 0.23d, -0.12d],
                [1.8d, 2.6d, 0.2d]
        ]].combinations()
    }
}
