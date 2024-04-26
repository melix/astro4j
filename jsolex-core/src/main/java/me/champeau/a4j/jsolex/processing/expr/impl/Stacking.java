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

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Stacking extends AbstractFunctionImpl {
    private static final String STACKING_MESSAGE = message("stacking");
    public static final int DEFAULT_TILE_SIZE = 32;
    public static final float DEFAULT_OVERLAP_FACTOR = 0.3f;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Scaling scaling;
    private final Crop crop;
    private final Broadcaster broadcaster;

    public Stacking(Map<Class<?>, Object> context, Scaling scaling, Crop crop, Broadcaster broadcaster) {
        super(context, broadcaster);
        this.scaling = scaling;
        this.crop = crop;
        this.broadcaster = broadcaster;
    }

    public ImageWrapper32 stack(List<ImageWrapper32> images, int tileSize, float overlap) {
        if (images.size() == 1) {
            return images.get(0);
        }
        return doStack(images, tileSize, overlap, null, 0);
    }

    public Object stack(List<Object> arguments) {
        assertExpectedArgCount(arguments, "stack takes 1, 2, or 3 arguments (image(s), [tile size], [overlap])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?> list) {
            if (list.size() == 1) {
                return list.get(0);
            }
            var images = list.stream()
                .parallel()
                .filter(ImageWrapper.class::isInstance)
                .map(img -> {
                    if (img instanceof FileBackedImage fbi) {
                        return fbi.unwrapToMemory();
                    }
                    return img;
                })
                .filter(ImageWrapper32.class::isInstance)
                .map(ImageWrapper32.class::cast)
                .toList();
            if (images.size() == 1) {
                return images.get(0);
            }
            if (images.isEmpty()) {
                return List.of();
            }
            var tileSize = arguments.size() >= 2 ? intArg(arguments, 1) : DEFAULT_TILE_SIZE;
            if (tileSize < 16) {
                throw new IllegalArgumentException("tile size must be at least 16");
            }
            var overlap = arguments.size() >= 3 ? floatArg(arguments, 2) : DEFAULT_OVERLAP_FACTOR;
            if (overlap < 0 || overlap > 1) {
                throw new IllegalArgumentException("overlap factor must be between 0 and 1");
            }
            return doStack(images, tileSize, overlap, null, 0);
        } else {
            throw new IllegalArgumentException("stack first argument must be a list of images");
        }
    }

    ImageWrapper32 doStack(List<ImageWrapper32> images, int tileSize, float overlap, ImageWrapper32 referenceImage, float threshold) {
        var widths = images.stream().mapToInt(ImageWrapper32::width).distinct().toArray();
        var heights = images.stream().mapToInt(ImageWrapper32::height).distinct().toArray();
        if (widths.length > 1 || heights.length > 1 || widths[0] != heights[0]) {
            // perform cropping
            images = (List<ImageWrapper32>) crop.autocrop2(List.of(images));
        }
        images = (List<ImageWrapper32>) scaling.radiusRescale(List.of(images));

        // the reference image is the first image
        // We perform stacking by doing the following:
        // for each image to stack, we split the image into tiles of size tileSize
        // then for each tile of the reference image, we're going to compute the average
        // error between the reference tile and the tile of the image to stack. However,
        // instead of simply using ONE tile for the image to stack, we're going to try
        // shifting the tile between -tileSize/4 and +tileSize/4 in both directions, to
        // select the tile which has the lowest error.
        var first = images.iterator().next();
        var height = first.height();
        var width = first.width();
        var sharpness = normalizedSharpnessOf(images, width, height);
        ImageWrapper32 reference = null;
        var referenceSharpness = Double.MIN_VALUE;
        for (int i = 0; i < sharpness.length; i++) {
            double v = sharpness[i];
            if (v > referenceSharpness) {
                referenceSharpness = v;
                reference = images.get(i);
            }
        }
        var integralImages = referenceImage == null ? null : images.stream()
            .parallel()
            .map(img -> imageMath.integralImage(img.asImage()))
            .toList();
        var referenceData = referenceImage != null ? referenceImage.data() : reference.data();
        var referenceIntegral = referenceImage != null ? imageMath.integralImage(referenceImage.asImage()) : null;
        var increment = overlap == -1 ? tileSize : (int) (tileSize * (1 - overlap));
        var result = createOverlappingStack(tileSize, height, width, increment);
        var imageCount = images.size();
        for (int y = 0; y < height; y += increment) {
            for (int x = 0; x < width; x += increment) {
                var idx = y * width + x;
                double progress = (double) (idx) / (width * height);
                broadcaster.broadcast(ProgressEvent.of(progress, STACKING_MESSAGE));
                var weights = prepareTileWeights(images, tileSize, referenceImage, sharpness, integralImages, x, y, referenceIntegral);
                stackSingleTile(images, tileSize, x, width, y, height, imageCount, referenceData, result, weights, threshold, overlap >= 0.3f);
            }
        }
        broadcaster.broadcast(ProgressEvent.of(1.0, STACKING_MESSAGE));
        var metadata = MutableMap.<Class<?>, Object>of();
        for (ImageWrapper32 image : images) {
            metadata.putAll(image.metadata());
        }
        var finalImage = assembleImage(result);
        return new ImageWrapper32(width, height, finalImage, metadata);
    }

    /**
     * Computes the weights for each tile. The weights are computed based on the sharpness, or,
     * if a reference image is provided, on the difference between the reference image and the
     * tile.
     *
     * @param images the images to stack
     * @param tileSize the tile size
     * @param referenceImage the reference image, if any
     * @param sharpness the sharpness of each image
     * @param integralImages the integral images of each image
     * @param x the x coordinate of the tile
     * @param y the y coordinate of the tile
     * @param referenceIntegral the integral image of the reference image, if any
     * @return the weights
     */
    private double[] prepareTileWeights(List<ImageWrapper32> images, int tileSize, ImageWrapper32 referenceImage, double[] sharpness, List<Image> integralImages, int x, int y, Image referenceIntegral) {
        var weights = sharpness;
        if (referenceImage != null) {
            weights = new double[sharpness.length];
            var refAvg = imageMath.areaAverage(referenceIntegral, x, y, tileSize, tileSize);
            for (int i = 0; i < images.size(); i++) {
                // The computation of the weights here is based on empirical observations
                // and is not based on any scientific paper. It's a heuristic which seems
                // to work well in practice, with the idea that we want to give more weight
                // to images which are similar to the reference image, and minimize the error
                // due to truncation of the solar disk at edges and artifacts at the borders.
                var areaAvg = imageMath.areaAverage(integralImages.get(i), x, y, tileSize, tileSize);
                double diff = Math.abs(areaAvg - refAvg) / (refAvg + 1e-5);
                double w = Math.exp(-8 * diff);
                weights[i] = w;
            }
        }
        return weights;
    }

    /**
     * The result image will be computed using overlapping tiles. This means that we have, for
     * each pixel, multiple values which will need to be stacked. This method is responsible
     * for creating the overlapping stack.
     *
     * @param tileSize the tile size
     * @param height the height of the image
     * @param width the width of the image
     * @param increment the increment between two tiles
     * @return the stack
     */
    private static float[][] createOverlappingStack(int tileSize, int height, int width, int increment) {
        var result = new float[height * width][1 + tileSize / increment];
        for (float[] stack : result) {
            Arrays.fill(stack, -1);
        }
        return result;
    }

    private double[] normalizedSharpnessOf(List<ImageWrapper32> images, int width, int height) {
        double[] sharpness = new double[images.size()];
        double totalSharpness = 0;
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            double progress = (double) i / images.size();
            broadcaster.broadcast(ProgressEvent.of(progress, message("estimating.sharpness")));
            var v = imageMath.estimateSharpness(new Image(width, height, image.data()));
            sharpness[i] = v;
            totalSharpness += v;
        }
        if (totalSharpness > 0) {
            for (int i = 0; i < sharpness.length; i++) {
                sharpness[i] = sharpness[i] / totalSharpness;
            }
        }
        broadcaster.broadcast(ProgressEvent.of(1, message("estimating.sharpness")));
        return sharpness;
    }

    private void stackSingleTile(List<ImageWrapper32> images,
                                 int tileSize,
                                 int x,
                                 int width,
                                 int y,
                                 int height,
                                 int imageCount,
                                 float[] referenceData,
                                 float[][] result,
                                 double[] weights,
                                 float threshold,
                                 boolean circle) {
        var tileStack = createStackForTile(tileSize, imageCount);
        for (var image : images) {
            var maxLookupShift = 2 * tileSize / 3;
            var data = image.data();
            var imageIndex = images.indexOf(image);
            findBestMatch(referenceData, data, width, height, tileSize, x, y, maxLookupShift).ifPresent(match -> {
                var bestX = match.x();
                var bestY = match.y();
                pushTileStack(tileSize, width, height, bestX, bestY, tileStack, imageIndex, data, threshold);
            });

        }

        writeOutputValue(tileSize, x, y, width, height, result, tileStack, weights, circle);
    }

    /**
     * This method is a local search algorithm which tries to find the best match
     * for a given tile. It will try to find the best match by computing an error
     * between the reference tile and the tile of the image to stack. Because a
     * full search is too expensive, we're going to try to find the best match
     * by doing a local search around the current position.
     *
     * @param referenceData the reference tile data
     * @param data the tile we're trying to match
     * @param width the width of the image
     * @param height the height of the image
     * @param tileSize the tile size
     * @param x the x coordinate of the reference tile
     * @param y the y coordinate of the reference tile
     * @param maxLookupShift the maximum shift we're going to try for alignment
     * @return the best match
     */
    static Optional<ScoredMatch> findBestMatch(float[] referenceData,
                                               float[] data,
                                               int width,
                                               int height,
                                               int tileSize,
                                               int x,
                                               int y,
                                               int maxLookupShift) {
        record IntermediateResult(
            double error,
            int x,
            int y) implements Comparable<IntermediateResult> {

            private static final Comparator<IntermediateResult> COMPARATOR =
                Comparator.comparingDouble(IntermediateResult::error);

            @Override
            public int compareTo(IntermediateResult o) {
                return COMPARATOR
                    .compare(this, o);
            }
        }

        int minX = Math.max(0, x - maxLookupShift);
        int minY = Math.max(0, y - maxLookupShift);
        int maxX = Math.min(width, x + maxLookupShift);
        int maxY = Math.min(height, y + maxLookupShift);

        // initialize search position with the heuristic values
        var curX = x;
        var curY = y;
        var bestError = Double.MAX_VALUE;
        boolean update = true;
        // We memoize the errors for the points we've already computed
        var errors = new double[2 * maxLookupShift][2 * maxLookupShift];
        for (double[] error : errors) {
            Arrays.fill(error, -1);
        }
        int localSearch = Math.min(maxLookupShift, 12);
        while (update) {
            // compute the errors for the points around the current position (if within bounds)
            var tests = new ArrayList<IntPair>();
            for (int dy = -localSearch; dy <= localSearch; dy++) {
                var yy = curY + dy;
                if (yy < minY || yy >= maxY) {
                    continue;
                }
                for (int dx = -localSearch; dx <= localSearch; dx++) {
                    var xx = curX + dx;
                    if (xx < minX || xx >= maxX || errors[yy - y + maxLookupShift][xx - x + maxLookupShift] != -1) {
                        continue;
                    }
                    var dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist <= localSearch) {
                        tests.add(new IntPair(xx, yy));
                    }
                }
            }

            var curError = new AtomicReference<>(bestError);
            var best = tests.stream()
                .parallel()
                .<IntermediateResult>mapMulti((coords, consumer) -> {
                    var xx = coords.a();
                    var yy = coords.b();
                    var e = computeError(tileSize, width, height, x, y, referenceData, xx, yy, data);
                    errors[yy - y + maxLookupShift][xx - x + maxLookupShift] = e;
                    if (e < curError.get()) {
                        // We don't care that another thread can change the value here
                        // in between, since we're going to sort and take the best anyway
                        curError.set(e);
                        consumer.accept(new IntermediateResult(e, xx, yy));
                    }
                })
                .min(Comparator.comparing(IntermediateResult::error));
            update = best.isPresent();
            if (update) {
                var result = best.get();
                curX = result.x();
                curY = result.y();
                bestError = result.error();
                if (bestError == 0) {
                    // Happy path: we found a perfect match, no need to continue
                    break;
                }
                // reduce search window for subsequent iterations
                localSearch = Math.min(maxLookupShift, 6);
            }
        }
        if (bestError == Double.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new ScoredMatch(curX, curY, bestError));
    }

    private void pushTileStack(int tileSize, int width, int height, int bestX, int bestY, double[][][] tileStack, int imageIndex, float[] bestTileData, float threshold) {
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                var yy = bestY + dy;
                var xx = bestX + dx;
                if (yy < 0 || yy >= height || xx < 0 || xx >= width) {
                    continue;
                }
                var idx = yy * width + xx;
                var v = bestTileData[idx];
                if (v > threshold) {
                    tileStack[dy][dx][imageIndex] = v;
                }
            }
        }
    }

    private static void writeOutputValue(int tileSize, int x, int y, int width, int height, float[][] result, double[][][] tileStack, double[] weights, boolean circle) {
        var c = tileSize / 2;
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                var yy = y + dy;
                var xx = x + dx;
                if (yy < 0 || yy >= height || xx < 0 || xx >= width) {
                    continue;
                }
                // only consider pixels within radius of the center
                if (circle && Math.sqrt((dx - c) * (dx - c) + (dy - c) * (dy - c)) > c) {
                    continue;
                }
                var values = tileStack[dy][dx];
                var pixelIndex = yy * width + xx;
                float[] stack = result[pixelIndex];
                float value;
                if (weights != null) {
                    value = (float) weightedAverage(values, weights);
                } else {
                    value = (float) Arrays.stream(values).filter(v -> v > 0).average().orElse(-1);
                }
                for (int i = 0; i < stack.length; i++) {
                    if (stack[i] == -1) {
                        stack[i] = value;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Because we can either use the median or the average of the stack, we need
     * a data structure which can handle both. The median requires all values to
     * be sorted, so we use 3-dimensional arrays to store the stack of values.
     *
     * @param tileSize the size of the tile
     * @param imageCount the maximum number of values in the stack
     * @return the stack
     */
    private static double[][][] createStackForTile(int tileSize, int imageCount) {
        var partial = new double[tileSize][tileSize][imageCount];
        for (int i = 0; i < tileSize; i++) {
            for (int j = 0; j < tileSize; j++) {
                Arrays.fill(partial[i][j], -1f);
            }
        }
        return partial;
    }

    static double computeError(int tileSize,
                               int width, int height,
                               int refX, int refY,
                               float[] referenceData,
                               int tileX, int tileY,
                               float[] data) {
        var error = 0d;
        int count = 0;
        var minDx = Math.max(Math.max(0, -refX), Math.max(0, -tileX));
        var minDy = Math.max(Math.max(0, -refY), Math.max(0, -tileY));
        var maxDx = Math.min(width - refX - 1, width - tileX - 1);
        var maxDy = Math.min(height - refY - 1, height - tileY - 1);
        for (int dx = minDx; dx < Math.min(maxDx, tileSize); dx++) {
            var xx = refX + dx;
            var tileXX = tileX + dx;
            for (int dy = minDy; dy < Math.min(maxDy, tileSize); dy++) {
                var yy = refY + dy;
                var tileYY = tileY + dy;
                var ref = referenceData[yy * width + xx];
                var img = data[tileYY * width + tileXX];
                var e = (ref - img);
                error += e * e;
                count++;
            }
        }
        if (count == 0) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(error / count);
    }


    private static double weightedAverage(double[] values, double[] weights) {
        if (values.length == weights.length) {
            var valueSum = 0d;
            var weightSum = 0d;
            for (int i = 0; i < values.length; i++) {
                var value = values[i];
                if (value >= 0) {
                    var weight = weights[i];
                    valueSum += value * weight;
                    weightSum += weight;
                }
            }
            if (weightSum > 0) {
                return valueSum / weightSum;
            } else {
                return 0;
            }
        } else {
            throw new IllegalStateException("Values and weights must have the same length");
        }
    }

    private static float[] assembleImage(float[][] stacks) {
        var result = new float[stacks.length];
        for (int pixelIndex = 0; pixelIndex < stacks.length; pixelIndex++) {
            float[] stack = stacks[pixelIndex];
            float sum = 0;
            float count = 0;
            for (float v : stack) {
                if (v >= 0) {
                    sum += v;
                    count++;
                }
            }
            if (sum > 0) {
                result[pixelIndex] = sum / count;
            }
        }
        return result;
    }

    record ScoredMatch(
        int x,
        int y,
        double score
    ) {

    }
}
