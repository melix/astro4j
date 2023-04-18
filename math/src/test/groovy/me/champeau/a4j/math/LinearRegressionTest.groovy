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
        when:
        def (a, b, c) = [reg.a(), reg.b(), reg.c()]
        series.each { Point2D p ->
            def estimatedY = a * p.x() + b
            println("Point ${p} has estimate y ${estimatedY} (expected ${p.y()}), error ${abs(p.y() - estimatedY)}")
        }

        then:
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
