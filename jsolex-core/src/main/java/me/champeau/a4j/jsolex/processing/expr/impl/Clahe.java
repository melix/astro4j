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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Clahe extends AbstractFunctionImpl {

    public static final int CLAHE2_BINS = 256;
    public static final int CLAHE2_MAX_LEVELS = 6;
    public static final double CLAHE2_DEFAULT_CLIP = 1.5;

    public Clahe(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object clahe(Map<String ,Object> arguments) {
        BuiltinFunction.CLAHE.validateArgs(arguments);
        int tileSize = intArg(arguments, "ts", ClaheStrategy.DEFAULT_TILE_SIZE);
        int bins = intArg(arguments, "bins", ClaheStrategy.DEFAULT_BINS);
        double clip = doubleArg(arguments, "clip", 1.0);
        if (tileSize * tileSize / (double) bins < 1.0) {
            throw new IllegalArgumentException("The number of bins is too high given the size of the tiles. Either reduce the bin count or increase the tile size");
        }
        return monoToMonoImageTransformer("clahe", "img", arguments, image -> new ClaheStrategy(tileSize, bins, clip).stretch(image));
    }

    public Object clahe2(Map<String ,Object> arguments) {
        BuiltinFunction.CLAHE2.validateArgs(arguments);
        double clip = doubleArg(arguments, "clip", CLAHE2_DEFAULT_CLIP);
        return monoToMonoImageTransformer("clahe2", "img", arguments, image -> {
            switch (image) {
                case ImageWrapper32 mono -> applyMultiScaleClahe(mono, clip);
                case RGBImage rgb -> {
                    var tileSizes = computeTileSizesForImage(rgb);
                    multiScaleClaheChannel(rgb.r(), rgb.width(), rgb.height(), rgb.metadata(), tileSizes, clip);
                    multiScaleClaheChannel(rgb.g(), rgb.width(), rgb.height(), rgb.metadata(), tileSizes, clip);
                    multiScaleClaheChannel(rgb.b(), rgb.width(), rgb.height(), rgb.metadata(), tileSizes, clip);
                }
                default -> throw new IllegalArgumentException("Unsupported image type for clahe2: " + image.getClass());
            }
        });
    }

    /**
     * Applies the multi-scale CLAHE strategy in place on a mono image. The tile sizes
     * are derived from the solar disk diameter (from the image's {@link Ellipse} metadata
     * if available, otherwise from the image dimensions).
     *
     * @param image the image to enhance, modified in place
     * @param clip the clip limit
     */
    public static void applyMultiScaleClahe(ImageWrapper32 image, double clip) {
        var tileSizes = computeTileSizesForImage(image);
        multiScaleClaheChannel(image.data(), image.width(), image.height(), image.metadata(), tileSizes, clip);
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
        return computeTileSizes(rawStart, CLAHE2_BINS, CLAHE2_MAX_LEVELS);
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

    private static void multiScaleClaheChannel(float[][] data,
                                               int width,
                                               int height,
                                               Map<Class<?>, Object> metadata,
                                               List<Integer> tileSizes,
                                               double clip) {
        var sum = new float[height][width];
        for (int tileSize : tileSizes) {
            var copy = ImageWrapper.copyData(data);
            new ClaheStrategy(tileSize, CLAHE2_BINS, clip)
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
