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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.analysis.GaussianSupport;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;

/**
 * Utility class for reducing banding artifacts in spectroheliographic images.
 * Banding artifacts appear as horizontal stripes in the reconstructed images and
 * are corrected by analyzing line averages and applying multi-scale corrections.
 */
public class BandingReduction {
    /**
     * Default number of correction passes to apply.
     */
    public static final int DEFAULT_PASS_COUNT = 4;

    /**
     * Default band size in pixels for the correction algorithm.
     */
    public static final int DEFAULT_BAND_SIZE = 24;

    private BandingReduction() {
    }

    /**
     * Which pixels of each line feed the banding correction.
     */
    public enum Mode {
        /** Ignore the solar disk: use all pixels of the line. */
        WHOLE_LINE,
        /** Use only pixels inside the solar disk. */
        INSIDE_DISK,
        /** Use only pixels outside the solar disk. */
        OUTSIDE_DISK
    }

    private static final int MIN_FIT_PIXELS = 16;

    /**
     * Share of a strip which must be usable for its own correction to be trusted entirely. Below
     * this, the strip is progressively replaced by the correction measured over the whole line.
     */
    private static final double TRUSTED_COVERAGE = 0.95;

    /**
     * Share of a strip below which its own correction is discarded entirely: what little it can
     * measure describes the edge of the disk sweeping across it rather than the banding.
     */
    private static final double UNUSABLE_COVERAGE = 0.6;

    /**
     * Target width, in pixels, of the vertical strips used by {@link #removeStripes}. The image is
     * split into strips of roughly this width so that banding whose amplitude varies horizontally
     * can be corrected independently on each strip.
     */
    private static final int STRIP_WIDTH = 256;

    /**
     * Maximum number of vertical strips used by {@link #removeStripes}.
     */
    private static final int MAX_STRIPS = 16;

    /**
     * How many times the magnitude of a line's own correction a strip must stick out from it to be
     * treated as a localized feature rather than banding, and rejected.
     */
    private static final double STRIP_OUTLIER_FACTOR = 4.0;

    /**
     * Lower bound of that magnitude, as a share of the typical correction of the image, so that the
     * lines where the banding happens to vanish do not reject everything.
     */
    private static final double STRIP_OUTLIER_FLOOR = 0.25;

    /**
     * Reduces banding artifacts and returns the average correction magnitude.
     * The algorithm multiplies each line by the ratio between the band average and the line
     * average, at multiple scales, and optionally constrains the operation within an elliptical
     * region. A return value close to 0 means little correction was needed.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param data the image data as a 2D array (modified in place)
     * @param bandSize the size of the bands for correction
     * @param ellipse optional ellipse defining the region to correct, or null for full image
     * @param mode which pixels of each line feed the correction
     * @return the mean absolute deviation of per-line corrections from 1.0
     */
    public static double reduceBanding(int width, int height, float[][] data, int bandSize, Ellipse ellipse, Mode mode) {
        var effectiveEllipse = mode == Mode.WHOLE_LINE ? null : ellipse;
        var outsideDisk = mode == Mode.OUTSIDE_DISK;
        var result = applyMultiplicativeCorrections(width, height, data, bandSize, effectiveEllipse, outsideDisk);
        if (effectiveEllipse != null && !outsideDisk) {
            bilinearSmoothing(effectiveEllipse, width, height, data);
        }
        clampToMax(width, height, data);
        return result;
    }

    /**
     * Removes horizontal banding by matching each line's level to that of its vertical
     * neighbourhood, at successively finer scales. To handle banding whose amplitude varies
     * horizontally, the image is split into vertical strips and each strip is corrected
     * independently; the per-strip corrections are then linearly interpolated across the strip
     * centres so that no vertical seam appears. At each scale, a robust per-line level (the median
     * across the strip) is estimated, a vertically smoothed reference is computed from it, and the
     * difference is added back. Going from coarse to fine scales removes both broad and fine
     * banding, while structures broader than the band size are preserved. A strip which lost too
     * many pixels to measure a level, typically because the solar disk covers it, is blended
     * towards the correction of the whole line by {@link #blendWithGlobalCorrection}, so the
     * correction never fades away next to the disk. {@link #rejectStripOutliers} then discards the
     * corrections caused by a compact bright feature, so it is not mistaken for banding and carved
     * out. The correction is additive, so it works on a background close to zero (unlike a ratio),
     * and it makes no assumption about the image content.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param data the image data as a 2D array (modified in place)
     * @param bandSize the coarsest scale, in pixels, of the vertical smoothing
     * @param ellipse optional ellipse defining the region to correct, or null for full image
     * @param mode which pixels of each line feed the correction
     * @return the mean absolute per-line correction that was applied
     */
    public static double removeStripes(int width, int height, float[][] data, int bandSize, Ellipse ellipse, Mode mode) {
        return removeStripes(width, height, data, bandSize, ellipse, mode, 0);
    }

    /**
     * Removes horizontal banding, using the requested number of vertical strips.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param data the image data as a 2D array (modified in place)
     * @param bandSize the coarsest scale, in pixels, of the vertical smoothing
     * @param ellipse optional ellipse defining the region to correct, or null for full image
     * @param mode which pixels of each line feed the correction
     * @param strips how many vertical strips to correct independently, or 0 to choose from the
     * image width. Use 1 to correct each line by a single value, which trades the ability to
     * follow banding whose strength varies horizontally for a correction free of any horizontal
     * variation, which is preferable when the image contains a large area with no usable pixels,
     * such as the solar disk.
     * @return the mean absolute per-line correction that was applied
     */
    public static double removeStripes(int width, int height, float[][] data, int bandSize, Ellipse ellipse, Mode mode, int strips) {
        var effectiveEllipse = mode == Mode.WHOLE_LINE ? null : ellipse;
        var outsideDisk = mode == Mode.OUTSIDE_DISK;
        var stripCount = strips > 0
                ? Math.min(strips, width)
                : (int) Math.max(1, Math.min(MAX_STRIPS, Math.round(width / (double) STRIP_WIDTH)));
        double total = 0;
        var scales = 0;
        for (var scale = bandSize; scale >= Math.max(4, bandSize / 4); scale /= 2) {
            total += matchLineLevels(width, height, data, scale, effectiveEllipse, outsideDisk, stripCount);
            scales++;
        }
        clampToMax(width, height, data);
        return scales > 0 ? total / scales : 0;
    }

    private static void clampToMax(int width, int height, float[][] data) {
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                data[y][x] = Math.min(data[y][x], Constants.MAX_PIXEL_VALUE);
            }
        }
    }

    private static double applyMultiplicativeCorrections(int width, int height, float[][] data, int bandSize, Ellipse ellipse, boolean outsideDisk) {
        var lineAverages = lineAverages(width, height, data, ellipse, outsideDisk);
        var corrections = computeMultiScaleCorrections(height, lineAverages, bandSize, ellipse);
        double totalDeviation = 0;
        int deviationCount = 0;
        for (var y = 0; y < height; y++) {
            var correction = corrections[y];
            if (!Double.isInfinite(correction) && !Double.isNaN(correction)) {
                totalDeviation += Math.abs(correction - 1.0);
                deviationCount++;
                for (var x = 0; x < width; x++) {
                    if (ellipse == null || outsideDisk != ellipse.isWithin(x, y)) {
                        data[y][x] *= correction;
                    }
                }
            }
        }
        return deviationCount > 0 ? totalDeviation / deviationCount : 0;
    }

    private static double matchLineLevels(int width, int height, float[][] data, int scale, Ellipse ellipse, boolean outsideDisk, int stripCount) {
        var edges = new int[stripCount + 1];
        for (var i = 0; i <= stripCount; i++) {
            edges[i] = (int) Math.round(i * (double) width / stripCount);
        }
        var correction = new double[stripCount][height];
        var coverage = new double[stripCount][height];
        IntStream.range(0, stripCount).parallel().forEach(s ->
                computeStripCorrection(data, edges[s], edges[s + 1], height, scale, ellipse, outsideDisk, correction[s], coverage[s]));
        if (stripCount > 1) {
            var global = new double[height];
            computeStripCorrection(data, 0, width, height, scale, ellipse, outsideDisk, global, new double[height]);
            blendWithGlobalCorrection(correction, coverage, global, stripCount, height);
        }
        var corrected = rejectStripOutliers(correction, stripCount, height);
        removeMean(corrected, stripCount, height);
        var centers = new double[stripCount];
        for (var s = 0; s < stripCount; s++) {
            centers[s] = (edges[s] + edges[s + 1]) / 2.0;
        }
        var totals = IntStream.range(0, height).parallel().mapToObj(y -> {
            var sum = 0.0;
            var count = 0;
            for (var x = 0; x < width; x++) {
                if (ellipse == null || outsideDisk != ellipse.isWithin(x, y)) {
                    var corr = interpolateAcrossStrips(corrected, centers, stripCount, x, y);
                    data[y][x] += (float) corr;
                    sum += Math.abs(corr);
                    count++;
                }
            }
            return new double[]{sum, count};
        }).toList();
        var total = totals.stream().mapToDouble(a -> a[0]).sum();
        var count = totals.stream().mapToDouble(a -> a[1]).sum();
        return count > 0 ? total / count : 0;
    }

    /**
     * Estimates the correction of a vertical strip, and how much of it could be measured on each
     * line. The level of a line is the median of the pixels the strip contributes, the reference is
     * that level smoothed vertically, and the correction is their difference.
     */
    private static void computeStripCorrection(float[][] data, int x0, int x1, int height, int scale, Ellipse ellipse, boolean outsideDisk, double[] correction, double[] coverage) {
        var level = new double[height];
        var present = new boolean[height];
        var values = new double[x1 - x0];
        for (var y = 0; y < height; y++) {
            var n = 0;
            for (var x = x0; x < x1; x++) {
                if (ellipse == null || outsideDisk != ellipse.isWithin(x, y)) {
                    values[n++] = data[y][x];
                }
            }
            coverage[y] = n / (double) (x1 - x0);
            if (n >= MIN_FIT_PIXELS) {
                level[y] = median(values, n);
                present[y] = true;
            }
        }
        interpolateMissing(level, present, height);
        var reference = smoothProfile(level, scale, height);
        for (var y = 0; y < height; y++) {
            correction[y] = reference[y] - level[y];
        }
    }

    /**
     * Replaces the correction of the strips which could not really be measured by the correction of
     * the whole line.
     * <p>
     * Splitting the image into strips is what lets the correction follow banding whose strength
     * varies horizontally, but it only works as long as every strip keeps enough pixels to measure
     * a level. Near the solar disk a strip is reduced to a thin sliver, or to nothing at all, and it
     * then reports almost no banding; interpolating towards such a strip makes the correction fade
     * away as the limb is approached, which leaves the banding visible there and shows up as an
     * abrupt change next to the disk. The correction measured over the whole line does not suffer
     * from this, because it rests on all the pixels the line has, on both sides of the disk.
     * <p>
     * Each strip is therefore blended towards the correction of the whole line as its coverage
     * drops, so a fully visible strip keeps its own correction while a strip hidden by the disk
     * borrows the one of its line.
     */
    private static void blendWithGlobalCorrection(double[][] correction, double[][] coverage, double[] global, int stripCount, int height) {
        for (var s = 0; s < stripCount; s++) {
            for (var y = 0; y < height; y++) {
                var weight = Math.min(1, Math.max(0, (coverage[s][y] - UNUSABLE_COVERAGE) / (TRUSTED_COVERAGE - UNUSABLE_COVERAGE)));
                correction[s][y] = weight * correction[s][y] + (1 - weight) * global[y];
            }
        }
    }

    /**
     * Rejects the corrections caused by a compact bright feature rather than by banding. Banding
     * affects the whole line, so every strip of a line is corrected in the same direction and the
     * spread across strips grows with the amplitude of the stripe, however much that amplitude
     * varies horizontally. A compact feature moves a single strip while the others stay at the
     * level of the line, so it stands out from the magnitude of the line's own correction. Each
     * strip deviating from the median correction of its line by more than
     * {@link #STRIP_OUTLIER_FACTOR} times that magnitude is therefore replaced by the line median,
     * so the feature is not subtracted while a stripe is corrected at every strip, edges included.
     * The threshold scales with the correction itself, so no absolute value has to be tuned.
     */
    private static double[][] rejectStripOutliers(double[][] correction, int stripCount, int height) {
        if (stripCount < 3) {
            return correction;
        }
        var lineMedians = new double[height];
        var buffer = new double[stripCount];
        for (var y = 0; y < height; y++) {
            for (var s = 0; s < stripCount; s++) {
                buffer[s] = correction[s][y];
            }
            lineMedians[y] = median(buffer, stripCount);
        }
        var floor = STRIP_OUTLIER_FLOOR * medianMagnitude(lineMedians, height);
        var result = new double[stripCount][height];
        for (var y = 0; y < height; y++) {
            var lineMedian = lineMedians[y];
            var threshold = STRIP_OUTLIER_FACTOR * Math.max(Math.abs(lineMedian), floor);
            for (var s = 0; s < stripCount; s++) {
                var deviation = correction[s][y] - lineMedian;
                result[s][y] = Math.abs(deviation) > threshold ? lineMedian : correction[s][y];
            }
        }
        return result;
    }

    private static double medianMagnitude(double[] values, int count) {
        var magnitudes = new double[count];
        for (var i = 0; i < count; i++) {
            magnitudes[i] = Math.abs(values[i]);
        }
        return median(magnitudes, count);
    }

    /**
     * Subtracts the mean of the correction field so that removing the banding does not shift the
     * overall brightness of the image: a banding correction should flatten the pattern, not darken
     * or brighten the picture.
     */
    private static void removeMean(double[][] correction, int stripCount, int height) {
        var sum = 0.0;
        for (var s = 0; s < stripCount; s++) {
            for (var y = 0; y < height; y++) {
                sum += correction[s][y];
            }
        }
        var mean = sum / (stripCount * (double) height);
        for (var s = 0; s < stripCount; s++) {
            for (var y = 0; y < height; y++) {
                correction[s][y] -= mean;
            }
        }
    }

    private static double interpolateAcrossStrips(double[][] correction, double[] centers, int stripCount, int x, int y) {
        if (stripCount == 1 || x <= centers[0]) {
            return correction[0][y];
        }
        if (x >= centers[stripCount - 1]) {
            return correction[stripCount - 1][y];
        }
        var s = 0;
        while (s < stripCount - 1 && centers[s + 1] < x) {
            s++;
        }
        var t = (x - centers[s]) / (centers[s + 1] - centers[s]);
        return correction[s][y] * (1 - t) + correction[s + 1][y] * t;
    }

    private static double median(double[] values, int n) {
        Arrays.sort(values, 0, n);
        var mid = n / 2;
        if ((n & 1) == 0) {
            return (values[mid - 1] + values[mid]) / 2.0;
        }
        return values[mid];
    }

    private static void interpolateMissing(double[] values, boolean[] present, int height) {
        var first = -1;
        for (var y = 0; y < height; y++) {
            if (present[y]) {
                first = y;
                break;
            }
        }
        if (first < 0) {
            return;
        }
        for (var y = 0; y < first; y++) {
            values[y] = values[first];
        }
        var prev = first;
        for (var y = first + 1; y < height; y++) {
            if (present[y]) {
                for (var k = prev + 1; k < y; k++) {
                    var t = (double) (k - prev) / (y - prev);
                    values[k] = values[prev] + t * (values[y] - values[prev]);
                }
                prev = y;
            }
        }
        for (var y = prev + 1; y < height; y++) {
            values[y] = values[prev];
        }
    }

    private static double[] smoothProfile(double[] values, double sigma, int height) {
        var kernel = GaussianSupport.gaussianKernel1D(sigma);
        var half = kernel.length / 2;
        var out = new double[height];
        for (var y = 0; y < height; y++) {
            var sum = 0.0;
            var weight = 0.0;
            for (var k = -half; k <= half; k++) {
                var idx = y + k;
                if (idx >= 0 && idx < height) {
                    sum += values[idx] * kernel[k + half];
                    weight += kernel[k + half];
                }
            }
            out[y] = sum / weight;
        }
        return out;
    }

    private static double[] computeMultiScaleCorrections(int height, double[] lineAverages, int baseBandSize, Ellipse ellipse) {
        var bandSizes = new ArrayList<Integer>();
        var currentSize = baseBandSize;
        do {
            bandSizes.add(currentSize);
            currentSize /= 2;
        } while (currentSize >= 4);

        var allCorrections = new double[bandSizes.size()][height];
        for (var i = 0; i < bandSizes.size(); i++) {
            int bandSize = bandSizes.get(i);
            var bandAverages = IntStream.range(0, height)
                    .parallel()
                    .mapToDouble(y -> computeAverageForBand(height, lineAverages, bandSize, y, ellipse))
                    .toArray();

            for (var y = 0; y < height; y++) {
                var bandAverage = bandAverages[y];
                var currentLineAverage = lineAverages[y];
                allCorrections[i][y] = bandAverage / currentLineAverage;
            }
        }

        var finalCorrections = new double[height];
        for (var y = 0; y < height; y++) {
            double sum = 0;
            var count = 0;
            for (var i = 0; i < bandSizes.size(); i++) {
                var correction = allCorrections[i][y];
                if (!Double.isInfinite(correction) && !Double.isNaN(correction)) {
                    sum += correction;
                    count++;
                }
            }
            finalCorrections[y] = count > 0 ? sum / count : 1.0;
        }

        return finalCorrections;
    }

    private static double computeAverageForBand(int height, double[] lineAverages, int bandSize, int y, Ellipse ellipse) {
        var adaptiveBandSize = computeAdaptiveBandSize(bandSize, y, height, ellipse);
        var halfSize = adaptiveBandSize / 2;
        double sum = 0;
        double weightSum = 0;

        for (var k = Math.max(0, y - halfSize); k < Math.min(y + halfSize + 1, height); k++) {
            if (k != y) {
                var lineAverage = lineAverages[k];
                if (Double.isNaN(lineAverage)) {
                    return Double.NaN;
                }
                var weight = computeWeight(y, k, height, ellipse);
                sum += lineAverage * weight;
                weightSum += weight;
            }
        }

        if (weightSum == 0) {
            return Double.NaN;
        }
        return sum / weightSum;
    }

    private static int computeAdaptiveBandSize(int baseBandSize, int y, int height, Ellipse ellipse) {
        if (ellipse == null) {
            return baseBandSize;
        }

        var centerY = ellipse.center().b();
        var poleDistance = Math.abs(y - centerY) / (height / 2.0);
        var poleProximity = Math.pow(poleDistance, 0.8);

        var adaptiveSize = (int) (baseBandSize * (1.0 - 0.5 * poleProximity));
        return Math.max(4, adaptiveSize);
    }

    private static double computeWeight(int centerY, int sampleY, int height, Ellipse ellipse) {
        var distance = Math.abs(centerY - sampleY);
        var baseWeight = 1.0 / (1.0 + distance);

        if (ellipse == null) {
            return baseWeight;
        }

        var centerEllipseY = ellipse.center().b();
        var poleFactor = Math.abs(centerY - centerEllipseY) / (height / 2.0);
        var poleWeight = 1.0 - 0.3 * Math.pow(poleFactor, 2);

        return baseWeight * poleWeight;
    }

    private static double[] lineAverages(int width, int height, float[][] data, Ellipse ellipse, boolean outsideDisk) {
        if (ellipse == null) {
            return ImageMath.newInstance().lineAverages(new Image(width, height, data));
        }
        return IntStream.range(0, height)
                .parallel()
                .mapToDouble(y -> lineAverage(width, y, data, ellipse, outsideDisk))
                .toArray();
    }

    private static double lineAverage(int width, int y, float[][] data, Ellipse ellipse, boolean outsideDisk) {
        double sum = 0;
        var count = 0;
        for (var x = 0; x < width; x++) {
            if (outsideDisk != ellipse.isWithin(x, y)) {
                sum += data[y][x];
                count++;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        return sum / count;
    }
}
