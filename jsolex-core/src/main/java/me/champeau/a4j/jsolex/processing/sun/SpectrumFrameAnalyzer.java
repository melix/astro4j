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
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

public class SpectrumFrameAnalyzer {
    public static final int MAX_DEVIATION = 8;
    private final int width;
    private final int height;
    private final Double sunDetectionThreshold;
    private final boolean isReducedSerFile;
    private Result result;
    private float[][] data;

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

    private void performAutomaticDetection() {
        Integer leftBorder = null;
        Integer rightBorder = null;
        var columnAverages = new double[width];
        for (int x = 0; x < width; x++) {
            double colSum = 0;
            for (int y = 0; y < height; y++) {
                double value = data[y][x];
                colSum += value;
            }
            columnAverages[x] = colSum / height;
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
            if (columnAverages[x] > 0.2) {
                if (leftBorder == null) {
                    leftBorder = x;
                }
                rightBorder = x;
            }
        }
        var distortionPolynomial = findDistortionPolynomial(leftBorder, rightBorder);
        this.result = distortionPolynomial;
        if (isReducedSerFile) {
            var doubleQuadruplet = distortionPolynomial.distortionQuadruplet();
            if (doubleQuadruplet.isPresent()) {
                var polynomial = doubleQuadruplet.get();
                this.result = new Result(leftBorder, rightBorder, new DoubleQuadruplet(0, 0, 0, Math.round(polynomial.d())), result.samplePoints);
            }
        }
    }

    private void performDetectionUsingExplicitThreshold() {
        Integer leftBorder = null;
        Integer rightBorder = null;
        for (int x = 0; x < width; x++) {
            double lineAvg = 0;
            for (int y = 0; y < height; y++) {
                double value = data[y][x];
                lineAvg = lineAvg + (value - lineAvg) / (y + 1);
            }
            if (lineAvg > sunDetectionThreshold) {
                if (leftBorder == null) {
                    leftBorder = x;
                }
                rightBorder = x;
            }
        }
        this.result = findDistortionPolynomial(leftBorder, rightBorder);
    }

    private void reset() {
        result = null;
    }

    private int findYAtMinimalValue(float[][] data, int x) {
        int minY = 0;
        double min = Double.MAX_VALUE;
        var margin = Math.min(height, 1);
        for (int y = margin; y < height - margin; y++) {
            double value = data[y][x];
            if (value < min) {
                minY = y;
                min = value;
            }
        }
        // If frame is overexposed, ignore match
        if (min < 0.95 * Constants.MAX_PIXEL_VALUE) {
            return minY;
        }
        return -1;
    }

    private Result findDistortionPolynomial(Integer leftBorder, Integer rightBorder) {
        var samplePoints = new ArrayList<Point2D>(width);
        int l = 0;
        int r = width;
        if (leftBorder != null) {
            l = leftBorder;
        }
        if (rightBorder != null) {
            r = rightBorder + 1;
        }
        int limit = (int) (0.75d * (r - l) / 2);
        // in the first pass we restrict samples to 50% of the spectrum
        int step = 4;
        for (int x = l + limit; x < r - limit; x += step) {
            var y = findYAtMinimalValue(data, x);
            if (y >= 0) {
                samplePoints.add(new Point2D(x, y));
            }
        }
        removeOutliersByStdDev(samplePoints, 2);

        if (samplePoints.size() > 2) {
            var polynomial = LinearRegression.secondOrderRegression(samplePoints.toArray(new Point2D[0])).asPolynomial();
            var minY = IntStream.range(l, r).mapToDouble(polynomial::applyAsDouble).min().orElse(0);
            var maxY = IntStream.range(l, r).mapToDouble(polynomial::applyAsDouble).max().orElse(0);
            var range = Math.max(16, 2 * (1 + maxY - minY));
            // In the 2d pass we consider all points but only keep those which are close enough to the first polynomial
            var sampleToValue = new HashMap<Point2D, Float>();
            for (int x = l; x < r; x += step) {
                var y = findYAtMinimalValue(data, x);
                if (y >= 0) {
                    var yy = polynomial.applyAsDouble(x);
                    if (Math.abs(yy - y) <= range) {
                        sampleToValue.put(new Point2D(x, y), data[y][x]);
                    }
                }
            }
            samplePoints = keepDarkestSamples(sampleToValue);
            samplePoints = filterByMinMaxY(samplePoints);
            removeOutliersByStdDev(samplePoints, 3);
            var regression = LinearRegression.thirdOrderRegression(samplePoints.toArray(new Point2D[0]));
            return new Result(leftBorder, rightBorder, regression, samplePoints);
        } else {
            // Not enough sample points, we have to include the whole width
            samplePoints.clear();
            for (int x = l; x < r; x += step) {
                var y = findYAtMinimalValue(data, x);
                if (y >= 0) {
                    samplePoints.add(new Point2D(x, y));
                }
            }
            if (samplePoints.size() > 2) {
                var regression = LinearRegression.secondOrderRegression(samplePoints.toArray(new Point2D[0]));
                return new Result(leftBorder, rightBorder, new DoubleQuadruplet(0, regression.a(), regression.b(), regression.c()), samplePoints);
            }
        }
        return new Result(leftBorder, rightBorder, null, samplePoints);
    }

    private static ArrayList<Point2D> keepDarkestSamples(Map<Point2D, Float> sampleToValue) {
        long limit = (long) (sampleToValue.size() * 0.8);
        return sampleToValue.entrySet().stream()
            .sorted((e1, e2) -> Float.compare(e1.getValue(), e2.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static ArrayList<Point2D> filterByMinMaxY(ArrayList<Point2D> samplePoints) {
        var minY = samplePoints.stream().mapToDouble(Point2D::y).min().orElse(0);
        var maxY = samplePoints.stream().mapToDouble(Point2D::y).max().orElse(0);
        // compute 2 groups, based on whether a point is closer to the min or max
        var minGroup = new ArrayList<Point2D>();
        var maxGroup = new ArrayList<Point2D>();
        for (var point : samplePoints) {
            if (Math.abs(point.y() - minY) < Math.abs(point.y() - maxY)) {
                minGroup.add(point);
            } else {
                maxGroup.add(point);
            }
        }
        // keep only the group with the maximum number of points
        var minSize = minGroup.size();
        var maxSize = maxGroup.size();
        if (minSize > 3 * maxSize / 2 || maxSize > 3 * minSize / 2) {
            return minSize > maxSize ? minGroup : maxGroup;
        }
        return samplePoints;
    }

    private static void removeOutliersByStdDev(ArrayList<Point2D> samplePoints, double factor) {
        // compute average y value and stddev
        var avgY = samplePoints.stream().mapToDouble(Point2D::y).average().orElse(0);
        var stddev = Math.sqrt(samplePoints.stream().mapToDouble(p -> Math.pow(p.y() - avgY, 2)).sum() / samplePoints.size());
        // remove samples which are 2*sigma away from the average
        samplePoints.removeIf(p -> Math.abs(p.y() - avgY) > factor * stddev);
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
}
