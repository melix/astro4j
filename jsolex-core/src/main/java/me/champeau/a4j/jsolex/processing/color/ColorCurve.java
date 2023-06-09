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
package me.champeau.a4j.jsolex.processing.color;

import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

public record ColorCurve(
        String ray,
        int rIn, int rOut,
        int gIn, int gOut,
        int bIn, int bOut
) {

    private static final Map<IntPair, DoubleUnaryOperator> POLYNOMIALS_CACHE = new ConcurrentHashMap<>();

    public static final int MAX = 65535;

    private static DoubleTriplet polynomialOf(int in, int out) {
        return LinearRegression.secondOrderRegression(
                new Point2D[]{
                        new Point2D(0, 0),
                        new Point2D(in, out),
                        new Point2D(MAX, MAX)
                }
        );
    }

    public static DoubleUnaryOperator cachedPolynomial(int in, int out) {
        return POLYNOMIALS_CACHE.computeIfAbsent(new IntPair(in, out), k -> polynomialOf(in, out).asPolynomial());
    }

    public DoubleTriplet toRGB(double grey) {
        if (grey < 0 || grey > MAX) {
            throw new IllegalArgumentException("Invalid input " + grey + " : input values must be normalized in range [0..65535]");
        }
        var rPoly = cachedPolynomial(rIn << 8, rOut << 8);
        var gPoly = cachedPolynomial(gIn << 8, gOut << 8);
        var bPoly = cachedPolynomial(bIn << 8, bOut << 8);
        double v = grey;
        return new DoubleTriplet(
                apply(v, rPoly),
                apply(v, gPoly),
                apply(v, bPoly)
        );
    }

    private static int apply(double value, DoubleUnaryOperator f) {
        double x = f.applyAsDouble(value);
        if (x < 0) {
            return 0;
        }
        if (x > MAX) {
            return MAX;
        }
        return (int) Math.round(x);
    }
}
