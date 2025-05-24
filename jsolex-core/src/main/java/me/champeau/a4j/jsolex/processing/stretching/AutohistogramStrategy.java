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
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.FloatPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutohistogramStrategy.class);
    public static final double DEFAULT_BACKGROUND_THRESHOLD = 0.5;
    public static final double DEFAULT_PROM_STRETCH = 0;

    private static final int TARGET_AVG = 18000;
    private static final DoubleUnaryOperator BRIGHTNESS_ENHANCE = ColorCurve.cachedPolynomial(100, 128);
    private static final ClaheStrategy CLAHE_STRATEGY = new ClaheStrategy(16, 64, 1.1);
    private static final StretchingStrategy PROTUS_STRATEGY = new ClaheStrategy(8, 64, 0.8);

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
        var height = image.height();
        var width = image.width();
        var ellipse = image.findMetadata(Ellipse.class);
        var output = image.data();
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            var bgNeutralized = iterativeBgNeutralize(image, e, width, height);
            var clahe = bgNeutralized.copy();
            CLAHE_STRATEGY.stretch(clahe);
            var eclipse = createEclipse(bgNeutralized, e.rescale(1.005, 1.005), width, height);
            PROTUS_STRATEGY.stretch(eclipse);
            var eclipseData = eclipse.data();
            var expand = ColorCurve.cachedPolynomial(16, (int) Math.clamp(16 * (1 + protusStretch), 16, 255));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    eclipseData[y][x] = (float) expand.applyAsDouble(eclipseData[y][x]);
                }
            }
            var claheData = blend(clahe.data(), eclipseData, width, height, 0.6);
            new GammaStrategy(gamma).stretch(bgNeutralized);
            var blended = blend(bgNeutralized.data(), claheData, width, height, 0.8);
            for (int y = 0; y < height; y++) {
                System.arraycopy(blended[y], 0, output[y], 0, width);
            }
        }
        clamping(output, 0, .9998).stretch(image);
        maybeAdjustBrightness(image, height, width, output);
        RangeExpansionStrategy.DEFAULT.stretch(image);
    }

    private static float[][] blend(float[][] img1, float[][] img2, int width, int height, double alpha) {
        double beta = 1 - alpha;
        var blended = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v1 = img1[y][x];
                var v2 = img2[y][x];
                blended[y][x] = (float) (alpha * v1 + beta * v2);
            }
        }
        return blended;
    }

    private static ImageWrapper32 createEclipse(ImageWrapper32 denoised, Ellipse e, int width, int height) {
        var eclipse = denoised.copy();
        var bb = e.boundingBox();
        var minX = (int) Math.max(0, bb.a());
        var maxX = (int) Math.min(width - 1, bb.b());
        var minY = (int) Math.max(0, bb.c());
        var maxY = (int) Math.min(height - 1, bb.d());
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                if (e.isWithin(x, y)) {
                    eclipse.data()[y][x] = 0;
                }
            }
        }
        return eclipse;
    }

    private ImageWrapper32 iterativeBgNeutralize(ImageWrapper32 image, Ellipse e, int width, int height) {
        var denoised = image.copy();
        if (backgroundThreshold >= 1) {
            return image;
        }
        var rescaledEllipse = e.rescale(1.05, 1.05);
        denoised = new ImageWrapper32(width, height, denoised.data(), Map.of(Ellipse.class, rescaledEllipse));
        var eclipse = createEclipse(denoised, rescaledEllipse, width, height);
        var prevBg = Double.MAX_VALUE;
        var smoothing = (float) (1 - backgroundThreshold);
        var pedestral = -1d;
        int maxIterations = 25;
        boolean foundPedestral = false;
        while (--maxIterations >= 0) {
            var copy = denoised.copy();
            neutralizeBg(copy, 2, 1.5, smoothing, null);
            double bg = neutralizeBg(eclipse, 2, 1.5, smoothing, null);
            if (bg == 0 || bg > prevBg) {
                break;
            }
            LOGGER.debug("Background neutralization: {} -> {}", prevBg, bg);
            prevBg = bg;
            denoised = copy;
            if (pedestral == -1 || (!foundPedestral && bg < 0.5 * pedestral)) {
                pedestral = bg;
            } else {
                foundPedestral = true;
            }
        }
        // Background neutralization can result in the image being clamped to 0, which is unnatural,
        // so we're adding a pedestal to the image.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                denoised.data()[y][x] += pedestral / 2;
            }
        }
        return denoised;
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

    public static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing, Ellipse e) {
        var diskData = disk.data();
        var optionalModel = BackgroundRemoval.backgroundModel(disk, degree, sigma);
        if (optionalModel.isPresent()) {
            var data = optionalModel.get().data();
            double avg = 0;
            for (int y = 0; y < disk.height(); y++) {
                for (int x = 0; x < disk.width(); x++) {
                    float v = data[y][x];
                    avg += v;
                    if (e == null || !e.isWithin(x, y)) {
                        float smoothed = smoothing * v;
                        diskData[y][x] = Math.clamp(diskData[y][x] - smoothed, 0, MAX_PIXEL_VALUE);
                    }
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
