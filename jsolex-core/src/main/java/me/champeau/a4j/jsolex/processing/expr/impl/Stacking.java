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
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoublePair;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolatingFunction;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolator;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.GradientPaint;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Stacking extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Stacking.class);

    private static final String STACKING_MESSAGE = message("stacking");
    private static final String FIND_CORRESP_MESSAGE = message("finding.correspondances");
    public static final int DEFAULT_TILE_SIZE = 32;
    public static final float DEFAULT_SAMPLING_RATIO = .5f;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Scaling scaling;
    private final Crop crop;
    private final SimpleFunctionCall simpleFunctionCall;
    private final ImageDraw imageDraw;
    private final Broadcaster broadcaster;

    public Stacking(Map<Class<?>, Object> context,
                    Scaling scaling,
                    Crop crop,
                    SimpleFunctionCall simpleFunctionCall,
                    ImageDraw imageDraw,
                    Broadcaster broadcaster) {
        super(context, broadcaster);
        this.scaling = scaling;
        this.crop = crop;
        this.simpleFunctionCall = simpleFunctionCall;
        this.imageDraw = imageDraw;
        this.broadcaster = broadcaster;
    }

    public ImageWrapper32 stack(List<ImageWrapper32> images, int tileSize, float sampling, ReferenceSelection referenceSelection) {
        if (images.size() == 1) {
            return images.get(0);
        }
        return doStack(images, tileSize, null, sampling, referenceSelection);
    }

    public Object stack(List<Object> arguments) {
        assertExpectedArgCount(arguments, "stack takes 1, to 4 arguments (image(s), [tile size], [sampling], [reference selection])", 1, 4);
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
            if (tileSize < 4) {
                throw new IllegalArgumentException("tile size must be at least 4");
            }
            if (!FFTSupport.isPowerOf2(tileSize)) {
                throw new IllegalArgumentException("tile size must be a power of 2");
            }
            var sampling = arguments.size() == 3 ? doubleArg(arguments, 2) : DEFAULT_SAMPLING_RATIO;
            var referenceSelection = arguments.size() == 4 ? ReferenceSelection.valueOf(stringArg(arguments, 3).toUpperCase(Locale.US)) : ReferenceSelection.SHARPNESS;
            return doStack(images, tileSize, null, sampling, referenceSelection);
        } else {
            throw new IllegalArgumentException("stack first argument must be a list of images");
        }
    }

    ImageWrapper32 doStack(List<ImageWrapper32> images,
                           int tileSize,
                           ImageWrapper32 referenceImage,
                           double sampling,
                           ReferenceSelection referenceSelection) {
        var widths = images.stream().mapToInt(ImageWrapper::width).distinct().toArray();
        var heights = images.stream().mapToInt(ImageWrapper::height).distinct().toArray();
        var preparationResult = measure(() -> prepareForStacking(images, widths, heights));
        var sourceImages = preparationResult.result;
        // the reference image is the first image
        // We perform stacking by doing the following:
        // for each image to stack, we split the image into tiles of size tileSize
        // then for each tile of the reference image, we're going to compute the average
        // error between the reference tile and the tile of the image to stack. However,
        // instead of simply using ONE tile for the image to stack, we're going to try
        // shifting the tile between -tileSize/4 and +tileSize/4 in both directions, to
        // select the tile which has the lowest error.
        var first = sourceImages.iterator().next();
        var height = first.height();
        var width = first.width();
        ImageWrapper32 reference = null;
        var weights = new double[sourceImages.size()];
        Arrays.fill(weights, -1);
        if (referenceImage == null) {
            Arrays.fill(weights, 1);
            reference = computeReferenceImageAndAdjustWeights(sourceImages, referenceSelection, weights);
        }
        var referenceData = referenceImage != null ? referenceImage.data() : reference.data();
        var signal = 0.2f * AnalysisUtils.estimateBackgroundLevel(referenceData);
        var imageCount = sourceImages.size();
        var increment = (int) Math.max(2, tileSize * sampling);
        var distorsions = new DistorsionMap[imageCount];
        for (int i = 0; i < distorsions.length; i++) {
            distorsions[i] = new DistorsionMap(width, height, tileSize, increment);
        }
        AtomicInteger progressCounter = new AtomicInteger();
        var displacementResult = measure(() -> {
            IntStream.iterate(0, y -> y < height, y -> y + increment)
                .parallel()
                .forEach(y -> {
                    for (int x = 0; x < width; x += increment) {
                        findDisplacement(sourceImages, tileSize, x, width, y, height, referenceData, distorsions, signal);
                    }
                    var progress = progressCounter.addAndGet(increment) / (double) height;
                    broadcaster.broadcast(ProgressEvent.of(progress, FIND_CORRESP_MESSAGE));
                });
            broadcaster.broadcast(ProgressEvent.of(1.0, FIND_CORRESP_MESSAGE));
            return null;
        });
        var metadata = MutableMap.<Class<?>, Object>of();
        for (ImageWrapper32 image : sourceImages) {
            metadata.putAll(image.metadata());
        }
        var interpolationResult = measure(() -> {
            broadcaster.broadcast(ProgressEvent.of(0.0, message("interpolating.models")));
            progressCounter.set(0);
            Arrays.stream(distorsions).parallel().forEach(distorsionMap -> {
                var progress = progressCounter.getAndIncrement() / (double) imageCount;
                broadcaster.broadcast(ProgressEvent.of(progress, message("interpolating.models")));
                distorsionMap.computeInterpolators();
            });
            broadcaster.broadcast(ProgressEvent.of(1.0, message("interpolating.models")));
            return null;
        });
        ProcessParams params = (ProcessParams) metadata.get(ProcessParams.class);
        boolean generateDebugImages = false;
        if (params == null) {
            params = (ProcessParams) context.get(ProcessParams.class);
            if (params == null) {
                params = ProcessParams.loadDefaults();
            }
            if (params.requestedImages().isEnabled(GeneratedImageKind.DEBUG)) {
                generateDebugImages = true;
            }
        }
        var dedistorted = generateDebugImages ? new float[sourceImages.size()][height][width] : null;
        var finalImage = assembleImage(sourceImages, weights, distorsions, width, height, tileSize, referenceImage, dedistorted);
        var stacked = new ImageWrapper32(width, height, finalImage, metadata);
        if (generateDebugImages) {
            createDebugImages(new ImageWrapper32(width, height, referenceData, Map.of()), stacked, distorsions, sourceImages, tileSize, increment, dedistorted, referenceSelection);
        }
        LOGGER.debug("Preparation took {}ms", preparationResult.duration.toMillis());
        LOGGER.debug("Finding displacements took {}ms", displacementResult.duration.toMillis());
        LOGGER.debug("Interpolating models took {}ms", interpolationResult.duration.toMillis());
        return stacked;
    }

    private void createDebugImages(ImageWrapper32 referenceImage,
                                   ImageWrapper32 stacked,
                                   DistorsionMap[] distorsions,
                                   List<ImageWrapper32> images,
                                   int tileSize,
                                   int increment,
                                   float[][][] dedistorted,
                                   ReferenceSelection referenceSelection) {
        var progress = new AtomicInteger(0);
        IntStream.range(0, images.size())
            .parallel()
            .forEach(i -> {
                createDebugImage(referenceImage, stacked, distorsions[i], images.get(i), tileSize, increment, i, dedistorted[i], referenceSelection);
                var pg = (double) progress.incrementAndGet() / images.size();
                broadcaster.broadcast(ProgressEvent.of(pg, "Generating stacking debug images"));
            });
        broadcaster.broadcast(ProgressEvent.of(1.0, "Generating stacking debug images"));
    }

    private void createDebugImage(ImageWrapper32 referenceImage, ImageWrapper32 stacked, DistorsionMap distorsion, ImageWrapper32 original, int tileSize, int increment, int index, float[][] dedistorted, ReferenceSelection referenceSelection) {
        var width = stacked.width();
        var height = stacked.height();
        var separator = 20 + 10 * width / 100;
        var heatmap = new float[3][2 * height + 3 * separator][4 * width + 5 * separator];
        float[][] diffImage = new float[height][width];
        float minDiff = Float.POSITIVE_INFINITY;
        float maxDiff = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                diffImage[y][x] = original.data()[y][x] - dedistorted[y][x];
                minDiff = Math.min(minDiff, diffImage[y][x]);
                maxDiff = Math.max(maxDiff, diffImage[y][x]);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                diffImage[y][x] += Math.abs(minDiff);
            }
        }
        double maxAmplitude = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < stacked.width(); x++) {
                var displacement = distorsion.findDistorsion(x, y);
                var dx = displacement.dx();
                var dy = displacement.dy();
                if (Double.isFinite(dx) && Double.isFinite(dy)) {
                    maxAmplitude = Math.max(maxAmplitude, Math.abs(dx));
                    maxAmplitude = Math.max(maxAmplitude, Math.abs(dy));
                }
            }
        }
        for (float[][] data : heatmap) {
            for (float[] line : data) {
                Arrays.fill(line, MAX_PIXEL_VALUE);
            }
        }
        for (int y = 0; y < height; y++) {
            var displayY = y + separator;
            for (int x = 0; x < stacked.width(); x++) {
                var displacement = distorsion.findDistorsion(x, y);
                var dx = displacement.dx();
                var dy = displacement.dy();
                var displayX = width + x + 2 * separator;
                if (Double.isFinite(dx) && Double.isFinite(dy)) {
                    var halfADU = MAX_PIXEL_VALUE / 2;
                    var dxADU = (float) Math.abs(dx / maxAmplitude) * halfADU;
                    var dyADU = (float) Math.abs(dy / maxAmplitude) * halfADU;
                    if (dx < 0) {
                        heatmap[0][displayY][displayX] = halfADU + dxADU;
                        heatmap[1][displayY][displayX] = halfADU;
                        heatmap[2][displayY][displayX] = halfADU;
                    } else if (dx > 0) {
                        heatmap[0][displayY][displayX] = halfADU;
                        heatmap[1][displayY][displayX] = halfADU;
                        heatmap[2][displayY][displayX] = halfADU + dxADU;
                    }
                    displayX += width + separator;
                    if (dy < 0) {
                        heatmap[0][displayY][displayX] = halfADU + dyADU;
                        heatmap[1][displayY][displayX] = halfADU;
                        heatmap[2][displayY][displayX] = halfADU;
                    } else if (dy > 0) {
                        heatmap[0][displayY][displayX] = halfADU;
                        heatmap[1][displayY][displayX] = halfADU;
                        heatmap[2][displayY][displayX] = halfADU + dyADU;
                    }
                    displayX += width + separator;
                    var amplitude = (float) (MAX_PIXEL_VALUE * Math.sqrt(dx * dx + dy * dy) / maxAmplitude);
                    heatmap[0][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                    heatmap[1][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                    heatmap[2][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                }
            }
            displayY += height + separator;
            for (int x = 0; x < stacked.width(); x++) {
                var displayX = x + separator;
                var reference = referenceImage.data()[y][x];
                heatmap[0][displayY][displayX] = reference;
                heatmap[1][displayY][displayX] = reference;
                heatmap[2][displayY][displayX] = reference;
                displayX += width + separator;
                var orig = original.data()[y][x];
                heatmap[0][displayY][displayX] = orig;
                heatmap[1][displayY][displayX] = orig;
                heatmap[2][displayY][displayX] = orig;
                displayX += width + separator;
                var dedist = dedistorted[y][x];
                heatmap[0][displayY][displayX] = dedist;
                heatmap[1][displayY][displayX] = dedist;
                heatmap[2][displayY][displayX] = dedist;
                displayX += width + separator;
                var diff = diffImage[y][x];
                heatmap[0][displayY][displayX] = diff;
                heatmap[1][displayY][displayX] = diff;
                heatmap[2][displayY][displayX] = diff;
            }
        }
        double finalMaxAmplitude = maxAmplitude;
        var image = scaling.relativeRescale(List.of(imageDraw.drawOnImage(new RGBImage(heatmap[0][0].length, heatmap[0].length, heatmap[0], heatmap[1], heatmap[2], MutableMap.of()), (g, img) -> {
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(16f * (height / 384f)));
            g.drawString("Displacement X", 2 * separator + width + width / 6, height + 1.5f * separator);
            g.drawString("Displacement Y", 3 * separator + 2 * width + width / 6, height + 1.5f * separator);
            g.drawString("Amplitude", 4 * separator + 3 * width + width / 6, height + 1.5f * separator);
            g.drawString("Reference image", separator + width / 6, 2 * height + 2.5f * separator);
            g.drawString("Distorted image", 2 * separator + width + width / 6, 2 * height + 2.5f * separator);
            g.drawString("Corrected image", 3 * separator + 2 * width + width / 6, 2 * height + 2.5f * separator);
            g.drawString("Difference", 4 * separator + 3 * width + width / 6, 2 * height + 2.5f * separator);
            original.findMetadata(MetadataTable.class).ifPresent(metadata -> {
                metadata.get(MetadataTable.FILE_NAME).ifPresent(name -> {
                    g.drawString("Source file : " + name, separator, separator);
                });
            });
            g.drawString("Tile size: " + tileSize + "px", separator, separator + 60);
            g.drawString("Sample every: " + increment + "px", separator, separator + 120);
            g.drawString("Maximal amplitude: " + Math.round(finalMaxAmplitude) + "px", separator, separator + 180);
            g.drawString("Reference selection method: " + referenceSelection, separator, separator + 240);
            // draw displacement scale between the two displacement images
            g.setColor(Color.BLACK);
            var middleLegend = (int) (2 * width + 2.5 * separator);
            g.drawLine(middleLegend, separator, middleLegend, height + separator);
            g.setFont(g.getFont().deriveFont(12f * (height / 384f)));
            var gradient = new GradientPaint(middleLegend - 20, separator, new Color(255, 128, 128),
                middleLegend - 20, separator + height / 2, new Color(128, 128, 128));
            g.setPaint(gradient);
            g.fillRect(middleLegend - 40, separator, 40, height / 2);
            gradient = new GradientPaint(middleLegend - 20, separator + height / 2, new Color(128, 128, 128),
                middleLegend - 20, separator + height, new Color(128, 128, 255));
            g.setPaint(gradient);
            g.fillRect(middleLegend - 40, separator + height / 2, 40, height / 2);
            g.setPaint(Color.BLACK);
            for (int i = -10; i <= 10; i += 2) {
                int y = height / 2 + separator + (i * height / 20);
                g.drawLine(middleLegend - 20, y, middleLegend + 20, y);
                double pixels = -i * finalMaxAmplitude / 10;
                g.drawString(String.format(Locale.US, "%.2fpx", pixels), (int) (2 * width + 2.5 * separator) + 15, y);
            }

        }), .5, .5));
        broadcaster.broadcast(new ImageGeneratedEvent(
            new GeneratedImage(
                GeneratedImageKind.DEBUG,
                "displacementXY_" + index,
                Path.of("displacement_" + index + ".png"),
                (ImageWrapper) image
            )
        ));
    }

    private ImageWrapper32 computeReferenceImageAndAdjustWeights(List<ImageWrapper32> images, ReferenceSelection referenceSelection, double[] weights) {
        return switch (referenceSelection) {
            case FIRST -> images.getFirst();
            case AVERAGE -> (ImageWrapper32) simpleFunctionCall.applyFunction("average", (List) images, DoubleStream::average);
            case MEDIAN -> (ImageWrapper32) simpleFunctionCall.applyFunction("median", (List) images, AbstractImageExpressionEvaluator::median);
            case ECCENTRICITY -> images.stream()
                .map(img -> {
                    var ecc = img.findMetadata(Ellipse.class).map(Ellipse::eccentricity).orElse(.99d);
                    return new Object() {
                        private final double eccentricity = ecc;
                        private final ImageWrapper32 image = img;
                    };
                })
                .min(Comparator.comparingDouble(a -> a.eccentricity))
                .map(o -> o.image)
                .orElseThrow();
            case SHARPNESS -> IntStream.range(0, images.size())
                .parallel()
                .mapToObj(i -> {
                    var img = images.get(i);
                    var v = imageMath.estimateSharpness(img.asImage());
                    weights[i] = v;
                    return new Object() {
                        private final double sharpness = v;
                        private final ImageWrapper32 image = img;
                    };
                })
                .max(Comparator.comparingDouble(a -> a.sharpness))
                .map(o -> o.image)
                .orElseThrow();
        };
    }

    private List<ImageWrapper32> prepareForStacking(List<ImageWrapper32> images, int[] widths, int[] heights) {
        List<ImageWrapper> images2 = images.stream().map(ImageWrapper.class::cast).toList();
        if (widths.length > 1 || heights.length > 1 || widths[0] != heights[0]) {
            // perform cropping
            images2 = (List<ImageWrapper>) crop.autocrop2(List.of(images2));
        }
        images2 = (List<ImageWrapper>) scaling.radiusRescale(List.of(images2));
        return images2.stream()
            .map(img -> {
                if (img instanceof FileBackedImage fbi) {
                    return fbi.unwrapToMemory();
                } else if (img instanceof ImageWrapper32 img32) {
                    return img32;
                }
                throw new IllegalStateException("Unexpected image type: " + img.getClass());
            })
            .map(ImageWrapper32.class::cast).toList();
    }

    private void findDisplacement(List<ImageWrapper32> images,
                                  int tileSize,
                                  int x,
                                  int width,
                                  int y,
                                  int height,
                                  float[][] referenceData,
                                  DistorsionMap[] distorsions,
                                  float signal) {
        double tileOffset = tileSize / 2d;
        for (var image : images) {
            var data = image.data();
            var imageIndex = images.indexOf(image);
            var result = createTilesForComparison(tileSize, x, width, y, height, referenceData, data, signal);
            if (result.isRelevant()) {
                var dxy = crossCorrelationShiftFFT(result.referenceTile(), result.dataTile());
                var dx = -dxy.b();
                var dy = -dxy.a();
                distorsions[imageIndex].recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), dx, dy);
            } else {
                distorsions[imageIndex].recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), 0, 0);
            }
        }
    }

    private static double heuristicWeight(double ref, double val) {
        return Math.exp(-8 * Math.abs(ref - val) / (Math.max(ref, val) + 1e-5));
    }

    private static double computeTileWeight(ImageMath imageMath, Image referenceIntegral, Image integralImage, int x, int y, int tileSize) {
        // The computation of the weights here is based on empirical observations
        // and is not based on any scientific paper. It's a heuristic which seems
        // to work well in practice, with the idea that we want to give more weight
        // to images which are similar to the reference image, and minimize the error
        // due to truncation of the solar disk at edges and artifacts at the borders.
        var offset = tileSize / 2;
        var refAvg = imageMath.areaAverage(referenceIntegral, Math.max(0, x - offset), Math.max(0, y - offset), tileSize, tileSize);
        var areaAvg = imageMath.areaAverage(integralImage, Math.max(0, x - offset), Math.max(0, y - offset), tileSize, tileSize);
        return heuristicWeight(refAvg, areaAvg);
    }

    public static Tiles createTilesForComparison(int tileSize,
                                                 int x,
                                                 int width,
                                                 int y,
                                                 int height,
                                                 float[][] referenceData,
                                                 float[][] data,
                                                 float signal) {
        var referenceTile = new float[tileSize][tileSize];
        var dataTile = new float[tileSize][tileSize];
        boolean relevant = true;
        for (int yy = 0; yy < tileSize; yy++) {
            if (y + yy < height) {
                int copyWidth = Math.min(tileSize, width - x);
                if (copyWidth > 0) {
                    for (int xx = 0; xx < copyWidth; xx++) {
                        var v = referenceData[y + yy][x + xx];
                        relevant &= v > signal;
                        referenceTile[yy][xx] = v;
                        dataTile[yy][xx] = data[y + yy][x + xx];
                    }
                }
            }
        }
        return new Tiles(referenceTile, dataTile, relevant);
    }

    private static double linearInterpolation(double v0, double v1, double t) {
        return (v0 + t * (v1 - v0));
    }

    private static float bilinearInterpolation(float[][] image, double xx, double yy, int width, int height) {
        int x0 = (int) Math.floor(xx);
        int x1 = Math.min(x0 + 1, width - 1);
        int y0 = (int) Math.floor(yy);
        int y1 = Math.min(y0 + 1, height - 1);

        // Interpolate along the x direction for both y0 and y1
        float i00 = image[y0][x0];
        float i10 = image[y0][x1];
        float i01 = image[y1][x0];
        float i11 = image[y1][x1];

        // Interpolate along the y direction
        float i0 = (float) linearInterpolation(i00, i10, xx - x0); // interpolate on the x-axis at y0
        float i1 = (float) linearInterpolation(i01, i11, xx - x0); // interpolate on the x-axis at y1

        return (float) linearInterpolation(i0, i1, yy - y0); // interpolate between the results on the y-axis
    }

    public static Complex[][] fftShift(Complex[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        Complex[][] shifted = new Complex[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                shifted[i][j] = data[(i + rows / 2) % rows][(j + cols / 2) % cols];
            }
        }
        return shifted;
    }

    public static DoublePair crossCorrelationShiftFFT(float[][] patchRef, float[][] patchDef) {
        // Step 1: Perform FFT on both reference and defined patches
        var fftRef = fft2(patchRef);
        var fftDef = fft2(patchDef);

        // Step 2: Compute cross-correlation in the frequency domain
        var crossCorr = fftShift(crossCorrelation(fftRef, fftDef));

        // Step 3: Find the peak of the cross-correlation (the best shift)
        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.length / 2d, crossCorr[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        // Step 4: Apply sub-pixel accuracy correction
        double dyOffset = 0;
        double dxOffset = 0;

        if (maxIdx[0] > 0 && maxIdx[0] < crossCorr.length - 1) {
            dyOffset = fitParabola1D(
                crossCorr[maxIdx[0] - 1][maxIdx[1]].getReal(),
                crossCorr[maxIdx[0]][maxIdx[1]].getReal(),
                crossCorr[maxIdx[0] + 1][maxIdx[1]].getReal()
            );
        }

        if (maxIdx[1] > 0 && maxIdx[1] < crossCorr[0].length - 1) {
            dxOffset = fitParabola1D(
                crossCorr[maxIdx[0]][maxIdx[1] - 1].getReal(),
                crossCorr[maxIdx[0]][maxIdx[1]].getReal(),
                crossCorr[maxIdx[0]][maxIdx[1] + 1].getReal()
            );
        }

        // Step 5: Adjust shifts with sub-pixel offsets
        shifts[0] += dyOffset;
        shifts[1] += dxOffset;

        return new DoublePair(shifts[0], shifts[1]);
    }

    public static Complex[][] fft2(float[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        var result = new Complex[rows][cols];
        var fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform 1D FFT on rows
        for (int i = 0; i < rows; i++) {
            var row = new Complex[cols];
            for (int j = 0; j < cols; j++) {
                row[j] = new Complex(data[i][j], 0);
            }
            row = fft.transform(row, TransformType.FORWARD);
            result[i] = row;
        }

        // Perform 1D FFT on columns
        for (int j = 0; j < cols; j++) {
            var column = new Complex[rows];
            for (int i = 0; i < rows; i++) {
                column[i] = result[i][j];
            }
            column = fft.transform(column, TransformType.FORWARD);
            for (int i = 0; i < rows; i++) {
                result[i][j] = column[i];
            }
        }

        return result;
    }

    public static Complex[][] ifft2(Complex[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        var result = new Complex[rows][cols];
        var fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform 1D IFFT on rows
        for (int i = 0; i < rows; i++) {
            var row = data[i];
            row = fft.transform(row, TransformType.INVERSE);
            result[i] = row;
        }

        // Perform 1D IFFT on columns
        for (int j = 0; j < cols; j++) {
            var column = new Complex[rows];
            for (int i = 0; i < rows; i++) {
                column[i] = result[i][j];
            }
            column = fft.transform(column, TransformType.INVERSE);
            for (int i = 0; i < rows; i++) {
                result[i][j] = column[i];
            }
        }

        return result;
    }


    // Compute the cross-correlation (inverse FFT of the product of the reference and defined FFT)
    private static Complex[][] crossCorrelation(Complex[][] fftRef, Complex[][] fftDef) {
        int rows = fftRef.length;
        int cols = fftRef[0].length;
        var result = new Complex[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = fftRef[i][j].multiply(fftDef[i][j].conjugate());
            }
        }

        return ifft2(result);
    }

    // Find the index of the maximum value in the cross-correlation matrix
    public static int[] findMaxIndex(Complex[][] crossCorr) {
        int maxRow = 0;
        int maxCol = 0;
        double maxValue = Double.NEGATIVE_INFINITY;
        int centerRow = crossCorr.length / 2;
        int centerCol = crossCorr[0].length / 2;
        for (int y = 0; y < crossCorr.length; y++) {
            for (int x = 0; x < crossCorr[y].length; x++) {
                double value = crossCorr[y][x].getReal();
                if (value > maxValue) {
                    maxValue = value;
                    maxRow = y;
                    maxCol = x;
                } else if (value == maxValue) {
                    // replace if closer to center
                    double currentDistance = Math.sqrt(Math.pow(y - centerRow, 2) + Math.pow(x - centerCol, 2));
                    double bestDistance = Math.sqrt(Math.pow(maxRow - centerRow, 2) + Math.pow(maxCol - centerCol, 2));

                    // Replace if closer to the center
                    if (currentDistance < bestDistance) {
                        maxRow = y;
                        maxCol = x;
                    }
                }
            }
        }
        return new int[]{maxRow, maxCol};
    }

    // Parabola fitting for sub-pixel accuracy correction
    public static double fitParabola1D(double v0, double v1, double v2) {
        double denom = 2 * (2 * v1 - v0 - v2);
        if (denom == 0) {
            return 0;
        }
        return (v0 - v2) / denom;
    }

    private float[][] assembleImage(List<ImageWrapper32> images,
                                    double[] weights,
                                    DistorsionMap[] distorsions,
                                    int width,
                                    int height,
                                    int tileSize,
                                    ImageWrapper32 referenceImage,
                                    float[][][] dedistorted) {
        var imageMath = ImageMath.newInstance();
        boolean usePixelWeight = weights[0] == -1;
        var integralImages = usePixelWeight ? images.stream().map(ImageWrapper32::asImage).map(imageMath::integralImage).toList() : null;
        var referenceIntegral = usePixelWeight ? imageMath.integralImage(referenceImage.asImage()) : null;
        var result = new float[height][width];
        var currentY = new AtomicInteger();
        IntStream.range(0, height).parallel()
            .forEach(y -> {
                var progress = currentY.incrementAndGet() / (double) height;
                broadcaster.broadcast(ProgressEvent.of(progress, STACKING_MESSAGE));
                var line = result[y];
                for (int x = 0; x < width; x++) {
                    double sum = 0;
                    double count = 0;
                    for (int i = 0; i < images.size(); i++) {
                        var w = usePixelWeight ? computeTileWeight(imageMath, referenceIntegral, integralImages.get(i), x, y, tileSize) : weights[i];
                        var image = images.get(i).data();
                        var displacement = distorsions[i].findDistorsion(x, y);
                        var xx = x + displacement.dx();
                        var yy = y + displacement.dy();
                        if (xx >= 0 && xx < width && yy >= 0 && yy < height) {
                            var interpolatedValue = bilinearInterpolation(image, xx, yy, width, height);
                            var pixelDiff = heuristicWeight(image[y][x], interpolatedValue);
                            if (usePixelWeight) {
                                w = Math.min(w, pixelDiff);
                            }
                            sum += w * interpolatedValue;
                            count += w;
                            if (dedistorted != null) {
                                dedistorted[i][y][x] = interpolatedValue;
                            }
                        }
                    }

                    if (count > 0) {
                        line[x] = (float) (sum / count);
                    }
                }
            });
        broadcaster.broadcast(ProgressEvent.of(1.0, STACKING_MESSAGE));
        return result;
    }

    public static class DistorsionMap {
        private final int width;
        private final int height;
        private final int step;
        private final int tileSize;
        private final double[][][] dxy;
        private BicubicInterpolatingFunction dxModel, dyModel;

        public DistorsionMap(int width, int height, int tileSize, int step) {
            this.width = width;
            this.height = height;
            this.step = step;
            this.tileSize = tileSize;
            var xSamples = ((width + tileSize) / step) + 1;
            var ySamples = ((height + tileSize) / step) + 1;
            this.dxy = new double[xSamples][ySamples][2];
        }

        public void recordDistorsion(int x, int y, double dx, double dy) {
            var offset = tileSize / 2;
            var sampleX = (x - offset) / step;
            var sampleY = (y - offset) / step;
            dxy[sampleY][sampleX][0] = dx;
            dxy[sampleY][sampleX][1] = dy;
        }

        private void computeInterpolators() {
            dxModel = interpolate(0);
            dyModel = interpolate(1);
            if (LOGGER.isDebugEnabled()) {
                double sum = 0;
                double count = 0;
                for (int yy = 0; yy < dxy.length; yy++) {
                    for (int xx = 0; xx < dxy[yy].length; xx++) {
                        var dx = dxy[yy][xx][1];
                        var dy = dxy[yy][xx][1];
                        var estimateX = dxModel.value(yy, xx);
                        var estimateY = dyModel.value(yy, xx);
                        if (Double.isFinite(estimateX) && Double.isFinite(estimateY)) {
                            sum += Math.pow(estimateX - dx, 2) + Math.pow(estimateY - dy, 2);
                            count++;
                        }
                    }
                }
                LOGGER.debug("Error: {}", Math.sqrt(sum / count));
            }
        }

        private BicubicInterpolatingFunction interpolate(int idx) {
            var interpolator = new BicubicInterpolator();

            // Original grid dimensions
            int gridXSize = dxy[0].length;
            int gridYSize = dxy.length;

            // Extended grid to include all angles
            double[] xGrid = new double[gridXSize];
            double[] yGrid = new double[gridYSize];
            double[][] values = new double[gridYSize][gridXSize];

            // Populate xGrid and yGrid
            for (int i = 0; i < gridXSize; i++) {
                xGrid[i] = i;
            }
            for (int j = 0; j < gridYSize; j++) {
                yGrid[j] = j;
            }

            // Fill values grid, handling inner and boundary points
            for (int y = 0; y < gridYSize; y++) {
                for (int x = 0; x < gridXSize; x++) {
                    values[y][x] = dxy[y][x][idx];
                }
            }

            // Build and return the spline interpolator
            return interpolator.interpolate(xGrid, yGrid, values);
        }

        private DeltaXY findDistorsion(int x, int y) {
            if (dxModel == null || dyModel == null) {
                computeInterpolators();
            }
            var ax = ((double) x) / step;
            var ay = ((double) y) / step;
            if (dxModel.isValidPoint(ay, ax) && dyModel.isValidPoint(ay, ax)) {
                return new DeltaXY(dxModel.value(ay, ax), dyModel.value(ay, ax));
            }
            return new DeltaXY(0, 0);
        }

    }

    private static <T> TimedResult<T> measure(Supplier<T> generator) {
        long start = System.nanoTime();
        var result = generator.get();
        long end = System.nanoTime();
        return new TimedResult<>(Duration.ofNanos(end - start), result);
    }

    private record TimedResult<T>(Duration duration, T result) {
    }

    private record DeltaXY(double dx, double dy) {

    }

    public record Tiles(float[][] referenceTile, float[][] dataTile, boolean isRelevant) {
    }

    public enum ReferenceSelection {
        FIRST,
        AVERAGE,
        MEDIAN,
        ECCENTRICITY,
        SHARPNESS
    }

}
