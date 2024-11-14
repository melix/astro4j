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
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

public class FlatCorrection {
    public static final double DEFAULT_LO_PERCENTILE = 0.1;
    public static final double DEFAULT_HI_PERCENTILE = 0.95;
    public static final int DEFAULT_ORDER = 2;

    private final double loPercentile;
    private final double hiPercentile;
    private final int order;

    public FlatCorrection() {
        this(DEFAULT_LO_PERCENTILE, DEFAULT_HI_PERCENTILE, DEFAULT_ORDER);
    }

    public FlatCorrection(double loPercentile, double hiPercentile, int order) {
        this.loPercentile = loPercentile;
        this.hiPercentile = hiPercentile;
        this.order = order;
    }

    public double[] computeCorrectionFactors(ImageWrapper32 source, Ellipse ellipse) {
        var blurred = ImageMath.newInstance().convolve(source.asImage(), Kernel33.GAUSSIAN_BLUR);
        int width = blurred.width();
        int height = blurred.height();
        var data = blurred.data();
        var minX = width;
        var maxX = 0;
        var minY = height;
        var maxY = 0;
        var maxValue = -Double.MAX_VALUE;
        var minValue = Double.MAX_VALUE;
        var builder = Histogram.builder(65536);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    var v = data[y][x];
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
        var lo = diskHistogram.percentile(loPercentile);
        var hi = diskHistogram.percentile(hiPercentile);
        var avgValues = new double[height];
        IntStream.range(0, height).parallel().forEach(y -> {
            double total = 0;
            double count = 0;
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    var v = data[y][x];
                    if (v > lo && v < hi) {
                        total += v;
                        count++;
                    }
                }
            }
            avgValues[y] = count == 0 ? 0 : total / count;
        });
        var weights = new double[height];
        // iterate over median values. If a value is 0, then replace it with the closest non-zero value
        for (int y = 0; y < height; y++) {
            if (avgValues[y] < lo) {
                weights[y] = 1;
                int left = y;
                while (left > 0 && avgValues[left] < lo) {
                    left--;
                }
                int right = y;
                while (right < height - 1 && avgValues[right] < lo) {
                    right++;
                }
                if (avgValues[left] > lo && avgValues[right] > lo) {
                    avgValues[y] = (avgValues[left] + avgValues[right]) / 2;
                } else if (avgValues[left] > lo) {
                    avgValues[y] = avgValues[left];
                } else if (avgValues[right] > lo) {
                    avgValues[y] = avgValues[right];
                }
            } else {
                weights[y] = 4;
            }
        }
        // perform polynomial regression on the median values
        var regression = PolynomialCurveFitter.create(order);
        var series = new WeightedObservedPoints();
        for (int y = 0; y < height; y++) {
            series.add(weights[y], y, avgValues[y]);
        }
        var fit = regression.fit(series.toList());
        var fun = new PolynomialFunction(fit);
        var fitted = IntStream.range(0, height).mapToDouble(fun::value).toArray();
        var max = Arrays.stream(fitted).max().orElse(1e-7);
        return Arrays.stream(fitted).map(v -> v / max).toArray();
    }

    public ImageWrapper32 correctImage(ImageWrapper32 source, double[] normalized) {
        int width = source.width();
        int height = source.height();
        var data = source.data();
        var outData = new float[height][width];
        for (int y = 0; y < height; y++) {
            var correction = normalized[y];
            for (int x = 0; x < width; x++) {
                outData[y][x] = (float) (data[y][x] / correction);
            }
        }
        return new ImageWrapper32(width, height, outData, new HashMap<>(source.metadata()));
    }

    public ImageWrapper32 correctImage(ImageWrapper32 source) {
        return source.findMetadata(Ellipse.class)
            .map(ellipse -> correctImage(source, computeCorrectionFactors(source, ellipse)))
            .orElse(source);
    }

}
