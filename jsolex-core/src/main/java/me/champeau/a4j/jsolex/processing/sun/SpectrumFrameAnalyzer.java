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
import me.champeau.a4j.math.tuples.DoubleTriplet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SpectrumFrameAnalyzer {
    private final int width;
    private final int height;
    private final double sunDetectionThreshold;
    private final List<Point2D> samplePoints;

    private Integer leftBorder;
    private Integer rightBorder;
    private double avg;
    private double max;
    private double min;

    public SpectrumFrameAnalyzer(int width,
                                 int height,
                                 double sunDetectionThreshold) {
        this.width = width;
        this.height = height;
        this.samplePoints = new ArrayList<>(width);
        this.sunDetectionThreshold = sunDetectionThreshold;
    }

    public void analyze(float[] data) {
        reset();
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
        int l = 0;
        int r = width;
        if (leftBorder != null) {
            l = leftBorder;
        }
        if (rightBorder != null) {
            r = rightBorder + 1;
        }
        int step = 8;
        for (int x = l; x < r; x += step) {
            var y = findYAtMinimalValue(data, x);
            samplePoints.add(new Point2D(x, y));
        }
    }

    private void reset() {
        for (int x = 0; x < width; x++) {
            samplePoints.clear();
        }
        leftBorder = null;
        rightBorder = null;
        avg = 0;
        max = 0;
        min = Double.MAX_VALUE;
    }

    private int findYAtMinimalValue(float[] data, int x) {
        int minY = 0;
        double min = Double.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            double value = data[y * width + x];
            if (value < min) {
                minY = y;
                min = value;
            }
        }
        return minY;
    }

    public Optional<Integer> leftSunBorder() {
        return Optional.ofNullable(leftBorder);
    }

    public Optional<Integer> rightSunBorder() {
        return Optional.ofNullable(rightBorder);
    }

    public Optional<DoubleTriplet> findDistortionPolynomial() {
        if (samplePoints.size() > 2) {
            var triplet = LinearRegression.secondOrderRegression(samplePoints.toArray(new Point2D[0]));
            return Optional.of(triplet);
        }
        return Optional.empty();
    }

    public List<Point2D> getSamplePoints() {
        return Collections.unmodifiableList(samplePoints);
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

}
