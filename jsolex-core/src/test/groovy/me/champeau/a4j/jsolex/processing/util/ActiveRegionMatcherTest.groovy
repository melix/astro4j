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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegion
import me.champeau.a4j.math.Point2D
import spock.lang.Specification

class ActiveRegionMatcherTest extends Specification {
    private static final double CENTER_X = 500
    private static final double CENTER_Y = 500
    private static final double RADIUS = 400

    def "projects a region at the center of the disk"() {
        given:
        def region = new NOAAActiveRegion("4410", 0, 0, 5)

        when:
        def anchor = ActiveRegionMatcher.project(region, CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        anchor.present
        anchor.get().x() == CENTER_X
        anchor.get().y() == CENTER_Y
    }

    def "ignores regions on the far side of the Sun"() {
        given:
        def region = new NOAAActiveRegion("4410", 0, 180, 5)

        when:
        def anchor = ActiveRegionMatcher.project(region, CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        !anchor.present
    }

    def "matches a detected region with the NOAA region at the same position"() {
        given:
        def detected = regionAt(CENTER_X, CENTER_Y, 40)
        def noaa = new NOAAActiveRegion("4410", 0, 0, 5)

        when:
        def matches = ActiveRegionMatcher.match([detected], [noaa], CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        matches[detected].id() == "4410"
    }

    def "does not match a detected region which is too far away"() {
        given:
        def detected = regionAt(CENTER_X + 0.5 * RADIUS, CENTER_Y, 40)
        def noaa = new NOAAActiveRegion("4410", 0, 0, 5)

        when:
        def matches = ActiveRegionMatcher.match([detected], [noaa], CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        matches.isEmpty()
    }

    def "associates all the parts of a split region with the same NOAA region"() {
        given:
        def firstPart = regionAt(CENTER_X, CENTER_Y, 20)
        def secondPart = regionAt(CENTER_X + 0.05 * RADIUS, CENTER_Y, 20)
        def noaa = new NOAAActiveRegion("4410", 0, 0, 5)

        when:
        def matches = ActiveRegionMatcher.match([firstPart, secondPart], [noaa], CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        matches[firstPart].id() == "4410"
        matches[secondPart].id() == "4410"
    }

    def "associates a detected region with the closest NOAA region"() {
        given:
        def detected = regionAt(CENTER_X, CENTER_Y, 20)
        def closest = new NOAAActiveRegion("4410", 0, 0, 5)
        def farther = new NOAAActiveRegion("4411", 3, 0, 5)

        when:
        def matches = ActiveRegionMatcher.match([detected], [farther, closest], CENTER_X, CENTER_Y, RADIUS, 0, 0)

        then:
        matches[detected].id() == "4410"
    }

    private static ActiveRegion regionAt(double centerX, double centerY, double size) {
        def half = size / 2
        return new ActiveRegion(
                List.of(new Point2D(centerX - half, centerY - half), new Point2D(centerX + half, centerY + half)),
                new Point2D(centerX - half, centerY - half),
                new Point2D(centerX + half, centerY + half))
    }
}
