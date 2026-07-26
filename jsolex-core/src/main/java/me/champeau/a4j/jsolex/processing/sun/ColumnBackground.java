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

import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;
import java.util.function.BiPredicate;

/**
 * Estimates the column illumination of an image: a background level which changes from one column
 * to the next but stays constant along a column.
 * <p>
 * In a spectroheliograph, one column of the reconstructed image is built from one frame of the
 * video, so anything which raises the background of a frame raises that whole column. Light
 * scattered inside the instrument does exactly that, in proportion to the flux entering the slit,
 * so the columns scanned while the slit crosses the solar disk sit above the ones scanned beside
 * it. The result is a dome of excess background, peaking at the middle of the disk and falling
 * steeply just past its edges, which no correction working line by line can see, because it does
 * not vary along a line.
 * <p>
 * The level of each column is measured as the median of its usable pixels, and the model is that
 * profile lightly smoothed along x. No parametric shape is imposed: the steep shoulders at the
 * edges of the disk are followed instead of being rounded off, which matters because a model which
 * overshoots there is subtracted from the inner corona. The median makes each column insensitive
 * to the features crossing it, and the columns deviating strongly from their neighbourhood, for
 * example under a bright streamer, are discarded and interpolated across.
 */
public class ColumnBackground {
    /**
     * Number of pixels a column must keep for its level to be measured. Below this the column is
     * interpolated from its neighbours.
     */
    private static final int MIN_COLUMN_PIXELS = 8;

    /**
     * Number of estimation rounds: each round after the first discards the columns which deviate
     * from the previous smoothed profile by more than the sigma threshold.
     */
    private static final int CLIPPING_ROUNDS = 3;

    private ColumnBackground() {
    }

    /**
     * Estimates the level of each column.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param data the image data as a 2D array, left untouched
     * @param usable which pixels the estimation may use, or null to use them all
     * @param smoothing the standard deviation, in pixels, of the horizontal smoothing of the model
     * @param sigma how many standard deviations a column level may deviate from the smoothed
     * profile before being discarded
     * @return the modelled level of each column
     */
    public static double[] estimate(int width,
                                    int height,
                                    float[][] data,
                                    BiPredicate<Integer, Integer> usable,
                                    double smoothing,
                                    double sigma) {
        var uniformRow = uniformRows(width, height, data);
        var uniformColumn = uniformColumns(width, height, data);
        var medians = new double[width];
        var present = new boolean[width];
        var buffer = new double[height];
        for (var x = 0; x < width; x++) {
            if (uniformColumn[x]) {
                continue;
            }
            var n = 0;
            for (var y = 0; y < height; y++) {
                if (!uniformRow[y] && (usable == null || usable.test(x, y))) {
                    buffer[n++] = data[y][x];
                }
            }
            if (n >= MIN_COLUMN_PIXELS) {
                medians[x] = BandingReduction.median(buffer, n);
                present[x] = true;
            }
        }
        return smoothWithRejection(width, medians, present, smoothing, sigma);
    }

    /**
     * Share of the semi-axis a column must sit beyond, on either side of the disk, to take part in
     * the normalization reference: those columns receive no scattered light, so their level is the
     * plain sky the model is normalized to.
     */
    private static final double NORMALIZATION_MARGIN = 1.05;

    /**
     * Scales the levels so that the columns beside the disk sit at 1: the model can then divide an
     * image without changing its scale, only the columns under the dome being reduced.
     *
     * @param levels the modelled level of each column, modified in place
     * @param ellipse the solar disk, or null to normalize by the darkest quartile of the levels
     */
    public static void normalizeLevels(double[] levels, Ellipse ellipse) {
        var reference = referenceLevel(levels, ellipse);
        if (reference <= 0) {
            throw new IllegalArgumentException("Cannot normalize the column illumination model because the sky level beside the disk is not positive; compute the model on an image which carries the illumination, such as the continuum");
        }
        for (var x = 0; x < levels.length; x++) {
            levels[x] /= reference;
        }
    }

    private static double referenceLevel(double[] levels, Ellipse ellipse) {
        if (ellipse != null) {
            var cx = ellipse.center().a();
            var reach = NORMALIZATION_MARGIN * Math.max(ellipse.semiAxis().a(), ellipse.semiAxis().b());
            var beside = new double[levels.length];
            var n = 0;
            for (var x = 0; x < levels.length; x++) {
                if (Math.abs(x - cx) > reach) {
                    beside[n++] = levels[x];
                }
            }
            if (n >= MIN_COLUMN_PIXELS) {
                return BandingReduction.median(beside, n);
            }
        }
        var sorted = Arrays.copyOf(levels, levels.length);
        Arrays.sort(sorted);
        return BandingReduction.median(sorted, Math.max(1, sorted.length / 4));
    }

    /**
     * Builds a model image out of column levels, each column being filled with its own level.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param levels the level of each column
     * @return the model
     */
    public static float[][] toImage(int width, int height, double[] levels) {
        var model = new float[height][width];
        for (var x = 0; x < width; x++) {
            var level = (float) levels[x];
            for (var y = 0; y < height; y++) {
                model[y][x] = level;
            }
        }
        return model;
    }

    /**
     * Smooths the measured column levels, discarding the columns which stand out from the smoothed
     * profile. The missing columns, discarded or never measured, are linearly interpolated from
     * their neighbours before each smoothing so they do not distort it.
     */
    private static double[] smoothWithRejection(int width, double[] medians, boolean[] present, double smoothing, double sigma) {
        var kept = Arrays.copyOf(present, width);
        var levels = new double[width];
        for (var round = 0; round < CLIPPING_ROUNDS; round++) {
            var filled = Arrays.copyOf(medians, width);
            BandingReduction.interpolateMissing(filled, kept);
            levels = gaussianSmooth(filled, smoothing);
            var ss = 0.0;
            var count = 0;
            for (var x = 0; x < width; x++) {
                if (kept[x]) {
                    var residual = medians[x] - levels[x];
                    ss += residual * residual;
                    count++;
                }
            }
            if (count == 0) {
                return new double[width];
            }
            var threshold = sigma * Math.sqrt(ss / count);
            var clipped = false;
            for (var x = 0; x < width; x++) {
                if (kept[x] && Math.abs(medians[x] - levels[x]) > threshold) {
                    kept[x] = false;
                    clipped = true;
                }
            }
            if (!clipped) {
                break;
            }
        }
        return levels;
    }

    /**
     * Smooths a profile with a Gaussian kernel. The values beyond the bounds are extrapolated by
     * odd reflection, which preserves the local slope: simply dropping the missing samples would
     * bend the ends of a sloped profile towards its interior.
     */
    private static double[] gaussianSmooth(double[] values, double sigma) {
        var radius = Math.max(1, (int) Math.ceil(3 * sigma));
        var kernel = new double[2 * radius + 1];
        var sum = 0.0;
        for (var i = -radius; i <= radius; i++) {
            var v = Math.exp(-(i * i) / (2 * sigma * sigma));
            kernel[i + radius] = v;
            sum += v;
        }
        for (var i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        var length = values.length;
        var out = new double[length];
        for (var i = 0; i < length; i++) {
            var s = 0.0;
            for (var k = -radius; k <= radius; k++) {
                s += reflectedValue(values, i + k) * kernel[k + radius];
            }
            out[i] = s;
        }
        return out;
    }

    private static double reflectedValue(double[] values, int idx) {
        var last = values.length - 1;
        if (idx < 0) {
            return 2 * values[0] - values[Math.min(-idx, last)];
        }
        if (idx > last) {
            return 2 * values[last] - values[Math.max(2 * last - idx, 0)];
        }
        return values[idx];
    }

    /**
     * Flags the rows holding a single value. Cropping to a field larger than the frame pads the
     * image with a constant, and such a row says nothing about the background while its value,
     * repeated thousands of times, would dominate every median it takes part in.
     */
    private static boolean[] uniformRows(int width, int height, float[][] data) {
        var uniform = new boolean[height];
        for (var y = 0; y < height; y++) {
            uniform[y] = true;
            var first = data[y][0];
            for (var x = 1; x < width; x++) {
                if (data[y][x] != first) {
                    uniform[y] = false;
                    break;
                }
            }
        }
        return uniform;
    }

    /**
     * Flags the columns holding a single value, for the same reason as {@link #uniformRows}. Their
     * level is not measured, the smoothed profile interpolates it from the neighbouring columns.
     */
    private static boolean[] uniformColumns(int width, int height, float[][] data) {
        var uniform = new boolean[width];
        for (var x = 0; x < width; x++) {
            uniform[x] = true;
            var first = data[0][x];
            for (var y = 1; y < height; y++) {
                if (data[y][x] != first) {
                    uniform[x] = false;
                    break;
                }
            }
        }
        return uniform;
    }

}
