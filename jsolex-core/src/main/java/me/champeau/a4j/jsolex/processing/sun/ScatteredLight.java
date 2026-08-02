/*
 * Copyright 2026 the original author or authors.
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

import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;

/**
 * Removes the excess background produced by the light scattered inside the instrument while the
 * slit crosses the solar disk.
 * <p>
 * In a spectroheliograph one column of the reconstructed image comes from one frame of the video,
 * so the amount of light scattered into a column is set by the flux entering the slit at that
 * moment. That flux is the chord of the disk at that scan position, which the ellipse gives
 * directly: it is exactly zero outside the column range the disk was scanned in, and it is defined
 * even under the disk, where no background pixel can be measured. Only its amplitude is fitted, as
 * one value per line along the slit axis.
 * <p>
 * Taking the shape from the geometry rather than from the background is what keeps the features
 * safe. Just outside the limb the only pixels available to measure a background are the ones
 * holding the prominences and the corona, so a model free to choose its own shape fits them and
 * subtracts them. Here the shape is fixed and the correction is identically zero beyond the disk's
 * column range, which is where the limb features sit on the east-west axis.
 * <p>
 * The image is not always linear, and a stretched one breaks the additivity the subtraction relies
 * on. The transfer is therefore inverted first, as a black point and an exponent chosen from the
 * image itself: the right pair makes the fitted amplitude vary smoothly from one line to the next,
 * while a wrong one forces it to jump, because the distortion depends on each line's own level.
 * The identity transfer is kept unless another beats it clearly.
 */
public class ScatteredLight {
    /**
     * Limb darkening coefficient used to weight the chord. The result is insensitive to it: it
     * only slightly rounds the profile, and the amplitude fit absorbs the difference.
     */
    private static final double LIMB_DARKENING = 0.6;

    /**
     * Full scale of the image values.
     */
    private static final double FULL_SCALE = 65535d;

    /**
     * Radius, in solar radii, beyond which a pixel is background. Slightly above 1 so that the
     * limb itself, blurred by seeing, stays out of the fit.
     */
    private static final double SKY_RADIUS = 1.06;

    /**
     * Radius beyond which the background is taken to be free of any excess, used as the reference
     * level. Falls back to the whole background when the frame is too tight for it.
     */
    private static final double FAR_RADIUS = 1.45;

    /**
     * Smoothing of the amplitude profile along the slit axis, in solar radii.
     */
    private static final double AMPLITUDE_SMOOTHING = 0.04;

    /**
     * A line must reach this fraction of the peak flux, over enough pixels, for its amplitude to
     * be measurable. The other lines are interpolated from their neighbours.
     */
    private static final double MIN_FLUX = 0.25;

    private static final int MIN_LINE_PIXELS = 60;
    private static final int REJECTION_ROUNDS = 5;
    private static final double REJECTION_SIGMA = 1.5;

    /**
     * Upper bound on the number of samples used to estimate the reference level, so that the cost
     * does not grow with the image size.
     */
    private static final int MAX_BACKGROUND_SAMPLES = 200_000;

    /**
     * Candidate black points and exponents for the transfer inverse.
     */
    private static final double[] BLACK_POINTS = {0, 0.02, 0.05};
    private static final double[] EXPONENTS = {0.75, 1.0, 1.25, 1.5, 2.0};

    /**
     * A non identity transfer is only used when it beats the identity by this factor, so that an
     * image which is already linear is left alone.
     */
    private static final double ACCEPTANCE_FACTOR = 1.3;

    private ScatteredLight() {
    }

    /**
     * Removes the scattered light background from an image, in place.
     * <p>
     * Each pass measures the amplitude again on the result of the previous one, so a second pass
     * only acts on what the first left behind.
     *
     * @param width the image width
     * @param height the image height
     * @param data the image data, modified in place
     * @param ellipse the solar disk
     * @param strength the fraction of the estimated background which is subtracted
     * @param iterations the number of passes, zero leaving the image untouched
     */
    public static void remove(int width, int height, float[][] data, Ellipse ellipse, double strength, int iterations) {
        if (strength == 0 || iterations <= 0) {
            return;
        }
        for (int i = 0; i < iterations; i++) {
            removeOnce(width, height, data, ellipse, strength);
        }
    }

    private static void removeOnce(int width, int height, float[][] data, Ellipse ellipse, double strength) {
        var center = ellipse.center();
        double cx = center.a();
        double cy = center.b();
        double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        if (radius <= 0) {
            return;
        }
        var rows = validRows(width, height, data);
        int firstRow = rows[0];
        int lastRow = rows[1];
        var flux = chordFlux(width, height, cx, cy, radius);

        var transfer = selectTransfer(width, height, data, flux, cx, cy, radius, firstRow, lastRow);
        double blackPoint = transfer[0];
        double exponent = transfer[1];

        double base = referenceLevel(width, data, blackPoint, exponent, cx, cy, radius, firstRow, lastRow);
        var amplitude = amplitudeProfile(width, height, data, blackPoint, exponent, base, flux,
            cx, cy, radius, firstRow, lastRow);
        smooth(amplitude, AMPLITUDE_SMOOTHING * radius, firstRow, lastRow);

        for (int y = firstRow; y <= lastRow; y++) {
            double gain = strength * amplitude[y];
            if (gain <= 0) {
                continue;
            }
            var line = data[y];
            double dy = y - cy;
            for (int x = 0; x < width; x++) {
                double f = flux[x];
                if (f <= 0) {
                    continue;
                }
                double dx = x - cx;
                if (Math.sqrt(dx * dx + dy * dy) / radius <= 1) {
                    continue;
                }
                double value = line[x] / FULL_SCALE;
                if (value <= blackPoint) {
                    continue;
                }
                double corrected = linearize(value, blackPoint, exponent) - gain * f;
                if (corrected < 0) {
                    corrected = 0;
                }
                line[x] = (float) (delinearize(corrected, blackPoint, exponent) * FULL_SCALE);
            }
        }
    }

    /**
     * Determines the lines which carry data. Padding added when fitting an image to a canvas is a
     * constant fill, so it is found by looking for lines holding a single value, contiguous with
     * the top and bottom borders. When no such line exists every line carries content and none is
     * excluded: dropping lines which hold real signal would leave them uncorrected.
     */
    static int[] validRows(int width, int height, float[][] data) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        double tolerance = Math.max(1e-6, 1e-3 * (max - min));
        int first = -1;
        int last = -1;
        boolean anyConstant = false;
        for (int y = 0; y < height; y++) {
            float lineMin = Float.MAX_VALUE;
            float lineMax = -Float.MAX_VALUE;
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                lineMin = Math.min(lineMin, v);
                lineMax = Math.max(lineMax, v);
            }
            if (lineMax - lineMin <= tolerance) {
                anyConstant = true;
            } else {
                if (first < 0) {
                    first = y;
                }
                last = y;
            }
        }
        if (!anyConstant || first < 0) {
            return new int[]{0, height - 1};
        }
        return new int[]{first, last};
    }

    /**
     * The flux entering the slit at each scan position, normalized to its maximum. This is the
     * limb darkened chord of the disk, so it falls to zero exactly where the slit stops crossing
     * the disk.
     */
    static double[] chordFlux(int width, int height, double cx, double cy, double radius) {
        var flux = new double[width];
        double max = 0;
        for (int x = 0; x < width; x++) {
            double dx = (x - cx) / radius;
            double sum = 0;
            for (int y = 0; y < height; y++) {
                double dy = (y - cy) / radius;
                double rho2 = dx * dx + dy * dy;
                if (rho2 <= 1) {
                    sum += 1 - LIMB_DARKENING * (1 - Math.sqrt(1 - rho2));
                }
            }
            flux[x] = sum;
            max = Math.max(max, sum);
        }
        if (max > 0) {
            for (int x = 0; x < width; x++) {
                flux[x] /= max;
            }
        }
        return flux;
    }

    private static double linearize(double value, double blackPoint, double exponent) {
        double scaled = (value - blackPoint) / (1 - blackPoint);
        if (scaled <= 0) {
            return 0;
        }
        return exponent == 1 ? scaled : Math.pow(scaled, exponent);
    }

    private static double delinearize(double value, double blackPoint, double exponent) {
        if (value <= 0) {
            return blackPoint;
        }
        double scaled = exponent == 1 ? value : Math.pow(value, 1 / exponent);
        return scaled * (1 - blackPoint) + blackPoint;
    }

    private static double linearizeAt(float[][] data, int x, int y, double blackPoint, double exponent) {
        return linearize(data[y][x] / FULL_SCALE, blackPoint, exponent);
    }

    /**
     * Chooses the black point and exponent which make the fitted amplitude vary smoothly from one
     * line to the next. Under a wrong transfer each line needs a different effective amplitude,
     * because the distortion depends on that line's own level, and the profile jumps.
     */
    private static double[] selectTransfer(int width, int height, float[][] data, double[] flux,
                                           double cx, double cy, double radius, int firstRow, int lastRow) {
        double bestScore = Double.MAX_VALUE;
        double bestBlack = 0;
        double bestExponent = 1;
        double identityScore = Double.MAX_VALUE;
        for (var blackPoint : BLACK_POINTS) {
            for (var exponent : EXPONENTS) {
                double base = referenceLevel(width, data, blackPoint, exponent, cx, cy, radius, firstRow, lastRow);
                var raw = rawAmplitudes(width, height, data, blackPoint, exponent, base, flux,
                    cx, cy, radius, firstRow, lastRow, 2);
                double score = roughness(raw);
                if (Double.isNaN(score)) {
                    continue;
                }
                if (blackPoint == 0 && exponent == 1) {
                    identityScore = score;
                }
                if (score < bestScore) {
                    bestScore = score;
                    bestBlack = blackPoint;
                    bestExponent = exponent;
                }
            }
        }
        if (identityScore == Double.MAX_VALUE || bestScore * ACCEPTANCE_FACTOR >= identityScore) {
            return new double[]{0, 1};
        }
        return new double[]{bestBlack, bestExponent};
    }

    /**
     * Median absolute step of the amplitude from one measured line to the next, relative to its
     * own level, so that transfers which simply shrink everything are not favoured.
     */
    private static double roughness(double[] raw) {
        var measured = Arrays.stream(raw).filter(v -> !Double.isNaN(v)).toArray();
        if (measured.length < 30) {
            return Double.NaN;
        }
        var magnitudes = Arrays.stream(measured).map(Math::abs).toArray();
        double level = median(magnitudes);
        if (level <= 0) {
            return Double.NaN;
        }
        var steps = new double[measured.length - 1];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = Math.abs(measured[i + 1] - measured[i]);
        }
        return median(steps) / level;
    }

    private static double referenceLevel(int width, float[][] data, double blackPoint, double exponent,
                                         double cx, double cy, double radius, int firstRow, int lastRow) {
        var far = collectBackground(width, data, blackPoint, exponent, cx, cy, radius, firstRow, lastRow, FAR_RADIUS);
        if (far.length < 500) {
            far = collectBackground(width, data, blackPoint, exponent, cx, cy, radius, firstRow, lastRow, SKY_RADIUS);
        }
        return far.length == 0 ? 0 : median(far);
    }

    private static double[] collectBackground(int width, float[][] data, double blackPoint, double exponent,
                                              double cx, double cy, double radius,
                                              int firstRow, int lastRow, double minRadius) {
        long candidates = (long) (lastRow - firstRow + 1) * width;
        int step = (int) Math.max(1, Math.sqrt((double) candidates / MAX_BACKGROUND_SAMPLES));
        var collected = new double[MAX_BACKGROUND_SAMPLES];
        int count = 0;
        for (int y = firstRow; y <= lastRow && count < collected.length; y += step) {
            double dy = y - cy;
            for (int x = 0; x < width && count < collected.length; x += step) {
                double dx = x - cx;
                if (Math.sqrt(dx * dx + dy * dy) / radius > minRadius) {
                    collected[count++] = linearizeAt(data, x, y, blackPoint, exponent);
                }
            }
        }
        return Arrays.copyOf(collected, count);
    }

    private static double[] amplitudeProfile(int width, int height, float[][] data,
                                             double blackPoint, double exponent, double base, double[] flux,
                                             double cx, double cy, double radius, int firstRow, int lastRow) {
        var raw = rawAmplitudes(width, height, data, blackPoint, exponent, base, flux,
            cx, cy, radius, firstRow, lastRow, 1);
        interpolate(raw, firstRow, lastRow);
        for (int y = 0; y < raw.length; y++) {
            if (Double.isNaN(raw[y]) || raw[y] < 0) {
                raw[y] = 0;
            }
        }
        return raw;
    }

    /**
     * Fits, for every line, the amplitude of a background shaped like the chord. The background of
     * the line itself is absorbed by a constant and a quadratic in radius, and the pixels sitting
     * well above the fit are rejected, so that a prominence crossing the line cannot pull it up.
     */
    private static double[] rawAmplitudes(int width, int height, float[][] data,
                                          double blackPoint, double exponent, double base, double[] flux,
                                          double cx, double cy, double radius,
                                          int firstRow, int lastRow, int step) {
        var amplitudes = new double[height];
        Arrays.fill(amplitudes, Double.NaN);
        var radii = new double[width];
        var fluxes = new double[width];
        var samples = new double[width];
        for (int y = firstRow; y <= lastRow; y += step) {
            double dy = y - cy;
            int count = 0;
            double maxFlux = 0;
            int aboveThreshold = 0;
            for (int x = 0; x < width; x++) {
                double dx = x - cx;
                double r = Math.sqrt(dx * dx + dy * dy) / radius;
                if (r <= SKY_RADIUS) {
                    continue;
                }
                radii[count] = r;
                fluxes[count] = flux[x];
                samples[count] = linearizeAt(data, x, y, blackPoint, exponent) - base;
                maxFlux = Math.max(maxFlux, flux[x]);
                if (flux[x] > 0.15) {
                    aboveThreshold++;
                }
                count++;
            }
            if (count < MIN_LINE_PIXELS || maxFlux < MIN_FLUX || aboveThreshold < MIN_LINE_PIXELS / 2) {
                continue;
            }
            var value = fitLine(radii, fluxes, samples, count);
            if (!Double.isNaN(value)) {
                amplitudes[y] = value;
            }
        }
        return amplitudes;
    }

    private static double fitLine(double[] radii, double[] fluxes, double[] samples, int count) {
        var keep = new boolean[count];
        Arrays.fill(keep, 0, count, true);
        double result = Double.NaN;
        var residuals = new double[count];
        for (int round = 0; round < REJECTION_ROUNDS; round++) {
            var coefficients = solveLeastSquares(radii, fluxes, samples, keep, count);
            if (coefficients == null) {
                return result;
            }
            result = coefficients[3];
            int kept = 0;
            for (int i = 0; i < count; i++) {
                double r = radii[i];
                double model = coefficients[0] + coefficients[1] * r + coefficients[2] * r * r
                               + coefficients[3] * fluxes[i];
                residuals[i] = samples[i] - model;
                if (keep[i]) {
                    kept++;
                }
            }
            if (kept < 30) {
                return result;
            }
            double sigma = 1.4826 * medianAbsoluteDeviation(residuals, keep, count);
            if (sigma <= 0) {
                return result;
            }
            for (int i = 0; i < count; i++) {
                keep[i] = residuals[i] < REJECTION_SIGMA * sigma;
            }
        }
        return result;
    }

    /**
     * Solves the four parameter model by normal equations. The design is small and well scaled, so
     * a direct elimination is enough.
     */
    private static double[] solveLeastSquares(double[] radii, double[] fluxes, double[] samples,
                                              boolean[] keep, int count) {
        var normal = new double[4][5];
        var terms = new double[4];
        for (int i = 0; i < count; i++) {
            if (!keep[i]) {
                continue;
            }
            double r = radii[i];
            terms[0] = 1;
            terms[1] = r;
            terms[2] = r * r;
            terms[3] = fluxes[i];
            for (int a = 0; a < 4; a++) {
                for (int b = 0; b < 4; b++) {
                    normal[a][b] += terms[a] * terms[b];
                }
                normal[a][4] += terms[a] * samples[i];
            }
        }
        for (int col = 0; col < 4; col++) {
            int pivot = col;
            for (int row = col + 1; row < 4; row++) {
                if (Math.abs(normal[row][col]) > Math.abs(normal[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(normal[pivot][col]) < 1e-12) {
                return null;
            }
            var tmp = normal[col];
            normal[col] = normal[pivot];
            normal[pivot] = tmp;
            for (int row = 0; row < 4; row++) {
                if (row == col) {
                    continue;
                }
                double factor = normal[row][col] / normal[col][col];
                for (int c = col; c < 5; c++) {
                    normal[row][c] -= factor * normal[col][c];
                }
            }
        }
        var solution = new double[4];
        for (int i = 0; i < 4; i++) {
            solution[i] = normal[i][4] / normal[i][i];
        }
        return solution;
    }

    private static double medianAbsoluteDeviation(double[] residuals, boolean[] keep, int count) {
        int kept = 0;
        for (int i = 0; i < count; i++) {
            if (keep[i]) {
                kept++;
            }
        }
        if (kept == 0) {
            return 0;
        }
        var values = new double[kept];
        int index = 0;
        for (int i = 0; i < count; i++) {
            if (keep[i]) {
                values[index++] = residuals[i];
            }
        }
        double center = median(values);
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.abs(values[i] - center);
        }
        return median(values);
    }

    private static void interpolate(double[] values, int firstRow, int lastRow) {
        int previous = -1;
        for (int y = firstRow; y <= lastRow; y++) {
            if (Double.isNaN(values[y])) {
                continue;
            }
            if (previous >= 0 && y - previous > 1) {
                double start = values[previous];
                double end = values[y];
                for (int k = previous + 1; k < y; k++) {
                    values[k] = start + (end - start) * (k - previous) / (double) (y - previous);
                }
            }
            previous = y;
        }
        int firstMeasured = -1;
        int lastMeasured = -1;
        for (int y = firstRow; y <= lastRow; y++) {
            if (!Double.isNaN(values[y])) {
                if (firstMeasured < 0) {
                    firstMeasured = y;
                }
                lastMeasured = y;
            }
        }
        if (firstMeasured < 0) {
            Arrays.fill(values, 0);
            return;
        }
        for (int y = 0; y < firstMeasured; y++) {
            values[y] = values[firstMeasured];
        }
        for (int y = lastMeasured + 1; y < values.length; y++) {
            values[y] = values[lastMeasured];
        }
    }

    private static void smooth(double[] values, double sigma, int firstRow, int lastRow) {
        if (sigma < 1) {
            return;
        }
        int radius = (int) Math.ceil(3 * sigma);
        var kernel = new double[2 * radius + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            double w = Math.exp(-0.5 * (i / sigma) * (i / sigma));
            kernel[i + radius] = w;
            sum += w;
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        var source = values.clone();
        for (int y = 0; y < values.length; y++) {
            double acc = 0;
            for (int i = -radius; i <= radius; i++) {
                int index = Math.min(Math.max(y + i, firstRow), lastRow);
                acc += kernel[i + radius] * source[index];
            }
            values[y] = acc;
        }
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        var copy = values.clone();
        Arrays.sort(copy);
        int middle = copy.length / 2;
        if (copy.length % 2 == 1) {
            return copy[middle];
        }
        return (copy[middle - 1] + copy[middle]) / 2;
    }
}
