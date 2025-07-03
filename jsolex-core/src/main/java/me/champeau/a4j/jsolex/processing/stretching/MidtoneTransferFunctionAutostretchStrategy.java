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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import static java.util.Arrays.*;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * This strategy implements the midtone transfer function (MTF) autostretching algorithm.
 * It calculates the shadows, midtones, and highlights parameters based on the image data,
 * and applies the MTF transformation to stretch the image. This is inspired by the SIRIL
 * autostretching algorithm.
 * See <a href="https://siril.readthedocs.io/en/stable/Commands.html#autostretch">AUTOSTRETCH</a>
 */
public final class MidtoneTransferFunctionAutostretchStrategy implements StretchingStrategy {

    public static final double DEFAULT_SHADOWS_CLIP = -2.8;
    public static final double DEFAULT_TARGET_BG = 0.25;
    private static final double EPSILON = 0.00001;

    private final double shadowsClip;
    private final double targetBg;

    public MidtoneTransferFunctionAutostretchStrategy() {
        this(DEFAULT_SHADOWS_CLIP, DEFAULT_TARGET_BG);
    }

    public MidtoneTransferFunctionAutostretchStrategy(double shadowsClip, double targetBg) {
        if (targetBg < 0 || targetBg > 1) {
            throw new IllegalArgumentException("Target background must be in range [0, 1]");
        }
        this.shadowsClip = shadowsClip;
        this.targetBg = targetBg;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();

        if (width == 0 || height == 0) {
            return;
        }

        var params = calculateMTFParams(data, width, height);

        var mtfStrategy = new MidtoneTransferFunctionStrategy(clampTo8Bit(params.shadows), params.midtones, clampTo8Bit(params.highlights));
        mtfStrategy.stretch(image);
    }

    private static double clampTo8Bit(double value) {
        return Math.clamp(value / 256, 0, 255);
    }

    private MTFParams calculateMTFParams(float[][] data, int width, int height) {
        var totalPixels = width * height;
        var pixels = new float[totalPixels];
        var index = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[index++] = data[y][x];
            }
        }

        sort(pixels);

        // Calculate median
        var median = 0.0;
        if (totalPixels % 2 == 0) {
            median = (pixels[totalPixels / 2 - 1] + pixels[totalPixels / 2]) / 2.0;
        } else {
            median = pixels[totalPixels / 2];
        }

        // Calculate MAD (Median Absolute Deviation)
        var deviations = new float[totalPixels];
        for (var i = 0; i < totalPixels; i++) {
            deviations[i] = Math.abs(pixels[i] - (float) median);
        }
        sort(deviations);

        var mad = 0.0;
        if (totalPixels % 2 == 0) {
            mad = (deviations[totalPixels / 2 - 1] + deviations[totalPixels / 2]) / 2.0;
        } else {
            mad = deviations[totalPixels / 2];
        }

        var normalizedMedian = median / MAX_PIXEL_VALUE;
        var normalizedMad = mad / MAX_PIXEL_VALUE * 1.4826; // MAD normalization constant

        if (normalizedMad == 0.0) {
            normalizedMad = EPSILON;
        }

        // Calculate shadow clipping point using sigma-based approach
        var c0 = normalizedMedian + shadowsClip * normalizedMad;
        if (c0 < 0.0) {
            c0 = 0.0;
        }

        var m2 = normalizedMedian - c0;

        // Calculate midtones parameter using MTF function
        var midtones = calculateMTF(m2, targetBg, 0.0, 1.0);

        // Convert back to pixel values
        return new MTFParams(
                c0 * MAX_PIXEL_VALUE,     // shadows
                midtones,                         // midtones (unitless)
                MAX_PIXEL_VALUE                   // highlights (full range)
        );
    }

    // MTF function: MTF(x, m, lo, hi)
    private static double calculateMTF(double x, double m, double lo, double hi) {
        if (x <= lo) {
            return 0.0;
        }
        if (x >= hi) {
            return 1.0;
        }

        var xp = (x - lo) / (hi - lo);
        return ((m - 1.0) * xp) / (((2.0 * m - 1.0) * xp) - m);
    }

    private record MTFParams(double shadows, double midtones, double highlights) {

    }

}