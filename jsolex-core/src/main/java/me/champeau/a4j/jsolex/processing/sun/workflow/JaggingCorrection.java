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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
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

public class JaggingCorrection {
    private static final double DEBUG_MAX_SHIFT = 8;
    public static final double DEFAULT_SIGMA = 1.5;
    private final WorkflowState state;
    private final ProcessParams processParams;
    private final ImageEmitter imageEmitter;

    public JaggingCorrection(WorkflowState state, ProcessParams processParams, ImageEmitter imageEmitter) {
        this.state = state;
        this.processParams = processParams;
        this.imageEmitter = imageEmitter;
    }

    public void maybePerformJaggedEdgesCorrection(ImageWrapper32 image, Ellipse ellipse) {
        if (!processParams.enhancementParams().jaggingCorrectionParams().enabled()) {
            return;
        }
        Optional<PhenomenaDetector.BorderDetection> bordersResult = state.findResult(WorkflowResults.BORDERS);
        bordersResult.ifPresent(borders -> {
            var sigma = processParams.enhancementParams().jaggingCorrectionParams().sigma();
            var totalLines = image.height();
            var limit = 2 * (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 100;
            var boundingBox = ellipse.boundingBox();
            var ey1 = Math.max(0, boundingBox.c());
            var ey2 = Math.min(boundingBox.d(), totalLines);
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
                    var detectedX1 = borders.left()[line];
                    var detectedX2 = borders.right()[line];
                    if (detectedX1 >= 0 && detectedX2 >= 0) {
                        var c = new Correction(line, detectedX1, detectedX2, left, right);
                        if (c.isValid(limit)) {
                            measurements.add(c);
                        }
                    }
                }
            }
            if (!measurements.isEmpty()) {
                // compute average and stddev
                var avgDx1 = measurements.stream().mapToDouble(Correction::dx1).average().orElse(0);
                var avgDx2 = measurements.stream().mapToDouble(Correction::dx2).average().orElse(0);
                var stddevDx1 = Math.sqrt(measurements.stream().mapToDouble(c -> Math.pow(c.dx1() - avgDx1, 2)).sum() / measurements.size());
                var stddevDx2 = Math.sqrt(measurements.stream().mapToDouble(c -> Math.pow(c.dx2() - avgDx2, 2)).sum() / measurements.size());
                var filtered = measurements.stream()
                        .filter(p ->
                                Math.abs(p.dx1() - avgDx1) < sigma * stddevDx1 && Math.abs(p.dx2() - avgDx2) < sigma * stddevDx2
                        )
                        .collect(Collectors.toMap(Correction::y, c -> c));
                // correct all images
                var data = image.data();
                float[][] debugR;
                float[][] debugG;
                float[][] debugB;
                boolean debug = false;
                if (processParams.requestedImages().isEnabled(GeneratedImageKind.DEBUG)) {
                    debug = true;
                    // we're going to generate a color image which shows the jagging correction
                    // by coloring the pixels that are corrected, more blue with more negative correction
                    // and more red with more positive correction
                    debugR = new float[data.length][data[0].length];
                    debugG = new float[data.length][data[0].length];
                    debugB = new float[data.length][data[0].length];
                } else {
                    debugB = null;
                    debugG = null;
                    debugR = null;
                }
                for (int y = 0; y < data.length; y++) {
                    var line = data[y];
                    var mid = filtered.get(y);
                    float[] debugLineR = debug ? debugR[y] : null;
                    float[] debugLineG = debug ? debugG[y] : null;
                    float[] debugLuneB = debug ? debugB[y] : null;
                    if (mid != null) {
                        performCorrectionLine(line, List.of(mid), debug, debugLineR, debugLineG, debugLuneB);
                    } else {
                        // interpolate between the two closest points
                        int finalY = y;
                        var left = filtered.entrySet().stream()
                                .filter(e -> e.getKey() < finalY)
                                .max(Map.Entry.comparingByKey());
                        var right = filtered.entrySet().stream()
                                .filter(e -> e.getKey() > finalY)
                                .min(Map.Entry.comparingByKey());
                        if (left.isPresent() || right.isPresent()) {
                            var leftCorrection = left.map(Map.Entry::getValue).orElse(null);
                            var rightCorrection = right.map(Map.Entry::getValue).orElse(null);
                            if (leftCorrection != null && rightCorrection != null) {
                                performCorrectionLine(line, List.of(leftCorrection, rightCorrection), debug, debugLineR, debugLineG, debugLuneB);
                            } else if (leftCorrection != null) {
                                performCorrectionLine(line, List.of(leftCorrection), debug, debugLineR, debugLineG, debugLuneB);
                            } else {
                                performCorrectionLine(line, List.of(rightCorrection), debug, debugLineR, debugLineG, debugLuneB);
                            }
                        }
                    }
                }
                if (debug) {
                    imageEmitter.newColorImage(
                            GeneratedImageKind.DEBUG,
                            null,
                            "Jagging correction",
                            "jagging-correction",
                            "Jagging correction",
                            image.width(),
                            image.height(),
                            image.metadata(),
                            () -> new float[][][]{debugR, debugG, debugB}
                    );
                }
            }
        });
    }

    private static float lanczos(float[] v, double x, int a) {
        int center = (int) Math.round(x);
        double sum = 0;
        double wsum = 0;
        for (int i = center - a + 1; i <= center + a; i++) {
            int idx = Math.min(Math.max(i, 0), v.length - 1);
            double d = x - i;
            double w = (d == 0) ? 1
                    : (a * Math.sin(Math.PI * d) * Math.sin(Math.PI * d / a))
                    / (Math.PI * Math.PI * d * d);
            sum += v[idx] * w;
            wsum += w;
        }
        return (float) (sum / wsum);
    }

    private void performCorrectionLine(float[] line, List<Correction> samples, boolean debug, float[] debugR, float[] debugG, float[] debugB) {
        var polynomials = new DoubleUnaryOperator[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            var sample = samples.get(i);
            var fun = LinearRegression.firstOrderRegression(
                    new Point2D[]{
                            new Point2D(sample.cx1(), sample.x1()),
                            new Point2D(sample.cx2(), sample.x2()),
                    }
            ).asPolynomial();
            polynomials[i] = fun;
        }
        var corrected = new float[line.length];

        for (int x = 0; x < line.length; x++) {
            // compute source coordinate
            double srcX = 0;
            for (var poly : polynomials) {
                srcX += poly.applyAsDouble(x);
            }
            srcX /= polynomials.length;

            float value = lanczos(line, srcX, 3);
            corrected[x] = value;

            if (debug) {
                var orig = line[x];
                var diff = value - orig;
                var abs = Math.abs(diff);
                debugR[x] = diff < 0 ? orig : abs;
                debugG[x] = 0;
                debugB[x] = diff > 0 ? orig : abs;
            }
        }

        System.arraycopy(corrected, 0, line, 0, line.length);
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
            return Math.abs(dx1()) <= limit && Math.abs(dx2()) <= limit;
        }

    }
}
