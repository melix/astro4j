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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

public class FlatCorrection {
    private static final int MARGIN = 5;
    private static final int BAND_SIZE = 2;

    private FlatCorrection() {

    }

    public static double[] computeCorrectionFactors(ImageWrapper32 source, Ellipse ellipse) {
        int width = source.width();
        int height = source.height();
        var data = source.data();
        var minX = width;
        var maxX = 0;
        var minY = height;
        var maxY = 0;
        var maxValue = 0d;
        var minValue = Double.MAX_VALUE;
        var builder = Histogram.builder(65536);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    var v = data[x + y * width];
                    builder.record(v);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    maxValue = Math.max(maxValue, v);
                    minValue = Math.min(minValue, v);
                }
            }
        }
        var diskHistogram = builder.build().cumulative();
        var percentile = diskHistogram.percentile(0.95);
        var lo = 0.5 * percentile;
        var hi = diskHistogram.percentile(0.99);
        var medianValues = new double[height];
        IntStream.range(0, height).parallel().forEach(y -> {
            var builder2 = Histogram.builder(65536);
            for (int x = 0; x < width; x++) {
                for (int y1 = Math.max(0, y - BAND_SIZE); y1 < Math.min(height, y + BAND_SIZE); y1++) {
                    if (ellipse.isWithin(x, y1)) {
                        var v = data[x + y1 * width];
                        if (v > lo && v < hi) {
                            builder2.record(v);
                        }
                    }
                }
            }
            var histogram = builder2.build().cumulative();
            var median = Histogram.medianOf(histogram);
            medianValues[y] = median;
        });
        var total = 0d;
        for (int k = 0; k < MARGIN; k++) {
            total += medianValues[minY + k];
        }
        for (int y = 0; y < minY + MARGIN; y++) {
            medianValues[y] = total / MARGIN;
        }
        total = 0d;
        for (int k = 0; k < MARGIN; k++) {
            total += medianValues[maxY - k];
        }
        for (int y = maxY - MARGIN + 1; y < height; y++) {
            medianValues[y] = total / MARGIN;
        }
        var avg = Arrays.stream(medianValues).average().orElse(1e-7);
        for (int y = 0; y < height; y++) {
            medianValues[y] = Math.max(avg, medianValues[y]);
        }
        var smoothed = performSmoothing(medianValues);
        var max = Arrays.stream(smoothed).max().orElse(1e-7);
        return Arrays.stream(smoothed).map(v -> v / max).toArray();
    }

    public static ImageWrapper32 correctImage(ImageWrapper32 source, double[] normalized) {
        int width = source.width();
        int height = source.height();
        var data = source.data();
        var outData = new float[data.length];
        for (int y = 0; y < height; y++) {
            var correction = normalized[y];
            for (int x = 0; x < width; x++) {
                var idx = x + y * width;
                outData[idx] = (float) (data[idx] / correction);
            }
        }
        return new ImageWrapper32(width, height, outData, new HashMap<>(source.metadata()));
    }

    private static double[] performSmoothing(double[] values) {
        double[] result = new double[values.length];
        double max = 0;
        result[0] = (2 * values[0] + values[1]) / 3d;
        for (int i = 1; i < values.length - 1; i++) {
            result[i] = (values[i - 1] + 2 * values[i] + values[i + 1]) / 4d;
            max = Math.max(max, result[i]);
        }
        result[values.length - 1] = (2 * values[values.length - 1] + values[values.length - 2]) / 3d;
        return result;
    }
}
