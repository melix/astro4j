/*
 * Copyright 2026 the original author or authors.
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
import me.champeau.a4j.math.RowStrips;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * Lifts the shadows and/or darkens the highlights of an image. The luminance is split
 * into an edge-preserving base layer, computed with a self-guided filter, and a detail
 * layer. The tone curve is only applied to the base layer, so that local contrast is
 * kept and no halo appears along strong edges such as the solar limb. Pixels which are
 * exactly 0 are left untouched.
 */
public final class ShadowsHighlightsStrategy implements StretchingStrategy {
    public static final double DEFAULT_SHADOWS = 25;
    public static final double DEFAULT_HIGHLIGHTS = 0;
    public static final int DEFAULT_RADIUS = 30;
    public static final double MAX_AMOUNT = 100;

    private static final double EPSILON = 0.01;
    private static final double RED_WEIGHT = 0.2126;
    private static final double GREEN_WEIGHT = 0.7152;
    private static final double BLUE_WEIGHT = 0.0722;

    private final double shadows;
    private final double highlights;
    private final int radius;
    private final double liftExponent;
    private final double compressExponent;

    /**
     * Creates a shadows/highlights adjustment.
     *
     * @param shadows the amount of shadows lifting, in percent (-100 to 100, negative values darken the shadows)
     * @param highlights the amount of highlights darkening, in percent (-100 to 100, negative values brighten the highlights)
     * @param radius the radius, in pixels, of the areas which are considered as a whole
     */
    public ShadowsHighlightsStrategy(double shadows, double highlights, int radius) {
        checkAmount("shadows", shadows);
        checkAmount("highlights", highlights);
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1. Found: " + radius);
        }
        this.shadows = shadows / MAX_AMOUNT;
        this.highlights = highlights / MAX_AMOUNT;
        this.radius = radius;
        this.liftExponent = this.shadows >= 0 ? 1 / (1 + 2 * this.shadows) : 1 - 2 * this.shadows;
        this.compressExponent = this.highlights >= 0 ? 1 + 2 * this.highlights : 1 / (1 - 2 * this.highlights);
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        if (isIdentity()) {
            return;
        }
        var data = image.data();
        adjust(data, data, image.width(), image.height());
    }

    @Override
    public void stretch(RGBImage image) {
        if (isIdentity()) {
            return;
        }
        var width = image.width();
        var height = image.height();
        var r = image.r();
        var g = image.g();
        var b = image.b();
        var luminance = new float[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    luminance[y][x] = (float) (RED_WEIGHT * r[y][x] + GREEN_WEIGHT * g[y][x] + BLUE_WEIGHT * b[y][x]);
                }
            }
        });
        var adjusted = new float[height][width];
        adjust(luminance, adjusted, width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var lum = luminance[y][x];
                    if (lum <= 0) {
                        continue;
                    }
                    var max = Math.max(r[y][x], Math.max(g[y][x], b[y][x]));
                    var ratio = Math.min(adjusted[y][x] / lum, MAX_PIXEL_VALUE / max);
                    r[y][x] *= ratio;
                    g[y][x] *= ratio;
                    b[y][x] *= ratio;
                }
            }
        });
    }

    private static void checkAmount(String name, double amount) {
        if (!(Math.abs(amount) <= MAX_AMOUNT)) {
            throw new IllegalArgumentException(name + " must be between -100 and 100. Found: " + amount);
        }
    }

    private boolean isIdentity() {
        return shadows == 0 && highlights == 0;
    }

    private void adjust(float[][] luminance, float[][] output, int width, int height) {
        var normalized = new float[height][width];
        var squares = new float[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var raw = luminance[y][x];
                    var v = Float.isFinite(raw) ? Math.clamp(raw / MAX_PIXEL_VALUE, 0f, 1f) : 0f;
                    normalized[y][x] = v;
                    squares[y][x] = v * v;
                }
            }
        });
        var coefficientsB = boxMean(normalized, width, height, radius);
        var coefficientsA = boxMean(squares, width, height, radius);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var mean = coefficientsB[y][x];
                    var variance = Math.max(0, coefficientsA[y][x] - mean * mean);
                    var a = variance / (variance + EPSILON);
                    coefficientsA[y][x] = (float) a;
                    coefficientsB[y][x] = (float) (mean * (1 - a));
                }
            }
        });
        var meanA = boxMean(coefficientsA, width, height, radius);
        var meanB = boxMean(coefficientsB, width, height, radius);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var v = normalized[y][x];
                    if (v == 0) {
                        output[y][x] = 0;
                        continue;
                    }
                    var base = meanA[y][x] * v + meanB[y][x];
                    var toned = toneCurve(Math.clamp(base, 0, 1));
                    output[y][x] = (float) (Math.clamp(toned + v - base, 0, 1) * MAX_PIXEL_VALUE);
                }
            }
        });
    }

    private double toneCurve(double b) {
        var shadowsWeight = (1 - b) * (1 - b);
        var highlightsWeight = b * b;
        var lifted = Math.pow(b, liftExponent);
        var compressed = Math.pow(b, compressExponent);
        return b + shadowsWeight * (lifted - b) + highlightsWeight * (compressed - b);
    }

    private static float[][] boxMean(float[][] source, int width, int height, int radius) {
        var horizontal = new float[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var prefix = new double[width + 1];
            for (int y = yStart; y < yEnd; y++) {
                var row = source[y];
                for (int x = 0; x < width; x++) {
                    prefix[x + 1] = prefix[x] + row[x];
                }
                var out = horizontal[y];
                for (int x = 0; x < width; x++) {
                    var lo = Math.max(0, x - radius);
                    var hi = Math.min(width - 1, x + radius);
                    out[x] = (float) ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1));
                }
            }
        });
        var result = new float[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var sums = new double[width];
            for (int y = Math.max(0, yStart - radius); y <= Math.min(height - 1, yStart + radius); y++) {
                addRow(sums, horizontal[y], 1);
            }
            for (int y = yStart; y < yEnd; y++) {
                var count = Math.min(height - 1, y + radius) - Math.max(0, y - radius) + 1;
                var out = result[y];
                for (int x = 0; x < width; x++) {
                    out[x] = (float) (sums[x] / count);
                }
                var removed = y - radius;
                if (removed >= 0) {
                    addRow(sums, horizontal[removed], -1);
                }
                var added = y + radius + 1;
                if (added < height) {
                    addRow(sums, horizontal[added], 1);
                }
            }
        });
        return result;
    }

    private static void addRow(double[] sums, float[] row, int sign) {
        for (int x = 0; x < sums.length; x++) {
            sums[x] += sign * row[x];
        }
    }
}
