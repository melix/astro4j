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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Sampling strategy that selects positions using a layered multi-scale approach.
 * This strategy places tiles in multiple layers:
 * <ul>
 *   <li>Layer 1 (base): Large tiles on a regular grid for full coverage</li>
 *   <li>Layer 2+: Progressively smaller tiles in areas with high detail, overlapping larger tiles</li>
 * </ul>
 * This approach is inspired by AutoStakkert's multi-scale alignment points.
 */
public final class InterestPointSamplingStrategy implements SamplingStrategy {

    private static final double MIN_SAMPLE_SPACING_RATIO = 0.5;
    public static final float MAX_GRADIENT_THRESHOLD = 0.15f;

    private static final Logger LOGGER = LoggerFactory.getLogger(InterestPointSamplingStrategy.class);
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance();
    private static final int MIN_TILE_SIZE = 32;
    private static final int MAX_SAMPLES = 8192;

    private final boolean multiscale;

    /**
     * Creates an interest point sampling strategy.
     *
     * @param ignoredBaseTileSize ignored (kept for API compatibility)
     * @param multiscale          whether to use variable tile sizes based on gradient strength
     */
    public InterestPointSamplingStrategy(int ignoredBaseTileSize, boolean multiscale) {
        this.multiscale = multiscale;
    }

    @Override
    public SamplePositions selectPositions(float[][] referenceData,
                                            int width,
                                            int height,
                                            int tileSize,
                                            float signalThreshold) {
        var image = new Image(width, height, referenceData);
        var integralImg = IMAGE_MATH.integralImage(image);

        // Compute gradient magnitude (rotationally symmetric)
        var gradient = IMAGE_MATH.gradientLT(image);
        var gradientMag = gradient.magnitude();

        var allPoints = new ArrayList<SelectedPoint>();

        if (multiscale) {
            // Layered multi-scale approach (inspired by AutoStakkert)
            // All layers use interest-point selection, only tile sizes differ

            // Layer 1: Large tiles (tileSize*2) for coarse coverage
            var largeSize = tileSize * 2;
            addInterestPointLayer(allPoints, width, height, largeSize,
                    integralImg, gradientMag, signalThreshold,
                    MAX_GRADIENT_THRESHOLD, "coarse");

            // Layer 2: Main tiles (tileSize) - the primary density layer
            addInterestPointLayer(allPoints, width, height, tileSize,
                    integralImg, gradientMag, signalThreshold,
                    MAX_GRADIENT_THRESHOLD, "main");

            // Layer 3: Small tiles (tileSize/2) for high-detail areas
            var smallSize = Math.max(MIN_TILE_SIZE, tileSize / 2);
            if (smallSize < tileSize) {
                addInterestPointLayer(allPoints, width, height, smallSize,
                        integralImg, gradientMag, signalThreshold,
                        MAX_GRADIENT_THRESHOLD,
                        "detail");
            }
        } else {
            // Single scale: interest-point selection
            addInterestPointLayer(allPoints, width, height, tileSize,
                    integralImg, gradientMag, signalThreshold,
                    MAX_GRADIENT_THRESHOLD, "uniform");
        }

        // Keep only the best samples if we have too many
        if (allPoints.size() > MAX_SAMPLES) {
            allPoints.sort(Comparator.comparingDouble(SelectedPoint::gradient).reversed());
            allPoints.subList(MAX_SAMPLES, allPoints.size()).clear();
            LOGGER.debug("Trimmed to {} best samples", MAX_SAMPLES);
        }

        // Convert to arrays
        var count = allPoints.size();
        var xArr = new int[count];
        var yArr = new int[count];
        var tileSizes = new int[count];

        for (var i = 0; i < count; i++) {
            var pt = allPoints.get(i);
            xArr[i] = pt.x;
            yArr[i] = pt.y;
            tileSizes[i] = pt.tileSize;
        }

        if (LOGGER.isDebugEnabled()) {
            // Log distribution
            var tileSizeCounts = new TreeMap<Integer, Integer>();
            for (var i = 0; i < count; i++) {
                tileSizeCounts.merge(tileSizes[i], 1, Integer::sum);
            }
            LOGGER.debug("Layered selection: {} total points, distribution: {}", count, tileSizeCounts);
        }
        return new SamplePositions(xArr, yArr, tileSizes, count);
    }

    /**
     * Adds tiles at interest points (local maxima of gradient magnitude).
     * Uses pure gradient-based detection with NO grid structure.
     * Points are placed at actual local maxima of the gradient image.
     */
    private void addInterestPointLayer(List<SelectedPoint> points,
                                       int width, int height,
                                       int tileSize,
                                       Image integralImg,
                                       Image gradientMag,
                                       float signalThreshold,
                                       float scoreThresholdRatio,
                                       String layerName) {
        var halfTile = tileSize / 2;
        var minSpacing = (int) (tileSize * MIN_SAMPLE_SPACING_RATIO);

        // Get gradient magnitude data directly
        var gradientData = gradientMag.data();

        // First pass: find maximum gradient value for thresholding
        float maxGradient = 0;
        for (var y = halfTile; y < height - halfTile; y++) {
            var row = gradientData[y];
            for (var x = halfTile; x < width - halfTile; x++) {
                maxGradient = Math.max(maxGradient, row[x]);
            }
        }

        var gradientThreshold = maxGradient * scoreThresholdRatio;

        // Second pass: find all local maxima above threshold
        // A pixel is a local maximum if it's greater than all 8 neighbors
        var candidates = new PriorityQueue<PointCandidate>(
                Comparator.comparingDouble(p -> -p.score)
        );

        for (var y = halfTile; y < height - halfTile; y++) {
            var rowPrev = gradientData[y - 1];
            var rowCurr = gradientData[y];
            var rowNext = gradientData[y + 1];

            for (var x = halfTile; x < width - halfTile; x++) {
                var val = rowCurr[x];

                // Skip if below threshold
                if (val < gradientThreshold) {
                    continue;
                }

                // Check if local maximum (strictly greater than all 8 neighbors)
                if (val > rowPrev[x - 1] && val > rowPrev[x] && val > rowPrev[x + 1] &&
                    val > rowCurr[x - 1] &&                     val > rowCurr[x + 1] &&
                    val > rowNext[x - 1] && val > rowNext[x] && val > rowNext[x + 1]) {

                    // Also check signal threshold using integral image
                    var avgSignal = IMAGE_MATH.areaAverage(integralImg, x - halfTile, y - halfTile, tileSize, tileSize);
                    if (avgSignal >= signalThreshold) {
                        candidates.add(new PointCandidate(x, y, val));
                    }
                }
            }
        }

        LOGGER.debug("Layer '{}': found {} local maxima candidates above {}% threshold",
                layerName, candidates.size(), (int) (scoreThresholdRatio * 100));

        // Non-maximum suppression: select best candidates with minimum spacing
        // Also check against points from previous layers to avoid overlapping centers
        var selected = new ArrayList<SelectedPoint>();

        while (!candidates.isEmpty()) {
            var best = candidates.poll();

            // Check if too close to any already selected point (current layer)
            var tooClose = false;
            for (var existing : selected) {
                var dx = best.x - existing.x;
                var dy = best.y - existing.y;
                if (dx * dx + dy * dy < minSpacing * minSpacing) {
                    tooClose = true;
                    break;
                }
            }

            // Also check against points from previous layers
            if (!tooClose) {
                for (var existing : points) {
                    var dx = best.x - existing.x;
                    var dy = best.y - existing.y;
                    if (dx * dx + dy * dy < minSpacing * minSpacing) {
                        tooClose = true;
                        break;
                    }
                }
            }

            if (!tooClose) {
                selected.add(new SelectedPoint(best.x, best.y, tileSize, best.score));
            }
        }

        points.addAll(selected);

        LOGGER.debug("Layer '{}': added {} tiles of size {} after NMS (minSpacing={})",
                layerName, selected.size(), tileSize, minSpacing);
    }

    @Override
    public int getOutputGridStep(int tileSize, double sampling) {
        return tileSize;
    }

    @Override
    public String getName() {
        return "InterestPoint (multiscale=" + multiscale + ")";
    }

    /**
     * Creates a debug image showing the selected interest points and their tiles.
     */
    public static float[][] createDebugImage(float[][] referenceData,
                                              SamplePositions positions,
                                              int width,
                                              int height) {
        var result = new float[height][width];
        float maxVal = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                maxVal = Math.max(maxVal, referenceData[y][x]);
            }
        }
        var scale = maxVal > 0 ? 0.5f / maxVal : 1f;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                result[y][x] = referenceData[y][x] * scale;
            }
        }

        // Find min/max tile sizes for color mapping
        var minTileSize = Integer.MAX_VALUE;
        var maxTileSize = Integer.MIN_VALUE;
        for (var i = 0; i < positions.count(); i++) {
            var ts = positions.tileSize()[i];
            minTileSize = Math.min(minTileSize, ts);
            maxTileSize = Math.max(maxTileSize, ts);
        }

        // Draw rectangles for each tile
        for (var i = 0; i < positions.count(); i++) {
            var cx = positions.x()[i];
            var cy = positions.y()[i];
            var ts = positions.tileSize()[i];
            var halfTile = ts / 2;

            var tileX = cx - halfTile;
            var tileY = cy - halfTile;

            // Brightness based on tile size (smaller = brighter)
            float brightness;
            if (maxTileSize > minTileSize) {
                brightness = 1.0f - (float) (ts - minTileSize) / (maxTileSize - minTileSize);
            } else {
                brightness = 1.0f;
            }
            brightness = 0.6f + brightness * 0.4f;

            drawRect(result, tileX, tileY, ts, ts, brightness, width, height);
            drawCross(result, cx, cy, 3, 1.0f, width, height);
        }

        LOGGER.debug("Debug image created with {} tiles (sizes {} to {})",
                positions.count(), minTileSize, maxTileSize);

        return result;
    }

    private static void drawRect(float[][] img, int x, int y, int w, int h,
                                  float value, int imgWidth, int imgHeight) {
        for (var dx = 0; dx < w; dx++) {
            var px = x + dx;
            if (px >= 0 && px < imgWidth) {
                if (y >= 0 && y < imgHeight) {
                    img[y][px] = value;
                }
                var py = y + h - 1;
                if (py >= 0 && py < imgHeight) {
                    img[py][px] = value;
                }
            }
        }
        for (var dy = 0; dy < h; dy++) {
            var py = y + dy;
            if (py >= 0 && py < imgHeight) {
                if (x >= 0 && x < imgWidth) {
                    img[py][x] = value;
                }
                var px = x + w - 1;
                if (px >= 0 && px < imgWidth) {
                    img[py][px] = value;
                }
            }
        }
    }

    private static void drawCross(float[][] img, int cx, int cy, int size,
                                   float value, int imgWidth, int imgHeight) {
        for (var d = -size; d <= size; d++) {
            var px = cx + d;
            var py = cy + d;
            if (px >= 0 && px < imgWidth && cy >= 0 && cy < imgHeight) {
                img[cy][px] = value;
            }
            if (cx >= 0 && cx < imgWidth && py >= 0 && py < imgHeight) {
                img[py][cx] = value;
            }
        }
    }

    private record PointCandidate(int x, int y, float score) {
    }

    private record SelectedPoint(int x, int y, int tileSize, float gradient) {
    }
}
