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

import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * Stretches an image by mapping specified percentiles to black and white points.
 * Values below the low percentile become black, values above the high percentile
 * become white, and values in between are linearly stretched.
 */
public final class PercentileStretchStrategy implements StretchingStrategy {
    private final double lowPercentile;
    private final double highPercentile;

    /**
     * Creates a percentile stretch strategy.
     *
     * @param lowPercentile the low percentile (0-100), values below this become black
     * @param highPercentile the high percentile (0-100), values above this become white
     */
    public PercentileStretchStrategy(double lowPercentile, double highPercentile) {
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
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();

        if (width == 0 || height == 0) {
            return;
        }

        var cumulative = Histogram.of(data, 65536).cumulative();
        float lowValue = cumulative.percentile(lowPercentile / 100.0);
        float highValue = cumulative.percentile(highPercentile / 100.0);

        if (highValue <= lowValue) {
            return;
        }

        float range = highValue - lowValue;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                if (v <= lowValue) {
                    data[y][x] = 0;
                } else if (v >= highValue) {
                    data[y][x] = MAX_PIXEL_VALUE;
                } else {
                    data[y][x] = ((v - lowValue) / range) * MAX_PIXEL_VALUE;
                }
            }
        }
    }
}
