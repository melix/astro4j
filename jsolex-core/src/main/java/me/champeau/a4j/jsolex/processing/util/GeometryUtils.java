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
     * the dimensions of that image, since the horizontal shift it carries depends on the height
     * and the output dimensions derive from both.
     *
     * @param image the input image
     * @param transform the transformation to apply
     * @param blackPoint black point value for the transformation
     * @return the geometry-corrected image
     */
    public static ImageWrapper32 applyGeometryCorrection(ImageWrapper32 image,
                                                         GeometryTransform transform,
                                                         float blackPoint) {
        var width = transform.width();
        var height = transform.height();
        if (image.width() != width || image.height() != height) {
            throw new IllegalArgumentException("Geometry transform was computed for an image of " + width + "x" + height + " but the image is " + image.width() + "x" + image.height());
        }
        var shear = transform.shear();
        var shift = transform.shift();
        var sx = transform.sx();
        var sy = transform.sy();
        var offsetX = transform.offsetX();
        var offsetY = transform.offsetY();

        var buffer = image.data();
        var extendedWidth = transform.extendedWidth();
        var newWidth = transform.outputWidth();
        var newHeight = transform.outputHeight();
        var newBuffer = new float[newHeight][newWidth];
        // on a downscale one output pixel covers several input ones: widen the kernel
        // to the source footprint so the redundant samples are averaged instead of dropped
        var xSupport = sx < 1 ? 1 / sx : 1;
        var ySupport = sy < 1 ? 1 / sy : 1;

        for (int y = 0; y < newHeight; y++) {
            var v = (y - offsetY) / sy;
            var targetRow = newBuffer[y];
            if (v < 0 || v > height - 1) {
                Arrays.fill(targetRow, blackPoint);
                continue;
            }
            var sheared = shift - v * shear;
            for (int x = 0; x < newWidth; x++) {
                var u = (x - offsetX) / sx;
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
     * Computes the corrected ellipse analytically instead of by sampling and regression, by
     * applying to the conic the very transformation the warp applies to the pixels.
     *
     * @param ellipse the original ellipse to correct
     * @param transform the transformation applied to the image
     * @return the transformed ellipse
     */
    public static Ellipse computeCorrectedCircle(Ellipse ellipse, GeometryTransform transform) {
        var coeffs = ellipse.getCartesianCoefficients();
        var conic = new double[][]{
                {coeffs.a(), coeffs.b() / 2, coeffs.d() / 2},
                {coeffs.b() / 2, coeffs.c(), coeffs.e() / 2},
                {coeffs.d() / 2, coeffs.e() / 2, coeffs.f()}
        };
        var shear = transform.shear();
        var sx = transform.sx();
        var sy = transform.sy();
        var tx = transform.offsetX() - sx * transform.shift();
        var ty = transform.offsetY();
        // source coordinates as a function of the corrected ones, in homogeneous form
        var inverse = new double[][]{
                {1 / sx, -shear / sy, shear * ty / sy - tx / sx},
                {0, 1 / sy, -ty / sy},
                {0, 0, 1}
        };
        var transformed = multiply(transpose(inverse), multiply(conic, inverse));
        return Ellipse.ofCartesian(new DoubleSextuplet(
                transformed[0][0],
                2 * transformed[0][1],
                transformed[1][1],
                2 * transformed[0][2],
                2 * transformed[1][2],
                transformed[2][2]
        ));
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        var result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += left[i][k] * right[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }

    private static double[][] transpose(double[][] matrix) {
        var result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[i][j] = matrix[j][i];
            }
        }
        return result;
    }
}