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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageInterpolation;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.LinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

/**
 * Corrects the jagged edges which can appear on reconstructed images, by realigning
 * each line on the fitted ellipse from its own border measurements. The correction
 * model only depends on the detected borders and the ellipse, which are shared by
 * all pixel shifts, so it is computed once and applied to each image.
 */
public class JaggingCorrection {
    public static final double DEFAULT_SIGMA = 2.5;

    private JaggingCorrection() {
    }

    /**
     * Computes the per-line corrections from the border detections.
     *
     * @param borders the detected borders
     * @param ellipse the fitted ellipse
     * @param width the width of the reconstructed images
     * @param height the height of the reconstructed images
     * @param sigma the outlier rejection factor
     * @param oscillationModel the oscillation model which was already applied to the images, if any
     * @param debugImageEmitter when non-null, a debug chart of the corrections is emitted
     * @return the correction model, if enough measurements are available
     */
    public static Optional<JaggingModel> computeModel(PhenomenaDetector.BorderDetection borders, Ellipse ellipse, int width, int height, double sigma, OscillationCorrection.OscillationModel oscillationModel, ImageEmitter debugImageEmitter) {
        var limit = 2 * (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 100;
        var boundingBox = ellipse.boundingBox();
        var ey1 = Math.max(0, boundingBox.c());
        var ey2 = Math.min(boundingBox.d(), height);
        var measurements = new ArrayList<Correction>();
        for (var y = ey1; y < ey2; y++) {
            var line = (int) Math.round(y);
            var prediction = ellipse.findX(line);
            if (prediction.isPresent()) {
                var pair = prediction.get();
                var left = pair.a();
                var right = pair.b();
                if (left > right) {
                    var tmp = left;
                    left = right;
                    right = tmp;
                }
                if (left < 0) {
                    left = 0;
                }
                if (right >= width) {
                    right = width - 1;
                }
                // the borders are detected on the raw frames, so when the oscillation correction
                // has already shifted the lines, the detected positions must be shifted too
                var oscillationShift = oscillationModel != null ? oscillationModel.shiftAt(line) : 0;
                var detectedX1 = borders.left()[line] >= 0 ? borders.left()[line] - oscillationShift : -1;
                var detectedX2 = borders.right()[line] >= 0 ? borders.right()[line] - oscillationShift : -1;
                if (detectedX1 >= 0 && detectedX2 >= 0) {
                    var c = new Correction(line, detectedX1, detectedX2, left, right);
                    if (c.isValid(limit)) {
                        measurements.add(c);
                    }
                } else if (detectedX1 < 0 && detectedX2 >= 0) {
                    // left border not detected
                    var c = new Correction(line, left, detectedX2, left, right);
                    if (c.isValid(limit)) {
                        measurements.add(c);
                    }
                } else if (detectedX1 >= 0) {
                    // right border not detected
                    var c = new Correction(line, detectedX1, right, left, right);
                    if (c.isValid(limit)) {
                        measurements.add(c);
                    }
                }
            }
        }
        if (measurements.isEmpty()) {
            return Optional.empty();
        }
        var avgDx1 = measurements.stream().mapToDouble(Correction::dx1).average().orElse(0);
        var avgDx2 = measurements.stream().mapToDouble(Correction::dx2).average().orElse(0);
        var stddevDx1 = Math.sqrt(measurements.stream().mapToDouble(c -> Math.pow(c.dx1() - avgDx1, 2)).sum() / measurements.size());
        var stddevDx2 = Math.sqrt(measurements.stream().mapToDouble(c -> Math.pow(c.dx2() - avgDx2, 2)).sum() / measurements.size());
        var filtered = measurements.stream()
                .filter(p ->
                        Math.abs(p.dx1() - avgDx1) < sigma * stddevDx1 || Math.abs(p.dx2() - avgDx2) < sigma * stddevDx2
                )
                .collect(Collectors.toMap(Correction::y, c -> c));
        var polynomials = new DoubleUnaryOperator[height];
        for (var y = 0; y < height; y++) {
            var mid = filtered.get(y);
            if (mid != null) {
                polynomials[y] = buildPolynomial(List.of(mid), width);
            } else {
                // interpolate between the two closest points
                int finalY = y;
                var left = filtered.entrySet().stream()
                        .filter(e -> e.getKey() < finalY)
                        .max(Map.Entry.comparingByKey());
                var right = filtered.entrySet().stream()
                        .filter(e -> e.getKey() > finalY)
                        .min(Map.Entry.comparingByKey());
                if (left.isPresent() && right.isPresent()) {
                    polynomials[y] = buildPolynomial(List.of(left.get().getValue(), right.get().getValue()), width);
                }
                if (left.isEmpty() && right.isPresent()) {
                    y = right.get().getKey() - 1;
                }
            }
        }
        if (debugImageEmitter != null) {
            emitDebugChart(debugImageEmitter, measurements, polynomials, ellipse, width, height);
        }
        return Optional.of(new JaggingModel(polynomials));
    }

    private static DoubleUnaryOperator buildPolynomial(List<Correction> samples, int width) {
        List<Point2D> points = new ArrayList<>();
        for (var sample : samples) {
            var dx = 1.5 * (sample.x2() - sample.x1());
            if (sample.x1() - dx > 0 && sample.x2() + dx < width) {
                // this makes the correction more robust when the sample points are close to each other
                points.add(new Point2D(sample.x1() - dx, sample.x1() - dx));
                points.add(new Point2D(sample.x2() + dx, sample.x2() + dx));
            }
            points.add(new Point2D(sample.cx1(), sample.x1()));
            points.add(new Point2D(sample.cx2(), sample.x2()));
        }
        return samples.size() == 4 ?
                LinearRegression.secondOrderRegression(points.toArray(new Point2D[0])).asPolynomial() :
                LinearRegression.firstOrderRegression(points.toArray(new Point2D[0])).asPolynomial();
    }

    public static void applyCorrection(ImageWrapper32 image, JaggingModel model) {
        var data = image.data();
        var polynomials = model.polynomials();
        var corrected = new float[image.width()];
        for (var y = 0; y < data.length && y < polynomials.length; y++) {
            var poly = polynomials[y];
            if (poly == null) {
                continue;
            }
            var line = data[y];
            for (var x = 0; x < line.length; x++) {
                double srcX = poly.applyAsDouble(x);
                float value = srcX == x ? line[x] : ImageInterpolation.lanczos1D(line, srcX);
                corrected[x] = Math.clamp(value, 0, Constants.MAX_PIXEL_VALUE);
            }
            System.arraycopy(corrected, 0, line, 0, line.length);
        }
    }

    /**
     * Emits a debug chart showing, for each border, the measured shifts and the
     * correction which is actually applied at the border position, after outlier
     * filtering and interpolation across unmeasured lines.
     */
    private static void emitDebugChart(ImageEmitter imageEmitter, List<Correction> measurements, DoubleUnaryOperator[] polynomials, Ellipse ellipse, int width, int height) {
        var leftShifts = new double[height];
        var leftWeights = new double[height];
        var rightShifts = new double[height];
        var rightWeights = new double[height];
        for (var measurement : measurements) {
            leftShifts[measurement.y()] = measurement.dx1();
            leftWeights[measurement.y()] = 1;
            rightShifts[measurement.y()] = measurement.dx2();
            rightWeights[measurement.y()] = 1;
        }
        var leftCurve = new double[height];
        var rightCurve = new double[height];
        var correctedLines = 0;
        for (var y = 0; y < height; y++) {
            leftCurve[y] = Double.NaN;
            rightCurve[y] = Double.NaN;
            var poly = polynomials[y];
            if (poly == null) {
                continue;
            }
            correctedLines++;
            var prediction = ellipse.findX(y);
            if (prediction.isPresent()) {
                var pair = prediction.get();
                var left = Math.clamp(Math.min(pair.a(), pair.b()), 0, width - 1);
                var right = Math.clamp(Math.max(pair.a(), pair.b()), 0, width - 1);
                leftCurve[y] = poly.applyAsDouble(left) - left;
                rightCurve[y] = poly.applyAsDouble(right) - right;
            }
        }
        var chartTitle = String.format("Jagged edges correction: %d measured lines, %d corrected lines", measurements.size(), correctedLines);
        ShiftDebugChart.emit(imageEmitter, chartTitle, "Jagging correction", "jagging-correction", List.of(
                new ShiftDebugChart.Panel("Left border shift and applied correction", leftShifts, leftWeights, leftCurve),
                new ShiftDebugChart.Panel("Right border shift and applied correction", rightShifts, rightWeights, rightCurve)
        ));
    }

    /**
     * The per-line correction polynomials, mapping the coordinates of the corrected
     * line to the coordinates of the source line. Lines without a correction have
     * a null entry.
     */
    public record JaggingModel(DoubleUnaryOperator[] polynomials) {
    }

    private record Correction(
            /*
             * The y coordinate of the line to be corrected
             */
            int y,
            /*
             * The position of the pixel corresponding to the left border of the sun
             */
            double x1,
            /*
             * The position of the pixel corresponding to the right border of the sun
             */
            double x2,
            /*
             * The corrected position of the left border of the sun
             */
            double cx1,
            /*
             * The corrected position of the right border of the sun
             */
            double cx2
    ) {
        double dx1() {
            return x1 - cx1;
        }

        double dx2() {
            return x2 - cx2;
        }

        boolean isValid(double limit) {
            return Math.abs(dx1()) <= limit && Math.abs(dx2()) <= limit
                    && (x2 - x1) / (cx2 - cx1) > 0.9;
        }

    }
}
