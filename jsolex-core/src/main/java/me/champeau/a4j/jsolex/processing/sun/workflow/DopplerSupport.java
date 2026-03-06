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

import me.champeau.a4j.jsolex.processing.expr.impl.DiskFill;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.matrix.DoubleMatrix;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class DopplerSupport {
    private final ProcessParams processParams;
    private final List<WorkflowState> states;
    private final ImageEmitter processedImagesEmitter;

    public DopplerSupport(ProcessParams processParams, List<WorkflowState> states, ImageEmitter processedImagesEmitter) {
        this.processParams = processParams;
        this.states = states;
        this.processedImagesEmitter = processedImagesEmitter;
    }

    public void produceDopplerImage() {
        if (Math.abs(SpectralRay.H_ALPHA.wavelength().angstroms() - processParams.spectrumParams().ray().wavelength().angstroms()) > 0.1) {
            return;
        }
        var dopplerShift = processParams.spectrumParams().dopplerShift();
        double lookupShift = processParams.spectrumParams().switchRedBlueChannels() ? -dopplerShift : dopplerShift;
        var first = states.stream().filter(s -> s.pixelShift() == lookupShift).findFirst();
        var second = states.stream().filter(s -> s.pixelShift() == -lookupShift).findFirst();
        first.ifPresent(s1 -> second.ifPresent(s2 -> {
            s1.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i1 -> s2.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i2 -> {
                var grey1 = (ImageWrapper32) ((GeometryCorrector.Result) i1).corrected().unwrapToMemory();
                var grey2 = (ImageWrapper32) ((GeometryCorrector.Result) i2).corrected().unwrapToMemory();
                var width = grey1.width();
                var height = grey1.height();
                var metadata = MetadataMerger.merge(List.of(grey1, grey2));
                processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER,
                        null, "Doppler",
                        "doppler",
                        message("doppler.description"),
                        width,
                        height,
                        metadata, () -> DopplerSupport.toDopplerImage(width, height, grey1, grey2));
                if (processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER_ROTATION_CORRECTED)) {
                    produceRotationCorrectedDopplerImage(grey1, grey2, width, height, metadata);
                }
                if (processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER_ECLIPSE)) {
                    produceDopplerEclipseImage(grey1, grey2, width, height);
                }
            }));
        }));
    }

    private void produceDopplerEclipseImage(ImageWrapper32 grey1, ImageWrapper32 grey2, int width, int height) {
        var grey1Eclipse = grey1.copy();
        var grey2Eclipse = grey2.copy();
        grey1Eclipse.findMetadata(Ellipse.class).ifPresent(eclipse1 -> {
            var g1 = BackgroundRemoval.neutralizeBackground(grey1Eclipse);
            grey2Eclipse.findMetadata(Ellipse.class).ifPresent(eclipse2 -> {
                var g2 = BackgroundRemoval.neutralizeBackground(grey2Eclipse);
                DiskFill.doFill(eclipse1, g1.data(), 0, null);
                DiskFill.doFill(eclipse2, g2.data(), 0, null);
                var stretch = new ArcsinhStretchingStrategy(0, 50, 50);
                stretch.stretch(g1);
                stretch.stretch(g2);
                var metadata = MetadataMerger.merge(List.of(grey1, grey2));
                processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER_ECLIPSE,
                        null, message("doppler.eclipse"),
                        "doppler-eclipse",
                        message("doppler.eclipse.description"),
                        width,
                        height,
                        metadata, () -> {
                            var dopplerImage = DopplerSupport.toDopplerImage(width, height, g1, g2);
                            RangeExpansionStrategy.DEFAULT.stretch(new RGBImage(width, height, dopplerImage[0], dopplerImage[1], dopplerImage[2], metadata));
                            return dopplerImage;
                        });
            });
        });
    }

    private void produceRotationCorrectedDopplerImage(ImageWrapper32 grey1, ImageWrapper32 grey2,
                                                         int width, int height,
                                                         Map<Class<?>, Object> metadata) {
        var corrected1 = grey1.copy();
        var corrected2 = grey2.copy();
        if (!cancelRotationGradient(width, height, corrected1, corrected2)) {
            return;
        }
        processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER_ROTATION_CORRECTED,
                null, message("doppler.rotation.corrected"),
                "doppler-rot-corrected",
                message("doppler.rotation.corrected.description"),
                width, height, metadata,
                () -> DopplerSupport.toDopplerImage(width, height, corrected1, corrected2));
    }

    private static final int POLY_DEGREE = 3;
    private static final int POLY_TERMS = (POLY_DEGREE + 1) * (POLY_DEGREE + 2) / 2;

    static boolean cancelRotationGradient(int width, int height, ImageWrapper32 grey1, ImageWrapper32 grey2) {
        var ellipseOpt = grey1.findMetadata(Ellipse.class);
        if (ellipseOpt.isEmpty()) {
            return false;
        }
        var ellipse = ellipseOpt.get();
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var semiAxis = ellipse.semiAxis();
        var radius = (semiAxis.a() + semiAxis.b()) / 2.0;
        if (radius <= 0) {
            return false;
        }
        var d1 = grey1.data();
        var d2 = grey2.data();

        // Fit a 2D polynomial of degree 3 to (d1 - d2) within the disk
        // using normal equations: β = (XᵀX)⁻¹ Xᵀy
        var xtx = new double[POLY_TERMS][POLY_TERMS];
        var xty = new double[POLY_TERMS];
        var fitRadius = 0.97 * radius;
        var fitRadius2 = fitRadius * fitRadius;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var px = x - cx;
                var py = y - cy;
                if (px * px + py * py >= fitRadius2) {
                    continue;
                }
                var dx = px / radius;
                var dy = py / radius;
                var diff = d1[y][x] - d2[y][x];
                var terms = polyTerms2D(dx, dy);
                for (int i = 0; i < POLY_TERMS; i++) {
                    xty[i] += terms[i] * diff;
                    for (int j = i; j < POLY_TERMS; j++) {
                        xtx[i][j] += terms[i] * terms[j];
                    }
                }
            }
        }
        for (int i = 1; i < POLY_TERMS; i++) {
            for (int j = 0; j < i; j++) {
                xtx[i][j] = xtx[j][i];
            }
        }

        double[] coeffs;
        try {
            var xtyMatrix = new double[POLY_TERMS][1];
            for (int i = 0; i < POLY_TERMS; i++) {
                xtyMatrix[i][0] = xty[i];
            }
            var result = DoubleMatrix.of(xtx).inverse().mul(DoubleMatrix.of(xtyMatrix)).asArray();
            coeffs = new double[POLY_TERMS];
            for (int i = 0; i < POLY_TERMS; i++) {
                coeffs[i] = result[i][0];
            }
        } catch (Exception e) {
            return false;
        }

        // Subtract half the fitted surface from each wing image
        var radius2 = radius * radius;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var px = x - cx;
                var py = y - cy;
                if (px * px + py * py >= radius2) {
                    continue;
                }
                var dx = px / radius;
                var dy = py / radius;
                var terms = polyTerms2D(dx, dy);
                double surface = 0;
                for (int i = 0; i < POLY_TERMS; i++) {
                    surface += coeffs[i] * terms[i];
                }
                var halfCorrection = (float) (surface / 2.0);
                d1[y][x] = Math.max(0, d1[y][x] - halfCorrection);
                d2[y][x] = Math.max(0, d2[y][x] + halfCorrection);
            }
        }
        return true;
    }

    private static double[] polyTerms2D(double dx, double dy) {
        var terms = new double[POLY_TERMS];
        int idx = 0;
        for (int d = 0; d <= POLY_DEGREE; d++) {
            for (int px = d; px >= 0; px--) {
                int py = d - px;
                terms[idx++] = Math.pow(dx, px) * Math.pow(dy, py);
            }
        }
        return terms;
    }

    static float[][][] toDopplerImage(int width, int height, ImageWrapper32 grey1, ImageWrapper32 grey2) {
        float[][] r = new float[height][width];
        float[][] g = new float[height][width];
        float[][] b = new float[height][width];
        var d1 = grey1.data();
        var d2 = grey2.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                r[y][x] = d1[y][x];
                g[y][x] = Math.min(d1[y][x], d2[y][x]);
                b[y][x] = d2[y][x];
            }
        }
        var rgb = new float[][][]{r, g, b};
        var hsl = ImageUtils.fromRGBtoHSL(rgb);
        var saturation = hsl[1];
        Arrays.stream(saturation).forEach(line -> {
            for (int j = 0; j < line.length; j++) {
                float v = line[j];
                var sat = Math.sqrt(v);
                line[j] = (float) sat;
            }
        });
        ImageUtils.fromHSLtoRGB(hsl, rgb);
        var metadata = MetadataMerger.merge(List.of(grey1, grey2));
        RangeExpansionStrategy.DEFAULT.stretch(new RGBImage(width, height, r, g, b, metadata));
        return rgb;
    }
}
