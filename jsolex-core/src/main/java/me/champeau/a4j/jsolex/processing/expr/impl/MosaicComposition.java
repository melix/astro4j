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
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.DrawUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.expr.impl.Dedistort.createTilesForComparison;
import static me.champeau.a4j.jsolex.processing.expr.impl.Dedistort.crossCorrelationShiftFFT;
import static me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils.estimateBackgroundLevel;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;
import static me.champeau.a4j.math.regression.LinearRegression.firstOrderRegression;

public class MosaicComposition extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosaicComposition.class);
    private static final String FINDING_MATCHES_MESSAGE = message("finding.matches");
    private static final AtomicInteger DEBUG_COUNTER = new AtomicInteger();
    private static final String DEBUG_IMAGES = System.getProperty("jsolex.debug.images");
    public static final int DEFAULT_TILE_SIZE = 64;
    public static final float DEFAULT_SAMPLING = 0.25f;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Broadcaster broadcaster;
    private final EllipseFit ellipseFit;
    private final Scaling scaling;

    public MosaicComposition(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit, Scaling scaling) {
        super(context, broadcaster);
        this.broadcaster = broadcaster;
        this.ellipseFit = ellipseFit;
        this.scaling = scaling;
    }

    public ImageWrapper32 mosaic(List<ImageWrapper32> stackedImages, int tileSize, float sampling) {
        return doMosaic(stackedImages, tileSize, sampling);
    }

    public Object mosaic(Map<String, Object> arguments) {
        BuiltinFunction.MOSAIC.validateArgs(arguments);
        var arg = arguments.get("images");
        if (arg instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("list")) {
            arg = map.get("list");
        }
        if (arg instanceof List<?> list) {
            var images = list.stream().filter(ImageWrapper.class::isInstance).map(img -> {
                if (img instanceof FileBackedImage fbi) {
                    return fbi.unwrapToMemory();
                }
                return img;
            }).filter(ImageWrapper32.class::isInstance).map(ImageWrapper32.class::cast).toList();
            if (images.isEmpty()) {
                return List.of();
            }
            if (images.size() == 1) {
                return images.getFirst();
            }
            var tileSize = intArg(arguments, "ts", DEFAULT_TILE_SIZE);
            if (tileSize < 16) {
                throw new IllegalArgumentException("tile size must be at least 16");
            }
            var sampling = floatArg(arguments, "sampling", DEFAULT_SAMPLING);
            if (sampling <= 0) {
                throw new IllegalArgumentException("sampling must be greater than 0");
            }
            try {
                return doMosaic(images, tileSize, sampling);
            } catch (Exception ex) {
                LOGGER.error("Mosaic composition failed", ex);
                throw new ProcessingException(ex);
            }
        } else {
            throw new IllegalArgumentException("mosaic first argument must be a list of images");
        }
    }

    ImageWrapper32 doMosaic(List<ImageWrapper32> images, int tileSize, float sampling) {
        var mainOperation = newOperation().createChild(message("mosaic.composition"));
        broadcaster.broadcast(mainOperation);
        try {
            var backgroundNeutralized = images.stream().map(BackgroundRemoval::neutralizeBackground).toList();
            broadcaster.broadcast(mainOperation.update(0.05));
            var rescaled = scaling.performRadiusRescale(backgroundNeutralized)
                .stream()
                .map(ImageWrapper::unwrapToMemory)
                .map(ImageWrapper32.class::cast).toList();
            broadcaster.broadcast(mainOperation.update(0.1));
            var adjustment = normalizeHistograms(rescaled);
            broadcaster.broadcast(mainOperation.update(0.2));
            var mosaic = doMosaicNoHistogramTransform(tileSize, adjustment.corrected(), adjustment.background(), new HashMap<>(), new HashMap<>(), mainOperation);
            broadcaster.broadcast(mainOperation.update(0.95));
            return ellipseFit.performEllipseFitting(mosaic);
        } finally {
            broadcaster.broadcast(mainOperation.complete());
        }
    }

    private Set<Integer> computeTilesOverBackground(ImageWrapper32 image, Image integralImage, float background, int tileSize) {
        var width = image.width();
        var height = image.height();
        var result = new HashSet<Integer>();
        for (var y = 0; y < height; y += tileSize) {
            for (var x = 0; x < width; x += tileSize) {
                var avg = imageMath.areaAverage(integralImage, x, y, tileSize, tileSize);
                if (avg > background) {
                    result.add(y * width + x);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private ImageWrapper32 doMosaicNoHistogramTransform(int tileSize, List<ImageWrapper32> corrected, float background, Map<ImageWrapper32, Set<Integer>> imageToTilesOverbackground, Map<ImageWrapper32, Image> integralImages, ProgressOperation parentOperation) {
        var totalPairs = Math.max(1, corrected.size() - 1);
        var pairsDone = 0;
        while (corrected.size() > 1) {
            pairsDone++;
            var imageCount = corrected.size();
            var first = corrected.getFirst();
            var height = first.height();
            var width = first.width();
            var distorsionGridSize = nextPowerOf2(Math.max(width / 64, 64));
            LOGGER.debug("Threshold: {}", background);
            computeMissingState(distorsionGridSize, corrected, background, imageToTilesOverbackground, integralImages);
            var tileOverlap = placeMostOverlappingImagesFirst(corrected, imageToTilesOverbackground, imageCount);
            if (tileOverlap != null && tileOverlap.overlappingTiles() == 0) {
                LOGGER.warn("Cannot find overlapping tiles between images, falling back to addition");
                return (ImageWrapper32) new SimpleFunctionCall(Map.of(), broadcaster).applyFunction("max", Map.of("list", corrected), DoubleStream::max);
            }
            var maxSteps = 2 * (height / distorsionGridSize);
            var step = 0;
            var mask = new boolean[width * height];
            Map<Point2D, Optional<DistorsionSample>> cachedInterestPoints = new ConcurrentHashMap<>();
            first = corrected.getFirst();
            var second = corrected.get(1);
            var warpedCompareData = ImageWrapper.copyData(first.data());
            var threshold = background;
            var noUpdateCount = 0;
            var pairOperation = parentOperation.createChild(String.format(message("mosaic.assembling.pair"), pairsDone, totalPairs, 0));
            broadcaster.broadcast(pairOperation);
            while (true) {
                step++;
                broadcaster.broadcast(pairOperation.update(Math.min(0.95, step / (double) Math.max(1, maxSteps)), String.format(message("mosaic.assembling.pair"), pairsDone, totalPairs, step)));
                var reference = corrected.getFirst();
                var compare = corrected.get(1);
                var referenceIntegral = imageMath.integralImage(reference.asImage());
                var compareIntegral = integralImages.get(compare);
                var referenceData = reference.data();
                var feat = findInterestPoints(reference, referenceIntegral, compareIntegral, threshold, distorsionGridSize);
                var length = width * height;
                var otherData = compare.data();
                var assembledData = ImageWrapper.copyData(referenceData);
                var updated = false;
                Map<Point2D, List<Point2D>> localInterestPoints = new HashMap<>();
                var offset = distorsionGridSize;
                var dist = distorsionGridSize;
                var window = distorsionGridSize / 2;
                for (var y = 0; y < height; y += offset) {
                    for (var x = 0; x < width; x += offset) {
                        if (!isTileCompleted(tileSize, y, width, x, length, mask)) {
                            var avg1 = imageMath.areaAverage(referenceIntegral, x - window, y - window, 2 * window, 2 * window);
                            var avg2 = imageMath.areaAverage(compareIntegral, x - window, y - window, 2 * window, 2 * window);
                            var px = distorsionGridSize * (x / distorsionGridSize);
                            var py = distorsionGridSize * (y / distorsionGridSize);
                            var localPoints = localInterestPoints.computeIfAbsent(new Point2D(px, py), p -> feat.stream().parallel().filter(f -> f.distanceTo(p) <= 2 * dist).toList());
                            if (avg1 == 0 && avg2 == 0) {
                                assembleSingleTile(distorsionGridSize, x, y, x, y, width, height, assembledData, otherData, warpedCompareData, mask);
                            } else {
                                var point = new Point2D(x, y);
                                var samples = findBestMatches(cachedInterestPoints, reference, referenceIntegral, localPoints, compare, compareIntegral, distorsionGridSize, step, x, y, dist);
                                if (!samples.isEmpty()) {
                                    var restrictedSamples = samples.stream().filter(s -> s.distanceTo(point) <= dist).toList();
                                    var avgError = restrictedSamples.stream().mapToDouble(DistorsionSample::error).average().orElse(0);
                                    restrictedSamples = restrictedSamples.stream().filter(s -> s.error() <= 1.5 * avgError).toList();
                                    if (!restrictedSamples.isEmpty()) {
                                        var model = buildDistorsionModel(restrictedSamples);
                                        var newX = (int) Math.round(model.modelForX().asPolynomial().applyAsDouble(point.x()));
                                        var newY = (int) Math.round(model.modelForY().asPolynomial().applyAsDouble(point.y()));
                                        updated = assembleSingleTile(distorsionGridSize, x, y, newX, newY, width, height, assembledData, otherData, warpedCompareData, mask);
                                    }
                                }
                            }
                        }
                    }
                }
                var metadata = MetadataMerger.merge(corrected);
                var assembled = new ImageWrapper32(width, height, assembledData, metadata);
                corrected.set(0, assembled);
                var reassembled = true;
                for (var v : mask) {
                    if (!v) {
                        reassembled = false;
                        break;
                    }
                }
                if (!updated) {
                    noUpdateCount++;
                } else {
                    noUpdateCount = 0;
                }
                if (!reassembled && !updated && step < maxSteps && noUpdateCount < 4) {
                    threshold = threshold / 4;
                } else if (reassembled || !updated || step == maxSteps) {
                    var blendOperation = parentOperation.createChild(message("mosaic.blending"));
                    broadcaster.broadcast(blendOperation);
                    try {
                        corrected.remove(1);
                        var smoothCompareData = buildSmoothWarpedCompare(second, cachedInterestPoints, width, height);
                        broadcaster.broadcast(blendOperation.update(0.3));
                        var blended = mosaicBlend(first, second, smoothCompareData, background, width, height);
                        corrected.set(0, blended);
                    } finally {
                        broadcaster.broadcast(blendOperation.complete());
                    }
                    break;
                }
            }
            broadcaster.broadcast(pairOperation.complete());
            broadcaster.broadcast(parentOperation.update(0.2 + 0.7 * pairsDone / (double) totalPairs));
        }

        return corrected.getFirst();
    }

    private static boolean assembleSingleTile(int tileSize, int x, int y, int newX, int newY, int width, int height, float[][] assembledData, float[][] otherData, float[][] warpedCompareData, boolean[] mask) {
        var updated = false;
        for (var dy = 0; dy < tileSize; dy++) {
            for (var dx = 0; dx < tileSize; dx++) {
                var origX = x + dx;
                var origY = y + dy;
                var targetX = newX + dx;
                var targetY = newY + dy;
                var origIdx = (y + dy) * width + (x + dx);
                if (origX >= 0 && origX < width && origY >= 0 && origY < height && targetX >= 0 && targetX < width && targetY >= 0 && targetY < height) {
                    var source = assembledData[y + dy][x + dx];
                    var other = otherData[newY + dy][newX + dx];
                    updated = true;
                    if (source < 0.5 * other) {
                        assembledData[y + dy][x + dx] = other;
                    }
                    warpedCompareData[y + dy][x + dx] = other;
                    mask[origIdx] = true;
                }
            }
        }
        return updated;
    }

    /**
     * Combines two aligned panels via multi-band blending. Builds per-pixel validity masks,
     * equalizes exposure over the overlap, derives a distance-transform feathered weight, then
     * reconstructs via Laplacian pyramid blending (Burt &amp; Adelson 1983, see {@link LaplacianPyramidBlend}).
     * The mask is purely geometric and follows wherever each panel has content, so any layout
     * (top/bottom, left/right, diagonal) works.
     */
    private ImageWrapper32 mosaicBlend(ImageWrapper32 reference, ImageWrapper32 compare, float[][] smoothCompareData, float background, int width, int height) {
        var referenceData = reference.data();
        var refThreshold = computeValidityThreshold(referenceData, width, height, background);
        var cmpThreshold = computeValidityThreshold(smoothCompareData, width, height, background);
        LOGGER.debug("Validity thresholds: ref={} cmp={} (background={})", refThreshold, cmpThreshold, background);
        var refValid = new boolean[height][width];
        var cmpValid = new boolean[height][width];
        var refHasSignal = new boolean[height][width];
        var cmpHasSignal = new boolean[height][width];
        var refCount = 0L;
        var cmpCount = 0L;
        var overlapCount = 0L;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var r = referenceData[y][x] > refThreshold;
                var c = smoothCompareData[y][x] > cmpThreshold;
                refValid[y][x] = r;
                cmpValid[y][x] = c;
                refHasSignal[y][x] = referenceData[y][x] > background;
                cmpHasSignal[y][x] = smoothCompareData[y][x] > background;
                if (r) {
                    refCount++;
                }
                if (c) {
                    cmpCount++;
                }
                if (r && c) {
                    overlapCount++;
                }
            }
        }
        LOGGER.debug("Validity: ref={} cmp={} overlap={} pixels", refCount, cmpCount, overlapCount);
        if (refCount == 0L) {
            return new ImageWrapper32(width, height, smoothCompareData, MetadataMerger.merge(List.of(reference, compare)));
        }
        if (cmpCount == 0L || overlapCount < 8L) {
            return reference;
        }
        var refCmpSum = 0d;
        var cmpSqSum = 0d;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (refValid[y][x] && cmpValid[y][x]) {
                    var r = referenceData[y][x];
                    var c = smoothCompareData[y][x];
                    refCmpSum += r * c;
                    cmpSqSum += c * c;
                }
            }
        }
        var equalizedCompare = smoothCompareData;
        if (cmpSqSum > 1e-6d) {
            var gain = refCmpSum / cmpSqSum;
            if (gain > 0d && Math.abs(gain - 1d) > 0.005d) {
                LOGGER.debug("Exposure gain (compare -> reference): {} (from {} overlap pixels)", gain, overlapCount);
                equalizedCompare = new float[height][width];
                for (var y = 0; y < height; y++) {
                    for (var x = 0; x < width; x++) {
                        equalizedCompare[y][x] = (float) Math.clamp(smoothCompareData[y][x] * gain, 0d, (double) Constants.MAX_PIXEL_VALUE);
                    }
                }
            }
        }
        var dRef = distanceToInvalid(refValid, width, height);
        var dCmp = distanceToInvalid(cmpValid, width, height);
        var maxMinDistance = 0d;
        var mask = new float[height][width];
        var sharpness = 3f;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var dr = dRef[y][x];
                var dc = dCmp[y][x];
                var denom = dr + dc;
                if (denom > 0f) {
                    var raw = dc / denom;
                    var centered = 2f * raw - 1f;
                    var sharpened = (float) Math.tanh(sharpness * centered);
                    mask[y][x] = 0.5f + 0.5f * sharpened;
                } else if (cmpValid[y][x]) {
                    mask[y][x] = 1f;
                } else {
                    mask[y][x] = 0f;
                }
                if (refValid[y][x] && cmpValid[y][x]) {
                    double md = Math.min(dr, dc);
                    if (md > maxMinDistance) {
                        maxMinDistance = md;
                    }
                }
            }
        }
        var refFilled = new float[height][width];
        var cmpFilled = new float[height][width];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var r = referenceData[y][x];
                var c = equalizedCompare[y][x];
                refFilled[y][x] = refHasSignal[y][x] ? r : Math.max(r, c);
                cmpFilled[y][x] = cmpHasSignal[y][x] ? c : Math.max(r, c);
            }
        }
        var maxLayers = LaplacianPyramidBlend.maxLevels(width, height);
        var overlapFeatherWidth = (int) Math.round(2d * maxMinDistance);
        var layers = Math.clamp(overlapFeatherWidth / 40, 3, Math.min(maxLayers, 7));
        LOGGER.debug("Overlap feather width: {} px, pyramid layers: {}", overlapFeatherWidth, layers);
        var blendedData = LaplacianPyramidBlend.blend(refFilled, cmpFilled, mask, layers);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = blendedData[y][x];
                if (v < 0f) {
                    blendedData[y][x] = 0f;
                } else if (v > Constants.MAX_PIXEL_VALUE) {
                    blendedData[y][x] = Constants.MAX_PIXEL_VALUE;
                }
            }
        }
        var metadata = MetadataMerger.merge(List.of(reference, compare));
        return new ImageWrapper32(width, height, blendedData, metadata);
    }

    /**
     * Computes a robust "valid content" threshold for an image by sampling the 90th percentile
     * of pixels above background and scaling it down. Pixels below this threshold are treated
     * as "fading edges" and excluded from the blend mask so they don't dim the overlap region.
     */
    private static float computeValidityThreshold(float[][] data, int width, int height, float background) {
        var bins = 256;
        var histogram = new long[bins];
        var maxVal = 0d;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = data[y][x];
                if (v > maxVal) {
                    maxVal = v;
                }
            }
        }
        if (maxVal <= background) {
            return background;
        }
        var scale = (bins - 1) / maxVal;
        var total = 0L;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = data[y][x];
                if (v > background) {
                    var idx = (int) (v * scale);
                    if (idx >= bins) {
                        idx = bins - 1;
                    }
                    histogram[idx]++;
                    total++;
                }
            }
        }
        if (total == 0L) {
            return background;
        }
        var target = (long) (0.9d * total);
        var running = 0L;
        var p90Bin = 0;
        for (var i = 0; i < bins; i++) {
            running += histogram[i];
            if (running >= target) {
                p90Bin = i;
                break;
            }
        }
        var p90 = p90Bin / scale;
        return (float) Math.max(background, 0.25d * p90);
    }

    /**
     * Chamfer distance transform: for each pixel in {@code valid} returns the distance to the
     * nearest invalid pixel (0 on invalid pixels themselves). Two-pass sequential algorithm
     * with 3x3 neighbourhood.
     */
    private static float[][] distanceToInvalid(boolean[][] valid, int width, int height) {
        var d = new float[height][width];
        float big = width + height;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                d[y][x] = valid[y][x] ? big : 0f;
            }
        }
        var d1 = 1f;
        var d2 = 1.41421356f;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (d[y][x] == 0f) {
                    continue;
                }
                var best = d[y][x];
                if (y > 0) {
                    if (x > 0) {
                        var v = d[y - 1][x - 1] + d2;
                        if (v < best) {
                            best = v;
                        }
                    }
                    var v = d[y - 1][x] + d1;
                    if (v < best) {
                        best = v;
                    }
                    if (x < width - 1) {
                        v = d[y - 1][x + 1] + d2;
                        if (v < best) {
                            best = v;
                        }
                    }
                }
                if (x > 0) {
                    var v = d[y][x - 1] + d1;
                    if (v < best) {
                        best = v;
                    }
                }
                d[y][x] = best;
            }
        }
        for (var y = height - 1; y >= 0; y--) {
            for (var x = width - 1; x >= 0; x--) {
                if (d[y][x] == 0f) {
                    continue;
                }
                var best = d[y][x];
                if (x < width - 1) {
                    var v = d[y][x + 1] + d1;
                    if (v < best) {
                        best = v;
                    }
                }
                if (y < height - 1) {
                    if (x > 0) {
                        var v = d[y + 1][x - 1] + d2;
                        if (v < best) {
                            best = v;
                        }
                    }
                    var v = d[y + 1][x] + d1;
                    if (v < best) {
                        best = v;
                    }
                    if (x < width - 1) {
                        v = d[y + 1][x + 1] + d2;
                        if (v < best) {
                            best = v;
                        }
                    }
                }
                d[y][x] = best;
            }
        }
        return d;
    }

    private float[][] buildSmoothWarpedCompare(ImageWrapper32 second, Map<Point2D, Optional<DistorsionSample>> cachedInterestPoints, int width, int height) {
        var source = second.data();
        var samples = cachedInterestPoints.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(DistorsionSample::error))
                .toList();
        if (samples.size() < 8) {
            LOGGER.warn("Only {} distorsion samples available; using identity warp for mosaic blend", samples.size());
            return ImageWrapper.copyData(source);
        }
        var globalModel = buildDistorsionModel(samples);
        if (!globalModel.isValid() || globalModel == DistorsionModel.NO_DISTORSION) {
            LOGGER.warn("Invalid distorsion model; using identity warp for mosaic blend");
            return ImageWrapper.copyData(source);
        }
        var out = new float[height][width];
        var polyX = globalModel.modelForX().asPolynomial();
        var polyY = globalModel.modelForY().asPolynomial();
        for (var y = 0; y < height; y++) {
            var sy = polyY.applyAsDouble(y);
            if (sy < 0 || sy > height - 1) {
                continue;
            }
            var iy = (int) sy;
            var iy1 = Math.min(iy + 1, height - 1);
            var fy = sy - iy;
            var row0 = source[iy];
            var row1 = source[iy1];
            for (var x = 0; x < width; x++) {
                var sx = polyX.applyAsDouble(x);
                if (sx < 0 || sx > width - 1) {
                    continue;
                }
                var ix = (int) sx;
                var ix1 = Math.min(ix + 1, width - 1);
                var fx = sx - ix;
                var v00 = row0[ix];
                var v01 = row0[ix1];
                var v10 = row1[ix];
                var v11 = row1[ix1];
                out[y][x] = (float) ((1 - fx) * (1 - fy) * v00 + fx * (1 - fy) * v01 + (1 - fx) * fy * v10 + fx * fy * v11);
            }
        }
        return out;
    }

    private static boolean isTileCompleted(int tileSize, int y, int width, int x, int length, boolean[] mask) {
        for (var dy = 0; dy < tileSize; dy++) {
            for (var dx = 0; dx < tileSize; dx++) {
                var idx = (y + dy) * width + (x + dx);
                if (idx >= 0 && idx < length && !mask[idx]) {
                    return false;
                }
            }
        }
        return true;
    }

    private DistorsionModel buildDistorsionModel(List<DistorsionSample> sortedSamples) {
        if (sortedSamples.size() < 8) {
            return DistorsionModel.NO_DISTORSION;
        }
        var pointsForX = extractPoints(sortedSamples, DistorsionSample::sampleForX);
        var pointsForY = extractPoints(sortedSamples, DistorsionSample::sampleForY);
        var maxScore = sortedSamples.getLast().error();
        var weights = sortedSamples.stream().mapToDouble(s -> (1 - (s.error() / maxScore))).map(d -> d * d).toArray();
        var regressionForX = firstOrderRegression(pointsForX.toArray(new Point2D[0]), weights);
        var regressionForY = firstOrderRegression(pointsForY.toArray(new Point2D[0]), weights);
        var model = new DistorsionModel(regressionForX, regressionForY);
        if (model.isValid()) {
            return model;
        } else {
            return DistorsionModel.NO_DISTORSION;
        }
    }

    private static List<Point2D> extractPoints(List<DistorsionSample> samples, Function<DistorsionSample, Point2D> extractor) {
        return samples.stream().map(extractor).toList();
    }

    private static TileOverlap placeMostOverlappingImagesFirst(List<ImageWrapper32> corrected, Map<ImageWrapper32, Set<Integer>> imageToTilesOverbackground, int imageCount) {
        TileOverlap bestOverlap = null;
        for (var i = 0; i < imageCount; i++) {
            for (var j = i + 1; j < imageCount; j++) {
                var img1 = corrected.get(i);
                var img2 = corrected.get(j);
                var tiles1 = imageToTilesOverbackground.get(img1);
                var tiles2 = imageToTilesOverbackground.get(img2);
                var intersection = new HashSet<>(tiles1);
                intersection.retainAll(tiles2);
                var overlapping = intersection.size();
                if (bestOverlap == null || overlapping > bestOverlap.overlappingTiles()) {
                    bestOverlap = new TileOverlap(i, j, overlapping);
                }
            }
        }
        if (bestOverlap != null) {
            var firstIndex = bestOverlap.image1();
            var secondIndex = bestOverlap.image2();
            if (firstIndex > 0) {
                var tmp = corrected.getFirst();
                corrected.set(0, corrected.get(firstIndex));
                corrected.set(firstIndex, tmp);
            }
            if (secondIndex > 1) {
                var tmp = corrected.get(1);
                corrected.set(1, corrected.get(secondIndex));
                corrected.set(secondIndex, tmp);
            }
        }
        return bestOverlap;
    }

    private void computeMissingState(int tileSize, List<ImageWrapper32> corrected, float background, Map<ImageWrapper32, Set<Integer>> imageToTilesOverbackground, Map<ImageWrapper32, Image> integralImages) {
        var count = new AtomicInteger();
        var totalOperations = 2 * corrected.size();
        var progressOperation = newOperation().createChild(message("computing.integral.images"));
        try {
            corrected.stream().parallel().peek(img -> broadcaster.broadcast(progressOperation.update(count.getAndIncrement() / (double) totalOperations))).filter(img -> !integralImages.containsKey(img))
                    .map(img -> new Object() {
                        final ImageWrapper32 image = img;
                        final Image integral = imageMath.integralImage(img.asImage());
                    }).forEachOrdered(o -> integralImages.put(o.image, o.integral));
            corrected.stream().parallel().peek(img -> broadcaster.broadcast(progressOperation.update(count.getAndIncrement() / (double) totalOperations))).filter(img -> !imageToTilesOverbackground.containsKey(img))
                    .map(img -> new Object() {
                        final ImageWrapper32 image = img;
                        final Set<Integer> tiles = computeTilesOverBackground(img, integralImages.get(img), background, tileSize);
                    }).forEachOrdered(o -> imageToTilesOverbackground.put(o.image, o.tiles));
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private List<Point2D> findInterestPoints(ImageWrapper32 image, Image integralImage, Image compareIntegral, float threshold, int tileSize) {
        var width = image.width();
        var height = image.height();
        var result = new ArrayList<Point2D>();
        var sampleInterval = Math.max(16, tileSize / 2);
        for (var y = 0; y < height; y += sampleInterval) {
            var i = 0;
            for (var x = 0; x < width; x += sampleInterval) {
                var yoffset = (i++) % 2 == 0 ? 0 : sampleInterval / 2;
                var yy = y + yoffset;
                var refAvg = imageMath.areaAverage(integralImage, x, y, sampleInterval, sampleInterval);
                var cmpAvg = imageMath.areaAverage(compareIntegral, x, yy, sampleInterval, sampleInterval);
                if (refAvg > threshold && cmpAvg > threshold) {
                    result.add(new Point2D(x, yy));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private List<DistorsionSample> findBestMatches(Map<Point2D, Optional<DistorsionSample>> cache, ImageWrapper32 reference, Image referenceIntegral, List<Point2D> referencePoints, ImageWrapper32 other, Image otherIntegral, int tileSize, int step,
                                                   int refX, int refY, double maxDist) {
        var progressOperation = newOperation().createChild(FINDING_MATCHES_MESSAGE.formatted(step));
        broadcaster.broadcast(progressOperation);
        var width = reference.width();
        var height = reference.height();
        var referenceData = reference.data();
        var data = other.data();
        var maxDistSquared = maxDist * maxDist;
        try {
            return referencePoints.stream().parallel().<DistorsionSample>mapMulti((referencePoint, consumer) -> {
                var x = (int) referencePoint.x();
                var y = (int) referencePoint.y();
                var dx = x - refX;
                var dy = y - refY;
                if (dx * dx + dy * dy > maxDistSquared) {
                    return;
                }
                var distorsionSample = cache.computeIfAbsent(referencePoint, p -> {
                    var tiles = createTilesForComparison(tileSize, x, width, y, height, referenceData, data, 0);
                    var dxy = crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile());
                    var sy = y - dxy.a();
                    var sx = x - dxy.b();
                    var referenceAvg = imageMath.areaAverage(referenceIntegral, x, y, tileSize, tileSize);
                    var otherAvg = imageMath.areaAverage(otherIntegral, (int) sx, (int) sy, tileSize, tileSize);
                    if (withinTolerance(otherAvg, referenceAvg, 0.25)) {
                        return Optional.of(new DistorsionSample(referencePoint, new Point2D(sx, sy), 0));
                    }

                    return Optional.empty();
                });
                distorsionSample.ifPresent(consumer);
            }).sorted(Comparator.comparingDouble(DistorsionSample::error)).toList();
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private static boolean withinTolerance(double value, double value2, double relativeTolerance) {
        return Math.abs(value - value2) <= Math.max(Math.abs(value), Math.abs(value2)) * relativeTolerance;
    }

    /**
     * Iteratively matches the mean brightness of each image to the brightest one, then sorts
     * the results by the count of pixels above background so the most-filled image is first.
     *
     * @return the adjusted images and a shared background-level estimate
     */
    private HistogramNormalization normalizeHistograms(List<ImageWrapper32> images) {
        record ImageWithBackground(ImageWrapper32 image, float background) {
            public int pixelsAboveBackground() {
                var data = image.data();
                var count = 0;
                for (var line : data) {
                    for (var v : line) {
                        if (v > background) {
                            count++;
                        }
                    }
                }
                return count;
            }
        }
        var progressOperation = newOperation().createChild(message("normalizing.histograms"));
        broadcaster.broadcast(progressOperation);
        try {
            List<ImageWrapper32> adjusted = new ArrayList<>(images);
            var nImages = adjusted.size();
            var trials = 8;
            while (--trials > 0) {
                var maxAvg = 0f;
                var avgs = new float[nImages];
                for (var i = 0; i < nImages; i++) {
                    var img = adjusted.get(i);
                    var signal = estimateBackgroundLevel(img.data(), 64);
                    avgs[i] = averageOfPixelsAbove(img, signal);
                    maxAvg = Math.max(avgs[i], maxAvg);
                }

                for (var i = 0; i < nImages; i++) {
                    var img = adjusted.get(i);
                    var v = avgs[i];
                    if (!withinTolerance(v, maxAvg, 0.01)) {
                        LOGGER.debug("Signal levels are not within tolerance, adjusting...");
                        adjusted.set(i, rescaleToTarget(img, maxAvg, v));
                    }
                }
            }

            var backgrounds = adjusted.stream().parallel().map(img -> new ImageWithBackground(img, estimateBackgroundLevel(img.data()))).toList();
            adjusted = backgrounds.stream().sorted(Comparator.comparingInt(ImageWithBackground::pixelsAboveBackground).reversed()).map(ImageWithBackground::image).collect(Collectors.toCollection(ArrayList::new));
            var averageBackground = backgrounds.stream().mapToDouble(ImageWithBackground::background).average().orElse(0);
            var background = (float) (0.8 * averageBackground);
            return new HistogramNormalization(adjusted, background);
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private static ImageWrapper32 rescaleToTarget(ImageWrapper32 image, float target, float current) {
        var correction = target / current;
        var result = image.copy();
        if (correction != 1) {
            var data = result.data();
            var height = result.height();
            var width = result.width();
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    data[y][x] = Math.max(0, Math.min(Constants.MAX_PIXEL_VALUE, data[y][x] * correction));
                }
            }
        }
        return result;
    }

    private static float averageOfPixelsAbove(ImageWrapper32 image, float threshold) {
        var data = image.data();
        var total = 0d;
        var count = 0;
        var height = image.height();
        var width = image.width();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = data[y][x];
                if (v > threshold && v < 65000) {
                    total += v;
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0;
        }

        return (float) (total / count);
    }


    private void maybeCreateMatchDebugImage(List<ImageWrapper32> corrected, int width, int height, List<DistorsionSample> candidates, int tileSize) {
        if (DEBUG_IMAGES == null) {
            return;
        }
        var cpt = DEBUG_COUNTER.incrementAndGet();
        IntStream.range(0, candidates.size()).mapToObj(i -> new Object() {
                private final DistorsionSample candidate = candidates.get(i);
                private final int idx = i;
            })
            .parallel()
            .forEach(o -> {
                var candidate = o.candidate;
                var idx = o.idx;
                var bi = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_RGB);
                var g = bi.createGraphics();
                for (var i = 0; i < 2; i++) {
                    var img = corrected.get(i).data();
                    var offset = width * i;
                    for (var y = 0; y < height; y++) {
                        for (var x = 0; x < width; x++) {
                            var grey = (int) img[y][x] >> 8;
                            var rgb = grey << 16 | grey << 8 | grey;
                            bi.setRGB(x + offset, y, rgb);
                        }
                    }
                }
                g.setColor(Color.WHITE);
                g.setFont(g.getFont().deriveFont(60f));
                g.drawString("Tile size %d".formatted(tileSize), (width / 2), 30);
                var fx = (int) candidate.source().x();
                var fy = (int) candidate.source().y();
                var sx = (int) candidate.target().x();
                var sy = (int) candidate.target().y();
                g.drawLine(fx, fy, sx + width, sy);
                g.setColor(Color.ORANGE);
                g.drawRect(fx, fy, tileSize, tileSize);
                g.drawRect(sx + width, sy, tileSize, tileSize);
                g.setFont(g.getFont().deriveFont(30f));
                g.drawString("(" + fx + "," + fy + ")", fx - 40, fy - 50);
                g.drawString("(%d,%d score %.1f)".formatted(sx, sy, candidate.error()), sx + width - 40, sy - 50);
                try {
                    var debugDir = new File(DEBUG_IMAGES + "/" + cpt);
                    if (!debugDir.mkdirs() && !debugDir.isDirectory()) {
                        LOGGER.warn("Could not create debug directory {}", debugDir);
                    }
                    ImageIO.write(bi, "png", new File(String.format(DEBUG_IMAGES + "/%d/%04d_match.png", cpt, idx)));
                } catch (IOException e) {
                    LOGGER.warn("Failed to write match debug image", e);
                }
            });
        var tmp = new float[2 * height][width];
        for (var i = 0; i < 2; i++) {
            var img = corrected.get(i).data();
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    tmp[y * 2 + i][x] = img[y][x];
                }
            }
        }
        for (var candidate : candidates) {
            var fx = (int) candidate.source().x();
            var fy = (int) candidate.source().y();
            var sx = (int) candidate.target().x();
            var sy = (int) candidate.target().y();
            DrawUtils.drawLine(tmp, 2 * width, height, fx, fy, sx + width, sy, 1);
        }
        broadcaster.broadcast(new ImageGeneratedEvent(
            new GeneratedImage(GeneratedImageKind.IMAGE_MATH, "match" + System.currentTimeMillis(), Path.of(DEBUG_IMAGES + "/match-" + System.currentTimeMillis() + ".png"), new ImageWrapper32(width * 2, height, tmp, MutableMap.of()), null)));
    }

    private record HistogramNormalization(List<ImageWrapper32> corrected, float background) {

    }

    private record DistorsionSample(Point2D source, Point2D target, double error) {

        public Point2D sampleForX() {
            return new Point2D(source.x(), target.x());
        }

        public Point2D sampleForY() {
            return new Point2D(source.y(), target.y());
        }

        public double distanceTo(Point2D point) {
            return source.distanceTo(point);
        }
    }

    private record TileOverlap(int image1, int image2, int overlappingTiles) {
    }

    private record DistorsionModel(DoublePair modelForX, DoublePair modelForY) {
        public static final DistorsionModel NO_DISTORSION = new DistorsionModel(new DoublePair(1, 0), new DoublePair(1, 0));

        public boolean isValid() {
            if (Double.isNaN(modelForX.a()) || Double.isNaN(modelForX.b())) {
                return false;
            }
            if (Double.isNaN(modelForY.a()) || Double.isNaN(modelForY.b())) {
                return false;
            }
            return true;
        }
    }


}
