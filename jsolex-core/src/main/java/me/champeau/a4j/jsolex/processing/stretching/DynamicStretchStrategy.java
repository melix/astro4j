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

import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

import java.util.ArrayList;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class DynamicStretchStrategy implements StretchingStrategy {
    private static final int FIT_RANGE = 16;

    private final double target;
    private final int source;

    public DynamicStretchStrategy(float source, double targetPosition) {
        this.source = Math.min((int) source >> 8, 255);
        if (targetPosition < 0 || targetPosition > 1) {
            throw new IllegalArgumentException("Cutoff must be between 0 and 1");
        }
        this.target = targetPosition * MAX_PIXEL_VALUE;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        double a = 0.8*findLinearGrowthLimit(image);
        double linearSlope = target / a;
        double compressedSlope = (MAX_PIXEL_VALUE - target) / (MAX_PIXEL_VALUE - a);
        var data = image.data();
        var observations = new ArrayList<WeightedObservedPoint>(65536);
        for (int i = 0; i < 65536; i++) {
            observations.add(new WeightedObservedPoint(1.0, i, i < a ? linearSlope * i : target + compressedSlope * (i - a)));
        }
        var fit = PolynomialCurveFitter.create(4).fit(observations);
        var poly = new PolynomialFunction(fit);
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                data[y][x] = (float) Math.clamp(poly.value(v), 0, MAX_PIXEL_VALUE);
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

    private int findLinearGrowthLimit(ImageWrapper32 image) {
        var histogram = Histogram.of(image.data(), 256);
        var values = histogram.values();
        var observations = new ArrayList<WeightedObservedPoint>(2 * FIT_RANGE + 1);
        for (int i = Math.max(0, source - FIT_RANGE); i < Math.min(255, source + FIT_RANGE); i++) {
            observations.add(new WeightedObservedPoint(1.0, i, values[i]));
        }
        try {
            return gaussianEstimate(observations);
        } catch (Exception e) {
            // in case fitting fails, perform a 2d iteration by using values from the histogram, without
            // considering the target value, and removing the first bucket
            observations = new ArrayList<>();
            for (int i = 1; i < 256; i++) {
                observations.add(new WeightedObservedPoint(1.0, i, values[i]));
            }
            try {
                return gaussianEstimate(observations);
            } catch (Exception e2) {
                return (int) (Math.min(255, target / 256) * 256);
            }
        }
    }

    private int gaussianEstimate(ArrayList<WeightedObservedPoint> observations) {
        var fit = GaussianCurveFitter.create()
            .withMaxIterations(1024)
            .fit(observations);
        var mean = fit[1];
        var stdDev = fit[2];
        int zeroCrossingIndex = (int) (mean + 3 * stdDev);
        // Use max in case the estimate is completely off
        return Math.min(255, Math.max(source, zeroCrossingIndex)) * 256;
    }

}
