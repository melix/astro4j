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

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.DrawUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.IntPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.math.regression.LinearRegression.firstOrderRegression;

public class MosaicComposition extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosaicComposition.class);
    private static final String FINDING_MATCHES_MESSAGE = message("finding.matches");
    private static final String ALIGNMENT_MESSAGE = message("aligning.images");
    private static final double SQRT_OF_2 = Math.sqrt(2);
    private static final AtomicInteger DEBUG_COUNTER = new AtomicInteger();
    private static final String DEBUG_IMAGES = System.getProperty("jsolex.debug.images");
    public static final int DEFAULT_TILE_SIZE = 64;
    public static final float DEFAULT_OVERLAP_FACTOR = 0.3f;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Broadcaster broadcaster;
    private final Stacking stacking;
    private final EllipseFit ellipseFit;
    private final Scaling scaling;

    public MosaicComposition(ForkJoinContext forkJoinContext,
                             Map<Class<?>, Object> context,
                             Broadcaster broadcaster,
                             Stacking stacking,
                             EllipseFit ellipseFit,
                             Scaling scaling) {
        super(forkJoinContext, context);
        this.broadcaster = broadcaster;
        this.stacking = stacking;
        this.ellipseFit = ellipseFit;
        this.scaling = scaling;
    }

    public ImageWrapper32 mosaic(List<ImageWrapper32> stackedImages, int tileSize, float overlap) {
        return doMosaic(stackedImages, tileSize, overlap);
    }

    public Object mosaic(List<Object> arguments) {
        assertExpectedArgCount(arguments, "mosaic takes 1, 2, or 3 arguments (image(s), [tile size], [overlap])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?> list) {
            var images = list.stream()
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
            if (images.isEmpty()) {
                return List.of();
            }
            if (images.size() == 1) {
                return images.get(0);
            }
            images = scaling.performRadiusRescale(images)
                .stream()
                .map(ImageWrapper32.class::cast)
                .toList();
            var tileSize = arguments.size() >= 2 ? intArg(arguments, 1) : DEFAULT_TILE_SIZE;
            if (tileSize < 16) {
                throw new IllegalArgumentException("tile size must be at least 16");
            }
            var overlap = arguments.size() >= 3 ? floatArg(arguments, 2) : DEFAULT_OVERLAP_FACTOR;
            if (overlap < 0 || overlap > 1) {
                throw new IllegalArgumentException("overlap factor must be between 0 and 1");
            }
            try {
                return doMosaic(images, tileSize, overlap);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new ProcessingException(ex);
            }
        } else {
            throw new IllegalArgumentException("mosaic first argument must be a list of images");
        }
    }

    ImageWrapper32 doMosaic(List<ImageWrapper32> images, int tileSize, float overlap) {
        var adjustment = normalizeHistograms(images);
        var mosaic = doMosaicNoHistogramTransform(tileSize, overlap, adjustment.corrected(), adjustment.background(), new HashMap<>(), new HashMap<>());
        return ellipseFit.performEllipseFitting(mosaic);
    }

    private Set<Integer> computeTilesOverBackground(ImageWrapper32 image, Image integralImage, float background, int tileSize) {
        var width = image.width();
        var height = image.height();
        var result = new HashSet<Integer>();
        for (int y = 0; y < height; y += tileSize) {
            for (int x = 0; x < width; x += tileSize) {
                var avg = imageMath.areaAverage(integralImage, x, y, tileSize, tileSize);
                if (avg > background) {
                    result.add(y * width + x);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private ImageWrapper32 doMosaicNoHistogramTransform(int tileSize, float overlap, List<ImageWrapper32> corrected, float background, Map<ImageWrapper32, Set<Integer>> imageToTilesOverbackground, Map<ImageWrapper32, Image> integralImages) {
        while (corrected.size() > 1) {
            var imageCount = corrected.size();
            var first = corrected.iterator().next();
            var height = first.height();
            var width = first.width();
            LOGGER.debug("Threshold: {}", background);
            computeMissingState(tileSize, corrected, background, imageToTilesOverbackground, integralImages);
            var tileOverlap = placeMostOverlappingImagesFirst(corrected, imageToTilesOverbackground, imageCount);
            if (tileOverlap != null && tileOverlap.overlappingTiles() == 0) {
                LOGGER.warn("Cannot find overlapping tiles between images, falling back to addition");
                return (ImageWrapper32) ScriptSupport.applyFunction(
                    "max",
                    List.of(corrected),
                    DoubleStream::max
                );
            }

            var result = new float[width * height][2];
            var reference = corrected.get(0);
            var compare = corrected.get(1);
            var referenceIntegral = integralImages.get(reference);
            var feat = findInterestPoints(reference, compare, referenceIntegral, background, tileSize);
            var referenceData = reference.data();
            for (int pixelIndex = 0; pixelIndex < width * height; pixelIndex++) {
                var c = referenceData[pixelIndex];
                result[pixelIndex][0] = c;
            }
            broadcaster.broadcast(ProgressEvent.of(0, FINDING_MATCHES_MESSAGE));
            var compareIntegral = integralImages.get(compare);
            var matches = findBestMatches(reference, referenceIntegral, feat, compare, compareIntegral, tileSize, background);
            broadcaster.broadcast(ProgressEvent.of(1.0, FINDING_MATCHES_MESSAGE));
            var samples = matches.stream()
                .parallel()
                .map(this::createDistortionModelSample)
                .sorted(Comparator.comparingDouble(DistorsionSample::score))
                .toList();
            var distorsionGridSize = Math.max(width / 64, 64);
            var localGridSize = tileSize / 4;
            var regressions = buildDistorsionGrid(width, height, distorsionGridSize, tileSize, samples);
            var otherData = compare.data();
            DistorsionModel model = null;
            var maxDist = Math.sqrt(width * width + height * height);
            var progress = 0d;
            for (int y = 0; y < height; y++) {
                broadcaster.broadcast(ProgressEvent.of(progress / (width * height), ALIGNMENT_MESSAGE));
                for (int x = 0; x < width; x++) {
                    progress++;
                    if (model == null || (x % localGridSize == 0 && y % localGridSize == 0)) {
                        model = computeLocalDistorsion(x, y, regressions, maxDist);
                    }
                    int newX = (int) model.modelForX().asPolynomial().applyAsDouble(x);
                    int newY = (int) model.modelForY().asPolynomial().applyAsDouble(y);
                    int idx = newY * width + newX;
                    if (idx >= 0 && idx < result.length && otherData[idx] > background) {
                        result[y * width + x][1] = otherData[idx];
                    }
                }
            }
            broadcaster.broadcast(ProgressEvent.of(1.0, ALIGNMENT_MESSAGE));

            var metadata = MutableMap.<Class<?>, Object>of();
            for (var image : corrected) {
                metadata.putAll(image.metadata());
            }

            var finalImage = assembleImage(result, width, height, background);
            var assembled = new ImageWrapper32(width, height, finalImage, metadata);
            var mosaic = stacking.doStack(List.of(reference, compare), tileSize, overlap, assembled, 0);
            // replace the first 2 images with the mosaic
            corrected.set(0, mosaic);
            corrected.remove(1);
        }
        return corrected.get(0);
    }

    private static DistorsionModel computeLocalDistorsion(int x, int y, List<LocalModel> regressions, double maxDist) {
        var point = new Point2D(x, y);
        var weights = regressions.stream()
            .mapToDouble(lm -> lm.distanceTo(point))
            .map(d -> d / maxDist)
            .map(d -> 1 / (1 + d))
            .map(d -> d * d)
            .toArray();
        var totalWeight = Arrays.stream(weights)
            .sum();
        return regressions.stream()
            .parallel()
            .map(lm -> {
                var modelForX = lm.model().modelForX();
                var modelForY = lm.model().modelForY();
                var weight = weights[regressions.indexOf(lm)] / totalWeight;
                modelForX = new DoublePair(
                    modelForX.a() * weight,
                    modelForX.b() * weight
                );
                modelForY = new DoublePair(
                    modelForY.a() * weight,
                    modelForY.b() * weight
                );
                return new DistorsionModel(modelForX, modelForY);
            })
            .reduce((m1, m2) -> {
                var modelForX = new DoublePair(
                    m1.modelForX().a() + m2.modelForX().a(),
                    m1.modelForX().b() + m2.modelForX().b()
                );
                var modelForY = new DoublePair(
                    m1.modelForY().a() + m2.modelForY().a(),
                    m1.modelForY().b() + m2.modelForY().b()
                );
                return new DistorsionModel(modelForX, modelForY);
            })
            .orElseThrow();
    }

    private List<LocalModel> buildDistorsionGrid(int width, int height, int distorsionGridSize, int tileSize, List<DistorsionSample> samples) {
        var progress = new AtomicInteger();
        var total = (width / distorsionGridSize) * (height / distorsionGridSize);
        try {
            var maxDist = 2 * tileSize * SQRT_OF_2;
            return IntStream.range(0, height + 1)
                .filter(y -> y % distorsionGridSize == 0)
                .mapToObj(y -> IntStream.range(0, width + 1)
                    .filter(x -> x % distorsionGridSize == 0)
                    .mapToObj(x -> new Point2D(x, y)))
                .flatMap(Function.identity())
                .parallel()
                .map(p -> {
                        try {
                            var closestSamples = samples.stream()
                                .filter(s -> s.distanceTo(p) < maxDist)
                                .sorted(Comparator.comparingDouble(DistorsionSample::score))
                                .toList();
                            if (closestSamples.size() < 8) {
                                return new LocalModel(p, DistorsionModel.UNDISTORTED);
                            }
                            var modelForX = buildModel(closestSamples, DistorsionSample::sampleForX);
                            var modelForY = buildModel(closestSamples, DistorsionSample::sampleForY);
                            var maxScore = closestSamples.get(closestSamples.size() - 1).score();
                            var weights = closestSamples.stream()
                                .mapToDouble(s -> (1 - (s.score() / maxScore)))
                                .map(d -> d * d)
                                .toArray();
                            var regressionForX = firstOrderRegression(modelForX.toArray(new Point2D[0]), weights);
                            var regressionForY = firstOrderRegression(modelForY.toArray(new Point2D[0]), weights);
                            var model = new DistorsionModel(regressionForX, regressionForY);
                            if (model.isValid()) {
                                return new LocalModel(p, model);
                            } else {
                                return null;
                            }
                        } finally {
                            progress.incrementAndGet();
                            broadcaster.broadcast(ProgressEvent.of(progress.get() / (double) total, message("building.distorsion.model")));
                        }
                    }
                )
                .filter(Objects::nonNull)
                .toList();
        } finally {
            broadcaster.broadcast(ProgressEvent.of(1.0, message("building.distorsion.model")));
        }
    }

    private static List<Point2D> buildModel(List<DistorsionSample> samples, Function<DistorsionSample, Point2D> extractor) {
        return samples.stream()
            .map(extractor)
            .toList();
    }

    private static TileOverlap placeMostOverlappingImagesFirst(List<ImageWrapper32> corrected, Map<ImageWrapper32, Set<Integer>> imageToTilesOverbackground, int imageCount) {
        TileOverlap bestOverlap = null;
        for (int i = 0; i < imageCount; i++) {
            for (int j = i + 1; j < imageCount; j++) {
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
            int firstIndex = bestOverlap.image1();
            int secondIndex = bestOverlap.image2();
            if (firstIndex > 0) {
                var tmp = corrected.get(0);
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
        try {
            // add missing images to integral images map
            corrected.stream()
                .parallel()
                .peek(img -> broadcaster.broadcast(ProgressEvent.of(count.getAndIncrement() / (double) totalOperations, message("computing.integral.images"))))
                .filter(img -> !integralImages.containsKey(img))
                .map(img -> new Object() {
                    final ImageWrapper32 image = img;
                    final Image integral = imageMath.integralImage(img.asImage());
                })
                .forEachOrdered(o -> integralImages.put(o.image, o.integral));
            // add tiles over background to map
            corrected.stream()
                .parallel()
                .peek(img -> broadcaster.broadcast(ProgressEvent.of(count.getAndIncrement() / (double) totalOperations, message("computing.integral.images"))))
                .filter(img -> !imageToTilesOverbackground.containsKey(img))
                .map(img -> new Object() {
                    final ImageWrapper32 image = img;
                    final Set<Integer> tiles = computeTilesOverBackground(img, integralImages.get(img), background, tileSize);
                })
                .forEachOrdered(o -> imageToTilesOverbackground.put(o.image, o.tiles));
        } finally {
            broadcaster.broadcast(ProgressEvent.of(1.0, message("computing.integral.images")));
        }
    }

    private DistorsionSample createDistortionModelSample(Match match) {
        var ip = match.first();
        var oip = match.second();
        return new DistorsionSample(
            new Point2D(ip.a(), ip.b()),
            new Point2D(oip.a(), oip.b()),
            match.score()
        );
    }

    private List<IntPair> findInterestPoints(ImageWrapper32 image, ImageWrapper32 compare, Image integralImage, float threshold, int tileSize) {
        var width = image.width();
        var height = image.height();
        var result = new ArrayList<IntPair>();
        int sampleInterval = Math.max(16, tileSize / 2);
        for (int y = 0; y < height; y += sampleInterval) {
            int i = 0;
            for (int x = 0; x < width; x += sampleInterval) {
                var yoffset = (i++) % 2 == 0 ? 0 : sampleInterval / 2;
                var yy = y + yoffset;
                var avg = imageMath.areaAverage(integralImage, x, yy, tileSize, tileSize);
                if (avg > threshold) {
                    var valid = isValidSample(image, x, yy, tileSize, 2 * threshold)
                                && isValidSample(compare, x, yy, tileSize, 2 * threshold);
                    if (valid) {
                        result.add(new IntPair(x, yy));
                    }
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isValidSample(ImageWrapper32 image, int x, int y, int tileSize, float threshold) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        double total = 0;
        double aboveThreshold = 0;
        for (int dy = -tileSize / 2; dy < tileSize / 2; dy++) {
            var yy = y + dy;
            if (yy < 0 || yy >= height) {
                continue;
            }
            for (int dx = -tileSize / 2; dx < tileSize / 2; dx++) {
                var xx = x + dx;
                if (xx < 0 || xx >= width) {
                    continue;
                }
                var idx = yy * width + xx;
                total++;
                var v = data[idx];
                if (v <= 0) {
                    return false;
                }
                if (v > threshold) {
                    aboveThreshold++;
                }
            }
        }
        return total != 0 && (aboveThreshold / total > 0.8d);
    }

    private List<Match> findBestMatches(ImageWrapper32 reference, Image referenceIntegral, List<IntPair> referencePoints, ImageWrapper32 other, Image otherIntegral, int tileSize, float threshold) {
        var width = reference.width();
        var height = reference.height();
        var referenceData = reference.data();
        var maxLookupShift = 2 * tileSize / 3;
        var data = other.data();
        var matches = new ArrayList<Match>();
        for (int i = 0; i < referencePoints.size(); i++) {
            var referencePoint = referencePoints.get(i);
            try {
                var x = referencePoint.a();
                var y = referencePoint.b();
                var bestMatch = Stacking.findBestMatch(referenceData, data, width, height, tileSize, x, y, maxLookupShift);
                if (bestMatch.isPresent()) {
                    var second = bestMatch.get();
                    var sy = second.y();
                    var sx = second.x();
                    if (isValidSample(other, sx, sy, tileSize, threshold)) {
                        var referenceAvg = imageMath.areaAverage(referenceIntegral, x, y, tileSize, tileSize);
                        var otherAvg = imageMath.areaAverage(otherIntegral, sx, sy, tileSize, tileSize);
                        if (withinTolerance(otherAvg, referenceAvg, 0.25)) {
                            matches.add(new Match(
                                referencePoint,
                                new IntPair(sx, sy),
                                second.score()
                            ));
                        }
                    }
                }
            } finally {
                broadcaster.broadcast(ProgressEvent.of((i + 1) / (double) referencePoints.size(), FINDING_MATCHES_MESSAGE));
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Checks that both values are within a relative tolerance of each other.
     *
     * @param value the first value
     * @param value2 the second value
     * @param relativeTolerance the tolerance (between 0 and 1)
     * @return {@code true} if the values are within the tolerance, {@code false} otherwise
     */
    private static boolean withinTolerance(double value, double value2, double relativeTolerance) {
        return Math.abs(value - value2) <= Math.max(Math.abs(value), Math.abs(value2)) * relativeTolerance;
    }

    /**
     * All images must have similar histograms (i.e similar brightness) in order to stitch
     * them together without artifacts. This method will normalize the histograms of the
     * images by adjusting the pixel values using the cumulative distribution function.
     *
     * @param images the images to normalize
     * @return the normalized images and an estimate of the background value of the normalized images
     */
    private HistogramNormalization normalizeHistograms(List<ImageWrapper32> images) {
        record ImageWithBackground(ImageWrapper32 image, float background) {
            public int pixelsAboveBackground() {
                var data = image.data();
                var count = 0;
                for (float datum : data) {
                    if (datum > background) {
                        count++;
                    }
                }
                return count;
            }
        }
        broadcaster.broadcast(ProgressEvent.of(0, message("normalizing.histograms")));
        try {
            List<ImageWrapper32> adjusted = new ArrayList<>(images);
            int trials = 8;
            while (--trials > 0) {
                float maxAvg = 0f;
                float[] avgs = new float[adjusted.size()];
                for (int i = 0; i < adjusted.size(); i++) {
                    var img = adjusted.get(i);
                    var signal = AnalysisUtils.estimateSignalLevel(img.data(), 64);
                    avgs[i] = averageOfPixelsAbove(img, signal);
                    maxAvg = Math.max(avgs[i], maxAvg);
                }

                // Adjust each image based on the overall average signal level
                for (int i = 0; i < adjusted.size(); i++) {
                    var img = adjusted.get(i);
                    var v = avgs[i];
                    if (!withinTolerance(v, maxAvg, 0.05)) {
                        LOGGER.debug("Signal levels are not within tolerance, adjusting...");
                        adjusted.set(i, adjust(img, maxAvg, v));
                    }
                }
            }

            var backgrounds = adjusted.stream()
                .parallel()
                .map(img -> new ImageWithBackground(img, AnalysisUtils.estimateBackgroundLevel(img.data())))
                .toList();
            adjusted = backgrounds.stream()
                .sorted(Comparator.comparingInt(ImageWithBackground::pixelsAboveBackground).reversed())
                .map(ImageWithBackground::image)
                .collect(Collectors.toCollection(ArrayList::new));
            var averageBackground = backgrounds.stream()
                .mapToDouble(ImageWithBackground::background)
                .average()
                .orElse(0);
            var background = (float) (0.8 * averageBackground);
            return new HistogramNormalization(adjusted, background);
        } finally {
            broadcaster.broadcast(ProgressEvent.of(1.0, message("normalizing.histograms")));
        }
    }

    private static ImageWrapper32 adjust(ImageWrapper32 image, float target, float cur) {
        var correction = target / cur;
        var result = image.copy();
        if (correction != 1) {
            var data = result.data();
            for (int j = 0; j < data.length; j++) {
                data[j] = Math.max(0, Math.min(Constants.MAX_PIXEL_VALUE, data[j] * correction));
            }
        }
        return result;
    }

    private static float averageOfPixelsAbove(ImageWrapper32 image, float threshold) {
        var data = image.data();
        var total = 0d;
        var count = 0;
        for (float datum : data) {
            // only count pixels which are above the threshold and not saturated
            if (datum > threshold && datum < 60000) {
                total += datum;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return (float) (total / count);
    }

    private static float[] assembleImage(float[][] stacks, int width, int height, float threshold) {
        float[] result = new float[stacks.length];
        var minValueNonZero = Float.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var pixelIndex = y * width + x;
                float[] stack = stacks[pixelIndex];
                if (stack[0] >= threshold && stack[1] >= threshold) {
                    if (withinTolerance(stack[0], stack[1], 0.5)) {
                        result[pixelIndex] = stack[0];
                    } else {
                        result[pixelIndex] = Math.max(stack[0], stack[1]);
                    }
                } else if (stack[0] >= threshold) {
                    result[pixelIndex] = stack[0];
                } else if (stack[1] >= threshold) {
                    result[pixelIndex] = stack[1];
                } else {
                    result[pixelIndex] = (stack[0] + stack[1]) / 2;
                }
                if (result[pixelIndex] > 0 && result[pixelIndex] < minValueNonZero) {
                    minValueNonZero = result[pixelIndex];
                }
            }
        }
        // Avoid clamping by replacing 0 values with the minimum non-zero value
        for (int pixelIndex = 0; pixelIndex < stacks.length; pixelIndex++) {
            if (result[pixelIndex] == 0) {
                result[pixelIndex] = minValueNonZero;
            }
        }
        return result;
    }

    private void maybeCreateMatchDebugImage(List<ImageWrapper32> corrected, int width, int height, List<DistorsionSample> candidates, int tileSize) {
        if (DEBUG_IMAGES == null) {
            return;
        }
        int cpt = DEBUG_COUNTER.incrementAndGet();
        int k = 0;
        for (var candidate : candidates) {
            var idx = k++;
            forkJoinContext.async(() -> {
                var bi = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_RGB);
                var g = bi.createGraphics();
                for (int i = 0; i < 2; i++) {
                    var img = corrected.get(i).data();
                    var offset = width * i;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int grey = (int) img[y * width + x] >> 8;
                            int rgb = grey << 16 | grey << 8 | grey;
                            bi.setRGB(x + offset, y, rgb);
                        }
                    }
                }
                g.setColor(Color.WHITE);
                g.setFont(g.getFont().deriveFont(60f));
                g.drawString("Tile size %d".formatted(tileSize), (width / 2), 30);
                int fx = (int) candidate.source().x();
                int fy = (int) candidate.source().y();
                int sx = (int) candidate.target().x();
                int sy = (int) candidate.target().y();
                g.drawLine(fx, fy, sx + width, sy);
                g.setColor(Color.ORANGE);
                g.drawRect(fx, fy, tileSize, tileSize);
                g.drawRect(sx + width, sy, tileSize, tileSize);
                g.setFont(g.getFont().deriveFont(30f));
                g.drawString("(" + fx + "," + fy + ")", fx - 40, fy - 50);
                g.drawString("(%d,%d score %.1f)".formatted(sx, sy, candidate.score()), sx + width - 40, sy - 50);
                try {
                    new File(DEBUG_IMAGES + "/" + cpt).mkdirs();
                    ImageIO.write(bi, "png", new File(String.format(DEBUG_IMAGES + "/%d/%04d_match.png", cpt, idx)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        var tmp = new float[2 * width * height];
        for (int i = 0; i < 2; i++) {
            var img = corrected.get(i).data();
            var offset = width * i;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    tmp[y * 2 * width + x + offset] = img[y * width + x];
                }
            }
        }
        for (var candidate : candidates) {
            int fx = (int) candidate.source().x();
            int fy = (int) candidate.source().y();
            int sx = (int) candidate.target().x();
            int sy = (int) candidate.target().y();
            DrawUtils.drawLine(tmp, 2 * width, height, fx, fy, sx + width, sy, 1);
        }
        broadcaster.broadcast(new ImageGeneratedEvent(
            new GeneratedImage(
                GeneratedImageKind.IMAGE_MATH,
                "match" + System.currentTimeMillis(),
                Path.of(DEBUG_IMAGES + "/match-" + System.currentTimeMillis() + ".png"),
                new ImageWrapper32(width * 2, height, tmp, MutableMap.of())
            )
        ));
    }

    private record HistogramNormalization(
        List<ImageWrapper32> corrected,
        float background
    ) {

    }

    private record DistorsionSample(
        Point2D source,
        Point2D target,
        double score
    ) {

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

    private record Match(
        IntPair first,
        IntPair second,
        double score
    ) {

    }

    private record TileOverlap(
        int image1,
        int image2,
        int overlappingTiles
    ) {
    }

    private record DistorsionModel(
        DoublePair modelForX,
        DoublePair modelForY
    ) {
        public static DistorsionModel UNDISTORTED = new DistorsionModel(
            new DoublePair(1, 0),
            new DoublePair(1, 0)
        );

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

    private record LocalModel(
        Point2D point,
        DistorsionModel model
    ) {
        public double distanceTo(Point2D point) {
            return this.point.distanceTo(point);
        }
    }
}
