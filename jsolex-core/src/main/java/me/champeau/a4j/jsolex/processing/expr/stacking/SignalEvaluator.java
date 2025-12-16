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

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

/**
 * Evaluates tile signal levels using integral images for O(1) area sum queries.
 * This is a key optimization for distortion map computation, replacing O(tileSize²)
 * signal computation loops with O(1) integral image lookups.
 *
 * <p>An integral image (also known as a summed-area table) allows computing the sum
 * of any rectangular region in constant time. This class precomputes integral images
 * for reference and optionally target images, enabling fast signal threshold checks
 * during tile-based distortion estimation.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * var evaluator = new SignalEvaluator(referenceData, targetData, width, height);
 * if (evaluator.passesThreshold(x, y, tileSize, tileSize, signalThreshold)) {
 *     // Process tile at (x, y)
 * }
 * }</pre>
 */
public class SignalEvaluator {
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance();

    private final Image refIntegral;
    private final Image targetIntegral;

    /**
     * Creates a signal evaluator with precomputed integral images.
     *
     * @param referenceData the reference image pixel data (height × width)
     * @param targetData optional target image pixel data for symmetric signal checking (may be null)
     * @param width image width in pixels
     * @param height image height in pixels
     */
    public SignalEvaluator(float[][] referenceData, float[][] targetData, int width, int height) {
        this.refIntegral = IMAGE_MATH.integralImage(new Image(width, height, referenceData));
        this.targetIntegral = targetData != null
            ? IMAGE_MATH.integralImage(new Image(width, height, targetData))
            : null;
    }

    /**
     * Returns the average signal in a tile of the reference image.
     * Computation is O(1) using integral image lookup.
     *
     * @param x left coordinate of the tile
     * @param y top coordinate of the tile
     * @param tileWidth width of the tile
     * @param tileHeight height of the tile
     * @return average pixel value in the tile
     */
    public float getRefSignal(int x, int y, int tileWidth, int tileHeight) {
        return IMAGE_MATH.areaAverage(refIntegral, x, y, tileWidth, tileHeight);
    }

    /**
     * Returns the average signal in a tile of the target image.
     * Returns Float.MAX_VALUE if no target image was provided.
     *
     * @param x left coordinate of the tile
     * @param y top coordinate of the tile
     * @param tileWidth width of the tile
     * @param tileHeight height of the tile
     * @return average pixel value in the tile, or Float.MAX_VALUE if no target
     */
    public float getTargetSignal(int x, int y, int tileWidth, int tileHeight) {
        return targetIntegral != null
            ? IMAGE_MATH.areaAverage(targetIntegral, x, y, tileWidth, tileHeight)
            : Float.MAX_VALUE;
    }

    /**
     * Checks if a tile passes the signal threshold for both reference and target images.
     * This is the primary method for filtering low-signal regions during distortion computation.
     *
     * @param x left coordinate of the tile
     * @param y top coordinate of the tile
     * @param tileWidth width of the tile
     * @param tileHeight height of the tile
     * @param threshold minimum average signal required
     * @return true if both reference and target (if present) exceed the threshold
     */
    public boolean passesThreshold(int x, int y, int tileWidth, int tileHeight, float threshold) {
        return getRefSignal(x, y, tileWidth, tileHeight) > threshold
            && getTargetSignal(x, y, tileWidth, tileHeight) > threshold;
    }
}
