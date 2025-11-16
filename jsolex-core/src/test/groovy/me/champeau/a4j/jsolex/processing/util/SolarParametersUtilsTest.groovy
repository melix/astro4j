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

import spock.lang.Specification

import java.time.LocalDateTime

class SolarParametersUtilsTest extends Specification {

    def "computes Julian date correctly"() {
        when:
        def jd = SolarParametersUtils.localDateTimeToJulianDate(LocalDateTime.of(2025, 11, 12, 12, 10, 36, 609269000))

        then:
        Math.abs(jd - 2460992.00736816) < 0.00001
    }

    def "computes Carrington rotation number correctly"() {
        when:
        def jd = SolarParametersUtils.localDateTimeToJulianDate(LocalDateTime.of(2025, 11, 12, 12, 10, 36, 609269000))
        def rotation = SolarParametersUtils.computeCarringtonRotationNumber(jd)

        then:
        rotation == 2304
    }

    def "computes L0 matching BASS2000 ephemeris data"() {
        when:
        def params = SolarParametersUtils.computeSolarParams(LocalDateTime.of(2025, 11, 12, 12, 10, 36, 609269000))
        def l0Degrees = Math.toDegrees(params.l0())

        then:
        Math.abs(l0Degrees - 231.59) < 0.5
    }

    def "computes B0 correctly"() {
        when:
        def params = SolarParametersUtils.computeSolarParams(LocalDateTime.of(2025, 11, 12, 12, 10, 36, 609269000))
        def b0Degrees = Math.toDegrees(params.b0())

        then:
        Math.abs(b0Degrees - 3.14) < 0.1
    }

    def "L0 decreases as time progresses within a rotation"() {
        given:
        def time1 = LocalDateTime.of(2025, 11, 12, 12, 0, 0)
        def time2 = LocalDateTime.of(2025, 11, 13, 12, 0, 0)

        when:
        def l0_1 = Math.toDegrees(SolarParametersUtils.computeSolarParams(time1).l0())
        def l0_2 = Math.toDegrees(SolarParametersUtils.computeSolarParams(time2).l0())
        def expectedDecrease = 360.0 / SolarParametersUtils.CARRINGTON_ROTATION_PERIOD

        then:
        def actualDecrease = l0_1 - l0_2
        if (actualDecrease < 0) {
            actualDecrease += 360.0
        }
        Math.abs(actualDecrease - expectedDecrease) < 0.1
    }

    def "L0 is always in range [0, 360)"() {
        when:
        def params = SolarParametersUtils.computeSolarParams(dateTime)
        def l0Degrees = Math.toDegrees(params.l0())

        then:
        l0Degrees >= 0
        l0Degrees < 360

        where:
        dateTime << [
            LocalDateTime.of(2020, 1, 1, 12, 0, 0),
            LocalDateTime.of(2021, 6, 15, 0, 0, 0),
            LocalDateTime.of(2022, 12, 31, 23, 59, 59),
            LocalDateTime.of(2025, 11, 12, 12, 10, 36, 609269000)
        ]
    }

    def "Carrington rotation increments correctly"() {
        given:
        def startOfRotation = SolarParametersUtils.CARRINGTON_ROTATION_1_START + (2303 * SolarParametersUtils.CARRINGTON_ROTATION_PERIOD)

        when:
        def rotationAtStart = SolarParametersUtils.computeCarringtonRotationNumber(startOfRotation)
        def rotationJustBefore = SolarParametersUtils.computeCarringtonRotationNumber(startOfRotation - 0.001)
        def rotationJustAfter = SolarParametersUtils.computeCarringtonRotationNumber(startOfRotation + 0.001)

        then:
        rotationAtStart == 2304
        rotationJustBefore == 2303
        rotationJustAfter == 2304
    }
}