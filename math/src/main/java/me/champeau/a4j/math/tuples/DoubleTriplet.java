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
package me.champeau.a4j.math.tuples;

import java.util.function.DoubleUnaryOperator;

/**
 * A double triplet.
 *
 * @param a the first component
 * @param b the 2d component
 * @param c the 3d component
 */
public record DoubleTriplet(
        double a,
        double b,
        double c
) {
    /**
     * Returns a polynomial operator representing ax^2 + bx + c.
     * @return a polynomial operator representing ax^2 + bx + c
     */
    public DoubleUnaryOperator asPolynomial() {
        return new Operator();
    }

    private class Operator implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double x) {
            return a * x * x + b * x + c;
        }

        @Override
        public String toString() {
            return String.format("ax2 + bx + c = 0\n   - a = %s\n   - b = %s\n   - c = %s\n   - d = %s", a, b, c);
        }
    }
}
