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

public sealed interface StretchingStrategy permits
        ArcsinhStretchingStrategy,
        CutoffStretchingStrategy,
        LinearStrechingStrategy,
        NegativeImageStrategy,
        RangeExpansionStrategy,
        ClaheStrategy,
        ConstrastAdjustmentStrategy {
    void stretch(int width, int height, float[] data);

    default void stretch(int width, int height, float[][] rgb) {
        float[][] hsl = ImageUtils.fromRGBtoHSL(rgb);
        var lightness = hsl[2];
        float[] rescaledL = new float[lightness.length];
        for (int i = 0; i < lightness.length; i++) {
            rescaledL[i] = lightness[i] * 65535f;
        }
        stretch(width, height, rescaledL);
        for (int i = 0; i < rescaledL.length; i++) {
            lightness[i] = rescaledL[i] / 65535f;
        }
        ImageUtils.fromHSLtoRGB(hsl, rgb);
    }
}
