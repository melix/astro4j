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
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

import java.util.ArrayList;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class DynamicStretchStrategy implements StretchingStrategy {
    private static final double EXPONENT = 1.1d;
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
        double a = findLinearGrowthLimit(image);
        double scaling = target / Math.pow(a, EXPONENT);
        // if a is low, it means that the histogram is very compressed on the left side
        // and there's a risk of saturating too many bright pixels, so we apply a simple
        // correction factor to reduce this risk. It seeems to work pretty well in
        // practice!
        var correction = Math.min(1, 0.5 + a / 65535d);
        var k = correction * MAX_PIXEL_VALUE / (a * (target - MAX_PIXEL_VALUE));
        var d = (a * (MAX_PIXEL_VALUE - target) * Math.log((MAX_PIXEL_VALUE - target) / target)) / MAX_PIXEL_VALUE;

        var data = image.data();
        for (int i = 0; i < data.length; i++) {
            var v = data[i];
            if (v < a) {
                data[i] = (float) (scaling * Math.pow(v, EXPONENT));
            } else {
                data[i] = (float) (MAX_PIXEL_VALUE / (1 + Math.exp(k * (v - (a + d)))));
            }
        }
    }

    private int findLinearGrowthLimit(ImageWrapper32 image) {
        var histogram = Histogram.of(image.data(), 256);
        var values = histogram.values();
        var observations = new ArrayList<WeightedObservedPoint>(2 * FIT_RANGE + 1);
        for (int i = Math.max(0, source - FIT_RANGE); i < Math.min(255, source + FIT_RANGE); i++) {
            observations.add(new WeightedObservedPoint(1.0, i, values[i]));
        }
        var fit = GaussianCurveFitter.create().fit(observations);
        var mean = fit[1];
        var stdDev = fit[2];
        int zeroCrossingIndex = (int) (mean + 3 * stdDev);
        // Use max in case the estimate is completely off
        return Math.min(255, Math.max(source, zeroCrossingIndex)) * 256;
    }

}
