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
package me.champeau.a4j.jsolex.processing.expr.stacking;

/**
 * A mask describing which tiles in the output distortion grid have already converged
 * (frozen) and should be skipped during sampling. Used by the adaptive consensus
 * dedistortion loop to avoid recomputing phase correlation in regions that no longer
 * benefit from further refinement.
 *
 * @param frozen   per-tile frozen flags, indexed as {@code [ty][tx]} on a grid of
 *                 spacing {@code gridStep}
 * @param gridStep grid spacing in pixels
 */
public record ActiveRegionMask(boolean[][] frozen, int gridStep) {

    public boolean isFrozenAt(int px, int py) {
        if (frozen.length == 0) {
            return false;
        }
        var ty = py / gridStep;
        var tx = px / gridStep;
        if (ty < 0 || tx < 0 || ty >= frozen.length || tx >= frozen[0].length) {
            return false;
        }
        return frozen[ty][tx];
    }

    public SamplingStrategy.SamplePositions filter(SamplingStrategy.SamplePositions positions) {
        var srcCount = positions.count();
        var srcX = positions.x();
        var srcY = positions.y();
        var srcSizes = positions.tileSize();

        var keptX = new int[srcCount];
        var keptY = new int[srcCount];
        var keptSizes = new int[srcCount];
        var kept = 0;
        for (var i = 0; i < srcCount; i++) {
            if (!isFrozenAt(srcX[i], srcY[i])) {
                keptX[kept] = srcX[i];
                keptY[kept] = srcY[i];
                keptSizes[kept] = srcSizes[i];
                kept++;
            }
        }
        if (kept == srcCount) {
            return positions;
        }
        var trimmedX = new int[kept];
        var trimmedY = new int[kept];
        var trimmedSizes = new int[kept];
        System.arraycopy(keptX, 0, trimmedX, 0, kept);
        System.arraycopy(keptY, 0, trimmedY, 0, kept);
        System.arraycopy(keptSizes, 0, trimmedSizes, 0, kept);
        return new SamplingStrategy.SamplePositions(trimmedX, trimmedY, trimmedSizes, kept);
    }

    public double frozenFraction() {
        if (frozen.length == 0) {
            return 0.0;
        }
        var total = 0;
        var frozenCount = 0;
        for (var row : frozen) {
            for (var f : row) {
                total++;
                if (f) {
                    frozenCount++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) frozenCount / total;
    }
}
