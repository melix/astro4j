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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Identifies the wavelength of a scan without being restricted to a list of known lines.
 * <p>
 * Line detection always locks onto the darkest line of the captured window, so the
 * wavelength being observed is necessarily one of the deep lines of the solar spectrum.
 * This class extracts those deep lines from the reference spectrum, then tests each of
 * them as a hypothesis: assuming the detected line is that one, the dispersion follows
 * from the instrument, a reference profile can be built, and its correlation with the
 * observed profile scores the hypothesis.
 * <p>
 * Nothing is returned unless one hypothesis clearly wins, so that a window which does
 * not carry enough spectrum to be identified is reported as unknown rather than guessed.
 */
public final class DeepLineIdentifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeepLineIdentifier.class);
    /** How many leading hypotheses are reported, to explain a fallback. */
    private static final int REPORTED_CANDIDATES = 8;
    /** How far a catalog line may be from a candidate to lend it its name. */
    private static final double CATALOG_NAME_TOLERANCE_ANGSTROMS = 0.5;
    private static final double ATLAS_STEP_ANGSTROMS = 0.01;
    /** Range over which the identification was validated. */
    private static final double FIRST_ANGSTROMS = 3900;
    private static final double LAST_ANGSTROMS = 6800;
    /** Padding so the broadening kernel stays valid at the edges of the range. */
    private static final double ATLAS_PADDING_ANGSTROMS = 5;
    /** Resolution at which lines are considered blended when extracting candidates. */
    private static final double CATALOG_SIGMA_ANGSTROMS = 0.15;
    /** Half width of the window used to measure the depth of a candidate. */
    private static final double DEPTH_HALF_WIDTH_ANGSTROMS = 2;
    /**
     * Fraction of the deepest lines kept as candidates. Deep enough to include every
     * line the software knows about, so that a scan centred on one of them is at least
     * considered.
     */
    private static final double DEEPEST_FRACTION = 0.50;
    /** Instrumental broadening hypotheses, in angstroms. */
    private static final double[] SIGMAS_ANGSTROMS = {0.05, 0.08, 0.12, 0.17, 0.24, 0.33};
    private static final int COARSE_SIGMA_INDEX = 2;
    /** Number of leading candidates re-scored over all broadening hypotheses. */
    private static final int REFINED_CANDIDATES = 24;
    /**
     * Width of the moving window used to estimate the continuum before correlating.
     * Widening it raises the score of the right line, but it raises the score of the
     * competing ones just as much, so identification does not become any surer.
     */
    private static final double NORMALIZATION_ANGSTROMS = 2;
    private static final int MIN_NORMALIZATION_WINDOW = 9;
    private static final int MIN_PROFILE_SIZE = 16;
    /**
     * A window narrower than this carries too few lines to tell one part of the spectrum
     * from another: candidates then reach very high correlations for the wrong reason,
     * and the lead of the winner stops meaning anything.
     */
    private static final double MIN_PROFILE_ANGSTROMS = 3;

    private static final double MIN_SCORE = 0.70;
    /**
     * Minimum lead over the best hypothesis which is a genuinely different line,
     * as a fraction of the headroom left above that hypothesis. Correlations saturate
     * close to one on a window dominated by a single broad line, which leaves the
     * winner an absolute lead of a few thousandths even when it is unambiguous, so
     * the lead is only meaningful relative to what was still available.
     */
    private static final double MIN_RELATIVE_MARGIN = 0.30;
    private static final double COMPETITOR_SEPARATION_ANGSTROMS = 3;

    private static volatile Reference reference;

    private DeepLineIdentifier() {
    }

    /**
     * The outcome of a successful identification.
     *
     * @param wavelength the wavelength of the detected line
     * @param score correlation of the observed profile with the reference spectrum
     * @param margin lead over the best competing line, as a fraction of the headroom above it
     * @param binning the binning which best explains the observation
     */
    public record Result(Wavelen wavelength, double score, double margin, int binning) {
    }

    /**
     * Identifies the line the profile is centred on.
     *
     * @param profile the de-smiled profile, as produced by {@link SpectrumAnalyzer#computeDataPoints}
     * @param instrument the spectroheliograph
     * @param pixelSize the sensor pixel size, in micrometers
     * @param binnings the binnings to consider
     * @return the identified line, or empty when no hypothesis clearly wins
     */
    public static Optional<Result> identify(List<SpectrumAnalyzer.DataPoint> profile,
                                            SpectroHeliograph instrument,
                                            double pixelSize,
                                            int... binnings) {
        var ranked = rank(profile, instrument, pixelSize, binnings);
        if (ranked.isEmpty()) {
            LOGGER.info(message("free.search.no.candidate"));
            return Optional.empty();
        }
        var winner = ranked.getFirst();
        if (winner.score() < MIN_SCORE) {
            return reject(ranked, String.format(Locale.US, message("free.search.rejected.score"),
                    winner.wavelength().angstroms(), winner.score(), MIN_SCORE));
        }
        var runnerUp = ranked.stream()
                .filter(c -> Math.abs(c.wavelength().angstroms() - winner.wavelength().angstroms()) >= COMPETITOR_SEPARATION_ANGSTROMS)
                .findFirst();
        if (runnerUp.isEmpty()) {
            return reject(ranked, String.format(Locale.US, message("free.search.rejected.alone"),
                    winner.wavelength().angstroms()));
        }
        var margin = (winner.score() - runnerUp.get().score()) / Math.max(1e-9, 1 - runnerUp.get().score());
        if (margin < MIN_RELATIVE_MARGIN) {
            return reject(ranked, String.format(Locale.US, message("free.search.rejected.margin"),
                    winner.wavelength().angstroms(), winner.score(),
                    runnerUp.get().wavelength().angstroms(), runnerUp.get().score(),
                    margin, MIN_RELATIVE_MARGIN));
        }
        return Optional.of(new Result(winner.wavelength(), winner.score(), margin, winner.binning()));
    }

    /**
     * The hypotheses this identification considered, best first and without any
     * confidence gate applied. Exposed so that a failure to identify a line can be
     * explained rather than guessed at.
     *
     * @param profile the de-smiled profile
     * @param instrument the spectroheliograph
     * @param pixelSize the sensor pixel size, in micrometers
     * @param binnings the binnings to consider
     * @return the scored hypotheses, best first, with a margin of zero
     */
    static List<Result> rank(List<SpectrumAnalyzer.DataPoint> profile,
                             SpectroHeliograph instrument,
                             double pixelSize,
                             int... binnings) {
        if (profile.size() < MIN_PROFILE_SIZE || pixelSize <= 0 || instrument == null || binnings.length == 0) {
            return List.of();
        }
        var shifts = profile.stream().mapToDouble(SpectrumAnalyzer.DataPoint::pixelShift).toArray();
        var observed = profile.stream().mapToDouble(SpectrumAnalyzer.DataPoint::intensity).toArray();
        // The observed profile only has to be prepared once per binning, since the
        // binning is what decides how much spectrum a pixel covers.
        var prepared = new Prepared[binnings.length];
        for (int b = 0; b < binnings.length; b++) {
            var dispersion = midDispersion(instrument, pixelSize, binnings[b]);
            var window = normalizationWindow(dispersion, shifts.length);
            prepared[b] = new Prepared(binnings[b], window, continuumNormalize(observed, window),
                    shifts.length * dispersion);
        }
        // The binning is a hypothesis, so the width is only known within a factor of two.
        // The narrowest reading is the one to trust: answering wrongly is worse than not
        // answering, and a window this narrow yields high correlations for wrong reasons.
        if (Arrays.stream(prepared).mapToDouble(Prepared::widthAngstroms).min().orElse(0) < MIN_PROFILE_ANGSTROMS) {
            return List.of();
        }

        // First pass over every candidate at a single broadening, then a second
        // pass which re-scores only the leading ones over all of them.
        var reference = reference();
        var scored = new ArrayList<Scored>();
        for (int b = 0; b < prepared.length; b++) {
            for (var wavelength : reference.candidates()) {
                scored.add(new Scored(wavelength,
                        correlate(reference.broadened()[COARSE_SIGMA_INDEX], prepared[b], shifts, wavelength, instrument, pixelSize),
                        b));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        var refined = new ArrayList<Scored>();
        for (var candidate : scored.subList(0, Math.min(REFINED_CANDIDATES, scored.size()))) {
            var best = candidate.score();
            for (var atlas : reference.broadened()) {
                best = Math.max(best, correlate(atlas, prepared[candidate.binningIndex()], shifts,
                        candidate.wavelength(), instrument, pixelSize));
            }
            refined.add(new Scored(candidate.wavelength(), best, candidate.binningIndex()));
        }
        refined.sort(Comparator.comparingDouble(Scored::score).reversed());
        return refined.stream()
                .map(c -> new Result(Wavelen.ofAngstroms(c.wavelength()), c.score(), 0, prepared[c.binningIndex()].binning()))
                .toList();
    }

    private static double midDispersion(SpectroHeliograph instrument, double pixelSize, int binning) {
        return SpectrumAnalyzer.computeSpectralDispersion(instrument,
                Wavelen.ofAngstroms((FIRST_ANGSTROMS + LAST_ANGSTROMS) / 2), pixelSize * binning).angstromsPerPixel();
    }

    private static double correlate(double[] atlas,
                                    Prepared prepared,
                                    double[] shifts,
                                    double wavelength,
                                    SpectroHeliograph instrument,
                                    double pixelSize) {
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument,
                Wavelen.ofAngstroms(wavelength), pixelSize * prepared.binning()).angstromsPerPixel();
        var reference = new double[shifts.length];
        for (int i = 0; i < shifts.length; i++) {
            reference[i] = sample(atlas, wavelength + shifts[i] * dispersion);
        }
        return pearson(prepared.observed(), continuumNormalize(reference, prepared.window()));
    }

    /**
     * The wavelengths which can be identified, in increasing order. A line which is not
     * in there can never be found, however good the scan is.
     *
     * @return the candidate wavelengths, in angstroms
     */
    static double[] candidateSet() {
        return reference().candidates().clone();
    }

    /**
     * The reference data, built once: reading and broadening the whole atlas is far too
     * expensive to repeat, and it never changes.
     */
    private static Reference reference() {
        var local = reference;
        if (local == null) {
            synchronized (DeepLineIdentifier.class) {
                local = reference;
                if (local == null) {
                    var raw = rawAtlas();
                    var broadened = new double[SIGMAS_ANGSTROMS.length][];
                    for (int i = 0; i < SIGMAS_ANGSTROMS.length; i++) {
                        broadened[i] = broaden(raw, SIGMAS_ANGSTROMS[i]);
                    }
                    local = new Reference(extractDeepLines(broaden(raw, CATALOG_SIGMA_ANGSTROMS)), broadened);
                    reference = local;
                }
            }
        }
        return local;
    }

    /**
     * The deepest local minima of the reference spectrum, which are the only lines the
     * detector can plausibly have locked onto.
     */
    private static double[] extractDeepLines(double[] atlas) {
        var halfWidth = (int) (DEPTH_HALF_WIDTH_ANGSTROMS / ATLAS_STEP_ANGSTROMS);
        var minima = new ArrayList<double[]>();
        for (int i = 1; i < atlas.length - 1; i++) {
            if (atlas[i] >= atlas[i - 1] || atlas[i] > atlas[i + 1]) {
                continue;
            }
            var low = Math.max(0, i - halfWidth);
            var high = Math.min(atlas.length, i + halfWidth + 1);
            var slice = Arrays.copyOfRange(atlas, low, high);
            Arrays.sort(slice);
            var continuum = slice[(int) (slice.length * 0.9)];
            if (continuum > 1e-9) {
                minima.add(new double[]{first() + i * ATLAS_STEP_ANGSTROMS, 1 - atlas[i] / continuum});
            }
        }
        minima.sort(Comparator.comparingDouble((double[] m) -> m[1]).reversed());
        var kept = Math.max(1, (int) (minima.size() * DEEPEST_FRACTION));
        var result = new double[kept];
        for (int i = 0; i < kept; i++) {
            result[i] = minima.get(i)[0];
        }
        Arrays.sort(result);
        return result;
    }

    private static double first() {
        return FIRST_ANGSTROMS - ATLAS_PADDING_ANGSTROMS;
    }

    private static double[] rawAtlas() {
        var count = (int) Math.ceil((LAST_ANGSTROMS + ATLAS_PADDING_ANGSTROMS - first()) / ATLAS_STEP_ANGSTROMS) + 1;
        var raw = new double[count];
        for (int i = 0; i < count; i++) {
            raw[i] = ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(first() + i * ATLAS_STEP_ANGSTROMS));
        }
        return raw;
    }

    private static double[] broaden(double[] raw, double sigmaAngstroms) {
        var radius = (int) Math.ceil(3 * sigmaAngstroms / ATLAS_STEP_ANGSTROMS);
        var kernel = new double[2 * radius + 1];
        var sum = 0d;
        for (int k = -radius; k <= radius; k++) {
            var d = k * ATLAS_STEP_ANGSTROMS / sigmaAngstroms;
            kernel[k + radius] = Math.exp(-0.5 * d * d);
            sum += kernel[k + radius];
        }
        for (int k = 0; k < kernel.length; k++) {
            kernel[k] /= sum;
        }
        var out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            var accumulator = 0d;
            for (int k = -radius; k <= radius; k++) {
                accumulator += kernel[k + radius] * raw[Math.max(0, Math.min(raw.length - 1, i + k))];
            }
            out[i] = accumulator;
        }
        return out;
    }

    private static double sample(double[] atlas, double wavelength) {
        var exact = (wavelength - first()) / ATLAS_STEP_ANGSTROMS;
        var lower = (int) Math.floor(exact);
        if (lower < 0) {
            return atlas[0];
        }
        if (lower >= atlas.length - 1) {
            return atlas[atlas.length - 1];
        }
        return atlas[lower] + (atlas[lower + 1] - atlas[lower]) * (exact - lower);
    }

    private static int normalizationWindow(double angstromsPerPixel, int length) {
        var upper = Math.max(MIN_NORMALIZATION_WINDOW, length / 2);
        var ideal = (int) Math.round(NORMALIZATION_ANGSTROMS / angstromsPerPixel);
        return Math.max(MIN_NORMALIZATION_WINDOW, Math.min(ideal, upper)) | 1;
    }

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

    /**
     * Declines to identify the line, reporting why and what was considered. The
     * hypotheses are only worth listing when they were not used, which is when the
     * question of what the search was hesitating between actually arises.
     */
    private static Optional<Result> reject(List<Result> ranked, String reason) {
        LOGGER.info(String.format(Locale.US, message("free.search.candidates"), describe(ranked)));
        LOGGER.info(reason);
        return Optional.empty();
    }

    /**
     * The leading hypotheses, each named after the line catalog when it knows the
     * wavelength.
     */
    private static String describe(List<Result> ranked) {
        var kept = new ArrayList<String>();
        for (var candidate : ranked) {
            if (kept.size() >= REPORTED_CANDIDATES) {
                break;
            }
            kept.add(String.format(Locale.US, "%s (%.3f)", nameOf(candidate.wavelength()), candidate.score()));
        }
        return String.join(", ", kept);
    }

    private static String nameOf(Wavelen wavelength) {
        var angstroms = String.format(Locale.US, "%.2f", wavelength.angstroms());
        return SpectralLineCatalog.findClosest(wavelength, CATALOG_NAME_TOLERANCE_ANGSTROMS)
                .map(line -> line.shortName() + " " + angstroms)
                .orElse(angstroms);
    }

    /**
     * The reference spectrum, ready to be correlated against.
     *
     * @param candidates the wavelengths which can be identified, in increasing order
     * @param broadened the atlas at each instrumental broadening hypothesis
     */
    private record Reference(double[] candidates, double[][] broadened) {
    }

    /**
     * The observed profile prepared for one binning hypothesis.
     *
     * @param binning the binning
     * @param window the continuum window it implies, in samples
     * @param observed the continuum normalized profile
     * @param widthAngstroms how much spectrum the profile covers under that binning
     */
    private record Prepared(int binning, int window, double[] observed, double widthAngstroms) {
    }

    private record Scored(double wavelength, double score, int binningIndex) {
    }
}
