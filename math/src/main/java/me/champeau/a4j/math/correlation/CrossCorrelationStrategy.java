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
 * Cross-correlation strategy that preserves amplitude information.
 * This may produce broader peaks but can be more robust for certain signal types.
 */
public final class CrossCorrelationStrategy implements CorrelationStrategy {
    public static final CrossCorrelationStrategy INSTANCE = new CrossCorrelationStrategy();

    private CrossCorrelationStrategy() {
    }

    @Override
    public float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles) {
        return CorrelationTools.getInstance().batchedCorrelation(refTiles, targetTiles, false);
    }

    @Override
    public CorrelationTools.ShiftResult correlate(float[][] refTile, float[][] targetTile) {
        return CorrelationTools.correlationShiftFFTWithConfidence(refTile, targetTile, false);
    }

    @Override
    public String name() {
        return "Cross-Correlation";
    }
}
