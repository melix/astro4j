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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.DistortionCorrection;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.math.Point2D;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public class SpectralLineFrameImageCreator {
    public static final int SPACING = 10;
    private final SpectrumFrameAnalyzer analyzer;
    private final float[][] original;
    private final int width;
    private final int height;

    public SpectralLineFrameImageCreator(SpectrumFrameAnalyzer analyzer, float[][] original, int width, int height) {
        this.analyzer = analyzer;
        this.original = original;
        this.width = width;
        this.height = height;
    }

    public RGBImage generateDebugImage() {
        return generateDebugImage(null);
    }

    public RGBImage generateDebugImage(DoubleUnaryOperator forcedPolynomial) {
        var lastResult = analyzer.result();
        Optional<DoubleUnaryOperator> polynomial = Optional.ofNullable(forcedPolynomial).or(lastResult::distortionPolynomial);
        float[][] corrected;
        if (polynomial.isPresent()) {
            var distorsionCorrection = new DistortionCorrection(original, width, height);
            corrected = distorsionCorrection.polynomialCorrection(polynomial.get());
        } else {
            corrected = new float[height][width];
        }
        var samples = lastResult.getSamplePoints();
        // We create RGB images for debugging, which contain the original image at top
        // and the corrected one at the bottom
        int spacing = SPACING;
        float[][] rr = new float[2 * height + spacing][width];
        float[][] gg = new float[2 * height + spacing][width];
        float[][] bb = new float[2 * height + spacing][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(original[y], 0, rr[y], 0, width);
            System.arraycopy(original[y], 0, gg[y], 0, width);
            System.arraycopy(original[y], 0, bb[y], 0, width);
            System.arraycopy(corrected[y], 0, rr[y + spacing + height], 0, width);
            System.arraycopy(corrected[y], 0, gg[y + spacing + height], 0, width);
            System.arraycopy(corrected[y], 0, bb[y + spacing + height], 0, width);
        }
        for (int x = 0; x < width; x++) {
            rr[height + spacing / 2][x] = MAX_PIXEL_VALUE;
            gg[height + spacing / 2][x] = MAX_PIXEL_VALUE / 2;
            bb[height + spacing / 2][x] = MAX_PIXEL_VALUE;
        }
        lastResult.leftBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[y + spacing + height][bx] = MAX_PIXEL_VALUE;
                gg[y + spacing + height][bx] = 0;
                bb[y + spacing + height][bx] = 0;
            }
        });
        lastResult.rightBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[y + spacing + height][bx] = MAX_PIXEL_VALUE;
                gg[y + spacing + height][bx] = 0;
                bb[y + spacing + height][bx] = 0;
            }
        });
        polynomial.ifPresent(poly -> {
            int size = height * width;
            // Draw a line on the top graph corresponding to the detected curvature
            for (int x = 0; x < width; x++) {
                int y = (int) Math.round(poly.applyAsDouble(x));
                int idx = x + y * width;
                if (idx < 0 || idx >= size) {
                    continue;
                }
                rr[y][x] = MAX_PIXEL_VALUE;
                gg[y][x] = 0;
                bb[y][x] = 0;
            }
        });
        for (Point2D sample : samples) {
            var x = sample.x();
            var y = sample.y();
            if (x < 0 || x >= width || y < 0 || y >= height) {
                continue;
            }
            rr[(int) y][(int) x] = 0;
            gg[(int) y][(int) x] = MAX_PIXEL_VALUE;
            bb[(int) y][(int) x] = 0;
        }
        analyzer.analyze(corrected);
        // Add green lines showing the detected spectrum line
        var distorsionCorrection = new DistortionCorrection(original, width, height);
        for (Point2D sample : samples) {
            int x = (int) sample.x();
            int y = polynomial.map(poly -> distorsionCorrection.correctY(poly, sample.x(), sample.y())).orElse(sample.y()).intValue();
            if (spacing + x >= width || y >= height || x < 0 || y < 0) {
                continue;
            }
            rr[y + height + spacing][x] = 0;
            gg[y + height + spacing][x] = MAX_PIXEL_VALUE;
            bb[y + height + spacing][x] = 0;
        }
        return new RGBImage(width, 2 * height + spacing, rr, gg, bb, MutableMap.of());
    }

    public RGBImage generateSpectrumImage(DoubleUnaryOperator forcedPolynomial,
                                          boolean stretch,
                                          Consumer<? super DebugImage> debugImageConsumer) {
        Optional<DoubleUnaryOperator> polynomial = Optional.ofNullable(forcedPolynomial).or(() -> analyzer.result().distortionPolynomial());
        // We create RGB images for debugging, which contain the original image at top
        // and the corrected one at the bottom
        int spacing = SPACING;
        float[][] rr = new float[2 * height + spacing][width];
        float[][] gg = new float[2 * height + spacing][width];
        float[][] bb = new float[2 * height + spacing][width];
        var src = original;
        if (stretch) {
            src = ImageWrapper.copyData(original);
            LinearStrechingStrategy.DEFAULT.stretch(new ImageWrapper32(width, height, src, Map.of()));
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(original[y], 0, rr[y], 0, width);
            System.arraycopy(original[y], 0, gg[y], 0, width);
            System.arraycopy(original[y], 0, bb[y], 0, width);
        }
        polynomial.ifPresent(poly -> {
            // Draw a line on the top graph corresponding to the detected curvature
            int size = width * height;
            for (int x = 0; x < width; x++) {
                int y = (int) Math.round(poly.applyAsDouble(x));
                int idx = x + y * width;
                if (idx < 0 || idx >= size) {
                    continue;
                }
                rr[y][x] = MAX_PIXEL_VALUE;
                gg[y][x] = 0;
                bb[y][x] = 0;
            }
            debugImageConsumer.accept(new DebugImage(rr, gg, bb, width, height, spacing, poly));
        });
        return new RGBImage(width, 2 * height + spacing, rr, gg, bb, MutableMap.of());
    }

    public record DebugImage(
        float[][] r,
        float[][] g,
        float[][] b,
        int width,
        int height,
        int spacing,
        DoubleUnaryOperator polynomial) {

    }
}
