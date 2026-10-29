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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.BlurKernel;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel;
import me.champeau.a4j.math.image.SharpenKernel;

import java.util.function.UnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * Blurs or sharpens an image. The result is clipped to the range of pixel values.
 * Color images are only filtered on their lightness.
 */
public final class ConvolutionStrategy implements StretchingStrategy {
    private final UnaryOperator<Image> filter;

    private ConvolutionStrategy(UnaryOperator<Image> filter) {
        this.filter = filter;
    }

    /**
     * Creates a box blur.
     *
     * @param kernelSize the size of the kernel
     * @return the strategy
     */
    public static ConvolutionStrategy blur(int kernelSize) {
        return convolve(BlurKernel.of(kernelSize));
    }

    /**
     * Creates a sharpening filter.
     *
     * @param kernelSize the size of the kernel, odd and at least 3
     * @return the strategy
     */
    public static ConvolutionStrategy sharpen(int kernelSize) {
        return convolve(SharpenKernel.of(kernelSize));
    }

    /**
     * Creates an unsharp mask.
     *
     * @param strength the strength of the mask
     * @param kernelSize the size of the blur kernel
     * @return the strategy
     */
    public static ConvolutionStrategy unsharpMask(double strength, int kernelSize) {
        var imageMath = ImageMath.newInstance();
        return new ConvolutionStrategy(image -> unsharpMask(imageMath, image, strength, kernelSize));
    }

    private static ConvolutionStrategy convolve(Kernel kernel) {
        var imageMath = ImageMath.newInstance();
        return new ConvolutionStrategy(image -> imageMath.convolve(image, kernel));
    }

    /**
     * Applies an unsharp mask: adds to the image the difference between the image
     * and a box blurred copy, multiplied by the strength. The result is not clipped.
     *
     * @param imageMath the image math implementation
     * @param image the image
     * @param strength the strength of the mask
     * @param kernelSize the size of the blur kernel
     * @return the sharpened image
     */
    public static Image unsharpMask(ImageMath imageMath, Image image, double strength, int kernelSize) {
        var data = image.data();
        var blurred = imageMath.convolve(image, BlurKernel.of(kernelSize)).data();
        var result = new float[image.height()][image.width()];
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                var diff = data[y][x] - blurred[y][x];
                result[y][x] = data[y][x] + (float) (diff * strength);
            }
        }
        return image.withData(result);
    }

    /**
     * Filters the lightness of a color image.
     *
     * @param image the color image
     * @param filter the filter applied on the lightness, whose values are between 0 and 1
     * @return the red, green and blue channels of the filtered image
     */
    public static float[][][] filterLightness(RGBImage image, UnaryOperator<Image> filter) {
        var hsl = ImageUtils.fromRGBtoHSL(new float[][][]{image.r(), image.g(), image.b()});
        hsl[2] = filter.apply(new Image(image.width(), image.height(), hsl[2])).data();
        return ImageUtils.fromHSLtoRGB(hsl);
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var filtered = filterAndClip(image.asImage(), MAX_PIXEL_VALUE);
        System.arraycopy(filtered, 0, image.data(), 0, image.height());
    }

    @Override
    public void stretch(RGBImage image) {
        var rgb = filterLightness(image, lightness -> lightness.withData(filterAndClip(lightness, 1f)));
        System.arraycopy(rgb[0], 0, image.r(), 0, image.height());
        System.arraycopy(rgb[1], 0, image.g(), 0, image.height());
        System.arraycopy(rgb[2], 0, image.b(), 0, image.height());
    }

    private float[][] filterAndClip(Image image, float max) {
        var filtered = filter.apply(image).data();
        for (var row : filtered) {
            for (int x = 0; x < row.length; x++) {
                row[x] = Math.clamp(row[x], 0f, max);
            }
        }
        return filtered;
    }
}
