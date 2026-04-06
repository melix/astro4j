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
package me.champeau.a4j.jsolex.processing.sun.align;

import me.champeau.a4j.math.correlation.CorrelationTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the angular (rotational) offset between two solar disk images
 * using 2D phase correlation in polar coordinates.
 * <p>
 * Both images are converted to polar representations where rows correspond
 * to radial distance and columns to angle. A rotation in Cartesian space
 * becomes a horizontal (column) shift in this polar image. The standard
 * 2D phase correlation then recovers the angular offset directly from the
 * column shift, using all 2D spatial structure for robustness.
 * <p>
 * The tile size is derived from the disk radius: {@code min(nextPow2(π·R), 1024)}
 * to match the angular resolution to the image pixel density.
 */
public class AngularCorrelation {
    private static final Logger LOGGER = LoggerFactory.getLogger(AngularCorrelation.class);
    private static final int MAX_TILE_SIZE = 1024;
    private static final int MIN_TILE_SIZE = 128;
    private static final double INNER_RADIUS_FRACTION = 0.3;
    private static final double OUTER_RADIUS_FRACTION = 0.92;

    public record Result(double angleDegrees, double confidence, double alignedNCC) {
    }

    private AngularCorrelation() {
    }

    /**
     * Detects the angular offset between a user image and a reference image.
     * Returns the angle, phase correlation confidence, and the NCC of the
     * polar patches after alignment (direct measure of match quality).
     *
     * @param userData   user image pixel data [height][width]
     * @param userCx     user image disk center X
     * @param userCy     user image disk center Y
     * @param userRadius user image disk radius in pixels
     * @param refData    reference image pixel data [height][width]
     * @param refCx      reference image disk center X
     * @param refCy      reference image disk center Y
     * @param refRadius  reference image disk radius in pixels
     * @return the angular offset, confidence, and aligned NCC
     */
    public static Result detectAngularOffset(
        float[][] userData, double userCx, double userCy, double userRadius,
        float[][] refData, double refCx, double refCy, double refRadius
    ) {
        var tileSize = computeTileSize(Math.min(userRadius, refRadius));
        LOGGER.info("Angular correlation: tile size {} (radius={})", tileSize,
            String.format("%.1f", Math.min(userRadius, refRadius)));

        var userPolar = createPolarPatch(userData, userCx, userCy, userRadius, tileSize);
        var refPolar = createPolarPatch(refData, refCx, refCy, refRadius, tileSize);

        var shift = CorrelationTools.phaseCorrelation2D(refPolar, userPolar, true);
        var angleDegrees = shift.dx() * (360.0 / tileSize);
        var confidence = shift.confidence();

        // Align user polar to detected shift and compute NCC
        var aligned = circularShiftColumns(userPolar, shift.dx(), tileSize);
        var ncc = computeNCC(refPolar, aligned, tileSize);

        LOGGER.info("  dx={}, angle={} deg, confidence={}, alignedNCC={}",
            String.format("%.2f", shift.dx()),
            String.format("%.2f", angleDegrees),
            String.format("%.3f", confidence),
            String.format("%.4f", ncc));
        return new Result(angleDegrees, confidence, ncc);
    }

    private static float[][] circularShiftColumns(float[][] data, double dx, int tileSize) {
        var intShift = (int) Math.round(dx);
        var result = new float[tileSize][tileSize];
        for (var ri = 0; ri < tileSize; ri++) {
            for (var ai = 0; ai < tileSize; ai++) {
                var srcCol = ((ai - intShift) % tileSize + tileSize) % tileSize;
                result[ri][ai] = data[ri][srcCol];
            }
        }
        return result;
    }

    private static double computeNCC(float[][] a, float[][] b, int tileSize) {
        var n = tileSize * tileSize;
        double sumA = 0;
        double sumB = 0;
        for (var y = 0; y < tileSize; y++) {
            for (var x = 0; x < tileSize; x++) {
                sumA += a[y][x];
                sumB += b[y][x];
            }
        }
        var meanA = sumA / n;
        var meanB = sumB / n;
        double sumAB = 0;
        double sumA2 = 0;
        double sumB2 = 0;
        for (var y = 0; y < tileSize; y++) {
            for (var x = 0; x < tileSize; x++) {
                var da = a[y][x] - meanA;
                var db = b[y][x] - meanB;
                sumAB += da * db;
                sumA2 += da * da;
                sumB2 += db * db;
            }
        }
        var denom = Math.sqrt(sumA2 * sumB2);
        return denom > 1e-10 ? sumAB / denom : 0;
    }

    private static int computeTileSize(double radius) {
        var ideal = Integer.highestOneBit((int) (Math.PI * radius));
        if (ideal < (int) (Math.PI * radius)) {
            ideal <<= 1;
        }
        return Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, ideal));
    }

    private static float[][] createPolarPatch(
        float[][] data, double cx, double cy, double radius, int tileSize
    ) {
        var width = data[0].length;
        var height = data.length;
        var polar = new float[tileSize][tileSize];
        var innerR = INNER_RADIUS_FRACTION * radius;
        var outerR = OUTER_RADIUS_FRACTION * radius;

        for (var ri = 0; ri < tileSize; ri++) {
            var r = innerR + (outerR - innerR) * ri / (tileSize - 1);
            for (var ai = 0; ai < tileSize; ai++) {
                var theta = ai * 2.0 * Math.PI / tileSize;
                var x = cx + r * Math.cos(theta);
                var y = cy + r * Math.sin(theta);
                polar[ri][ai] = bilinearSample(data, x, y, width, height);
            }
        }
        return polar;
    }

    private static float bilinearSample(float[][] data, double x, double y, int width, int height) {
        var x0 = (int) Math.floor(x);
        var y0 = (int) Math.floor(y);
        var x1 = x0 + 1;
        var y1 = y0 + 1;

        if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height) {
            return 0;
        }

        var fx = x - x0;
        var fy = y - y0;

        var v00 = data[y0][x0];
        var v10 = data[y0][x1];
        var v01 = data[y1][x0];
        var v11 = data[y1][x1];

        return (float) (v00 * (1 - fx) * (1 - fy)
            + v10 * fx * (1 - fy)
            + v01 * (1 - fx) * fy
            + v11 * fx * fy);
    }
}
