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
package me.champeau.a4j.math.regression

import spock.lang.Specification

class Polynomial2DTest extends Specification {

    def "term count"() {
        expect:
        Polynomial2D.termCount(degree) == count

        where:
        degree | count
        1      | 3
        2      | 6
        3      | 10
    }

    def "basis terms are ordered by ascending total degree"() {
        given:
        double[] out = new double[6]

        when:
        Polynomial2D.terms(2d, 3d, 2, out)

        then: "[1, dx, dy, dx^2, dx*dy, dy^2]"
        (out as List) == [1d, 2d, 3d, 4d, 6d, 9d]
    }

    def "evaluate reconstructs the surface value"() {
        given:
        double[] coeffs = [1, 2, -3, 4, 5, -6]
        double[] scratch = new double[6]

        expect: "1 + 2*2 - 3*3 + 4*4 + 5*6 - 6*9"
        Math.abs(Polynomial2D.evaluate(coeffs, 2d, 3d, 2, scratch) - (-12)) < 1e-9
    }

    def "fitting recovers a known degree-2 surface"() {
        given:
        double[] truth = [1, 2, -3, 4, 5, -6]
        double[] b = new double[6]
        def fit = new LeastSquares(6)
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                Polynomial2D.terms(i, j, 2, b)
                double z = 0
                for (int k = 0; k < 6; k++) {
                    z += truth[k] * b[k]
                }
                fit.add(b, z)
            }
        }

        when:
        double[] coeffs = fit.solve()

        then:
        coeffs != null
        (0..<6).every { Math.abs(coeffs[it] - truth[it]) < 1e-6 }
    }
}
