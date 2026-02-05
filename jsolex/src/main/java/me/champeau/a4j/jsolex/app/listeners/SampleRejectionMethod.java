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
package me.champeau.a4j.jsolex.app.listeners;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for rejecting outlier samples before aggregation.
 * Applied before NoiseReductionMethod aggregation.
 */
public sealed interface SampleRejectionMethod permits SampleRejectionMethod.None, SampleRejectionMethod.Sigma {

    FilteredData filter(List<Double> velocities, List<Double> weights);

    record FilteredData(List<Double> velocities, List<Double> weights) {}

    record None() implements SampleRejectionMethod {
        @Override
        public FilteredData filter(List<Double> velocities, List<Double> weights) {
            return new FilteredData(velocities, weights);
        }
    }

    record Sigma(double threshold) implements SampleRejectionMethod {
        @Override
        public FilteredData filter(List<Double> velocities, List<Double> weights) {
            if (velocities.size() < 3) {
                return new FilteredData(velocities, weights);
            }

            // Compute mean and standard deviation
            var mean = velocities.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            var variance = velocities.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0);
            var stdDev = Math.sqrt(variance);

            if (stdDev == 0) {
                return new FilteredData(velocities, weights);
            }

            // Filter out samples beyond sigma threshold
            var filteredVelocities = new ArrayList<Double>();
            var filteredWeights = new ArrayList<Double>();
            for (int i = 0; i < velocities.size(); i++) {
                var deviation = Math.abs(velocities.get(i) - mean);
                if (deviation <= threshold * stdDev) {
                    filteredVelocities.add(velocities.get(i));
                    if (weights != null && i < weights.size()) {
                        filteredWeights.add(weights.get(i));
                    }
                }
            }

            // If too many samples rejected, fall back to original data
            if (filteredVelocities.size() < 2) {
                return new FilteredData(velocities, weights);
            }

            return new FilteredData(filteredVelocities, filteredWeights.isEmpty() ? weights : filteredWeights);
        }
    }
}
