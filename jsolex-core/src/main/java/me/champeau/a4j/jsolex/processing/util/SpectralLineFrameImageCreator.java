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
    private final SpectrumFrameAnalyzer analyzer;
    private final float[] original;
    private final int width;
    private final int height;

    public SpectralLineFrameImageCreator(SpectrumFrameAnalyzer analyzer, float[] original, int width, int height) {
        this.analyzer = analyzer;
        this.original = original;
        this.width = width;
        this.height = height;
    }

    public RGBImage generateDebugImage() {
        return generateDebugImage(null);
    }

    public RGBImage generateDebugImage(DoubleUnaryOperator forcedPolynomial) {
        int size = width * height;
        var lastResult = analyzer.result();
        Optional<DoubleUnaryOperator> polynomial = Optional.ofNullable(forcedPolynomial).or(lastResult::distortionPolynomial);
        float[] corrected;
        if (polynomial.isPresent()) {
            var distorsionCorrection = new DistortionCorrection(original, width, height);
            corrected = distorsionCorrection.polynomialCorrection(polynomial.get());
        } else {
            corrected = new float[size];
        }
        var samples = lastResult.getSamplePoints();
        // We create RGB images for debugging, which contain the original image at top
        // and the corrected one at the bottom
        int spacing = 10 * width;
        int offset = size + spacing;
        float[] rr = new float[2 * size + spacing];
        float[] gg = new float[2 * size + spacing];
        float[] bb = new float[2 * size + spacing];
        System.arraycopy(original, 0, rr, 0, size);
        System.arraycopy(original, 0, gg, 0, size);
        System.arraycopy(original, 0, bb, 0, size);
        System.arraycopy(corrected, 0, rr, offset, size);
        System.arraycopy(corrected, 0, gg, offset, size);
        System.arraycopy(corrected, 0, bb, offset, size);
        for (int x = 0; x < width; x++) {
            rr[x + size + 5 * width] = MAX_PIXEL_VALUE;
            gg[x + size + 5 * width] = MAX_PIXEL_VALUE / 2;
            bb[x + size + 5 * width] = MAX_PIXEL_VALUE;
        }
        lastResult.leftBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[offset + bx + y * width] = MAX_PIXEL_VALUE;
                gg[offset + bx + y * width] = 0;
                bb[offset + bx + y * width] = 0;
            }
        });
        lastResult.rightBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[offset + bx + y * width] = MAX_PIXEL_VALUE;
                gg[offset + bx + y * width] = 0;
                bb[offset + bx + y * width] = 0;
            }
        });
        polynomial.ifPresent(poly -> {
            // Draw a line on the top graph corresponding to the detected curvature
            for (int x = 0; x < width; x++) {
                int y = (int) Math.round(poly.applyAsDouble(x));
                int idx = x + y * width;
                if (idx < 0 || idx >= size) {
                    continue;
                }
                rr[idx] = MAX_PIXEL_VALUE;
                gg[idx] = 0;
                bb[idx] = 0;
            }
        });
        for (Point2D sample : samples) {
            var x = sample.x();
            var y = sample.y();
            if (x < 0 || x >= width || y < 0 || y >= height) {
                continue;
            }
            var idx = (int) (x + y * width);
            rr[idx] = 0;
            gg[idx] = MAX_PIXEL_VALUE;
            bb[idx] = 0;
        }
        analyzer.analyze(corrected);
        // Add green lines showing the detected spectrum line
        var distorsionCorrection = new DistortionCorrection(original, width, height);
        for (Point2D sample : samples) {
            int x = (int) sample.x();
            int y = polynomial.map(poly -> distorsionCorrection.correctY(poly, sample.x(), sample.y())).orElse(sample.y()).intValue();
            if (offset + x >= width || y >= height || x < 0 || y < 0) {
                continue;
            }
            var idx = offset + x + y * width;
            rr[idx] = 0;
            gg[idx] = MAX_PIXEL_VALUE;
            bb[idx] = 0;
        }
        return new RGBImage(width, 2 * height + 10, rr, gg, bb, MutableMap.of());
    }

    public RGBImage generateSpectrumImage(DoubleUnaryOperator forcedPolynomial,
                                          boolean stretch,
                                          Consumer<? super DebugImage> debugImageConsumer) {
        int size = width * height;
        Optional<DoubleUnaryOperator> polynomial = Optional.ofNullable(forcedPolynomial).or(() -> analyzer.result().distortionPolynomial());
        // We create RGB images for debugging, which contain the original image at top
        // and the corrected one at the bottom
        int spacing = 10 * width;
        int offset = size + spacing;
        float[] rr = new float[2 * size + spacing];
        float[] gg = new float[2 * size + spacing];
        float[] bb = new float[2 * size + spacing];
        var src = original;
        if (stretch) {
            src = new float[src.length];
            System.arraycopy(original, 0, src, 0, src.length);
            LinearStrechingStrategy.DEFAULT.stretch(new ImageWrapper32(width, height, src, Map.of()));
        }
        System.arraycopy(original, 0, rr, 0, size);
        System.arraycopy(original, 0, gg, 0, size);
        System.arraycopy(original, 0, bb, 0, size);
        polynomial.ifPresent(poly -> {
            // Draw a line on the top graph corresponding to the detected curvature
            for (int x = 0; x < width; x++) {
                int y = (int) Math.round(poly.applyAsDouble(x));
                int idx = x + y * width;
                if (idx < 0 || idx >= size) {
                    continue;
                }
                rr[idx] = MAX_PIXEL_VALUE;
                gg[idx] = 0;
                bb[idx] = 0;
            }
            debugImageConsumer.accept(new DebugImage(rr, gg, bb, offset, width, height, poly));
        });
        return new RGBImage(width, 2 * height + 10, rr, gg, bb, MutableMap.of());
    }

    public record DebugImage(
        float[] r,
        float[] g,
        float[] b,
        int offset,
        int width,
        int height,
        DoubleUnaryOperator polynomial) {

    }
}
