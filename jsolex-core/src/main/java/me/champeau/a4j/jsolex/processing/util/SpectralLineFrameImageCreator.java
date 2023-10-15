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

import me.champeau.a4j.jsolex.processing.sun.DistortionCorrection;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.math.Point2D;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        Optional<DoubleUnaryOperator> polynomial = Optional.ofNullable(forcedPolynomial).or(analyzer::findDistortionPolynomial);
        float[] corrected;
        if (polynomial.isPresent()) {
            var distorsionCorrection = new DistortionCorrection(original, width, height);
            corrected = distorsionCorrection.polynomialCorrection(polynomial.get());
        } else {
            corrected = new float[size];
        }
        List<Point2D> samples = analyzer.getSamplePoints();
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
        analyzer.leftSunBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[offset + bx + y * width] = MAX_PIXEL_VALUE;
                gg[offset + bx + y * width] = 0;
                bb[offset + bx + y * width] = 0;
            }
        });
        analyzer.rightSunBorder().ifPresent(bx -> {
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
            rr[(int) (x + y * width)] = 0;
            gg[(int) (x + y * width)] = MAX_PIXEL_VALUE;
            bb[(int) (x + y * width)] = 0;
        }
        analyzer.analyze(corrected);
        // Add green lines showing the detected spectrum line
        for (Point2D sample : samples) {
            var x = sample.x();
            var y = sample.y();
            rr[(int) (offset + x + y * width)] = 0;
            gg[(int) (offset + x + y * width)] = MAX_PIXEL_VALUE;
            bb[(int) (offset + x + y * width)] = 0;
        }
        return new RGBImage(width, 2 * height + 10, rr, gg, bb, Map.of());
    }
}
