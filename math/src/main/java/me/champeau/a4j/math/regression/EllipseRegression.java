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

import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.matrix.DoubleMatrix;
import me.champeau.a4j.math.tuples.DoubleSextuplet;

import java.util.List;

/**
 * Implements the numerically stable direct least squares fitting
 * of ellipses algorith, presented by Radim Halíř and Jan Flusser
 * in https://autotrace.sourceforge.net/WSCG98.pdf
 */
public class EllipseRegression {
    private static final DoubleMatrix CONSTRAINT_C1_INV = DoubleMatrix.of(new double[][]{
            {0, 0, 2},
            {0, -1, 0},
            {2, 0, 0}
    }).inverse();

    private final List<Point2D> samples;

    /**
     * Creates a new ellipse regression solver.
     * @param samples the data points to fit
     */
    public EllipseRegression(List<Point2D> samples) {
        this.samples = samples;
    }

    private DoubleMatrix designMatrix1() {
        double[][] result = new double[samples.size()][3];
        for (int i = 0; i < samples.size(); i++) {
            var p = samples.get(i);
            var x = p.x();
            var y = p.y();
            result[i][0] = x * x;
            result[i][1] = x * y;
            result[i][2] = y * y;
        }
        return DoubleMatrix.of(result);
    }

    private DoubleMatrix designMatrix2() {
        double[][] result = new double[samples.size()][3];
        for (int i = 0; i < samples.size(); i++) {
            var p = samples.get(i);
            var x = p.x();
            var y = p.y();
            result[i][0] = x;
            result[i][1] = y;
            result[i][2] = 1d;
        }
        return DoubleMatrix.of(result);
    }

    /**
     * Solves the ellipse fitting problem.
     * @return the fitted ellipse
     */
    public Ellipse solve() {
        var d1 = designMatrix1();
        var d2 = designMatrix2();
        var td1 = d1.transpose();
        var s1 = td1.mul(d1);
        var s2 = td1.mul(d2);
        var s3 = d2.transpose().mul(d2);
        var t = s3.inverse().neg().mul(s2.transpose());
        var m = CONSTRAINT_C1_INV.mul(s1.add(s2.mul(t)));
        var eig = m.solveEigenSystem();
        var abc = evaluateSolutions(eig);
        var def = t.mul(DoubleMatrix.of(abc)).asArray();
        return Ellipse.ofCartesian(
                new DoubleSextuplet(abc[0][0], abc[1][0], abc[2][0], def[0][0], def[1][0], def[2][0])
        );
    }

    /**
     * Computes element-wise scaled product of two vectors.
     * @param scale the scaling factor
     * @param vec1 the first vector
     * @param vec2 the second vector
     * @return the scaled product
     */
    public static double[] scalarProduct(double scale, double[] vec1, double[] vec2) {
        int len = vec1.length;
        if (len != vec2.length) {
            throw new IllegalArgumentException("Both vectors must have the same length");
        }
        double[] result = new double[len];
        for (int i = 0; i < len; i++) {
            result[i] = scale * vec1[i] * vec2[i];
        }
        return result;
    }

    /**
     * Subtracts one vector from another.
     * @param vec1 the first vector
     * @param vec2 the second vector
     * @return the difference vector
     */
    public static double[] substract(double[] vec1, double[] vec2) {
        int len = vec1.length;
        if (len != vec2.length) {
            throw new IllegalArgumentException("Both vectors must have the same length");
        }
        double[] result = new double[len];
        for (int i = 0; i < len; i++) {
            result[i] = vec1[i] - vec2[i];
        }
        return result;
    }

    /**
     * Evaluates the eigensystem to find the valid ellipse solution.
     * @param system the eigensystem
     * @return the solution coefficients
     */
    public static double[][] evaluateSolutions(DoubleMatrix.EigenSystem system) {
        double[][] vectors = system.vectors();
        double[] v0 = vectors[0];
        double[] v1 = vectors[1];
        double[] v2 = vectors[2];
        double[] eval = substract(scalarProduct(4, v0, v2), scalarProduct(1, v1, v1));
        for (int i = 0; i < 3; i++) {
            double e = eval[i];
            if (e > 0) {
                return new double[][]{
                        new double[]{v0[i]},
                        new double[]{v1[i]},
                        new double[]{v2[i]}
                };
            }
        }
        throw new IllegalArgumentException("Unable to find solution");
    }

}
