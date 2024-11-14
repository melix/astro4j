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
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.DrawUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
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

import static me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils.estimateBackgroundLevel;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.math.regression.LinearRegression.firstOrderRegression;

public class MosaicComposition extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosaicComposition.class);
    private static final String FINDING_MATCHES_MESSAGE = message("finding.matches");
    private static final AtomicInteger DEBUG_COUNTER = new AtomicInteger();
    private static final String DEBUG_IMAGES = System.getProperty("jsolex.debug.images");
    public static final int DEFAULT_TILE_SIZE = 64;
    public static final float DEFAULT_OVERLAP_FACTOR = 0.3f;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Broadcaster broadcaster;
    private final Stacking stacking;
    private final EllipseFit ellipseFit;
    private final Scaling scaling;

    public MosaicComposition(Map<Class<?>, Object> context, Broadcaster broadcaster, Stacking stacking, EllipseFit ellipseFit, Scaling scaling) {
        super(context, broadcaster);
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
                return images.get(0);
            }
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
        var backgroundNeutralized = images.stream().map(BackgroundRemoval::neutralizeBackground).toList();
        var rescaled = scaling.performRadiusRescale(backgroundNeutralized)
            .stream()
            .map(ImageWrapper::unwrapToMemory)
            .map(ImageWrapper32.class::cast).toList();
        var adjustment = normalizeHistograms(rescaled);
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
            var distorsionGridSize = Math.max(width / 64, 64);
            LOGGER.debug("Threshold: {}", background);
            computeMissingState(distorsionGridSize, corrected, background, imageToTilesOverbackground, integralImages);
            var tileOverlap = placeMostOverlappingImagesFirst(corrected, imageToTilesOverbackground, imageCount);
            if (tileOverlap != null && tileOverlap.overlappingTiles() == 0) {
                LOGGER.warn("Cannot find overlapping tiles between images, falling back to addition");
                return (ImageWrapper32) new SimpleFunctionCall(Map.of(), broadcaster).applyFunction("max", List.of(corrected), DoubleStream::max);
            }
            int maxSteps = 2 * (height / distorsionGridSize);
            int step = 0;
            boolean[] mask = new boolean[width * height];
            Map<Point2D, Optional<DistorsionSample>> cachedInterestPoints = new ConcurrentHashMap<>();
            first = corrected.get(0);
            var second = corrected.get(1);
            var threshold = background;
            int noUpdateCount = 0;
            while (true) {
                step++;
                var reference = corrected.get(0);
                var compare = corrected.get(1);
                var referenceIntegral = imageMath.integralImage(reference.asImage());
                var compareIntegral = integralImages.get(compare);
                var referenceData = reference.data();
                var feat = findInterestPoints(reference, referenceIntegral, compareIntegral, threshold, distorsionGridSize);
                var length = width * height;
                var otherData = compare.data();
                var assembledData = ImageWrapper.copyData(referenceData);
                boolean updated = false;
                Map<Point2D, List<Point2D>> localInterestPoints = new HashMap<>();
                var offset = distorsionGridSize;
                var dist = distorsionGridSize;
                int window = distorsionGridSize / 2;
                for (int y = 0; y < height; y += offset) {
                    for (int x = 0; x < width; x += offset) {
                        if (!isTileCompleted(tileSize, y, width, x, length, mask)) {
                            var avg1 = imageMath.areaAverage(referenceIntegral, x - window, y - window, 2 * window, 2 * window);
                            var avg2 = imageMath.areaAverage(compareIntegral, x - window, y - window, 2 * window, 2 * window);
                            int px = distorsionGridSize * (x / distorsionGridSize);
                            int py = distorsionGridSize * (y / distorsionGridSize);
                            var localPoints = localInterestPoints.computeIfAbsent(new Point2D(px, py), p -> feat.stream().parallel().filter(f -> f.distanceTo(p) <= 2 * dist).toList());
                            if (avg1 == 0 && avg2 == 0) {
                                assembleSingleTile(distorsionGridSize, x, y, x, y, width, length, assembledData, otherData, mask);
                            } else {
                                var point = new Point2D(x, y);
                                var samples = findBestMatches(cachedInterestPoints, reference, referenceIntegral, localPoints, compare, compareIntegral, distorsionGridSize, step, x, y, dist);
                                if (!samples.isEmpty()) {
                                    var restrictedSamples = samples.stream().filter(s -> s.distanceTo(point) <= dist).toList();
                                    var avgError = restrictedSamples.stream().mapToDouble(DistorsionSample::error).average().orElse(0);
                                    restrictedSamples = restrictedSamples.stream().filter(s -> s.error() <= 1.5 * avgError).toList();
                                    if (!restrictedSamples.isEmpty()) {
                                        var model = buildDistorsionModel(restrictedSamples);
                                        int newX = (int) Math.round(model.modelForX().asPolynomial().applyAsDouble(point.x()));
                                        int newY = (int) Math.round(model.modelForY().asPolynomial().applyAsDouble(point.y()));
                                        updated = assembleSingleTile(distorsionGridSize, x, y, newX, newY, width, length, assembledData, otherData, mask);
                                    }
                                }
                            }
                        }
                    }
                }
                var metadata = MutableMap.<Class<?>, Object>of();
                for (var image : corrected) {
                    metadata.putAll(image.metadata());
                }
                var assembled = new ImageWrapper32(width, height, assembledData, metadata);
                corrected.set(0, assembled);
                boolean reassembled = true;
                for (boolean v : mask) {
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
                    corrected.remove(1);
                    var mosaic = stacking.doStack(List.of(first, second), tileSize, overlap, assembled, 0);
                    corrected.set(0, mosaic);
                    break;
                }
            }
        }

        return corrected.get(0);
    }

    private static boolean assembleSingleTile(int tileSize, int x, int y, int newX, int newY, int width, int length, float[][] assembledData, float[][] otherData, boolean[] mask) {
        var updated = false;
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                int idx = (newY + dy) * width + (newX + dx);
                var origIdx = (y + dy) * width + (x + dx);
                if (idx >= 0 && idx < length && origIdx >= 0 && origIdx < length) {
                    var source = assembledData[y + dy][x + dx];
                    var other = otherData[newY + dy][newX + dx];
                    updated = true;
                    if (source < 0.5 * other) {
                        assembledData[y + dy][x + dx] = other;
                    }
                    mask[origIdx] = true;
                }
            }
        }
        return updated;
    }

    private static boolean isTileCompleted(int tileSize, int y, int width, int x, int length, boolean[] mask) {
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                int idx = (y + dy) * width + (x + dx);
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
        var modelForX = buildModel(sortedSamples, DistorsionSample::sampleForX);
        var modelForY = buildModel(sortedSamples, DistorsionSample::sampleForY);
        var maxScore = sortedSamples.get(sortedSamples.size() - 1).error();
        var weights = sortedSamples.stream().mapToDouble(s -> (1 - (s.error() / maxScore))).map(d -> d * d).toArray();
        var regressionForX = firstOrderRegression(modelForX.toArray(new Point2D[0]), weights);
        var regressionForY = firstOrderRegression(modelForY.toArray(new Point2D[0]), weights);
        var model = new DistorsionModel(regressionForX, regressionForY);
        if (model.isValid()) {
            return model;
        } else {
            return DistorsionModel.NO_DISTORSION;
        }
    }

    private static List<Point2D> buildModel(List<DistorsionSample> samples, Function<DistorsionSample, Point2D> extractor) {
        return samples.stream().map(extractor).toList();
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
            corrected.stream().parallel().peek(img -> broadcaster.broadcast(ProgressEvent.of(count.getAndIncrement() / (double) totalOperations, message("computing.integral.images")))).filter(img -> !integralImages.containsKey(img))
                .map(img -> new Object() {
                    final ImageWrapper32 image = img;
                    final Image integral = imageMath.integralImage(img.asImage());
                }).forEachOrdered(o -> integralImages.put(o.image, o.integral));
            // add tiles over background to map
            corrected.stream().parallel().peek(img -> broadcaster.broadcast(ProgressEvent.of(count.getAndIncrement() / (double) totalOperations, message("computing.integral.images")))).filter(img -> !imageToTilesOverbackground.containsKey(img))
                .map(img -> new Object() {
                    final ImageWrapper32 image = img;
                    final Set<Integer> tiles = computeTilesOverBackground(img, integralImages.get(img), background, tileSize);
                }).forEachOrdered(o -> imageToTilesOverbackground.put(o.image, o.tiles));
        } finally {
            broadcaster.broadcast(ProgressEvent.of(1.0, message("computing.integral.images")));
        }
    }

    private List<Point2D> findInterestPoints(ImageWrapper32 image, Image integralImage, Image compareIntegral, float threshold, int tileSize) {
        var width = image.width();
        var height = image.height();
        var result = new ArrayList<Point2D>();
        int sampleInterval = Math.max(16, tileSize / 2);
        for (int y = 0; y < height; y += sampleInterval) {
            int i = 0;
            for (int x = 0; x < width; x += sampleInterval) {
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
        broadcaster.broadcast(ProgressEvent.of(0, FINDING_MATCHES_MESSAGE.formatted(step)));
        var width = reference.width();
        var height = reference.height();
        var referenceData = reference.data();
        var maxLookupShift = tileSize / 2;
        var data = other.data();
        var maxDistSquared = maxDist * maxDist;
        return referencePoints.stream().parallel().<DistorsionSample>mapMulti((referencePoint, consumer) -> {
            int x = (int) referencePoint.x();
            int y = (int) referencePoint.y();
            var dx = x - refX;
            var dy = y - refY;
            if (dx * dx + dy * dy > maxDistSquared) {
                return;
            }
            var distorsionSample = cache.computeIfAbsent(referencePoint, p -> {
                var bestMatch = Stacking.findBestMatch(referenceData, data, width, height, tileSize / 2, x, y, maxLookupShift);
                if (bestMatch.isPresent()) {
                    var second = bestMatch.get();
                    var sy = second.y();
                    var sx = second.x();
                    var referenceAvg = imageMath.areaAverage(referenceIntegral, x, y, tileSize, tileSize);
                    var otherAvg = imageMath.areaAverage(otherIntegral, sx, sy, tileSize, tileSize);
                    if (withinTolerance(otherAvg, referenceAvg, 0.25)) {
                        return Optional.of(new DistorsionSample(referencePoint, new Point2D(sx, sy), second.score()));
                    }
                }
                return Optional.empty();
            });
            distorsionSample.ifPresent(consumer);
        }).sorted(Comparator.comparingDouble(DistorsionSample::error)).toList();
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
                for (float[] line : data) {
                    for (float v : line) {
                        if (v > background) {
                            count++;
                        }
                    }
                }
                return count;
            }
        }
        broadcaster.broadcast(ProgressEvent.of(0, message("normalizing.histograms")));
        try {
            List<ImageWrapper32> adjusted = new ArrayList<>(images);
            var nImages = adjusted.size();
            int trials = 8;
            while (--trials > 0) {
                float maxAvg = 0f;
                float[] avgs = new float[nImages];
                for (int i = 0; i < nImages; i++) {
                    var img = adjusted.get(i);
                    var signal = estimateBackgroundLevel(img.data(), 64);
                    avgs[i] = averageOfPixelsAbove(img, signal);
                    maxAvg = Math.max(avgs[i], maxAvg);
                }

                // Adjust each image based on the overall average signal level
                for (int i = 0; i < nImages; i++) {
                    var img = adjusted.get(i);
                    var v = avgs[i];
                    if (!withinTolerance(v, maxAvg, 0.01)) {
                        LOGGER.debug("Signal levels are not within tolerance, adjusting...");
                        adjusted.set(i, adjust(img, maxAvg, v));
                    }
                }
            }

            var backgrounds = adjusted.stream().parallel().map(img -> new ImageWithBackground(img, estimateBackgroundLevel(img.data()))).toList();
            adjusted = backgrounds.stream().sorted(Comparator.comparingInt(ImageWithBackground::pixelsAboveBackground).reversed()).map(ImageWithBackground::image).collect(Collectors.toCollection(ArrayList::new));
            var averageBackground = backgrounds.stream().mapToDouble(ImageWithBackground::background).average().orElse(0);
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
            var height = result.height();
            var width = result.width();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
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
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                // only count pixels which are above the threshold and not saturated
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
        int cpt = DEBUG_COUNTER.incrementAndGet();
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
                for (int i = 0; i < 2; i++) {
                    var img = corrected.get(i).data();
                    var offset = width * i;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int grey = (int) img[y][x] >> 8;
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
                g.drawString("(%d,%d score %.1f)".formatted(sx, sy, candidate.error()), sx + width - 40, sy - 50);
                try {
                    new File(DEBUG_IMAGES + "/" + cpt).mkdirs();
                    ImageIO.write(bi, "png", new File(String.format(DEBUG_IMAGES + "/%d/%04d_match.png", cpt, idx)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        var tmp = new float[2 * height][width];
        for (int i = 0; i < 2; i++) {
            var img = corrected.get(i).data();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    tmp[y * 2 + i][x] = img[y][x];
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
            new GeneratedImage(GeneratedImageKind.IMAGE_MATH, "match" + System.currentTimeMillis(), Path.of(DEBUG_IMAGES + "/match-" + System.currentTimeMillis() + ".png"), new ImageWrapper32(width * 2, height, tmp, MutableMap.of()))));
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

    private record Match(IntPair first, IntPair second, double score) {

    }

    private record TileOverlap(int image1, int image2, int overlappingTiles) {
    }

    private record DistorsionModel(DoublePair modelForX, DoublePair modelForY) {
        public static DistorsionModel NO_DISTORSION = new DistorsionModel(new DoublePair(1, 0), new DoublePair(1, 0));

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
