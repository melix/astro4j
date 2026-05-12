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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * Automatic histogram stretching strategy with background neutralization and brightness adjustment.
 */
public final class AutohistogramStrategy implements StretchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutohistogramStrategy.class);

    /**
     * Default background threshold for neutralization.
     */
    public static final double DEFAULT_BACKGROUND_THRESHOLD = 0.5;

    /**
     * Default prominence stretch factor.
     */
    public static final double DEFAULT_PROM_STRETCH = 0;

    private static final int TARGET_AVG = 18000;
    private static final DoubleUnaryOperator BRIGHTNESS_ENHANCE = ColorCurve.cachedPolynomial(100, 128);
    private static final ClaheStrategy CLAHE_STRATEGY = new ClaheStrategy(16, 64, 1.1);
    private static final StretchingStrategy PROTUS_STRATEGY = new ClaheStrategy(8, 64, 0.8);
    private static final double HIGHLIGHT_KNEE_PERCENTILE = 0.995;

    /**
     * Default gamma correction value.
     */
    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;
    private final boolean adjustBrightness;
    private final double backgroundThreshold;
    private final double protusStretch;

    /**
     * Creates a new autohistogram stretching strategy.
     *
     * @param gamma               the gamma correction value (must be greater than 1)
     * @param adjustBrightness    whether to adjust brightness automatically
     * @param backgroundThreshold the background neutralization threshold (0 to 1)
     * @param protusStretch       the prominence stretch factor (must be non-negative)
     */
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
            // Take both copies before forking: from this point clahe, eclipse and
            // bgNeutralized hold disjoint buffers, so the three stretches that
            // follow can run in parallel without aliasing each other.
            var clahe = bgNeutralized.copy();
            var eclipse = createEclipse(bgNeutralized, e.rescale(1.005, 1.005), width, height);
            var claheTask = CompletableFuture.runAsync(() -> CLAHE_STRATEGY.stretch(clahe));
            var eclipseTask = CompletableFuture.runAsync(() -> {
                PROTUS_STRATEGY.stretch(eclipse);
                var eclipseData = eclipse.data();
                var expand = ColorCurve.cachedPolynomial(16, (int) Math.clamp(16 * (1 + protusStretch), 16, 255));
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        eclipseData[y][x] = (float) expand.applyAsDouble(eclipseData[y][x]);
                    }
                }
            });
            new GammaStrategy(gamma).stretch(bgNeutralized);
            CompletableFuture.allOf(claheTask, eclipseTask).join();
            var claheData = new float[height][width];
            var blended = new float[height][width];
            blendInto(clahe.data(), eclipse.data(), width, height, 0.6, claheData);
            blendInto(bgNeutralized.data(), claheData, width, height, 0.8, blended);
            for (int y = 0; y < height; y++) {
                System.arraycopy(blended[y], 0, output[y], 0, width);
            }
        }
        softKneeHighlights(output, 0, HIGHLIGHT_KNEE_PERCENTILE);
        maybeAdjustBrightness(image, height, width, output);
        RangeExpansionStrategy.DEFAULT.stretch(image);
        CutoffStretchingStrategy.DEFAULT.stretch(image);
    }

    private static void blendInto(float[][] img1, float[][] img2, int width, int height, double alpha, float[][] out) {
        double beta = 1 - alpha;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v1 = img1[y][x];
                var v2 = img2[y][x];
                out[y][x] = (float) (alpha * v1 + beta * v2);
            }
        }
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
        if (backgroundThreshold >= 1) {
            return image;
        }
        var rescaledEllipse = e.rescale(1.05, 1.05);
        var meta = Map.<Class<?>, Object>of(Ellipse.class, rescaledEllipse);
        // Ping-pong between two buffers across the iteration loop instead of allocating
        // a fresh float[h][w] copy on every iteration. Each iteration reads from the
        // "current" buffer and writes into the "scratch" buffer; on accept they swap.
        var currentData = ImageWrapper.copyData(image.data());
        var scratchData = new float[height][width];
        var current = new ImageWrapper32(width, height, currentData, meta);
        var scratch = new ImageWrapper32(width, height, scratchData, meta);
        var eclipse = createEclipse(current, rescaledEllipse, width, height);
        // Shared scratch buffer for the polynomial background model used by both
        // neutralizeBg calls below; reused across all iterations.
        var bgModelBuffer = new float[height][width];
        var prevBg = Double.MAX_VALUE;
        var smoothing = (float) (1 - backgroundThreshold);
        var pedestral = -1d;
        int maxIterations = 25;
        boolean foundPedestral = false;
        while (--maxIterations >= 0) {
            for (int y = 0; y < height; y++) {
                System.arraycopy(currentData[y], 0, scratchData[y], 0, width);
            }
            neutralizeBg(scratch, 2, 1.5, smoothing, null, bgModelBuffer);
            double bg = neutralizeBg(eclipse, 2, 1.5, smoothing, null, bgModelBuffer);
            if (bg == 0 || bg > prevBg) {
                break;
            }
            LOGGER.debug("Background neutralization: {} -> {}", prevBg, bg);
            prevBg = bg;
            var tmpData = currentData;
            currentData = scratchData;
            scratchData = tmpData;
            var tmp = current;
            current = scratch;
            scratch = tmp;
            if (pedestral == -1 || (!foundPedestral && bg < 0.5 * pedestral)) {
                pedestral = bg;
            } else {
                foundPedestral = true;
            }
        }
        var denoised = current;
        if (pedestral > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    denoised.data()[y][x] += pedestral / 2;
                }
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

    /**
     * Neutralizes the background of an image using polynomial background modeling.
     *
     * @param disk      the image to process
     * @param degree    the polynomial degree for background modeling
     * @param sigma     the sigma value for outlier rejection
     * @param smoothing the smoothing factor for background subtraction
     * @param e         optional ellipse to exclude from background calculation
     * @return the average background level after neutralization
     */
    public static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing, Ellipse e) {
        return neutralizeBg(disk, degree, sigma, smoothing, e, new float[disk.height()][disk.width()]);
    }

    /**
     * Same as {@link #neutralizeBg(ImageWrapper32, int, double, float, Ellipse)} but
     * uses a caller-supplied buffer for the background model, avoiding per-call
     * {@code float[h][w]} allocation. The buffer must have dimensions matching
     * {@code disk}; its prior contents are fully overwritten.
     */
    public static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing, Ellipse e, float[][] backgroundBuffer) {
        var diskData = disk.data();
        var optionalModel = BackgroundRemoval.backgroundModel(disk, degree, sigma, backgroundBuffer);
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

    // Reinhard-style rational soft-knee: y = knee + s*t/(1+t), t = (x-knee)/s,
    // s = MAX_PIXEL_VALUE - knee. C¹ at the knee, asymptotic to MAX_PIXEL_VALUE.
    private static void softKneeHighlights(float[][] data, double loPercentile, double kneePercentile) {
        var cumulative = Histogram.of(data, 65536).cumulative();
        float lo = (float) cumulative.percentile(loPercentile);
        float knee = (float) cumulative.percentile(kneePercentile);
        float scale = MAX_PIXEL_VALUE - knee;
        for (int y = 0; y < data.length; y++) {
            var line = data[y];
            for (int x = 0; x < line.length; x++) {
                float v = line[x];
                if (v < lo) {
                    line[x] = 0;
                } else if (scale > 0 && v > knee) {
                    float excess = v - knee;
                    line[x] = knee + scale * excess / (scale + excess);
                } else if (v > MAX_PIXEL_VALUE) {
                    line[x] = MAX_PIXEL_VALUE;
                }
            }
        }
    }

}
