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
package me.champeau.a4j.jsolex.processing.util;

public final class ImageInterpolation {
    private static final int LANCZOS_A = 3;
    private static final int LANCZOS_LUT_SIZE = 1024;
    private static final double LANCZOS_LUT_SCALE = (LANCZOS_LUT_SIZE - 1) / (2.0 * LANCZOS_A);
    private static final double[] LANCZOS_LUT = precomputeLanczosLUT();

    private ImageInterpolation() {
    }

    private static double[] precomputeLanczosLUT() {
        var lut = new double[LANCZOS_LUT_SIZE];
        for (var i = 0; i < LANCZOS_LUT_SIZE; i++) {
            var x = (i / (double) (LANCZOS_LUT_SIZE - 1)) * 2 * LANCZOS_A - LANCZOS_A;
            lut[i] = computeLanczosKernel(x);
        }
        return lut;
    }

    private static double computeLanczosKernel(double x) {
        if (x == 0) {
            return 1.0;
        }
        if (Math.abs(x) >= LANCZOS_A) {
            return 0.0;
        }
        var pix = Math.PI * x;
        return (LANCZOS_A * Math.sin(pix) * Math.sin(pix / LANCZOS_A)) / (pix * pix);
    }

    private static double lanczosKernel(double x) {
        var absX = Math.abs(x);
        if (absX >= LANCZOS_A) {
            return 0.0;
        }
        var lutIndex = (x + LANCZOS_A) * LANCZOS_LUT_SCALE;
        var idx = (int) lutIndex;
        if (idx >= LANCZOS_LUT_SIZE - 1) {
            return LANCZOS_LUT[LANCZOS_LUT_SIZE - 1];
        }
        if (idx < 0) {
            return LANCZOS_LUT[0];
        }
        var frac = lutIndex - idx;
        return LANCZOS_LUT[idx] + frac * (LANCZOS_LUT[idx + 1] - LANCZOS_LUT[idx]);
    }

    public static float lanczos2D(float[][] image, double xx, double yy, int width, int height) {
        var x0 = (int) Math.floor(xx);
        var y0 = (int) Math.floor(yy);

        var sum = 0.0;
        var weightSum = 0.0;

        for (var j = y0 - LANCZOS_A + 1; j <= y0 + LANCZOS_A; j++) {
            for (var i = x0 - LANCZOS_A + 1; i <= x0 + LANCZOS_A; i++) {
                if (i >= 0 && i < width && j >= 0 && j < height) {
                    var wx = lanczosKernel(xx - i);
                    var wy = lanczosKernel(yy - j);
                    var w = wx * wy;
                    sum += image[j][i] * w;
                    weightSum += w;
                }
            }
        }

        return weightSum > 0 ? (float) (sum / weightSum) : 0f;
    }

    public static float lanczos1D(float[] v, double x) {
        var center = (int) Math.round(x);
        var sum = 0.0;
        var wsum = 0.0;
        for (var i = center - LANCZOS_A + 1; i <= center + LANCZOS_A; i++) {
            var idx = Math.min(Math.max(i, 0), v.length - 1);
            var d = x - i;
            var w = lanczosKernel(d);
            sum += v[idx] * w;
            wsum += w;
        }
        return (float) (sum / wsum);
    }

    public static float bilinear(float[][] image, double xx, double yy, int width, int height) {
        var x0 = (int) Math.floor(xx);
        var x1 = Math.min(x0 + 1, width - 1);
        var y0 = (int) Math.floor(yy);
        var y1 = Math.min(y0 + 1, height - 1);

        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }

        var i00 = image[y0][x0];
        var i10 = image[y0][x1];
        var i01 = image[y1][x0];
        var i11 = image[y1][x1];

        var fx = xx - x0;
        var fy = yy - y0;

        var i0 = i00 + fx * (i10 - i00);
        var i1 = i01 + fx * (i11 - i01);

        return (float) (i0 + fy * (i1 - i0));
    }
}
