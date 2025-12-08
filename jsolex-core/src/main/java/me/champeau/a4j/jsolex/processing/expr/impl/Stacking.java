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
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.BestImages;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionDebugImageCreator;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMaps;
import me.champeau.a4j.jsolex.processing.expr.stacking.ConsensusReference;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Stacking extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Stacking.class);

    private static final String STACKING_MESSAGE = message("stacking");
    private static final String FIND_CORRESP_MESSAGE = message("finding.correspondances");
    private static final double DECAY_RATE = -2.0;
    public static final int DEFAULT_TILE_SIZE = 32;
    public static final float DEFAULT_SAMPLING = .5f;
    public static final double DEFAULT_SELECTION_RATIO = 1;

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Scaling scaling;
    private final Crop crop;
    private final SimpleFunctionCall simpleFunctionCall;
    private final ImageDraw imageDraw;
    private final Utilities utilities;
    private final Broadcaster broadcaster;

    public Stacking(Map<Class<?>, Object> context,
                    Scaling scaling,
                    Crop crop,
                    SimpleFunctionCall simpleFunctionCall,
                    ImageDraw imageDraw,
                    Utilities utilities,
                    Broadcaster broadcaster) {
        super(context, broadcaster);
        this.scaling = scaling;
        this.crop = crop;
        this.simpleFunctionCall = simpleFunctionCall;
        this.imageDraw = imageDraw;
        this.utilities = utilities;
        this.broadcaster = broadcaster;
    }

    /**
     * Stacks a list of images, using a given tile size and sampling ratio.
     * The reference image is selected based on the given reference selection.
     *
     * @param images             the list of images to stack
     * @param tileSize           the size of the tiles to use
     * @param sampling           the sampling ratio
     * @param referenceSelection the reference selection
     * @return the stacked image
     */
    public ImageWrapper32 stack(List<ImageWrapper32> images, int tileSize, float sampling, ReferenceSelection referenceSelection) {
        if (images.size() == 1) {
            return images.get(0);
        }
        return doStack(images, tileSize, null, sampling, referenceSelection);
    }

    /**
     * Chooses or generates a reference image from a list of images, based on the given reference selection.
     *
     * @param images             the list of images
     * @param referenceSelection the reference selection
     * @return the reference image
     */
    public ImageWrapper32 chooseReference(List<ImageWrapper32> images, SourceInfo bestSourceInfo, ReferenceSelection referenceSelection) {
        return computeReferenceImageAndAdjustWeights(images, bestSourceInfo, referenceSelection, new double[images.size()]);
    }

    public Object stackDedistorted(Map<String, Object> arguments) {
        BuiltinFunction.STACK_DEDIS.validateArgs(arguments);
        var arg = arguments.get("images");
        if (arg instanceof List<?> list) {
            if (list.size() == 1) {
                return list.getFirst();
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
                return images.getFirst();
            } else if (images.isEmpty()) {
                return List.of();
            }
            var ratio = Math.clamp(doubleArg(arguments, "best", DEFAULT_SELECTION_RATIO), 0, 1);
            var useLocalWeights = intArg(arguments, "local", 0) != 0;
            var imagesWithError = images.stream()
                    .filter(img -> img.findMetadata(DistorsionMaps.class).isPresent())
                    .map(img -> {
                        var distMaps = img.findMetadata(DistorsionMaps.class).get();
                        var lastMap = distMaps.getLast();
                        return new ImageWithError(lastMap.error(), img, lastMap);
                    })
                    .toList();
            if (imagesWithError.size() != images.size()) {
                throw new IllegalArgumentException("All images must have a DistorsionMaps metadata");
            }

            if (useLocalWeights) {
                return stackDedistortedWithLocalWeights(imagesWithError, ratio);
            } else {
                var maxError = imagesWithError.stream().mapToDouble(ImageWithError::error).max().orElse(1);
                var imagesWithWeight = imagesWithError.stream()
                        .map(i -> new Object() {
                            private final double weight = Math.exp(DECAY_RATE * i.error / maxError);
                            private final ImageWrapper32 image = i.image;
                        })
                        .sorted(Comparator.comparingDouble(i -> -i.weight))
                        .limit((int) Math.ceil(ratio * imagesWithError.size()))
                        .toList();
                return utilities.weightedAverage(Map.of(
                        "images", imagesWithWeight.stream().map(i -> i.image).toList(),
                        "weights", imagesWithWeight.stream().mapToDouble(i -> i.weight).boxed().toList()
                ));
            }
        } else {
            throw new IllegalArgumentException("stack_dedis first argument must be a list of images");
        }
    }

    private ImageWrapper32 stackDedistortedWithLocalWeights(List<ImageWithError> imagesWithError, double ratio) {
        var first = imagesWithError.getFirst();
        var width = first.image().width();
        var height = first.image().height();

        var distortionMaps = imagesWithError.stream()
                .map(ImageWithError::distorsionMap)
                .toList();
        var tileSize = DistorsionMap.estimateTurbulenceScale(distortionMaps);

        var sharpnessMaps = imagesWithError.stream()
                .map(img -> computeSharpnessMap(img.image().data(), width, height, tileSize))
                .toList();

        var maxImagesToKeep = (int) Math.ceil(ratio * imagesWithError.size());
        var result = new float[height][width];
        var currentY = new AtomicInteger();
        var progressOperation = newOperation().createChild(STACKING_MESSAGE);

        IntStream.range(0, height)
                .parallel()
                .forEach(y -> {
                    var progress = currentY.incrementAndGet() / (double) height;
                    broadcaster.broadcast(progressOperation.update(progress));
                    var line = result[y];
                    for (int x = 0; x < width; x++) {
                        double sum = 0;
                        double weightSum = 0;
                        double localMaxError = 0;
                        double maxSharpness = 0;
                        var localErrors = new double[imagesWithError.size()];
                        var localSharpness = new double[imagesWithError.size()];
                        for (int i = 0; i < imagesWithError.size(); i++) {
                            var img = imagesWithError.get(i);
                            var localError = img.distorsionMap().interpolateTileError(x, y, tileSize);
                            localErrors[i] = localError;
                            localMaxError = Math.max(localMaxError, localError);
                            var sharpness = sharpnessMaps.get(i)[y][x];
                            localSharpness[i] = sharpness;
                            maxSharpness = Math.max(maxSharpness, sharpness);
                        }

                        if (localMaxError > 0 && maxSharpness > 0) {
                            var weightsAndIndices = new double[imagesWithError.size()][2];
                            for (int i = 0; i < imagesWithError.size(); i++) {
                                var errorWeight = Math.exp(DECAY_RATE * localErrors[i] / localMaxError);
                                var sharpnessWeight = localSharpness[i] / maxSharpness;
                                weightsAndIndices[i][0] = errorWeight * sharpnessWeight;
                                weightsAndIndices[i][1] = i;
                            }
                            Arrays.sort(weightsAndIndices, (a, b) -> Double.compare(b[0], a[0]));

                            for (int i = 0; i < maxImagesToKeep; i++) {
                                var weight = weightsAndIndices[i][0];
                                var idx = (int) weightsAndIndices[i][1];
                                sum += weight * imagesWithError.get(idx).image().data()[y][x];
                                weightSum += weight;
                            }
                        } else {
                            for (int i = 0; i < maxImagesToKeep; i++) {
                                sum += imagesWithError.get(i).image().data()[y][x];
                                weightSum += 1;
                            }
                        }
                        if (weightSum > 0) {
                            line[x] = (float) (sum / weightSum);
                        }
                    }
                });
        broadcaster.broadcast(progressOperation.complete());

        var metadata = MetadataMerger.merge(imagesWithError.stream().map(ImageWithError::image).toList());
        return new ImageWrapper32(width, height, result, metadata);
    }

    private static float[][] computeSharpnessMap(float[][] data, int width, int height, int tileSize) {
        var stride = tileSize / 2;
        var gridWidth = (width + stride - 1) / stride + 1;
        var gridHeight = (height + stride - 1) / stride + 1;
        var sparseGrid = new double[gridHeight][gridWidth];

        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                var tx = gx * stride;
                var ty = gy * stride;
                sparseGrid[gy][gx] = computeLocalSharpness(data, tx, ty, tileSize, width, height);
            }
        }

        var sharpnessMap = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var gx = (double) x / stride;
                var gy = (double) y / stride;
                var gx0 = (int) Math.floor(gx);
                var gy0 = (int) Math.floor(gy);
                var gx1 = Math.min(gx0 + 1, gridWidth - 1);
                var gy1 = Math.min(gy0 + 1, gridHeight - 1);

                var fx = gx - gx0;
                var fy = gy - gy0;

                var v00 = sparseGrid[gy0][gx0];
                var v10 = sparseGrid[gy0][gx1];
                var v01 = sparseGrid[gy1][gx0];
                var v11 = sparseGrid[gy1][gx1];

                var v0 = v00 + fx * (v10 - v00);
                var v1 = v01 + fx * (v11 - v01);
                sharpnessMap[y][x] = (float) (v0 + fy * (v1 - v0));
            }
        }
        return sharpnessMap;
    }

    private static double computeLocalSharpness(float[][] data, int cx, int cy, int tileSize, int width, int height) {
        var halfTile = tileSize / 2;
        var x0 = Math.max(0, cx - halfTile);
        var x1 = Math.min(width - 1, cx + halfTile);
        var y0 = Math.max(0, cy - halfTile);
        var y1 = Math.min(height - 1, cy + halfTile);

        var tileWidth = x1 - x0 + 1;
        var tileHeight = y1 - y0 + 1;
        if (tileWidth < 4 || tileHeight < 4 || !FFTSupport.isPowerOf2(tileWidth) || !FFTSupport.isPowerOf2(tileHeight)) {
            return computeGradientSharpness(data, x0, x1, y0, y1);
        }

        var tile = new float[tileHeight][tileWidth];
        for (int y = 0; y < tileHeight; y++) {
            System.arraycopy(data[y0 + y], x0, tile[y], 0, tileWidth);
        }

        var fftResult = FFTSupport.fft2Float(tile);
        double highFreqEnergy = 0;
        double totalEnergy = 0;
        var cutoff = Math.min(tileWidth, tileHeight) / 4;

        for (int y = 0; y < tileHeight; y++) {
            for (int x = 0; x < tileWidth; x++) {
                var real = fftResult.real[y][x];
                var imag = fftResult.imaginary[y][x];
                var energy = real * real + imag * imag;
                totalEnergy += energy;
                var freqDist = Math.sqrt((x - tileWidth / 2.0) * (x - tileWidth / 2.0) + (y - tileHeight / 2.0) * (y - tileHeight / 2.0));
                if (freqDist > cutoff) {
                    highFreqEnergy += energy;
                }
            }
        }

        if (totalEnergy < 1e-10) {
            return 0;
        }
        return highFreqEnergy / totalEnergy;
    }

    private static double computeGradientSharpness(float[][] data, int x0, int x1, int y0, int y1) {
        double gradientSum = 0;
        double intensitySum = 0;
        int count = 0;

        for (int y = Math.max(1, y0); y <= Math.min(y1, data.length - 2); y++) {
            for (int x = Math.max(1, x0); x <= Math.min(x1, data[0].length - 2); x++) {
                var dx = data[y][x + 1] - data[y][x - 1];
                var dy = data[y + 1][x] - data[y - 1][x];
                gradientSum += dx * dx + dy * dy;
                intensitySum += data[y][x] * data[y][x];
                count++;
            }
        }

        if (intensitySum < 1e-10 || count == 0) {
            return 0;
        }
        return gradientSum / (intensitySum * count);
    }

    /**
     * Chooses or generates a reference image from a list of images, based on the given reference selection.
     */
    public Object chooseReference(Map<String, Object> arguments) {
        BuiltinFunction.STACK_REF.validateArgs(arguments);
        var arg = arguments.get("images");
        if (arg instanceof List<?> list) {
            if (list.size() == 1) {
                return list.getFirst();
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
                return images.getFirst();
            } else if (images.isEmpty()) {
                return List.of();
            }
            var referenceSelection = ReferenceSelection.valueOf(stringArg(arguments, "select", ReferenceSelection.SHARPNESS.toString()).toUpperCase(Locale.US));
            var bestSource = referenceSelection == ReferenceSelection.MANUAL ? bestSourceInfo() : null;
            return chooseReference(images, bestSource, referenceSelection);
        } else {
            throw new IllegalArgumentException("choose_reference first argument must be a list of images");
        }
    }

    private SourceInfo bestSourceInfo() {
        var bestImages = (BestImages) context.get(BestImages.class);
        if (bestImages != null) {
            return bestImages.sourceInfo();
        }
        return null;
    }

    /**
     * Stacks a list of images, using a given tile size and sampling ratio.
     */
    public Object stack(Map<String, Object> arguments) {
        BuiltinFunction.STACK.validateArgs(arguments);
        var arg = arguments.get("images");
        if (arg instanceof List<?> list) {
            if (list.size() == 1) {
                return list.getFirst();
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
                return images.getFirst();
            }
            if (images.isEmpty()) {
                return List.of();
            }
            var tileSize = intArg(arguments, "ts", DEFAULT_TILE_SIZE);
            if (tileSize < 4) {
                throw new IllegalArgumentException("tile size must be at least 4");
            }
            if (!FFTSupport.isPowerOf2(tileSize)) {
                throw new IllegalArgumentException("tile size must be a power of 2");
            }
            var sampling = doubleArg(arguments, "sampling", DEFAULT_SAMPLING);
            var referenceSelection = ReferenceSelection.valueOf(stringArg(arguments, "select", ReferenceSelection.SHARPNESS.toString()).toUpperCase(Locale.US));
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
            reference = computeReferenceImageAndAdjustWeights(sourceImages, bestSourceInfo(), referenceSelection, weights);
        }
        var referenceData = referenceImage != null ? referenceImage.data() : reference.data();
        var signal = 1;
        var imageCount = sourceImages.size();
        var increment = (int) Math.max(2, tileSize * sampling);
        var distorsions = new DistorsionMap[imageCount];
        for (int i = 0; i < distorsions.length; i++) {
            distorsions[i] = new DistorsionMap(width, height, tileSize, increment);
        }
        AtomicInteger progressCounter = new AtomicInteger();
        var displacementResult = measure(() -> {
            var progressOperation = newOperation().createChild(FIND_CORRESP_MESSAGE);
            IntStream.iterate(0, y -> y < height, y -> y + increment)
                    .parallel()
                    .forEach(y -> {
                        for (int x = 0; x < width; x += increment) {
                            findDisplacement(sourceImages, tileSize, x, width, y, height, referenceData, distorsions, signal);
                        }
                        var progress = progressCounter.addAndGet(increment) / (double) height;
                        broadcaster.broadcast(progressOperation.update(progress));
                    });
            broadcaster.broadcast(progressOperation.complete());
            return null;
        });
        var metadata = MetadataMerger.merge(sourceImages);

        var interpolationResult = measure(() -> {
            var progressOperation = newOperation().createChild(message("interpolating.models"));
            broadcaster.broadcast(progressOperation);
            progressCounter.set(0);
            Arrays.stream(distorsions).parallel().forEach(distorsionMap -> {
                var progress = progressCounter.getAndIncrement() / (double) imageCount;
                broadcaster.broadcast(progressOperation.update(progress));
            });
            broadcaster.broadcast(progressOperation.complete());
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
        } else {
            generateDebugImages = params.requestedImages().isEnabled(GeneratedImageKind.DEBUG);
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
        if (context.get(ImageEmitter.class) instanceof ImageEmitter imageEmitter) {
            var progress = new AtomicInteger(0);
            var progressOperation = newOperation().createChild(message("Generating stacking debug images"));
            var creator = new DistorsionDebugImageCreator(imageEmitter, scaling, imageDraw);
            IntStream.range(0, images.size())
                    .parallel()
                    .forEach(i -> {
                        creator.createDebugImage(referenceImage, stacked, distorsions[i], images.get(i), tileSize, increment, i, dedistorted[i], referenceSelection);
                        var pg = (double) progress.incrementAndGet() / images.size();
                        broadcaster.broadcast(progressOperation.update(pg));
                    });
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private ImageWrapper32 computeReferenceImageAndAdjustWeights(List<ImageWrapper32> images,
                                                                 SourceInfo bestImageSource,
                                                                 ReferenceSelection referenceSelection,
                                                                 double[] weights) {
        return switch (referenceSelection) {
            case FIRST -> images.getFirst();
            case AVERAGE ->
                    (ImageWrapper32) simpleFunctionCall.applyFunction("avg", Map.of("list", images), DoubleStream::average);
            case MEDIAN ->
                    (ImageWrapper32) simpleFunctionCall.applyFunction("median", Map.of("list", images), AbstractImageExpressionEvaluator::median);
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
            case MANUAL -> {
                if (bestImageSource == null) {
                    yield computeReferenceImageAndAdjustWeights(images, null, ReferenceSelection.SHARPNESS, weights);
                }
                yield images.stream()
                        .filter(i -> i.findMetadata(SourceInfo.class).map(bestImageSource::equals).orElse(false))
                        .findFirst()
                        .map(ImageWrapper::unwrapToMemory)
                        .map(ImageWrapper32.class::cast)
                        .orElseThrow();
            }
            case CONSENSUS -> {
                var keyframe = computeReferenceImageAndAdjustWeights(images, null, ReferenceSelection.SHARPNESS, weights);
                var copy = keyframe.copy();
                copy.metadata().put(ConsensusReference.class, ConsensusReference.INSTANCE);
                yield copy;
            }
        };
    }

    private List<ImageWrapper32> prepareForStacking(List<ImageWrapper32> images, int[] widths, int[] heights) {
        List<ImageWrapper> images2 = images.stream().map(ImageWrapper.class::cast).toList();
        if (widths.length > 1 || heights.length > 1 || widths[0] != heights[0]) {
            // perform cropping
            images2 = (List<ImageWrapper>) crop.autocrop2(Map.of("img", images2));
        }
        images2 = (List<ImageWrapper>) scaling.radiusRescale(Map.of("images", images2));
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
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            var distorsion = distorsions[i];
            Dedistort.findDisplacement(referenceData, image, width, height, x, y, tileSize, signal, distorsion);
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

    private float[][] assembleImage(List<ImageWrapper32> images,
                                    double[] weights,
                                    DistorsionMap[] distorsions,
                                    int width,
                                    int height,
                                    int tileSize,
                                    ImageWrapper32 referenceImage,
                                    float[][][] dedistorted) {
        boolean usePixelWeight = weights[0] == -1;
        var integralImages = usePixelWeight ? images.stream().map(ImageWrapper32::asImage).map(imageMath::integralImage).toList() : null;
        var referenceIntegral = usePixelWeight ? imageMath.integralImage(referenceImage.asImage()) : null;
        var result = new float[height][width];
        var currentY = new AtomicInteger();
        var progressOperation = newOperation().createChild(message(STACKING_MESSAGE));
        IntStream.range(0, height)
                .parallel()
                .forEach(y -> {
                    var progress = currentY.incrementAndGet() / (double) height;
                    broadcaster.broadcast(progressOperation.update(progress));
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
                                var interpolatedValue = Dedistort.bilinearInterpolation(image, xx, yy, width, height);
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
        broadcaster.broadcast(progressOperation.complete());
        return result;
    }

    private static <T> TimedResult<T> measure(Supplier<T> generator) {
        long start = System.nanoTime();
        var result = generator.get();
        long end = System.nanoTime();
        return new TimedResult<>(Duration.ofNanos(end - start), result);
    }

    private record TimedResult<T>(Duration duration, T result) {
    }

    public record Tiles(float[][] referenceTile, float[][] dataTile, boolean isRelevant) {
    }

    private record ImageWithError(double error, ImageWrapper32 image, DistorsionMap distorsionMap) {
    }

    public enum ReferenceSelection {
        FIRST,
        AVERAGE,
        MEDIAN,
        ECCENTRICITY,
        SHARPNESS,
        MANUAL,
        CONSENSUS
    }

}
