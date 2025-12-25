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
 * Strategy interface for computing correlation-based displacement between tiles.
 * Implementations provide different correlation algorithms (phase vs cross-correlation).
 */
public sealed interface CorrelationStrategy
        permits PhaseCorrelationStrategy, CrossCorrelationStrategy, NCCCorrelationStrategy, AdaptiveCorrelationStrategy {

    /**
     * Compute displacements for multiple tile pairs.
     * Uses GPU if available and tile count is large enough, otherwise falls back to CPU.
     *
     * @param refTiles    Reference tiles [N][tileSize][tileSize]
     * @param targetTiles Target tiles [N][tileSize][tileSize]
     * @return Displacements [N][3] containing (dx, dy, confidence) per tile
     */
    float[][] batchedCorrelation(float[][][] refTiles, float[][][] targetTiles);

    /**
     * Compute displacement for a single tile pair.
     *
     * @param refTile    Reference tile [tileSize][tileSize]
     * @param targetTile Target tile [tileSize][tileSize]
     * @return The displacement with confidence
     */
    CorrelationTools.ShiftResult correlate(float[][] refTile, float[][] targetTile);

    /**
     * Returns the name of this correlation strategy.
     *
     * @return the strategy name
     */
    String name();
}
