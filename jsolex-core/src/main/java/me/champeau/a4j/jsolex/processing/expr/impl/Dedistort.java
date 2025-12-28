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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.stacking.ConsensusReference;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMaps;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUDedistort;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUDistortionGrid;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUImage;
import me.champeau.a4j.jsolex.processing.expr.stacking.GridSamplingStrategy;
import me.champeau.a4j.jsolex.processing.expr.stacking.InterestPointSamplingStrategy;
import me.champeau.a4j.jsolex.processing.expr.stacking.SamplingStrategy;
import me.champeau.a4j.jsolex.processing.expr.stacking.SparseDistortionField;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.correlation.CorrelationStrategy;
import me.champeau.a4j.math.correlation.NCCCorrelationStrategy;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.correlation.CorrelationTools;
import me.champeau.a4j.math.opencl.GPUImageCache;
import me.champeau.a4j.math.opencl.GPUMemoryBudget;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Dedistort extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dedistort.class);
    private static final String FIND_CORRESP_MESSAGE = message("finding.correspondances");
    private static final String DEDISTORT = message("dedistorting");
    private static final Map<Integer, float[][]> HANN_WINDOW_CACHE = new ConcurrentHashMap<>();
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance();
    private static final AtomicInteger DEBUG_IMAGE_COUNTER = new AtomicInteger(0);

    // By the Central Limit Theorem, the sample mean converges to the true mean with standard
    // error proportional to σ/√n. With 30 samples, we get a good approximation (σ/√30 ≈ 0.18σ)
    // while keeping computational complexity linear O(N×k) instead of quadratic O(N²).
    private static final int MAX_CONSENSUS_COMPARISONS = 30;

    // Minimum relative improvement threshold for convergence
    private static final double CONVERGENCE_THRESHOLD = 0.01;

    // Reject bottom N% of samples by confidence (adaptive filtering)
    private static final double CONFIDENCE_REJECTION_PERCENTILE = 0.50;

    private final CorrelationStrategy correlationStrategy = NCCCorrelationStrategy.INSTANCE;

    static {
//        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Dedistort.class)).setLevel(Level.DEBUG);
    }

    public Dedistort(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    private static float[][] getOrCreateHannWindow(int size) {
        return HANN_WINDOW_CACHE.computeIfAbsent(size, Dedistort::createHannWindow);
    }

    private static float[][] createHannWindow(int size) {
        var window = new float[size][size];
        var weights1D = new float[size];
        var scale = 2 * Math.PI / (size - 1);
        for (var i = 0; i < size; i++) {
            weights1D[i] = 0.5f * (1 - (float) Math.cos(scale * i));
        }
        for (var y = 0; y < size; y++) {
            var wy = weights1D[y];
            for (var x = 0; x < size; x++) {
                window[y][x] = weights1D[x] * wy;
            }
        }
        return window;
    }

    private static float[][] applyWindowCopy(float[][] tile, float[][] window) {
        var size = tile.length;
        var result = new float[size][size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                result[y][x] = tile[y][x] * window[y][x];
            }
        }
        return result;
    }

    static void findDisplacement(float[][] referenceData,
                                 ImageWrapper32 image,
                                 int width,
                                 int height,
                                 int x,
                                 int y,
                                 int tileSize,
                                 float signal,
                                 DistorsionMap distorsion) {
        var tileOffset = tileSize / 2d;
        var copyWidth = Math.min(tileSize, width - x);
        var copyHeight = Math.min(tileSize, height - y);
        if (copyWidth > 0 && copyHeight > 0) {
            var sum = 0f;
            for (var yy = 0; yy < copyHeight; yy++) {
                var srcY = y + yy;
                for (var xx = 0; xx < copyWidth; xx++) {
                    sum += referenceData[srcY][x + xx];
                }
            }
            var avgSignal = sum / (copyWidth * copyHeight);
            if (avgSignal > signal) {
                var data = image.data();
                var tiles = createTilesForComparison(tileSize, x, y, referenceData, data, copyWidth, copyHeight);
                var dxy = phaseCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile());
                var dx = -dxy.b();
                var dy = -dxy.a();
                distorsion.recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), dx, dy);
                return;
            }
        }
        distorsion.recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), 0, 0);
    }

    public static Stacking.Tiles createTilesForComparison(int tileSize,
                                                          int x,
                                                          int width,
                                                          int y,
                                                          int height,
                                                          float[][] referenceData,
                                                          float[][] data,
                                                          float signal) {
        var copyWidth = Math.min(tileSize, width - x);
        var copyHeight = Math.min(tileSize, height - y);
        var referenceTile = new float[tileSize][tileSize];
        var dataTile = new float[tileSize][tileSize];
        var sum = 0f;
        var cpt = 0;
        if (copyWidth > 0 && copyHeight > 0) {
            for (var yy = 0; yy < copyHeight; yy++) {
                var srcY = y + yy;
                System.arraycopy(referenceData[srcY], x, referenceTile[yy], 0, copyWidth);
                System.arraycopy(data[srcY], x, dataTile[yy], 0, copyWidth);
                for (var xx = 0; xx < copyWidth; xx++) {
                    sum += referenceTile[yy][xx];
                }
            }
            cpt = copyWidth * copyHeight;
        }
        var relevant = cpt > 0 && (sum / cpt) > signal;
        return new Stacking.Tiles(referenceTile, dataTile, relevant);
    }

    private static Stacking.Tiles createTilesForComparison(int tileSize,
                                                           int x,
                                                           int y,
                                                           float[][] referenceData,
                                                           float[][] data,
                                                           int copyWidth,
                                                           int copyHeight) {
        var referenceTile = new float[tileSize][tileSize];
        var dataTile = new float[tileSize][tileSize];
        for (var yy = 0; yy < copyHeight; yy++) {
            var srcY = y + yy;
            System.arraycopy(referenceData[srcY], x, referenceTile[yy], 0, copyWidth);
            System.arraycopy(data[srcY], x, dataTile[yy], 0, copyWidth);
        }
        return new Stacking.Tiles(referenceTile, dataTile, true);
    }

    public static DoublePair crossCorrelationShiftFFT(float[][] patchRef, float[][] patchDef) {
        // Step 1: Perform FFT on both reference and defined patches
        var fftRef = FFTSupport.fft2Float(patchRef);
        var fftDef = FFTSupport.fft2Float(patchDef);

        // Step 2: Compute cross-correlation in the frequency domain
        var crossCorr = fftShift(FFTSupport.crossCorrelationFloat(fftRef, fftDef));

        // Step 3: Find the peak of the cross-correlation (the best shift)
        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.real.length / 2d, crossCorr.real[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        // Step 4: Apply sub-pixel accuracy correction using 2D Gaussian fitting
        var subpixelOffset = fitGaussian2D(crossCorr.real, maxIdx[0], maxIdx[1]);

        // Step 5: Adjust shifts with sub-pixel offsets
        shifts[0] += subpixelOffset[0];
        shifts[1] += subpixelOffset[1];

        return new DoublePair(shifts[0], shifts[1]);
    }

    public static DoublePair bestShiftFFT(float[][] patchRef, float[][] patchDef) {
        if (patchRef.length < 32) {
            return crossCorrelationShiftFFT(patchRef, patchDef);
        } else {
            return phaseCorrelationShiftFFT(patchRef, patchDef);
        }
    }

    public static DoublePair phaseCorrelationShiftFFT(float[][] patchRef, float[][] patchDef) {
        var size = patchRef.length;

        // Apply Hann window to reduce spectral leakage
        var window = getOrCreateHannWindow(size);
        var windowedRef = applyWindowCopy(patchRef, window);
        var windowedDef = applyWindowCopy(patchDef, window);

        // FFT both patches
        var fftRef = FFTSupport.fft2Float(windowedRef);
        var fftDef = FFTSupport.fft2Float(windowedDef);

        // Compute cross-power spectrum with phase normalization
        var rows = fftRef.real.length;
        var cols = fftRef.real[0].length;
        var realResult = new float[rows][cols];
        var imagResult = new float[rows][cols];

        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var refR = fftRef.real[i][j];
                var refI = fftRef.imaginary[i][j];
                var defR = fftDef.real[i][j];
                var defI = fftDef.imaginary[i][j];

                // Cross-power: conj(ref) * def
                var crossR = refR * defR + refI * defI;
                var crossI = refI * defR - refR * defI;

                // Normalize by magnitude (phase correlation)
                var magSq = crossR * crossR + crossI * crossI;
                if (magSq > 1e-20f) {
                    var mag = (float) Math.sqrt(magSq);
                    realResult[i][j] = crossR / mag;
                    imagResult[i][j] = crossI / mag;
                }
            }
        }

        // IFFT to get correlation surface
        var crossCorr = fftShift(FFTSupport.ifft2Float(new FFTSupport.FloatFFT2DResult(realResult, imagResult)));

        // Find peak and apply sub-pixel refinement
        var maxIdx = findMaxIndex(crossCorr);
        var center = new double[]{crossCorr.real.length / 2d, crossCorr.real[0].length / 2d};
        var shifts = new double[]{maxIdx[0] - center[0], maxIdx[1] - center[1]};

        var subpixelOffset = fitGaussian2D(crossCorr.real, maxIdx[0], maxIdx[1]);
        shifts[0] += subpixelOffset[0];
        shifts[1] += subpixelOffset[1];

        return new DoublePair(shifts[0], shifts[1]);
    }

    /**
     * Phase correlation for displacement estimation.
     */
    public static DisplacementResult phaseCorrelationWithPSR(float[][] patchRef, float[][] patchDef) {
        var shift = phaseCorrelationShiftFFT(patchRef, patchDef);
        return new DisplacementResult(-shift.b(), -shift.a());
    }

    // Find the index of the maximum value in the cross-correlation matrix
    public static int[] findMaxIndex(FFTSupport.FloatFFT2DResult crossCorr) {
        var maxRow = 0;
        var maxCol = 0;
        var maxValue = Float.NEGATIVE_INFINITY;
        var centerRow = crossCorr.real.length / 2;
        var centerCol = crossCorr.real[0].length / 2;
        for (var y = 0; y < crossCorr.real.length; y++) {
            for (var x = 0; x < crossCorr.real[y].length; x++) {
                var value = crossCorr.real[y][x];
                if (value > maxValue) {
                    maxValue = value;
                    maxRow = y;
                    maxCol = x;
                } else if (value == maxValue) {
                    // Replace if closer to center (compare squared distances to avoid sqrt)
                    var dy = y - centerRow;
                    var dx = x - centerCol;
                    var currentDistSq = dy * dy + dx * dx;
                    var bestDy = maxRow - centerRow;
                    var bestDx = maxCol - centerCol;
                    var bestDistSq = bestDy * bestDy + bestDx * bestDx;
                    if (currentDistSq < bestDistSq) {
                        maxRow = y;
                        maxCol = x;
                    }
                }
            }
        }
        return new int[]{maxRow, maxCol};
    }

    public static FFTSupport.FloatFFT2DResult fftShift(FFTSupport.FloatFFT2DResult data) {
        var rows = data.real.length;
        var cols = data.real[0].length;
        var shiftedReal = new float[rows][cols];
        var shiftedImag = new float[rows][cols];
        var halfRows = rows / 2;
        var halfCols = cols / 2;
        for (var i = 0; i < rows; i++) {
            var newI = (i < halfRows) ? i + halfRows : i - halfRows;
            for (var j = 0; j < cols; j++) {
                var newJ = (j < halfCols) ? j + halfCols : j - halfCols;
                shiftedReal[newI][newJ] = data.real[i][j];
                shiftedImag[newI][newJ] = data.imaginary[i][j];
            }
        }
        return new FFTSupport.FloatFFT2DResult(shiftedReal, shiftedImag);
    }

    static double linearInterpolation(double v0, double v1, double t) {
        return (v0 + t * (v1 - v0));
    }

    private static double[] fitGaussian2D(float[][] data, int peakY, int peakX) {
        var height = data.length;
        var width = data[0].length;

        if (peakY == 0 || peakY >= height - 1 || peakX == 0 || peakX >= width - 1) {
            return new double[]{0, 0};
        }

        var c = Math.log(Math.max(1e-10, data[peakY][peakX]));
        var n = Math.log(Math.max(1e-10, data[peakY - 1][peakX]));
        var s = Math.log(Math.max(1e-10, data[peakY + 1][peakX]));
        var w = Math.log(Math.max(1e-10, data[peakY][peakX - 1]));
        var e = Math.log(Math.max(1e-10, data[peakY][peakX + 1]));

        var denomY = 2 * (n + s - 2 * c);
        var denomX = 2 * (w + e - 2 * c);

        var dy = 0.0;
        var dx = 0.0;

        if (Math.abs(denomY) > 1e-10) {
            dy = (n - s) / denomY;
            dy = Math.max(-1.0, Math.min(1.0, dy));
        }

        if (Math.abs(denomX) > 1e-10) {
            dx = (w - e) / denomX;
            dx = Math.max(-1.0, Math.min(1.0, dx));
        }

        return new double[]{dy, dx};
    }

    static float bilinearInterpolation(float[][] image, double xx, double yy, int width, int height) {
        var x0 = (int) Math.floor(xx);
        var x1 = Math.min(x0 + 1, width - 1);
        var y0 = (int) Math.floor(yy);
        var y1 = Math.min(y0 + 1, height - 1);

        // Interpolate along the x direction for both y0 and y1
        var i00 = image[y0][x0];
        var i10 = image[y0][x1];
        var i01 = image[y1][x0];
        var i11 = image[y1][x1];

        // Interpolate along the y direction
        var i0 = (float) linearInterpolation(i00, i10, xx - x0); // interpolate on the x-axis at y0
        var i1 = (float) linearInterpolation(i01, i11, xx - x0); // interpolate on the x-axis at y1

        return (float) linearInterpolation(i0, i1, yy - y0); // interpolate between the results on the y-axis
    }

    private static final int MIN_STEP = 8;
    private static final int ABSOLUTE_MIN_TILE_SIZE = 16;

    public Object dedistort(Map<String, Object> arguments) {
        BuiltinFunction.DEDISTORT.validateArgs(arguments);
        var arg = arguments.get("ref");
        if (arg instanceof ImageWrapper wrapper) {
            var reference = wrapper.unwrapToMemory();
            if (!(reference instanceof ImageWrapper32 mono)) {
                throw new IllegalArgumentException("dedistort only supports mono images");
            }
            var target = arguments.get("img");
            if (target instanceof List<?> listOfImages) {
                if (mono.findMetadata(ConsensusReference.class).isPresent()) {
                    return dedistortWithConsensusReference(listOfImages, arguments);
                }
                return listOfImages.stream()
                        .parallel()
                        .map(img -> {
                            var newArgs = new HashMap<>(arguments);
                            newArgs.put("img", img);
                            return dedistort(newArgs);
                        })
                        .toList();
            } else if (target instanceof ImageWrapper w && w.unwrapToMemory() instanceof ImageWrapper32 image) {
                var tileSize = intArg(arguments, "ts", Stacking.DEFAULT_TILE_SIZE);
                var sampling = doubleArg(arguments, "sampling", Stacking.DEFAULT_SAMPLING);
                var threshold = doubleArg(arguments, "threshold", -1);
                var iterations = intArg(arguments, "iterations", 1);
                var useSparse = booleanArg(arguments, "sparse", false);
                var multiscale = booleanArg(arguments, "multiscale", false);
                if (iterations < 1) {
                    iterations = 1;
                }
                // Warn if both sampling and sparse are set (sparse takes precedence)
                if (useSparse && sampling != Stacking.DEFAULT_SAMPLING) {
                    LOGGER.warn(message("dedistort.sparse.overrides.sampling"));
                }
                // Warn if multiscale is set without sparse
                if (multiscale && !useSparse) {
                    LOGGER.warn(message("dedistort.multiscale.requires.sparse"));
                    multiscale = false;
                }
                return dedistort(mono, image, tileSize, sampling, threshold == -1 ? null : threshold, iterations, useSparse, multiscale);
            }
            throw new IllegalArgumentException("dedistort 2d argument must be a mono image");
        } else if (arg instanceof List<?>) {
            var reference = arguments.get("ref");
            var target = arguments.get("img");
            if (reference instanceof List<?> referenceList && target instanceof List<?> targetList) {
                var images = asMonoImages(targetList);
                var references = asMonoImages(referenceList);
                var distorsionMaps = references.stream()
                        .map(img -> img.findMetadata(DistorsionMaps.class).orElseThrow(() -> new IllegalArgumentException("No distorsion maps found in reference image")))
                        .toList();
                return IntStream.range(0, images.size())
                        .parallel()
                        .mapToObj(i -> applyDistorsionMaps(images.get(i), distorsionMaps.get(i)))
                        .toList();
            }
        }
        throw new IllegalArgumentException("dedistort first argument must be a mono image");
    }

    private static List<ImageWrapper32> asMonoImages(List<?> imageList) {
        return imageList.stream()
                .filter(ImageWrapper.class::isInstance)
                .map(ImageWrapper.class::cast)
                .map(ImageWrapper::unwrapToMemory)
                .map(ImageWrapper32.class::cast)
                .toList();
    }

    private static String filenameOf(ImageWrapper32 image) {
        return image.findMetadata(MetadataTable.class)
                .flatMap(t -> t.get(MetadataTable.FILE_NAME))
                .orElse("");
    }

    public ImageWrapper32 dedistort(ImageWrapper32 reference,
                                    ImageWrapper32 image,
                                    int tileSize,
                                    double sampling,
                                    Double backgroundThreshold,
                                    int iterations,
                                    boolean useSparse,
                                    boolean multiscale) {
        var width = reference.width();
        var height = reference.height();
        if (image.width() != width || image.height() != height) {
            throw new IllegalArgumentException("Both images must have the same sizes to perform dedistortion");
        }
        var referenceData = reference.data();
        var signal = backgroundThreshold != null ? backgroundThreshold.floatValue() : 1;

        LOGGER.debug("Dedistort: image={}x{}, tileSize={}, sampling={}, iterations={}, sparse={}, multiscale={}",
                width, height, tileSize, sampling, iterations, useSparse, multiscale);

        var distorsionMapList = new ArrayList<DistorsionMap>();
        var currentImage = image;
        var iterationOperation = newOperation().createChild(DEDISTORT);
        var previousDistortion = Double.MAX_VALUE;

        // Create the sampling strategy once - it handles multi-scale internally
        SamplingStrategy strategy = useSparse
                ? new InterestPointSamplingStrategy(tileSize, multiscale)
                : new GridSamplingStrategy(sampling);

        for (int iteration = 0; iteration < iterations; iteration++) {
            LOGGER.debug("Iteration {}/{}", iteration + 1, iterations);
            var suffix = iterations > 1 ? " (" + (iteration + 1) + "/" + iterations + ")" : "";
            broadcaster.broadcast(iterationOperation.update((double) iteration / iterations, DEDISTORT + suffix));

            var iterationInputImage = currentImage;

            var passName = String.format("Iteration %d [%s]", iteration + 1, strategy.getName());

            var iterationMap = computeDistortionMapWithStrategy(
                    referenceData, currentImage, width, height,
                    tileSize, sampling, signal, strategy, passName, iterationOperation);

            // Check convergence: stop if distortion increased or improvement < threshold
            var currentDistortion = iterationMap.totalDistorsion();
            var relativeImprovement = (previousDistortion - currentDistortion) / previousDistortion;
            if (currentDistortion > previousDistortion) {
                LOGGER.warn(message("dedistort.iteration.distortion.increased"), iteration + 1, currentDistortion, previousDistortion);
                break;
            }
            if (relativeImprovement < CONVERGENCE_THRESHOLD && iteration > 0) {
                LOGGER.debug(message("dedistort.iteration.converged"), iteration + 1, String.format("%.2f", relativeImprovement * 100), CONVERGENCE_THRESHOLD * 100);
                distorsionMapList.add(iterationMap);
                break;
            }

            distorsionMapList.add(iterationMap);
            currentImage = dedistortSingleWithoutMetadata(iterationInputImage, iterationMap, iterationOperation, height, width);
            previousDistortion = currentDistortion;

            LOGGER.debug("Iteration {} complete: distortion={}, improvement={}%",
                    iteration + 1, currentDistortion, String.format("%.4f", relativeImprovement * 100));
        }

        broadcaster.broadcast(iterationOperation.complete());

        DistorsionMap finalMap;
        if (distorsionMapList.size() == 1) {
            finalMap = distorsionMapList.getFirst();
        } else {
            finalMap = DistorsionMap.synthesize(distorsionMapList, width, height);
            LOGGER.debug("Synthesized {} iteration maps into final map", distorsionMapList.size());
        }

        LOGGER.debug("Dedistort complete: finalDistortion={}", finalMap.totalDistorsion());

        var finalImage = dedistortSingleWithoutMetadata(image, finalMap, iterationOperation, height, width);

        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.putAll(image.metadata());
        metadata.put(DistorsionMaps.class, DistorsionMaps.of(finalMap));
        return new ImageWrapper32(width, height, finalImage.data(), metadata);
    }

    private DistorsionMap computeDistortionMap(float[][] referenceData,
                                               ImageWrapper32 currentImage,
                                               int width,
                                               int height,
                                               int tileSize,
                                               double sampling,
                                               float signal,
                                               String passName,
                                               ProgressOperation parent) {
        return computeDistortionMap(referenceData, currentImage, width, height,
                tileSize, sampling, signal, null, passName, parent);
    }

    private DistorsionMap computeDistortionMap(float[][] referenceData,
                                               ImageWrapper32 currentImage,
                                               int width,
                                               int height,
                                               int tileSize,
                                               double sampling,
                                               float signal,
                                               float[][] targetSignalData,
                                               String passName,
                                               ProgressOperation parent) {
        // Default to grid sampling strategy
        var strategy = new GridSamplingStrategy(sampling);
        return computeDistortionMapWithStrategy(referenceData, currentImage, width, height,
                tileSize, sampling, signal, strategy, passName, parent);
    }

    /**
     * Computes a distortion map using the specified sampling strategy.
     * This is the unified algorithm used by both grid and interest-point sampling.
     * The same algorithm runs on CPU or GPU - only the tile extraction and
     * correlation steps differ in implementation.
     */
    private DistorsionMap computeDistortionMapWithStrategy(float[][] referenceData,
                                                            ImageWrapper32 currentImage,
                                                            int width,
                                                            int height,
                                                            int tileSize,
                                                            double sampling,
                                                            float signal,
                                                            SamplingStrategy strategy,
                                                            String passName,
                                                            ProgressOperation parent) {
        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);

        var progressOperation = parent.createChild(FIND_CORRESP_MESSAGE);

        // Select sample positions using the strategy
        broadcaster.broadcast(progressOperation.update(0.0, "Selecting sample positions"));
        var positions = strategy.selectPositions(referenceData, width, height, safeTileSize, signal);

        // Save debug image showing interest points when debug logging is enabled
        if (LoggerFactory.getLogger(InterestPointSamplingStrategy.class).isDebugEnabled() && strategy instanceof InterestPointSamplingStrategy) {
            saveInterestPointDebugImage(referenceData, positions, width, height, passName);
        }

        return computeDistortionMapWithPositions(referenceData, currentImage, width, height,
                tileSize, sampling, strategy, positions, passName, progressOperation);
    }

    /**
     * Computes a distortion map using pre-selected sample positions.
     * Use this when the same positions should be reused across multiple comparisons.
     */
    private DistorsionMap computeDistortionMapWithPositions(float[][] referenceData,
                                                             ImageWrapper32 currentImage,
                                                             int width,
                                                             int height,
                                                             int tileSize,
                                                             double sampling,
                                                             SamplingStrategy strategy,
                                                             SamplingStrategy.SamplePositions positions,
                                                             String passName,
                                                             ProgressOperation progressOperation) {
        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);
        var outputGridStep = strategy.getOutputGridStep(safeTileSize, sampling);

        LOGGER.debug("{}: strategy={}, samples={}, tileSize={}, outputGridStep={}",
                passName, strategy.getName(), positions.count(), safeTileSize, outputGridStep);

        if (positions.count() == 0) {
            LOGGER.warn("{}: no valid sample positions, returning empty distortion map", passName);
            broadcaster.broadcast(progressOperation.complete());
            var emptyMap = new DistorsionMap(width, height, safeTileSize, outputGridStep);
            emptyMap.filterAndSmooth();
            return emptyMap;
        }

        // Extract tiles and compute phase correlations
        broadcaster.broadcast(progressOperation.update(0.1, "Computing displacements"));
        var sparseField = computeDisplacementsAtPositions(
                referenceData, currentImage.data(), width, height,
                safeTileSize, positions, progressOperation);

        LOGGER.debug("{}: sparse field has {} samples", passName, sparseField.getSampleCount());

        // Convert sparse field to regular grid for warping
        broadcaster.broadcast(progressOperation.update(0.9, "Building distortion grid"));
        var distorsionMap = sparseField.toRegularGrid(outputGridStep);

        broadcaster.broadcast(progressOperation.complete());

        LOGGER.debug("{}: totalDistortion={}", passName, distorsionMap.totalDistorsion());

        return distorsionMap;
    }

    /**
     * Computes displacements at the specified positions using phase correlation.
     * Uses GPU if available, falls back to CPU otherwise.
     * Tiles are grouped by size to ensure GPU batches contain uniform tile sizes.
     */
    private SparseDistortionField computeDisplacementsAtPositions(float[][] referenceData,
                                                                    float[][] targetData,
                                                                    int width,
                                                                    int height,
                                                                    int baseTileSize,
                                                                    SamplingStrategy.SamplePositions positions,
                                                                    ProgressOperation progressOperation) {
        // Verify reference and target are different arrays
        boolean sameData = (referenceData == targetData);
        if (sameData) {
            LOGGER.warn("Reference and target data are the same array! Displacements will be zero.");
        } else {
            // Check if the data content is actually different
            double sumDiff = 0;
            int sampleCount = 0;
            int sampleStep = Math.max(1, Math.min(width, height) / 100);
            for (int y = 0; y < height && sampleCount < 1000; y += sampleStep) {
                for (int x = 0; x < width && sampleCount < 1000; x += sampleStep) {
                    sumDiff += Math.abs(referenceData[y][x] - targetData[y][x]);
                    sampleCount++;
                }
            }
            double avgDiff = sampleCount > 0 ? sumDiff / sampleCount : 0;
            LOGGER.debug("Reference vs target: avgDiff={} (sampled {} points)", String.format("%.4f", avgDiff), sampleCount);
            if (avgDiff < 0.001) {
                LOGGER.warn("Reference and target appear nearly identical (avgDiff={})", String.format("%.6f", avgDiff));
            }
        }

        // Group tiles by size for GPU batching (GPU requires uniform tile sizes per batch)
        var tilesBySize = new HashMap<Integer, TileBatch>();

        for (int i = 0; i < positions.count(); i++) {
            int px = positions.x()[i];
            int py = positions.y()[i];
            int ts = positions.tileSize()[i];
            int tileOffset = ts / 2;

            int x = px - tileOffset;
            int y = py - tileOffset;

            if (x < 0 || y < 0 || x + ts > width || y + ts > height) {
                continue;
            }

            var referenceTile = new float[ts][ts];
            var dataTile = new float[ts][ts];

            for (int yy = 0; yy < ts; yy++) {
                System.arraycopy(referenceData[y + yy], x, referenceTile[yy], 0, ts);
                System.arraycopy(targetData[y + yy], x, dataTile[yy], 0, ts);
            }

            var batch = tilesBySize.computeIfAbsent(ts, TileBatch::new);
            batch.add(px, py, referenceTile, dataTile);
        }

        // Process each tile size group and collect all samples
        var allSamples = new ArrayList<DisplacementSample>();
        int totalTiles = tilesBySize.values().stream().mapToInt(b -> b.positions.size()).sum();
        int processedTiles = 0;

        for (var entry : tilesBySize.entrySet()) {
            int tileSize = entry.getKey();
            var batch = entry.getValue();
            int batchSize = computeBatchSize(tileSize);

            LOGGER.debug("Processing {} tiles of size {}", batch.positions.size(), tileSize);

            var gridPositions = new ArrayList<int[]>(batchSize);
            var refTilesList = new ArrayList<float[][]>(batchSize);
            var targetTilesList = new ArrayList<float[][]>(batchSize);

            for (int i = 0; i < batch.positions.size(); i++) {
                gridPositions.add(batch.positions.get(i));
                refTilesList.add(batch.refTiles.get(i));
                targetTilesList.add(batch.targetTiles.get(i));

                if (refTilesList.size() >= batchSize) {
                    allSamples.addAll(processBatchToSamples(gridPositions, refTilesList, targetTilesList, tileSize));
                    gridPositions.clear();
                    refTilesList.clear();
                    targetTilesList.clear();
                }

                processedTiles++;
                if (processedTiles % 100 == 0) {
                    broadcaster.broadcast(progressOperation.update(0.1 + 0.7 * processedTiles / totalTiles));
                }
            }

            if (!refTilesList.isEmpty()) {
                allSamples.addAll(processBatchToSamples(gridPositions, refTilesList, targetTilesList, tileSize));
            }
        }

        // Compute confidence threshold at Nth percentile
        float confidenceThreshold = 0f;
        int rejectedCount = 0;
        if (!allSamples.isEmpty()) {
            var sortedConfidences = allSamples.stream()
                    .map(DisplacementSample::confidence)
                    .sorted()
                    .toList();
            int thresholdIndex = (int) (sortedConfidences.size() * CONFIDENCE_REJECTION_PERCENTILE);
            thresholdIndex = Math.min(thresholdIndex, sortedConfidences.size() - 1);
            confidenceThreshold = sortedConfidences.get(thresholdIndex);
        }

        // Add only samples above threshold to sparse field
        var sparseField = SparseDistortionField.builder(width, height)
                .neighborsK(12)
                .baseTileSize(baseTileSize)
                .useTileWeighting(true)
                .interpolationMethod(SparseDistortionField.InterpolationMethod.RBF_THIN_PLATE);

        for (var sample : allSamples) {
            if (sample.confidence() >= confidenceThreshold) {
                sparseField.addSample(sample.x(), sample.y(), sample.dx(), sample.dy(),
                        sample.tileSize(), sample.confidence());
            } else {
                rejectedCount++;
            }
        }

        LOGGER.debug("Confidence filtering: threshold={} ({}th percentile), rejected {}/{} samples",
                String.format("%.4f", confidenceThreshold),
                (int) (CONFIDENCE_REJECTION_PERCENTILE * 100),
                rejectedCount, allSamples.size());

        broadcaster.broadcast(progressOperation.update(0.9));

        return sparseField.build();
    }

    private static class TileBatch {
        final int tileSize;
        final List<int[]> positions = new ArrayList<>();
        final List<float[][]> refTiles = new ArrayList<>();
        final List<float[][]> targetTiles = new ArrayList<>();

        TileBatch(int tileSize) {
            this.tileSize = tileSize;
        }

        void add(int px, int py, float[][] refTile, float[][] targetTile) {
            positions.add(new int[]{px, py});
            refTiles.add(refTile);
            targetTiles.add(targetTile);
        }
    }

    private record DisplacementSample(int x, int y, float dx, float dy, int tileSize, float confidence) {
    }

    private static int computeBatchSize(int tileSize) {
        // Use only GPU memory constraint for deterministic batch size.
        // GPU memory is constant per machine, unlike Java heap which varies between
        // JVM restarts due to Runtime.freeMemory() returning different values.
        // Variable batch sizes can cause different tiles to be processed by GPU vs CPU
        // (when the last batch has fewer than MIN_TILES_FOR_GPU tiles), leading to
        // non-deterministic results due to floating-point differences between implementations.
        //
        // GPU memory requirements per tile:
        // - 2 input buffers (real): tileSize² × 4 bytes each
        // - 2 complex buffers (ref + target): tileSize² × 8 bytes each
        // - 1 temp buffer (complex): tileSize² × 8 bytes
        // - 1 real output buffer: tileSize² × 4 bytes
        // Total: ~36 bytes per element = 36 × tileSize² per tile
        long bytesPerTileGpu = (long) tileSize * tileSize * 36;
        var ctx = OpenCLSupport.getContext();
        // Use total global memory size; default to 2GB if no GPU context
        long gpuMemory = ctx != null ? ctx.getCapabilities().globalMemSize() : 2L * 1024 * 1024 * 1024;
        // Use proportion of GPU memory, leaving room for driver overhead and other processes
        long availableGpuMemory = (long) (gpuMemory * 0.50);
        int gpuBatchSize = (int) (availableGpuMemory / bytesPerTileGpu);

        int batchSize = Math.max(100, gpuBatchSize);
        LOGGER.debug("Computed batch size: {} tiles (gpu limit: {}, gpu memory: {} MB, tile size: {})",
                batchSize, gpuBatchSize, gpuMemory / (1024 * 1024), tileSize);
        return batchSize;
    }

    private List<DisplacementSample> processBatchToSamples(List<int[]> gridPositions,
                                                            List<float[][]> refTilesList,
                                                            List<float[][]> targetTilesList,
                                                            int tileSize) {
        var refTiles = refTilesList.toArray(new float[0][][]);
        var targetTiles = targetTilesList.toArray(new float[0][][]);

        var displacements = correlationStrategy.batchedCorrelation(refTiles, targetTiles);

        var samples = new ArrayList<DisplacementSample>(displacements.length);

        // Track displacement statistics for debugging
        double sumDx = 0, sumDy = 0, maxDx = 0, maxDy = 0;
        double sumConfidence = 0;
        int nonZeroCount = 0;

        for (int i = 0; i < displacements.length; i++) {
            var pos = gridPositions.get(i);
            float dx = displacements[i][0];
            float dy = displacements[i][1];
            float confidence = displacements[i][2];
            samples.add(new DisplacementSample(pos[0], pos[1], dx, dy, tileSize, confidence));

            sumDx += Math.abs(dx);
            sumDy += Math.abs(dy);
            sumConfidence += confidence;
            maxDx = Math.max(maxDx, Math.abs(dx));
            maxDy = Math.max(maxDy, Math.abs(dy));
            if (dx != 0 || dy != 0) {
                nonZeroCount++;
            }
        }

        if (displacements.length > 0) {
            LOGGER.debug("Batch displacements: count={}, nonZero={}, avgDx={}, avgDy={}, maxDx={}, maxDy={}, avgConf={}",
                    displacements.length, nonZeroCount,
                    String.format("%.4f", sumDx / displacements.length),
                    String.format("%.4f", sumDy / displacements.length),
                    String.format("%.4f", maxDx),
                    String.format("%.4f", maxDy),
                    String.format("%.4f", sumConfidence / displacements.length));
        }

        return samples;
    }

    private ImageWrapper32 applyDistorsionMaps(ImageWrapper32 image, DistorsionMaps distorsionMaps) {
        var height = image.height();
        var width = image.width();
        var currentImage = image;

        for (var distorsionMap : distorsionMaps.maps()) {
            currentImage = dedistortSingleWithoutMetadata(currentImage, distorsionMap, newOperation(), height, width);
        }

        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.putAll(image.metadata());
        metadata.put(DistorsionMaps.class, distorsionMaps);
        return new ImageWrapper32(width, height, currentImage.data(), metadata);
    }

    private ImageWrapper32 dedistortSingleWithoutMetadata(ImageWrapper32 image,
                                                          DistorsionMap distorsionMap,
                                                          ProgressOperation parent,
                                                          int height,
                                                          int width) {
        var progressOperation = parent.createChild(DEDISTORT);
        broadcaster.broadcast(progressOperation.update(0.0));

        var gridDx = distorsionMap.getGridDx();
        var gridDy = distorsionMap.getGridDy();
        var gridStep = distorsionMap.getStep();

        var sourceImage = new Image(width, height, image.data());
        var resultImage = IMAGE_MATH.dedistort(sourceImage, gridDx, gridDy, gridStep, true);

        broadcaster.broadcast(progressOperation.complete());
        return new ImageWrapper32(width, height, resultImage.data(), MutableMap.of());
    }


    public record DisplacementResult(double dx, double dy) {
    }

    /**
     * Dedistorts images using consensus reference mode.
     * <p>
     * This algorithm estimates the true undistorted geometry by computing pairwise
     * displacement fields between all images and averaging them. For each image i,
     * we compute displacement maps to all other images j, then average these maps.
     * Since displacement(i→j) = d_j - d_i (where d_x is image x's distortion),
     * averaging over all j gives approximately -d_i. Negating this gives the correction
     * needed to map image i to the true geometry.
     * <p>
     * Optimization: Since distortion(i→j) = -distortion(j→i), we only compute unique
     * pairs (i,j) where i < j and derive the inverse by negation. This cuts the number
     * of expensive distortion map computations in half.
     */
    private List<ImageWrapper32> dedistortWithConsensusReference(List<?> listOfImages,
                                                                Map<String, Object> arguments) {
        var images = asMonoImages(listOfImages);

        // Sort by filename for deterministic processing order across JVM restarts
        var sortedImages = new ArrayList<>(images);
        sortedImages.sort(Comparator.comparing(Dedistort::filenameOf));
        images = sortedImages;

        var imageCount = images.size();

        if (imageCount == 1) {
            return images;
        }
        if (imageCount < 5) {
            LOGGER.warn(message("consensus.reference.few.images"), imageCount);
        }

        var tileSize = intArg(arguments, "ts", Stacking.DEFAULT_TILE_SIZE);
        var sampling = doubleArg(arguments, "sampling", Stacking.DEFAULT_SAMPLING);
        var threshold = doubleArg(arguments, "threshold", -1);
        var signal = threshold == -1 ? 1f : (float) threshold;
        var iterations = intArg(arguments, "iterations", 1);
        var useSparse = booleanArg(arguments, "sparse", false);
        var multiscale = booleanArg(arguments, "multiscale", false);
        if (iterations < 1) {
            iterations = 1;
        }
        // Warn if both sampling and sparse are set (sparse takes precedence)
        if (useSparse && sampling != Stacking.DEFAULT_SAMPLING) {
            LOGGER.warn(message("dedistort.sparse.overrides.sampling"));
        }
        // Warn if multiscale is set without sparse
        if (multiscale && !useSparse) {
            LOGGER.warn(message("dedistort.multiscale.requires.sparse"));
            multiscale = false;
        }

        // Create the sampling strategy once - it handles multi-scale internally
        SamplingStrategy strategy = useSparse
                ? new InterestPointSamplingStrategy(tileSize, multiscale)
                : new GridSamplingStrategy(sampling);

        var width = images.getFirst().width();
        var height = images.getFirst().height();
        LOGGER.debug("Consensus dedistort: images={}, tileSize={}, sampling={}, iterations={}, sparse={}, multiscale={}",
                imageCount, tileSize, sampling, iterations, useSparse, multiscale);

        var mainOperation = newOperation().createChild(DEDISTORT);

        // Track accumulated distortion maps per image across iterations
        var accumulatedMaps = new ArrayList<DistorsionMaps>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            accumulatedMaps.add(new DistorsionMaps(List.of()));
        }

        // Current working images (will be warped after each iteration)
        var currentImages = new ArrayList<>(images);
        var previousAvgDistortion = Double.MAX_VALUE;

        // Determine GPU-resident mode settings (outside iteration loop for cross-iteration caching)
        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);
        var context = OpenCLSupport.getContext();
        boolean supportedTileSize = (safeTileSize == 32 || safeTileSize == 64 || safeTileSize == 128);
        boolean gpuAvailable = context != null && supportedTileSize;

        // For GPU-resident warping, we need all images to fit in cache
        // This avoids complexity of hybrid CPU/GPU warping with cache eviction
        GPUImageCache gpuCache = null;
        boolean useGPUResidentWarping = false;

        if (gpuAvailable && iterations > 1) {
            var budget = new GPUMemoryBudget(context, safeTileSize, 10000);
            int maxResidentImages = budget.maxResidentImages(width, height);

            if (maxResidentImages >= imageCount) {
                // All images fit in cache - enable GPU-resident warping
                useGPUResidentWarping = true;
                LOGGER.debug("GPU-resident warping enabled: all {} images fit in cache (max={}), {}",
                        imageCount, maxResidentImages, budget);

                List<ImageWrapper32> imageListForCache = currentImages;
                gpuCache = new GPUImageCache(
                        context,
                        imageCount,
                        width,
                        height,
                        idx -> flattenImageData(imageListForCache.get(idx).data())
                );
            } else {
                LOGGER.debug("GPU-resident warping disabled: {} images, max resident={}", imageCount, maxResidentImages);
            }
        }

        try {
        for (int iteration = 0; iteration < iterations; iteration++) {
            var suffix = iterations > 1 ? " (" + (iteration + 1) + "/" + iterations + ")" : "";
            LOGGER.debug("Consensus reference iteration {}/{}: computing distortion maps for {} images",
                    iteration + 1, iterations, imageCount);
            var computingMessage = message("consensus.reference.computing");
            broadcaster.broadcast(mainOperation.update((double) iteration / iterations, computingMessage + suffix));

            var comparingOperation = mainOperation.createChild(computingMessage);

            // Determine how many comparisons to make per image
            var comparisonsPerImage = Math.min(imageCount - 1, MAX_CONSENSUS_COMPARISONS);
            var useSubsampling = comparisonsPerImage < imageCount - 1;

            // For each image, determine which other images to compare with
            var comparisonsPerSourceImage = new HashMap<Integer, List<Integer>>();
            for (int i = 0; i < imageCount; i++) {
                var targetIndices = new ArrayList<Integer>(imageCount - 1);
                for (int j = 0; j < imageCount; j++) {
                    if (j != i) {
                        targetIndices.add(j);
                    }
                }

                if (useSubsampling) {
                    var random = new Random(42L + i + (long) iteration * imageCount);
                    Collections.shuffle(targetIndices, random);
                    targetIndices = new ArrayList<>(targetIndices.subList(0, comparisonsPerImage));
                }

                comparisonsPerSourceImage.put(i, targetIndices);
            }

            // Collect directed pairs to compute (i → j means distortion from i's perspective)
            // We compute BOTH directions so each image uses its own interest points
            var pairsToCompute = new LinkedHashSet<Long>();
            for (int i = 0; i < imageCount; i++) {
                for (int j : comparisonsPerSourceImage.get(i)) {
                    // Add both directions: i→j and j→i
                    pairsToCompute.add(encodeDirectedPair(i, j, imageCount));
                    pairsToCompute.add(encodeDirectedPair(j, i, imageCount));
                }
            }

            LOGGER.debug("Consensus reference iteration {}: {} directed pairs to compute (from {} comparisons)",
                    iteration + 1, pairsToCompute.size(),
                    comparisonsPerSourceImage.values().stream().mapToInt(List::size).sum());

            // Pre-compute interest points for each unique source image
            var positionsPerSource = new ConcurrentHashMap<Integer, SamplingStrategy.SamplePositions>();
            var sourceIndices = pairsToCompute.stream()
                    .mapToInt(pairKey -> (int) (pairKey / imageCount)) // source index from directed pair
                    .distinct()
                    .toArray();
            int finalIteration = iteration;
            Arrays.stream(sourceIndices)
                    .parallel()
                    .forEach(srcIdx -> {
                        var srcData = currentImages.get(srcIdx).data();
                        var positions = strategy.selectPositions(srcData, width, height, safeTileSize, signal);
                        positionsPerSource.put(srcIdx, positions);

                        // Save debug image showing interest points when debug logging is enabled
                        if (LoggerFactory.getLogger(InterestPointSamplingStrategy.class).isDebugEnabled()
                                && strategy instanceof InterestPointSamplingStrategy) {
                            var passName = String.format("Iter_%d_Src_%d", finalIteration + 1, srcIdx + 1);
                            saveInterestPointDebugImage(srcData, positions, width, height, passName);

                        }
                    });
            LOGGER.debug("Consensus reference iteration {}: computed interest points for {} source images",
                    iteration + 1, positionsPerSource.size());

            // Compute distortion maps for unique pairs only
            var computedMaps = new HashMap<Long, DistorsionMap>();
            int pairIndex = 0;
            int totalPairs = pairsToCompute.size();
            var pairOperation = comparingOperation.createChild(message("consensus.reference.comparing"));

            // Use outer GPU cache if available, otherwise create per-iteration cache for correlation only
            boolean useGPUResident = gpuAvailable;
            GPUImageCache iterationCache = null;
            GPUImageCache effectiveCache = gpuCache; // Use outer cache if GPU-resident warping enabled

            if (useGPUResident && effectiveCache == null) {
                // Create per-iteration cache for correlation (no cross-iteration warping)
                var budget = new GPUMemoryBudget(context, safeTileSize, 10000);
                int maxResidentImages = budget.maxResidentImages(width, height);
                useGPUResident = maxResidentImages >= 2;

                if (useGPUResident) {
                    int cacheCapacity = Math.min(maxResidentImages, imageCount);
                    if (iteration == 0) {
                        LOGGER.debug("Using GPU-resident image correlation: cache capacity={}/{} images, tileSize={}, multiscale={}, {}",
                                cacheCapacity, imageCount, safeTileSize, multiscale, budget);
                    }

                    iterationCache = new GPUImageCache(
                            context,
                            cacheCapacity,
                            width,
                            height,
                            idx -> flattenImageData(currentImages.get(idx).data())
                    );
                    effectiveCache = iterationCache;
                } else if (iteration == 0) {
                    LOGGER.debug("GPU memory insufficient for resident images (max={}, need>=2)", maxResidentImages);
                }
            } else if (!gpuAvailable && iteration == 0) {
                if (context == null) {
                    LOGGER.debug("GPU not available, using CPU correlation");
                } else {
                    LOGGER.debug("GPU-resident correlation requires tile size 32, 64, or 128, using CPU (tile size={})", safeTileSize);
                }
            }

            try {
                GPUImageCache finalGpuCache = effectiveCache;

                if (useGPUResident) {
                    // GPU-resident path: process ALL pairs in one lock session
                    // This eliminates per-pair lock overhead (100-300 lock calls → 1 call)
                    var outputGridStep = strategy.getOutputGridStep(safeTileSize, sampling);
                    int finalPairIndex = pairIndex;

                    var gpuResults = context.executeWithLock(() -> {
                        var results = new HashMap<Long, DistorsionMap>();
                        int localPairIndex = finalPairIndex;

                        for (long pairKey : pairsToCompute) {
                            int i = (int) (pairKey / imageCount);
                            int j = (int) (pairKey % imageCount);

                            broadcaster.broadcast(pairOperation.update((double) localPairIndex / totalPairs,
                                    String.format("Pair %d/%d (%d→%d)", localPairIndex + 1, totalPairs, i + 1, j + 1)));

                            var positions = positionsPerSource.get(i);

                            long refBuffer = finalGpuCache.getImage(i);
                            long targetBuffer = finalGpuCache.getImage(j);

                            // Get CPU data for fallback on unsupported tile sizes
                            var refData = currentImages.get(i).data();
                            var targetDataArray = currentImages.get(j).data();

                            var sparseField = computeDisplacementsGPUResident(
                                    context, refBuffer, targetBuffer,
                                    refData, targetDataArray,
                                    width, height, safeTileSize, positions);

                            results.put(pairKey, sparseField.toRegularGrid(outputGridStep));
                            localPairIndex++;
                        }
                        return results;
                    });

                    computedMaps.putAll(gpuResults);
                    pairIndex += pairsToCompute.size();
                } else {
                    // CPU path: process pairs individually
                    for (long pairKey : pairsToCompute) {
                        int i = (int) (pairKey / imageCount);
                        int j = (int) (pairKey % imageCount);

                        broadcaster.broadcast(pairOperation.update((double) pairIndex / totalPairs,
                                String.format("Pair %d/%d (%d→%d)", pairIndex + 1, totalPairs, i + 1, j + 1)));

                        var sourceImage = currentImages.get(i);
                        var targetImage = currentImages.get(j);
                        var positions = positionsPerSource.get(i);

                        var passName = String.format("Iter %d - Pair %d→%d (tile=%d, %s)",
                                iteration + 1, i + 1, j + 1, tileSize, strategy.getName());
                        var pairMap = computeDistortionMapWithPositions(sourceImage.data(), targetImage, width, height,
                                tileSize, sampling, strategy, positions, passName, pairOperation);

                        computedMaps.put(pairKey, pairMap);
                        pairIndex++;
                    }
                }

                if (finalGpuCache != null) {
                    LOGGER.debug("GPU image cache stats: {}", finalGpuCache);
                }
            } finally {
                // Clean up only per-iteration cache (outer cache is cleaned up in outer finally)
                if (iterationCache != null) {
                    context.executeWithLock(iterationCache::close);
                }
            }
            broadcaster.broadcast(pairOperation.complete());

            // For each image, gather maps and compute average
            // Each map is now computed directly (i → j uses i's interest points)
            var iterationMaps = new ArrayList<DistorsionMap>(imageCount);
            for (int i = 0; i < imageCount; i++) {
                var mapsToOthers = new ArrayList<DistorsionMap>(comparisonsPerImage);

                for (int j : comparisonsPerSourceImage.get(i)) {
                    long pairKey = encodeDirectedPair(i, j, imageCount);
                    var map = computedMaps.get(pairKey);
                    mapsToOthers.add(map);
                }

                // Average: average(image[i] → image[j]) ≈ -d_i (negative of image[i]'s distortion)
                // So we negate to get the correction: image[i] → truth ≈ d_i
                var avgMap = DistorsionMap.average(mapsToOthers);
                var imageToTruth = avgMap.negate();
                iterationMaps.add(imageToTruth);
                LOGGER.debug("Consensus reference iteration {}: image {}: toTruth distortion={}",
                        iteration + 1, i, imageToTruth.totalDistorsion());
            }
            broadcaster.broadcast(comparingOperation.complete());

            // Check convergence: stop if distortion increased or improvement below threshold
            var avgDistortion = iterationMaps.stream()
                    .mapToDouble(DistorsionMap::totalDistorsion)
                    .average()
                    .orElse(0);
            var relativeImprovement = previousAvgDistortion > 0
                    ? (previousAvgDistortion - avgDistortion) / previousAvgDistortion
                    : 1.0;

            LOGGER.debug("Consensus reference iteration {}: avgDistortion={}, improvement={}%",
                    iteration + 1, avgDistortion, String.format("%.4f", relativeImprovement * 100));

            if (avgDistortion > previousAvgDistortion) {
                LOGGER.warn(message("consensus.reference.distortion.increased"), iteration + 1, avgDistortion, previousAvgDistortion);
                break;
            }
            if (relativeImprovement < CONVERGENCE_THRESHOLD && iteration > 0) {
                LOGGER.info(message("consensus.reference.converged"), iteration + 1, String.format("%.2f", relativeImprovement * 100), CONVERGENCE_THRESHOLD * 100);
                warpImagesAfterIteration(iterationMaps, accumulatedMaps, currentImages, mainOperation,
                        height, width, imageCount, useGPUResidentWarping, gpuCache, context);
                break;
            }

            previousAvgDistortion = avgDistortion;

            // Warp each image and accumulate its distortion map
            warpImagesAfterIteration(iterationMaps, accumulatedMaps, currentImages, mainOperation,
                    height, width, imageCount, useGPUResidentWarping, gpuCache, context);
        }
        } finally {
            // Clean up outer GPU cache (used for cross-iteration warping)
            if (gpuCache != null) {
                context.executeWithLock(gpuCache::close);
            }
        }

        // Build final results with accumulated maps in metadata
        LOGGER.debug("Consensus reference dedistort: building results for {} images", imageCount);
        broadcaster.broadcast(mainOperation.update(0.95, "Building results"));

        var results = new ArrayList<ImageWrapper32>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            var correctedImage = currentImages.get(i);
            var distorsionMaps = accumulatedMaps.get(i);

            var metadata = MutableMap.<Class<?>, Object>of();
            metadata.putAll(images.get(i).metadata());
            metadata.put(DistorsionMaps.class, distorsionMaps);
            results.add(new ImageWrapper32(width, height, correctedImage.data(), metadata));

            LOGGER.debug("Consensus reference: image {}: total maps={}", i, distorsionMaps.size());
        }

        broadcaster.broadcast(mainOperation.complete());
        LOGGER.debug("Consensus reference dedistort: completed for {} images with {} iterations", imageCount, iterations);

        return results;
    }

    /**
     * Warps all images using the computed distortion maps after an iteration.
     * Uses GPU-resident warping when available (all images fit in cache).
     */
    private void warpImagesAfterIteration(List<DistorsionMap> iterationMaps,
                                           List<DistorsionMaps> accumulatedMaps,
                                           List<ImageWrapper32> currentImages,
                                           ProgressOperation mainOperation,
                                           int height,
                                           int width,
                                           int imageCount,
                                           boolean useGPUResidentWarping,
                                           GPUImageCache gpuCache,
                                           OpenCLContext context) {
        if (useGPUResidentWarping && gpuCache != null && context != null) {
            // GPU-resident warping: warp all images on GPU and update cache buffers
            context.executeWithLock(() -> {
                for (int i = 0; i < imageCount; i++) {
                    var map = iterationMaps.get(i);
                    accumulatedMaps.set(i, accumulatedMaps.get(i).append(map));

                    // Get current GPU buffer for this image
                    long imageBuffer = gpuCache.getImage(i);

                    // Upload distortion grid to GPU
                    try (var gpuGrid = GPUDistortionGrid.fromDistorsionMap(map, context)) {
                        // Wrap image buffer in GPUImage for warping
                        var gpuImage = GPUImage.fromBuffer(imageBuffer, width, height, context);

                        // Warp on GPU (creates new buffer with result)
                        var warpedGpuImage = GPUDedistort.dedistort(gpuImage, gpuGrid, true);

                        // Replace buffer in cache with warped buffer
                        long warpedBuffer = warpedGpuImage.getBuffer();
                        gpuCache.replaceBuffer(i, warpedBuffer);

                        // Download warped image for final results
                        var warpedData = warpedGpuImage.download();
                        currentImages.set(i, new ImageWrapper32(width, height, warpedData, MutableMap.of()));
                    }
                }
            });
            LOGGER.debug("GPU-resident warping completed for {} images", imageCount);
        } else {
            // CPU warping: use existing method
            for (int i = 0; i < imageCount; i++) {
                var map = iterationMaps.get(i);
                accumulatedMaps.set(i, accumulatedMaps.get(i).append(map));
                currentImages.set(i, dedistortSingleWithoutMetadata(currentImages.get(i), map, mainOperation, height, width));
            }
        }
    }

    private static long encodeDirectedPair(int source, int target, int imageCount) {
        return (long) source * imageCount + target;
    }

    /**
     * Flattens a 2D float array to a 1D array for GPU upload.
     */
    private static float[] flattenImageData(float[][] data) {
        int height = data.length;
        int width = data[0].length;
        var flat = new float[height * width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, flat, y * width, width);
        }
        return flat;
    }

    /**
     * Computes distortion map using GPU-resident images.
     * This method extracts tiles directly on GPU, avoiding CPU-GPU transfer overhead.
     * Supports multiscale mode by grouping positions by tile size.
     * Falls back to CPU correlation for tile sizes not supported by GPU (e.g., 256).
     */
    private SparseDistortionField computeDisplacementsGPUResident(OpenCLContext context,
                                                                   long refImageBuffer,
                                                                   long targetImageBuffer,
                                                                   float[][] refData,
                                                                   float[][] targetData,
                                                                   int width,
                                                                   int height,
                                                                   int baseTileSize,
                                                                   SamplingStrategy.SamplePositions positions) {
        int numPositions = positions.count();
        if (numPositions == 0) {
            return SparseDistortionField.builder(width, height)
                    .baseTileSize(baseTileSize)
                    .build();
        }

        var correlationTools = CorrelationTools.getInstance();

        // Group positions by tile size for efficient batching
        var positionsByTileSize = new HashMap<Integer, List<Integer>>();
        for (int i = 0; i < numPositions; i++) {
            int ts = positions.tileSize()[i];
            positionsByTileSize.computeIfAbsent(ts, k -> new ArrayList<>()).add(i);
        }

        // Process each tile size group and collect all displacements
        var allDisplacements = new float[numPositions][3];
        for (var entry : positionsByTileSize.entrySet()) {
            int tileSize = entry.getKey();
            var indices = entry.getValue();

            // Build position array for this tile size
            var tilePositions = new int[indices.size()][2];
            for (int i = 0; i < indices.size(); i++) {
                int idx = indices.get(i);
                tilePositions[i][0] = positions.x()[idx];
                tilePositions[i][1] = positions.y()[idx];
            }

            // Correlate using GPU-resident images with appropriate tile size
            float[][] displacements;
            if (correlationTools.isGpuResidentCorrelationSupported(context, tileSize)) {
                displacements = correlationTools.correlateResidentImagesNCC(
                        context, refImageBuffer, targetImageBuffer,
                        width, height, tilePositions, tileSize);
            } else {
                // Fallback to CPU correlation for unsupported tile sizes or insufficient GPU resources
                LOGGER.debug("Using CPU fallback for tile size {} ({} positions)", tileSize, indices.size());
                displacements = correlateTilesCPU(refData, targetData, width, height, tilePositions, tileSize);
            }

            // Copy results back to original indices
            for (int i = 0; i < indices.size(); i++) {
                int idx = indices.get(i);
                allDisplacements[idx] = displacements[i];
            }
        }

        // Compute confidence threshold at Nth percentile
        float confidenceThreshold = 0f;
        var validConfidences = new ArrayList<Float>();
        for (int i = 0; i < numPositions; i++) {
            if (allDisplacements[i] != null && allDisplacements[i].length == 3) {
                validConfidences.add(allDisplacements[i][2]);
            }
        }
        if (!validConfidences.isEmpty()) {
            validConfidences.sort(Float::compare);
            int thresholdIndex = (int) (validConfidences.size() * CONFIDENCE_REJECTION_PERCENTILE);
            thresholdIndex = Math.min(thresholdIndex, validConfidences.size() - 1);
            confidenceThreshold = validConfidences.get(thresholdIndex);
        }

        // Build sparse field from results
        var sparseField = SparseDistortionField.builder(width, height)
                .neighborsK(12)
                .baseTileSize(baseTileSize)
                .useTileWeighting(true)
                .interpolationMethod(SparseDistortionField.InterpolationMethod.RBF_THIN_PLATE);

        int rejectedCount = 0;
        int processedCount = 0;
        for (int i = 0; i < numPositions; i++) {
            if (allDisplacements[i] == null || allDisplacements[i].length != 3) {
                continue;
            }
            processedCount++;

            float dx = allDisplacements[i][0];
            float dy = allDisplacements[i][1];
            float confidence = allDisplacements[i][2];

            if (confidence >= confidenceThreshold) {
                int px = positions.x()[i];
                int py = positions.y()[i];
                int ts = positions.tileSize()[i];
                sparseField.addSample(px, py, dx, dy, ts, confidence);
            } else {
                rejectedCount++;
            }
        }

        LOGGER.debug("GPU-resident correlation: {} samples ({} tile sizes), {} rejected (threshold={})",
                processedCount, positionsByTileSize.size(), rejectedCount, String.format("%.4f", confidenceThreshold));

        return sparseField.build();
    }

    /**
     * CPU fallback for tile correlation when GPU doesn't support the tile size.
     * Extracts tiles at the given positions and correlates using the CPU strategy.
     */
    private float[][] correlateTilesCPU(float[][] refData,
                                         float[][] targetData,
                                         int width,
                                         int height,
                                         int[][] positions,
                                         int tileSize) {
        int tileOffset = tileSize / 2;
        var refTiles = new ArrayList<float[][]>(positions.length);
        var targetTiles = new ArrayList<float[][]>(positions.length);
        var validPositions = new ArrayList<int[]>(positions.length);

        for (int[] pos : positions) {
            int px = pos[0];
            int py = pos[1];
            int x = px - tileOffset;
            int y = py - tileOffset;

            // Skip tiles that don't fit
            if (x < 0 || y < 0 || x + tileSize > width || y + tileSize > height) {
                continue;
            }

            var refTile = new float[tileSize][tileSize];
            var targetTile = new float[tileSize][tileSize];

            for (int yy = 0; yy < tileSize; yy++) {
                System.arraycopy(refData[y + yy], x, refTile[yy], 0, tileSize);
                System.arraycopy(targetData[y + yy], x, targetTile[yy], 0, tileSize);
            }

            refTiles.add(refTile);
            targetTiles.add(targetTile);
            validPositions.add(pos);
        }

        if (refTiles.isEmpty()) {
            return new float[positions.length][3];
        }

        var displacements = correlationStrategy.batchedCorrelation(
                refTiles.toArray(new float[0][][]),
                targetTiles.toArray(new float[0][][]));

        // Map results back to original position indices
        var result = new float[positions.length][3];
        var validIndex = 0;
        for (int i = 0; i < positions.length; i++) {
            if (validIndex < validPositions.size() &&
                    positions[i][0] == validPositions.get(validIndex)[0] &&
                    positions[i][1] == validPositions.get(validIndex)[1]) {
                result[i] = displacements[validIndex];
                validIndex++;
            }
        }

        return result;
    }

    private void saveInterestPointDebugImage(float[][] referenceData,
                                              SamplingStrategy.SamplePositions positions,
                                              int width,
                                              int height,
                                              String passName) {
        try {
            var debugData = InterestPointSamplingStrategy.createDebugImage(referenceData, positions, width, height);

            // Convert float[][] to 16-bit grayscale BufferedImage
            var image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
            short[] pixels = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Scale 0-1 float to 0-65535 short
                    float val = Math.max(0, Math.min(1, debugData[y][x]));
                    pixels[y * width + x] = (short) (val * 65535);
                }
            }

            // Save to temp directory with unique name
            int counter = DEBUG_IMAGE_COUNTER.incrementAndGet();
            String safeName = passName.replaceAll("[^a-zA-Z0-9_-]", "_");
            var debugFile = new File(System.getProperty("java.io.tmpdir"),
                    String.format("dedistort/dedistort_debug_%03d_%s.png", counter, safeName));
            debugFile.getParentFile().mkdirs();
            ImageIO.write(image, "png", debugFile);
            LOGGER.debug("Saved interest point debug image: {}", debugFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("Failed to save interest point debug image", e);
        }
    }

}
