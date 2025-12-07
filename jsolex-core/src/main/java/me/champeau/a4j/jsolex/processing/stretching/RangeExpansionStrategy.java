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

// Not worth running on GPU: memory transfer overhead dominates the simple scaling operation
public final class RangeExpansionStrategy implements StretchingStrategy {
    private static final int MAX_VALUE = 65535;

    public static final RangeExpansionStrategy DEFAULT = new RangeExpansionStrategy();

    private RangeExpansionStrategy() {

    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        double max = -Double.MAX_VALUE;
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                if (v > max) {
                    max = v;
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) ((MAX_VALUE / max) * data[y][x]);
            }
        }
    }

}
