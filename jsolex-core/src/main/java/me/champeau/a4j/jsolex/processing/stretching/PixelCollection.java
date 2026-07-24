/*
 * Copyright 2026 the original author or authors.
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

import java.util.function.BiPredicate;

final class PixelCollection {

    private PixelCollection() {
    }

    /**
     * Collects the pixel values selected by the mask, or all of them when the mask is null.
     */
    static float[] collect(float[][] data, int width, int height, BiPredicate<Integer, Integer> pixelMask) {
        if (pixelMask == null) {
            var pixels = new float[width * height];
            var index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[index++] = data[y][x];
                }
            }
            return pixels;
        }
        var count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (pixelMask.test(x, y)) {
                    count++;
                }
            }
        }
        var pixels = new float[count];
        var index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (pixelMask.test(x, y)) {
                    pixels[index++] = data[y][x];
                }
            }
        }
        return pixels;
    }
}
