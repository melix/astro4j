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

import java.util.Optional;

public final class LinearStrechingStrategy implements StretchingStrategy {
    public static final LinearStrechingStrategy DEFAULT = new LinearStrechingStrategy(0, Constants.MAX_PIXEL_VALUE);

    private final float lo;
    private final float hi;

    public LinearStrechingStrategy(float lo, float hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public float getLo() {
        return lo;
    }

    public float getHi() {
        return hi;
    }

    @Override
    public void stretch(int width, int height, float[] data) {
        double min = min(data).orElse((double) lo);
        double max = max(data).orElse((double) lo);
        double range = max - min;
        if (range == 0) {
            return;
        }
        for (int i = 0; i < data.length; i++) {
            float v = (float) (hi /range * (data[i] - min));
            data[i] = v;
        }
    }

    private static Optional<Double> min(float[] array) {
        if (array.length == 0) {
            return Optional.empty();
        }
        double min = Double.MAX_VALUE;
        for (float v : array) {
            if (v < min) {
                min = v;
            }
        }
        return Optional.of(min);
    }

    private static Optional<Double> max(float[] array) {
        if (array.length == 0) {
            return Optional.empty();
        }
        double max = Double.MIN_VALUE;
        for (float v : array) {
            if (v > max) {
                max = v;
            }
        }
        return Optional.of(max);
    }
}
