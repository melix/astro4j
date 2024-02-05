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

import static org.apache.commons.math3.util.FastMath.asinh;

/**
 * Implements arcsinh stretching, as described in SIRIL docs:
 * https://free-astro.org/siril_doc-en/co/AsinhTransformation.html
 */
public final class ArcsinhStretchingStrategy implements StretchingStrategy {
    private final double blackPoint;
    private final double stretch;
    private final double maxStretch;
    private final double asinh;

    public ArcsinhStretchingStrategy(float blackPoint, float stretch, double maxStretch) {
        this.blackPoint = blackPoint;
        this.stretch = stretch;
        this.maxStretch = maxStretch;
        this.asinh = asinh(stretch);
    }

    public double getBlackPoint() {
        return blackPoint;
    }

    public double getStretch() {
        return stretch;
    }

    public double getMaxStretch() {
        return maxStretch;
    }

    @Override
    public void stretch(int width, int height, float[] data) {
        double max = Constants.MAX_PIXEL_VALUE;
        var bp = blackPoint / max;
        for (int i = 0; i < data.length; i++) {
            double original = data[i] / max;
            var pixel = Math.max(0, original - bp);
            double stretched = (pixel * asinh(original * stretch)) / (original * asinh);
            data[i] = (float) (stretched * max);
            if (Float.valueOf(data[i]).isNaN()) {
                data[i] = 0;
            }
            data[i] = Math.max(0, data[i]);
            data[i] = Math.min(Constants.MAX_PIXEL_VALUE, data[i]);
        }
        LinearStrechingStrategy.DEFAULT.stretch(width, height, data);
    }

    @Override
    public void stretch(int width, int height, float[][] rgb) {
        double max = Constants.MAX_PIXEL_VALUE;
        var bp = blackPoint / max;
        int length = rgb[0].length;
        for (int i = 0; i < length; i++) {
            double mean = (0.2126 * rgb[0][i] + 0.7152 * rgb[1][i] + 0.0722 * rgb[2][i]);
            for (int j = 0; j < rgb.length; j++) {
                double original = rgb[j][i] / max;
                var pixel = Math.max(0, original - bp);
                double stretched = (pixel * asinh(original * stretch)) / (mean * asinh);
                rgb[j][i] = (float) (stretched * max);
                if (Float.valueOf(rgb[j][i]).isNaN()) {
                    rgb[j][i] = 0;
                }
                rgb[j][i] = Math.max(0, rgb[j][i]);
                rgb[j][i] = Math.min(Constants.MAX_PIXEL_VALUE, rgb[j][i]);
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(width, height, rgb);
    }

}
