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
        if (image.width() == 0 && image.height() == 0) {
            return;
        }
        switch (image) {
            case ImageWrapper32 mono -> stretch(mono);
            case RGBImage rgb -> stretch(rgb);
            case null, default -> throw new IllegalArgumentException("Unsupported image type: " + image.getClass());
        }
    }

    void stretch(ImageWrapper32 image);

    default void stretch(RGBImage image) {
        var r = image.r();
        var g = image.g();
        var b = image.b();
        var rgb = new float[][][]{r, g, b};
        float[][][] hsl = ImageUtils.fromRGBtoHSL(rgb);
        var lightness = hsl[2];
        int height = hsl[0].length;
        int width = hsl[0][0].length;
        float[][] rescaledL = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rescaledL[y][x] = lightness[y][x] * 65535f;
            }
        }
        stretch(new ImageWrapper32(width, height, rescaledL, image.metadata()));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                lightness[y][x] = rescaledL[y][x] / 65535f;
            }
        }
        ImageUtils.fromHSLtoRGB(hsl, rgb);

        stretch(new ImageWrapper32(width, height, r, image.metadata()));
        stretch(new ImageWrapper32(width, height, g, image.metadata()));
        stretch(new ImageWrapper32(width, height, b, image.metadata()));
    }

}
