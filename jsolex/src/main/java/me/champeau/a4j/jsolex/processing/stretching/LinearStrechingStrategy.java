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

import java.util.Optional;

public class LinearStrechingStrategy implements StretchingStrategy {
    private final float whitePoint;

    public LinearStrechingStrategy(float whitePoint) {
        this.whitePoint = whitePoint;
    }

    @Override
    public void stretch(float[] data) {
        double min = min(data).orElse(0d);
        double max = max(data).orElse(0d);
        double range = max - min;
        if (range == 0) {
            return;
        }
        for (int i = 0; i < data.length; i++) {
            float v = (float) (whitePoint/range * (data[i] - min));
            data[i] = v;
        }
    }

    private static Optional<Double> min(float[] array) {
        if (array.length == 0) {
            return Optional.empty();
        }
        double min = Double.MAX_VALUE;
        for (float v : array) {
            if (v < min) {
                min = v;
            }
        }
        return Optional.of(min);
    }

    private static Optional<Double> max(float[] array) {
        if (array.length == 0) {
            return Optional.empty();
        }
        double max = Double.MIN_VALUE;
        for (float v : array) {
            if (v > max) {
                max = v;
            }
        }
        return Optional.of(max);
    }
}
