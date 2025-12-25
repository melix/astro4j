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
 * Phase correlation strategy that normalizes by magnitude.
 * This produces sharp, delta-like peaks and is robust to intensity variations.
 */
public final class PhaseCorrelationStrategy implements CorrelationStrategy {
    public static final PhaseCorrelationStrategy INSTANCE = new PhaseCorrelationStrategy();

    private PhaseCorrelationStrategy() {
    }

    @Override
    public float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles) {
        return CorrelationTools.getInstance().batchedCorrelation(refTiles, targetTiles, true);
    }

    @Override
    public CorrelationTools.ShiftResult correlate(float[][] refTile, float[][] targetTile) {
        return CorrelationTools.correlationShiftFFTWithConfidence(refTile, targetTile, true);
    }

    @Override
    public String name() {
        return "Phase Correlation";
    }
}
