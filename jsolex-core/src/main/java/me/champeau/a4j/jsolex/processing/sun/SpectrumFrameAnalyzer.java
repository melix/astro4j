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
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

public class SpectrumFrameAnalyzer {
    public static final int MAX_DEVIATION = 4;
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
        if (polynomial != null) {
            this.result = new Result(
                    leftBorder,
                    rightBorder,
                    null,
                    List.of()
            );
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
                lineAvg += value;
            }
            lineAvg /= height;
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
        var fallback = new ArrayList<>(samplePoints);
        if (samplePoints.size() < 3) {
            samplePoints = fallback;
        }

        var regression = LinearRegression.secondOrderRegression(samplePoints.toArray(new Point2D[0]));
        return new Result(leftBorder, rightBorder, new DoubleQuadruplet(
                0,
                regression.a(),
                regression.b(),
                regression.c()
        ), samplePoints);
    }

    private double findLocalMinimumClosestTo(int column, float[][] data, double targetY) {
        record Minimum(double y, float value) {
        }
        var minima = new ArrayList<Minimum>();

        int height = data.length;
        int y = 0;
        while (y < height - 1) {
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
                    var center = (start + end) / 2d;
                    minima.add(new Minimum(center, v));
                }
            }

            y++;
        }

        return minima.stream()
                .filter(m -> targetY < 0 || Math.abs(m.y() - targetY) <= MAX_DEVIATION)
                .min(Comparator.comparingDouble(Minimum::value))
                .map(Minimum::y)
                .orElse(-1d);
    }


    private double findLocalMinimum(int column, float[][] data, int skip) {
        record Minimum(double y, float value) {
        }
        var minima = new ArrayList<Minimum>();

        int height = data.length;
        int margin = Math.max(1, height / 10);
        int y = margin + 1;
        while (y < height - margin - 1) {
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
                    var center = (start + end) / 2d;
                    minima.add(new Minimum(center, v));
                }
            }

            y++;
        }
        return minima.stream()
                .sorted(Comparator.comparingDouble(Minimum::value))
                .skip(skip)
                .findFirst()
                .map(Minimum::y)
                .orElse(-1d);
    }

    public void forcePolynomial(DoubleUnaryOperator polynomial) {
        this.polynomial = polynomial;
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
