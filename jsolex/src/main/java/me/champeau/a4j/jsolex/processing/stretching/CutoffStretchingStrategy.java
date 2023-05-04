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

import me.champeau.a4j.jsolex.app.util.Constants;

public final class CutoffStretchingStrategy implements StretchingStrategy {
    public static final CutoffStretchingStrategy DEFAULT = new CutoffStretchingStrategy(0, Constants.MAX_PIXEL_VALUE);
    private final float min;
    private final float max;

    public CutoffStretchingStrategy(float min, float max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public void stretch(float[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < min) {
                data[i] = min;
            } else if (data[i] > max) {
                data[i] = max;
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
