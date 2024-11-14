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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
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
    private final double normalizedBlackPoint;

    public ArcsinhStretchingStrategy(float blackPoint, float stretch, double maxStretch) {
        this.blackPoint = blackPoint;
        this.stretch = stretch;
        this.maxStretch = maxStretch;
        this.asinh = asinh(stretch);
        this.normalizedBlackPoint = blackPoint / MAX_PIXEL_VALUE;
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
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            var line = data[y];
            for (int x = 0; x < width; x++) {
                var v = line[x];
                var sv = stretchPixel(v);
                line[x] = sv;
                if (Float.valueOf(line[x]).isNaN()) {
                    line[x] = 0;
                }
                line[x] = Math.max(0, line[x]);
                line[x] = Math.min(MAX_PIXEL_VALUE, line[x]);
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

    public float stretchPixel(float v) {
        if (v == 0) {
            return 0;
        }
        double original = v / MAX_PIXEL_VALUE;
        var pixel = Math.max(0, original - normalizedBlackPoint);
        double stretched = (pixel * asinh(original * stretch)) / (original * asinh);
        return (float) (stretched * MAX_PIXEL_VALUE);
    }

    @Override
    public void stretch(RGBImage image) {
        var rgb = new float[][][]{
            image.r(),
            image.g(),
            image.b()
        };
        double max = MAX_PIXEL_VALUE;
        var bp = blackPoint / max;
        int height = image.height();
        int width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double mean = (0.2126 * rgb[0][y][x] + 0.7152 * rgb[1][y][x] + 0.0722 * rgb[2][y][x]);
                for (int j = 0; j < rgb.length; j++) {
                    double original = rgb[j][y][x] / max;
                    var pixel = Math.max(0, original - bp);
                    double stretched = (pixel * asinh(original * stretch)) / (mean * asinh);
                    rgb[j][y][x] = (float) (stretched * max);
                    if (Float.valueOf(rgb[j][y][x]).isNaN()) {
                        rgb[j][y][x] = 0;
                    }
                    rgb[j][y][x] = Math.max(0, rgb[j][y][x]);
                    rgb[j][y][x] = Math.min(MAX_PIXEL_VALUE, rgb[j][y][x]);
                }
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

}
