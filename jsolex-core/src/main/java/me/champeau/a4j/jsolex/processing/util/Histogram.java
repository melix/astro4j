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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.image.Image;

import java.util.Arrays;

/**
 * Histogram of an image
 *
 * @param values the values of the histogram
 * @param pixelCount the total number of pixels
 * @param maxValue the maximum value in a bin
 */
public record Histogram(
    int[] values,
    int pixelCount,
    int maxValue
) {
    public static Histogram of(Image image, int bins) {
        return of(image.data(), bins);
    }

    public static Histogram of(float[][] image, int bins) {
        var builder = builder(bins);
        for (float[] line : image) {
            for (float d : line) {
                builder.record(d);
            }
        }
        return builder.build();
    }

    /**
     * Returns the number of distinct levels count
     * in the image.
     *
     * @return the number of bins which have a non-zero number of pixels
     */
    public int levelsCount() {
        return (int) Arrays.stream(values)
            .filter(i -> i > 0)
            .count();
    }

    public Histogram cumulative() {
        int[] cumulative = new int[values.length];
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            cumulative[i] = sum;
        }
        return new Histogram(cumulative, pixelCount, maxValue);
    }

    public Histogram derivative() {
        int[] derivative = new int[values.length];
        for (int i = 1; i < values.length; i++) {
            derivative[i] = values[i] - values[i - 1];
        }
        return new Histogram(derivative, pixelCount, maxValue);
    }

    public static int medianOf(Histogram cumulativeHistogram) {
        var limit = cumulativeHistogram.pixelCount() / 2;
        for (int i = 0; i < cumulativeHistogram.values().length; i++) {
            if (cumulativeHistogram.get(i) >= limit) {
                return i;
            }
        }
        return 0;
    }

    public int get(int index) {
        return values[index];
    }

    public static Builder builder(int bins) {
        return new Builder(bins);
    }

    public int lastPercentile(double ratio) {
        int limit = (int) (pixelCount * ratio);
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] >= limit) {
                return i;
            }
        }
        return 0;
    }

    public int percentile(double ratio) {
        int limit = (int) (pixelCount * ratio);
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= limit) {
                return i;
            }
        }
        return 0;
    }

    public static class Builder {
        private final int bins;
        private final int[] buckets;

        private int maxValue;
        private int pixelCount;

        public Builder(int bins) {
            this.bins = bins;
            this.buckets = new int[bins];
        }

        public void record(float pixel) {
            pixelCount++;
            int i = Math.max(0, Math.min(bins - 1, Math.round(pixel * bins / Constants.MAX_PIXEL_VALUE)));
            buckets[i]++;
            if (i > maxValue) {
                maxValue = i;
            }
        }

        public Histogram build() {
            return new Histogram(buckets, pixelCount, maxValue);
        }
    }
}
