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

public final class ContrastAdjustmentStrategy implements StretchingStrategy {
    public static final ContrastAdjustmentStrategy DEFAULT = new ContrastAdjustmentStrategy(0, .95f*Constants.MAX_PIXEL_VALUE);

    private final boolean normalize;
    private final float min;
    private final float max;

    public ContrastAdjustmentStrategy(float min, float max) {
        this(min, max, false);
    }

    private ContrastAdjustmentStrategy(float min, float max, boolean normalize) {
        this.min = min;
        this.max = max;
        this.normalize = normalize;
    }

    public ContrastAdjustmentStrategy withRange(float min, float max) {
        return new ContrastAdjustmentStrategy(min, max, normalize);
    }

    public ContrastAdjustmentStrategy withNormalize(boolean normalize) {
        return new ContrastAdjustmentStrategy(min, max, normalize);
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
                data[i] = 0;
            } else if (data[i] > max) {
                data[i] = max;
            }
        }
        if (normalize) {
            LinearStrechingStrategy.DEFAULT.stretch(width, height, data);
        }
    }

}
