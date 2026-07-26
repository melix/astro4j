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

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The reference radii below were measured on the browse images published by SDO,
 * by locating the limb on a radial brightness profile. The AIA figure comes from
 * the 4500 white light channel and the HMI one from the HMIIC continuum, because
 * both show the photospheric limb, unlike the EUV channels whose limb brightening
 * ring sits about 1.6% higher.
 */
class SDOGeometryTest extends Specification {

    private static final ZonedDateTime APHELION_SIDE = ZonedDateTime.of(2026, 7, 25, 0, 15, 0, 0, ZoneId.of('UTC'))
    private static final ZonedDateTime PERIHELION_SIDE = ZonedDateTime.of(2026, 1, 4, 0, 15, 0, 0, ZoneId.of('UTC'))

    private static SDO.SdoCandidate candidate(ZonedDateTime date, String channel, int resolution) {
        new SDO.SdoCandidate(new URL('file:///unused.jpg'), date, channel, resolution, 'unused.jpg')
    }

    def "computes the solar disk radius of an AIA frame"() {
        expect:
        Math.abs(candidate(APHELION_SIDE, channel, 1024).solarDiskRadius() - 393.6) < 1.0

        where:
        channel << ['0094', '0131', '0171', '0193', '0211', '0304', '0335', '1600', '1700', '4500']
    }

    def "computes the solar disk radius of an HMI frame"() {
        expect:
        Math.abs(candidate(APHELION_SIDE, channel, 1024).solarDiskRadius() - 468.6) < 1.0

        where:
        channel << ['HMIB', 'HMIBC', 'HMII', 'HMIIC', 'HMIIF', 'HMID']
    }

    def "composite channels are published on the AIA grid"() {
        expect:
        Math.abs(candidate(APHELION_SIDE, channel, 1024).solarDiskRadius() - 393.6) < 1.0

        where:
        channel << ['HMI171', '211193171', '211193171n', '211193171rg', '304211171', '094335193']
    }

    def "channel codes are matched regardless of case"() {
        expect:
        candidate(APHELION_SIDE, 'hmiic', 1024).solarDiskRadius() == candidate(APHELION_SIDE, 'HMIIC', 1024).solarDiskRadius()
    }

    def "the radius scales with the resolution of the frame"() {
        given:
        def at1024 = candidate(APHELION_SIDE, '0094', 1024).solarDiskRadius()

        expect:
        Math.abs(candidate(APHELION_SIDE, '0094', resolution).solarDiskRadius() - at1024 * resolution / 1024) < 0.001

        where:
        resolution << [512, 2048, 4096]
    }

    def "the disk is larger near perihelion than near aphelion"() {
        given:
        def summer = candidate(APHELION_SIDE, '0094', 1024).solarDiskRadius()
        def winter = candidate(PERIHELION_SIDE, '0094', 1024).solarDiskRadius()

        expect:
        Math.abs(winter - 406.6) < 1.0
        Math.abs((winter / summer - 1) * 100 - 3.3) < 0.2
    }
}
