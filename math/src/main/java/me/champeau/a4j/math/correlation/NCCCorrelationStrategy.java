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

/**
 * Normalized Cross-Correlation (NCC) strategy.
 * <p>
 * NCC normalizes by local variance, making it invariant to brightness and contrast changes.
 * The correlation values are in [-1, 1], where 1 indicates perfect match.
 * <p>
 * This is the same algorithm used by programs like AutoStakkert for image alignment.
 * The confidence value is the peak NCC value itself, which is directly interpretable
 * and comparable across different tiles (unlike phase correlation's PSR-based confidence).
 */
public final class NCCCorrelationStrategy implements CorrelationStrategy {
    public static final NCCCorrelationStrategy INSTANCE = new NCCCorrelationStrategy();

    private NCCCorrelationStrategy() {
    }

    @Override
    public float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles) {
        return CorrelationTools.getInstance().batchedNCC(refTiles, targetTiles);
    }

    @Override
    public CorrelationTools.ShiftResult correlate(float[][] refTile, float[][] targetTile) {
        return CorrelationTools.nccShiftWithConfidence(refTile, targetTile);
    }

    @Override
    public String name() {
        return "Normalized Cross-Correlation";
    }
}
