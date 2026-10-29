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

import me.champeau.a4j.math.RowStrips;

public class StretchingUtils {
    private StretchingUtils() {
    }

    /**
     * Returns the maximum of the pixels of an image and of an initial value,
     * with the semantics of {@link Math#max(float, float)}.
     *
     * @param data the pixels
     * @param initial the initial value
     * @return the maximum
     */
    static float max(float[][] data, float initial) {
        var partials = RowStrips.map(data.length, (yStart, yEnd) -> {
            var max = initial;
            for (int y = yStart; y < yEnd; y++) {
                for (var v : data[y]) {
                    max = Math.max(max, v);
                }
            }
            return max;
        });
        var max = initial;
        for (var partial : partials) {
            max = Math.max(max, partial);
        }
        return max;
    }


    public static double[] performSmoothing(int[] values) {
        double[] result = new double[values.length];
        double max = 0;
        result[0] = (2 * values[0] + values[1]) / 3d;
        for (int i = 1; i < values.length - 1; i++) {
            result[i] = (values[i - 1] + 2 * values[i] + values[i + 1]) / 4d;
            max = Math.max(max, result[i]);
        }
        result[values.length - 1] = (2 * values[values.length - 1] + values[values.length - 2]) / 3d;
        return result;
    }
}
