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

import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

public class SpectrumFrameAnalyzer {
    public static final int MAX_DEVIATION = 4;
    private static final int CONTINUUM_WINDOW = 16;
    private static final int SIGMA_CLIPPING_ITERATIONS = 3;
    private static final double SIGMA_CLIPPING_KAPPA = 3.0;
    private static final double MIN_SIGMA = 0.01;
    private static final double CENTROID_DEPTH_FRACTION = 0.25;
    private static final int MIN_SAMPLES_FOR_REFINEMENT = 8;
    private final int width;
    private final int height;
    private final Double sunDetectionThreshold;
    private final boolean isReducedSerFile;
    private Result result;
    private float[][] data;
    private DoubleUnaryOperator polynomial;

    public SpectrumFrameAnalyzer(int width,
                                 int height,
                                 boolean isReducedSerFile,
                                 Double sunDetectionThreshold) {
        this.width = width;
        this.height = height;
        this.sunDetectionThreshold = sunDetectionThreshold;
        this.isReducedSerFile = isReducedSerFile;
    }

    public Result result() {
        return result;
    }

    public Result analyze(float[][] data) {
        reset();
        this.data = data;
        if (sunDetectionThreshold != null) {
            performDetectionUsingExplicitThreshold();
        } else {
            performAutomaticDetection();
        }
        return result;
    }

    public Optional<IntPair> findBorders(float[][] data) {
        var borders = detectBorders(data);
        var leftBorder = borders.left();
        var rightBorder = borders.right();
        if (leftBorder == null || rightBorder == null) {
            return Optional.empty();
        }
        return Optional.of(new IntPair(leftBorder, rightBorder));
    }

    /**
     * Same as {@link #findBorders(float[][])}, but the returned positions are
     * interpolated at sub-pixel precision.
     */
    public Optional<DoublePair> findSubPixelBorders(float[][] data) {
        var borders = detectBorders(data);
        var leftBorder = borders.subPixelLeft();
        var rightBorder = borders.subPixelRight();
        if (leftBorder == null || rightBorder == null) {
            return Optional.empty();
        }
        return Optional.of(new DoublePair(leftBorder, rightBorder));
    }

    private Borders detectBorders(float[][] data) {
        return sunDetectionThreshold != null ? findBordersExplicit(data) : findBordersAuto(data);
    }

    private void performAutomaticDetection() {
        var borders = findBordersAuto(data);
        var leftBorder = borders.left();
        var rightBorder = borders.right();
        if (polynomial != null) {
            this.result = new Result(
                    leftBorder,
                    rightBorder,
                    null,
                    List.of()
            );
        }
        this.result = flattenIfReducedSerFile(findDistortionPolynomial(leftBorder, rightBorder), leftBorder, rightBorder);
    }

    /**
     * A trimmed SER file has already been resampled along the line, so the
     * remaining geometry is a constant offset.
     */
    private Result flattenIfReducedSerFile(Result result, Integer leftBorder, Integer rightBorder) {
        if (!isReducedSerFile) {
            return result;
        }
        return result.distortionQuadruplet()
                .map(quadruplet -> new Result(leftBorder, rightBorder, new DoubleQuadruplet(0, 0, 0, Math.round(quadruplet.d())), result.getSamplePoints()))
                .orElse(result);
    }

    private Borders findBordersAuto(float[][] data) {
        Integer leftBorder = null;
        Integer rightBorder = null;
        var columnAverages = new double[width];
        for (int y = 0; y < height; y++) {
            var row = data[y];
            for (int x = 0; x < width; x++) {
                columnAverages[x] += row[x];
            }
        }
        for (int x = 0; x < width; x++) {
            double avg = columnAverages[x] / height;
            // square the average to increase sensitivity
            columnAverages[x] = avg * avg;
        }
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double avg : columnAverages) {
            min = Math.min(avg, min);
            max = Math.max(avg, max);
        }
        for (int i = 0; i < columnAverages.length; i++) {
            columnAverages[i] = (columnAverages[i] - min) / (max - min);
        }
        for (int x = 0; x < width; x++) {
            if (columnAverages[x] > 0.05) {
                if (leftBorder == null) {
                    leftBorder = x;
                }
                rightBorder = x;
            }
        }
        return bordersOf(leftBorder, rightBorder, columnAverages, 0.05);
    }

    private void performDetectionUsingExplicitThreshold() {
        var borders = findBordersExplicit(data);
        var leftBorder = borders.left();
        var rightBorder = borders.right();
        this.result = findDistortionPolynomial(leftBorder, rightBorder);
    }

    private Borders findBordersExplicit(float[][] data) {
        var columnAverages = new double[width];
        for (int y = 0; y < height; y++) {
            var row = data[y];
            for (int x = 0; x < width; x++) {
                columnAverages[x] += row[x];
            }
        }
        Integer leftBorder = null;
        Integer rightBorder = null;
        for (int x = 0; x < width; x++) {
            columnAverages[x] /= height;
            if (columnAverages[x] > sunDetectionThreshold) {
                if (leftBorder == null) {
                    leftBorder = x;
                }
                rightBorder = x;
            }
        }
        return bordersOf(leftBorder, rightBorder, columnAverages, sunDetectionThreshold);
    }

    /**
     * Refines the integer border positions by interpolating, on each side, the
     * position at which the column averages cross the detection threshold.
     */
    private static Borders bordersOf(Integer leftBorder, Integer rightBorder, double[] columnAverages, double threshold) {
        Double subPixelLeft = null;
        Double subPixelRight = null;
        if (leftBorder != null && leftBorder > 0) {
            subPixelLeft = interpolateCrossing(leftBorder - 1, columnAverages[leftBorder - 1], leftBorder, columnAverages[leftBorder], threshold);
        } else if (leftBorder != null) {
            subPixelLeft = (double) leftBorder;
        }
        if (rightBorder != null && rightBorder < columnAverages.length - 1) {
            subPixelRight = interpolateCrossing(rightBorder + 1, columnAverages[rightBorder + 1], rightBorder, columnAverages[rightBorder], threshold);
        } else if (rightBorder != null) {
            subPixelRight = (double) rightBorder;
        }
        return new Borders(leftBorder, rightBorder, subPixelLeft, subPixelRight);
    }

    /**
     * Interpolates the position at which the column averages cross the threshold,
     * between the last column below the threshold and the first column above it.
     */
    private static double interpolateCrossing(int indexBelow, double valueBelow, int indexAbove, double valueAbove, double threshold) {
        var delta = valueAbove - valueBelow;
        if (delta <= 0) {
            return indexAbove;
        }
        return indexBelow + (threshold - valueBelow) * (indexAbove - indexBelow) / delta;
    }

    private void reset() {
        result = null;
    }

    private Result findDistortionPolynomial(Integer leftBorder, Integer rightBorder) {

        int l = leftBorder != null ? leftBorder : 0;
        int r = rightBorder != null ? rightBorder + 1 : width;
        int mid = (l + r) / 2;
        double previousY = -1;

        var p1 = findPolynomialAround(leftBorder, rightBorder, findLocalMinimum(mid, data, 0), mid, previousY, l, r);
        var p2 = findPolynomialAround(leftBorder, rightBorder, findLocalMinimum(mid, data, 1), mid, previousY, l, r);
        // keep polynomial with the lowest average value
        var avg1 = averageFor(p1);
        var avg2 = averageFor(p2);
        if (avg1 < avg2) {
            return p1;
        } else {
            return p2;
        }
    }

    private double averageFor(Result r) {
        if (r.distortionPolynomial().isPresent()) {
            var polynomial = r.distortionPolynomial().get();
            double sum = 0;
            int left = r.leftBorder().orElse(0);
            int right = r.rightBorder().orElse(width);
            for (int x = left; x < right; x++) {
                int y = (int) Math.round(polynomial.applyAsDouble(x));
                if (y < 0 || y >= height) {
                    continue;
                }
                sum += data[y][x];
            }
            return sum / (right - left);
        } else {
            return Double.MAX_VALUE;
        }
    }

    private Result findPolynomialAround(Integer leftBorder, Integer rightBorder, double centerY, int mid, double previousY, int l, int r) {
        var samplePoints = new ArrayList<Point2D>(width);
        if (centerY > 0) {
            samplePoints.add(new Point2D(mid, centerY));
            previousY = centerY;
        }

        for (int x = mid - 1; x >= l; x--) {
            var y = findLocalMinimumClosestTo(x, data, previousY);
            if (y > 0) {
                samplePoints.add(new Point2D(x, y));
                previousY = y;
            }
        }
        previousY = centerY;
        for (int x = mid + 1; x < r; x++) {
            var y = findLocalMinimumClosestTo(x, data, previousY);
            if (y > 0) {
                samplePoints.add(new Point2D(x, y));
                previousY = y;
            }
        }
        var regression = LinearRegression.secondOrderRegression(samplePoints.toArray(new Point2D[0]));
        if (isFinite(regression) && samplePoints.size() >= MIN_SAMPLES_FOR_REFINEMENT) {
            var refined = refineFit(regression, l, r);
            if (refined != null) {
                regression = refined.fit();
                samplePoints = new ArrayList<>(refined.samplePoints());
            }
        }
        return new Result(leftBorder, rightBorder, new DoubleQuadruplet(
                0,
                regression.a(),
                regression.b(),
                regression.c()
        ), samplePoints);
    }

    /**
     * Refines an initial fit by re-extracting the line center in each column around
     * the position predicted by the initial polynomial, using a depth-weighted centroid,
     * then performing a sigma-clipped regression weighted by line depth.
     */
    private RobustFit refineFit(DoubleTriplet initialFit, int l, int r) {
        var poly = initialFit.asPolynomial();
        var points = new ArrayList<Point2D>(r - l);
        var weights = new double[r - l];
        int count = 0;
        for (int x = l; x < r; x++) {
            var predicted = poly.applyAsDouble(x);
            if (predicted < 0 || predicted >= height) {
                continue;
            }
            var candidate = deepestMinimumNear(x, predicted);
            if (candidate.isEmpty()) {
                continue;
            }
            var sample = depthWeightedCenter(x, candidate.get());
            if (sample.weight() > 0) {
                points.add(new Point2D(x, sample.y()));
                weights[count++] = sample.weight();
            }
        }
        if (points.size() < MIN_SAMPLES_FOR_REFINEMENT) {
            return null;
        }
        return sigmaClippedFit(points, Arrays.copyOf(weights, count));
    }

    /**
     * Computes the sub-pixel center of the line in a column as the centroid of the
     * contiguous pixels within {@link #CENTROID_DEPTH_FRACTION} of the line depth
     * above the minimum, weighted by their darkness. The weight of the returned
     * sample is the depth of the line relative to the local continuum.
     */
    private LineSample depthWeightedCenter(int column, Minimum minimum) {
        var yMin = Math.clamp((int) Math.round(minimum.y()), 0, height - 1);
        var lo = Math.max(0, yMin - CONTINUUM_WINDOW);
        var hi = Math.min(height - 1, yMin + CONTINUUM_WINDOW);
        double continuum = 0;
        for (int y = lo; y <= hi; y++) {
            continuum = Math.max(continuum, data[y][column]);
        }
        var depth = continuum - minimum.value();
        if (depth <= 0) {
            return new LineSample(minimum.y(), 0);
        }
        var threshold = minimum.value() + CENTROID_DEPTH_FRACTION * depth;
        var runLo = yMin;
        while (runLo > lo && data[runLo - 1][column] <= threshold) {
            runLo--;
        }
        var runHi = yMin;
        while (runHi < hi && data[runHi + 1][column] <= threshold) {
            runHi++;
        }
        if (runHi - runLo < 2) {
            return new LineSample(minimum.y(), depth);
        }
        double sumW = 0;
        double sumWY = 0;
        for (int y = runLo; y <= runHi; y++) {
            double w = threshold - data[y][column];
            if (w > 0) {
                sumW += w;
                sumWY += w * y;
            }
        }
        if (sumW <= 0) {
            return new LineSample(minimum.y(), depth);
        }
        return new LineSample(sumWY / sumW, depth);
    }

    private static RobustFit sigmaClippedFit(List<Point2D> points, double[] weights) {
        var currentPoints = points;
        var currentWeights = weights;
        var fit = LinearRegression.secondOrderRegression(currentPoints.toArray(new Point2D[0]), currentWeights);
        if (!isFinite(fit)) {
            return null;
        }
        for (int iteration = 0; iteration < SIGMA_CLIPPING_ITERATIONS; iteration++) {
            var poly = fit.asPolynomial();
            var residuals = new double[currentPoints.size()];
            for (int i = 0; i < residuals.length; i++) {
                var point = currentPoints.get(i);
                residuals[i] = point.y() - poly.applyAsDouble(point.x());
            }
            var center = median(residuals);
            var absoluteDeviations = new double[residuals.length];
            for (int i = 0; i < residuals.length; i++) {
                absoluteDeviations[i] = Math.abs(residuals[i] - center);
            }
            var sigma = 1.4826 * median(absoluteDeviations);
            if (sigma < MIN_SIGMA) {
                break;
            }
            var cutoff = SIGMA_CLIPPING_KAPPA * sigma;
            var keptPoints = new ArrayList<Point2D>(currentPoints.size());
            var keptWeights = new double[currentPoints.size()];
            int kept = 0;
            for (int i = 0; i < residuals.length; i++) {
                if (Math.abs(residuals[i] - center) <= cutoff) {
                    keptPoints.add(currentPoints.get(i));
                    keptWeights[kept++] = currentWeights[i];
                }
            }
            if (kept == currentPoints.size() || kept < MIN_SAMPLES_FOR_REFINEMENT) {
                break;
            }
            var newFit = LinearRegression.secondOrderRegression(keptPoints.toArray(new Point2D[0]), Arrays.copyOf(keptWeights, kept));
            if (!isFinite(newFit)) {
                break;
            }
            fit = newFit;
            currentPoints = keptPoints;
            currentWeights = Arrays.copyOf(keptWeights, kept);
        }
        return new RobustFit(fit, currentPoints);
    }

    private static boolean isFinite(DoubleTriplet fit) {
        return Double.isFinite(fit.a()) && Double.isFinite(fit.b()) && Double.isFinite(fit.c());
    }

    private static double median(double[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        var mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
    }

    private double findLocalMinimumClosestTo(int column, float[][] data, double targetY) {
        return deepestMinimumNear(column, targetY)
                .map(Minimum::y)
                .orElse(-1d);
    }

    private Optional<Minimum> deepestMinimumNear(int column, double targetY) {
        return findMinima(column, data, 0, data.length - 1).stream()
                .filter(m -> targetY < 0 || Math.abs(m.y() - targetY) <= MAX_DEVIATION)
                .min(Comparator.comparingDouble(Minimum::value));
    }


    private double findLocalMinimum(int column, float[][] data, int skip) {
        int margin = Math.max(1, data.length / 10);
        return findMinima(column, data, margin + 1, data.length - margin - 1).stream()
                .sorted(Comparator.comparingDouble(Minimum::value))
                .skip(skip)
                .findFirst()
                .map(Minimum::y)
                .orElse(-1d);
    }

    /**
     * Collects the local minima of a column, between the given ordinates.
     */
    private static List<Minimum> findMinima(int column, float[][] data, int from, int to) {
        var minima = new ArrayList<Minimum>();
        int height = data.length;
        int y = from;
        while (y < to) {
            float v = data[y][column];

            int start = y;
            while (y + 1 < height && data[y + 1][column] == v) {
                y++;
            }
            int end = y;

            if (start > 0 && end < height - 1) {
                float prev = data[start - 1][column];
                float next = data[end + 1][column];
                if (v < prev && v < next) {
                    minima.add(new Minimum(refineMinimumPosition(start, end, v, prev, next), v));
                }
            }

            y++;
        }
        return minima;
    }

    /**
     * Refines the position of a single pixel minimum by fitting a parabola on the
     * minimum and its two neighbours. Plateaus keep their midpoint.
     */
    private static double refineMinimumPosition(int start, int end, float v, float prev, float next) {
        if (start < end) {
            return (start + end) / 2d;
        }
        var denominator = (double) prev - 2 * (double) v + next;
        if (denominator <= 0) {
            return start;
        }
        return start + Math.clamp(0.5 * (prev - next) / denominator, -0.5, 0.5);
    }

    public void forcePolynomial(DoubleUnaryOperator polynomial) {
        this.polynomial = polynomial;
    }

    /**
     * Analyzes the frame by tracking the line which passes through the given
     * ordinate at mid-width, instead of the darkest one.
     *
     * @param data the image data
     * @param centerY the ordinate of the target line at mid-width
     * @return the result of the analysis
     */
    public Result analyzeAround(float[][] data, double centerY) {
        reset();
        this.data = data;
        var borders = detectBorders(data);
        var leftBorder = borders.left();
        var rightBorder = borders.right();
        int l = leftBorder != null ? leftBorder : 0;
        int r = rightBorder != null ? rightBorder + 1 : width;
        int mid = (l + r) / 2;
        var seed = findLocalMinimumClosestTo(mid, data, centerY);
        var polynomialAround = findPolynomialAround(leftBorder, rightBorder, seed > 0 ? seed : centerY, mid, -1, l, r);
        this.result = flattenIfReducedSerFile(polynomialAround, leftBorder, rightBorder);
        return result;
    }

    public static class Result {
        private final Integer leftBorder;
        private final Integer rightBorder;
        private final DoubleQuadruplet distortionQuadruplet;
        private final List<Point2D> samplePoints;

        public Result(Integer leftBorder, Integer rightBorder, DoubleQuadruplet distortionQuadruplet, List<Point2D> samplePoints) {
            this.leftBorder = leftBorder;
            this.rightBorder = rightBorder;
            this.distortionQuadruplet = distortionQuadruplet;
            this.samplePoints = samplePoints;
        }

        public Optional<Integer> leftBorder() {
            return Optional.ofNullable(leftBorder);
        }

        public Optional<Integer> rightBorder() {
            return Optional.ofNullable(rightBorder);
        }

        public Optional<IntPair> borders() {
            return leftBorder().flatMap(l -> rightBorder().map(r -> new IntPair(l, r)));
        }

        public Optional<DoubleUnaryOperator> distortionPolynomial() {
            return Optional.ofNullable(distortionQuadruplet).map(DoubleQuadruplet::asPolynomial);
        }

        public Optional<DoubleQuadruplet> distortionQuadruplet() {
            return Optional.ofNullable(distortionQuadruplet);
        }

        public List<Point2D> getSamplePoints() {
            return samplePoints;
        }
    }

    public record Borders(Integer left, Integer right, Double subPixelLeft, Double subPixelRight) {
    }

    private record Minimum(double y, float value) {
    }

    private record LineSample(double y, double weight) {
    }

    private record RobustFit(DoubleTriplet fit, List<Point2D> samplePoints) {
    }
}
