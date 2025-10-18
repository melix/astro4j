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
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;

public class BandingReduction {
    public static final int DEFAULT_PASS_COUNT = 4;
    public static final int DEFAULT_BAND_SIZE = 24;

    private BandingReduction() {
    }

    public static void reduceBanding(int width, int height, float[][] data, int bandSize, Ellipse ellipse) {
        var lineAverages = lineAverages(width, height, data, ellipse);
        var corrections = computeMultiScaleCorrections(height, lineAverages, bandSize, ellipse);

        for (var y = 0; y < height; y++) {
            var correction = corrections[y];
            if (!Double.isInfinite(correction) && !Double.isNaN(correction)) {
                for (var x = 0; x < width; x++) {
                    if (ellipse == null || ellipse.isWithin(x, y)) {
                        data[y][x] *= correction;
                    }
                }
            }
        }
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

    private static double[] computeMultiScaleCorrections(int height, double[] lineAverages, int baseBandSize, Ellipse ellipse) {
        var bandSizes = new ArrayList<Integer>();
        var currentSize = baseBandSize;
        do {
            bandSizes.add(currentSize);
            currentSize /= 2;
        } while (currentSize >= 4);

        var allCorrections = new double[bandSizes.size()][height];
        for (var i = 0; i < bandSizes.size(); i++) {
            int bandSize = bandSizes.get(i);
            var bandAverages = IntStream.range(0, height)
                    .parallel()
                    .mapToDouble(y -> computeAverageForBand(height, lineAverages, bandSize, y, ellipse))
                    .toArray();

            for (var y = 0; y < height; y++) {
                var bandAverage = bandAverages[y];
                var currentLineAverage = lineAverages[y];
                allCorrections[i][y] = bandAverage / currentLineAverage;
            }
        }

        var finalCorrections = new double[height];
        for (var y = 0; y < height; y++) {
            double sum = 0;
            var count = 0;
            for (var i = 0; i < bandSizes.size(); i++) {
                var correction = allCorrections[i][y];
                if (!Double.isInfinite(correction) && !Double.isNaN(correction)) {
                    sum += correction;
                    count++;
                }
            }
            finalCorrections[y] = count > 0 ? sum / count : 1.0;
        }

        return finalCorrections;
    }

    private static double computeAverageForBand(int height, double[] lineAverages, int bandSize, int y, Ellipse ellipse) {
        var adaptiveBandSize = computeAdaptiveBandSize(bandSize, y, height, ellipse);
        var halfSize = adaptiveBandSize / 2;
        double sum = 0;
        double weightSum = 0;

        for (var k = Math.max(0, y - halfSize); k < Math.min(y + halfSize + 1, height); k++) {
            if (k != y) {
                var lineAverage = lineAverages[k];
                if (Double.isNaN(lineAverage)) {
                    return Double.NaN;
                }
                var weight = computeWeight(y, k, height, ellipse);
                sum += lineAverage * weight;
                weightSum += weight;
            }
        }

        if (weightSum == 0) {
            return Double.NaN;
        }
        return sum / weightSum;
    }

    private static int computeAdaptiveBandSize(int baseBandSize, int y, int height, Ellipse ellipse) {
        if (ellipse == null) {
            return baseBandSize;
        }

        var centerY = ellipse.center().b();
        var poleDistance = Math.abs(y - centerY) / (height / 2.0);
        var poleProximity = Math.pow(poleDistance, 0.8);

        var adaptiveSize = (int) (baseBandSize * (1.0 - 0.5 * poleProximity));
        return Math.max(4, adaptiveSize);
    }

    private static double computeWeight(int centerY, int sampleY, int height, Ellipse ellipse) {
        var distance = Math.abs(centerY - sampleY);
        var baseWeight = 1.0 / (1.0 + distance);

        if (ellipse == null) {
            return baseWeight;
        }

        var centerEllipseY = ellipse.center().b();
        var poleFactor = Math.abs(centerY - centerEllipseY) / (height / 2.0);
        var poleWeight = 1.0 - 0.3 * Math.pow(poleFactor, 2);

        return baseWeight * poleWeight;
    }

    private static double[] lineAverages(int width, int height, float[][] data, Ellipse ellipse) {
        if (ellipse == null) {
            return ImageMath.newInstance().lineAverages(new Image(width, height, data));
        }
        return IntStream.range(0, height)
                .parallel()
                .mapToDouble(y -> lineAverage(width, y, data, ellipse))
                .toArray();
    }

    private static double lineAverage(int width, int y, float[][] data, Ellipse ellipse) {
        double sum = 0;
        var count = 0;
        for (var x = 0; x < width; x++) {
            if (ellipse.isWithin(x, y)) {
                sum += data[y][x];
                count++;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        return sum / count;
    }
}
