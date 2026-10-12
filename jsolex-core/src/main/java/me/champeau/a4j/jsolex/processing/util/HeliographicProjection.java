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

import me.champeau.a4j.math.Point2D;

import java.util.Optional;

/**
 * Converts between heliographic coordinates and positions on an image of the solar disk.
 * Latitudes are positive towards north and longitudes, measured from the central meridian,
 * positive towards west, as in the NOAA reports. Images are assumed to show the Sun with north
 * up and east left before the rotation by the P angle, which is the orientation given by the flips
 * of the processing parameters and the one used by the overlays.
 */
public final class HeliographicProjection {
    private HeliographicProjection() {
    }

    /**
     * Heliographic coordinates.
     *
     * @param latitude the latitude, in degrees, positive towards north
     * @param longitude the longitude from the central meridian, in degrees, positive towards west
     */
    public record Coordinates(double latitude, double longitude) {
    }

    /**
     * Returns the position of a point of the Sun on the image, even if it is on the far side of the Sun.
     *
     * @param coordinates the heliographic coordinates of the point
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @param angleP the P angle, in radians, or 0 if the image is already P-corrected
     * @param b0 the B0 angle, in radians
     * @return the position
     */
    public static Point2D project(Coordinates coordinates, double centerX, double centerY, double radius,
                                  double angleP, double b0) {
        var v = toImageFrame(coordinates, b0, angleP);
        return new Point2D(centerX + v.x() * radius, centerY + v.y() * radius);
    }

    /**
     * Tells if a point of the Sun is on the side facing the observer.
     *
     * @param coordinates the heliographic coordinates of the point
     * @param b0 the B0 angle, in radians
     * @return true if the point is visible
     */
    public static boolean isVisible(Coordinates coordinates, double b0) {
        return toImageFrame(coordinates, b0, 0).z() > 0;
    }

    /**
     * Returns the position of a point of the Sun on the image.
     *
     * @param coordinates the heliographic coordinates of the point
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @param angleP the P angle, in radians, or 0 if the image is already P-corrected
     * @param b0 the B0 angle, in radians
     * @return the position, or empty if the point is on the far side of the Sun
     */
    public static Optional<Point2D> toImage(Coordinates coordinates, double centerX, double centerY, double radius,
                                            double angleP, double b0) {
        if (!isVisible(coordinates, b0)) {
            return Optional.empty();
        }
        return Optional.of(project(coordinates, centerX, centerY, radius, angleP, b0));
    }

    /**
     * Returns the heliographic coordinates of a position of the image.
     *
     * @param x the column
     * @param y the row
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @param angleP the P angle, in radians, or 0 if the image is already P-corrected
     * @param b0 the B0 angle, in radians
     * @return the coordinates, or empty if the position is outside of the disk
     */
    public static Optional<Coordinates> toHeliographic(double x, double y, double centerX, double centerY, double radius,
                                                       double angleP, double b0) {
        var mu = mu(x, y, centerX, centerY, radius);
        if (Double.isNaN(mu)) {
            return Optional.empty();
        }
        var v = new Vector((x - centerX) / radius, (y - centerY) / radius, mu).rotateZ(angleP).rotateX(b0);
        var latitude = -Math.toDegrees(Math.asin(Math.clamp(v.y(), -1, 1)));
        var longitude = Math.toDegrees(Math.atan2(v.x(), v.z()));
        return Optional.of(new Coordinates(latitude, longitude));
    }

    /**
     * Returns the cosine of the angle between the line of sight and the normal to the solar surface
     * at a position of the image, which is 1 at the disk center and 0 at the limb.
     *
     * @param x the column
     * @param y the row
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @return the cosine, or NaN if the position is outside of the disk
     */
    public static double mu(double x, double y, double centerX, double centerY, double radius) {
        var dx = (x - centerX) / radius;
        var dy = (y - centerY) / radius;
        var r2 = dx * dx + dy * dy;
        if (r2 > 1) {
            return Double.NaN;
        }
        return Math.sqrt(1 - r2);
    }

    private static Vector toImageFrame(Coordinates coordinates, double b0, double angleP) {
        var latitude = Math.toRadians(coordinates.latitude());
        var longitude = Math.toRadians(coordinates.longitude());
        return new Vector(Math.sin(longitude) * Math.cos(latitude), -Math.sin(latitude), Math.cos(longitude) * Math.cos(latitude))
                .rotateX(-b0)
                .rotateZ(-angleP);
    }

    private record Vector(double x, double y, double z) {
        Vector rotateX(double angle) {
            return new Vector(x, Math.cos(angle) * y - Math.sin(angle) * z, Math.sin(angle) * y + Math.cos(angle) * z);
        }

        Vector rotateZ(double angle) {
            return new Vector(Math.cos(angle) * x - Math.sin(angle) * y, Math.sin(angle) * x + Math.cos(angle) * y, z);
        }
    }
}
