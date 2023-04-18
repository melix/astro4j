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
package me.champeau.a4j.math;

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
            sumX += point.x();
            sumY += point.y();
            sumXX += point.x() * point.x();
            sumXY += point.x() * point.y();
        }
        int n = series.length;
        double a = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double b = (sumY - a * sumX) / n;
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
            sumX += point.x();
            sumY += point.y();
            sumXX += point.x() * point.x();
            sumXY += point.x() * point.y();
            sumXXX += point.x() * point.x() * point.x();
            sumXXXX += point.x() * point.x() * point.x() * point.x();
            sumXXY += point.x() * point.x() * point.y();
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
}
