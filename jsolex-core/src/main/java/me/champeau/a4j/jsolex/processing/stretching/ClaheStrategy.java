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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.util.Histogram;

public final class ClaheStrategy implements StretchingStrategy {
    public static final int DEFAULT_TILE_SIZE = 16;
    public static final int DEFAULT_BINS = 256;
    public static final double DEFAULT_CLIP = 1.0d;
    private static final int MAX_PIXEL_VALUE = 65535;

    private final int tileSize;
    private final int bins;
    private final double clipRatio;

    public ClaheStrategy(int tileSize, int bins, double clipRatio) {
        this.tileSize = tileSize;
        this.bins = bins;
        this.clipRatio = clipRatio;
    }

    public static ClaheStrategy of(ClaheParams claheParams) {
        return new ClaheStrategy(claheParams.tileSize(), claheParams.bins(), claheParams.clipping());
    }

    public ClaheStrategy withClipRatio(double clipRatio) {
        return new ClaheStrategy(tileSize, bins, clipRatio);
    }

    /**
     * Stretches an image using the CLAHE strategy.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @param data grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(int width, int height, float[] data) {
        RangeExpansionStrategy.DEFAULT.stretch(width, height, data);
        int xTilesCount = (int) Math.ceil(width / (double) tileSize);
        int yTilesCount = (int) Math.ceil(height / (double) tileSize);
        var cdf = new CumulativeDistributionFunction[yTilesCount][xTilesCount];
        for (int y = 0; y < yTilesCount; y++) {
            for (int x = 0; x < xTilesCount; x++) {
                var histogram = histogram(width, height, data, x, y, tileSize);
                clipHistogram(histogram, (int) (clipRatio * histogram.pixelCount() / bins));
                cdf[y][x] = computeCdf(histogram);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = x + y * width;
                float src = data[offset];
                int bin = findHistogramBin(src);
                int tileX = x / tileSize;
                int tileY = y / tileSize;
                var thisCdf = cdf[tileY][tileX].normalizedHistogram[bin];
                float interpolatedValue;
                if (isCornerPixel(x, y, width, height, tileSize)) {
                    interpolatedValue = thisCdf;
                } else if (isLeftOrRightBorder(x, y, width, height, tileSize)) {
                    // linear interpolation between this tile and the bottom/right closest center
                    float distanceToTileCenter = y - centerOf(tileY, tileSize);
                    if (distanceToTileCenter <= 0) {
                        var otherCdf = cdf[tileY - 1][tileX].normalizedHistogram[bin];
                        interpolatedValue = linearInterpolation(otherCdf, thisCdf, centerOf(tileY - 1, tileSize), centerOf(tileY, tileSize), y);
                    } else {
                        var otherCdf = cdf[tileY + 1][tileX].normalizedHistogram[bin];
                        interpolatedValue = linearInterpolation(thisCdf, otherCdf, centerOf(tileY, tileSize), centerOf(tileY + 1, tileSize), y);
                    }
                } else if (isTopOrBottomBorder(x, y, width, height, tileSize)) {
                    // linear interpolation between this tile and the left/right closest center
                    float distanceToTileCenter = x - centerOf(tileX, tileSize);
                    if (distanceToTileCenter <= 0) {
                        var otherCdf = cdf[tileY][tileX - 1].normalizedHistogram[bin];
                        interpolatedValue = linearInterpolation(otherCdf, thisCdf, centerOf(tileX - 1, tileSize), centerOf(tileX, tileSize), x);
                    } else {
                        var otherCdf = cdf[tileY][tileX + 1].normalizedHistogram[bin];
                        interpolatedValue = linearInterpolation(thisCdf, otherCdf, centerOf(tileX, tileSize), centerOf(tileX + 1, tileSize), x);
                    }
                } else {
                    // We are at coordinates (x,y) and we need to interpolate the value at (x,y)
                    // from the values at (x1,y1), (x1, y2), (x2, y1), (x2, y2)
                    // where (x1, y1) are the coordinates of the point in the middle of the tile
                    // which contains (x1,y1) and the value is the cdf value of the tile
                    float x1, y1, x2, y2;
                    float v11, v12, v21, v22;
                    float distanceToTileCenterX = x - centerOf(tileX, tileSize);
                    float distanceToTileCenterY = y - centerOf(tileY, tileSize);

                    if (distanceToTileCenterX <= 0) {
                        if (distanceToTileCenterY <= 0) {
                            // current point is at 2-2
                            x1 = centerOf(tileX - 1, tileSize);
                            y1 = centerOf(tileY - 1, tileSize);
                            v11 = cdf[tileY - 1][tileX - 1].normalizedHistogram[bin];
                            v12 = cdf[tileY][tileX - 1].normalizedHistogram[bin];
                            v21 = cdf[tileY - 1][tileX].normalizedHistogram[bin];
                            v22 = cdf[tileY][tileX].normalizedHistogram[bin];
                        } else {
                            // current point is at 2-1
                            x1 = centerOf(tileX - 1, tileSize);
                            y1 = centerOf(tileY, tileSize);
                            v11 = cdf[tileY][tileX - 1].normalizedHistogram[bin];
                            v12 = cdf[tileY + 1][tileX - 1].normalizedHistogram[bin];
                            v21 = cdf[tileY][tileX].normalizedHistogram[bin];
                            v22 = cdf[tileY + 1][tileX].normalizedHistogram[bin];
                        }
                    } else {
                        if (distanceToTileCenterY <= 0) {
                            // current point is at 1-2
                            x1 = centerOf(tileX, tileSize);
                            y1 = centerOf(tileY - 1, tileSize);
                            v11 = cdf[tileY - 1][tileX].normalizedHistogram[bin];
                            v12 = cdf[tileY][tileX].normalizedHistogram[bin];
                            v21 = cdf[tileY - 1][tileX + 1].normalizedHistogram[bin];
                            v22 = cdf[tileY][tileX + 1].normalizedHistogram[bin];
                        } else {
                            // current point is at 1-1
                            x1 = centerOf(tileX, tileSize);
                            y1 = centerOf(tileY, tileSize);
                            v11 = cdf[tileY][tileX].normalizedHistogram[bin];
                            v12 = cdf[tileY + 1][tileX].normalizedHistogram[bin];
                            v21 = cdf[tileY][tileX + 1].normalizedHistogram[bin];
                            v22 = cdf[tileY + 1][tileX + 1].normalizedHistogram[bin];
                        }
                    }
                    x2 = x1 + tileSize;
                    y2 = y1 + tileSize;
                    interpolatedValue = bilinearInterpolation(v11, v12, v21, v22, x1, y1, x2, y2, x, y);
                }
                data[offset] = MAX_PIXEL_VALUE * interpolatedValue;
            }
        }
    }

    private static float centerOf(int tile, int tileSize) {
        return tile * tileSize + (tileSize / 2f);
    }


    private boolean isLeftOrRightBorder(int x, int y, int width, int height, int tileSize) {
        int halfSize = tileSize / 2;
        return x <= halfSize || (x >= width - 1 - halfSize);
    }

    private boolean isTopOrBottomBorder(int x, int y, int width, int height, int tileSize) {
        int halfSize = tileSize / 2;
        return y <= halfSize || (y >= height - 1 - halfSize);
    }


    private boolean isCornerPixel(int x, int y, int width, int height, int tileSize) {
        int halfSize = tileSize / 2;
        return (x <= halfSize || (x >= width - 1 - halfSize)) && (y <= halfSize || (y >= height - 1 - halfSize));
    }

    private void clipHistogram(Histogram histogram, int clipLimit) {
        var values = histogram.values();
        int excess = 0;
        for (int i = 0; i < bins; i++) {
            if (values[i] > clipLimit) {
                excess += values[i] - clipLimit;
                values[i] = clipLimit;
            }
        }

        // redistribute excess evenly on each bin
        int increment = excess / bins;
        int remainder = excess % bins;
        for (int i = 0; i < bins; i++) {
            values[i] += increment;
        }
        for (int i = 0; i < remainder; i++) {
            values[i]++;
        }
    }


    /**
     * Returns the cumulative histogram mapping function,
     * which returns values between 0 and 1
     *
     * @param histogram the input histogram
     * @return the mapping function
     */
    private CumulativeDistributionFunction computeCdf(Histogram histogram) {
        var values = histogram.values();
        float[] cumulative = new float[values.length];
        cumulative[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            cumulative[i] = cumulative[i - 1] + values[i];
        }
        float max = cumulative[cumulative.length - 1];
        if (max == 0) {
            for (int i = 0; i < values.length; i++) {
                cumulative[i] = 1.0f;
            }
            return new CumulativeDistributionFunction(cumulative);
        }
        for (int i = 0; i < values.length; i++) {
            cumulative[i] /= max;
        }
        return new CumulativeDistributionFunction(cumulative);
    }

    private Histogram histogram(int width, int height, float[] data, int tileX, int tileY, int tileSize) {
        int[] histogram = new int[bins];
        int xStart = tileX * tileSize;
        int xEnd = Math.min(width, (tileX + 1) * tileSize);
        int yStart = tileY * tileSize;
        int yEnd = Math.min(height, (tileY + 1) * tileSize);
        int size = 0;
        int max = -1;
        for (int y = yStart; y < yEnd; y++) {
            for (int x = xStart; x < xEnd; x++) {
                float src = data[x + y * width];
                int bin = findHistogramBin(src);
                histogram[bin]++;
                size++;
                if (histogram[bin] > max) {
                    max = histogram[bin];
                }
            }
        }
        return new Histogram(histogram, size, max);
    }

    private int findHistogramBin(float src) {
        return Math.round(src * (bins - 1) / MAX_PIXEL_VALUE);
    }

    private float linearInterpolation(
            float v1, float v2,
            float x1, float x2,
            float a
    ) {
        float slope = (a - x1) / (x2 - x1);
        return v1 + slope * (v2 - v1);
    }


    private float bilinearInterpolation(
            float v11,
            float v12,
            float v21,
            float v22,
            float x1, float y1,
            float x2, float y2,
            float x, float y
    ) {
        var d = (x2 - x1) * (y2 - y1);
        var w11 = (x2 - x) * (y2 - y) / d;
        var w12 = (x2 - x) * (y - y1) / d;
        var w21 = (x - x1) * (y2 - y) / d;
        var w22 = (x - x1) * (y - y1) / d;
        return w11 * v11 + w12 * v12 + w21 * v21 + w22 * v22;
    }

    private record CumulativeDistributionFunction(
            float[] normalizedHistogram
    ) {

    }
}
