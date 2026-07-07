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
package me.champeau.a4j.math.regression;

/**
 * Basis of a 2D polynomial surface, with terms ordered by ascending total degree.
 */
public final class Polynomial2D {
    private Polynomial2D() {
    }

    public static int termCount(int degree) {
        return (degree + 1) * (degree + 2) / 2;
    }

    /**
     * Fills {@code out} (length {@link #termCount(int)}) with the polynomial basis terms for {@code (dx, dy)}.
     */
    public static void terms(double dx, double dy, int degree, double[] out) {
        var xpow = new double[degree + 1];
        var ypow = new double[degree + 1];
        xpow[0] = 1;
        ypow[0] = 1;
        for (int i = 1; i <= degree; i++) {
            xpow[i] = xpow[i - 1] * dx;
            ypow[i] = ypow[i - 1] * dy;
        }
        var idx = 0;
        for (int d = 0; d <= degree; d++) {
            for (int px = d; px >= 0; px--) {
                out[idx++] = xpow[px] * ypow[d - px];
            }
        }
    }

    /**
     * Evaluates the surface at {@code (dx, dy)}; {@code scratch} is a reusable buffer of length {@code coeffs.length}.
     */
    public static double evaluate(double[] coeffs, double dx, double dy, int degree, double[] scratch) {
        terms(dx, dy, degree, scratch);
        var value = 0.0;
        for (int i = 0; i < coeffs.length; i++) {
            value += coeffs[i] * scratch[i];
        }
        return value;
    }
}
