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
import me.champeau.a4j.jsolex.processing.util.RGBImage;

public final class ContrastAdjustmentStrategy implements StretchingStrategy {
    public static final ContrastAdjustmentStrategy DEFAULT = new ContrastAdjustmentStrategy(0, Constants.MAX_PIXEL_VALUE);

    private final float min;
    private final float max;

    public ContrastAdjustmentStrategy(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public ContrastAdjustmentStrategy withRange(float min, float max) {
        return new ContrastAdjustmentStrategy(min, max);
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        truncate(data);
    }

    private void truncate(float[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < min) {
                data[i] = 0;
            } else if (data[i] > max) {
                data[i] = max;
            }
        }
    }

    @Override
    public void stretch(RGBImage image) {
       truncate(image.r());
       truncate(image.g());
       truncate(image.b());
    }
}
