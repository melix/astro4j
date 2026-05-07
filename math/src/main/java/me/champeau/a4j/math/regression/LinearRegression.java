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
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.math.tuples.DoubleTriplet;

import java.util.function.DoubleUnaryOperator;

/**
 * A utility class for computing linear regressions.
 */
public abstract class LinearRegression {
    private LinearRegression() {
    }

    /**
     * Computes the first order regression of a series of points.
     *
     * @param series the series of points
     * @return the coefficients of the regression for the form y = a * x + b
     */
    public static DoublePair firstOrderRegression(Point2D[] series) {
        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumXY = 0;
        for (Point2D point : series) {
            double x = point.x();
            double y = point.y();
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
        }
        int n = series.length;
        double a = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double b = (sumY - a * sumX) / n;
        return new DoublePair(a, b);
    }

    /**
     * Computes the first order regression of a series of points with weights.
     *
     * @param series  the series of points
     * @param weights the weights associated to each point of the series
     * @return the coefficients of the regression for the form y = a * x + b
     */
    public static DoublePair firstOrderRegression(Point2D[] series, double[] weights) {
        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumXY = 0;
        double sumW = 0;
        for (int i = 0; i < series.length; i++) {
            double x = series[i].x();
            double y = series[i].y();
            double w = weights[i];
            sumX += w * x;
            sumY += w * y;
            sumXX += w * x * x;
            sumXY += w * x * y;
            sumW += w;
        }

        double a = (sumW * sumXY - sumX * sumY) / (sumW * sumXX - sumX * sumX);
        double b = (sumY - a * sumX) / sumW;

        return new DoublePair(a, b);
    }

    /**
     * Computes the second order regression of a series of points using the method of least squares.
     *
     * @param series the series of points
     * @return the coefficients of the regression for the form y = a * x^2 + b * x + c
     */
    public static DoubleTriplet secondOrderRegression(Point2D[] series) {
        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumXY = 0;
        double sumXXY = 0;
        double sumXXX = 0;
        double sumXXXX = 0;
        for (Point2D point : series) {
            double x = point.x();
            double y = point.y();
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
            sumXXX += x * x * x;
            sumXXXX += x * x * x * x;
            sumXXY += x * x * y;
        }
        int n = series.length;

        double sxx = sumXX - sumX * sumX / n;
        double sxy = sumXY - sumX * sumY / n;
        double sxx2 = sumXXX - sumX * sumXX / n;
        double sx2y = sumXXY - sumXX * sumY / n;
        double sx2x2 = sumXXXX - sumXX * sumXX / n;

        double a = (sx2y * sxx - sxy * sxx2) / (sxx * sx2x2 - sxx2 * sxx2);
        double b = (sxy * sx2x2 - sx2y * sxx2) / (sxx * sx2x2 - sxx2 * sxx2);
        double c = (sumY / n - b * sumX / n - a * sumXX / n);
        return new DoubleTriplet(a, b, c);
    }

    /**
     * Computes the second order regression of a series of points with weights.
     *
     * @param series  the series of points
     * @param weights the weights associated to each point of the series
     * @return the coefficients of the regression for the form y = a * x^2 + b * x + c
     */
    public static DoubleTriplet secondOrderRegression(Point2D[] series, double[] weights) {
        double sumW = 0;
        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumXY = 0;
        double sumXXY = 0;
        double sumXXX = 0;
        double sumXXXX = 0;
        for (int i = 0; i < series.length; i++) {
            double x = series[i].x();
            double y = series[i].y();
            double w = weights[i];
            sumW += w;
            sumX += w * x;
            sumY += w * y;
            sumXX += w * x * x;
            sumXY += w * x * y;
            sumXXX += w * x * x * x;
            sumXXXX += w * x * x * x * x;
            sumXXY += w * x * x * y;
        }

        double sxx = sumXX - sumX * sumX / sumW;
        double sxy = sumXY - sumX * sumY / sumW;
        double sxx2 = sumXXX - sumX * sumXX / sumW;
        double sx2y = sumXXY - sumXX * sumY / sumW;
        double sx2x2 = sumXXXX - sumXX * sumXX / sumW;

        double a = (sx2y * sxx - sxy * sxx2) / (sxx * sx2x2 - sxx2 * sxx2);
        double b = (sxy * sx2x2 - sx2y * sxx2) / (sxx * sx2x2 - sxx2 * sxx2);
        double c = (sumY / sumW - b * sumX / sumW - a * sumXX / sumW);
        return new DoubleTriplet(a, b, c);
    }

    /**
     * Computes the third order regression of a series of points using the method of least squares.
     *
     * @param series the series of points
     * @return the coefficients of the regression for the form y = a * x^3 + b * x^2 + c * x + d
     */
    public static DoubleQuadruplet thirdOrderRegression(Point2D[] series) {
        var result = kOrderRegression(series, 3);

        return new DoubleQuadruplet(result[0], result[1], result[2], result[3]);
    }

    /**
     * Computes the k-order linear regression of a series of points using the method of
     * least squares. More precisely, this uses matrix computations, which is slower than
     * the direct methods which should be preferred for order 1 and 2.
     *
     * @param series the series of points
     * @param k      the order of the polynomial
     * @return the polynomial coefficients
     */
    public static double[] kOrderRegression(Point2D[] series, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Order must be >= 0");
        }
        double[][] m = new double[series.length][];
        double[][] yy = new double[series.length][];
        for (int i = 0; i < series.length; i++) {
            Point2D point = series[i];
            double x = point.x();
            double y = point.y();
            double[] mm = new double[k + 1];
            yy[i] = new double[]{y};
            for (int j = 0; j <= k; j++) {
                mm[j] = Math.pow(x, j);
            }
            m[i] = mm;
        }

        var mm = DoubleMatrix.of(m);
        var mmT = mm.transpose();
        double[][] result = mmT.mul(mm).inverse().mul(mmT).mul(DoubleMatrix.of(yy)).asArray();
        double[] res = new double[k + 1];
        if (result.length == k + 1) {
            for (int i = 0; i <= k; i++) {
                res[k - i] = result[i][0];
            }
        }
        return res;
    }

    /**
     * Primitive-array overload of {@link #kOrderRegression(Point2D[], int)} that avoids
     * the {@code Point2D[]} indirection and the heavyweight matrix machinery used by the
     * other overload. The normal equations form a Hankel-style {@code (k+1)×(k+1)} system
     * whose entries are sums of {@code x^q}; we accumulate those sums in one pass and
     * solve the small dense system in place via Gaussian elimination with partial pivoting.
     * <p>For a singular system, returns an array of zeros — matching the existing overload's
     * behaviour for inputs the matrix path can't invert.
     *
     * @param xs the x coordinates of the series
     * @param ys the y coordinates of the series (must have the same length as {@code xs})
     * @param k  the polynomial order
     * @return the polynomial coefficients in descending power order
     */
    public static double[] kOrderRegression(double[] xs, double[] ys, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Order must be >= 0");
        }
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys must have the same length");
        }
        int n = xs.length;
        int p = k + 1;
        // sumXq[q] = sum of x^q over all points, for q = 0..2k
        // sumXqY[q] = sum of (x^q * y) over all points, for q = 0..k
        double[] sumXq = new double[2 * k + 1];
        double[] sumXqY = new double[p];
        for (int i = 0; i < n; i++) {
            double x = xs[i];
            double y = ys[i];
            double xq = 1.0;
            sumXq[0] += xq;
            sumXqY[0] += y;
            for (int q = 1; q < 2 * k + 1; q++) {
                xq *= x;
                sumXq[q] += xq;
                if (q <= k) {
                    sumXqY[q] += xq * y;
                }
            }
        }
        // Augmented matrix [M^T M | M^T y], shape (p) x (p+1).
        double[][] aug = new double[p][p + 1];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                aug[i][j] = sumXq[i + j];
            }
            aug[i][p] = sumXqY[i];
        }
        // Forward elimination with partial pivoting.
        for (int col = 0; col < p; col++) {
            int pivot = col;
            double pivotMag = Math.abs(aug[col][col]);
            for (int r = col + 1; r < p; r++) {
                double mag = Math.abs(aug[r][col]);
                if (mag > pivotMag) {
                    pivot = r;
                    pivotMag = mag;
                }
            }
            if (pivotMag == 0) {
                return new double[p];
            }
            if (pivot != col) {
                double[] tmp = aug[col];
                aug[col] = aug[pivot];
                aug[pivot] = tmp;
            }
            double inv = 1.0 / aug[col][col];
            for (int r = col + 1; r < p; r++) {
                double factor = aug[r][col] * inv;
                if (factor != 0) {
                    for (int c = col; c <= p; c++) {
                        aug[r][c] -= factor * aug[col][c];
                    }
                }
            }
        }
        // Back-substitution; coeff[i] is the coefficient of x^i (ascending power).
        double[] coeff = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double sum = aug[i][p];
            for (int j = i + 1; j < p; j++) {
                sum -= aug[i][j] * coeff[j];
            }
            coeff[i] = sum / aug[i][i];
        }
        // Re-order to descending power, matching the existing convention.
        double[] res = new double[p];
        for (int i = 0; i <= k; i++) {
            res[k - i] = coeff[i];
        }
        return res;
    }

    /**
     * Returns a polynomial function from the given coefficients.
     * The coefficients are ordered by power descending, i.e. the
     * first coefficient is the coefficient of the highest power.
     *
     * @param coefficients the coefficients
     * @return the polynomial function
     */
    public static DoubleUnaryOperator asPolynomial(double[] coefficients) {
        return x -> {
            double result = 0;
            for (int i = 0; i < coefficients.length; i++) {
                result += coefficients[i] * Math.pow(x, coefficients.length - i - 1d);
            }
            return result;
        };
    }

}
