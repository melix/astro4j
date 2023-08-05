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

import me.champeau.a4j.math.image.Image;

/**
 * Histogram of an image
 *
 * @param values the values of the histogram
 * @param pixelCount the total number of pixels
 * @param maxValue the maximum value in a bin
 */
public record Histogram(
        int[] values,
        int pixelCount,
        int maxValue
) {
    public static Histogram of(Image image, int bins) {
        int[] histogram = new int[bins + 1];
        int pixelCount = image.width() * image.height();
        int maxValue = 0;
        for (float d : image.data()) {
            int i = Math.round(d * bins / Constants.MAX_PIXEL_VALUE);
            maxValue = Math.max(i, maxValue);
            histogram[i]++;
        }
        return new Histogram(histogram, pixelCount, maxValue);
    }
}
