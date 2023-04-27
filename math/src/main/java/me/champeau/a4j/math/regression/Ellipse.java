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
        return a > 0 && value <= 0 || a < 0 && value >= 0;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Ellipse parameters C(x,y) = ax² + bxy + cy² + ey + z = 0\n");
        sb.append("   - a = ").append(format(cart.a())).append("\n");
        sb.append("   - b = ").append(format(cart.b())).append("\n");
        sb.append("   - c = ").append(format(cart.c())).append("\n");
        sb.append("   - d = ").append(format(cart.d())).append("\n");
        sb.append("   - e = ").append(format(cart.e())).append("\n");
        sb.append("   - f = ").append(format(cart.f())).append("\n");
        return sb.toString();
    }

    private static String format(double d) {
        return String.format("%4.2f", d);
    }
}
