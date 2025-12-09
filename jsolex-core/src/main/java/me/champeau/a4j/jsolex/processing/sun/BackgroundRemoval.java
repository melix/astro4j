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
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
        var height = image.height();
        var width = image.width();
        int estimatedSamples = ((width / 8) + 1) * ((height / 8) + 1);
        List<double[]> samples = new ArrayList<>(estimatedSamples);
        List<Double> values = new ArrayList<>(estimatedSamples);
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

        int numTerms = (degree + 1) * (degree + 2) / 2;
        var step = Math.max(width, height) / 32;
        int estimatedSamples = ((width / step) + 1) * ((height / step) + 1);
        List<double[]> xMatrix = new ArrayList<>(estimatedSamples);
        List<Double> yVector = new ArrayList<>(estimatedSamples);
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
        List<double[]> filteredX = new ArrayList<>(xMatrix.size());
        List<Double> filteredY = new ArrayList<>(yVector.size());
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

        generateBackgroundCPU(background, coefficients, width, height, degree);
        return Optional.of(new ImageWrapper32(width, height, background, new HashMap<>(image.metadata())));
    }

    private static double[] generatePolynomialTermsDeg1(double x, double y, int width, int height) {
        double xNorm = x / (width - 1);
        double yNorm = y / (height - 1);
        return new double[] {
                1.0,
                xNorm,
                yNorm
        };
    }

    private static double[] generatePolynomialTermsDeg2(double x, double y, int width, int height) {
        double xNorm = x / (width - 1);
        double yNorm = y / (height - 1);
        return new double[] {
                1.0,
                xNorm,
                yNorm,
                xNorm * xNorm,
                xNorm * yNorm,
                yNorm * yNorm
        };
    }

    private static double[] generatePolynomialTerms(double x, double y, int width, int height, int degree) {
        if (degree == 1) {
            return generatePolynomialTermsDeg1(x, y, width, height);
        } else if (degree == 2) {
            return generatePolynomialTermsDeg2(x, y, width, height);
        }
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

    private static void generateBackgroundCPU(float[][] background, RealVector coefficients, int width, int height, int degree) {
        int numTerms = coefficients.getDimension();
        double[] coeffs = new double[numTerms];
        for (int i = 0; i < numTerms; i++) {
            coeffs[i] = coefficients.getEntry(i);
        }

        switch (degree) {
            case 1 -> generateBackgroundCPUDeg1(background, coeffs, width, height);
            case 2 -> generateBackgroundCPUDeg2(background, coeffs, width, height);
            case 3 -> generateBackgroundCPUDeg3(background, coeffs, width, height);
            default -> generateBackgroundCPUGeneric(background, coeffs, width, height, degree);
        }
    }

    private static void generateBackgroundCPUDeg1(float[][] background, double[] coeffs, int width, int height) {
        double widthNorm = width - 1;
        double heightNorm = height - 1;
        double c0 = coeffs[0];
        double c1 = coeffs[1];
        double c2 = coeffs[2];

        for (int y = 0; y < height; y++) {
            double yNorm = y / heightNorm;
            double c0_c2y = c0 + c2 * yNorm;
            for (int x = 0; x < width; x++) {
                double xNorm = x / widthNorm;
                double bgValue = c0_c2y + c1 * xNorm;
                background[y][x] = (float) Math.clamp(bgValue, 0, Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static void generateBackgroundCPUDeg2(float[][] background, double[] coeffs, int width, int height) {
        double widthNorm = width - 1;
        double heightNorm = height - 1;
        double c0 = coeffs[0];
        double c1 = coeffs[1];
        double c2 = coeffs[2];
        double c3 = coeffs[3];
        double c4 = coeffs[4];
        double c5 = coeffs[5];

        for (int y = 0; y < height; y++) {
            double yNorm = y / heightNorm;
            double y2 = yNorm * yNorm;
            double yTerms = c0 + c2 * yNorm + c5 * y2;
            double c4y = c4 * yNorm;
            for (int x = 0; x < width; x++) {
                double xNorm = x / widthNorm;
                double x2 = xNorm * xNorm;
                double bgValue = yTerms + c1 * xNorm + c3 * x2 + c4y * xNorm;
                background[y][x] = (float) Math.clamp(bgValue, 0, Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static void generateBackgroundCPUDeg3(float[][] background, double[] coeffs, int width, int height) {
        double widthNorm = width - 1;
        double heightNorm = height - 1;
        double c0 = coeffs[0];
        double c1 = coeffs[1];
        double c2 = coeffs[2];
        double c3 = coeffs[3];
        double c4 = coeffs[4];
        double c5 = coeffs[5];
        double c6 = coeffs[6];
        double c7 = coeffs[7];
        double c8 = coeffs[8];
        double c9 = coeffs[9];

        for (int y = 0; y < height; y++) {
            double yNorm = y / heightNorm;
            double y2 = yNorm * yNorm;
            double y3 = y2 * yNorm;
            double yTerms = c0 + c2 * yNorm + c5 * y2 + c9 * y3;
            double c4y = c4 * yNorm;
            double c7y = c7 * yNorm;
            double c8y2 = c8 * y2;
            for (int x = 0; x < width; x++) {
                double xNorm = x / widthNorm;
                double x2 = xNorm * xNorm;
                double x3 = x2 * xNorm;
                double bgValue = yTerms + c1 * xNorm + c3 * x2 + c6 * x3
                               + c4y * xNorm + c7y * x2 + c8y2 * xNorm;
                background[y][x] = (float) Math.clamp(bgValue, 0, Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static void generateBackgroundCPUGeneric(float[][] background, double[] coeffs, int width, int height, int degree) {
        double widthNorm = width - 1;
        double heightNorm = height - 1;

        for (int y = 0; y < height; y++) {
            double yNorm = y / heightNorm;
            double[] yPow = new double[degree + 1];
            yPow[0] = 1.0;
            for (int i = 1; i <= degree; i++) {
                yPow[i] = yPow[i - 1] * yNorm;
            }
            for (int x = 0; x < width; x++) {
                double xNorm = x / widthNorm;
                double[] xPow = new double[degree + 1];
                xPow[0] = 1.0;
                for (int i = 1; i <= degree; i++) {
                    xPow[i] = xPow[i - 1] * xNorm;
                }
                double bgValue = 0;
                int termIdx = 0;
                for (int s = 0; s <= degree; s++) {
                    for (int i = s; i >= 0; i--) {
                        int j = s - i;
                        bgValue += coeffs[termIdx++] * xPow[i] * yPow[j];
                    }
                }
                background[y][x] = (float) Math.clamp(bgValue, 0, Constants.MAX_PIXEL_VALUE);
            }
        }
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
