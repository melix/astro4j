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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification

class HeliographicProjectionTest extends Specification {
    private static final double CX = 500
    private static final double CY = 400
    private static final double RADIUS = 300

    def "the disk center is at the latitude B0 on the central meridian"() {
        when:
        def coords = HeliographicProjection.toHeliographic(CX, CY, CX, CY, RADIUS, Math.toRadians(20), Math.toRadians(6)).get()

        then:
        Math.abs(coords.latitude() - 6) < 1e-9
        Math.abs(coords.longitude()) < 1e-9
    }

    def "points on the far side of the Sun are not visible"() {
        expect:
        HeliographicProjection.toImage(new HeliographicProjection.Coordinates(0, 120), CX, CY, RADIUS, 0, 0).empty
        !HeliographicProjection.isVisible(new HeliographicProjection.Coordinates(0, 120), 0)
    }

    def "the P angle turns the north pole towards east"() {
        when:
        def pole = HeliographicProjection.project(new HeliographicProjection.Coordinates(90, 0), CX, CY, RADIUS, Math.toRadians(20), 0)

        then:
        pole.x() < CX
        pole.y() < CY
    }

    def "north is up and west is right"() {
        expect:
        HeliographicProjection.toHeliographic(CX, CY - 150, CX, CY, RADIUS, 0, 0).get().latitude() > 0
        HeliographicProjection.toHeliographic(CX + 150, CY, CX, CY, RADIUS, 0, 0).get().longitude() > 0
    }

    def "converting to the image and back gives the same coordinates"() {
        when:
        def point = HeliographicProjection.toImage(new HeliographicProjection.Coordinates(latitude, longitude), CX, CY, RADIUS, Math.toRadians(p), Math.toRadians(b0)).get()
        def coords = HeliographicProjection.toHeliographic(point.x(), point.y(), CX, CY, RADIUS, Math.toRadians(p), Math.toRadians(b0)).get()

        then:
        Math.abs(coords.latitude() - latitude) < 1e-6
        Math.abs(coords.longitude() - longitude) < 1e-6

        where:
        latitude | longitude | p     | b0
        12       | 30        | 0     | 0
        -25      | -40       | 17.5  | -6.2
        45       | 60        | -24.8 | 7.1
        -8       | -70       | 26.1  | 6.3
    }

    def "with a positive B0 the center of the disk is north of the equator"() {
        when:
        def point = HeliographicProjection.toImage(new HeliographicProjection.Coordinates(0, 0), CX, CY, RADIUS, 0, Math.toRadians(7)).get()

        then:
        point.y() > CY
    }

    def "positions outside of the disk have no coordinates"() {
        expect:
        HeliographicProjection.toHeliographic(CX + RADIUS + 1, CY, CX, CY, RADIUS, 0, 0).empty
    }
}
