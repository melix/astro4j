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

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * This strategy implements the midtone transfer function (MTF) stretching algorithm.
 * It applies a transformation based on shadows, midtones, and highlights to stretch the image.
 * The shadows and highlights are specified in 8-bit values (0-255), while midtones is a floating-point value.
 * Inspired from the SIRIL MTF algorithm, see <a href="https://siril.readthedocs.io/en/stable/processing/stretching.html#midtone-transfer-function-transformation-mtf">MTF</a>
 */
public final class MidtoneTransferFunctionStrategy implements StretchingStrategy {
    
    private final double shadows;
    private final double midtones;
    private final double highlights;
    
    public MidtoneTransferFunctionStrategy(double shadows8bit, double midtones, double highlights8bit) {
        if (shadows8bit < 0 || shadows8bit > 255) {
            throw new IllegalArgumentException("Shadows must be in range [0, 255], found: " + shadows8bit);
        }
        if (highlights8bit < 0 || highlights8bit > 255) {
            throw new IllegalArgumentException("Highlights must be in range [0, 255], found: " + highlights8bit);
        }
        if (shadows8bit >= highlights8bit) {
            throw new IllegalArgumentException("Shadows must be less than highlights. Found: shadows=" + shadows8bit + ", highlights=" + highlights8bit);
        }
        if (midtones <= 0) {
            throw new IllegalArgumentException("Midtones must be greater than 0. Found: " + midtones);
        }
        
        this.shadows = shadows8bit * 256;
        this.midtones = midtones;
        this.highlights = highlights8bit * 256;
    }
    
    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        
        if (width == 0 || height == 0) {
            return;
        }
        
        // Apply MTF transformation to all pixels
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var originalValue = data[y][x];
                var transformedValue = mtfTransform(originalValue, midtones, shadows, highlights);
                data[y][x] = (float) Math.clamp(transformedValue, 0, MAX_PIXEL_VALUE);
            }
        }
    }
    
    // MTF transformation: MTF(x, m, lo, hi)
    private double mtfTransform(double x, double m, double lo, double hi) {
        if (x <= lo) {
            return lo;
        }
        if (x >= hi) {
            return hi;
        }
        
        var xp = (x - lo) / (hi - lo);
        var result = ((m - 1.0) * xp) / (((2.0 * m - 1.0) * xp) - m);
        
        return lo + result * (hi - lo);
    }
}