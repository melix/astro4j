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

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;

public class BandingReduction {
    public static final int DEFAULT_PASS_COUNT = 16;
    public static final int DEFAULT_BAND_SIZE = 24;

    private BandingReduction() {
    }

    public static void reduceBanding(int width, int height, float[][] data, int bandSize, Ellipse ellipse) {
        double[] lineAverages = lineAverages(width, height, data, ellipse);
        for (int y = 0; y < height; y++) {
            double bandAverage = computeAverageForBand(height, lineAverages, bandSize, y);
            double currentLineAverage = lineAverages[y];
            if (currentLineAverage < bandAverage) {
                Double correction = bandAverage / currentLineAverage;
                if (!correction.isInfinite() && !correction.isNaN()) {
                    for (int x = 0; x < width; x++) {
                        if (ellipse == null || ellipse.isWithin(x, y)) {
                            data[y][x] *= correction;
                        }
                    }
                }
            }
        }
        if (ellipse != null) {
            bilinearSmoothing(ellipse, width, height, data);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                data[y][x] = Math.min(v, Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static double computeAverageForBand(int height, double[] lineAverages, int bandSize, int y) {
        int halfSize = bandSize / 2;
        double sum = 0;
        int count = 0;
        for (int k = Math.max(0, y - halfSize); k < Math.min(y + halfSize + 1, height); k++) {
            if (k != y) {
                sum += lineAverages[k];
                count++;
            }
        }
        return sum / count;
    }

    private static double[] lineAverages(int width, int height, float[][] data, Ellipse ellipse) {
        if (ellipse == null) {
            return ImageMath.newInstance().lineAverages(new Image(width, height, data));
        }
        double[] averages = new double[height];
        for (int y = 0; y < height; y++) {
            averages[y] = lineAverage(width, y, data, ellipse);
        }
        return averages;
    }

    private static double lineAverage(int width, int y, float[][] data, Ellipse ellipse) {
        double sum = 0;
        int count = 0;
        for (int x = 0; x < width; x++) {
            if (ellipse.isWithin(x, y)) {
                sum += data[y][x];
                count++;
            }
        }
        return sum / count;
    }
}
