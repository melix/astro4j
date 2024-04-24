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

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

public final class GammaStrategy implements StretchingStrategy {
    private final double gamma;

    public GammaStrategy(double gamma) {
        this.gamma = gamma;
    }

    /**
     * Stretches an image using the gamma correction
     *
     * @param image grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        float max = 1e-7f;
        for (float v : data) {
            max = Math.max(v, max);
        }
        for (int i = 0; i < data.length; i++) {
            var v = data[i];
            float normalized = v / max;
            float corrected = (float) Math.pow(normalized, gamma);
            data[i] = corrected * Constants.MAX_PIXEL_VALUE;
        }
    }

}
