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

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.math.MathUtils;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;

public class BandingReduction {
    public static final int DEFAULT_PASS_COUNT = 4;
    public static final int DEFAULT_BAND_SIZE = 24;
    public static final double DISK_AVERAGE_WEIGHT = 0.15;
    public static final double MAX_CORRECTION = 0.05;

    private BandingReduction() {
    }

    public static void reduceBanding(int width,
                                     int height,
                                     float[][] data,
                                     int bandSize,
                                     Ellipse ellipse) {
        int minY = 0;
        int maxY = height;
        if (ellipse != null) {
            var bbox = ellipse.boundingBox();
            minY = (int) Math.max(0, Math.floor(bbox.c()));
            maxY = (int) Math.min(height, Math.ceil(bbox.d()));
        }
        var lineMedians = lineMedians(width, height, data, ellipse);
        var globalMedian = ellipse != null ? computeGlobalMedian(width, height, data, ellipse) : -1.0;
        int finalMinY = minY;
        int finalMaxY = maxY;
        IntStream.range(0, height)
                .parallel()
                .forEach(y -> {
                    var bandAverage = computeMedianForBand(bandSize, y, finalMinY, finalMaxY, globalMedian, lineMedians);
                    var currentLineMedian = lineMedians[y];
                    if (bandAverage > 0 && currentLineMedian > 0) {
                        var correction = Math.max(Math.min(bandAverage / currentLineMedian, 1 + MAX_CORRECTION), 1 - MAX_CORRECTION);
                        for (var x = 0; x < width; x++) {
                            if (ellipse == null || ellipse.isWithin(x, y)) {
                                data[y][x] *= correction;
                            }
                        }
                    }
                });
        if (ellipse != null) {
            bilinearSmoothing(ellipse, width, height, data);
        }
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = data[y][x];
                data[y][x] = Math.min(v, Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static double computeMedianForBand(int bandSize,
                                               int y,
                                               int minY,
                                               int maxY,
                                               double globalMedian,
                                               double[] lineMedians) {
        if (y < minY || y >= maxY) {
            return -1.0;
        }

        var halfSize = bandSize / 2;
        int bandStart = Math.max(minY, y - halfSize);
        int bandEnd = Math.min(maxY, y + halfSize + 1);

        if (bandStart < minY) {
            var deficit = minY - bandStart;
            bandStart = minY;
            bandEnd = Math.min(bandEnd + deficit, maxY);
        }
        if (bandEnd > maxY) {
            var deficit = bandEnd - maxY;
            bandEnd = maxY;
            bandStart = Math.max(bandStart - deficit, minY);
        }

        var size = bandEnd - bandStart - 1;
        if (size < 3) {
            return -1.0;
        }

        var values = new double[size];
        var count = 0;
        for (var k = bandStart; k < bandEnd; k++) {
            if (k != y) {
                var lineMedian = lineMedians[k];
                if (lineMedian >= 0) {
                    values[count++] = lineMedian;
                }
            }
        }

        if (count < 3) {
            return -1.0;
        }

        var median = count == values.length ? MathUtils.median(values) : MathUtils.median(Arrays.copyOf(values, count));
        if (Double.isNaN(median)) {
            return -1.0;
        }

        if (globalMedian >= 0) {
            return median * (1.0 - DISK_AVERAGE_WEIGHT) + globalMedian * DISK_AVERAGE_WEIGHT;
        }
        return median;
    }

    private static double computeGlobalMedian(int width,
                                              int height,
                                              float[][] data,
                                              Ellipse ellipse) {
        var maxSize = width * height;
        var values = new double[maxSize];
        var count = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    values[count++] = data[y][x];
                }
            }
        }
        var median = count == values.length ? MathUtils.median(values) : MathUtils.median(Arrays.copyOf(values, count));
        return Double.isNaN(median) ? -1.0 : median;
    }


    private static double[] lineMedians(int width,
                                        int height,
                                        float[][] data,
                                        Ellipse ellipse) {
        return IntStream.range(0, height)
                .parallel()
                .mapToDouble(y -> lineMedian(width, y, data, ellipse))
                .toArray();
    }

    private static double lineMedian(int width,
                                     int y,
                                     float[][] data,
                                     Ellipse ellipse) {
        var values = new double[width];
        var count = 0;
        for (var x = 0; x < width; x++) {
            if (ellipse == null || ellipse.isWithin(x, y)) {
                values[count++] = data[y][x];
            }
        }
        var median = count == values.length ? MathUtils.median(values) : MathUtils.median(Arrays.copyOf(values, count));
        return Double.isNaN(median) ? -1.0 : median;
    }
}
