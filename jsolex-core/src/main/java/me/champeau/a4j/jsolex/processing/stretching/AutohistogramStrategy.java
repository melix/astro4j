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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.expr.impl.Utilities;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.FloatPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import static java.lang.Math.clamp;
import static java.lang.Math.pow;
import static me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast.equalize;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutohistogramStrategy.class);

    private static final float BLEND_START = 1f;
    private static final float BLEND_END = 1.05f;
    private static final int TARGET_AVG = 12000;
    private static final DoubleUnaryOperator BRIGHTNESS_ENHANCE = ColorCurve.cachedPolynomial(100, 128);

    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;
    private final boolean adjustBrightness;

    public AutohistogramStrategy(double gamma, boolean adjustBrightness) {
        if (gamma < 1) {
            throw new IllegalArgumentException("Gamma must be greater than 1");
        }
        this.gamma = gamma;
        this.adjustBrightness = adjustBrightness;
    }

    /**
     * Stretches an image using automatic parameters.
     *
     * @param image grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(ImageWrapper32 image) {
        var disk = image.copy();
        var height = image.height();
        var width = image.width();
        var diskData = disk.data();
        var avg = neutralizeBg(disk, 3, 2.5, 0.9f);
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            var blackPoint = (float) AnalysisUtils.estimateBlackPoint(disk, e) * 1.2f;
            var backgroundMask = createBackgroundMask(height, width, diskData, blackPoint);
            var cx = e.center().a();
            var cy = e.center().b();
            var semiAxis = e.semiAxis();
            var radius = (semiAxis.a() + semiAxis.b()) / 2;
            float max = 1e-7f;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    max = Math.max(diskData[y][x], max);
                }
            }
            var protus = new float[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = diskData[y][x];
                    float normalized = v / max;
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var gammaCorrected = (float) pow(normalized, gamma) * MAX_PIXEL_VALUE;
                    var v1 = clamp(gammaCorrected, 0, MAX_PIXEL_VALUE);
                    if (dist <= BLEND_START) {
                        diskData[y][x] = v1;
                    } else {
                        diskData[y][x] = 0;
                        protus[y][x] = v;
                    }
                }
            }

            var analysis = ImageAnalysis.of(disk, true);
            equalize(disk, analysis, TARGET_AVG, 8000);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diskData[y][x] = backgroundMask[y][x] ? blackPoint : diskData[y][x];
                }
            }
            // Grow ellipse by 1% to better estimate background
            var rescaledEllipse = e.rescale(1.001, 1.001);
            var protusImage = new ImageWrapper32(width, height, protus, Map.of(Ellipse.class, rescaledEllipse));
            var limit = clampLimits(diskData, 0, 0.9998).b();
            stretchProtus(protusImage, blackPoint, limit);
            var stats = ImageAnalysis.of(protusImage, false);
            while (stats.avg() / stats.stddev() > 0.2) {
                if (neutralizeBg(protusImage, 2, 1.5, 0.8f) == 0) {
                    break;
                }
                stats = ImageAnalysis.of(protusImage, false);
            }

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    if (dist >= BLEND_START && dist <= BLEND_END) {
                        // Smooth transition using a cosine blend
                        var alpha = (0.5 * (1 + Math.cos(Math.PI * (dist - BLEND_START) / (BLEND_END - BLEND_START))));
                        var prominenceValue = protus[y][x];
                        diskData[y][x] = (float) (alpha * diskData[y][x] + (1 - alpha) * prominenceValue);
                    } else if (dist > BLEND_END) {
                        diskData[y][x] = protus[y][x];
                    }
                }
            }
            for (int y = 0; y < height; y++) {
                System.arraycopy(diskData[y], 0, image.data()[y], 0, width);
            }
        } else {
            new GammaStrategy(gamma).stretch(disk);
            for (int y = 0; y < height; y++) {
                System.arraycopy(diskData[y], 0, image.data()[y], 0, width);
            }
        }
        diskData = image.data();
        clamping(diskData, 0, .9998).stretch(image);
        LinearStrechingStrategy.DEFAULT.stretch(image);
        maybeAdjustBrightness(image, height, width, diskData);
    }

    private void maybeAdjustBrightness(ImageWrapper32 image, int height, int width, float[][] diskData) {
        if (adjustBrightness) {
            var stats = ImageAnalysis.of(image, true);
            while (stats.histogram().cumulative().percentile(0.25f) < TARGET_AVG) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        diskData[y][x] = (float) Math.clamp(BRIGHTNESS_ENHANCE.applyAsDouble(diskData[y][x]), 0, MAX_PIXEL_VALUE);
                    }
                }
                stats = ImageAnalysis.of(image, true);
            }
        }
    }

    private void stretchProtus(ImageWrapper32 protusImage, float blackPoint, float max) {
        int height = protusImage.height();
        int width = protusImage.width();

        var protus = protusImage.data();
        var asinh = new ArcsinhStretchingStrategy(blackPoint, 5f, 1.5f);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                protus[y][x] = asinh.stretchPixel(protus[y][x]);
            }
        }
        protusClamping(protusImage, 0, .9998).stretch(protusImage);
        new LinearStrechingStrategy(0, 0.8f * max).stretch(protusImage);
    }

    private static boolean[][] createBackgroundMask(int height, int width, float[][] diskData, float blackPoint) {
        var backgroundMask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                backgroundMask[y][x] = diskData[y][x] < blackPoint;
            }
        }
        return backgroundMask;
    }

    public static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing) {
        var diskData = disk.data();
        var optionalModel = BackgroundRemoval.backgroundModel(disk, degree, sigma);
        if (optionalModel.isPresent()) {
            var data = optionalModel.get().data();
            double avg = 0;
            for (int y = 0; y < disk.height(); y++) {
                for (int x = 0; x < disk.width(); x++) {
                    float v = data[y][x];
                    avg += v;
                    diskData[y][x] = Math.clamp(diskData[y][x] - smoothing * v, 0, MAX_PIXEL_VALUE);
                }
            }
            avg = avg / (disk.width() * disk.height());
            return avg;
        }
        return 0;
    }

    private static FloatPair clampLimits(float[][] diskData, double loPercentile, double hiPercentile) {
        var cumulative = Histogram.of(diskData, 65536).cumulative();
        var lo = cumulative.percentile(loPercentile);
        var hi = cumulative.percentile(hiPercentile);
        return new FloatPair((float) lo, (float) hi);
    }

    private static ContrastAdjustmentStrategy clamping(float[][] diskData, double loPercentile, double hiPercentile) {
        var limits = clampLimits(diskData, loPercentile, hiPercentile);
        return new ContrastAdjustmentStrategy(limits.a(), limits.b());
    }

    private static ContrastAdjustmentStrategy protusClamping(ImageWrapper32 protusImage, double loPercentile, double hiPercentile) {
        var builder = Histogram.builder(65536);
        var ellipse = protusImage.findMetadata(Ellipse.class).get();
        for (int y = 0; y < protusImage.height(); y++) {
            for (int x = 0; x < protusImage.width(); x++) {
                if (!ellipse.isWithin(x, y)) {
                    builder.record(protusImage.data()[y][x]);
                }
            }
        }
        var cumulative = builder.build().cumulative();
        var lo = cumulative.percentile(loPercentile);
        var hi = cumulative.percentile(hiPercentile);
        return new ContrastAdjustmentStrategy(lo, hi);
    }

}
