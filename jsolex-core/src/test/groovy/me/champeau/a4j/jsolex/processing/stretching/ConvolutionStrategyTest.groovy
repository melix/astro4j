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

import me.champeau.a4j.jsolex.processing.expr.impl.Convolution
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.image.BlurKernel
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.SharpenKernel
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.stretching.StretchingTestImages.copyOf
import static me.champeau.a4j.jsolex.processing.stretching.StretchingTestImages.halves
import static me.champeau.a4j.jsolex.processing.stretching.StretchingTestImages.maxDifference
import static me.champeau.a4j.jsolex.processing.stretching.StretchingTestImages.noise
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE

class ConvolutionStrategyTest extends Specification {
    private static final int SIZE = 64

    def "blur and sharpen apply their kernel"() {
        given:
        def image = noise(1)
        def expected = ImageMath.newInstance().convolve(image.asImage(), kernel).data()

        when:
        strategy.stretch(image)

        then:
        maxDifference(image.data(), clip(expected)) == 0

        where:
        strategy                         | kernel
        ConvolutionStrategy.blur(5)      | BlurKernel.of(5)
        ConvolutionStrategy.sharpen(3)   | SharpenKernel.of(3)
    }

    def "unsharp mask adds the difference with a blurred copy"() {
        given:
        def image = noise(2)
        def original = copyOf(image.data())
        def blurred = ImageMath.newInstance().convolve(image.asImage(), BlurKernel.of(3)).data()

        when:
        ConvolutionStrategy.unsharpMask(0.5, 3).stretch(image)

        then:
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def expected = Math.clamp(original[y][x] + 0.5 * (original[y][x] - blurred[y][x]), 0, MAX_PIXEL_VALUE)
                assert Math.abs(image.data()[y][x] - expected) < 0.01
            }
        }
    }

    def "unsharp mask with a strength of 0 leaves the image unchanged"() {
        given:
        def image = noise(3)
        def expected = image.copy()

        when:
        ConvolutionStrategy.unsharpMask(0, 7).stretch(image)

        then:
        maxDifference(image.data(), expected.data()) == 0
    }

    def "sharpened values are clipped to the range of pixel values"() {
        given:
        def image = halves(1000, 64000)

        when:
        strategy.stretch(image)

        then:
        def values = image.data().flatten() as List<Float>
        values.min() == 0f
        values.max() == MAX_PIXEL_VALUE

        where:
        strategy << [ConvolutionStrategy.sharpen(3), ConvolutionStrategy.unsharpMask(3, 5)]
    }

    def "the unsharp_mask function is not clipped"() {
        given:
        def image = halves(1000, 64000)
        def convolution = new Convolution([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)

        when:
        def result = (ImageWrapper32) convolution.unsharpMask([img: image, strength: 3.0d, kernel: 5])

        then:
        def values = result.data().flatten() as List<Float>
        values.min() < 0f
        values.max() > MAX_PIXEL_VALUE
        image.data()[0][0] == 1000f
    }

    def "color images are only filtered on their lightness"() {
        given:
        def mono = noise(4)
        def grey = new RGBImage(SIZE, SIZE, copyOf(mono.data()), copyOf(mono.data()), copyOf(mono.data()), [:])

        when:
        ConvolutionStrategy.sharpen(3).stretch(mono)
        ConvolutionStrategy.sharpen(3).stretch(grey)

        then:
        maxDifference(grey.r(), mono.data()) < 1
        maxDifference(grey.g(), mono.data()) < 1
        maxDifference(grey.b(), mono.data()) < 1
    }

    private static float[][] clip(float[][] data) {
        data.collect { row -> row.collect { Math.clamp(it, 0f, MAX_PIXEL_VALUE) } as float[] } as float[][]
    }
}
