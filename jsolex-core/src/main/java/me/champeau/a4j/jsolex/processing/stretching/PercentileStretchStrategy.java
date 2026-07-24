/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.function.BiPredicate;

import static java.util.Arrays.sort;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * Stretches an image by mapping specified percentiles to black and white points.
 * Values below the low percentile become black, values above the high percentile
 * become white, and values in between are linearly stretched.
 */
public final class PercentileStretchStrategy implements StretchingStrategy {

    /**
     * How pixels outside the percentile range are mapped.
     */
    public enum ClipMode {
        /**
         * No clipping: pure affine mapping, pixels outside the percentile range may end up
         * outside the displayable range. Preserves the full dynamic range with a gain which
         * only depends on the percentiles, making it suitable for normalizing images before
         * stacking.
         */
        NONE,
        /** Pixels below the low percentile become black, pixels above the high percentile become white. */
        CLAMP,
        /**
         * The white point is extended to the brightest pixel of the entire image, so that
         * regions brighter than the high percentile, for example excluded by a mask, are
         * not clipped.
         */
        EXTEND
    }

    private final double lowPercentile;
    private final double highPercentile;
    private final BiPredicate<Integer, Integer> pixelMask;
    private final ClipMode clipMode;

    /**
     * Creates a percentile stretch strategy.
     *
     * @param lowPercentile the low percentile (0-100), values below this become black
     * @param highPercentile the high percentile (0-100), values above this become white
     */
    public PercentileStretchStrategy(double lowPercentile, double highPercentile) {
        this(lowPercentile, highPercentile, null);
    }

    /**
     * Creates a percentile stretch strategy.
     *
     * @param lowPercentile the low percentile (0-100), values below this become black
     * @param highPercentile the high percentile (0-100), values above this become white
     * @param pixelMask optional predicate (x, y) -&gt; boolean indicating which pixels to include
     *                  in the percentile computation. If null, all pixels are included. The
     *                  stretch itself is always applied to the entire image.
     */
    public PercentileStretchStrategy(double lowPercentile, double highPercentile, BiPredicate<Integer, Integer> pixelMask) {
        this(lowPercentile, highPercentile, pixelMask, ClipMode.CLAMP);
    }

    /**
     * Creates a percentile stretch strategy.
     *
     * @param lowPercentile the low percentile (0-100)
     * @param highPercentile the high percentile (0-100)
     * @param pixelMask optional predicate (x, y) -&gt; boolean indicating which pixels to include
     *                  in the percentile computation. If null, all pixels are included. The
     *                  stretch itself is always applied to the entire image.
     * @param clipMode how pixels outside the percentile range are mapped
     */
    public PercentileStretchStrategy(double lowPercentile, double highPercentile, BiPredicate<Integer, Integer> pixelMask, ClipMode clipMode) {
        if (lowPercentile < 0 || lowPercentile > 100) {
            throw new IllegalArgumentException("Low percentile must be in range [0, 100], found: " + lowPercentile);
        }
        if (highPercentile < 0 || highPercentile > 100) {
            throw new IllegalArgumentException("High percentile must be in range [0, 100], found: " + highPercentile);
        }
        if (lowPercentile >= highPercentile) {
            throw new IllegalArgumentException("Low percentile must be less than high percentile. Found: low=" + lowPercentile + ", high=" + highPercentile);
        }
        this.lowPercentile = lowPercentile;
        this.highPercentile = highPercentile;
        this.pixelMask = pixelMask;
        this.clipMode = clipMode;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();

        if (width == 0 || height == 0) {
            return;
        }

        var pixels = PixelCollection.collect(data, width, height, pixelMask);
        if (pixels.length == 0) {
            return;
        }
        sort(pixels);
        float lowValue = percentileValue(pixels, lowPercentile);
        float highValue = percentileValue(pixels, highPercentile);
        if (clipMode == ClipMode.EXTEND) {
            for (var line : data) {
                for (var v : line) {
                    highValue = Math.max(highValue, v);
                }
            }
        }

        if (highValue <= lowValue) {
            return;
        }

        float range = highValue - lowValue;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                if (clipMode == ClipMode.NONE) {
                    data[y][x] = ((v - lowValue) / range) * MAX_PIXEL_VALUE;
                } else if (v <= lowValue) {
                    data[y][x] = 0;
                } else if (v >= highValue) {
                    data[y][x] = MAX_PIXEL_VALUE;
                } else {
                    data[y][x] = ((v - lowValue) / range) * MAX_PIXEL_VALUE;
                }
            }
        }
    }

    private static float percentileValue(float[] sorted, double percentile) {
        return sorted[(int) Math.round(percentile / 100.0 * (sorted.length - 1))];
    }
}
