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
     * @param series the series of points
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
     * @param k the order of the polynomial
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
     * Returns a polynomial function from the given coefficients.
     * The coefficients are ordered by power descending, i.e. the
     * first coefficient is the coefficient of the highest power.
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
