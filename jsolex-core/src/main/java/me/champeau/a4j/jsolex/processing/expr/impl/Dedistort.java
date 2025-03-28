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
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.tuples.DoublePair;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class Dedistort extends AbstractFunctionImpl {
    private static final String FIND_CORRESP_MESSAGE = message("finding.correspondances");
    private static final String DEDISTORT = message("dedistorting");

    public Dedistort(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
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
        double tileOffset = tileSize / 2d;
        var data = image.data();
        var tiles = createTilesForComparison(tileSize, x, width, y, height, referenceData, data, signal);
        if (tiles.isRelevant()) {
            var dxy = crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile());
            var dx = -dxy.b();
            var dy = -dxy.a();
            distorsion.recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), dx, dy);
        } else {
            distorsion.recordDistorsion((int) (x + tileOffset), (int) (y + tileOffset), 0, 0);
        }
    }

    public static Stacking.Tiles createTilesForComparison(int tileSize,
                                                          int x,
                                                          int width,
                                                          int y,
                                                          int height,
                                                          float[][] referenceData,
                                                          float[][] data,
                                                          float signal) {
        var referenceTile = new float[tileSize][tileSize];
        var dataTile = new float[tileSize][tileSize];
        int copyWidth = Math.min(tileSize, width - x);
        var sum = 0d;
        var cpt = 0d;
        if (copyWidth > 0) {
            for (int yy = 0; yy < tileSize; yy++) {
                if (y + yy < height) {
                    for (int xx = 0; xx < copyWidth; xx++) {
                        var v = referenceData[y + yy][x + xx];
                        sum += v;
                        cpt++;
                        referenceTile[yy][xx] = v;
                        dataTile[yy][xx] = data[y + yy][x + xx];
                    }
                }
            }
        }
        var relevant = cpt > 0 && (sum / cpt) > signal;
        return new Stacking.Tiles(referenceTile, dataTile, relevant);
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
        var fftRef = FFTSupport.fft2(patchRef);
        var fftDef = FFTSupport.fft2(patchDef);

        // Step 2: Compute cross-correlation in the frequency domain
        var crossCorr = fftShift(FFTSupport.crossCorrelation(fftRef, fftDef));

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

    static double linearInterpolation(double v0, double v1, double t) {
        return (v0 + t * (v1 - v0));
    }

    static float bilinearInterpolation(float[][] image, double xx, double yy, int width, int height) {
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

    public Object dedistort(Map<String ,Object> arguments) {
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
                return dedistort(mono, image, tileSize, sampling, threshold == -1 ? null : threshold);
            }
            throw new IllegalArgumentException("dedistort 2d argument must be a mono image");
        } else if (arg instanceof List<?> imageList) {
            var reference = arguments.get("ref");
            if (reference instanceof List<?> referenceList) {
                var images = asMonoImages(imageList);
                var references = asMonoImages(referenceList);
                var distorsions = references.stream()
                    .map(img -> img.findMetadata(DistorsionMap.class).orElseThrow(() -> new IllegalArgumentException("No distorsion map found in reference image")))
                    .toList();
                return IntStream.range(0, images.size())
                    .parallel()
                    .mapToObj(i -> dedistortSingle(images.get(i), distorsions.get(i), images.get(i).height(), images.get(i).width()))
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

    public ImageWrapper32 dedistort(ImageWrapper32 reference, ImageWrapper32 image, int tileSize, double sampling, Double backgroundThreshold) {
        var width = reference.width();
        var height = reference.height();
        if (image.width() != width || image.height() != height) {
            throw new IllegalArgumentException("Both images must have the same sizes to perform dedistortion");
        }
        var increment = (int) Math.max(2, tileSize * sampling);
        var distorsionMap = new DistorsionMap(width, height, tileSize, increment);
        var referenceData = reference.data();
        var progressCounter = new AtomicInteger();
        var signal = backgroundThreshold != null ? backgroundThreshold.floatValue() : 1;
        var progressOperation = newOperation().createChild(FIND_CORRESP_MESSAGE);
        for (int y = 0; y < height; y += increment) {
            for (int x = 0; x < width; x += increment) {
                findDisplacement(referenceData, image, width, height, x, y, tileSize, signal, distorsionMap);
            }
            var progress = progressCounter.addAndGet(increment) / (double) height;
            broadcaster.broadcast(progressOperation.update(progress));
        }
        broadcaster.broadcast(progressOperation.complete());
        return dedistortSingle(image, distorsionMap, height, width);
    }

    private ImageWrapper32 dedistortSingle(ImageWrapper32 image,
                                           DistorsionMap distorsionMap,
                                           int height,
                                           int width) {
        distorsionMap.computeInterpolators();
        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.putAll(image.metadata());
        var currentY = new AtomicInteger();
        var imageData = image.data();
        var result = new float[height][width];
        var progressOperation = newOperation().createChild(DEDISTORT);
        for (int y = 0; y < height; y++) {
            var progress = currentY.incrementAndGet() / (double) height;
            broadcaster.broadcast(progressOperation.update(progress));
            for (int x = 0; x < width; x++) {
                var displacement = distorsionMap.findDistorsion(x, y);
                var xx = x + displacement.dx();
                var yy = y + displacement.dy();
                if (xx >= 0 && xx < width && yy >= 0 && yy < height) {
                    var interpolatedValue = bilinearInterpolation(imageData, xx, yy, width, height);
                    result[y][x] = interpolatedValue;
                }
            }
        }
        broadcaster.broadcast(progressOperation.complete());
        metadata.put(DistorsionMap.class, distorsionMap);
        return new ImageWrapper32(width, height, result, metadata);
    }
}
