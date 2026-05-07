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
package me.champeau.a4j.math

import me.champeau.a4j.math.regression.LinearRegression
import me.champeau.a4j.math.tuples.DoubleTriplet
import spock.lang.Specification

import static java.lang.Math.abs

class LinearRegressionTest extends Specification {
    private static final double EPSILON = 0.0001d;

    def "test 1st order regression"() {
        given:
        def series = createSeries(points)

        when:
        def reg = LinearRegression.firstOrderRegression(series)
        def (a, b) = [reg.a(), reg.b()]
        series.each { Point2D p ->
            def estimatedY = a * p.x() + b
            println("Point ${p} has estimate y ${estimatedY} (expected ${p.y()}), error ${abs(p.y() - estimatedY)}")
        }

        then:
        assertEquals(a, expectedA)
        assertEquals(b, expectedB)

        where:
        points                                   | expectedA | expectedB
        [1.0, 1.0, 2.0, 2.0, 3.0, 3.0]           | 1.0       | 0.0
        [1.0, 1.1, 2.0, 2.1, 3.1, 3.2]           | 1.0       | 0.1
        [1.5, 2.2, 3.0, 3.4, 4.5, 4.5, 5.0, 5.2] | 0.826667  | 0.9316667
    }

    def "test 2d order regression"() {
        given:
        def series = createSeries(points)


        DoubleTriplet reg = LinearRegression.secondOrderRegression(series)
        double[] generic = LinearRegression.kOrderRegression(series, 2)
        DoubleTriplet genericReg = new DoubleTriplet(generic[0], generic[1], generic[2])

        when:
        def (a, b, c) = [reg.a(), reg.b(), reg.c()]
        series.each { Point2D p ->
            def estimatedY = a * p.x() + b
            println("Point ${p} has estimate y ${estimatedY} (expected ${p.y()}), error ${abs(p.y() - estimatedY)}")
        }

        then:
        assertEquals(reg.a(), genericReg.a())
        assertEquals(reg.b(), genericReg.b())
        assertEquals(reg.c(), genericReg.c())
        assertEquals(a, expectedA)
        assertEquals(b, expectedB)
        assertEquals(c, expectedC)

        where:
        points                                     | expectedA | expectedB | expectedC
        [0.0, 1.0, 1.0, 6.0, 2.0, 17.0, 3.0, 34.0] | 3.0       | 2.0       | 1.0
        [0.0, 3.0, 1.0, 4.0, 2.0, 3.0, 3.0, 0.0]   | -1.0      | 2.0       | 3.0
        [0.0, -2.0, 1.0, 0.5, 2.0, 0.0, 3.0, -3.5] | -1.5      | 4.0       | -2.0
        [0.1, -2.1, 1.0, 0.6, 2.1, 0.1, 3.1, -3.6] | -1.6304   | 4.70323   | -2.53146
    }

    def "primitive kOrderRegression matches Point2D variant — #scenario, k=#k"() {
        given:
        def series = createSeries(points as double[])
        def xs = new double[series.length]
        def ys = new double[series.length]
        for (int i = 0; i < series.length; i++) {
            xs[i] = series[i].x()
            ys[i] = series[i].y()
        }

        when:
        double[] viaPoints = LinearRegression.kOrderRegression(series, k)
        double[] viaPrimitive = LinearRegression.kOrderRegression(xs, ys, k)

        then:
        viaPoints.length == viaPrimitive.length
        viaPoints.length == k + 1
        for (int i = 0; i < viaPoints.length; i++) {
            assertEquals(viaPrimitive[i], viaPoints[i])
        }

        where:
        scenario           | k | points
        "linear, exact"    | 1 | [0.0, 1.0, 1.0, 3.0, 2.0, 5.0, 3.0, 7.0]
        "linear, noisy"    | 1 | [0.0, 1.1, 1.0, 2.9, 2.0, 5.05, 3.0, 6.95, 4.0, 9.1]
        "quadratic, exact" | 2 | [0.0, 1.0, 1.0, 6.0, 2.0, 17.0, 3.0, 34.0]
        "quadratic, noisy" | 2 | [0.1, -2.1, 1.0, 0.6, 2.1, 0.1, 3.1, -3.6]
        "cubic, exact"     | 3 | [0.0, -5.0, 1.0, -5.0, 2.0, 1.0, 3.0, 25.0, 4.0, 79.0, 5.0, 175.0, 6.0, 325.0, 7.0, 541.0]
        "negative x"       | 2 | [-3.0, 9.0, -1.0, 1.0, 1.0, 1.0, 3.0, 9.0]
    }

    def "primitive kOrderRegression matches Point2D variant on column-index data (k=3, 200 points)"() {
        // Mimics PhenomenaDetector.computeColumnModels: xs are column indices, ys are smoothly varying.
        given:
        int n = 200
        def xs = new double[n]
        def ys = new double[n]
        def series = new Point2D[n]
        def rng = new Random(0xC0FFEE as long)
        for (int i = 0; i < n; i++) {
            double x = i
            double y = 0.0001 * x * x * x - 0.05 * x * x + 2 * x + 100 + rng.nextGaussian() * 5
            xs[i] = x
            ys[i] = y
            series[i] = new Point2D(x, y)
        }

        when:
        double[] viaPoints = LinearRegression.kOrderRegression(series, 3)
        double[] viaPrimitive = LinearRegression.kOrderRegression(xs, ys, 3)

        then:
        viaPoints.length == 4
        viaPrimitive.length == 4
        // Both implementations solve the same normal equations; expect coefficients to agree
        // to several significant figures even with the worse conditioning of column-index x ranges.
        for (int i = 0; i < 4; i++) {
            // Relative tolerance: large coefficient magnitudes call for relative comparison.
            def scale = Math.max(Math.abs(viaPoints[i]), 1.0d)
            assert Math.abs(viaPrimitive[i] - viaPoints[i]) / scale < 1e-9
        }
    }

    def "primitive kOrderRegression recovers cubic coefficients exactly"() {
        given:
        // y = 2 x^3 - 3 x^2 + x - 5, sampled at x = 0..7
        def xs = (0..7).collect { it as double } as double[]
        def ys = xs.collect { x -> 2 * x * x * x - 3 * x * x + x - 5 } as double[]

        when:
        double[] coeffs = LinearRegression.kOrderRegression(xs, ys, 3)

        then:
        coeffs.length == 4
        assertEquals(coeffs[0], 2.0)
        assertEquals(coeffs[1], -3.0)
        assertEquals(coeffs[2], 1.0)
        assertEquals(coeffs[3], -5.0)
    }

    def "primitive kOrderRegression returns zeros on a singular system"() {
        given:
        // All x identical → design matrix singular for any k>=1.
        def xs = [3.0, 3.0, 3.0, 3.0] as double[]
        def ys = [1.0, 2.0, 3.0, 4.0] as double[]

        when:
        double[] coeffs = LinearRegression.kOrderRegression(xs, ys, 3)

        then:
        coeffs.length == 4
        coeffs.every { it == 0.0d }
    }

    static void assertEquals(double actual, double expected) {
        assert abs(actual - expected) < EPSILON
    }

    private static Point2D[] createSeries(List<BigDecimal> list) {
        return createSeries(list.stream().mapToDouble(BigDecimal::doubleValue).toArray())
    }

    private static Point2D[] createSeries(double[] array) {
        assert array.length % 2 == 0
        def series = []
        for (int i = 0; i < array.length; i += 2) {
            series << new Point2D(array[i], array[i + 1])
        }
        return series.toArray(new Point2D[0])
    }
}
