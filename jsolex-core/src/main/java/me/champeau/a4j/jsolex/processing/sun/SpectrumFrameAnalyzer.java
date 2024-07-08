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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

public class SpectrumFrameAnalyzer {
    public static final int MAX_DEVIATION = 10;
    private final int width;
    private final int height;
    private final Double sunDetectionThreshold;

    private Result result;
    private double avg;
    private double max;
    private double min;
    private float[] data;

    public SpectrumFrameAnalyzer(int width,
                                 int height,
                                 Double sunDetectionThreshold) {
        this.width = width;
        this.height = height;
        this.sunDetectionThreshold = sunDetectionThreshold;
    }

    public Result result() {
        return result;
    }

    public Result analyze(float[] data) {
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
            double columnAverage = 0;
            for (int y = 0; y < height; y++) {
                double value = data[y * width + x];
                avg = avg + (value - avg) / (x + y + 1);
                columnAverage = columnAverage + (value - columnAverage) / (y + 1);
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
            columnAverages[x] = columnAverage;
        }
        // compute average of averages and stddev
        var avgColumnAverage = 0d;
        for (double columnAverage : columnAverages) {
            avgColumnAverage += columnAverage;
        }
        avgColumnAverage /= width;
        var stddev = 0d;
        for (double columnAverage : columnAverages) {
            stddev += Math.pow(columnAverage - avgColumnAverage, 2);
        }
        stddev = Math.sqrt(stddev / width);
        // left and right border are the first and last columns which are more than 1 stddev away from the average
        for (int x = 0; x < width; x++) {
            if (columnAverages[x] > avgColumnAverage - stddev) {
                if (leftBorder == null) {
                    leftBorder = x;
                }
                rightBorder = x;
            }
        }
        this.result = findDistortionPolynomial(leftBorder, rightBorder);
    }

    private void performDetectionUsingExplicitThreshold() {
        Integer leftBorder = null;
        Integer rightBorder = null;
        for (int x = 0; x < width; x++) {
            double lineAvg = 0;
            for (int y = 0; y < height; y++) {
                double value = data[y * width + x];
                avg = avg + (value - avg) / (x + y + 1);
                lineAvg = lineAvg + (value - lineAvg) / (y + 1);
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
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
        avg = 0;
        max = 0;
        min = Double.MAX_VALUE;
    }

    private int findYAtMinimalValue(float[] data, int x) {
        int minY = 0;
        double min = Double.MAX_VALUE;
        var margin = Math.min(height, 1);
        for (int y = margin; y < height - margin; y++) {
            double value = data[y * width + x];
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
        int limit = (int) (0.5d * (r - l) / 2);
        // in the first pass we restrict samples to 50% of the spectrum
        int step = 8;
        for (int x = l + limit; x < r - limit; x += step) {
            var y = findYAtMinimalValue(data, x);
            if (y >= 0) {
                samplePoints.add(new Point2D(x, y));
            }
        }
        // compute average y value and stddev
        var avgY = samplePoints.stream().mapToDouble(Point2D::y).average().orElse(0);
        var stddev = Math.sqrt(samplePoints.stream().mapToDouble(p -> Math.pow(p.y() - avgY, 2)).sum() / samplePoints.size());
        // remove samples which are 2*sigma away from the average
        samplePoints.removeIf(p -> Math.abs(p.y() - avgY) > 2 * stddev);

        if (samplePoints.size() > 2) {
            var polynomial = LinearRegression.thirdOrderRegression(samplePoints.toArray(new Point2D[0])).asPolynomial();
            // In the 2d pass we consider all points but only keep those which are close enough to the first polynomial
            samplePoints.clear();
            for (int x = l; x < r; x += step) {
                var y = findYAtMinimalValue(data, x);
                if (y >= 0) {
                    var yy = polynomial.applyAsDouble(x);
                    if (Math.abs(yy - y) < MAX_DEVIATION) {
                        samplePoints.add(new Point2D(x, y));
                    }
                }
            }
            var regression = LinearRegression.thirdOrderRegression(samplePoints.toArray(new Point2D[0]));
            return new Result(leftBorder, rightBorder, regression.asPolynomial(), samplePoints);
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
                var triplet = LinearRegression.thirdOrderRegression(samplePoints.toArray(new Point2D[0]));
                return new Result(leftBorder, rightBorder, triplet.asPolynomial(), samplePoints);
            }
        }
        return new Result(leftBorder, rightBorder, null, samplePoints);
    }

    public double avg() {
        return avg;
    }

    public double max() {
        return max;
    }

    public double min() {
        return min;
    }

    public static class Result {
        private final Integer leftBorder;
        private final Integer rightBorder;
        private final DoubleUnaryOperator distortionPolynomial;
        private final List<Point2D> samplePoints;

        public Result(Integer leftBorder, Integer rightBorder, DoubleUnaryOperator distortionPolynomial, List<Point2D> samplePoints) {
            this.leftBorder = leftBorder;
            this.rightBorder = rightBorder;
            this.distortionPolynomial = distortionPolynomial;
            this.samplePoints = samplePoints;
        }

        public Optional<Integer> leftBorder() {
            return Optional.ofNullable(leftBorder);
        }

        public Optional<Integer> rightBorder() {
            return Optional.ofNullable(rightBorder);
        }

        public Optional<DoubleUnaryOperator> distortionPolynomial() {
            return Optional.ofNullable(distortionPolynomial);
        }

        public List<Point2D> getSamplePoints() {
            return samplePoints;
        }
    }
}
