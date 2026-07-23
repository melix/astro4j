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
package me.champeau.a4j.jsolex.processing.expr.impl.deghost;

import java.util.Arrays;

import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.AZIMUTH_SAMPLES;
import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.RADIAL_SAMPLES;

/**
 * Per-azimuth measurement of a reflection at its rim: the amplitude (the excess step across the rim, where the real
 * background and coronal detail are continuous and therefore cancel), the excess baseline just beyond the rim and
 * its radial slope. A candidate rim placed on a glow that continues past it has a high baseline, which neutralises
 * the removal.
 */
public record RimProfile(double[] alpha, double[] baseline, double[] slope, double[] reference) {

    public static final double RIM_FEATHER = 0.08;         // typical blur width of the crescent rim (× R)
    public static final double INNER_FEATHER = 0.015;      // limb feather (× R)
    public static final double ALPHA_SIGMA_BINS = 20;      // ≈ 10° smoothing of the rim amplitude

    private static final double BAND_NEAR = 0.06;          // rim amplitude bands, distances from the rim (× R)
    private static final double BAND_FAR = 0.18;
    private static final double MIN_BAND_WIDTH = 0.04;     // minimum usable band width (× R)
    private static final double BASELINE_PERCENTILE = 0.25; // robust against bright rows crossing the baseline bands

    public static RimProfile measure(double[][] excess, GhostShift shift, double innerR, double outerR, double radius) {
        var prefix = radialPrefixSums(excess);
        var alpha = new double[1][AZIMUTH_SAMPLES];
        var baseline = new double[1][AZIMUTH_SAMPLES];
        var slope = new double[1][AZIMUTH_SAMPLES];
        var reference = new double[AZIMUTH_SAMPLES];
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            var rim = shift.rimAt(it, radius);
            var crescentWidth = rim - innerR;
            if (crescentWidth <= 4 * PolarGrid.radialStep(innerR, outerR)) {
                continue;
            }
            var near = Math.min(BAND_NEAR * radius, 0.25 * crescentWidth);
            var far = Math.min(BAND_FAR * radius, 0.8 * crescentWidth);
            var minWidth = Math.max(3 * PolarGrid.radialStep(innerR, outerR), Math.min(MIN_BAND_WIDTH * radius, 0.25 * crescentWidth));
            var nearOut = Math.max(RIM_FEATHER * radius, near);
            var bandWidth = Math.max(far - near, minWidth);
            var inner = band(rim - far, rim - near, minWidth, innerR, outerR, radius);
            var outer = band(rim + nearOut, rim + nearOut + bandWidth, minWidth, innerR, outerR, radius);
            var beyond = band(rim + nearOut + bandWidth, rim + nearOut + 2 * bandWidth, minWidth, innerR, outerR, radius);
            if (outer != null) {
                baseline[0][it] = bandPercentile(excess, it, outer);
                reference[it] = PolarGrid.radiusAt((outer.from() + outer.to()) / 2, innerR, outerR);
                if (beyond != null) {
                    var beyondValue = bandPercentile(excess, it, beyond);
                    var beyondRadius = PolarGrid.radiusAt((beyond.from() + beyond.to()) / 2, innerR, outerR);
                    if (beyondRadius > reference[it]) {
                        slope[0][it] = Math.min(0, (beyondValue - baseline[0][it]) / (beyondRadius - reference[it]));
                    }
                }
                if (inner != null) {
                    alpha[0][it] = Math.max(0, bandMean(prefix[it], inner) - baseline[0][it]);
                }
            }
        }
        PolarGrid.smoothAzimuthal(alpha, ALPHA_SIGMA_BINS);
        PolarGrid.smoothAzimuthal(baseline, ALPHA_SIGMA_BINS);
        PolarGrid.smoothAzimuthal(slope, ALPHA_SIGMA_BINS);
        return new RimProfile(alpha[0], baseline[0], slope[0], reference);
    }

    /**
     * Baseline under the crescent at the given radius: the level just beyond the rim, extended inward along the
     * radial trend measured beyond the rim, so that decaying glow is not mistaken for reflection amplitude.
     */
    public double baselineAt(int azimuthBin, double r) {
        return Math.max(0, baseline[azimuthBin] + slope[azimuthBin] * (r - reference[azimuthBin]));
    }

    private record Band(int from, int to) {
    }

    private static Band band(double rFrom, double rTo, double minWidth, double innerR, double outerR, double radius) {
        var lo = Math.max(rFrom, innerR + INNER_FEATHER * radius);
        var from = Math.max(0, (int) Math.ceil(PolarGrid.indexAt(lo, innerR, outerR)));
        var to = Math.min(RADIAL_SAMPLES - 1, (int) Math.floor(PolarGrid.indexAt(Math.min(rTo, outerR), innerR, outerR)));
        if ((to - from + 1) * PolarGrid.radialStep(innerR, outerR) < minWidth) {
            return null;
        }
        return new Band(from, to);
    }

    private static double bandMean(double[] prefixRow, Band band) {
        return (prefixRow[band.to() + 1] - prefixRow[band.from()]) / (band.to() - band.from() + 1);
    }

    private static double bandPercentile(double[][] excess, int azimuthBin, Band band) {
        var values = new double[band.to() - band.from() + 1];
        for (int ir = band.from(); ir <= band.to(); ir++) {
            values[ir - band.from()] = excess[ir][azimuthBin];
        }
        Arrays.sort(values);
        return values[(int) (BASELINE_PERCENTILE * (values.length - 1))];
    }

    private static double[][] radialPrefixSums(double[][] polar) {
        var prefix = new double[AZIMUTH_SAMPLES][RADIAL_SAMPLES + 1];
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                prefix[it][ir + 1] = prefix[it][ir] + polar[ir][it];
            }
        }
        return prefix;
    }
}
