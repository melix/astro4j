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

public final class CutoffStretchingStrategy implements StretchingStrategy {
    public static final CutoffStretchingStrategy DEFAULT = new CutoffStretchingStrategy(0, Constants.MAX_PIXEL_VALUE);
    private final float min;
    private final float max;
    private final float zeroFill;
    private final float maxFill;

    public CutoffStretchingStrategy(float min, float max) {
        this(min, max, min, max);
    }

    public CutoffStretchingStrategy(float min, float max, float zeroFill, float maxFill) {
        this.min = min;
        this.max = max;
        this.zeroFill = zeroFill;
        this.maxFill = maxFill;
    }

    @Override
    public void stretch(float[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < min) {
                data[i] = zeroFill;
            } else if (data[i] > max) {
                data[i] = maxFill;
            }
        }
    }

    @Override
    public void stretch(float[][] rgb) {
        stretch(rgb[0]);
        stretch(rgb[1]);
        stretch(rgb[2]);
    }
}
