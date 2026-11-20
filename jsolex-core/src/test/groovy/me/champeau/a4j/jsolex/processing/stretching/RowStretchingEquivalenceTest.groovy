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

import me.champeau.a4j.jsolex.processing.LegacyImageAlgorithms
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.image.BlurKernel
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.ImagePlane
import spock.lang.Specification

import java.util.function.BiConsumer
import java.util.function.BiPredicate

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.assertSameBits
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class RowStretchingEquivalenceTest extends Specification {

    private static ImageWrapper32 mono(float[][] data) {
        new ImageWrapper32(data[0].length, data.length, data, [:])
    }

    private static float[][] stretched(float[][] data, StretchingStrategy strategy) {
        def image = mono(data)
        strategy.stretch(image)
        image.pixels().toArray()
    }

    private static float[][][] channels(RGBImage image) {
        [image.red().toArray(), image.green().toArray(), image.blue().toArray()] as float[][][]
    }

    def "per-pixel strategies are identical to the full-copy reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 43L * width + height)

        expect:
        def arcsinh = new ArcsinhStretchingStrategy(800f, 12f, 1000d)
        def arcsinhImage = mono(data)
        arcsinh.stretchCPU(arcsinhImage)
        assertSameBits(stretched(LegacyImageAlgorithms.arcsinh(data, 800f, 12f), LinearStrechingStrategy.DEFAULT), arcsinhImage.pixels().toArray())

        assertSameBits(LegacyImageAlgorithms.curve(data, 60, 140, 0, 65535), stretched(data, new CurveTransformStrategy(60, 140)))
        assertSameBits(LegacyImageAlgorithms.curve(data, 60, 140, 1000, 60000), stretched(data, new CurveTransformStrategy(60, 140, 1000, 60000)))
        assertSameBits(LegacyImageAlgorithms.sigmoid(data, 0.4d, 7d), stretched(data, new SigmoidStretchingStrategy(0.4d, 7d)))
        assertSameBits(LegacyImageAlgorithms.contrast(data, 2000f, 50000f), stretched(data, new ContrastAdjustmentStrategy(2000f, 50000f)))
        assertSameBits(LegacyImageAlgorithms.mtf(data, 10d, 0.3d, 240d), stretched(data, new MidtoneTransferFunctionStrategy(10d, 0.3d, 240d)))
        assertSameBits(LegacyImageAlgorithms.negative(data), stretched(data, NegativeImageStrategy.DEFAULT))

        def gamma = new GammaStrategy(1.7d)
        def gammaImage = mono(data)
        gamma.stretchCPU(gammaImage)
        assertSameBits(LegacyImageAlgorithms.gamma(data, 1.7d), gammaImage.pixels().toArray())

        assertSameBits(stretched(LegacyImageAlgorithms.dynamic(data, 5000f, 0.3d), RangeExpansionStrategy.DEFAULT), stretched(data, new DynamicStretchStrategy(5000f, 0.3d)))

        where:
        [width, height] << SIZES
    }

    def "percentile stretch is identical to the full-copy reference on #width x #height with mask=#masked and clip=#clipMode"() {
        given:
        def data = randomImage(width, height, 47L * width + height)
        BiPredicate<Integer, Integer> mask = masked ? ({ x, y -> (x + 2 * y) % 3 != 0 } as BiPredicate<Integer, Integer>) : null
        def expected = LegacyImageAlgorithms.percentile(data, 2d, 97d, mask, clipMode == PercentileStretchStrategy.ClipMode.NONE, clipMode == PercentileStretchStrategy.ClipMode.EXTEND)

        expect:
        assertSameBits(expected, stretched(data, new PercentileStretchStrategy(2d, 97d, mask, clipMode)))

        where:
        [[width, height], masked, clipMode] << [SIZES, [false, true], PercentileStretchStrategy.ClipMode.values().toList()].combinations()
    }

    def "pixel collection and maximum read from planes are identical to the reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 53L * width + height)
        if (width > 3) {
            data[0][3] = Float.NaN
        }
        def plane = ImagePlane.of(width, height, data)
        BiPredicate<Integer, Integer> mask = { x, y -> (x * y) % 5 == 1 } as BiPredicate<Integer, Integer>

        expect:
        Arrays.equals(LegacyImageAlgorithms.collect(data, null), PixelCollection.collect(plane, null))
        Arrays.equals(LegacyImageAlgorithms.collect(data, mask), PixelCollection.collect(plane, mask))
        Float.floatToRawIntBits(LegacyImageAlgorithms.max(data, 1e-7f)) == Float.floatToRawIntBits(StretchingUtils.max(plane, 1e-7f))
        Float.floatToRawIntBits(LegacyImageAlgorithms.max(data, 70000f)) == Float.floatToRawIntBits(StretchingUtils.max(plane, 70000f))

        where:
        [width, height] << SIZES
    }

    def "color stretches are identical to the full-copy reference on #width x #height"() {
        given:
        def r = randomImage(width, height, 59L * width + height)
        def g = randomImage(width, height, 61L * width + height)
        def b = randomImage(width, height, 67L * width + height)

        expect:
        [new LinearStrechingStrategy(0, 50000f), new SigmoidStretchingStrategy(0.5d, 5d), new CurveTransformStrategy(80, 160), new PercentileStretchStrategy(1d, 99d)].each { strategy ->
            def image = LegacyImageAlgorithms.rgb(r, g, b)
            strategy.stretch(image)
            assertSameBits(LegacyImageAlgorithms.stretchRgb(strategy, r, g, b, [:]), channels(image))
        }

        def arcsinh = new ArcsinhStretchingStrategy(500f, 8f, 1000d)
        def arcsinhImage = LegacyImageAlgorithms.rgb(r, g, b)
        arcsinh.stretch(arcsinhImage)
        def arcsinhReference = LegacyImageAlgorithms.arcsinhRgb(r, g, b, 500f, 8f)
        assertSameBits(LegacyImageAlgorithms.stretchRgb(LinearStrechingStrategy.DEFAULT, arcsinhReference[0], arcsinhReference[1], arcsinhReference[2], [:]), channels(arcsinhImage))

        where:
        [width, height] << SIZES
    }

    def "convolution of the lightness is identical to the full-copy reference on #width x #height"() {
        given:
        def r = randomImage(width, height, 71L * width + height)
        def g = randomImage(width, height, 73L * width + height)
        def b = randomImage(width, height, 79L * width + height)
        def imageMath = ImageMath.newInstance()
        def kernel = BlurKernel.of(3)
        def strategy = ConvolutionStrategy.blur(3)

        when:
        def filtered = ConvolutionStrategy.filterLightness(LegacyImageAlgorithms.rgb(r, g, b), { imageMath.convolve(it, kernel) })
        def image = LegacyImageAlgorithms.rgb(r, g, b)
        strategy.stretch(image)

        then:
        assertSameBits(LegacyImageAlgorithms.filterLightness(r, g, b, { imageMath.convolve(it, kernel) }), filtered)
        assertSameBits(LegacyImageAlgorithms.filterLightness(r, g, b, { it.withData(strategy.filterAndClip(it, 1f)) }), channels(image))

        where:
        [width, height] << SIZES
    }

    def "shadows and highlights on a color image are identical to the full-copy reference on #width x #height"() {
        given:
        def r = randomImage(width, height, 83L * width + height)
        def g = randomImage(width, height, 89L * width + height)
        def b = randomImage(width, height, 97L * width + height)
        def strategy = new ShadowsHighlightsStrategy(40d, -30d, 4)
        def image = LegacyImageAlgorithms.rgb(r, g, b)

        when:
        strategy.stretch(image)

        then:
        def adjust = { float[][] luminance, float[][] output -> strategy.adjust(luminance, output, width, height) } as BiConsumer<float[][], float[][]>
        assertSameBits(LegacyImageAlgorithms.shadowsHighlightsRgb(r, g, b, adjust), channels(image))

        where:
        [width, height] << SIZES
    }

    def "autohistogram without a disk nor brightness adjustment only applies the cutoff on #width x #height"() {
        given:
        def data = randomImage(width, height, 101L * width + height)

        expect:
        assertSameBits(stretched(data, CutoffStretchingStrategy.DEFAULT), stretched(data, new AutohistogramStrategy(1.5d, false, 0.25d, 0.5d)))

        where:
        [width, height] << SIZES
    }
}
