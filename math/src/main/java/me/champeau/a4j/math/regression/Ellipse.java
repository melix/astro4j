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
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.DoubleSextuplet;

import java.util.Optional;

/**
 * Represents an ellipse, as the result of ellipse regression.
 */
public class Ellipse {
    private final DoubleSextuplet cart;

    private Ellipse(DoubleSextuplet cartesian) {
        this.cart = cartesian;
    }

    public static Ellipse ofCartesian(DoubleSextuplet coefficients) {
        return new Ellipse(coefficients);
    }

    public Ellipse scale(double scaleFactor) {
        return new Ellipse(new DoubleSextuplet(
                cart.a(),
                cart.b(),
                cart.c(),
                cart.d(),
                cart.e(),
                cart.f() / scaleFactor
        ));
    }

    public DoubleSextuplet getCartesianCoefficients() {
        return cart;
    }

    public boolean isAlmostCircle(double epsilon) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        return !(Math.abs(a - c) < epsilon && Math.abs(b) < epsilon);
    }

    /**
     * Computes the rotation angle of the ellipse
     * Formulas from https://mathworld.wolfram.com/Ellipse.html
     */
    public double rotationAngle() {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        if (b == 0) {
            if (a < c) {
                return 0;
            }
            return Math.PI / 2;
        }
        if (a < c) {
            return Math.atan(b / (a - c)) / 2;
        }
        return Math.PI / 2 + Math.atan(b / (a - c)) / 2;
    }

    public double discriminant() {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        return b * b - 4 * a * c;
    }

    public DoublePair center() {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var discri = discriminant();
        var cx = 2 * c * d - b * e;
        var cy = 2 * a * e - b * d;
        return new DoublePair(cx / discri, cy / discri);
    }

    // Formulas from https://mathworld.wolfram.com/Ellipse.html
    public DoublePair semiAxis() {
        // we're using the same parameter names as in the page above in order
        // to doublecheck results more easily
        var a = cart.a();
        var b = cart.b() / 2;
        var c = cart.c();
        var d = cart.d() / 2;
        var f = cart.e() / 2;
        var g = cart.f();
        var disc = b * b - a * c;
        var num = 2 * (a * f * f + c * d * d + g * b * b - 2 * b * d * f - a * c * g);
        var z = Math.sqrt((a - c) * (a - c) + 4 * b * b);
        var aPrime = Math.sqrt(num / (disc * (z - (a + c))));
        var bPrime = Math.sqrt(num / (disc * (-z - (a + c))));
        return new DoublePair(aPrime, bPrime);
    }

    public double xyRatio() {
        var sa = semiAxis();
        return sa.b() / sa.a();
    }

    public Point2D toCartesian(double angle) {
        var sa = semiAxis();
        var rotation = rotationAngle();
        var a = sa.a();
        var b = sa.b();
        var center = center();
        var cx = center.a();
        var cy = center.b();
        var cosA = Math.cos(angle);
        var sinA = Math.sin(angle);
        var cosR = Math.cos(rotation);
        var sinR = Math.sin(rotation);
        return new Point2D(
                cx + a * cosA * cosR - b * sinA * sinR,
                cy + a * cosA * sinR + b * sinA * cosR
        );
    }

    public Ellipse translatedBy(double dx, double dy) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();

        var newD = d - 2 * a * dx - b * dy;
        var newE = e - 2 * c * dy - b * dx;
        var newF = f + a * dx * dx + b * dx * dy + c * dy * dy - d * dx - e * dy;

        return Ellipse.ofCartesian(new DoubleSextuplet(a, b, c, newD, newE, newF));
    }


    public Ellipse centeredAt(int cx, int cy) {
        var center = center();
        var dx = cx - center.a();
        var dy = cy - center.b();
        return translatedBy(dx, dy);
    }


    public Point2D[] findVertices() {
        var theta = rotationAngle();
        var ortho = theta - Math.PI / 2;
        var center = center();
        var cx = center.a();
        var cy = center.b();
        var semiAxis = semiAxis();
        var s1 = semiAxis.a();
        var s2 = semiAxis.b();
        var cosTheta = Math.cos(theta);
        var sinTheta = Math.sin(theta);
        var cosOrtho = Math.cos(ortho);
        var sinOrtho = Math.sin(ortho);
        return new Point2D[]{
                new Point2D(cx - s1 * cosTheta, cy - s1 * sinTheta),
                new Point2D(cx + s1 * cosTheta, cy + s1 * sinTheta),
                new Point2D(cx - s2 * cosOrtho, cy - s2 * sinOrtho),
                new Point2D(cx + s2 * cosOrtho, cy + s2 * sinOrtho)
        };
    }

    public Optional<DoublePair> findY(double x) {
        var a = cart.c();
        var b = cart.b() * x + cart.e();
        var c = cart.a() * x * x + cart.d() * x + cart.f();
        var disc = b * b - 4 * a * c;
        if (disc >= 0 && 2 * a != 0) {
            var y1 = (-b + Math.sqrt(disc)) / 2 * a;
            var y2 = (-b - Math.sqrt(disc)) / 2 * a;
            return Optional.of(new DoublePair(y1, y2));
        }
        return Optional.empty();
    }

    public boolean isWithin(Point2D point) {
        return isWithin(point.x(), point.y());
    }

    public boolean isWithin(double x, double y) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();
        double value = a * x * x + b * x * y + c * y * y + d * x + e * y + f;
        return a >= 0 && value <= 0 || a <= 0 && value >= 0;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Ellipse parameters C(x,y) = ax² + bxy + cy² + dx + ey + f = 0\n");
        sb.append("   - a = ").append(format(cart.a())).append("\n");
        sb.append("   - b = ").append(format(cart.b())).append("\n");
        sb.append("   - c = ").append(format(cart.c())).append("\n");
        sb.append("   - d = ").append(format(cart.d())).append("\n");
        sb.append("   - e = ").append(format(cart.e())).append("\n");
        sb.append("   - f = ").append(format(cart.f())).append("\n");
        var center = center();
        sb.append("Center = (").append(format(center.a())).append(",").append(format(center.b())).append(")\n");
        return sb.toString();
    }

    private static String format(double d) {
        return String.format("%4.2f", d);
    }
}
