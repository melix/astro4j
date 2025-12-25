/*
 * Copyright 2025-2025 the original author or authors.
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
package me.champeau.a4j.math.correlation;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Adaptive correlation strategy that uses phase correlation first,
 * then falls back to cross-correlation for low-confidence tiles.
 * <p>
 * This provides the sharp peaks of phase correlation for most tiles,
 * while using cross-correlation's broader peaks for difficult cases.
 * <p>
 * The threshold for fallback is computed as a percentile of the confidence
 * values, making it adaptive to the actual data distribution.
 */
public final class AdaptiveCorrelationStrategy implements CorrelationStrategy {
    public static final AdaptiveCorrelationStrategy INSTANCE = new AdaptiveCorrelationStrategy();
    private static final double FALLBACK_PERCENTILE = 0.20;

    private AdaptiveCorrelationStrategy() {
    }

    @Override
    public float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles) {
        // 1. Run phase correlation on all tiles (GPU-batched)
        var results = PhaseCorrelationStrategy.INSTANCE.batchedCorrelation(refTiles, targetTiles);

        if (results.length == 0) {
            return results;
        }

        // 2. Compute the confidence threshold as the Nth percentile
        var confidences = new float[results.length];
        for (int i = 0; i < results.length; i++) {
            confidences[i] = results[i][2];
        }
        Arrays.sort(confidences);
        int percentileIdx = (int) (FALLBACK_PERCENTILE * results.length);
        float threshold = confidences[Math.min(percentileIdx, results.length - 1)];

        // 3. Collect indices of low-confidence tiles (below percentile threshold)
        var lowConfIndices = new ArrayList<Integer>();
        for (int i = 0; i < results.length; i++) {
            if (results[i][2] <= threshold) {
                lowConfIndices.add(i);
            }
        }

        if (lowConfIndices.isEmpty()) {
            return results;
        }

        // 4. Extract low-confidence tiles for cross-correlation
        var lowConfRef = new float[lowConfIndices.size()][][];
        var lowConfTarget = new float[lowConfIndices.size()][][];
        for (int i = 0; i < lowConfIndices.size(); i++) {
            int idx = lowConfIndices.get(i);
            lowConfRef[i] = refTiles[idx];
            lowConfTarget[i] = targetTiles[idx];
        }

        // 5. Run cross-correlation on low-confidence tiles (GPU-batched if enough)
        var crossResults = CrossCorrelationStrategy.INSTANCE.batchedCorrelation(lowConfRef, lowConfTarget);

        // 6. Replace results where cross-correlation improved confidence
        for (int i = 0; i < lowConfIndices.size(); i++) {
            int idx = lowConfIndices.get(i);
            if (crossResults[i][2] > results[idx][2]) {
                results[idx] = crossResults[i];
            }
        }

        return results;
    }

    @Override
    public CorrelationTools.ShiftResult correlate(float[][] refTile, float[][] targetTile) {
        // For single tiles, try both and return the one with higher confidence
        var phaseResult = PhaseCorrelationStrategy.INSTANCE.correlate(refTile, targetTile);
        var crossResult = CrossCorrelationStrategy.INSTANCE.correlate(refTile, targetTile);
        return crossResult.confidence() > phaseResult.confidence() ? crossResult : phaseResult;
    }

    @Override
    public String name() {
        return "Adaptive Correlation";
    }
}
