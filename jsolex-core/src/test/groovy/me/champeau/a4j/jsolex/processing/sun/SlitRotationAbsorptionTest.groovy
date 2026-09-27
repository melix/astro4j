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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords
import me.champeau.a4j.math.Point2D
import spock.lang.Specification

class SlitRotationAbsorptionTest extends Specification {
    private static final int SIZE = 1000
    private static final double CENTER = 500
    private static final double RADIUS = 400

    def "a right ascension scan keeps cos²P of a rigid rotation"() {
        given:
        def refCoords = new ReferenceCoords([]).addLeftRotation(SIZE)

        expect:
        Math.abs(retainedFraction(refCoords, Math.toRadians(p), Math.toRadians(b0)) - Math.pow(Math.cos(Math.toRadians(p)), 2)) < 0.005

        where:
        p      | b0
        0      | 0
        10     | 0
        24.09  | 7.2
        -26.3  | -7.25
    }

    def "a declination scan keeps sin²P of a rigid rotation"() {
        given:
        def refCoords = new ReferenceCoords([])

        expect:
        Math.abs(retainedFraction(refCoords, Math.toRadians(p), Math.toRadians(b0)) - Math.pow(Math.sin(Math.toRadians(p)), 2)) < 0.005

        where:
        p      | b0
        0      | 0
        24.09  | 7.2
    }

    def "a polynomial which does not come from the scan absorbs nothing"() {
        expect:
        SlitRotationAbsorption.none().absorbedRatios(0.3, 1, -1, 100, 900) == [0, 0, 0] as double[]
    }

    private static double retainedFraction(ReferenceCoords refCoords, double angleP, double b0) {
        def absorption = SlitRotationAbsorption.compute(refCoords, CENTER, CENTER, RADIUS, b0, angleP, SIZE, SIZE)
        def eastLon = Math.toRadians(60)
        def westLon = -eastLon
        def colatitude = Math.PI / 2
        def eastColumn = column(refCoords, SlitRotationAbsorption.projectToImage(eastLon, colatitude, RADIUS, b0, angleP))
        def westColumn = column(refCoords, SlitRotationAbsorption.projectToImage(westLon, colatitude, RADIUS, b0, angleP))
        1 - absorption.absorbedRatios(0, eastLon, westLon, eastColumn, westColumn)[0]
    }

    private static int column(ReferenceCoords refCoords, double[] offset) {
        def original = refCoords.determineOriginalCoordinates(new Point2D(CENTER + offset[0], CENTER + offset[1]), ReferenceCoords.NO_LIMIT)
        (int) Math.round(original.x())
    }
}
