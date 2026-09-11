/*
 * Copyright 2026-2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Identifies every absorption line visible in a single spectrum frame, such as the
 * live view of a capture software, and tracks the identification across frames.
 * <p>
 * The line the frame is centred on is found by {@link DeepLineIdentifier}; the other
 * lines are the absorption features of the observed profile which coincide with a
 * line of the reference spectrum once the dispersion is known. Successive frames
 * agreeing on the same line raise the confidence, and when a frame is ambiguous the
 * previously identified line is preferred as long as it remains a strong candidate,
 * since the observed window only moves slowly while the user tunes the instrument.
 * <p>
 * A line is always proposed as soon as the frame carries a spectrum, even when no
 * hypothesis wins clearly: the observer is looking at a line and wants to know which
 * one it is, so the {@link Confidence} says how much the answer is worth rather than
 * the answer being withheld.
 * <p>
 * The row of a line at column {@code x} is {@code polynomial(x) + pixelShift}.
 */
public final class SpectralWindowIdentifier {
    private static final double SAME_LINE_TOLERANCE_ANGSTROMS = 0.5;
    private static final double CONTINUITY_SCORE_SLACK = 0.02;
    private static final double MIN_LINE_DEPTH = 0.03;
    private static final int MINIMUM_HALF_WIDTH_PIXELS = 2;
    private static final double LINE_MATCH_TOLERANCE_PIXELS = 1.5;
    private static final double CATALOG_NAME_TOLERANCE_ANGSTROMS = 0.5;
    private static final double MAX_SCALE_DEVIATION = 0.03;
    private static final double SCALE_STEP = 0.0025;
    private static final int MEDIUM_CONFIDENCE_STREAK = 2;
    private static final int HIGH_CONFIDENCE_STREAK = 4;
    private static final double HIGH_CONFIDENCE_SCORE = 0.85;
    /** Below this sag of the lines across the frame, their curvature cannot tell which way the frame runs. */
    private static final double MIN_SAG_PIXELS = 1.0;
    /** Below this transmission of the atmosphere, a line without a catalog name is one of its own. */
    private static final double TELLURIC_LINE_TRANSMISSION = 0.95;
    /** The name given to a line which belongs to the atmosphere rather than to the Sun. */
    public static final String TELLURIC_LINE_NAME = "telluric";
    /** How far the spectrum is looked for when checking whether it moved since the last frame. */
    private static final int MAX_TRACKED_SHIFT = 16;
    /** Below this correlation with the running average, a frame is not of the same spectrum. */
    private static final double MIN_STILL_CORRELATION = 0.9;
    /** One column in this many is enough to tell whether the spectrum moved. */
    private static final int PROFILE_COLUMN_STEP = 8;
    private static final double[] SCALES = scales();

    private final ReentrantLock lock = new ReentrantLock();
    private float[][] accumulator;
    private Wavelen lastWavelength;
    private int streak;
    private boolean flipped;

    /**
     * How much an identification can be trusted. A line is always proposed when the frame
     * carries a spectrum, so this is what tells a firm answer from a mere best guess.
     */
    public enum Confidence {
        /** The best hypothesis did not pass the gates: it is a guess, and may well be wrong. */
        NONE,
        /** The hypothesis is the line of the previous frames, kept because the frame is ambiguous. */
        LOW,
        /** The hypothesis won clearly, or is confirmed by the previous frames. */
        MEDIUM,
        /** The hypothesis won clearly and several successive frames agree on it. */
        HIGH
    }

    /**
     * An absorption line found in the window.
     *
     * @param wavelength the wavelength of the line
     * @param name the name of the line in the catalog, or null when it has none
     * @param pixelShift the position of the line relative to the centre of the window
     * @param depth how deep the line is, as a fraction of the local continuum
     */
    public record Line(Wavelen wavelength, String name, double pixelShift, double depth) {
    }

    /**
     * The line the window is centred on.
     *
     * @param wavelength the wavelength of the line
     * @param name the name of the line in the catalog, or null when it has none
     * @param score the correlation of the observed profile with the reference spectrum
     * @param margin the lead over the best competing line
     * @param binning the binning which best explains the observation
     */
    public record Anchor(Wavelen wavelength, String name, double score, double margin, int binning) {
    }

    /**
     * The outcome of the identification of a frame.
     *
     * @param width the frame width
     * @param height the frame height
     * @param polynomial the polynomial describing the curvature of the lines, or null when no spectrum was found
     * @param leftBorder the first column carrying spectrum
     * @param rightBorder the column following the last one carrying spectrum
     * @param flipped whether the frame had to be turned over, its rows running the other way
     * than the wavelength; the polynomial and the lines are nonetheless expressed in the
     * coordinates of the frame as received, which is where they are drawn
     * @param anchor the line the window is centred on, or null when no line was identified
     * @param angstromsPerPixel the dispersion at the line the window is centred on, which
     * is not quite the dispersion elsewhere in the window
     * @param dispersionScale the ratio of the dispersion to its theoretical value
     * @param streak how many successive frames identified the same line
     * @param confidence how much the identification can be trusted
     * @param lines the lines found in the window, in increasing pixel shift order
     */
    public record Identification(int width,
                                 int height,
                                 DoubleQuadruplet polynomial,
                                 int leftBorder,
                                 int rightBorder,
                                 boolean flipped,
                                 Anchor anchor,
                                 double angstromsPerPixel,
                                 double dispersionScale,
                                 int streak,
                                 Confidence confidence,
                                 List<Line> lines) {
        public boolean spectrumFound() {
            return polynomial != null;
        }

        public boolean identified() {
            return anchor != null;
        }
    }

    /**
     * Prepares the reference spectrum, so that the first identification is as fast as
     * the following ones.
     */
    public static void warmUp() {
        DeepLineIdentifier.warmUp(true);
    }

    /**
     * Forgets the previously identified line and the accumulated frames.
     */
    public void reset() {
        lock.lock();
        try {
            accumulator = null;
            lastWavelength = null;
            streak = 0;
            flipped = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Identifies the lines of a frame. Short exposures give noisy frames, so the
     * identification is performed on a running average of the last frames.
     *
     * @param data the frame, one row per dispersion position
     * @param width the frame width
     * @param height the frame height
     * @param instrument the spectroheliograph
     * @param pixelSize the sensor pixel size, in micrometers
     * @param averagedFrames how many frames the running average spans, 1 to use the frame as is
     * @param binnings the binnings to consider
     * @return the identification
     */
    public Identification identify(float[][] data,
                                   int width,
                                   int height,
                                   SpectroHeliograph instrument,
                                   double pixelSize,
                                   int averagedFrames,
                                   int... binnings) {
        if (binnings.length == 0) {
            throw new IllegalArgumentException("At least one binning must be considered");
        }
        lock.lock();
        try {
            var averaged = accumulate(data, width, height, averagedFrames);
            var frame = flipped ? flipRows(averaged) : averaged;
            var analysis = new SpectrumFrameAnalyzer(width, height, false, null).analyze(frame);
            var quadruplet = analysis.distortionQuadruplet().filter(SpectralWindowIdentifier::isFinite).orElse(null);
            if (quadruplet != null && upsideDown(quadruplet, analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(width))) {
                // The rows run the other way than the wavelength: turn the frame over and
                // look again. The orientation is remembered, being a property of the capture
                // software rather than of the frame.
                flipped = !flipped;
                frame = flipped ? flipRows(averaged) : averaged;
                analysis = new SpectrumFrameAnalyzer(width, height, false, null).analyze(frame);
                quadruplet = analysis.distortionQuadruplet().filter(SpectralWindowIdentifier::isFinite).orElse(null);
            }
            if (quadruplet == null) {
                return inFrameCoordinates(unidentified(width, height, null, 0, width, flipped));
            }
            var leftBorder = analysis.leftBorder().orElse(0);
            var rightBorder = analysis.rightBorder().orElse(width);
            var probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, pixelSize, binnings[0], instrument);
            var profile = SpectrumAnalyzer.computeDataPoints(probe, quadruplet.asPolynomial(), leftBorder, rightBorder, width, height, frame);
            // A frame observed live comes through the atmosphere, whose lines the reference
            // has to carry too: around H-alpha they are as deep as the solar ones.
            var ranked = DeepLineIdentifier.rank(profile, instrument, pixelSize, true, binnings);
            if (ranked.isEmpty()) {
                return inFrameCoordinates(unidentified(width, height, quadruplet, leftBorder, rightBorder, flipped));
            }
            // The observer is looking at a line and wants to know which one it is, so the
            // best hypothesis is always reported: declining to answer, as the free search
            // does on an already recorded scan, would leave the live view blank exactly
            // when it is being used. The gates only decide how far the answer can be
            // trusted, which the confidence carries to the display.
            var selected = DeepLineIdentifier.select(ranked, false);
            var tracked = selected.isEmpty() ? continuityFallback(ranked) : Optional.<DeepLineIdentifier.Result>empty();
            var result = selected.or(() -> tracked).orElseGet(ranked::getFirst);
            streak = lastWavelength != null && sameLine(lastWavelength, result.wavelength()) ? streak + 1 : 1;
            lastWavelength = result.wavelength();
            var refined = DeepLineIdentifier.refine(profile, instrument, pixelSize, result.binning(), result.wavelength(), true, SCALES);
            var solution = WavelengthSolution.around(instrument, result.wavelength().angstroms(),
                    pixelSize * result.binning(), refined.dispersionScale());
            var lines = findLines(profile, instrument, pixelSize, result, solution, refined.sigmaIndex());
            var anchor = new Anchor(result.wavelength(), nameOf(result.wavelength()), result.score(),
                    DeepLineIdentifier.margin(ranked, result), result.binning());
            var confidence = selected.isPresent() ? confidence(result.score(), streak)
                    : tracked.isPresent() ? Confidence.LOW : Confidence.NONE;
            return inFrameCoordinates(new Identification(width, height, quadruplet, leftBorder, rightBorder, flipped, anchor,
                    solution.angstromsPerPixel(), refined.dispersionScale(), streak, confidence, lines));
        } finally {
            lock.unlock();
        }
    }

    private float[][] accumulate(float[][] data, int width, int height, int averagedFrames) {
        if (averagedFrames <= 1) {
            accumulator = null;
            return data;
        }
        if (accumulator == null || accumulator.length != height || accumulator[0].length != width) {
            accumulator = new float[height][];
            for (int y = 0; y < height; y++) {
                accumulator[y] = data[y].clone();
            }
            return accumulator;
        }
        if (moved(accumulator, data, width, height)) {
            // The spectrum shifted since the last frame, as it does while the grating is
            // being turned. Averaging frames taken at different positions blends two
            // spectra into none, so the average starts over from this frame.
            for (int y = 0; y < height; y++) {
                System.arraycopy(data[y], 0, accumulator[y], 0, width);
            }
            return accumulator;
        }
        var alpha = 1f / averagedFrames;
        for (int y = 0; y < height; y++) {
            var target = accumulator[y];
            var source = data[y];
            for (int x = 0; x < width; x++) {
                target[x] += alpha * (source[x] - target[x]);
            }
        }
        return accumulator;
    }

    /**
     * Whether the spectrum of a frame is not where it was in the running average, comparing
     * the vertical profile of the two: the lines carry enough structure for the profiles to
     * correlate almost perfectly when they are aligned, and to fall apart within a pixel.
     */
    private static boolean moved(float[][] average, float[][] frame, int width, int height) {
        var before = verticalProfile(average, width, height);
        var after = verticalProfile(frame, width, height);
        var bestShift = 0;
        var best = Double.NEGATIVE_INFINITY;
        for (int shift = -MAX_TRACKED_SHIFT; shift <= MAX_TRACKED_SHIFT; shift++) {
            var correlation = correlation(before, after, shift);
            if (correlation > best) {
                best = correlation;
                bestShift = shift;
            }
        }
        return bestShift != 0 || best < MIN_STILL_CORRELATION;
    }

    private static double[] verticalProfile(float[][] data, int width, int height) {
        var profile = new double[height];
        for (int y = 0; y < height; y++) {
            var sum = 0d;
            var count = 0;
            for (int x = 0; x < width; x += PROFILE_COLUMN_STEP) {
                sum += data[y][x];
                count++;
            }
            profile[y] = sum / count;
        }
        return profile;
    }

    private static double correlation(double[] a, double[] b, int shift) {
        var from = Math.max(0, -shift);
        var to = Math.min(a.length, b.length - shift);
        var n = to - from;
        if (n < 2) {
            return Double.NEGATIVE_INFINITY;
        }
        var meanA = 0d;
        var meanB = 0d;
        for (int i = from; i < to; i++) {
            meanA += a[i];
            meanB += b[i + shift];
        }
        meanA /= n;
        meanB /= n;
        var covariance = 0d;
        var varianceA = 0d;
        var varianceB = 0d;
        for (int i = from; i < to; i++) {
            var da = a[i] - meanA;
            var db = b[i + shift] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        return covariance / Math.max(1e-12, Math.sqrt(varianceA * varianceB));
    }

    private static boolean isFinite(DoubleQuadruplet polynomial) {
        return Double.isFinite(polynomial.a()) && Double.isFinite(polynomial.b())
               && Double.isFinite(polynomial.c()) && Double.isFinite(polynomial.d());
    }

    private static Identification unidentified(int width, int height, DoubleQuadruplet polynomial, int leftBorder, int rightBorder, boolean flipped) {
        return new Identification(width, height, polynomial, leftBorder, rightBorder, flipped, null, 0, 1, 0, Confidence.NONE, List.of());
    }

    /**
     * Whether the frame runs the other way than the frames JSol'Ex reads. The smile of a
     * grating bends the ends of the lines towards the longer wavelengths, and the wavelength
     * grows with the row in the frames JSol'Ex reads, so the curvature the analyzer fits is
     * positive: a negative one means the rows run the other way, as they do when a capture
     * software reads the sensor bottom-up. A sag too small to measure, as on a narrow frame,
     * says nothing either way.
     */
    private static boolean upsideDown(DoubleQuadruplet polynomial, int leftBorder, int rightBorder) {
        var halfWidth = (rightBorder - leftBorder) / 2.0;
        var sag = Math.abs(polynomial.b()) * halfWidth * halfWidth;
        return polynomial.b() < 0 && sag >= MIN_SAG_PIXELS;
    }

    private static float[][] flipRows(float[][] data) {
        var flipped = new float[data.length][];
        for (int y = 0; y < data.length; y++) {
            flipped[y] = data[data.length - 1 - y];
        }
        return flipped;
    }

    /**
     * Expresses an identification made on a frame turned over in the coordinates of the
     * frame as received, which is where the caller draws it: a row becomes the row counted
     * from the other end, so a line found below the centre line is above it.
     */
    private static Identification inFrameCoordinates(Identification identification) {
        if (!identification.flipped()) {
            return identification;
        }
        var p = identification.polynomial();
        var polynomial = p == null ? null : new DoubleQuadruplet(-p.a(), -p.b(), -p.c(), identification.height() - 1 - p.d());
        var lines = identification.lines().stream()
                .map(line -> new Line(line.wavelength(), line.name(), -line.pixelShift(), line.depth()))
                .sorted(Comparator.comparingDouble(Line::pixelShift))
                .toList();
        return new Identification(identification.width(), identification.height(), polynomial,
                identification.leftBorder(), identification.rightBorder(), true, identification.anchor(),
                identification.angstromsPerPixel(), identification.dispersionScale(), identification.streak(),
                identification.confidence(), lines);
    }

    /**
     * The previously identified line, when the ranking still holds it as good as its own
     * winner. The window only moves slowly while the observer tunes the instrument, so a
     * line which is still tied with the winner is a likelier answer than a winner which
     * changes from one frame to the next. The slack is what lets go of it: turning the
     * grating makes the score of the line being left behind fall away quickly.
     */
    private Optional<DeepLineIdentifier.Result> continuityFallback(List<DeepLineIdentifier.Result> ranked) {
        if (lastWavelength == null || ranked.isEmpty()) {
            return Optional.empty();
        }
        var best = ranked.getFirst();
        return ranked.stream()
                .filter(candidate -> sameLine(candidate.wavelength(), lastWavelength))
                .findFirst()
                .filter(candidate -> candidate.score() >= best.score() - CONTINUITY_SCORE_SLACK);
    }

    private static boolean sameLine(Wavelen first, Wavelen second) {
        return Math.abs(first.angstroms() - second.angstroms()) <= SAME_LINE_TOLERANCE_ANGSTROMS;
    }

    private static Confidence confidence(double score, int streak) {
        if (streak >= HIGH_CONFIDENCE_STREAK && score >= HIGH_CONFIDENCE_SCORE) {
            return Confidence.HIGH;
        }
        if (streak >= MEDIUM_CONFIDENCE_STREAK || score >= HIGH_CONFIDENCE_SCORE) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }

    private static List<Line> findLines(List<SpectrumAnalyzer.DataPoint> profile,
                                        SpectroHeliograph instrument,
                                        double pixelSize,
                                        DeepLineIdentifier.Result result,
                                        WavelengthSolution solution,
                                        int sigmaIndex) {
        var normalized = DeepLineIdentifier.normalizedObservation(profile, instrument, pixelSize, result.binning());
        var shifts = profile.stream().mapToDouble(SpectrumAnalyzer.DataPoint::pixelShift).toArray();
        // The dispersion varies by a couple of percent at most over a window, which is
        // neither here nor there for a tolerance, so the one at the centre will do.
        var tolerance = LINE_MATCH_TOLERANCE_PIXELS * solution.angstromsPerPixel();
        var atlasLines = DeepLineIdentifier.atlasLines(true, sigmaIndex,
                solution.wavelengthAt(shifts[0]) - tolerance,
                solution.wavelengthAt(shifts[shifts.length - 1]) + tolerance);
        var byWavelength = new LinkedHashMap<Double, Line>();
        for (int i = MINIMUM_HALF_WIDTH_PIXELS; i < normalized.length - MINIMUM_HALF_WIDTH_PIXELS; i++) {
            var depth = 1 - normalized[i];
            if (depth < MIN_LINE_DEPTH || !isLocalMinimum(normalized, i)) {
                continue;
            }
            var pixelShift = shifts[i] + parabolicOffset(normalized, i);
            var observed = solution.wavelengthAt(pixelShift);
            var matched = nearest(atlasLines, observed);
            if (matched.isEmpty() || Math.abs(matched.getAsDouble() - observed) > tolerance) {
                continue;
            }
            var wavelength = Wavelen.ofAngstroms(matched.getAsDouble());
            var line = new Line(wavelength, nameOf(wavelength), pixelShift, depth);
            byWavelength.merge(matched.getAsDouble(), line, (a, b) -> a.depth() >= b.depth() ? a : b);
        }
        return byWavelength.values().stream()
                .sorted(Comparator.comparingDouble(Line::pixelShift))
                .toList();
    }

    private static boolean isLocalMinimum(double[] values, int i) {
        for (int k = 1; k <= MINIMUM_HALF_WIDTH_PIXELS; k++) {
            if (values[i] >= values[i - k] || values[i] > values[i + k]) {
                return false;
            }
        }
        return true;
    }

    private static double parabolicOffset(double[] values, int i) {
        var left = values[i - 1];
        var center = values[i];
        var right = values[i + 1];
        var denominator = left - 2 * center + right;
        if (denominator <= 1e-12) {
            return 0;
        }
        return Math.clamp(0.5 * (left - right) / denominator, -0.5, 0.5);
    }

    private static OptionalDouble nearest(double[] sorted, double value) {
        if (sorted.length == 0) {
            return OptionalDouble.empty();
        }
        var index = Arrays.binarySearch(sorted, value);
        if (index >= 0) {
            return OptionalDouble.of(sorted[index]);
        }
        var insertion = -index - 1;
        if (insertion == 0) {
            return OptionalDouble.of(sorted[0]);
        }
        if (insertion == sorted.length) {
            return OptionalDouble.of(sorted[sorted.length - 1]);
        }
        var below = sorted[insertion - 1];
        var above = sorted[insertion];
        return OptionalDouble.of(value - below <= above - value ? below : above);
    }

    /**
     * The name of a line: the one of the catalog when it knows the line, "telluric" when
     * the atmosphere absorbs there, since that is what the observer is looking at.
     */
    private static String nameOf(Wavelen wavelength) {
        return SpectralLineCatalog.findClosest(wavelength, CATALOG_NAME_TOLERANCE_ANGSTROMS)
                .map(SpectralLineCatalog.CatalogLine::shortName)
                .orElseGet(() -> TelluricTransmission.transmissionAt(wavelength) < TELLURIC_LINE_TRANSMISSION ? TELLURIC_LINE_NAME : null);
    }

    private static double[] scales() {
        var steps = (int) Math.round(MAX_SCALE_DEVIATION / SCALE_STEP);
        var scales = new double[2 * steps];
        for (int i = 1; i <= steps; i++) {
            scales[2 * i - 2] = 1 - i * SCALE_STEP;
            scales[2 * i - 1] = 1 + i * SCALE_STEP;
        }
        return scales;
    }
}
