/*
 * Copyright 2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-scale CLAHE: the image is equalized with several tile sizes derived from
 * the solar disk diameter (from the image's {@link Ellipse} metadata if available,
 * otherwise from the image dimensions) and the results are averaged. When the
 * ellipse is known and {@code diskOnly} is set, tile histograms only count pixels
 * of the solar disk, so that the limb is not brightened by the sky pixels of the
 * tiles which straddle it.
 */
public final class Clahe2Strategy implements StretchingStrategy {
    private static final int BINS = 256;
    private static final int MAX_LEVELS = 6;
    public static final double DEFAULT_CLIP = 1.5;

    private final double clip;
    private final boolean diskOnly;

    /**
     * Creates a multi-scale CLAHE strategy which restricts tile histograms to the solar disk.
     *
     * @param clip the clip limit
     */
    public Clahe2Strategy(double clip) {
        this(clip, true);
    }

    /**
     * Creates a multi-scale CLAHE strategy.
     *
     * @param clip the clip limit
     * @param diskOnly whether to restrict tile histograms to the solar disk
     */
    public Clahe2Strategy(double clip, boolean diskOnly) {
        this.clip = clip;
        this.diskOnly = diskOnly;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var tileSizes = computeTileSizesForImage(image);
        var mask = diskOnly ? diskMask(image) : null;
        multiScaleClaheChannel(image.data(), image.width(), image.height(), image.metadata(), tileSizes, mask);
    }

    @Override
    public void stretch(RGBImage image) {
        var tileSizes = computeTileSizesForImage(image);
        var mask = diskOnly ? diskMask(image) : null;
        multiScaleClaheChannel(image.r(), image.width(), image.height(), image.metadata(), tileSizes, mask);
        multiScaleClaheChannel(image.g(), image.width(), image.height(), image.metadata(), tileSizes, mask);
        multiScaleClaheChannel(image.b(), image.width(), image.height(), image.metadata(), tileSizes, mask);
    }

    private static boolean[][] diskMask(ImageWrapper image) {
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isEmpty()) {
            return null;
        }
        var e = ellipse.get();
        var width = image.width();
        var height = image.height();
        var mask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            var row = mask[y];
            for (int x = 0; x < width; x++) {
                row[x] = e.isWithin(x, y);
            }
        }
        return mask;
    }

    private static List<Integer> computeTileSizesForImage(ImageWrapper image) {
        var ellipse = image.findMetadata(Ellipse.class);
        int rawStart;
        if (ellipse.isPresent()) {
            var sa = ellipse.get().semiAxis();
            var diameter = 2 * Math.max(sa.a(), sa.b());
            rawStart = (int) (diameter / 5);
        } else {
            rawStart = Math.min(image.width(), image.height()) / 4;
        }
        return computeTileSizes(rawStart, BINS, MAX_LEVELS);
    }

    static List<Integer> computeTileSizes(int rawStartTileSize, int bins, int maxLevels) {
        int minTile = Math.max(2, (int) Math.ceil(Math.sqrt(bins)));
        int startTile = Math.max(minTile, Integer.highestOneBit(Math.max(minTile, rawStartTileSize)));
        var tileSizes = new ArrayList<Integer>();
        int t = startTile;
        while (tileSizes.size() < maxLevels && t >= minTile) {
            tileSizes.add(t);
            t /= 2;
        }
        if (tileSizes.isEmpty()) {
            tileSizes.add(minTile);
        }
        return tileSizes;
    }

    private void multiScaleClaheChannel(float[][] data,
                                        int width,
                                        int height,
                                        Map<Class<?>, Object> metadata,
                                        List<Integer> tileSizes,
                                        boolean[][] mask) {
        var sum = new float[height][width];
        for (int tileSize : tileSizes) {
            var copy = ImageWrapper.copyData(data);
            new ClaheStrategy(tileSize, BINS, clip, mask)
                    .stretch(new ImageWrapper32(width, height, copy, new HashMap<>(metadata)));
            for (int y = 0; y < height; y++) {
                var row = copy[y];
                var sumRow = sum[y];
                for (int x = 0; x < width; x++) {
                    sumRow[x] += row[x];
                }
            }
        }
        float n = tileSizes.size();
        for (int y = 0; y < height; y++) {
            var dst = data[y];
            var sumRow = sum[y];
            for (int x = 0; x < width; x++) {
                dst[x] = sumRow[x] / n;
            }
        }
    }
}
