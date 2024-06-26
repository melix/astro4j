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
        rescale(data, range, min);
    }

    @Override
    public void stretch(RGBImage image) {
        var r = image.r();
        var g = image.g();
        var b = image.b();
        double min = Math.min(Math.min(min(r), min(g)), min(b));
        double max = Math.max(Math.max(max(r), max(g)), max(b));
        double range = max - min;
        if (range == 0) {
            return;
        }
        rescale(r, range, min);
        rescale(g, range, min);
        rescale(b, range, min);
    }

    private void rescale(float[] data, double range, double min) {
        for (int i = 0; i < data.length; i++) {
            float v = (float) (hi / range * (data[i] - min));
            data[i] = v;
        }
    }

    private double min(float[] array) {
        if (array.length == 0) {
            return lo;
        }
        double min = Double.MAX_VALUE;
        for (float v : array) {
            if (v < min) {
                min = v;
            }
        }
        return min;
    }

    private double max(float[] array) {
        if (array.length == 0) {
            return hi;
        }
        double max = -Double.MAX_VALUE;
        for (float v : array) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }
}
