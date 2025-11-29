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
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap;
import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMaps;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageInterpolation;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Dedistort extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dedistort.class);
    private static final String FIND_CORRESP_MESSAGE = message("finding.correspondances");
    private static final String DEDISTORT = message("dedistorting");
    private static final Map<Integer, float[][]> HANN_WINDOW_CACHE = new ConcurrentHashMap<>();

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
                                                          float signal) {
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
                return phaseCorrelationWithPSR(tiles.referenceTile(), tiles.dataTile());
            }
        }
        return new DisplacementResult(0, 0);
    }

    private void processWithRefinement(int x, int y, int tileSize,
                                       float[][] referenceData,
                                       ImageWrapper32 image,
                                       int width, int height,
                                       DistorsionMap distorsionMap,
                                       float signal) {
        var result = findDisplacementWithQuality(referenceData, image, width, height, x, y, tileSize, signal);
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

        for (int iteration = 0; iteration < iterations; iteration++) {
            LOGGER.debug("Iteration {}/{}", iteration + 1, iterations);
            broadcaster.broadcast(iterationOperation.update((double) iteration / iterations));

            var iterationInputImage = currentImage;
            var levelMaps = new ArrayList<DistorsionMap>();
            var currentTileSize = tileSize;
            var level = 1;
            var iterationDistortions = new ArrayList<Double>();

            while (true) {
                var passName = String.format("Iteration %d - Level %d (tile=%d)", iteration + 1, level, currentTileSize);
                var map = computeDistortionMap(
                        referenceData, currentImage, width, height,
                        currentTileSize, sampling, signal, passName);
                levelMaps.add(map);
                iterationDistortions.add(map.totalDistorsion());

                if (!refine || !canRefine(currentTileSize)) {
                    break;
                }

                currentImage = dedistortSingleWithoutMetadata(currentImage, map, height, width);
                currentTileSize = computeRefinementTileSize(currentTileSize);
                level++;
            }

            DistorsionMap iterationMap;
            if (levelMaps.size() == 1) {
                iterationMap = levelMaps.getFirst();
            } else {
                iterationMap = DistorsionMap.synthesize(levelMaps, width, height);
                LOGGER.debug("Iteration {} synthesized {} level maps into one", iteration + 1, levelMaps.size());
            }

            distorsionMapList.add(iterationMap);
            currentImage = dedistortSingleWithoutMetadata(iterationInputImage, iterationMap, height, width);

            LOGGER.debug("Iteration {} complete: distortions by level = {}, synthesized = {}",
                    iteration + 1, iterationDistortions, iterationMap.totalDistorsion());
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

        var finalImage = dedistortSingleWithoutMetadata(image, finalMap, height, width);

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
                                               String passName) {
        var safeTileSize = Math.max(ABSOLUTE_MIN_TILE_SIZE, tileSize);
        var increment = (int) Math.max(MIN_STEP, safeTileSize * sampling);
        var distorsionMap = new DistorsionMap(width, height, safeTileSize, increment);

        var maxY = height - safeTileSize;
        var maxX = width - safeTileSize;
        var totalPoints = ((maxY / increment) + 1) * ((maxX / increment) + 1);

        LOGGER.debug("{}: tileSize={}, sampling={}, increment={}, gridPoints={}", passName, safeTileSize, sampling, increment, totalPoints);

        var progressCounter = new AtomicInteger();
        var progressOperation = newOperation().createChild(FIND_CORRESP_MESSAGE);

        for (int y = 0; y <= maxY; y += increment) {
            for (int x = 0; x <= maxX; x += increment) {
                processWithRefinement(x, y, safeTileSize,
                        referenceData, currentImage, width, height, distorsionMap, signal);
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

    private ImageWrapper32 applyDistorsionMaps(ImageWrapper32 image, DistorsionMaps distorsionMaps) {
        var height = image.height();
        var width = image.width();
        var currentImage = image;

        for (var distorsionMap : distorsionMaps.maps()) {
            currentImage = dedistortSingleWithoutMetadata(currentImage, distorsionMap, height, width);
        }

        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.putAll(image.metadata());
        metadata.put(DistorsionMaps.class, distorsionMaps);
        return new ImageWrapper32(width, height, currentImage.data(), metadata);
    }

    private ImageWrapper32 dedistortSingleWithoutMetadata(ImageWrapper32 image,
                                                          DistorsionMap distorsionMap,
                                                          int height,
                                                          int width) {
        var currentY = new AtomicInteger();
        var imageData = image.data();
        var result = new float[height][width];
        var progressOperation = newOperation().createChild(DEDISTORT);
        for (var y = 0; y < height; y++) {
            var progress = currentY.incrementAndGet() / (double) height;
            broadcaster.broadcast(progressOperation.update(progress));
            for (var x = 0; x < width; x++) {
                var displacement = distorsionMap.findDistorsion(x, y);
                var xx = x + displacement.dx();
                var yy = y + displacement.dy();
                if (xx >= 0 && xx < width && yy >= 0 && yy < height) {
                    result[y][x] = ImageInterpolation.lanczos2D(imageData, xx, yy, width, height);
                }
            }
        }
        broadcaster.broadcast(progressOperation.complete());
        return new ImageWrapper32(width, height, result, MutableMap.of());
    }

    public record DisplacementResult(double dx, double dy) {
    }

}
