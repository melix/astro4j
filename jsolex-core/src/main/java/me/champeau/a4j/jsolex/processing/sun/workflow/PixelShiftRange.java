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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import java.util.function.DoubleUnaryOperator;

/**
 * Stores information about the maximum range of pixel shifts
 * which can be used given the detected polynomial and frame
 * dimensions. The shift is expressed in pixels relative to
 * the middle of the detected spectral line.
 *
 * @param minPixelShift the min pixel shift
 * @param maxPixelShift the max pixel shift
 * @param step the step to use when sampling in the range
 */
public record PixelShiftRange(
    double minPixelShift,
    double maxPixelShift,
    double step
) {
    public static PixelShiftRange computePixelShiftRange(int start, int end, int height, DoubleUnaryOperator polynomial) {
        // determine the min and max pixel shifts
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = start; x < end; x++) {
            var v = polynomial.applyAsDouble(x);
            min = Math.min(v, min);
            max = Math.max(v, max);
        }
        if (min == Double.MAX_VALUE) {
            min = 0;
        }
        if (max == -Double.MAX_VALUE) {
            max = height;
        }
        min = Math.max(0, min);
        max = Math.min(height, max);
        double mid = (max + min) / 2.0;
        double range = (max - min) / 2.0;
        var maxPixelShift = -Double.MAX_VALUE;
        var minPixelShift = Double.MAX_VALUE;
        for (int y = (int) range; y < height - range; y++) {
            double cpt = 0;
            for (int x = start; x < end; x++) {
                var v = polynomial.applyAsDouble(x);
                var shift = v - mid;
                int ny = (int) Math.round(y + shift);
                if (ny >= 0 && ny < height) {
                    cpt++;
                }
            }
            if (cpt > 0) {
                var pixelShift = y - mid;
                minPixelShift = Math.min(minPixelShift, pixelShift);
                maxPixelShift = Math.max(maxPixelShift, pixelShift);
            }
        }
        // round to a 1/10th
        minPixelShift = Math.floor(minPixelShift / 10) * 10;
        maxPixelShift = Math.ceil(maxPixelShift / 10) * 10;
        return new PixelShiftRange(minPixelShift, maxPixelShift, (maxPixelShift - minPixelShift) / 10);
    }

    public boolean includes(double shift) {
        return shift >= minPixelShift && shift <= maxPixelShift;
    }
}
