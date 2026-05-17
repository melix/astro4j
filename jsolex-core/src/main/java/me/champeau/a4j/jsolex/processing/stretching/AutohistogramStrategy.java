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
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    private static final ClaheStrategy CLAHE_STRATEGY = new ClaheStrategy(16, 64, 1.1);
    private static final StretchingStrategy PROTUS_STRATEGY = new ClaheStrategy(8, 64, 0.8);
    private static final float DISK_MIDTONE_TARGET = 0.45f * MAX_PIXEL_VALUE;
    private static final float MIN_PEDESTAL = 0.005f * MAX_PIXEL_VALUE;
    private static final float MIDTONE_CONTRAST_SLOPE = 1.25f;
    // Contrast S-curve fades from full strength to identity between these normalized-radius
    // depths so it doesn't affect prominences and the limb fade.
    private static final double CONTRAST_FADE_INNER = 0.85;
    private static final double CONTRAST_FADE_OUTER = 1.05;

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
                for (var y = 0; y < height; y++) {
                    for (var x = 0; x < width; x++) {
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
            for (var y = 0; y < height; y++) {
                System.arraycopy(blended[y], 0, output[y], 0, width);
            }
        }
        maybeAdjustBrightness(image, height, width, output);
        CutoffStretchingStrategy.DEFAULT.stretch(image);
    }

    private static void blendInto(float[][] img1, float[][] img2, int width, int height, double alpha, float[][] out) {
        var beta = 1 - alpha;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
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
        for (var y = minY; y < maxY; y++) {
            for (var x = minX; x < maxX; x++) {
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
        var maxIterations = 25;
        var foundPedestral = false;
        while (--maxIterations >= 0) {
            for (var y = 0; y < height; y++) {
                System.arraycopy(currentData[y], 0, scratchData[y], 0, width);
            }
            neutralizeBg(scratch, 2, 1.5, smoothing, null, bgModelBuffer);
            var bg = neutralizeBg(eclipse, 2, 1.5, smoothing, null, bgModelBuffer);
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
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    denoised.data()[y][x] += pedestral / 2;
                }
            }
        }
        return denoised;
    }

    private void maybeAdjustBrightness(ImageWrapper32 image, int height, int width, float[][] diskData) {
        if (!adjustBrightness) {
            return;
        }
        var ellipse = image.findMetadata(Ellipse.class).orElse(null);
        // Pedestal comes from the whole-frame histogram so the shadow floor stays in the bg
        // region (typically near 0 after bg-neutralize). Using inside-ellipse p5 instead would
        // raise pedestal into the disk's noise floor and crush filaments/dark features.
        var cumulative = Histogram.of(diskData, 65536).cumulative();
        var p5 = (float) cumulative.percentile(0.05);
        var pedestal = Math.max(p5, MIN_PEDESTAL);
        // Midtone, on the other hand, must come from inside the ellipse: for partial-disk crops
        // the off-disk area would otherwise dominate the histogram and bias the median.
        var midtone = diskMedianInside(diskData, ellipse, width, height, pedestal);
        if (midtone <= pedestal || midtone >= DISK_MIDTONE_TARGET) {
            return;
        }
        // Apply an asinh stretch anchored at (pedestal, pedestal) and (MAX, MAX):
        //   y = pedestal + (MAX - pedestal) * asinh(beta * t) / asinh(beta),  t = (v - pedestal)/(MAX - pedestal)
        // beta is solved by bisection so y(midtone) = DISK_MIDTONE_TARGET. asinh has a much
        // gentler upper roll-off than a sigmoid (~log at the top vs ~exp), so bright disk content
        // keeps its dynamics instead of asymptoting flat against MAX.
        var range = MAX_PIXEL_VALUE - pedestal;
        var tMid = (midtone - pedestal) / range;
        var tTarget = (DISK_MIDTONE_TARGET - pedestal) / range;
        var beta = solveAsinhBeta(tMid, tTarget);
        var asinhBeta = Math.log(beta + Math.sqrt(beta * beta + 1));
        // Apply the curve to all pixels above the pedestal — including those just outside the
        // detected ellipse, so prominences, the chromospheric limb fade, and any off-disk signal
        // get the same lift as disk pixels and the limb transitions smoothly.
        //
        // After the asinh lift, apply a cubic contrast S-curve y = a·v³ + b·v² + c·v through
        // (0,0), (midtone, midtone), (MAX, MAX) with slope MIDTONE_CONTRAST_SLOPE at midtone.
        // This pushes darks darker and brights brighter symmetrically around midtone, leaving
        // the midtone position unchanged. The cubic stays monotonic provided the slope value
        // isn't too aggressive (≈ ≤1.3 at this midtone position).
        var m = (double) DISK_MIDTONE_TARGET;
        var maxD = (double) MAX_PIXEL_VALUE;
        var ca = (MIDTONE_CONTRAST_SLOPE - 1.0) / (m * (m - maxD));
        var cb = -ca * (maxD + m);
        var cc = 1.0 + ca * m * maxD;
        // The contrast S-curve is faded toward identity outside the disk so it doesn't crush
        // prominences and the chromospheric limb fade. We use the ellipse's implicit equation:
        //   f(x,y) = A x² + B xy + C y² + D x + E y + F
        // is < 0 inside, 0 on the boundary, > 0 outside. Normalizing by f at the ellipse center
        // gives a "normalized radius squared" that is 0 at the center and 1 at the limb. The
        // fade is a smoothstep between CONTRAST_FADE_INNER (full S-curve) and CONTRAST_FADE_OUTER
        // (identity).
        double ellA = 0, ellB = 0, ellC = 0, ellD = 0, ellE = 0, ellF = 0, fCenter = -1;
        var hasEllipse = ellipse != null;
        if (hasEllipse) {
            var coeffs = ellipse.getCartesianCoefficients();
            ellA = coeffs.a();
            ellB = coeffs.b();
            ellC = coeffs.c();
            ellD = coeffs.d();
            ellE = coeffs.e();
            ellF = coeffs.f();
            var c2 = ellipse.center();
            var cx = c2.a();
            var cy = c2.b();
            fCenter = ellA * cx * cx + ellB * cx * cy + ellC * cy * cy + ellD * cx + ellE * cy + ellF;
            if (fCenter >= 0) {
                hasEllipse = false; // degenerate; skip the fade
            }
        }
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var v = diskData[y][x];
                if (v > pedestal) {
                    var t = (v - pedestal) / range;
                    var arg = beta * t;
                    var asinhArg = Math.log(arg + Math.sqrt(arg * arg + 1));
                    var afterAsinh = pedestal + range * asinhArg / asinhBeta;
                    var contrasted = ca * afterAsinh * afterAsinh * afterAsinh + cb * afterAsinh * afterAsinh + cc * afterAsinh;
                    double result;
                    if (hasEllipse) {
                        var fVal = ellA * x * x + ellB * x * y + ellC * y * y + ellD * x + ellE * y + ellF;
                        var depth = 1.0 - fVal / fCenter;  // 0 at center, 1 at limb, >1 outside
                        var s = (depth - CONTRAST_FADE_INNER) / (CONTRAST_FADE_OUTER - CONTRAST_FADE_INNER);
                        var fadeOut = s <= 0 ? 0.0 : s >= 1 ? 1.0 : s * s * (3 - 2 * s);
                        result = afterAsinh + (1 - fadeOut) * (contrasted - afterAsinh);
                    } else {
                        result = contrasted;
                    }
                    diskData[y][x] = (float) Math.clamp(result, 0, MAX_PIXEL_VALUE);
                }
            }
        }
    }

    private static float diskMedianInside(float[][] data, Ellipse e, int width, int height, float pedestal) {
        var hist = new int[65536];
        var count = 0;
        if (e == null) {
            for (var line : data) {
                for (var v : line) {
                    if (v > pedestal) {
                        hist[(int) Math.clamp(v, 0, 65535)]++;
                        count++;
                    }
                }
            }
        } else {
            var bb = e.boundingBox();
            var minX = (int) Math.max(0, bb.a());
            var maxX = (int) Math.min(width - 1, bb.b());
            var minY = (int) Math.max(0, bb.c());
            var maxY = (int) Math.min(height - 1, bb.d());
            for (var y = minY; y <= maxY; y++) {
                for (var x = minX; x <= maxX; x++) {
                    if (e.isWithin(x, y) && data[y][x] > pedestal) {
                        hist[(int) Math.clamp(data[y][x], 0, 65535)]++;
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return 0;
        }
        var half = count / 2;
        var cum = 0;
        for (var i = 0; i < hist.length; i++) {
            cum += hist[i];
            if (cum >= half) {
                return i;
            }
        }
        return 0;
    }

    private static double solveAsinhBeta(double tMid, double tTarget) {
        // Solve asinh(beta * tMid) = tTarget * asinh(beta) by bisection.
        var lo = 0.01;
        var hi = 10000.0;
        for (var i = 0; i < 200; i++) {
            var beta = (lo + hi) / 2;
            var lhs = Math.log(beta * tMid + Math.sqrt(beta * beta * tMid * tMid + 1));
            var rhs = tTarget * Math.log(beta + Math.sqrt(beta * beta + 1));
            if (lhs > rhs) {
                hi = beta;
            } else {
                lo = beta;
            }
            if (hi - lo < 1e-8) {
                break;
            }
        }
        return (lo + hi) / 2;
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
            for (var y = 0; y < disk.height(); y++) {
                for (var x = 0; x < disk.width(); x++) {
                    var v = data[y][x];
                    avg += v;
                    if (e == null || !e.isWithin(x, y)) {
                        var smoothed = smoothing * v;
                        diskData[y][x] = Math.clamp(diskData[y][x] - smoothed, 0, MAX_PIXEL_VALUE);
                    }
                }
            }
            avg = avg / (disk.width() * disk.height());
            return avg;
        }
        return 0;
    }

}
