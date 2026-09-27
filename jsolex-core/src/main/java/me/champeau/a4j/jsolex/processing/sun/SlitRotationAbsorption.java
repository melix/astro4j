/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * Models the part of the solar rotation which is absorbed by the spectral line
 * polynomial when that polynomial is computed from the scan itself.
 * <p>
 * The polynomial gives, for each column of the slit, the line position of the average
 * of the frames. The rotation velocity averaged over the frames of a column is therefore
 * part of the polynomial and disappears from any measurement made relative to it.
 * <p>
 * The rotation law is modelled as ω(φ) = A + B·sin²φ + C·sin⁴φ. For each of the three basis
 * functions (1, sin²φ, sin⁴φ), this class computes the line-of-sight velocity averaged over
 * the frames of each column, weighted by the limb-darkened intensity, then fits it with a
 * second order polynomial like the spectral line polynomial.
 */
public final class SlitRotationAbsorption {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlitRotationAbsorption.class);

    public static final int BASIS_SIZE = 3;

    private static final double LIMB_DARKENING_COEFFICIENT = 0.6;

    private static final SlitRotationAbsorption NONE = new SlitRotationAbsorption(null, 0);

    private final DoubleUnaryOperator[] absorbed;
    private final double b0;

    private SlitRotationAbsorption(DoubleUnaryOperator[] absorbed, double b0) {
        this.absorbed = absorbed;
        this.b0 = b0;
    }

    /**
     * Returns an instance for a polynomial which does not come from the scan, which
     * therefore did not absorb any rotation.
     *
     * @return an instance which absorbs nothing
     */
    public static SlitRotationAbsorption none() {
        return NONE;
    }

    /**
     * Computes the absorbed rotation for each column of the slit.
     *
     * @param refCoords the transforms between the SER file and the image
     * @param centerX the x coordinate of the disk center in the image
     * @param centerY the y coordinate of the disk center in the image
     * @param radius the disk radius in the image, in pixels
     * @param b0 the B0 angle, in radians
     * @param angleP the P angle, in radians
     * @param frameCount the number of frames of the SER file
     * @param columnCount the number of columns of a SER frame
     * @return the absorbed rotation model
     */
    public static SlitRotationAbsorption compute(ReferenceCoords refCoords,
                                                 double centerX,
                                                 double centerY,
                                                 double radius,
                                                 double b0,
                                                 double angleP,
                                                 int frameCount,
                                                 int columnCount) {
        var frameWeights = new double[frameCount];
        forEachDiskSample(refCoords, centerX, centerY, radius, b0, angleP, frameCount, columnCount,
                (frame, column, weight, basis) -> frameWeights[frame] += weight);
        var threshold = AverageImageCreator.FRAME_INCLUSION_THRESHOLD * Arrays.stream(frameWeights).max().orElse(0);
        var columnWeights = new double[columnCount];
        var columnSums = new double[BASIS_SIZE][columnCount];
        forEachDiskSample(refCoords, centerX, centerY, radius, b0, angleP, frameCount, columnCount,
                (frame, column, weight, basis) -> {
                    if (frameWeights[frame] > threshold) {
                        columnWeights[column] += weight;
                        for (int k = 0; k < BASIS_SIZE; k++) {
                            columnSums[k][column] += weight * basis[k];
                        }
                    }
                });
        var columns = new ArrayList<Integer>();
        for (int column = 0; column < columnCount; column++) {
            if (columnWeights[column] > 0) {
                columns.add(column);
            }
        }
        if (columns.size() < BASIS_SIZE) {
            LOGGER.warn("The rotation absorbed by the spectral line polynomial could not be modelled: no disk pixel maps to the video frames");
            return NONE;
        }
        var xs = new double[columns.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = columns.get(i);
        }
        var absorbed = new DoubleUnaryOperator[BASIS_SIZE];
        for (int k = 0; k < BASIS_SIZE; k++) {
            var ys = new double[xs.length];
            for (int i = 0; i < xs.length; i++) {
                int column = columns.get(i);
                ys[i] = columnSums[k][column] / columnWeights[column];
            }
            absorbed[k] = LinearRegression.asPolynomial(LinearRegression.kOrderRegression(xs, ys, 2));
        }
        return new SlitRotationAbsorption(absorbed, b0);
    }

    /**
     * Computes, for an East/West pair of points at the same latitude, the absorbed part
     * of the velocity difference of each basis function, relative to the velocity
     * difference of a rigid rotation (basis function 1).
     *
     * @param latitude the latitude of both points, in radians
     * @param eastLongitude the longitude of the East point, in radians
     * @param westLongitude the longitude of the West point, in radians
     * @param eastColumn the SER column of the East point
     * @param westColumn the SER column of the West point
     * @return the absorbed ratio for each basis function
     */
    public double[] absorbedRatios(double latitude, double eastLongitude, double westLongitude, int eastColumn, int westColumn) {
        var ratios = new double[BASIS_SIZE];
        if (absorbed == null) {
            return ratios;
        }
        var rigidDifference = Math.cos(latitude) * Math.cos(b0) * (Math.sin(westLongitude) - Math.sin(eastLongitude));
        if (rigidDifference == 0) {
            return ratios;
        }
        for (int k = 0; k < BASIS_SIZE; k++) {
            ratios[k] = (absorbed[k].applyAsDouble(westColumn) - absorbed[k].applyAsDouble(eastColumn)) / rigidDifference;
        }
        return ratios;
    }

    /**
     * Projects a point of the solar sphere onto the image plane.
     *
     * @param longitude the longitude, in radians: 0 faces the observer
     * @param colatitude the colatitude, in radians
     * @param radius the disk radius, in pixels
     * @param b0 the B0 angle, in radians
     * @param angleP the P angle, in radians
     * @return the (x, y) offset from the disk center, in pixels
     */
    public static double[] projectToImage(double longitude, double colatitude, double radius, double b0, double angleP) {
        var x = Math.sin(longitude) * Math.sin(colatitude);
        var y = Math.cos(colatitude);
        var z = Math.cos(longitude) * Math.sin(colatitude);
        var cosB0 = Math.cos(-b0);
        var sinB0 = Math.sin(-b0);
        var y1 = y * cosB0 - z * sinB0;
        var cosP = Math.cos(-angleP);
        var sinP = Math.sin(-angleP);
        return new double[]{
                (x * cosP - y1 * sinP) * radius,
                (x * sinP + y1 * cosP) * radius
        };
    }

    private static void forEachDiskSample(ReferenceCoords refCoords,
                                          double centerX,
                                          double centerY,
                                          double radius,
                                          double b0,
                                          double angleP,
                                          int frameCount,
                                          int columnCount,
                                          DiskSampleConsumer consumer) {
        var cosB0 = Math.cos(-b0);
        var sinB0 = Math.sin(-b0);
        var cosP = Math.cos(-angleP);
        var sinP = Math.sin(-angleP);
        var basis = new double[BASIS_SIZE];
        var minX = (int) Math.floor(centerX - radius);
        var maxX = (int) Math.ceil(centerX + radius);
        var minY = (int) Math.floor(centerY - radius);
        var maxY = (int) Math.ceil(centerY + radius);
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                var dx = (px - centerX) / radius;
                var dy = (py - centerY) / radius;
                var rho2 = dx * dx + dy * dy;
                if (rho2 >= 1) {
                    continue;
                }
                var original = refCoords.determineOriginalCoordinates(new Point2D(px, py), ReferenceCoords.NO_LIMIT);
                var column = (int) Math.round(original.x());
                var frame = (int) Math.round(original.y());
                if (column < 0 || column >= columnCount || frame < 0 || frame >= frameCount) {
                    continue;
                }
                var x = dx * cosP + dy * sinP;
                var y1 = -dx * sinP + dy * cosP;
                var mu = Math.sqrt(Math.max(0, 1 - rho2));
                var y = y1 * cosB0 + mu * sinB0;
                var sinLat = Math.max(-1, Math.min(1, y));
                var cosLat = Math.sqrt(1 - sinLat * sinLat);
                var sinLon = cosLat > 0 ? x / cosLat : 0;
                var sinLat2 = sinLat * sinLat;
                var rigid = cosLat * sinLon * Math.cos(b0);
                basis[0] = rigid;
                basis[1] = rigid * sinLat2;
                basis[2] = rigid * sinLat2 * sinLat2;
                var weight = 1 - LIMB_DARKENING_COEFFICIENT * (1 - mu);
                consumer.accept(frame, column, weight, basis);
            }
        }
    }

    @FunctionalInterface
    private interface DiskSampleConsumer {
        void accept(int frame, int column, double weight, double[] basis);
    }
}
