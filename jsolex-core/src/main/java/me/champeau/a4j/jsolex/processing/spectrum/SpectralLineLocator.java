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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Locates a target wavelength inside a spectrum crop window.
 * <p>
 * Line detection picks the darkest local minimum, which is not necessarily the
 * line the user asked for: a neighbouring line can easily be deeper. Given the
 * profile extracted along an initial polynomial, this class finds the pixel
 * offset at which the target wavelength actually sits, by matching the observed
 * profile against the BASS2000 reference spectrum over the whole window.
 * <p>
 * Matching uses the whole window rather than individual line depths because the
 * pattern of several lines is far more discriminating than any single one.
 */
public class SpectralLineLocator {
    private static final double OFFSET_STEP = 0.25;
    private static final double COARSE_OFFSET_STEP = 1;
    private static final int MIN_PROFILE_SIZE = 16;
    private static final double MIN_SIGMA_PIXELS = 0.6;
    private static final double MAX_SIGMA_PIXELS = 3.0;
    private static final double SIGMA_STEP_PIXELS = 0.3;
    private static final double ATLAS_STEP_ANGSTROMS = 0.01;
    private static final int MIN_NORMALIZATION_WINDOW = 9;

    /**
     * Minimum correlation for a match to be considered usable at all.
     */
    private static final double MIN_SCORE = 0.70;
    /**
     * Minimum lead over the best alternative offset. Without it, a window
     * containing repeated similar lines could resolve to the wrong one.
     */
    private static final double MIN_MARGIN = 0.05;
    /**
     * Alternatives closer than this to the winner are considered the same
     * solution rather than competitors.
     */
    private static final double COMPETITOR_SEPARATION_PIXELS = 4;

    private SpectralLineLocator() {
    }

    /**
     * The outcome of a successful match.
     *
     * @param pixelOffset offset, in pixels, from the profile origin to the target wavelength
     * @param score correlation of the observed profile with the reference at that offset
     * @param margin lead over the best competing offset
     * @param sigmaPixels fitted instrumental broadening
     */
    public record Result(double pixelOffset, double score, double margin, double sigmaPixels) {
    }

    /**
     * Finds where the target wavelength sits in the given profile.
     *
     * @param profile the de-smiled profile, as produced by {@link SpectrumAnalyzer#computeDataPoints}
     * @param target the wavelength the user asked for
     * @param dispersion the spectral dispersion
     * @param maxOffsetPixels how far from the profile origin to search
     * @return the match, or empty when no confident match exists
     */
    public static Optional<Result> locate(List<SpectrumAnalyzer.DataPoint> profile,
                                          Wavelen target,
                                          Dispersion dispersion,
                                          double maxOffsetPixels) {
        var dispersionAngstroms = dispersion.angstromsPerPixel();
        if (profile.size() < MIN_PROFILE_SIZE || dispersionAngstroms <= 0 || maxOffsetPixels <= 0) {
            return Optional.empty();
        }
        var shifts = profile.stream().mapToDouble(SpectrumAnalyzer.DataPoint::pixelShift).toArray();
        var observed = profile.stream().mapToDouble(SpectrumAnalyzer.DataPoint::intensity).toArray();
        // A window of about 2 angstroms is wide enough to span any line and
        // narrow enough to follow the blaze and illumination gradient. It is kept
        // odd so that it stays centered on the sample it normalizes.
        var normalizationWindow = clamp((int) Math.round(2 / dispersionAngstroms), MIN_NORMALIZATION_WINDOW, Math.max(MIN_NORMALIZATION_WINDOW, shifts.length / 2)) | 1;
        var observedNormalized = continuumNormalize(observed, normalizationWindow);

        var minShift = Arrays.stream(shifts).min().orElse(0);
        var maxShift = Arrays.stream(shifts).max().orElse(0);
        var atlas = new BroadenedAtlas(target, dispersionAngstroms, minShift - maxOffsetPixels, maxShift + maxOffsetPixels);
        var sigmaCount = (int) Math.round((MAX_SIGMA_PIXELS - MIN_SIGMA_PIXELS) / SIGMA_STEP_PIXELS) + 1;
        var coarseCount = (int) Math.round(2 * maxOffsetPixels / COARSE_OFFSET_STEP) + 1;

        // The correlation peak is at least as wide as the instrumental profile, so a
        // coarse sweep locates it and only its neighbourhood needs the fine step.
        var bestScore = Double.NEGATIVE_INFINITY;
        var bestOffset = 0d;
        var bestSigma = MIN_SIGMA_PIXELS;
        for (int s = 0; s < sigmaCount; s++) {
            var sigma = MIN_SIGMA_PIXELS + s * SIGMA_STEP_PIXELS;
            var broadened = atlas.broaden(sigma * dispersionAngstroms);
            for (int k = 0; k < coarseCount; k++) {
                var offset = -maxOffsetPixels + k * COARSE_OFFSET_STEP;
                var score = correlationAt(shifts, observedNormalized, broadened, offset, normalizationWindow);
                if (score > bestScore) {
                    bestScore = score;
                    bestOffset = offset;
                    bestSigma = sigma;
                }
            }
        }
        var broadened = atlas.broaden(bestSigma * dispersionAngstroms);
        var fineCount = (int) Math.round(2 * COARSE_OFFSET_STEP / OFFSET_STEP) + 1;
        for (int k = 0; k < fineCount; k++) {
            var offset = bestOffset - COARSE_OFFSET_STEP + k * OFFSET_STEP;
            var score = correlationAt(shifts, observedNormalized, broadened, offset, normalizationWindow);
            if (score > bestScore) {
                bestScore = score;
                bestOffset = offset;
            }
        }
        if (bestScore < MIN_SCORE) {
            return Optional.empty();
        }

        // Best score reachable at an offset which is a genuinely different solution.
        // Without such an alternative there is nothing to compare against, so the
        // match cannot be called unambiguous.
        var runnerUp = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < coarseCount; k++) {
            var offset = -maxOffsetPixels + k * COARSE_OFFSET_STEP;
            if (Math.abs(offset - bestOffset) < COMPETITOR_SEPARATION_PIXELS) {
                continue;
            }
            runnerUp = Math.max(runnerUp, correlationAt(shifts, observedNormalized, broadened, offset, normalizationWindow));
        }
        if (runnerUp == Double.NEGATIVE_INFINITY) {
            return Optional.empty();
        }
        var margin = bestScore - runnerUp;
        if (margin < MIN_MARGIN) {
            return Optional.empty();
        }
        var refined = refineOffset(shifts, observedNormalized, broadened, bestOffset, normalizationWindow);
        return Optional.of(new Result(refined, bestScore, margin, bestSigma));
    }

    private static double correlationAt(double[] shifts,
                                        double[] observedNormalized,
                                        Broadened broadened,
                                        double offset,
                                        int normalizationWindow) {
        var reference = new double[shifts.length];
        for (int i = 0; i < shifts.length; i++) {
            reference[i] = broadened.at(shifts[i] - offset);
        }
        return pearson(observedNormalized, continuumNormalize(reference, normalizationWindow));
    }

    /**
     * Parabolic interpolation of the correlation peak, for sub-step accuracy.
     */
    private static double refineOffset(double[] shifts,
                                       double[] observedNormalized,
                                       Broadened broadened,
                                       double offset,
                                       int normalizationWindow) {
        var scores = new double[3];
        for (int k = -1; k <= 1; k++) {
            scores[k + 1] = correlationAt(shifts, observedNormalized, broadened, offset + k * OFFSET_STEP, normalizationWindow);
        }
        var denominator = scores[0] - 2 * scores[1] + scores[2];
        if (denominator >= -1e-12) {
            return offset;
        }
        var correction = 0.5 * (scores[0] - scores[2]) / denominator;
        return offset + OFFSET_STEP * clamp(correction, -1, 1);
    }

    /**
     * Divides by a moving upper quartile so that the blaze profile, the
     * illumination gradient and the overall intensity scale cancel out, leaving
     * only line structure to be compared.
     */
    private static double[] continuumNormalize(double[] values, int window) {
        var result = new double[values.length];
        var half = window / 2;
        for (int i = 0; i < values.length; i++) {
            var low = Math.max(0, i - half);
            var high = Math.min(values.length, i + half + 1);
            var slice = Arrays.copyOfRange(values, low, high);
            Arrays.sort(slice);
            var continuum = slice[(int) (slice.length * 0.75)];
            result[i] = continuum > 1e-9 ? values[i] / continuum : 1;
        }
        return result;
    }

    private static double pearson(double[] a, double[] b) {
        var meanA = 0d;
        var meanB = 0d;
        for (int i = 0; i < a.length; i++) {
            meanA += a[i];
            meanB += b[i];
        }
        meanA /= a.length;
        meanB /= b.length;
        var covariance = 0d;
        var varianceA = 0d;
        var varianceB = 0d;
        for (int i = 0; i < a.length; i++) {
            var da = a[i] - meanA;
            var db = b[i] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        return covariance / Math.max(1e-12, Math.sqrt(varianceA * varianceB));
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * The reference spectrum resampled on a regular grid around the target, so
     * that broadening is computed once per width instead of once per lookup.
     */
    private static final class BroadenedAtlas {
        private final double firstAngstroms;
        private final double[] raw;
        private final Wavelen target;
        private final double dispersionAngstroms;

        private BroadenedAtlas(Wavelen target, double dispersionAngstroms, double minPixels, double maxPixels) {
            this.target = target;
            this.dispersionAngstroms = dispersionAngstroms;
            // Extend by the widest broadening kernel so convolution stays valid at the edges.
            var pad = 3 * MAX_SIGMA_PIXELS * Math.abs(dispersionAngstroms);
            this.firstAngstroms = target.angstroms() + minPixels * dispersionAngstroms - pad;
            var last = target.angstroms() + maxPixels * dispersionAngstroms + pad;
            var count = (int) Math.ceil((last - firstAngstroms) / ATLAS_STEP_ANGSTROMS) + 1;
            this.raw = new double[count];
            for (int i = 0; i < count; i++) {
                raw[i] = ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(firstAngstroms + i * ATLAS_STEP_ANGSTROMS));
            }
        }

        private Broadened broaden(double sigmaAngstroms) {
            var absSigma = Math.abs(sigmaAngstroms);
            double[] values;
            if (absSigma < ATLAS_STEP_ANGSTROMS) {
                values = raw.clone();
            } else {
                var radius = (int) Math.ceil(3 * absSigma / ATLAS_STEP_ANGSTROMS);
                var kernel = new double[2 * radius + 1];
                var sum = 0d;
                for (int k = -radius; k <= radius; k++) {
                    var d = k * ATLAS_STEP_ANGSTROMS / absSigma;
                    kernel[k + radius] = Math.exp(-0.5 * d * d);
                    sum += kernel[k + radius];
                }
                for (int k = 0; k < kernel.length; k++) {
                    kernel[k] /= sum;
                }
                values = new double[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    var accumulator = 0d;
                    for (int k = -radius; k <= radius; k++) {
                        var index = clamp(i + k, 0, raw.length - 1);
                        accumulator += kernel[k + radius] * raw[index];
                    }
                    values[i] = accumulator;
                }
            }
            return new Broadened(values, firstAngstroms, target.angstroms(), dispersionAngstroms);
        }
    }

    /**
     * A broadened reference spectrum, addressed in pixel-shift space.
     */
    private record Broadened(double[] values, double firstAngstroms, double targetAngstroms, double dispersionAngstroms) {
        private double at(double pixelShift) {
            var exact = (targetAngstroms + pixelShift * dispersionAngstroms - firstAngstroms) / ATLAS_STEP_ANGSTROMS;
            var lower = (int) Math.floor(exact);
            if (lower < 0) {
                return values[0];
            }
            if (lower >= values.length - 1) {
                return values[values.length - 1];
            }
            var fraction = exact - lower;
            return values[lower] + (values[lower + 1] - values[lower]) * fraction;
        }
    }
}
