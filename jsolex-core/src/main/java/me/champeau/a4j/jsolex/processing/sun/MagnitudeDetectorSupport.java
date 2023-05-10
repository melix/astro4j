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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.tuples.FloatPair;
import me.champeau.a4j.math.tuples.IntPair;

public class MagnitudeDetectorSupport {
    private static final IntPair NOT_FOUND = new IntPair(-1, -1);

    private MagnitudeDetectorSupport() {

    }

    public static FloatPair minMax(float[] array) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (float v : array) {
            if (v > max) {
                max = v;
            }
            if (v < min) {
                min = v;
            }
        }
        return new FloatPair(min, max);
    }

    public static IntPair findEdges(float[] magnitudes, double sensitivity) {
        var minMax = minMax(magnitudes);
        double min = minMax.a();
        double max = minMax.b();
        double amplitude = max - min;
        if (amplitude == 0) {
            return NOT_FOUND;
        }
        double threshold = amplitude / sensitivity;
        int start = -1;
        int end = -1;
        for (int i = 0; i < magnitudes.length; i++) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold) {
                start = i;
                break;
            }
        }
        for (int i = magnitudes.length - 1; i >= 0; i--) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold) {
                end = i;
                break;
            }
        }
        return new IntPair(start, end);
    }
}
