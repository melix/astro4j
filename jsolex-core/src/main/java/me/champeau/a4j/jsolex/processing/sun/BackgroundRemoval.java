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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;
import static me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils.estimateBackground;
import static me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils.estimateBackgroundLevel;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class BackgroundRemoval {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundRemoval.class);
    private static final double PENALTY = 1e-6;

    private BackgroundRemoval() {

    }

    public static void removeBackground(int width,
                                        int height,
                                        float[][] data,
                                        double tolerance,
                                        double background,
                                        Ellipse ellipse) {
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = data[y][x];
                    var offcenter = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius;
                    var correction = tolerance * (offcenter * offcenter) * background;
                    var corrected = Math.max(0, v - correction);
                    data[y][x] = (float) corrected;
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
     * @param maxIterations the number of iterations
     * @return the image with background neutralized
     */
    public static ImageWrapper32 neutralizeBackground(ImageWrapper32 image, int maxIterations) {
        ImageWrapper32 img = image;
        for (int i = 0; i < maxIterations; i++) {
            img = neutralizeBackground(img);
        }
        return img;
    }

    /**
     * Neutralizes background by estimating the background level and
     * modeling it using a 2nd order regression.
     *
     * @param image the image to process
     * @return the image with background neutralized
     */
    public static ImageWrapper32 neutralizeBackground(ImageWrapper32 image) {
        return blindBackgroundNeutralization(image).neutralized();
    }

    public static BlindBackgroundNeutralizationResult blindBackgroundNeutralization(ImageWrapper32 image) {
        int bins = 64;
        var neut = blindBackgroundNeutralization2(image, bins);
        while (neut.isEmpty() && bins < 1024) {
            bins *= 2;
            neut = blindBackgroundNeutralization2(image, bins);
        }
        return neut.orElse(new BlindBackgroundNeutralizationResult(image, 0));
    }

    private static Optional<BlindBackgroundNeutralizationResult> blindBackgroundNeutralization2(ImageWrapper32 image, int bins) {
        var copy = removeZeroPixels(image);
        var data = copy.data();
        var background = 0.8 * estimateBackgroundLevel(copy.data(), bins);
        LOGGER.debug("Background neutralization level: {}", background);
        var ellipse = image.findMetadata(Ellipse.class).orElse(null);
        if (ellipse != null) {
            background = 0.8 * estimateBackground(image, ellipse);
        }
        // Find samples for 2d order regression
        List<double[]> samples = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        var height = image.height();
        var width = image.width();
        var iterations = 10;
        while (samples.size() < 16 && iterations-- > 0) {
            for (int y = 0; y < height; y += 8) {
                for (int x = 0; x < width; x += 8) {
                    if (ellipse == null || !ellipse.isWithin(x, y)) {
                        var value = data[y][x];
                        if (value < background && value > 0) {
                            // Include x^2, y^2, and xy terms
                            samples.add(new double[]{x, y, x * x, y * y, x * y});
                            values.add((double) value);
                        }
                    }
                }
            }
            background = 1.2 * background;
        }

        if (samples.size() < 16) {
            LOGGER.warn(message("cannot.perform.bg.neutralization"));
            return Optional.of(new BlindBackgroundNeutralizationResult(copy, background));
        }

        // Perform 2nd order fitting
        double avgBackground = 0;
        try {
            var regression = new OLSMultipleLinearRegression();
            regression.newSampleData(values.stream().mapToDouble(Double::doubleValue).toArray(), samples.toArray(new double[0][]));
            double[] coefficients = regression.estimateRegressionParameters();

            // Remove background using the regression model
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var value = data[y][x];
                    var estimated = coefficients[0] + coefficients[1] * x + coefficients[2] * y
                                    + coefficients[3] * x * x + coefficients[4] * y * y
                                    + coefficients[5] * x * y;
                    avgBackground += estimated;
                    data[y][x] = (float) Math.max(0, value - estimated);
                }
            }
            avgBackground /= (width * height);
        } catch (Exception ex) {
            LOGGER.warn(message("cannot.perform.bg.neutralization"));
        }
        if (avgBackground > 8 * background) {
            // something went very wrong, probably bad estimate of the background
            return Optional.empty();
        }
        return Optional.of(new BlindBackgroundNeutralizationResult(copy, avgBackground));
    }

    /**
     * Computes a background model for the given image.
     *
     * @param image the image to process
     * @return the background model
     */
    public static Optional<ImageWrapper32> backgroundModel(ImageWrapper32 image, int degree, double sigma) {
        var data = image.data();
        int height = image.height();
        int width = image.width();
        var ellipse = image.findMetadata(Ellipse.class).orElse(null);

        List<double[]> xMatrix = new ArrayList<>();
        List<Double> yVector = new ArrayList<>();
        int numTerms = (degree + 1) * (degree + 2) / 2;

        var step = Math.max(width, height) / 32;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (ellipse == null || !ellipse.isWithin(x, y)) {
                    double z = data[y][x];
                    var terms = generatePolynomialTerms(x, y, width, height, degree);
                    xMatrix.add(terms);
                    yVector.add(z);
                }
            }
        }

        // Filter samples using sigma
        var mean = yVector.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        var stddev = Math.sqrt(yVector.stream()
                                   .mapToDouble(v -> (v - mean) * (v - mean))
                                   .sum() / (yVector.size() - 1));
        var hiThreshold = mean + stddev * sigma;
        var loThreshold = mean - stddev * sigma;
        List<double[]> filteredX = new ArrayList<>();
        List<Double> filteredY = new ArrayList<>();
        for (int i = 0; i < yVector.size(); i++) {
            var v = yVector.get(i);
            if (v>0 && v < hiThreshold && v > loThreshold) {
                filteredX.add(xMatrix.get(i));
                filteredY.add(yVector.get(i));
            }
        }
        xMatrix = filteredX;
        yVector = filteredY;

        var background = new float[height][width];
        // Check for sufficient samples
        if (xMatrix.size() < numTerms) {
            LOGGER.debug("Insufficient samples: {} < {} for background model", xMatrix.size(), numTerms);
            return Optional.empty();
        }

        var mX = MatrixUtils.createRealMatrix(xMatrix.toArray(new double[0][]));
        var mY = MatrixUtils.createRealVector(yVector.stream().mapToDouble(Double::doubleValue).toArray());

        // Compute X^T * X and X^T * y
        var mXtX = mX.transpose().multiply(mX);
        var mXty = mX.transpose().operate(mY);

        var penalty = MatrixUtils.createRealIdentityMatrix(mXtX.getRowDimension()).scalarMultiply(PENALTY);
        var mXtXRegularized = mXtX.add(penalty);

        // Solve (XtX + lambda*I) * coefficients = Xty
        var solver = new LUDecomposition(mXtXRegularized).getSolver();
        var coefficients = solver.solve(mXty);

        // Generate background using the coefficients
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                double[] terms = generatePolynomialTerms(x, y, width, height, degree);
                double bgValue = 0;
                for (int i = 0; i < terms.length; i++) {
                    bgValue += coefficients.getEntry(i) * terms[i];
                }
                background[y][x] = (float) Math.clamp(bgValue, 0, Constants.MAX_PIXEL_VALUE);
            }
        });
        return Optional.of(new ImageWrapper32(width, height, background, new HashMap<>(image.metadata())));
    }

    private static double[] generatePolynomialTerms(double x, double y, int width, int height, int degree) {
        int termCount = (degree + 1) * (degree + 2) / 2;
        var terms = new double[termCount];
        double xNorm = x / (width - 1);
        double yNorm = y / (height - 1);

        int index = 0;
        for (int s = 0; s <= degree; s++) {
            for (int i = s; i >= 0; i--) {
                int j = s - i;
                terms[index++] = Math.pow(xNorm, i) * Math.pow(yNorm, j);
            }
        }

        return terms;
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
        for (var line : data) {
            for (float v : line) {
                if (v >= 1 && v < minValue) {
                    minValue = v;
                }
            }
        }
        for (var line : data) {
            for (int i = 0; i < line.length; i++) {
                float v = line[i];
                if (v == 0) {
                    line[i] = minValue;
                }
            }
        }
        return copy;
    }

    public record BlindBackgroundNeutralizationResult(
            ImageWrapper32 neutralized,
            double averageBackground
    ) {

    }
}
