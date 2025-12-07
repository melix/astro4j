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

// Not worth running on GPU: memory transfer overhead dominates the simple linear scaling
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
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        double min = min(data);
        double max = max(data);
        double range = max - min;
        if (range == 0) {
            return;
        }
        rescale(data, image.width(), image.height(), range, min);
    }

    private void rescale(float[][] data, int width, int height, double range, double min) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = (float) (hi / range * (data[y][x] - min));
                data[y][x] = v;
            }
        }
    }

    private double min(float[][] array) {
        if (array.length == 0) {
            return lo;
        }
        double min = Double.MAX_VALUE;
        for (float[] line : array) {
            for (float v : line) {
                if (v < min) {
                    min = v;
                }
            }
        }
        return min;
    }

    private double max(float[][] array) {
        if (array.length == 0) {
            return hi;
        }
        double max = -Double.MAX_VALUE;
        for (float[] line : array) {
            for (float v : line) {
                if (v > max) {
                    max = v;
                }
            }
        }
        return max;
    }
}
