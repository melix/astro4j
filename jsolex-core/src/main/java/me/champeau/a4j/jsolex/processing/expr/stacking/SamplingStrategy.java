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

import java.util.Arrays;

/**
 * Strategy for selecting sample positions in the dedistortion algorithm.
 * This abstraction allows different sampling approaches:
 * <ul>
 *   <li>{@link GridSamplingStrategy} - regular grid sampling (traditional approach)</li>
 *   <li>{@link InterestPointSamplingStrategy} - gradient-based interest point sampling</li>
 * </ul>
 * Both produce the same output format (sample positions) that can be processed
 * uniformly by the dedistortion algorithm.
 */
public sealed interface SamplingStrategy permits GridSamplingStrategy, InterestPointSamplingStrategy {

    /**
     * Result of sample position selection.
     *
     * @param x array of x coordinates (pixel positions)
     * @param y array of y coordinates (pixel positions)
     * @param tileSize suggested tile size for each sample (may vary for multi-scale)
     * @param count number of valid samples
     */
    record SamplePositions(
            int[] x,
            int[] y,
            int[] tileSize,
            int count
    ) {
        /**
         * Creates positions with uniform tile size.
         */
        public static SamplePositions uniform(int[] x, int[] y, int tileSize, int count) {
            var sizes = new int[count];
            Arrays.fill(sizes, tileSize);
            return new SamplePositions(x, y, sizes, count);
        }
    }

    /**
     * Selects sample positions based on the reference image.
     *
     * @param referenceData the reference image data
     * @param width         image width
     * @param height        image height
     * @param tileSize      base tile size for correlation
     * @param signalThreshold minimum signal level for valid samples
     * @return the selected sample positions
     */
    SamplePositions selectPositions(float[][] referenceData,
                                     int width,
                                     int height,
                                     int tileSize,
                                     float signalThreshold);

    /**
     * Returns the recommended grid step for converting sparse results to a regular grid.
     * This determines the resolution of the final distortion map used for warping.
     *
     * @param tileSize base tile size
     * @param sampling sampling density factor
     * @return grid step in pixels
     */
    int getOutputGridStep(int tileSize, double sampling);

    /**
     * Returns a descriptive name for logging.
     *
     * @return strategy name
     */
    String getName();
}
