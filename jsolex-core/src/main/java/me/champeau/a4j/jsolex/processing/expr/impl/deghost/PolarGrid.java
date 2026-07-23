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

import me.champeau.a4j.math.image.analysis.GaussianSupport;

/**
 * Geometry and smoothing utilities for the polar grid sampled around the solar disk, on which ghost detection and
 * removal operate. The grid covers the annulus from the limb to {@code SAMPLE_RADIUS} times the disk radius, with
 * radius as the first index and azimuth (from -180° to 180°) as the second.
 */
public final class PolarGrid {

    public static final int AZIMUTH_SAMPLES = 720;
    public static final int RADIAL_SAMPLES = 240;

    private PolarGrid() {
    }

    public static double radiusAt(int ir, double innerR, double outerR) {
        return innerR + (ir / (double) (RADIAL_SAMPLES - 1)) * (outerR - innerR);
    }

    public static double indexAt(double r, double innerR, double outerR) {
        return (r - innerR) / (outerR - innerR) * (RADIAL_SAMPLES - 1);
    }

    public static double radialStep(double innerR, double outerR) {
        return (outerR - innerR) / (RADIAL_SAMPLES - 1);
    }

    public static double azimuthAt(int azimuthBin) {
        return -Math.PI + azimuthBin * (2 * Math.PI / AZIMUTH_SAMPLES);
    }

    public static void smoothAzimuthal(double[][] polar, double sigma) {
        var kernel = GaussianSupport.gaussianKernel1D(sigma);
        var half = kernel.length / 2;
        var nt = polar[0].length;
        var row = new double[nt];
        for (var radial : polar) {
            System.arraycopy(radial, 0, row, 0, nt);
            for (int it = 0; it < nt; it++) {
                var sum = 0.0;
                for (int k = -half; k <= half; k++) {
                    var idx = ((it + k) % nt + nt) % nt;
                    sum += row[idx] * kernel[k + half];
                }
                radial[it] = sum;
            }
        }
    }

    public static void smoothRadial(double[][] polar, double sigma) {
        var kernel = GaussianSupport.gaussianKernel1D(sigma);
        var half = kernel.length / 2;
        var nr = polar.length;
        var nt = polar[0].length;
        var col = new double[nr];
        for (int it = 0; it < nt; it++) {
            for (int ir = 0; ir < nr; ir++) {
                col[ir] = polar[ir][it];
            }
            for (int ir = 0; ir < nr; ir++) {
                var sum = 0.0;
                var weight = 0.0;
                for (int k = -half; k <= half; k++) {
                    var idx = ir + k;
                    if (idx < 0 || idx >= nr) {
                        continue;
                    }
                    sum += col[idx] * kernel[k + half];
                    weight += kernel[k + half];
                }
                polar[ir][it] = sum / weight;
            }
        }
    }

    /**
     * In-place 1D Gaussian smoothing of a radial profile, ignoring {@code NaN} entries.
     */
    public static void smoothProfile(double[] values, double sigma) {
        var kernel = GaussianSupport.gaussianKernel1D(sigma);
        var half = kernel.length / 2;
        var copy = values.clone();
        for (int i = 0; i < values.length; i++) {
            var sum = 0.0;
            var weight = 0.0;
            for (int k = -half; k <= half; k++) {
                var idx = i + k;
                if (idx < 0 || idx >= values.length || Double.isNaN(copy[idx])) {
                    continue;
                }
                sum += copy[idx] * kernel[k + half];
                weight += kernel[k + half];
            }
            values[i] = weight == 0 ? Double.NaN : sum / weight;
        }
    }

    public static double samplePolar(double[][] polar, double fr, double ft) {
        var nr = polar.length;
        var nt = polar[0].length;
        fr = Math.max(0, Math.min(nr - 1.0001, fr));
        var r0 = (int) Math.floor(fr);
        var r1 = Math.min(r0 + 1, nr - 1);
        var wr = fr - r0;
        var t0 = ((int) Math.floor(ft) % nt + nt) % nt;
        var t1 = (t0 + 1) % nt;
        var wt = ft - Math.floor(ft);
        var top = polar[r0][t0] * (1 - wt) + polar[r0][t1] * wt;
        var bottom = polar[r1][t0] * (1 - wt) + polar[r1][t1] * wt;
        return top * (1 - wr) + bottom * wr;
    }

    public static double smoothstep(double x) {
        var t = x < 0 ? 0 : (x > 1 ? 1 : x);
        return t * t * (3 - 2 * t);
    }
}
