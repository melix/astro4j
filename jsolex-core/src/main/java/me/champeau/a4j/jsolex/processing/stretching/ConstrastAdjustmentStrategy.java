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

public final class ConstrastAdjustmentStrategy implements StretchingStrategy {
    public static final ConstrastAdjustmentStrategy DEFAULT = new ConstrastAdjustmentStrategy(0, Constants.MAX_PIXEL_VALUE);

    private final float min;
    private final float max;

    public ConstrastAdjustmentStrategy(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public ConstrastAdjustmentStrategy withRange(float min, float max) {
        return new ConstrastAdjustmentStrategy(min, max);
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    @Override
    public void stretch(int width, int height, float[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < min) {
                data[i] = min;
            } else if (data[i] > max) {
                data[i] = max;
            }
        }
        RangeExpansionStrategy.DEFAULT.stretch(width, height, data);
    }

    @Override
    public void stretch(int width, int height, float[][] rgb) {
        stretch(width, height, rgb[0]);
        stretch(width, height, rgb[1]);
        stretch(width, height, rgb[2]);
    }
}
