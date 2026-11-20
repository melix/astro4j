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
package me.champeau.a4j.jsolex.processing.sun.workflow

import me.champeau.a4j.jsolex.processing.LegacyImageAlgorithms
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.centeredDisk
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class BackgroundEstimationEquivalenceTest extends Specification {

    def "black point and background estimates are identical to the column-major reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 31L * width + height)
        if (width > 2 && height > 2) {
            data[1][2] = Float.NaN
            data[2][1] = Float.POSITIVE_INFINITY
            data[height - 1][width - 1] = -12f
        }
        def image = new ImageWrapper32(width, height, data, [:])
        def ellipse = centeredDisk(width, height)

        expect:
        Double.doubleToRawLongBits(AnalysisUtils.estimateBlackPoint(image, ellipse)) == Double.doubleToRawLongBits(LegacyImageAlgorithms.estimateBlackPoint(data, ellipse))
        Double.doubleToRawLongBits(AnalysisUtils.estimateBackground(image, ellipse)) == Double.doubleToRawLongBits(LegacyImageAlgorithms.estimateBackground(data, ellipse))
        Double.doubleToRawLongBits(AnalysisUtils.estimateBackground(data, ellipse)) == Double.doubleToRawLongBits(LegacyImageAlgorithms.estimateBackground(data, ellipse))

        where:
        [width, height] << SIZES + [[300, 4000], [1031, 2049]]
    }
}
