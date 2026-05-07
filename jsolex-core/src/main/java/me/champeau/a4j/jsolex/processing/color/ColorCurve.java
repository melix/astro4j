/*
 * Copyright 2023-2026 the original author or authors.
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

/**
 * A color curve describing how a monochrome pixel value is mapped to RGB.
 * Each instance precomputes the three polynomials (red, green, blue) once
 * in the constructor; per-pixel application reads them as plain final-field
 * loads instead of going through a global cache. This avoids the per-call
 * {@code IntPair} allocation that the cache lookup would otherwise produce.
 */
public final class ColorCurve {
    private static final Map<IntPair, DoubleUnaryOperator> POLYNOMIALS_CACHE = new ConcurrentHashMap<>();

    public static final int MAX = 65535;

    private final String ray;
    private final int rIn;
    private final int rOut;
    private final int gIn;
    private final int gOut;
    private final int bIn;
    private final int bOut;
    private final DoubleUnaryOperator rPoly;
    private final DoubleUnaryOperator gPoly;
    private final DoubleUnaryOperator bPoly;

    public ColorCurve(String ray,
                      int rIn, int rOut,
                      int gIn, int gOut,
                      int bIn, int bOut) {
        this.ray = ray;
        this.rIn = rIn;
        this.rOut = rOut;
        this.gIn = gIn;
        this.gOut = gOut;
        this.bIn = bIn;
        this.bOut = bOut;
        this.rPoly = cachedPolynomial(rIn << 8, rOut << 8);
        this.gPoly = cachedPolynomial(gIn << 8, gOut << 8);
        this.bPoly = cachedPolynomial(bIn << 8, bOut << 8);
    }

    public String ray() {
        return ray;
    }

    public int rIn() {
        return rIn;
    }

    public int rOut() {
        return rOut;
    }

    public int gIn() {
        return gIn;
    }

    public int gOut() {
        return gOut;
    }

    public int bIn() {
        return bIn;
    }

    public int bOut() {
        return bOut;
    }

    public DoubleUnaryOperator rPoly() {
        return rPoly;
    }

    public DoubleUnaryOperator gPoly() {
        return gPoly;
    }

    public DoubleUnaryOperator bPoly() {
        return bPoly;
    }

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
        return new DoubleTriplet(
                apply(grey, rPoly),
                apply(grey, gPoly),
                apply(grey, bPoly)
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ColorCurve other)) {
            return false;
        }
        return rIn == other.rIn
                && rOut == other.rOut
                && gIn == other.gIn
                && gOut == other.gOut
                && bIn == other.bIn
                && bOut == other.bOut
                && Objects.equals(ray, other.ray);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ray, rIn, rOut, gIn, gOut, bIn, bOut);
    }

    @Override
    public String toString() {
        return "ColorCurve[ray=" + ray
                + ", rIn=" + rIn + ", rOut=" + rOut
                + ", gIn=" + gIn + ", gOut=" + gOut
                + ", bIn=" + bIn + ", bOut=" + bOut + ']';
    }
}
