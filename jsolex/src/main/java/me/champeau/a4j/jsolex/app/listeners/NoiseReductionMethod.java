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

import java.util.List;

/**
 * Strategy for aggregating multiple velocity measurements at the same latitude.
 * Returns both the aggregated value and its associated error estimate.
 */
public enum NoiseReductionMethod {
    MEDIAN,
    AVERAGE,
    WEIGHTED_AVERAGE;

    public record AggregationResult(double value, double error) {}

    /**
     * Aggregates velocity measurements into a single representative value with error.
     * @param velocities list of velocity measurements
     * @param weights list of weights (only used for WEIGHTED_AVERAGE, can be null otherwise)
     * @return the aggregated velocity and its error estimate
     */
    AggregationResult aggregate(List<Double> velocities, List<Double> weights) {
        if (velocities.isEmpty()) {
            return new AggregationResult(0, 0);
        }
        return switch (this) {
            case MEDIAN -> computeMedianWithMAD(velocities);
            case AVERAGE -> computeAverageWithStdErr(velocities);
            case WEIGHTED_AVERAGE -> computeWeightedAverageWithError(velocities, weights);
        };
    }

    // MEDIAN: Use Median Absolute Deviation (MAD) as error estimate
    // MAD is robust to outliers, consistent with median's outlier resistance
    private static AggregationResult computeMedianWithMAD(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        var median = sorted.get(sorted.size() / 2);

        // MAD = median of |x_i - median|
        var deviations = values.stream()
                .map(v -> Math.abs(v - median))
                .sorted()
                .toList();
        var mad = deviations.get(deviations.size() / 2);

        return new AggregationResult(median, mad);
    }

    // AVERAGE: Use Standard Error of the Mean (SEM) as error estimate
    // SEM = stdDev / sqrt(n), represents uncertainty of the mean
    private static AggregationResult computeAverageWithStdErr(List<Double> values) {
        var mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        var variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0);
        var stdDev = Math.sqrt(variance);
        var stdErr = stdDev / Math.sqrt(values.size());

        return new AggregationResult(mean, stdErr);
    }

    // WEIGHTED_AVERAGE: Use weighted standard error
    // Error accounts for both spread and effective sample size from weights
    private static AggregationResult computeWeightedAverageWithError(List<Double> values, List<Double> weights) {
        if (weights == null || weights.size() != values.size()) {
            return computeAverageWithStdErr(values);
        }

        double weightedSum = 0;
        double totalWeight = 0;
        for (int i = 0; i < values.size(); i++) {
            weightedSum += values.get(i) * weights.get(i);
            totalWeight += weights.get(i);
        }
        if (totalWeight == 0) {
            return new AggregationResult(0, 0);
        }
        var weightedMean = weightedSum / totalWeight;

        // Weighted variance: sum(w_i * (x_i - mean)^2) / sum(w_i)
        double weightedVarianceSum = 0;
        double sumWeightsSquared = 0;
        for (int i = 0; i < values.size(); i++) {
            var diff = values.get(i) - weightedMean;
            weightedVarianceSum += weights.get(i) * diff * diff;
            sumWeightsSquared += weights.get(i) * weights.get(i);
        }

        // Effective sample size: (sum w)^2 / sum(w^2)
        var effectiveN = totalWeight * totalWeight / sumWeightsSquared;
        var weightedVariance = weightedVarianceSum / totalWeight;
        var weightedStdErr = Math.sqrt(weightedVariance / effectiveN);

        return new AggregationResult(weightedMean, weightedStdErr);
    }
}
