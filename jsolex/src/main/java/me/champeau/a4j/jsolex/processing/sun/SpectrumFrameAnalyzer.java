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

import me.champeau.a4j.math.DoubleTriplet;
import me.champeau.a4j.math.LinearRegression;
import me.champeau.a4j.math.Point2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SpectrumFrameAnalyzer {
    private final int width;
    private final int height;
    private final double spectrumDetectionThreshold;
    private final double sunDetectionThreshold;
    private final SpectrumLine[] spectrumLines;

    private Integer leftBorder;
    private Integer rightBorder;
    private double avg;
    private double max;
    private double min;

    public SpectrumFrameAnalyzer(int width,
                                 int height,
                                 double spectrumDetectionThreshold,
                                 double sunDetectionThreshold) {
        this.width = width;
        this.height = height;
        this.spectrumLines = new SpectrumLine[width];
        this.spectrumDetectionThreshold = spectrumDetectionThreshold;
        this.sunDetectionThreshold = sunDetectionThreshold;
    }

    public void analyze(double[] data) {
        reset();
        double lineAvg = 0;
        for (int x = 0; x < width; x++) {
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
        // We don't need to detect all the spectrum lines, let's just detect half
        int step = 2;
        for (int x = l; x < r; x += step) {
            Optional<SpectrumLine> spectrumLineMiddle = findSpectrumLine(data, x);
            spectrumLineMiddle.ifPresent(line -> spectrumLines[line.x()] = line);
        }
    }

    private void reset() {
        for (int x = 0; x < width; x++) {
            spectrumLines[x] = null;
        }
        leftBorder = null;
        rightBorder = null;
        avg = 0;
        max = 0;
        min = Double.MAX_VALUE;
    }

    private Optional<SpectrumLine> findSpectrumLine(double[] data, int x) {
        // let's detect the spectrum line
        Integer top = null;
        int bottom = 0;
        double lineAvg = 0;
        for (int y = 0; y < height; y++) {
            double value = data[y * width + x];
            value = value * value;
            lineAvg = lineAvg + (value - lineAvg) / (y + 1);
        }
        for (int y = 0; y < height; y++) {
            double value = data[y * width + x];
            value = value * value;
            if (value < spectrumDetectionThreshold * lineAvg) {
                if (top == null) {
                    top = y;
                }
                bottom = y;
            }
        }
        if (top != null) {
            return Optional.of(new SpectrumLine(x, top, bottom));
        }
        return Optional.empty();
    }

    public Optional<Integer> leftSunBorder() {
        return Optional.ofNullable(leftBorder);
    }

    public Optional<Integer> rightSunBorder() {
        return Optional.ofNullable(rightBorder);
    }

    public Optional<DoubleTriplet> findDistortionPolynomial() {
        List<Point2D> points = new ArrayList<>();
        for (SpectrumLine spectrumLine : spectrumLines) {
            if (spectrumLine != null) {
                var middle = new Point2D(spectrumLine.x(), spectrumLine.middle());
                points.add(middle);
            }
        }
        if (points.size() > 2) {
            var triplet = LinearRegression.secondOrderRegression(points.toArray(new Point2D[0]));
            return Optional.of(triplet);
        }
        return Optional.empty();
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

    public List<SpectrumLine> spectrumLines() {
        return Arrays.stream(spectrumLines)
                .filter(Objects::nonNull)
                .toList();
    }

    public SpectrumLine[] spectrumLinesArray() {
        return spectrumLines;
    }
}
