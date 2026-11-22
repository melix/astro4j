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
package me.champeau.a4j.jsolex.processing.sun.detection;

import me.champeau.a4j.jsolex.processing.util.Dispersion;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.GUARD_RANGE;
import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.LOCAL_RANGE;
import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.MIN_REFERENCE_ROWS;
import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.REFERENCE_END_ANGSTROMS;
import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.WING_END_ANGSTROMS;

/**
 * The line profile of a detected brightening, compared with the profiles of the ring of
 * neighbouring columns used by the detector. The profile of the brightening is the mean
 * of 3 adjacent columns. Each profile is divided by the mean intensity of its far wings.
 *
 * @param offsets the distance to the line center of each sample, in angstroms, negative towards the blue
 * @param detection the profile of the brightening
 * @param neighbours the median profile of the neighbours
 * @param scatter the scatter of the profiles of the neighbours, as a robust standard deviation
 */
public record EllermanBombProfile(double[] offsets, double[] detection, double[] neighbours, double[] scatter) {
    private static final int AVERAGED_COLUMNS = 1;
    private static final double MAD_TO_STANDARD_DEVIATION = 1.4826;

    /**
     * Extracts the profiles around a column of a frame.
     *
     * @param frame the frame, indexed by row then column
     * @param column the column of the brightening
     * @param polynomial the row of the line center for each column
     * @param dispersion the spectral dispersion
     * @param wavelengthGrowsWithRow whether the wavelength grows with the row index
     * @return the profile, or empty if the frame doesn't contain the far wings of the line around the column
     */
    public static Optional<EllermanBombProfile> extract(float[][] frame,
                                                        int column,
                                                        DoubleUnaryOperator polynomial,
                                                        Dispersion dispersion,
                                                        boolean wavelengthGrowsWithRow) {
        int height = frame.length;
        int width = frame[0].length;
        if (column - LOCAL_RANGE < 0 || column + LOCAL_RANGE >= width) {
            return Optional.empty();
        }
        double angstromsPerPixel = dispersion.angstromsPerPixel();
        int margin = EllermanBombDetector.frameMargin(height);
        double available = REFERENCE_END_ANGSTROMS / angstromsPerPixel;
        for (int x = column - LOCAL_RANGE; x <= column + LOCAL_RANGE; x++) {
            double center = polynomial.applyAsDouble(x);
            available = Math.min(available, Math.min(center - margin, height - 2 - margin - center));
        }
        int half = (int) Math.floor(available);
        var reference = EllermanBombDetector.ReferenceBand.of(WING_END_ANGSTROMS / angstromsPerPixel, half, angstromsPerPixel);
        int referenceRows = 0;
        for (int k = 0; k <= half; k++) {
            if (reference.contains(k)) {
                referenceRows++;
            }
        }
        if (referenceRows < MIN_REFERENCE_ROWS) {
            return Optional.empty();
        }
        int samples = 2 * half + 1;
        int direction = wavelengthGrowsWithRow ? 1 : -1;
        var offsets = new double[samples];
        for (int k = 0; k < samples; k++) {
            offsets[k] = (k - half) * angstromsPerPixel;
        }
        var detection = new double[samples];
        for (int x = column - AVERAGED_COLUMNS; x <= column + AVERAGED_COLUMNS; x++) {
            var profile = sample(frame, x, polynomial, half, direction);
            for (int k = 0; k < samples; k++) {
                detection[k] += profile[k] / (2 * AVERAGED_COLUMNS + 1);
            }
        }
        normalize(detection, half, reference);
        var ring = new double[2 * (LOCAL_RANGE - GUARD_RANGE + 1)][];
        int n = 0;
        for (int k = GUARD_RANGE; k <= LOCAL_RANGE; k++) {
            for (int x : new int[]{column - k, column + k}) {
                var profile = sample(frame, x, polynomial, half, direction);
                normalize(profile, half, reference);
                ring[n++] = profile;
            }
        }
        var neighbours = new double[samples];
        var scatter = new double[samples];
        var values = new double[n];
        for (int k = 0; k < samples; k++) {
            for (int i = 0; i < n; i++) {
                values[i] = ring[i][k];
            }
            Arrays.sort(values);
            var median = values[n / 2];
            for (int i = 0; i < n; i++) {
                values[i] = Math.abs(values[i] - median);
            }
            Arrays.sort(values);
            neighbours[k] = median;
            scatter[k] = MAD_TO_STANDARD_DEVIATION * values[n / 2];
        }
        return Optional.of(new EllermanBombProfile(offsets, detection, neighbours, scatter));
    }

    private static double[] sample(float[][] frame, int column, DoubleUnaryOperator polynomial, int half, int direction) {
        double center = polynomial.applyAsDouble(column);
        var profile = new double[2 * half + 1];
        for (int k = -half; k <= half; k++) {
            double row = center + direction * k;
            int row0 = (int) Math.floor(row);
            double t = row - row0;
            profile[k + half] = frame[row0][column] * (1 - t) + frame[row0 + 1][column] * t;
        }
        return profile;
    }

    private static void normalize(double[] profile, int half, EllermanBombDetector.ReferenceBand reference) {
        double sum = 0;
        int count = 0;
        for (int k = -half; k <= half; k++) {
            if (reference.contains(Math.abs(k))) {
                sum += profile[k + half];
                count++;
            }
        }
        var level = sum / count;
        for (int k = 0; k < profile.length; k++) {
            profile[k] /= level;
        }
    }
}
