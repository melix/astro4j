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
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
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

    public DoubleSextuplet getCartesianCoefficients() {
        return cart;
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

    public Ellipse centeredAt(int cx, int cy) {
        var center = center();
        var dx = cx - center.a();
        var dy = cy - center.b();
        return translate(dx, dy);
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

    public Optional<DoublePair> findX(double y) {
        var a = cart.a();
        var b = cart.b() * y + cart.d();
        var c = cart.c() * y * y + cart.e() * y + cart.f();
        var disc = b * b - 4 * a * c;
        if (disc >= 0 && a != 0) {
            var sqrt = Math.sqrt(disc);
            var x1 = (-b + sqrt) / (2 * a);
            var x2 = (-b - sqrt) / (2 * a);
            return Optional.of(new DoublePair(x1, x2));
        }
        if (a == 0 && b != 0) {
            var x = -c / b;
            return Optional.of(new DoublePair(x, Double.NaN));
        }
        return Optional.empty();
    }

    public Optional<DoublePair> findY(double x) {
        var a = cart.c();
        var b = cart.b() * x + cart.e();
        var c = cart.a() * x * x + cart.d() * x + cart.f();
        var disc = b * b - 4 * a * c;
        if (disc >= 0 && a != 0) {
            var sqrt = Math.sqrt(disc);
            var y1 = (-b + sqrt) / (2 * a);
            var y2 = (-b - sqrt) / (2 * a);
            return Optional.of(new DoublePair(y1, y2));
        }
        if (a == 0 && b != 0) {
            var y = -c / b;
            return Optional.of(new DoublePair(y, Double.NaN));
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

    /**
     * Computes the parameters of this ellipse translated by vector (u,v)
     *
     * @param u the translation on the X axis
     * @param v the translation on the Y axis
     * @return the new ellipse
     */
    public Ellipse translate(double u, double v) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();
        return new Ellipse(new DoubleSextuplet(
                a,
                b,
                c,
                d - 2 * a * u - b * v,
                e - 2 * c * v - b * u,
                a * u * u + b * u * v + c * v * v - d * u - e * v + f
        ));
    }

    public Ellipse rescale(double scaleX, double scaleY) {
        var cx = center().a();
        var cy = center().b();
        // translate(-cx, -cy);
        var atOrigin = translate(-cx, -cy);
        var a = atOrigin.cart.a();
        var b = atOrigin.cart.b();
        var c = atOrigin.cart.c();
        var d = atOrigin.cart.d();
        var e = atOrigin.cart.e();
        var f = atOrigin.cart.f();
        var scaleXSq = scaleX * scaleX;
        var scaleYSq = scaleY * scaleY;
        var scaleXY = scaleX * scaleY;
        var rescaled = new Ellipse(new DoubleSextuplet(
                a * scaleYSq,
                b * scaleXY,
                c * scaleXSq,
                d * scaleX,
                e * scaleY,
                f * scaleXSq * scaleYSq
        ));
        return rescaled.translate(cx, cy);
    }

    /**
     * Rotates an ellipse using the origin (0,0) as the rotation center.
     * @param theta the rotation angle
     * @return the rotated ellipse
     */
    public Ellipse rotate(double theta) {
        // In SageMath, use:
        // x,y,a,b,c,d,e,f,u,v,t=var("x,y,a,b,c,d,e,f,u,v,t")
        // expand(a*(x*cos(u)-y*sin(u))^2+b*(x*cos(u)-y*sin(u))*(x*sin(u)+y*cos(u))+c*(x*sin(u)+y*cos(u))^2+d*(x*cos(u)-y*sin(u))+e*(x*sin(u)+y*cos(u))+f).maxima_methods().collectterms(x,y)
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        return new Ellipse(new DoubleSextuplet(
                a * cos * cos + b * cos * sin + c * sin * sin,
                b * cos * cos - 2 * a * cos * sin + 2 * c * cos * sin - b * sin * sin,
                c * cos * cos - b * cos * sin + a * sin * sin,
                d * cos + e * sin,
                e * cos - d * sin,
                f
        ));
    }

    /**
     * Rotates an ellipse using the supplied rotation center.
     * @param theta the rotation angle
     * @return the rotated ellipse
     */
    public Ellipse rotate(double theta, Point2D rotationCenter) {
        var cx = rotationCenter.x();
        var cy = rotationCenter.y();
        var e1 = translate(-cx, -cy);
        var e2 = e1.rotate(theta);
        return e2.translate(cx, cy);
    }

    public double eccentricity() {
        var sa = semiAxis();
        var a = sa.a();
        var b = sa.b();
        if (a < b) {
            var temp = a;
            a = b;
            b = temp;
        }
        return Math.sqrt(1 - (b * b) / (a * a));
    }

    public DoubleQuadruplet boundingBox() {
        var center = center();
        var x0 = center.a();
        var y0 = center.b();
        var sa = semiAxis();
        var a = sa.a();
        var b = sa.b();
        var theta = rotationAngle();

        var cosTheta = Math.cos(theta);
        var sinTheta = Math.sin(theta);

        var w = Math.sqrt(a * a * cosTheta * cosTheta + b * b * sinTheta * sinTheta);
        var h = Math.sqrt(a * a * sinTheta * sinTheta + b * b * cosTheta * cosTheta);

        return new DoubleQuadruplet(
            x0 - w, x0 + w,
            y0 - h, y0 + h
        );
    }

    public Ellipse rotate(double angle, int srcWidth, int srcHeight, int newWidth, int newHeight) {
        var sx = srcWidth / 2d;
        var sy = srcHeight / 2d;
        var rotated = rotate(-angle, new Point2D(sx, sy));
        sx = (newWidth - srcWidth) / 2d;
        sy = (newHeight - srcHeight) / 2d;
        if (sx != 0 || sy != 0) {
            rotated = rotated.translate(sx, sy);
        }
        return rotated;
    }

    public Ellipse vflip(double height) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();
        return new Ellipse(new DoubleSextuplet(
                a,
                -b,
                c,
                b * height + d,
                -2 * c * height - e,
                c * height * height + e * height + f
        ));
    }

    public Ellipse hflip(double width) {
        var a = cart.a();
        var b = cart.b();
        var c = cart.c();
        var d = cart.d();
        var e = cart.e();
        var f = cart.f();
        return new Ellipse(new DoubleSextuplet(
                a,
                -b,
                c,
                -2 * a * width - d,
                b * width + e,
                a * width * width + d * width + f
        ));
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
