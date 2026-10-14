/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.RowStrips;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Undecimated "à trous" wavelet transform with a B3 spline kernel (starlet). Each scale is the
 * difference between two successive smoothings, the step of the kernel doubling at each scale, so
 * that the sum of all the scales and of the residual gives back the image. Pixels outside the mask
 * do not take part in the smoothing, which is normalized by the weight of the pixels it covers.
 */
public final class AtrousWavelet {
    private static final float[] B3 = {1 / 16f, 4 / 16f, 6 / 16f, 4 / 16f, 1 / 16f};
    private static final double GAUSSIAN_MEAN_ABS_TO_SIGMA = Math.sqrt(Math.PI / 2);
    private static final double CLIPPING = 3;
    private static final int NOISE_ESTIMATION_LEVELS = 3;

    private final int width;
    private final int height;
    private final float[][] weights;
    private final int count;
    private final int levels;
    private final float[][][] normalization;

    private AtrousWavelet(int width, int height, float[][] data, BiPredicate<Integer, Integer> mask, int levels) {
        this.width = width;
        this.height = height;
        this.levels = levels;
        this.weights = new float[height][width];
        var inMask = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isFinite(data[y][x]) && (mask == null || mask.test(x, y))) {
                    weights[y][x] = 1;
                    inMask++;
                }
            }
        }
        this.count = inMask;
        this.normalization = new float[levels][][];
        for (int j = 0; j < levels; j++) {
            normalization[j] = convolve(weights, 1 << j);
        }
    }

    /**
     * Returns the default number of scales of an image, so that the coarsest kernel still fits in it.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @return the number of scales
     */
    public static int defaultLevels(int width, int height) {
        var size = Math.min(width, height);
        return Math.max(1, 31 - Integer.numberOfLeadingZeros(size) - 3);
    }

    /**
     * Decomposes an image into wavelet scales.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @param data the image data, which is not modified
     * @param mask the pixels to decompose, or null for the whole image
     * @param levels the number of scales
     * @return the scales, from the finest to the coarsest, followed by the residual. Outside the
     * mask, the scales are 0 and the residual is the image.
     */
    public static List<float[][]> decompose(int width, int height, float[][] data, BiPredicate<Integer, Integer> mask, int levels) {
        var wavelet = new AtrousWavelet(width, height, data, mask, levels);
        var result = new ArrayList<float[][]>();
        var residual = wavelet.transform(data, (level, scale) -> {
            wavelet.restoreOutsideMask(scale, null);
            result.add(scale);
        });
        wavelet.restoreOutsideMask(residual, data);
        result.add(residual);
        return result;
    }

    /**
     * Removes the noise of an image. Wavelet coefficients are kept only when they exceed the
     * universal threshold {@code σ·sqrt(2·ln N)}, where N is the number of pixels and σ the local
     * noise level, estimated on the image itself, so that virtually no noise coefficient survives.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @param data the image data, which is not modified
     * @param mask the pixels to denoise, or null for the whole image. The other pixels are left unchanged.
     * @param grain the fraction of the removed noise which is added back, between 0 and 1
     * @return the denoised image data
     */
    public static float[][] denoise(int width, int height, float[][] data, BiPredicate<Integer, Integer> mask, double grain) {
        var wavelet = new AtrousWavelet(width, height, data, mask, defaultLevels(width, height));
        if (wavelet.count < 2) {
            return ImageWrapper.copyData(data);
        }
        return wavelet.denoise(data, Math.sqrt(2 * Math.log(wavelet.count)), grain);
    }

    private float[][] denoise(float[][] data, double threshold, double grain) {
        var noiseFactors = noiseFactors(levels);
        var sigmas = noiseSigmas(data, noiseFactors);
        var result = new float[height][width];
        var coarse = transform(data, (level, scale) -> {
            var scaleThreshold = threshold * noiseFactors[level];
            var sigma = level == 0 ? sigmas.finest() : sigmas.coarser();
            RowStrips.forEach(height, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    for (int x = 0; x < width; x++) {
                        var coefficient = scale[y][x];
                        if (Math.abs(coefficient) > scaleThreshold * sigma[y][x]) {
                            result[y][x] += coefficient;
                        }
                    }
                }
            });
        });
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    var denoised = result[y][x] + coarse[y][x];
                    result[y][x] = (float) (denoised + grain * (data[y][x] - denoised));
                }
            }
        });
        restoreOutsideMask(result, data);
        return result;
    }

    private float[][] transform(float[][] data, ScaleConsumer consumer) {
        var current = data;
        for (int j = 0; j < levels; j++) {
            var next = smooth(current, j);
            var scale = new float[height][width];
            var c = current;
            RowStrips.forEach(height, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    for (int x = 0; x < width; x++) {
                        scale[y][x] = c[y][x] - next[y][x];
                    }
                }
            });
            consumer.accept(j, scale);
            current = next;
        }
        return current;
    }

    private void restoreOutsideMask(float[][] target, float[][] source) {
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    if (weights[y][x] == 0) {
                        target[y][x] = source == null ? 0 : source[y][x];
                    }
                }
            }
        });
    }

    /**
     * Estimates the local noise level on the two finest scales, where the signal of smooth
     * structures is negligible. If the noise of neighbouring pixels is independent, both give the
     * same level. When it is correlated, for example after an interpolation, the finest scale
     * carries less noise than the coarser ones, so these use the larger of the two estimates.
     */
    private NoiseSigmas noiseSigmas(float[][] data, double[] noiseFactors) {
        var smoothed = smooth(data, 0);
        var finest = localSigma(absoluteDifference(data, smoothed), noiseFactors[0]);
        if (levels < 2) {
            return new NoiseSigmas(finest, finest);
        }
        var second = localSigma(absoluteDifference(smoothed, smooth(smoothed, 1)), noiseFactors[1]);
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    second[y][x] = Math.max(second[y][x], finest[y][x]);
                }
            }
        });
        return new NoiseSigmas(finest, second);
    }

    private float[][] absoluteDifference(float[][] a, float[][] b) {
        var result = new float[height][width];
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] = Math.abs(a[y][x] - b[y][x]);
                }
            }
        });
        return result;
    }

    /**
     * Estimates the local noise level from the absolute values of the coefficients of a scale.
     * Values beyond a few sigmas, caused by sharp edges or defective pixels, are clipped so that
     * they don't inflate the estimate. The mean is taken over a few hundred pixels, enough for a
     * precision of about 5 %, while following the variations of the noise across the image.
     */
    private float[][] localSigma(float[][] absolute, double factor) {
        var level = smoothLevels(absolute, Math.min(levels, NOISE_ESTIMATION_LEVELS));
        for (int pass = 0; pass < 2; pass++) {
            var clipped = new float[height][width];
            var mean = level;
            RowStrips.forEach(height, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    for (int x = 0; x < width; x++) {
                        clipped[y][x] = (float) Math.min(absolute[y][x], CLIPPING * GAUSSIAN_MEAN_ABS_TO_SIGMA * mean[y][x]);
                    }
                }
            });
            level = smoothLevels(clipped, Math.min(levels, NOISE_ESTIMATION_LEVELS));
        }
        var sigma = level;
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    sigma[y][x] = (float) (sigma[y][x] * GAUSSIAN_MEAN_ABS_TO_SIGMA / factor);
                }
            }
        });
        return sigma;
    }

    private float[][] smoothLevels(float[][] data, int count) {
        var current = data;
        for (int j = 0; j < count; j++) {
            current = smooth(current, j);
        }
        return current;
    }

    private float[][] smooth(float[][] data, int level) {
        var weighted = new float[height][width];
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    weighted[y][x] = weights[y][x] == 0 ? 0 : data[y][x];
                }
            }
        });
        var result = convolve(weighted, 1 << level);
        var norm = normalization[level];
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] = norm[y][x] > 0 ? result[y][x] / norm[y][x] : data[y][x];
                }
            }
        });
        return result;
    }

    private float[][] convolve(float[][] data, int step) {
        var horizontal = new float[height][width];
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                var row = data[y];
                for (int x = 0; x < width; x++) {
                    var sum = 0f;
                    for (int k = -2; k <= 2; k++) {
                        var xx = x + k * step;
                        if (xx >= 0 && xx < width) {
                            sum += B3[k + 2] * row[xx];
                        }
                    }
                    horizontal[y][x] = sum;
                }
            }
        });
        var result = new float[height][width];
        RowStrips.forEach(height, (y0, y1) -> {
            for (int y = y0; y < y1; y++) {
                for (int k = -2; k <= 2; k++) {
                    var yy = y + k * step;
                    if (yy >= 0 && yy < height) {
                        var source = horizontal[yy];
                        var coefficient = B3[k + 2];
                        for (int x = 0; x < width; x++) {
                            result[y][x] += coefficient * source[x];
                        }
                    }
                }
            }
        });
        return result;
    }

    /**
     * Computes, for each scale, the standard deviation of the coefficients of a white noise of
     * unit standard deviation, from the impulse response of the transform. The 2D smoothing
     * kernels being separable, the squared norm of a scale is
     * {@code S(j,j)² - 2·S(j,j+1)² + S(j+1,j+1)²}, where S is the dot product of the 1D kernels.
     */
    static double[] noiseFactors(int levels) {
        var kernels = new double[levels + 1][];
        kernels[0] = new double[]{1};
        for (int j = 0; j < levels; j++) {
            var previous = kernels[j];
            var step = 1 << j;
            var next = new double[previous.length + 4 * step];
            for (int i = 0; i < previous.length; i++) {
                for (int k = 0; k < 5; k++) {
                    next[i + k * step] += previous[i] * B3[k];
                }
            }
            kernels[j + 1] = next;
        }
        var factors = new double[levels];
        for (int j = 0; j < levels; j++) {
            var same = dot(kernels[j], kernels[j]);
            var cross = dot(kernels[j], kernels[j + 1]);
            var nextSame = dot(kernels[j + 1], kernels[j + 1]);
            factors[j] = Math.sqrt(same * same - 2 * cross * cross + nextSame * nextSame);
        }
        return factors;
    }

    private static double dot(double[] a, double[] b) {
        var offset = (b.length - a.length) / 2;
        var sum = 0d;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i + offset];
        }
        return sum;
    }

    @FunctionalInterface
    private interface ScaleConsumer {
        void accept(int level, float[][] scale);
    }

    private record NoiseSigmas(float[][] finest, float[][] coarser) {
    }
}
