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
     * https://math.stackexchange.com/questions/280937/finding-the-angle-of-rotation-of-an-ellipse-from-its-general-equation-and-the-ot
     */
    public double tiltAngle() {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        return Math.atan(b / (a - c)) / 2;
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
        var discri = discriminant();
        var a = cart.a();
        var b = cart.b() / 2;
        var c = cart.c();
        var d = cart.d() / 2;
        var e = cart.e() / 2;
        var f = cart.f();
        var num = 2 * (a * e * e + c * d * d + e * b * b - 2 * b * d * e - a * c * f);
        var z = Math.sqrt((a - c) * (a - c) + 4 * b * b);
        var aPrime = Math.sqrt(num / (discri * (z - (a + c))));
        var bPrime = Math.sqrt(num / (discri * (-z - (a + c))));
        if (aPrime > bPrime) {
            return new DoublePair(bPrime, aPrime);
        }
        return new DoublePair(aPrime, bPrime);
    }

    public double xyRatio() {
        var sa = semiAxis();
        if (cart.b() < 0) {
            return sa.a() / sa.b();
        }
        return sa.b() / sa.a();
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
