/*
 * Copyright 2026 the original author or authors.
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

/**
 * The transformation which turns the elliptical solar disk of a raw scan into a circular one:
 * every row is sheared and shifted, then both axes are scaled.
 * <p>
 * The same transformation has to be applied to the image pixels, to the ellipse stored in the
 * metadata and to any other coordinates which are carried over, so it must be computed once and
 * shared instead of being derived again by every consumer.
 * <p>
 * {@link #transformX} and {@link #transformY} give the position of a source pixel in the
 * corrected image. The warp scales around the image centre rather than around the origin, which
 * adds an offset of {@code newCenter - center * scale} per axis. That offset is zero in real
 * arithmetic and only differs from it by the truncation of both centres to integers, so the two
 * agree to less than a pixel.
 * <p>
 * The transformation is tied to the height it was computed for, because the shift depends on how
 * far the shear displaces the last row.
 *
 * @param theta the tilt angle used, either forced or read from the ellipse
 * @param shear the horizontal shear applied to each row
 * @param shift the horizontal shift which keeps the sheared image within positive coordinates
 * @param sx the horizontal scaling factor
 * @param sy the vertical scaling factor
 * @param detectedRatio the X/Y ratio computed from the ellipse, before any user override
 * @param height the height of the image this transformation was computed for
 */
public record GeometryTransform(
        double theta,
        double shear,
        double shift,
        double sx,
        double sy,
        double detectedRatio,
        int height
) {
    /**
     * Computes the transformation which makes the given ellipse circular.
     *
     * @param ellipse the detected solar disk
     * @param forcedTilt optional forced tilt angle (null to use the ellipse rotation)
     * @param xyRatio optional forced X/Y ratio (null to use the detected one)
     * @param height the height of the image to correct
     * @param disallowDownsampling whether the correction must stretch instead of shrinking
     * @return the transformation
     */
    public static GeometryTransform of(Ellipse ellipse,
                                       Double forcedTilt,
                                       Double xyRatio,
                                       int height,
                                       boolean disallowDownsampling) {
        var theta = forcedTilt == null ? ellipse.rotationAngle() : forcedTilt;
        var m = Math.tan(-theta);
        var semiAxis = ellipse.semiAxis();
        var a = semiAxis.a();
        var b = semiAxis.b();
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        var shear = (m * cos * a * a + sin * b * b) / (b * b * cos - a * a * m * sin);
        var maxDx = height * shear;
        var shift = maxDx < 0 ? maxDx : 0;
        var detectedRatio = Math.abs((a * b * Math.sqrt((a * a * m * m + b * b) / (a * a * sin * sin + b * b * cos * cos)) / (b * b * cos - a * a * m * sin)));
        var ratio = xyRatio == null ? detectedRatio : xyRatio;
        double sx;
        double sy;
        if (ratio < 1 || !disallowDownsampling) {
            sx = 1 / ratio;
            sy = 1.0d;
        } else {
            sx = 1.0d;
            sy = ratio;
        }
        return new GeometryTransform(theta, shear, shift, sx, sy, detectedRatio, height);
    }

    /**
     * Transforms the abscissa of a point of the source image.
     *
     * @param x the source abscissa
     * @param y the source ordinate
     * @return the abscissa of the point in the corrected image
     */
    public double transformX(double x, double y) {
        return (x - shift + y * shear) * sx;
    }

    /**
     * Transforms the ordinate of a point of the source image.
     *
     * @param y the source ordinate
     * @return the ordinate of the point in the corrected image
     */
    public double transformY(double y) {
        return y * sy;
    }
}
