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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

class PercentileStretchStrategyTest extends Specification {

    def "maps the percentile range linearly to the full dynamic range"() {
        given: "a 100-pixel gradient from 0 to 39600"
        def image = gradient(0, 400)

        when:
        new PercentileStretchStrategy(1, 99).stretch(image)

        then: "values below/above the percentiles are clipped, the middle maps linearly"
        def data = image.data()[0]
        data[0] == 0f
        data[99] == 65535f
        Math.abs(data[50] - 65535 * (50 - 1) / 98.0) < 500
    }

    def "negative values are stretched linearly, not clipped to the black point"() {
        given: "a signed gradient centered on zero, like a continuum-subtracted image"
        def image = gradient(-19800, 400)

        when:
        new PercentileStretchStrategy(1, 99).stretch(image)

        then: "the result is identical to the same gradient shifted into positive values"
        def offsetImage = gradient(0, 400)
        new PercentileStretchStrategy(1, 99).stretch(offsetImage)
        image.data()[0] == offsetImage.data()[0]
    }

    def "clip mode EXTEND raises the white point to the brightest pixel"() {
        given: "a gradient whose top values would clip with a 99th percentile white point"
        def clamped = gradient(0, 400)
        def extended = gradient(0, 400)

        when:
        new PercentileStretchStrategy(1, 99, null, PercentileStretchStrategy.ClipMode.CLAMP).stretch(clamped)
        new PercentileStretchStrategy(1, 99, null, PercentileStretchStrategy.ClipMode.EXTEND).stretch(extended)

        then: "clamping saturates the top of the range"
        clamped.data()[0][99] == 65535f
        clamped.data()[0][98] == 65535f

        and: "with an extended white point only the brightest pixel reaches the maximum"
        extended.data()[0][99] == 65535f
        extended.data()[0][98] < 65535f
        extended.data()[0][98] > extended.data()[0][97]
    }

    def "clip mode NONE is a pure affine transformation"() {
        given:
        def image = gradient(0, 400)

        when:
        new PercentileStretchStrategy(1, 99, null, PercentileStretchStrategy.ClipMode.NONE).stretch(image)

        then: "pixels outside the percentile range keep scaling linearly"
        def data = image.data()[0]
        data[0] < 0f
        data[99] > 65535f
        Math.abs((data[50] - data[49]) - (data[99] - data[98])) < 1f
    }

    def "clip mode NONE keeps the gain independent of outliers"() {
        given: "two identical gradients, one with a hot pixel outside the percentile range"
        def clean = gradient(0, 400)
        def withHotPixel = gradient(0, 400)
        withHotPixel.data()[0][99] = 1e7f

        when:
        new PercentileStretchStrategy(1, 99, null, PercentileStretchStrategy.ClipMode.NONE).stretch(clean)
        new PercentileStretchStrategy(1, 99, null, PercentileStretchStrategy.ClipMode.NONE).stretch(withHotPixel)

        then:
        clean.data()[0][50] == withHotPixel.data()[0][50]
    }

    private static ImageWrapper32 gradient(double from, double step) {
        def data = new float[1][100]
        for (int x = 0; x < 100; x++) {
            data[0][x] = (float) (from + step * x)
        }
        new ImageWrapper32(100, 1, data, [:] as Map<Class<?>, Object>)
    }
}
