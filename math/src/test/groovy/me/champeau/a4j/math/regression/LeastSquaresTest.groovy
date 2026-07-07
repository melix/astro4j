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

class LeastSquaresTest extends Specification {

    def "recovers coefficients of an exact linear model"() {
        given: "y = 3 + 2*x with basis [1, x]"
        def fit = new LeastSquares(2)
        [0d, 1d, 2d, 3d, 4d].each { x -> fit.add([1d, x] as double[], 3 + 2 * x) }

        when:
        double[] c = fit.solve()

        then:
        c != null
        Math.abs(c[0] - 3) < 1e-9
        Math.abs(c[1] - 2) < 1e-9
    }

    def "honours sample weights"() {
        given:
        def fit = new LeastSquares(2)
        fit.add([1d, 0d] as double[], 1d, 2d)
        fit.add([1d, 1d] as double[], 3d, 0.5d)
        fit.add([1d, 2d] as double[], 5d, 1d)

        when:
        double[] c = fit.solve()

        then:
        c != null
        Math.abs(c[0] - 1) < 1e-9
        Math.abs(c[1] - 2) < 1e-9
    }

    def "returns null for a singular (under-determined) system"() {
        given:
        def fit = new LeastSquares(2)
        fit.add([1d, 1d] as double[], 4d)

        expect:
        fit.solve() == null
    }
}
