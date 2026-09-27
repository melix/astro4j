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
package me.champeau.a4j.jsolex.app.listeners

import spock.lang.Specification

class DifferentialRotationMeasurementTest extends Specification {
    private static final double A = 13.1
    private static final double B = -1.7
    private static final double C = -2.3

    def "recovers the rotation law from scans with different P angles"() {
        given:
        def scans = [syntheticScan(0.10, 0, null), syntheticScan(0.18, 0, null)]

        when:
        def coeffs = DifferentialRotationMeasurement.fit(scans, DifferentialRotationConfig.defaultConfig())

        then:
        Math.abs(coeffs.a() - A) < 1e-9
        Math.abs(coeffs.b() - B) < 1e-9
        Math.abs(coeffs.c() - C) < 1e-9
    }

    def "corrected points of combined scans follow the rotation law"() {
        given:
        def scans = [syntheticScan(0.10, 0, null), syntheticScan(0.18, 0, null)]
        def coeffs = DifferentialRotationMeasurement.fit(scans, DifferentialRotationConfig.defaultConfig())

        when:
        def combined = DifferentialRotationMeasurement.combineCorrected(scans, coeffs)

        then:
        combined.size() == scans.first().size()
        combined.every { p -> Math.abs(p[1] - toVelocity(law(p[0]))) < 1e-9 }
    }

    def "combining scans reduces the uncertainties"() {
        given:
        def random = new Random(42)
        def config = DifferentialRotationConfig.defaultConfig()
        def single = [syntheticScan(0.15, 0.3, random)]
        def several = (0..<4).collect { syntheticScan(0.15, 0.3, random) }

        when:
        def singleCoeffs = DifferentialRotationMeasurement.fit(single, config)
        def severalCoeffs = DifferentialRotationMeasurement.fit(several, config)

        then:
        severalCoeffs.aError() < singleCoeffs.aError() * 0.75
        severalCoeffs.bError() < singleCoeffs.bError() * 0.75
        severalCoeffs.cError() < singleCoeffs.cError() * 0.75
    }

    private static List<double[]> syntheticScan(double sin2P, double noise, Random random) {
        (-60..60).step(2).collect { int lat ->
            def s = Math.sin(Math.toRadians(lat))
            def s2 = s * s
            def ratios = [sin2P, sin2P * s2, sin2P * s2 * s2] as double[]
            def measured = A * (1 - ratios[0]) + B * (s2 - ratios[1]) + C * (s2 * s2 - ratios[2])
            if (random != null) {
                measured += noise * random.nextGaussian()
            }
            [lat, toVelocity(measured), 0.05, ratios[0], ratios[1], ratios[2]] as double[]
        }
    }

    private static double law(double lat) {
        def s = Math.sin(Math.toRadians(lat))
        def s2 = s * s
        A + B * s2 + C * s2 * s2
    }

    private static double toVelocity(double degreesPerDay) {
        Math.toRadians(degreesPerDay) / 86400 * 695700
    }
}
