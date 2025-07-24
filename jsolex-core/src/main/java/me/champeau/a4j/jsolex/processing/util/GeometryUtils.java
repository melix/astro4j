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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;

/**
 * Utility class for geometry-related operations on images
 */
public final class GeometryUtils {
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance();

    private GeometryUtils() {
        // Utility class
    }

    /**
     * Applies geometry correction to an image using the provided ellipse.
     * This is the core geometry correction algorithm that can be used independently.
     *
     * @param image the input image
     * @param ellipse the ellipse to use for correction
     * @param forcedTilt optional forced tilt angle (null to use ellipse rotation)
     * @param xyRatio optional forced X/Y ratio (null to calculate from ellipse)
     * @param blackPoint black point value for the transformation
     * @param disallowDownsampling whether to disallow downsampling
     * @return the geometry-corrected image
     */
    public static ImageWrapper32 applyGeometryCorrection(ImageWrapper32 image,
                                                         Ellipse ellipse,
                                                         Double forcedTilt,
                                                         Double xyRatio,
                                                         float blackPoint,
                                                         boolean disallowDownsampling) {
        var theta = forcedTilt == null ? ellipse.rotationAngle() : forcedTilt;
        var m = Math.tan(-theta);
        var semiAxis = ellipse.semiAxis();
        var a = semiAxis.a();
        var b = semiAxis.b();
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        var shear = (m * cos * a * a + sin * b * b) / (b * b * cos - a * a * m * sin);

        var width = image.width();
        var height = image.height();
        var buffer = image.data();

        var maxDx = height * shear;
        var shift = maxDx < 0 ? maxDx : 0;
        var extendedWidth = width + (int) Math.ceil(Math.abs(maxDx));
        var newBuffer = new float[height][extendedWidth];

        for (int y = 0; y < height; y++) {
            var dx = y * shear;
            for (int x = 0; x < width; x++) {
                var nx = x - shift + dx;
                var x1 = (int) Math.floor(nx);
                var x2 = x1 + 1;
                var factor = nx - x1;
                if (x1 >= 0 && x2 < extendedWidth) {
                    newBuffer[y][x1] += (1 - factor) * buffer[y][x];
                    newBuffer[y][x2] += factor * buffer[y][x];
                }
                // reduce transform artifacts by filling with same border color
                if (x == 0) {
                    for (int k = 0; k < nx; k++) {
                        newBuffer[y][k] = buffer[y][x];
                    }
                } else if (x == width - 1) {
                    for (int k = (int) nx; k < extendedWidth; k++) {
                        newBuffer[y][k] = buffer[y][x];
                    }
                }
            }
        }

        double sx;
        double sy = Math.abs((a * b * Math.sqrt((a * a * m * m + b * b) / (a * a * sin * sin + b * b * cos * cos)) / (b * b * cos - a * a * m * sin)));

        if (xyRatio != null) {
            sy = xyRatio;
        }
        if (sy < 1 || !disallowDownsampling) {
            sx = 1 / sy;
            sy = 1.0d;
        } else {
            sx = 1.0d;
        }

        var rescaled = IMAGE_MATH.rotateAndScale(new Image(extendedWidth, height, newBuffer), 0, blackPoint, sx, sy);

        return ImageWrapper32.fromImage(rescaled, image.metadata());
    }

    /**
     * Computes the corrected ellipse using direct mathematical transformation
     * instead of sampling and regression. Applies the same transformations as
     * the image correction: translation, shear, and scaling.
     *
     * @param ellipse the original ellipse to correct
     * @param shear the shear value
     * @param shift pixel shifting to avoid negative number overflow
     * @param sx    the x correction ratio
     * @param sy    the y correction ratio
     * @return the transformed ellipse
     */
    public static Ellipse computeCorrectedCircle(Ellipse ellipse, double shear, double shift, double sx, double sy) {
        var coeffs = ellipse.getCartesianCoefficients();
        var a = coeffs.a();
        var b = coeffs.b();
        var c = coeffs.c();
        var d = coeffs.d();
        var e = coeffs.e();
        var f = coeffs.f();

        var u = -shift;
        var v = 0.0;
        var d1 = d - 2 * a * u - b * v;
        var e1 = e - 2 * c * v - b * u;
        var f1 = a * u * u + b * u * v + c * v * v - d * u - e * v + f;

        var b2 = b - 2 * a * shear;
        var c2 = c + a * shear * shear - b * shear;
        var e2 = e1 - d1 * shear;

        var sx2 = sx * sx;
        var sy2 = sy * sy;
        var sxsy = sx * sy;
        var a3 = a * sy2;
        var b3 = b2 * sxsy;
        var c3 = c2 * sx2;
        var d3 = d1 * sy2 * sx;
        var e3 = e2 * sx2 * sy;
        var f3 = f1 * sx2 * sy2;

        return Ellipse.ofCartesian(new DoubleSextuplet(a3, b3, c3, d3, e3, f3));
    }
}