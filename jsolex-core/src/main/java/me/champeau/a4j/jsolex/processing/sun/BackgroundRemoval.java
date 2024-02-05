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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;
import static me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils.estimateBackgroundLevel;

public class BackgroundRemoval {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundRemoval.class);

    private BackgroundRemoval() {

    }

    public static void removeBackground(int width,
                                        int height,
                                        float[] data,
                                        double tolerance,
                                        double background,
                                        Ellipse ellipse) {
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = y * width + x;
                if (!ellipse.isWithin(x, y)) {
                    var v = data[offset];
                    var offcenter = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius;
                    var correction = tolerance * (offcenter * offcenter) * background;
                    var corrected = Math.max(0, v - correction);
                    data[offset] = (float) corrected;
                }
            }
        }
        // perform bilinear interpolation at edges for smoothing
        bilinearSmoothing(ellipse, width, height, data);
    }

    /**
     * Neutralizes background by estimating the background level and
     * modeling it using a 2nd order regression.
     *
     * @param image the image to process
     * @return the image with background neutralized
     */
    public static ImageWrapper32 neutralizeBackground(ImageWrapper32 image) {
        var copy = removeZeroPixels(image);
        var data = copy.data();
        var background = 0.8 * estimateBackgroundLevel(copy.data(), 64);
        LOGGER.debug("Background neutralization level: {}", background);

        // Find samples for 2d order regression
        List<double[]> samples = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                var idx = y * width + x;
                var value = data[idx];
                if (value < background && value > 0) {
                    // Include x^2, y^2, and xy terms
                    samples.add(new double[]{x, y, x * x, y * y, x * y});
                    values.add((double) value);
                }
            }
        }

        // Perform 2nd order fitting
        var regression = new OLSMultipleLinearRegression();
        regression.newSampleData(values.stream().mapToDouble(Double::doubleValue).toArray(), samples.toArray(new double[0][]));
        double[] coefficients = regression.estimateRegressionParameters();

        // Remove background using the regression model
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var idx = y * width + x;
                var value = data[idx];
                var estimated = coefficients[0] + coefficients[1] * x + coefficients[2] * y
                                + coefficients[3] * x * x + coefficients[4] * y * y
                                + coefficients[5] * x * y;
                data[idx] = (float) Math.max(0, value - estimated);
            }
        }
        return copy;
    }

    /**
     * This method replaces all pixels which have a value of 0 with the
     * minimal non-zero value. This is done in order to reduce artifacts
     * due to resizing for example.
     *
     * @param image the image
     */
    public static ImageWrapper32 removeZeroPixels(ImageWrapper32 image) {
        var copy = image.copy();
        var data = copy.data();
        var minValue = Float.MAX_VALUE;
        for (var v : data) {
            if (v >= 1 && v < minValue) {
                minValue = v;
            }
        }
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                data[i] = minValue;
            }
        }
        return copy;
    }
}
