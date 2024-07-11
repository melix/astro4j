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

import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

/**
 * A quadruplet of doubles.
 */
public record DoubleQuadruplet(
        double a,
        double b,
        double c,
        double d
) {
    public DoubleUnaryOperator asPolynomial() {
        return new Operator();
    }

    private class Operator implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double x) {
            return a * x * x * x + b * x * x + c * x + d;
        }

        @Override
        public String toString() {
            return String.format("ax3 + bx2 + cx + d = 0\n   - a = %s\n   - b = %s\n   - c = %s\n   - d = %s", a, b, c, d);
        }
    }

    public String asPolynomialString() {
        return String.format(Locale.US, "{%s,%s,%s,%s}", a, b, c, d);
    }

    public static Optional<DoubleQuadruplet> parsePolynomial(String polynomial) {
        try {
            String[] parts = polynomial.substring(1, polynomial.length() - 1).split("\\s*,\\s*");
            return Optional.of(new DoubleQuadruplet(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
