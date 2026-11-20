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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.RowStrips;
import me.champeau.a4j.math.image.ImagePlane;

public sealed interface StretchingStrategy permits
    ArcsinhStretchingStrategy,
    CutoffStretchingStrategy,
    CurveTransformStrategy,
    LinearStrechingStrategy,
    NegativeImageStrategy,
    PercentileStretchStrategy,
    RangeExpansionStrategy,
    SigmoidStretchingStrategy,
    ClaheStrategy,
    ConvolutionStrategy,
        AdaptativeClaheStrategy,
    GammaStrategy,
    AutohistogramStrategy,
    ContrastAdjustmentStrategy,
    DynamicStretchStrategy,
    MidtoneTransferFunctionStrategy,
    MidtoneTransferFunctionAutostretchStrategy,
    ShadowsHighlightsStrategy,
    StretchingChain {

    default void stretch(ImageWrapper image) {
        if (image.width() == 0 && image.height() == 0) {
            return;
        }
        switch (image) {
            case ImageWrapper32 mono -> stretch(mono);
            case RGBImage rgb -> stretch(rgb);
            case null, default -> throw new IllegalArgumentException("Unsupported image type: " + image.getClass());
        }
    }

    /**
     * Stretches a monochrome image.
     *
     * @param image the image to stretch
     */
    void stretch(ImageWrapper32 image);

    /**
     * Stretches an RGB color image by converting to HSL, stretching the lightness channel,
     * and converting back to RGB.
     *
     * @param image the RGB image to stretch
     */
    default void stretch(RGBImage image) {
        int width = image.width();
        int height = image.height();
        var red = image.red();
        var green = image.green();
        var blue = image.blue();
        var hue = new float[height][width];
        var saturation = new float[height][width];
        var lightness = ImagePlane.allocate(width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var r = new float[width];
            var g = new float[width];
            var b = new float[width];
            var l = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                red.getRow(y, r);
                green.getRow(y, g);
                blue.getRow(y, b);
                ImageUtils.rgbToHsl(r, g, b, hue[y], saturation[y], l, width);
                for (int x = 0; x < width; x++) {
                    l[x] *= 65535f;
                }
                lightness.setRow(y, l);
            }
        });
        stretch(new ImageWrapper32(lightness, image.metadata()));
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var r = new float[width];
            var g = new float[width];
            var b = new float[width];
            var l = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                lightness.getRow(y, l);
                for (int x = 0; x < width; x++) {
                    l[x] /= 65535f;
                }
                ImageUtils.hslToRgb(hue[y], saturation[y], l, r, g, b, width);
                red.setRow(y, r);
                green.setRow(y, g);
                blue.setRow(y, b);
            }
        });
        stretch(new ImageWrapper32(red, image.metadata()));
        stretch(new ImageWrapper32(green, image.metadata()));
        stretch(new ImageWrapper32(blue, image.metadata()));
    }

}
