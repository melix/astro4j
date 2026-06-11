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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageInterpolation;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.matrix.DoubleMatrix;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Corrects periodic horizontal shifts of scan lines caused by mount oscillations.
 * Unlike {@link JaggingCorrection}, which corrects each line independently from
 * its own border measurements, this correction fits a global model of the limb
 * displacement, which makes it insensitive to per-line detection noise.
 *
 * <p>Two models are evaluated and the one which best explains the measurements
 * is retained: a parametric sinusoid, which is the strongest denoiser when the
 * oscillation is stationary, and a band-pass smoothed displacement curve, which
 * handles oscillations whose period or amplitude drifts during the scan. In both
 * cases the detection is gated by a spectral significance test, so that no
 * correction is applied when the limb displacement is just noise.</p>
 */
public class OscillationCorrection {
    private static final Logger LOGGER = LoggerFactory.getLogger(OscillationCorrection.class);

    private static final int MIN_SAMPLES = 64;
    private static final double MIN_PERIOD = 8;
    private static final int FREQUENCY_OVERSAMPLING = 8;
    private static final double MIN_AMPLITUDE = 0.25;
    private static final double MIN_VARIANCE_REDUCTION = 0.2;
    private static final double OUTLIER_SIGMA = 4.0;
    private static final double MIN_HARMONIC_RATIO = 0.1;
    private static final int PERIODOGRAM_WINDOW = 512;
    private static final int MIN_PERIODOGRAM_WINDOW = 64;
    private static final double PEAK_SIGNIFICANCE = 0.025;
    private static final double PEAK_MEDIAN_RATIO = 4.0;
    private static final double DEFAULT_SMOOTHING_SIGMA = 3.0;
    private static final double MIN_SMOOTHING_SIGMA = 2.0;
    private static final double SMOOTHING_PERIOD_FRACTION = 12.0;
    private static final double MIN_SMOOTHED_RMS = 0.18;
    private static final double NULL_RMS_RATIO = 2.0;

    private OscillationCorrection() {
    }

    /**
     * Detects mount oscillations from the border detections. The borders are detected
     * only once, on the line center reconstruction, so the model is computed once and
     * shared by all pixel shifts.
     *
     * @param borders the detected borders
     * @param ellipse the fitted ellipse
     * @param width the width of the reconstructed images
     * @param height the height of the reconstructed images
     * @param fps the frame rate of the capture, if known
     * @param debugImageEmitter when non-null, a debug chart of the detection is emitted
     * @return the fitted model, if a significant oscillation was detected
     */
    public static Optional<OscillationModel> detectOscillation(PhenomenaDetector.BorderDetection borders, Ellipse ellipse, int width, int height, Double fps, ImageEmitter debugImageEmitter) {
        var values = new double[height];
        var weights = new double[height];
        collectShiftMeasurements(borders, ellipse, width, height, values, weights);
        var evaluation = evaluate(values, weights).orElse(null);
        if (debugImageEmitter != null) {
            emitDebugChart(debugImageEmitter, values, weights, evaluation, fps);
        }
        if (evaluation != null && evaluation.significant()) {
            var model = evaluation.model();
            LOGGER.info(message("oscillation.detected"), String.format("%.2f", model.amplitude()), String.format("%.1f", model.period()), periodInSeconds(model, fps));
            return Optional.of(model);
        }
        if (evaluation != null) {
            var model = evaluation.model();
            LOGGER.info(message("oscillation.rejected"), String.format("%.2f", model.amplitude()), String.format("%.1f", model.period()), String.format("%.0f", 100 * evaluation.varianceReduction()));
        } else {
            LOGGER.info(message("oscillation.not.detected"));
        }
        return Optional.empty();
    }

    private static String periodInSeconds(OscillationModel model, Double fps) {
        return fps != null ? String.format(" (%.2fs)", model.period() / fps) : "";
    }

    /**
     * Measures, for each line where both limbs are detected, the common-mode
     * horizontal displacement of the limb with respect to the fitted ellipse.
     * Samples are weighted by the chord length, since the border positions are
     * less reliable near the poles where the limb is almost tangent to the lines.
     */
    private static void collectShiftMeasurements(PhenomenaDetector.BorderDetection borders, Ellipse ellipse, int width, int height, double[] values, double[] weights) {
        var limit = 2 * (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 100;
        var boundingBox = ellipse.boundingBox();
        var y1 = (int) Math.ceil(Math.max(0, boundingBox.c()));
        var y2 = (int) Math.min(boundingBox.d(), height);
        for (var line = y1; line < y2; line++) {
            var prediction = ellipse.findX(line);
            if (prediction.isEmpty()) {
                continue;
            }
            var pair = prediction.get();
            var left = Math.min(pair.a(), pair.b());
            var right = Math.max(pair.a(), pair.b());
            if (left < 0 || right >= width) {
                // truncated chord, unreliable for common-mode estimation
                continue;
            }
            var detectedX1 = borders.left()[line];
            var detectedX2 = borders.right()[line];
            if (detectedX1 < 0 || detectedX2 < 0) {
                continue;
            }
            var dx1 = detectedX1 - left;
            var dx2 = detectedX2 - right;
            if (Math.abs(dx1) > limit || Math.abs(dx2) > limit) {
                continue;
            }
            values[line] = (dx1 + dx2) / 2;
            weights[line] = right - left;
        }
        var maxWeight = 0d;
        for (var w : weights) {
            maxWeight = Math.max(maxWeight, w);
        }
        if (maxWeight > 0) {
            for (var i = 0; i < weights.length; i++) {
                weights[i] /= maxWeight;
            }
        }
    }

    /**
     * Searches for a significant oscillation in a displacement signal. Entries with
     * a zero weight are ignored, which natively supports gaps in the samples.
     *
     * @param values the per-line displacement measurements, in pixels
     * @param weights the per-line weights, 0 for lines without a measurement
     * @return the fitted model, if a significant oscillation was detected
     */
    public static Optional<OscillationModel> findOscillation(double[] values, double[] weights) {
        return evaluate(values, weights).filter(Evaluation::significant).map(Evaluation::model);
    }

    private static Optional<Evaluation> evaluate(double[] values, double[] weights) {
        var first = -1;
        var last = -1;
        var count = 0;
        for (var i = 0; i < weights.length; i++) {
            if (weights[i] > 0) {
                if (first < 0) {
                    first = i;
                }
                last = i;
                count++;
            }
        }
        if (count < MIN_SAMPLES) {
            return Optional.empty();
        }
        var span = last - first + 1;
        var minFrequency = 2.0 / span;
        var maxFrequency = 1.0 / MIN_PERIOD;
        var frequencyStep = 1.0 / ((double) FREQUENCY_OVERSAMPLING * span);
        if (minFrequency >= maxFrequency) {
            return Optional.empty();
        }
        var clippedWeights = robustClip(values, weights);
        var peaks = findSignificantPeriods(values, clippedWeights, first, last);
        var baseline = fitAt(values, clippedWeights, 0, 0);
        if (baseline == null || baseline.rss() <= 0) {
            return Optional.empty();
        }
        var sineModel = fitSineModel(values, clippedWeights, minFrequency, maxFrequency, frequencyStep);
        var smoothedModel = fitSmoothedModel(values, clippedWeights, first, last, peaks);
        if (sineModel == null && smoothedModel == null) {
            return Optional.empty();
        }
        var sineRss = sineModel != null ? sineModel.rss() : Double.MAX_VALUE;
        var smoothedRss = smoothedModel != null ? smoothedModel.rss() : Double.MAX_VALUE;
        var hasPeaks = peaks != null && !peaks.isEmpty();
        boolean significant;
        OscillationModel model;
        double varianceReduction;
        if (sineRss <= smoothedRss) {
            model = sineModel.model();
            varianceReduction = 1 - sineRss / baseline.rss();
            significant = (peaks == null || hasPeaks) && model.amplitude() >= MIN_AMPLITUDE && varianceReduction >= MIN_VARIANCE_REDUCTION;
        } else {
            model = smoothedModel.model();
            varianceReduction = 1 - smoothedRss / baseline.rss();
            significant = hasPeaks && smoothedModel.rms() >= Math.max(MIN_SMOOTHED_RMS, NULL_RMS_RATIO * smoothedModel.nullRms());
        }
        return Optional.of(new Evaluation(model, varianceReduction, significant));
    }

    /**
     * Fits a sinusoid with a second harmonic, using a weighted least-squares
     * periodogram over the whole signal. This is the best model when the
     * oscillation is stationary, since all cycles contribute coherently.
     */
    private static ModelFit fitSineModel(double[] values, double[] weights, double minFrequency, double maxFrequency, double frequencyStep) {
        var fit = scanFrequencies(values, weights, minFrequency, maxFrequency, frequencyStep);
        if (fit == null) {
            return null;
        }
        var finalFit = fitAt(values, weights, fit.frequency(), 2);
        if (finalFit == null) {
            return null;
        }
        var coefficients = finalFit.coefficients();
        var a1 = coefficients[2];
        var b1 = coefficients[3];
        var a2 = coefficients[4];
        var b2 = coefficients[5];
        if (Math.hypot(a2, b2) < MIN_HARMONIC_RATIO * Math.hypot(a1, b1)) {
            a2 = 0;
            b2 = 0;
        }
        return new ModelFit(new SineModel(finalFit.frequency(), a1, b1, a2, b2), finalFit.rss(), 0, 0);
    }

    /**
     * Builds a band-pass smoothed model of the displacement: a narrow Gaussian
     * smoothing tracks the oscillation while rejecting per-line noise, and a wide
     * smoothing estimates the slowly varying baseline, which belongs to the disk
     * geometry and is excluded from the correction. This model handles oscillations
     * whose period or amplitude drifts during the scan, where a global sinusoid
     * loses phase coherence.
     */
    private static ModelFit fitSmoothedModel(double[] values, double[] weights, int first, int last, List<Peak> peaks) {
        var span = last - first + 1;
        var smoothingSigma = DEFAULT_SMOOTHING_SIGMA;
        var dominantPeriod = span / 4.0;
        if (peaks != null && !peaks.isEmpty()) {
            var shortest = peaks.stream().mapToDouble(Peak::period).min().orElseThrow();
            var longest = peaks.stream().mapToDouble(Peak::period).max().orElseThrow();
            smoothingSigma = Math.max(MIN_SMOOTHING_SIGMA, shortest / SMOOTHING_PERIOD_FRACTION);
            dominantPeriod = peaks.stream().max(Comparator.comparingDouble(Peak::power)).orElseThrow().period();
            var baselineSigma = Math.min(Math.max(2 * longest, 8 * smoothingSigma), span / 4.0);
            return buildSmoothedModel(values, weights, first, last, smoothingSigma, baselineSigma, dominantPeriod);
        }
        var baselineSigma = Math.max(8 * smoothingSigma, span / 6.0);
        return buildSmoothedModel(values, weights, first, last, smoothingSigma, baselineSigma, dominantPeriod);
    }

    private static ModelFit buildSmoothedModel(double[] values, double[] weights, int first, int last, double smoothingSigma, double baselineSigma, double dominantPeriod) {
        var smoothed = gaussianSmooth(values, weights, smoothingSigma);
        var baseline = gaussianSmooth(values, weights, baselineSigma);
        var n = values.length;
        var shifts = new double[n];
        var taper = Math.max(8, (int) Math.round(dominantPeriod));
        for (var i = first; i <= last; i++) {
            var shift = smoothed[i] - baseline[i];
            var distanceToEdge = Math.min(i - first, last - i);
            if (distanceToEdge < taper) {
                shift *= 0.5 * (1 - Math.cos(Math.PI * distanceToEdge / taper));
            }
            shifts[i] = shift;
        }
        double rss = 0;
        double squares = 0;
        double weightSum = 0;
        for (var i = first; i <= last; i++) {
            var weight = weights[i];
            if (weight <= 0) {
                continue;
            }
            var residual = values[i] - baseline[i] - shifts[i];
            rss += weight * residual * residual;
            squares += weight * shifts[i] * shifts[i];
            weightSum += weight;
        }
        if (weightSum == 0) {
            return null;
        }
        var rms = Math.sqrt(squares / weightSum);
        var noiseSigma = estimateNoiseSigma(values, weights);
        var nullRms = noiseSigma / Math.sqrt(2 * Math.sqrt(Math.PI) * smoothingSigma);
        return new ModelFit(new SmoothedModel(shifts, dominantPeriod, Math.sqrt(2) * rms), rss, rms, nullRms);
    }

    /**
     * Estimates the per-sample noise from the differences between adjacent samples,
     * which cancels any structure varying slower than two lines.
     */
    private static double estimateNoiseSigma(double[] values, double[] weights) {
        double sum = 0;
        double squares = 0;
        var count = 0;
        for (var i = 1; i < values.length; i++) {
            if (weights[i] > 0 && weights[i - 1] > 0) {
                var difference = values[i] - values[i - 1];
                sum += difference;
                squares += difference * difference;
                count++;
            }
        }
        if (count < 2) {
            return 0;
        }
        var mean = sum / count;
        return Math.sqrt(Math.max(0, (squares / count - mean * mean) / 2));
    }

    /**
     * Weighted Gaussian kernel smoothing which ignores zero-weight samples; the
     * kernel is renormalized over the available samples, which natively handles gaps.
     */
    private static double[] gaussianSmooth(double[] values, double[] weights, double sigma) {
        var n = values.length;
        var half = (int) Math.min(Math.ceil(4 * sigma), n);
        var kernel = new double[2 * half + 1];
        for (var i = 0; i < kernel.length; i++) {
            var x = (i - half) / sigma;
            kernel[i] = Math.exp(-0.5 * x * x);
        }
        var result = new double[n];
        for (var i = 0; i < n; i++) {
            double numerator = 0;
            double denominator = 0;
            var from = Math.max(0, i - half);
            var to = Math.min(n - 1, i + half);
            for (var j = from; j <= to; j++) {
                var w = weights[j] * kernel[j - i + half];
                numerator += w * values[j];
                denominator += w;
            }
            if (denominator > 1e-6) {
                result[i] = numerator / denominator;
            }
        }
        return result;
    }

    /**
     * Removes outliers (e.g. prominences distorting the border detection) by
     * iteratively clipping samples which deviate from a smoothed estimate by more
     * than a multiple of the robust (median-based) residual dispersion.
     */
    private static double[] robustClip(double[] values, double[] weights) {
        var clipped = weights.clone();
        for (var iteration = 0; iteration < 2; iteration++) {
            var smoothed = gaussianSmooth(values, clipped, DEFAULT_SMOOTHING_SIGMA);
            var residuals = new ArrayList<Double>();
            for (var i = 0; i < values.length; i++) {
                if (clipped[i] > 0) {
                    residuals.add(values[i] - smoothed[i]);
                }
            }
            var median = median(residuals.stream().mapToDouble(Double::doubleValue).toArray());
            var deviations = residuals.stream().mapToDouble(r -> Math.abs(r - median)).toArray();
            var sigma = 1.4826 * median(deviations);
            if (sigma == 0) {
                break;
            }
            for (var i = 0; i < values.length; i++) {
                if (clipped[i] > 0 && Math.abs(values[i] - smoothed[i] - median) > OUTLIER_SIGMA * sigma) {
                    clipped[i] = 0;
                }
            }
        }
        return clipped;
    }

    private static double median(double[] data) {
        if (data.length == 0) {
            return 0;
        }
        var sorted = data.clone();
        Arrays.sort(sorted);
        var mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
    }

    /**
     * Finds the significant periods of the signal using a periodogram averaged over
     * half-overlapping windows. Averaging makes the detection robust to slow drifts
     * of the oscillation frequency, which spread the power of a single global
     * periodogram. Returns null when the sampled span is too short for the analysis.
     */
    private static List<Peak> findSignificantPeriods(double[] values, double[] weights, int first, int last) {
        var span = last - first + 1;
        var window = Math.min(PERIODOGRAM_WINDOW, span / 2);
        if (window < MIN_PERIODOGRAM_WINDOW) {
            return null;
        }
        var frequencies = new ArrayList<Double>();
        for (var f = 2.0 / window; f < 1.0 / MIN_PERIOD; f += 1.0 / (2 * window)) {
            frequencies.add(f);
        }
        var power = new double[frequencies.size()];
        var windows = 0;
        for (var start = first; start + window <= last + 1; start += window / 2) {
            var subValues = Arrays.copyOfRange(values, start, start + window);
            var subWeights = Arrays.copyOfRange(weights, start, start + window);
            var samples = 0;
            for (var w : subWeights) {
                if (w > 0) {
                    samples++;
                }
            }
            if (samples < window / 4) {
                continue;
            }
            var detrended = fitAt(subValues, subWeights, 0, 0);
            if (detrended == null || detrended.rss() <= 0) {
                continue;
            }
            for (var i = 0; i < power.length; i++) {
                var fit = fitAt(subValues, subWeights, frequencies.get(i), 1);
                if (fit != null) {
                    power[i] += 1 - fit.rss() / detrended.rss();
                }
            }
            windows++;
        }
        if (windows == 0) {
            return null;
        }
        for (var i = 0; i < power.length; i++) {
            power[i] /= windows;
        }
        var threshold = Math.max(PEAK_SIGNIFICANCE, PEAK_MEDIAN_RATIO * median(power));
        var peaks = new ArrayList<Peak>();
        for (var i = 1; i < power.length - 1; i++) {
            if (power[i] >= power[i - 1] && power[i] >= power[i + 1] && power[i] >= threshold) {
                peaks.add(new Peak(1 / frequencies.get(i), power[i]));
            }
        }
        return peaks;
    }

    private static Fit scanFrequencies(double[] values, double[] weights, double minFrequency, double maxFrequency, double frequencyStep) {
        var steps = (int) Math.floor((maxFrequency - minFrequency) / frequencyStep) + 1;
        var rss = new double[steps];
        var bestIndex = -1;
        var bestRss = Double.MAX_VALUE;
        for (var i = 0; i < steps; i++) {
            var fit = fitAt(values, weights, minFrequency + i * frequencyStep, 1);
            rss[i] = fit == null ? Double.MAX_VALUE : fit.rss();
            if (rss[i] < bestRss) {
                bestRss = rss[i];
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        var frequency = minFrequency + bestIndex * frequencyStep;
        if (bestIndex > 0 && bestIndex < steps - 1) {
            var previous = rss[bestIndex - 1];
            var next = rss[bestIndex + 1];
            var denominator = previous - 2 * bestRss + next;
            if (denominator > 0) {
                var delta = 0.5 * (previous - next) / denominator;
                if (Math.abs(delta) <= 1) {
                    frequency += delta * frequencyStep;
                }
            }
        }
        var refined = fitAt(values, weights, frequency, 1);
        return refined != null && refined.rss() <= bestRss ? refined : fitAt(values, weights, minFrequency + bestIndex * frequencyStep, 1);
    }

    /**
     * Weighted least-squares fit of an offset, a linear trend and the requested
     * number of harmonics of the given frequency. The basis layout of the
     * returned coefficients is [offset, trend, sin(f), cos(f), sin(2f), cos(2f), ...].
     */
    private static Fit fitAt(double[] values, double[] weights, double frequency, int harmonics) {
        var n = values.length;
        var k = 2 + 2 * harmonics;
        var normal = new double[k][k];
        var rhs = new double[k][1];
        var basis = new double[k];
        for (var y = 0; y < n; y++) {
            var weight = weights[y];
            if (weight <= 0) {
                continue;
            }
            computeBasis(basis, y, n, frequency, harmonics);
            for (var i = 0; i < k; i++) {
                var wb = weight * basis[i];
                for (var j = i; j < k; j++) {
                    normal[i][j] += wb * basis[j];
                }
                rhs[i][0] += wb * values[y];
            }
        }
        for (var i = 0; i < k; i++) {
            for (var j = 0; j < i; j++) {
                normal[i][j] = normal[j][i];
            }
        }
        var solution = DoubleMatrix.of(normal).inverse().mul(DoubleMatrix.of(rhs)).asArray();
        var coefficients = new double[k];
        for (var i = 0; i < k; i++) {
            coefficients[i] = solution[i][0];
            if (!Double.isFinite(coefficients[i])) {
                return null;
            }
        }
        double rss = 0;
        for (var y = 0; y < n; y++) {
            var weight = weights[y];
            if (weight <= 0) {
                continue;
            }
            computeBasis(basis, y, n, frequency, harmonics);
            var predicted = 0d;
            for (var i = 0; i < k; i++) {
                predicted += coefficients[i] * basis[i];
            }
            var residual = values[y] - predicted;
            rss += weight * residual * residual;
        }
        return new Fit(frequency, coefficients, rss);
    }

    private static void computeBasis(double[] basis, int y, int n, double frequency, int harmonics) {
        basis[0] = 1;
        basis[1] = (y - n / 2.0) / n;
        for (var h = 1; h <= harmonics; h++) {
            var phase = 2 * Math.PI * frequency * h * y;
            basis[2 * h] = Math.sin(phase);
            basis[2 * h + 1] = Math.cos(phase);
        }
    }

    private static void emitDebugChart(ImageEmitter imageEmitter, double[] values, double[] weights, Evaluation evaluation, Double fps) {
        var model = evaluation != null ? evaluation.model() : null;
        var significant = evaluation != null && evaluation.significant();
        String chartTitle;
        if (significant) {
            var kind = model instanceof SmoothedModel ? "quasi-periodic oscillation" : "oscillation";
            chartTitle = String.format("Detected %s: amplitude %.2f px, dominant period %.1f frames%s", kind, model.amplitude(), model.period(), periodInSeconds(model, fps));
        } else if (model != null) {
            chartTitle = String.format("No significant oscillation detected (best candidate: amplitude %.2f px, period %.1f frames%s, explains %.0f%% of the variance)",
                    model.amplitude(), model.period(), periodInSeconds(model, fps), 100 * evaluation.varianceReduction());
        } else {
            chartTitle = "No significant oscillation detected";
        }
        List<ShiftDebugChart.Panel> panels;
        if (model != null) {
            var curve = new double[values.length];
            var residuals = new double[values.length];
            for (var i = 0; i < values.length; i++) {
                curve[i] = model.shiftAt(i);
                residuals[i] = values[i] - curve[i];
            }
            panels = List.of(
                    new ShiftDebugChart.Panel("Measured limb shift and fitted model", values, weights, curve),
                    new ShiftDebugChart.Panel("Residual shift after subtracting the model", residuals, weights, null)
            );
        } else {
            panels = List.of(new ShiftDebugChart.Panel("Measured limb shift", values, weights, null));
        }
        ShiftDebugChart.emit(imageEmitter, chartTitle, "Oscillation detection", "oscillation-detection", panels);
    }

    public static void applyCorrection(ImageWrapper32 image, OscillationModel model) {
        var data = image.data();
        var corrected = new float[image.width()];
        for (var y = 0; y < data.length; y++) {
            var shift = model.shiftAt(y);
            if (Math.abs(shift) < 1e-3) {
                continue;
            }
            var line = data[y];
            for (var x = 0; x < line.length; x++) {
                corrected[x] = Math.clamp(ImageInterpolation.lanczos1D(line, x + shift), 0, Constants.MAX_PIXEL_VALUE);
            }
            System.arraycopy(corrected, 0, line, 0, line.length);
        }
    }

    private record Fit(double frequency, double[] coefficients, double rss) {
    }

    private record ModelFit(OscillationModel model, double rss, double rms, double nullRms) {
    }

    private record Evaluation(OscillationModel model, double varianceReduction, boolean significant) {
    }

    private record Peak(double period, double power) {
    }

    /**
     * A model of the horizontal displacement of scan lines.
     */
    public sealed interface OscillationModel permits SineModel, SmoothedModel {
        double shiftAt(double y);

        double amplitude();

        double period();
    }

    /**
     * A sinusoidal model with an optional second harmonic, used when the
     * oscillation is stationary.
     */
    public record SineModel(double frequency, double a1, double b1, double a2, double b2) implements OscillationModel {
        @Override
        public double shiftAt(double y) {
            var phase = 2 * Math.PI * frequency * y;
            return a1 * Math.sin(phase) + b1 * Math.cos(phase) + a2 * Math.sin(2 * phase) + b2 * Math.cos(2 * phase);
        }

        @Override
        public double amplitude() {
            return Math.hypot(a1, b1);
        }

        @Override
        public double period() {
            return 1 / frequency;
        }
    }

    /**
     * A band-pass smoothed displacement curve, used when the oscillation drifts
     * in period or amplitude during the scan. The amplitude is the RMS-equivalent
     * sinusoid amplitude.
     */
    public record SmoothedModel(double[] shifts, double period, double amplitude) implements OscillationModel {
        @Override
        public double shiftAt(double y) {
            var index = (int) Math.round(y);
            if (index < 0 || index >= shifts.length) {
                return 0;
            }
            return shifts[index];
        }
    }
}
