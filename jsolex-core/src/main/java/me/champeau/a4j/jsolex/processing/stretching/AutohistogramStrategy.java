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
    public static final double DEFAULT_BACKGROUND_THRESHOLD = 0.25;
    public static final double DEFAULT_PROM_STRETCH = 0;

    private static final float BLEND_START = 1.00f;
    private static final float BLEND_END = 1.02f;
    private static final int TARGET_AVG = 12000;
    private static final DoubleUnaryOperator BRIGHTNESS_ENHANCE = ColorCurve.cachedPolynomial(100, 128);

    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;
    private final boolean adjustBrightness;
    private final double backgroundThreshold;
    private final double protusStretch;

    public AutohistogramStrategy(double gamma, boolean adjustBrightness, double backgroundThreshold, double protusStretch) {
        if (gamma < 1) {
            throw new IllegalArgumentException("Gamma must be greater than 1");
        }
        if (backgroundThreshold <= 0 || backgroundThreshold > 1) {
            throw new IllegalArgumentException("Background threshold must be in the range (0, 1]");
        }
        if (protusStretch < 0) {
            throw new IllegalArgumentException("Protus stretch must be non-negative");
        }
        this.backgroundThreshold = backgroundThreshold;
        this.gamma = gamma;
        this.adjustBrightness = adjustBrightness;
        this.protusStretch = protusStretch;
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
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            var blackPoint = (float) Math.min(AnalysisUtils.estimateBackgroundLevel(diskData, 1024), AnalysisUtils.estimateBlackPoint(disk, e));
            var cx = e.center().a();
            var cy = e.center().b();
            var semiAxis = e.semiAxis();
            var radius = (semiAxis.a() + semiAxis.b()) / 2;
            var analysis = ImageAnalysis.of(disk, false);
            var max = analysis.max();
            var protus = new float[height][width];
            var stretchedBp = stretch(blackPoint, max);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = diskData[y][x];
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var stretch = stretch(v, max);
                    if (dist >= BLEND_START && dist <= BLEND_END) {
                        protus[y][x] = v;
                        diskData[y][x] = stretch;
                    } else if (dist <= BLEND_END) {
                        diskData[y][x] = stretch;
                    } else {
                        diskData[y][x] = stretch;
                        protus[y][x] = v;
                    }
                }
            }
            analysis = ImageAnalysis.of(disk, true);
            equalize(disk, analysis, TARGET_AVG, 8000);
            analysis = ImageAnalysis.of(disk, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diskData[y][x] = diskData[y][x] - analysis.min();
                }
            }
            // Grow ellipse by 1% to better estimate background
            var rescaledEllipse = e.rescale(1.05, 1.05);
            var protusImage = new ImageWrapper32(width, height, protus, Map.of(Ellipse.class, rescaledEllipse));
            var stats = ImageAnalysis.of(protusImage, false);
            while (stats.avg() / stats.stddev() > backgroundThreshold) {
                neutralizeBg(disk, 2, 1.5, 0.8f);
                if (neutralizeBg(protusImage, 2, 1.5, 0.8f) == 0) {
                    break;
                }
                stats = ImageAnalysis.of(protusImage, false);
            }
            if (protusStretch > 0) {
                stretchProtus(protusImage, blackPoint);
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var prominenceValue = protus[y][x];
                    if (dist >= BLEND_START && dist <= BLEND_END) {
                        // Smooth transition using a cosine blend
                        var alpha = Math.pow(0.5 * (1 + Math.cos(Math.PI * (dist - BLEND_START) / (BLEND_END - BLEND_START))),2);
                        diskData[y][x] = (float) (alpha * diskData[y][x] + (1 - alpha) * prominenceValue);
                    } else if (dist >= BLEND_END) {
                        diskData[y][x] = prominenceValue;
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

    private float stretch(float v, float max) {
        float normalized = v / max;
        var gammaCorrected = (float) pow(normalized, gamma) * MAX_PIXEL_VALUE;
        if (Float.isNaN(gammaCorrected)) {
            return 0;
        }
        return clamp(gammaCorrected, 0, MAX_PIXEL_VALUE);
    }

    private void maybeAdjustBrightness(ImageWrapper32 image, int height, int width, float[][] diskData) {
        if (adjustBrightness) {
            var backup = image.copy();
            var stats = ImageAnalysis.of(image, true);
            int maxIterations = 10;
            while (stats.histogram().cumulative().percentile(0.75f) < TARGET_AVG && maxIterations-- > 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        diskData[y][x] = (float) Math.clamp(BRIGHTNESS_ENHANCE.applyAsDouble(diskData[y][x]), 0, MAX_PIXEL_VALUE);
                    }
                }
                stats = ImageAnalysis.of(image, true);
            }
            if (maxIterations == -1) {
                LOGGER.warn("Could not reach target average after 10 iterations. Skipping brightness adjustment.");
                for (int y = 0; y < height; y++) {
                    System.arraycopy(backup.data()[y], 0, diskData[y], 0, width);
                }
            }
        }
    }

    private void stretchProtus(ImageWrapper32 protusImage, float blackPoint) {
        int height = protusImage.height();
        int width = protusImage.width();

        var protus = protusImage.data();
        var asinh = new ArcsinhStretchingStrategy(blackPoint, (float) protusStretch, protusStretch);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                protus[y][x] = asinh.stretchPixel(protus[y][x]);
            }
        }
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

}
