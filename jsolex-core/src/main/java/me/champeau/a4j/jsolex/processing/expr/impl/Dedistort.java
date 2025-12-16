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
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMaps;
import me.champeau.a4j.jsolex.processing.expr.stacking.ConsensusReference;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUDedistort;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUDistortionGrid;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUImage;
import me.champeau.a4j.jsolex.processing.expr.stacking.GPUTileExtractor;
import me.champeau.a4j.jsolex.processing.expr.stacking.SignalEvaluator;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.correlation.PhaseCorrelation;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    // By the Central Limit Theorem, the sample mean converges to the true mean with standard
    // error proportional to σ/√n. With 30 samples, we get a good approximation (σ/√30 ≈ 0.18σ)
    // while keeping computational complexity linear O(N×k) instead of quadratic O(N²).
    private static final int MAX_CONSENSUS_COMPARISONS = 30;

    // Minimum relative improvement threshold for convergence
    private static final double CONVERGENCE_THRESHOLD = 0.01;

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

    static DisplacementResult findDisplacementWithQuality(float[][] referenceData,
                                                          ImageWrapper32 image,
                                                          int width,
                                                          int height,
                                                          int x,
                                                          int y,
                                                          int tileSize,
                                                          float signal,
                                                          float[][] targetSignalData) {
        var copyWidth = Math.min(tileSize, width - x);
        var copyHeight = Math.min(tileSize, height - y);
        if (copyWidth > 0 && copyHeight > 0) {
            var refSum = 0f;
            var targetSum = 0f;
            for (var yy = 0; yy < copyHeight; yy++) {
                var srcY = y + yy;
                for (var xx = 0; xx < copyWidth; xx++) {
                    refSum += referenceData[srcY][x + xx];
                    if (targetSignalData != null) {
                        targetSum += targetSignalData[srcY][x + xx];
                    }
                }
            }
            var pixelCount = copyWidth * copyHeight;
            var refAvgSignal = refSum / pixelCount;
            var targetAvgSignal = targetSignalData != null ? targetSum / pixelCount : Float.MAX_VALUE;
            if (refAvgSignal > signal && targetAvgSignal > signal) {
                var data = image.data();
                var tiles = createTilesForComparison(tileSize, x, y, referenceData, data, copyWidth, copyHeight);
                return phaseCorrelationWithPSR(tiles.referenceTile(), tiles.dataTile());
            }
        }
        return new DisplacementResult(0, 0);
    }

    /**
     * Optimized version using SignalEvaluator for O(1) signal threshold checking.
     */
    static DisplacementResult findDisplacementWithQuality(float[][] referenceData,
                                                          ImageWrapper32 image,
                                                          int x,
                                                          int y,
                                                          int tileSize,
                                                          float signal,
                                                          SignalEvaluator signalEvaluator) {
        if (signalEvaluator.passesThreshold(x, y, tileSize, tileSize, signal)) {
            var data = image.data();
            var tiles = createTilesForComparison(tileSize, x, y, referenceData, data, tileSize, tileSize);
            return phaseCorrelationWithPSR(tiles.referenceTile(), tiles.dataTile());
        }
        return new DisplacementResult(0, 0);
    }

    /**
     * Optimized version using SignalEvaluator for O(1) signal threshold checking.
     */
    private void processWithRefinement(int x, int y, int tileSize,
                                       float[][] referenceData,
                                       ImageWrapper32 image,
                                       DistorsionMap distorsionMap,
                                       float signal,
                                       SignalEvaluator signalEvaluator) {
        var result = findDisplacementWithQuality(referenceData, image, x, y, tileSize, signal, signalEvaluator);
        var tileOffset = (int) (tileSize / 2d);
        distorsionMap.recordDistorsion(x + tileOffset, y + tileOffset, result.dx(), result.dy());
    }

    private static final int MIN_TILE_SIZE = 32;
    private static final int MIN_STEP = 8;
    private static final int ABSOLUTE_MIN_TILE_SIZE = 16;

    private static int computeRefinementTileSize(int coarseTileSize) {
        return coarseTileSize / 2;
    }

    private static boolean canRefine(int tileSize) {
        return tileSize > MIN_TILE_SIZE;
    }

    private static int countRefinementLevels(int tileSize) {
        int levels = 0;
        int currentSize = tileSize;
        while (currentSize > MIN_TILE_SIZE) {
            levels++;
            currentSize /= 2;
        }
        return levels;
    }

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
                var refine = intArg(arguments, "refine", 1) != 0;
                if (iterations < 1) {
                    iterations = 1;
                }
                return dedistort(mono, image, tileSize, sampling, threshold == -1 ? null : threshold, iterations, refine);
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
                                    boolean refine) {
        var width = reference.width();
        var height = reference.height();
        if (image.width() != width || image.height() != height) {
            throw new IllegalArgumentException("Both images must have the same sizes to perform dedistortion");
        }
        var referenceData = reference.data();
        var signal = backgroundThreshold != null ? backgroundThreshold.floatValue() : 1;

        var refinementLevels = refine ? countRefinementLevels(tileSize) : 0;

        LOGGER.debug("Dedistort: image={}x{}, tileSize={}, sampling={}, iterations={}, refine={}, refinementLevels={}",
                width, height, tileSize, sampling, iterations, refine, refinementLevels);

        var distorsionMapList = new ArrayList<DistorsionMap>();
        var currentImage = image;
        var iterationOperation = newOperation().createChild(DEDISTORT);
        var previousDistortion = Double.MAX_VALUE;

        for (int iteration = 0; iteration < iterations; iteration++) {
            LOGGER.debug("Iteration {}/{}", iteration + 1, iterations);
            var suffix = iterations > 1 ? " (" + (iteration + 1) + "/" + iterations + ")" : "";
            broadcaster.broadcast(iterationOperation.update((double) iteration / iterations, DEDISTORT + suffix));

            var iterationInputImage = currentImage;
            List<DistorsionMap> levelMaps = null;
            var iterationDistortions = new ArrayList<Double>();

            // Try GPU-resident refinement first (more efficient for multi-level refinement)
            if (refine && canRefine(tileSize)) {
                levelMaps = performGPUResidentRefinement(referenceData, currentImage.data(),
                        width, height, tileSize, sampling, signal, refine, iteration);
            }

            // Fallback to standard path if GPU-resident refinement not available or failed
            if (levelMaps == null) {
                levelMaps = new ArrayList<>();
                var currentTileSize = tileSize;
                var level = 1;

                while (true) {
                    var passName = String.format("Iteration %d - Level %d (tile=%d)", iteration + 1, level, currentTileSize);
                    var map = computeDistortionMap(
                            referenceData, currentImage, width, height,
                            currentTileSize, sampling, signal, passName, iterationOperation);
                    levelMaps.add(map);
                    iterationDistortions.add(map.totalDistorsion());

                    if (!refine || !canRefine(currentTileSize)) {
                        break;
                    }

                    currentImage = dedistortSingleWithoutMetadata(currentImage, map, iterationOperation, height, width);
                    currentTileSize = computeRefinementTileSize(currentTileSize);
                    level++;
                }
            } else {
                // GPU-resident path succeeded - collect distortions
                for (var map : levelMaps) {
                    iterationDistortions.add(map.totalDistorsion());
                }
            }

            DistorsionMap iterationMap;
            if (levelMaps.size() == 1) {
                iterationMap = levelMaps.getFirst();
            } else {
                iterationMap = DistorsionMap.synthesize(levelMaps, width, height);
                LOGGER.debug("Iteration {} synthesized {} level maps into one", iteration + 1, levelMaps.size());
            }

            // Check convergence: stop if distortion increased or improvement < 0.1%
            var currentDistortion = iterationMap.totalDistorsion();
            var relativeImprovement = (previousDistortion - currentDistortion) / previousDistortion;
            if (currentDistortion > previousDistortion) {
                LOGGER.warn(message("dedistort.iteration.distortion.increased"), iteration + 1, currentDistortion, previousDistortion);
                break;
            }
            if (relativeImprovement < CONVERGENCE_THRESHOLD && iteration > 0) {
                LOGGER.info(message("dedistort.iteration.converged"), iteration + 1, String.format("%.2f", relativeImprovement * 100), CONVERGENCE_THRESHOLD * 100);
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
        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);
        var increment = (int) Math.max(MIN_STEP, safeTileSize * sampling);

        if (OpenCLSupport.isEnabled() && (safeTileSize == 32 || safeTileSize == 64 || safeTileSize == 128)) {
            try {
                return computeDistortionMapGPU(referenceData, currentImage, width, height,
                        safeTileSize, increment, signal, targetSignalData, passName, parent);
            } catch (Exception e) {
                System.err.println("[Dedistort] GPU distortion map computation failed, falling back to CPU");
                e.printStackTrace();
                var context = OpenCLSupport.getContext();
                if (context != null) {
                    context.recordError("Dedistort.computeDistortionMap", e);
                }
            }
        }

        return computeDistortionMapCPU(referenceData, currentImage, width, height,
                safeTileSize, increment, signal, targetSignalData, passName, parent);
    }

    private DistorsionMap computeDistortionMapCPU(float[][] referenceData,
                                                   ImageWrapper32 currentImage,
                                                   int width,
                                                   int height,
                                                   int safeTileSize,
                                                   int increment,
                                                   float signal,
                                                   float[][] targetSignalData,
                                                   String passName,
                                                   ProgressOperation parent) {
        var distorsionMap = new DistorsionMap(width, height, safeTileSize, increment);

        var maxY = height - safeTileSize;
        var maxX = width - safeTileSize;
        var totalPoints = ((maxY / increment) + 1) * ((maxX / increment) + 1);

        LOGGER.debug("{}: tileSize={}, increment={}, gridPoints={} (CPU)", passName, safeTileSize, increment, totalPoints);

        // Use SignalEvaluator for O(1) signal threshold checks instead of O(tileSize²) loops
        var signalEvaluator = new SignalEvaluator(referenceData, targetSignalData, width, height);

        var progressCounter = new AtomicInteger();
        var progressOperation = parent.createChild(FIND_CORRESP_MESSAGE);

        for (int y = 0; y <= maxY; y += increment) {
            for (int x = 0; x <= maxX; x += increment) {
                processWithRefinement(x, y, safeTileSize,
                        referenceData, currentImage, distorsionMap, signal, signalEvaluator);
                var progress = progressCounter.incrementAndGet() / (double) totalPoints;
                if (progressCounter.get() % 100 == 0) {
                    broadcaster.broadcast(progressOperation.update(progress));
                }
            }
        }

        broadcaster.broadcast(progressOperation.complete());
        distorsionMap.filterAndSmooth();

        LOGGER.debug("{}: totalDistortion={}", passName, distorsionMap.totalDistorsion());

        return distorsionMap;
    }

    private DistorsionMap computeDistortionMapGPU(float[][] referenceData,
                                                   ImageWrapper32 currentImage,
                                                   int width,
                                                   int height,
                                                   int safeTileSize,
                                                   int increment,
                                                   float signal,
                                                   float[][] targetSignalData,
                                                   String passName,
                                                   ProgressOperation parent) {
        var distorsionMap = new DistorsionMap(width, height, safeTileSize, increment);
        var data = currentImage.data();
        var tileOffset = safeTileSize / 2;

        var progressOperation = parent.createChild(FIND_CORRESP_MESSAGE);

        // Use GPU tile extractor for full pipeline: integral images, signal filtering, tile extraction
        var targetData = targetSignalData != null ? targetSignalData : data;
        var extraction = GPUTileExtractor.extract(referenceData, targetData, width, height,
                safeTileSize, increment, signal);

        LOGGER.debug("{}: tileSize={}, increment={}, validTiles={}/{} (GPU extracted)",
                passName, safeTileSize, increment, extraction.validCount(),
                extraction.gridWidth() * extraction.gridHeight());

        if (extraction.validCount() > 0) {
            // Process tiles in batches for phase correlation
            int batchSize = computeBatchSize(safeTileSize);
            var gridPositions = new ArrayList<int[]>(batchSize);
            var refTilesList = new ArrayList<float[][]>(batchSize);
            var targetTilesList = new ArrayList<float[][]>(batchSize);

            for (int i = 0; i < extraction.validCount(); i++) {
                int x = extraction.gridX()[i];
                int y = extraction.gridY()[i];

                // Convert 1D tile to 2D for phase correlation
                var refTile = new float[safeTileSize][safeTileSize];
                var targetTile = new float[safeTileSize][safeTileSize];
                float[] refFlat = extraction.refTiles()[i];
                float[] targetFlat = extraction.targetTiles()[i];

                for (int row = 0; row < safeTileSize; row++) {
                    System.arraycopy(refFlat, row * safeTileSize, refTile[row], 0, safeTileSize);
                    System.arraycopy(targetFlat, row * safeTileSize, targetTile[row], 0, safeTileSize);
                }

                gridPositions.add(new int[]{x + tileOffset, y + tileOffset});
                refTilesList.add(refTile);
                targetTilesList.add(targetTile);

                if (refTilesList.size() >= batchSize) {
                    processBatch(distorsionMap, gridPositions, refTilesList, targetTilesList);
                    gridPositions.clear();
                    refTilesList.clear();
                    targetTilesList.clear();
                }

                if (i % 1000 == 0) {
                    broadcaster.broadcast(progressOperation.update((double) i / extraction.validCount()));
                }
            }

            if (!refTilesList.isEmpty()) {
                processBatch(distorsionMap, gridPositions, refTilesList, targetTilesList);
            }
        }

        broadcaster.broadcast(progressOperation.complete());
        distorsionMap.filterAndSmooth();

        LOGGER.debug("{}: totalDistortion={}", passName, distorsionMap.totalDistorsion());

        return distorsionMap;
    }

    /**
     * Computes distortion map using GPU-resident images.
     * Must be called within OpenCLContext.executeWithLock.
     *
     * @param refImage      GPU-resident reference image
     * @param targetImage   GPU-resident target image
     * @param width         image width
     * @param height        image height
     * @param safeTileSize  tile size
     * @param increment     grid increment
     * @param signal        signal threshold
     * @param passName      name for logging
     * @return distortion map
     */
    private DistorsionMap computeDistortionMapGPUResident(GPUImage refImage, GPUImage targetImage,
                                                          int width, int height, int safeTileSize,
                                                          int increment, float signal, String passName) {
        var distorsionMap = new DistorsionMap(width, height, safeTileSize, increment);
        var context = refImage.getContext();
        var tileOffset = safeTileSize / 2;

        // Use GPU tile extractor with GPU-resident images
        var extraction = GPUTileExtractor.extractFromGPU(context, refImage, targetImage,
                width, height, safeTileSize, increment, signal);

        LOGGER.debug("{}: tileSize={}, increment={}, validTiles={}/{} (GPU resident)",
                passName, safeTileSize, increment, extraction.validCount(),
                extraction.gridWidth() * extraction.gridHeight());

        if (extraction.validCount() > 0) {
            // Process tiles in batches for phase correlation
            int batchSize = computeBatchSize(safeTileSize);
            var gridPositions = new ArrayList<int[]>(batchSize);
            var refTilesList = new ArrayList<float[][]>(batchSize);
            var targetTilesList = new ArrayList<float[][]>(batchSize);

            for (int i = 0; i < extraction.validCount(); i++) {
                int x = extraction.gridX()[i];
                int y = extraction.gridY()[i];

                // Convert 1D tile to 2D for phase correlation
                var refTile = new float[safeTileSize][safeTileSize];
                var targetTile = new float[safeTileSize][safeTileSize];
                float[] refFlat = extraction.refTiles()[i];
                float[] targetFlat = extraction.targetTiles()[i];

                for (int row = 0; row < safeTileSize; row++) {
                    System.arraycopy(refFlat, row * safeTileSize, refTile[row], 0, safeTileSize);
                    System.arraycopy(targetFlat, row * safeTileSize, targetTile[row], 0, safeTileSize);
                }

                gridPositions.add(new int[]{x + tileOffset, y + tileOffset});
                refTilesList.add(refTile);
                targetTilesList.add(targetTile);

                if (refTilesList.size() >= batchSize) {
                    processBatch(distorsionMap, gridPositions, refTilesList, targetTilesList);
                    gridPositions.clear();
                    refTilesList.clear();
                    targetTilesList.clear();
                }
            }

            if (!refTilesList.isEmpty()) {
                processBatch(distorsionMap, gridPositions, refTilesList, targetTilesList);
            }
        }

        distorsionMap.filterAndSmooth();

        LOGGER.debug("{}: totalDistortion={}", passName, distorsionMap.totalDistorsion());

        return distorsionMap;
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

    private void processBatch(DistorsionMap distorsionMap,
                              List<int[]> gridPositions,
                              List<float[][]> refTilesList,
                              List<float[][]> targetTilesList) {
        var refTiles = refTilesList.toArray(new float[0][][]);
        var targetTiles = targetTilesList.toArray(new float[0][][]);

        var displacements = PhaseCorrelation.getInstance().batchedCorrelation(refTiles, targetTiles);

        for (int i = 0; i < displacements.length; i++) {
            var pos = gridPositions.get(i);
            distorsionMap.recordDistorsion(pos[0], pos[1], displacements[i][0], displacements[i][1]);
        }
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

    /**
     * Performs GPU-resident refinement.
     * Uploads images to GPU, runs refinement loop keeping images GPU-resident,
     * and returns synthesized distortion maps.
     *
     * @param referenceData reference image data
     * @param targetData    target image data
     * @param width         image width
     * @param height        image height
     * @param tileSize      starting tile size
     * @param sampling      sampling factor
     * @param signal        signal threshold
     * @param refine        whether to do multi-level refinement
     * @param iteration     current iteration number (for logging)
     * @return list of distortion maps for each level
     */
    private List<DistorsionMap> performGPUResidentRefinement(float[][] referenceData, float[][] targetData,
                                                              int width, int height, int tileSize,
                                                              double sampling, float signal, boolean refine,
                                                              int iteration) {
        var levelMaps = new ArrayList<DistorsionMap>();
        var context = OpenCLSupport.getContext();

        if (context == null) {
            return null; // Fallback to non-GPU path
        }

        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);
        if (safeTileSize != 32 && safeTileSize != 64 && safeTileSize != 128) {
            return null; // GPU only supports these tile sizes
        }

        var increment = (int) Math.max(MIN_STEP, safeTileSize * sampling);

        try {
            return context.executeWithLock(() -> {
                GPUImage refGpu = null;
                GPUImage currentTargetGpu = null;
                List<GPUImage> intermediateImages = new ArrayList<>();

                try {
                    // Upload images once
                    refGpu = GPUImage.upload(referenceData, width, height, context);
                    currentTargetGpu = GPUImage.upload(targetData, width, height, context);

                    var currentTileSize = safeTileSize;
                    var level = 1;

                    while (true) {
                        var currentIncrement = (int) Math.max(MIN_STEP, currentTileSize * sampling);
                        var passName = String.format("Iteration %d - Level %d (tile=%d, GPU-resident)",
                                iteration + 1, level, currentTileSize);

                        // Compute distortion map using GPU-resident images
                        var map = computeDistortionMapGPUResident(refGpu, currentTargetGpu,
                                width, height, currentTileSize, currentIncrement, signal, passName);
                        levelMaps.add(map);

                        if (!refine || !canRefine(currentTileSize)) {
                            break;
                        }

                        // Upload grid and warp on GPU
                        try (var gpuGrid = GPUDistortionGrid.fromDistorsionMap(map, context)) {
                            var warpedGpu = dedistortGPUResident(currentTargetGpu, gpuGrid, true);

                            // Track current target for cleanup if it's not the original
                            if (!intermediateImages.isEmpty() && currentTargetGpu == intermediateImages.getLast()) {
                                // Current target is already tracked
                            } else if (currentTargetGpu != null) {
                                intermediateImages.add(currentTargetGpu);
                            }
                            currentTargetGpu = warpedGpu;
                            intermediateImages.add(warpedGpu);
                        }

                        currentTileSize = computeRefinementTileSize(currentTileSize);
                        level++;
                    }

                    return levelMaps;

                } finally {
                    // Cleanup GPU resources
                    if (refGpu != null) {
                        refGpu.close();
                    }
                    for (var img : intermediateImages) {
                        if (img != null && !img.isClosed()) {
                            img.close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.warn("GPU-resident refinement failed, falling back to standard path", e);
            return null;
        }
    }

    /**
     * Performs GPU-resident dedistortion.
     * Both input and output remain on GPU memory to avoid transfers.
     * Must be called within OpenCLContext.executeWithLock.
     *
     * @param gpuImage    GPU-resident input image
     * @param gpuGrid     GPU-resident distortion grid
     * @param useLanczos  true for Lanczos interpolation, false for bilinear
     * @return new GPU-resident image (caller must close)
     */
    private GPUImage dedistortGPUResident(GPUImage gpuImage, GPUDistortionGrid gpuGrid, boolean useLanczos) {
        return GPUDedistort.dedistort(gpuImage, gpuGrid, useLanczos);
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
        if (iterations < 1) {
            iterations = 1;
        }

        var width = images.getFirst().width();
        var height = images.getFirst().height();
        LOGGER.debug("Consensus dedistort: images={}, tileSize={}, sampling={}, iterations={}",
                imageCount, tileSize, sampling, iterations);

        var mainOperation = newOperation().createChild(DEDISTORT);

        // Track accumulated distortion maps per image across iterations
        var accumulatedMaps = new ArrayList<DistorsionMaps>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            accumulatedMaps.add(new DistorsionMaps(List.of()));
        }

        // Current working images (will be warped after each iteration)
        var currentImages = new ArrayList<>(images);
        var previousAvgDistortion = Double.MAX_VALUE;

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

            // Collect unique pairs to compute (always store as smaller,larger)
            var pairsToCompute = new LinkedHashSet<Long>();
            for (int i = 0; i < imageCount; i++) {
                for (int j : comparisonsPerSourceImage.get(i)) {
                    int smaller = Math.min(i, j);
                    int larger = Math.max(i, j);
                    pairsToCompute.add(encodePair(smaller, larger, imageCount));
                }
            }

            LOGGER.debug("Consensus reference iteration {}: {} unique pairs to compute (reduced from {} total comparisons)",
                    iteration + 1, pairsToCompute.size(),
                    comparisonsPerSourceImage.values().stream().mapToInt(List::size).sum());

            // Compute distortion maps for unique pairs only
            var computedMaps = new HashMap<Long, DistorsionMap>();
            int pairIndex = 0;
            int totalPairs = pairsToCompute.size();
            var pairOperation = comparingOperation.createChild(message("consensus.reference.comparing"));

            for (long pairKey : pairsToCompute) {
                int i = (int) (pairKey / imageCount);
                int j = (int) (pairKey % imageCount);

                broadcaster.broadcast(pairOperation.update((double) pairIndex / totalPairs,
                        String.format("Pair %d/%d (%d→%d)", pairIndex + 1, totalPairs, i + 1, j + 1)));

                var sourceImage = currentImages.get(i);
                var targetImage = currentImages.get(j);

                // Compute single distortion map (no refinement in consensus mode)
                var passName = String.format("Iter %d - Pair %d→%d (tile=%d)",
                        iteration + 1, i + 1, j + 1, tileSize);
                var pairMap = computeDistortionMap(sourceImage.data(), targetImage, width, height,
                        tileSize, sampling, signal, targetImage.data(), passName, pairOperation);
                computedMaps.put(pairKey, pairMap);
                pairIndex++;
            }
            broadcaster.broadcast(pairOperation.complete());

            // For each image, gather maps (direct or negated) and compute average
            var iterationMaps = new ArrayList<DistorsionMap>(imageCount);
            for (int i = 0; i < imageCount; i++) {
                var mapsToOthers = new ArrayList<DistorsionMap>(comparisonsPerImage);

                for (int j : comparisonsPerSourceImage.get(i)) {
                    int smaller = Math.min(i, j);
                    int larger = Math.max(i, j);
                    long pairKey = encodePair(smaller, larger, imageCount);
                    var map = computedMaps.get(pairKey);

                    if (i < j) {
                        // We computed i → j directly
                        mapsToOthers.add(map);
                    } else {
                        // We computed j → i, so negate to get i → j
                        mapsToOthers.add(map.negate());
                    }
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
                for (int i = 0; i < imageCount; i++) {
                    var map = iterationMaps.get(i);
                    accumulatedMaps.set(i, accumulatedMaps.get(i).append(map));
                    currentImages.set(i, dedistortSingleWithoutMetadata(currentImages.get(i), map, mainOperation, height, width));
                }
                break;
            }

            previousAvgDistortion = avgDistortion;

            // Warp each image and accumulate its distortion map
            for (int i = 0; i < imageCount; i++) {
                var map = iterationMaps.get(i);
                accumulatedMaps.set(i, accumulatedMaps.get(i).append(map));
                currentImages.set(i, dedistortSingleWithoutMetadata(currentImages.get(i), map, mainOperation, height, width));
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

    private static long encodePair(int smaller, int larger, int imageCount) {
        return (long) smaller * imageCount + larger;
    }

}
