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

import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegion;
import me.champeau.a4j.math.Point2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Projects NOAA active regions, whose position is given in heliographic coordinates,
 * onto image coordinates, and matches them with the active regions which were detected
 * on the image.
 */
public final class ActiveRegionMatcher {
    private static final double MATCH_RADIUS_FACTOR = 0.1;

    private ActiveRegionMatcher() {
    }

    /**
     * Projects a NOAA active region onto image coordinates.
     *
     * @param region the region to project
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @param angleP the P angle, in radians, or 0 if the image is already P-corrected
     * @param b0 the B0 angle, in radians
     * @return the position of the region, or empty if it is on the far side of the Sun
     */
    public static Optional<Point2D> project(NOAAActiveRegion region,
                                            double centerX,
                                            double centerY,
                                            double radius,
                                            double angleP,
                                            double b0) {
        return HeliographicProjection.toImage(new HeliographicProjection.Coordinates(region.latitudeDeg(), region.longitudeDeg()),
                centerX, centerY, radius, angleP, b0);
    }

    /**
     * Determines if a detected active region corresponds to a NOAA region projected at
     * the supplied position.
     *
     * @param detected the detected region
     * @param anchor the projected position of the NOAA region
     * @param radius the radius of the solar disk, in pixels
     * @return true if both correspond to the same region
     */
    public static boolean matches(ActiveRegion detected, Point2D anchor, double radius) {
        return distance(detected, anchor) < MATCH_RADIUS_FACTOR * radius;
    }

    /**
     * Matches detected active regions with the NOAA regions listed in the SRS reports.
     * Detectors often split a single NOAA region into several parts, so more than one
     * detected region can be associated with the same NOAA region.
     *
     * @param detectedRegions the regions which were detected on the image
     * @param noaaRegions the NOAA regions
     * @param centerX the x coordinate of the disk center
     * @param centerY the y coordinate of the disk center
     * @param radius the radius of the solar disk, in pixels
     * @param angleP the P angle, in radians, or 0 if the image is already P-corrected
     * @param b0 the B0 angle, in radians
     * @return the detected regions which could be identified, and their NOAA counterpart
     */
    public static Map<ActiveRegion, NOAAActiveRegion> match(List<ActiveRegion> detectedRegions,
                                                            List<NOAAActiveRegion> noaaRegions,
                                                            double centerX,
                                                            double centerY,
                                                            double radius,
                                                            double angleP,
                                                            double b0) {
        var anchors = new ArrayList<Anchored>();
        for (var noaaRegion : noaaRegions) {
            project(noaaRegion, centerX, centerY, radius, angleP, b0)
                    .ifPresent(anchor -> anchors.add(new Anchored(noaaRegion, anchor)));
        }
        var result = new HashMap<ActiveRegion, NOAAActiveRegion>();
        for (var detected : detectedRegions) {
            anchors.stream()
                    .filter(anchored -> matches(detected, anchored.anchor(), radius))
                    .min(Comparator.comparingDouble(anchored -> distance(detected, anchored.anchor())))
                    .ifPresent(anchored -> result.put(detected, anchored.noaaRegion()));
        }
        return result;
    }

    private static double distance(ActiveRegion detected, Point2D anchor) {
        var cx = detected.topLeft().x() + detected.width() / 2;
        var cy = detected.topLeft().y() + detected.height() / 2;
        return Math.hypot(cx - anchor.x(), cy - anchor.y());
    }

    private record Anchored(NOAAActiveRegion noaaRegion, Point2D anchor) {
    }
}
