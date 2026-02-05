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
package me.champeau.a4j.jsolex.processing.spectrum;

import java.util.List;
import java.util.OptionalDouble;

@FunctionalInterface
public interface DopplerLineCenterMeasurement {
    OptionalDouble measureLineCenter(List<SpectrumAnalyzer.DataPoint> profile);

    static DopplerLineCenterMeasurement voigtFit(double windowHalfWidthAngstroms) {
        return profile -> {
            var stats = SpectralLineAnalysis.computeStatisticsForDoppler(profile, windowHalfWidthAngstroms);
            if (stats.voigtFit() == null) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(stats.voigtFit().center());
        };
    }

    static DopplerLineCenterMeasurement centerOfGravity(double windowHalfWidthAngstroms) {
        return profile -> {
            var center = SpectralLineAnalysis.computeCenterOfGravity(profile, windowHalfWidthAngstroms);
            if (Double.isNaN(center)) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(center);
        };
    }
}
