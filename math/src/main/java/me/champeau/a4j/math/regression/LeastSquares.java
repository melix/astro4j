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

import me.champeau.a4j.math.matrix.DoubleMatrix;

/**
 * Accumulates the normal equations of a linear least-squares problem and solves them.
 * Callers add samples as {@code (basis, value)} pairs (optionally weighted) and call {@link #solve()}.
 */
public final class LeastSquares {
    private final int terms;
    private final double[][] xtx;
    private final double[] xty;

    public LeastSquares(int terms) {
        this.terms = terms;
        this.xtx = new double[terms][terms];
        this.xty = new double[terms];
    }

    public void add(double[] basis, double value) {
        add(basis, value, 1.0);
    }

    public void add(double[] basis, double value, double weight) {
        for (int i = 0; i < terms; i++) {
            var wb = weight * basis[i];
            xty[i] += wb * value;
            for (int j = i; j < terms; j++) {
                xtx[i][j] += wb * basis[j];
            }
        }
    }

    /**
     * @return the least-squares coefficients, or {@code null} if the system is singular or the solution is not finite
     */
    public double[] solve() {
        for (int i = 1; i < terms; i++) {
            for (int j = 0; j < i; j++) {
                xtx[i][j] = xtx[j][i];
            }
        }
        try {
            var rhs = new double[terms][1];
            for (int i = 0; i < terms; i++) {
                rhs[i][0] = xty[i];
            }
            var solved = DoubleMatrix.of(xtx).inverse().mul(DoubleMatrix.of(rhs)).asArray();
            var coeffs = new double[terms];
            for (int i = 0; i < terms; i++) {
                coeffs[i] = solved[i][0];
                if (!Double.isFinite(coeffs[i])) {
                    return null;
                }
            }
            return coeffs;
        } catch (Exception ex) {
            return null;
        }
    }
}
