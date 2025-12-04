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
 * Applies a sigmoid (S-curve) transformation to enhance contrast.
 * The sigmoid function provides a smooth, gentle contrast enhancement
 * that avoids harsh clipping at the extremes unlike gamma correction.
 */
public final class SigmoidStretchingStrategy implements StretchingStrategy {
    private final double midpoint;
    private final double steepness;

    /**
     * Creates a sigmoid stretch strategy.
     *
     * @param midpoint the center of the sigmoid curve (0-1), default 0.5
     * @param steepness how sharp the transition is, higher values create more contrast (default ~5-10)
     */
    public SigmoidStretchingStrategy(double midpoint, double steepness) {
        if (midpoint < 0 || midpoint > 1) {
            throw new IllegalArgumentException("Midpoint must be in range [0, 1], found: " + midpoint);
        }
        if (steepness <= 0) {
            throw new IllegalArgumentException("Steepness must be greater than 0, found: " + steepness);
        }
        this.midpoint = midpoint;
        this.steepness = steepness;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();

        if (width == 0 || height == 0) {
            return;
        }

        double sigmoidAtZero = sigmoid(0);
        double sigmoidAtOne = sigmoid(1);
        double sigmoidRange = sigmoidAtOne - sigmoidAtZero;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double normalized = data[y][x] / MAX_PIXEL_VALUE;
                double transformed = (sigmoid(normalized) - sigmoidAtZero) / sigmoidRange;
                data[y][x] = (float) Math.clamp(transformed * MAX_PIXEL_VALUE, 0, MAX_PIXEL_VALUE);
            }
        }
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-steepness * (x - midpoint)));
    }
}
