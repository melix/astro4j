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
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

public sealed interface StretchingStrategy permits
    ArcsinhStretchingStrategy,
    CutoffStretchingStrategy,
    LinearStrechingStrategy,
    NegativeImageStrategy,
    RangeExpansionStrategy,
    ClaheStrategy,
    GammaStrategy,
    AutohistogramStrategy,
    ContrastAdjustmentStrategy,
    DynamicStretchStrategy,
    StretchingChain {

    default void stretch(ImageWrapper image) {
        switch (image) {
            case ImageWrapper32 mono -> stretch(mono);
            case ColorizedImageWrapper colorized -> stretch(colorized.mono());
            case RGBImage rgb -> stretch(rgb);
            case null, default -> throw new IllegalArgumentException("Unsupported image type: " + image.getClass());
        }
    }

    void stretch(ImageWrapper32 image);

    default void stretch(RGBImage image) {
        var r = image.r();
        var g = image.g();
        var b = image.b();
        var rgb = new float[][]{r, g, b};
        float[][] hsl = ImageUtils.fromRGBtoHSL(rgb);
        var lightness = hsl[2];
        float[] rescaledL = new float[lightness.length];
        for (int i = 0; i < lightness.length; i++) {
            rescaledL[i] = lightness[i] * 65535f;
        }
        stretch(new ImageWrapper32(image.width(), image.height(), rescaledL, image.metadata()));
        for (int i = 0; i < rescaledL.length; i++) {
            lightness[i] = rescaledL[i] / 65535f;
        }
        ImageUtils.fromHSLtoRGB(hsl, rgb);

        stretch(new ImageWrapper32(image.width(), image.height(), r, image.metadata()));
        stretch(new ImageWrapper32(image.width(), image.height(), g, image.metadata()));
        stretch(new ImageWrapper32(image.width(), image.height(), b, image.metadata()));
    }

}
