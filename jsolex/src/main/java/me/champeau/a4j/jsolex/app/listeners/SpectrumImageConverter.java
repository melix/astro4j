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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.math.image.Image;

/**
 * Helper for converting spectrum images to display format.
 */
final class SpectrumImageConverter {

    private SpectrumImageConverter() {
        // utility class
    }

    /**
     * Converts a spectrum image to an RGB byte array for display.
     * Applies normalization and contrast stretching.
     *
     * @param spectrum the spectrum image to convert
     * @return RGB byte array suitable for display
     */
    static byte[] convertSpectrumImage(Image spectrum) {
        var width = spectrum.width();
        var height = spectrum.height();
        var spectrumBuffer = new byte[3 * width * height];

        // Single pass: find max and store normalized values
        var max = 0;
        var normalizedValues = new double[width * height];
        var data = spectrum.data();

        for (var yy = 0; yy < height; yy++) {
            var row = data[yy];
            for (var xx = 0; xx < width; xx++) {
                var v = 255.0 * row[xx] / Constants.MAX_PIXEL_VALUE;
                normalizedValues[yy * width + xx] = v;
                var intV = (int) v;
                if (intV > max) {
                    max = intV;
                }
            }
        }

        // Second pass: apply stretching and convert to RGB bytes
        var maxInverse = max > 0 ? 255.0 / max : 0.0;
        for (var i = 0; i < normalizedValues.length; i++) {
            var s = (byte) (normalizedValues[i] * maxInverse);
            var offset = 3 * i;
            spectrumBuffer[offset] = s;
            spectrumBuffer[offset + 1] = s;
            spectrumBuffer[offset + 2] = s;
        }

        return spectrumBuffer;
    }

    /**
     * Stretches pixel values to fill the full 0-255 range.
     *
     * @param pixels the ARGB pixel array to stretch
     * @param width image width
     * @param height image height
     * @return stretched pixel array
     */
    static int[] stretchPixels(int[] pixels, int width, int height) {
        // find max value
        var max = 0;
        for (var pixel : pixels) {
            var v = pixel & 0xFF;
            if (v > max) {
                max = v;
            }
        }

        if (max > 0) {
            var result = new int[pixels.length];
            for (var i = 0; i < pixels.length; i++) {
                var v = (255 * (pixels[i] & 0xFF)) / max;
                result[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
            return result;
        }
        return pixels;
    }
}
