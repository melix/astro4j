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

import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;

import java.util.Arrays;

/**
 * Utility class for geometry-related operations on images
 */
public final class GeometryUtils {
    private static final double KERNEL_RADIUS = 2;

    private GeometryUtils() {
        // Utility class
    }

    /**
     * Applies a geometry correction to an image. The transformation must have been computed for
     * the height of that image, since the horizontal shift it carries depends on it.
     *
     * @param image the input image
     * @param transform the transformation to apply
     * @param blackPoint black point value for the transformation
     * @return the geometry-corrected image
     */
    public static ImageWrapper32 applyGeometryCorrection(ImageWrapper32 image,
                                                         GeometryTransform transform,
                                                         float blackPoint) {
        var height = transform.height();
        if (image.height() != height) {
            throw new IllegalArgumentException("Geometry transform was computed for a height of " + height + " but the image is " + image.height() + " pixels high");
        }
        var shear = transform.shear();
        var shift = transform.shift();
        var sx = transform.sx();
        var sy = transform.sy();

        var width = image.width();
        var buffer = image.data();
        var extendedWidth = width + (int) Math.ceil(Math.abs(height * shear));

        var newWidth = (int) (extendedWidth * sx);
        var newHeight = (int) (height * sy);
        var centerX = extendedWidth / 2;
        var centerY = height / 2;
        var newCenterX = newWidth / 2;
        var newCenterY = newHeight / 2;
        var newBuffer = new float[newHeight][newWidth];
        // on a downscale one output pixel covers several input ones: widen the kernel
        // to the source footprint so the redundant samples are averaged instead of dropped
        var xSupport = sx < 1 ? 1 / sx : 1;
        var ySupport = sy < 1 ? 1 / sy : 1;

        for (int y = 0; y < newHeight; y++) {
            var v = (y - newCenterY) / sy + centerY;
            var targetRow = newBuffer[y];
            if (v < 0 || v > height - 1) {
                Arrays.fill(targetRow, blackPoint);
                continue;
            }
            var sheared = shift - v * shear;
            for (int x = 0; x < newWidth; x++) {
                var u = (x - newCenterX) / sx + centerX;
                targetRow[x] = u < 0 || u > extendedWidth - 1
                        ? blackPoint
                        : sampleCatmullRom(buffer, u + sheared, v, width, height, xSupport, ySupport);
            }
        }

        return new ImageWrapper32(newWidth, newHeight, newBuffer, image.metadata());
    }

    /**
     * Samples an image at fractional coordinates using a separable Catmull-Rom
     * cubic kernel. Coordinates outside the image are handled by edge replication.
     * A support greater than one widens the kernel along that axis, which is the
     * anti-aliasing footprint required when the axis is being downscaled.
     *
     * @param data the source pixels
     * @param x the fractional source column
     * @param y the fractional source row
     * @param width the image width
     * @param height the image height
     * @param xSupport the horizontal kernel widening factor
     * @param ySupport the vertical kernel widening factor
     * @return the interpolated value
     */
    private static float sampleCatmullRom(float[][] data, double x, double y, int width, int height, double xSupport, double ySupport) {
        double value;
        if (ySupport == 1) {
            var j = (int) Math.floor(y);
            var t = y - j;
            if (t == 0) {
                value = sampleRow(data[clamp(j, height)], x, width, xSupport);
            } else {
                var weights = CubicWeights.of(t);
                value = weights.apply(
                        sampleRow(data[clamp(j - 1, height)], x, width, xSupport),
                        sampleRow(data[clamp(j, height)], x, width, xSupport),
                        sampleRow(data[clamp(j + 1, height)], x, width, xSupport),
                        sampleRow(data[clamp(j + 2, height)], x, width, xSupport)
                );
            }
        } else {
            double sum = 0;
            double weightSum = 0;
            var from = (int) Math.ceil(y - KERNEL_RADIUS * ySupport);
            var to = (int) Math.floor(y + KERNEL_RADIUS * ySupport);
            for (int j = from; j <= to; j++) {
                var w = catmullRom((j - y) / ySupport);
                if (w != 0) {
                    sum += w * sampleRow(data[clamp(j, height)], x, width, xSupport);
                    weightSum += w;
                }
            }
            value = weightSum == 0 ? 0 : sum / weightSum;
        }
        if (value < 0) {
            return 0;
        }
        if (value > Constants.MAX_PIXEL_VALUE) {
            return Constants.MAX_PIXEL_VALUE;
        }
        return (float) value;
    }

    private static double sampleRow(float[] row, double x, int width, double support) {
        if (support == 1) {
            var i = (int) Math.floor(x);
            var weights = CubicWeights.of(x - i);
            return weights.apply(
                    row[clamp(i - 1, width)],
                    row[clamp(i, width)],
                    row[clamp(i + 1, width)],
                    row[clamp(i + 2, width)]
            );
        }
        double sum = 0;
        double weightSum = 0;
        var from = (int) Math.ceil(x - KERNEL_RADIUS * support);
        var to = (int) Math.floor(x + KERNEL_RADIUS * support);
        for (int i = from; i <= to; i++) {
            var w = catmullRom((i - x) / support);
            if (w != 0) {
                sum += w * row[clamp(i, width)];
                weightSum += w;
            }
        }
        return weightSum == 0 ? 0 : sum / weightSum;
    }

    private static double catmullRom(double t) {
        var abs = Math.abs(t);
        if (abs < 1) {
            return (1.5 * abs - 2.5) * abs * abs + 1;
        }
        if (abs < KERNEL_RADIUS) {
            return ((-0.5 * abs + 2.5) * abs - 4) * abs + 2;
        }
        return 0;
    }

    private static int clamp(int index, int length) {
        if (index < 0) {
            return 0;
        }
        if (index >= length) {
            return length - 1;
        }
        return index;
    }

    private record CubicWeights(double wm1, double w0, double w1, double w2) {
        static CubicWeights of(double t) {
            return new CubicWeights(catmullRom(t + 1), catmullRom(t), catmullRom(1 - t), catmullRom(2 - t));
        }

        double apply(double vm1, double v0, double v1, double v2) {
            return wm1 * vm1 + w0 * v0 + w1 * v1 + w2 * v2;
        }
    }

    /**
     * Computes the corrected ellipse using direct mathematical transformation
     * instead of sampling and regression. Applies the same transformations as
     * the image correction: translation, shear, and scaling.
     *
     * @param ellipse the original ellipse to correct
     * @param transform the transformation applied to the image
     * @return the transformed ellipse
     */
    public static Ellipse computeCorrectedCircle(Ellipse ellipse, GeometryTransform transform) {
        var shear = transform.shear();
        var shift = transform.shift();
        var sx = transform.sx();
        var sy = transform.sy();
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