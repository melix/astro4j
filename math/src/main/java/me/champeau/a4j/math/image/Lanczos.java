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
package me.champeau.a4j.math.image;

/**
 * Lanczos-3 interpolation. The kernel is separable, so the horizontal weights are
 * computed once per sample and reused for every row of the neighbourhood. An
 * instance holds a buffer for these weights and must not be shared between threads.
 */
final class Lanczos {
    private static final int A = 3;

    private final double[] weights = new double[2 * A];

    float interpolate(float[][] image, double xx, double yy, int width, int height) {
        int x0 = (int) Math.floor(xx);
        int y0 = (int) Math.floor(yy);
        int iStart = Math.max(x0 - A + 1, 0);
        int iEnd = Math.min(x0 + A, width - 1);
        int jStart = Math.max(y0 - A + 1, 0);
        int jEnd = Math.min(y0 + A, height - 1);

        double wxSum = 0.0;
        for (int i = iStart; i <= iEnd; i++) {
            double wx = kernel(xx - i);
            weights[i - iStart] = wx;
            wxSum += wx;
        }

        double sum = 0.0;
        double wySum = 0.0;
        for (int j = jStart; j <= jEnd; j++) {
            double wy = kernel(yy - j);
            var row = image[j];
            double rowSum = 0.0;
            for (int i = iStart; i <= iEnd; i++) {
                rowSum += row[i] * weights[i - iStart];
            }
            sum += rowSum * wy;
            wySum += wy;
        }

        double weightSum = wxSum * wySum;
        return weightSum > 0 ? (float) (sum / weightSum) : 0f;
    }

    private static double kernel(double x) {
        if (x == 0) {
            return 1.0;
        }
        double absX = Math.abs(x);
        if (absX >= A) {
            return 0.0;
        }
        double pix = Math.PI * x;
        return (A * Math.sin(pix) * Math.sin(pix / A)) / (pix * pix);
    }
}
