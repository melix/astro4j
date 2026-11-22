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
package me.champeau.a4j.jsolex.processing.sun.detection

import me.champeau.a4j.jsolex.processing.util.Dispersion
import me.champeau.a4j.math.Point2D
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.regression.EllipseRegression
import spock.lang.Specification

import java.util.function.DoubleUnaryOperator

class EllermanBombDetectorTest extends Specification {
    private static final int WIDTH = 400
    private static final int HEIGHT = 120
    private static final int LEFT = 20
    private static final int RIGHT = 380
    private static final double ANGSTROMS_PER_PIXEL = 0.1d
    private static final double CONTINUUM = 30000d
    private static final int COLUMN = 200
    private static final int FRAME = 5

    def "detects a compact brightening of both wings"() {
        when:
        def flares = detect { x, frameId, angstroms ->
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }

        then:
        flares.size() == 1
        with(flares.first()) {
            kind() == Flare.Kind.ELLERMAN_BOMB
            sourceX() == COLUMN
            frameId() == FRAME
            score() > 90
        }
    }

    def "detects a brightening which is stronger in the #side wing"() {
        when:
        def flares = detect { x, frameId, angstroms ->
            def amplitude = angstroms * strongest > 0 ? 0.3d : 0.15d
            line(angstroms) + amplitude * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }

        then:
        flares.size() == 1
        flares.first().kind() == Flare.Kind.ELLERMAN_BOMB
        flares.first().sourceX() == COLUMN

        where:
        side   | strongest
        "low"  | -1
        "high" | 1
    }

    def "ignores a brightening of the #side wing alone"() {
        expect:
        detect { x, frameId, angstroms ->
            def amplitude = angstroms * brightened > 0 ? 0.3d : 0d
            line(angstroms) + amplitude * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }.empty

        where:
        side   | brightened
        "low"  | -1
        "high" | 1
    }

    def "ignores a column which is brighter at all wavelengths"() {
        expect:
        detect { x, frameId, angstroms ->
            line(angstroms) * (1 + 0.2d * spot(x, frameId))
        }.empty
    }

    def "ignores a dark feature even if its wings are brighter than its far wings"() {
        expect:
        detect { x, frameId, angstroms ->
            def dimming = 0.6d - 0.03d * Math.min(5d, Math.abs(angstroms))
            def weight = spot(x, frameId)
            line(angstroms) * (1 - weight + weight * dimming)
        }.empty
    }

    def "detects an Ellerman bomb inside a plage"() {
        when:
        def flares = detect { x, frameId, angstroms ->
            def depth = Math.abs(x - COLUMN) < 50 ? 0.55d : 0.8d
            line(angstroms, depth) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }

        then:
        flares.size() == 1
        flares.first().kind() == Flare.Kind.ELLERMAN_BOMB
        flares.first().sourceX() == COLUMN
    }

    def "reports a flare when the line core is in emission"() {
        when:
        def flares = detect { x, frameId, angstroms ->
            line(angstroms) + CONTINUUM * (0.25d * wings(angstroms) + core(angstroms)) * spot(x, frameId)
        }

        then:
        flares.size() == 1
        flares.first().kind() == Flare.Kind.FLARE
        flares.first().sourceX() == COLUMN
    }

    def "gives a higher confidence to a stronger brightening"() {
        when:
        def flares = [0.01d, 0.12d, 0.25d].collect { amplitude ->
            detect { x, frameId, angstroms ->
                line(angstroms) + amplitude * CONTINUUM * wings(angstroms) * spot(x, frameId)
            }
        }

        then:
        flares[0].empty
        flares[1].size() == 1
        flares[2].size() == 1
        flares[1].first().score() < flares[2].first().score()
    }

    def "ignores the noise of the frames"() {
        given:
        def random = new Random(42)

        expect:
        detect(0..49) { x, frameId, angstroms ->
            line(angstroms) * (1 + 0.01d * random.nextGaussian())
        }.empty
    }

    def "detects an Ellerman bomb in noisy frames"() {
        given:
        def random = new Random(42)

        when:
        def flares = detect { x, frameId, angstroms ->
            (line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)) * (1 + 0.01d * random.nextGaussian())
        }

        then:
        flares.size() == 1
        flares.first().kind() == Flare.Kind.ELLERMAN_BOMB
        Math.abs(flares.first().sourceX() - COLUMN) <= 1
    }

    def "ignores a brightening seen in a single column of a single frame"() {
        expect:
        detect { x, frameId, angstroms ->
            def weight = x == COLUMN && frameId == FRAME ? 1d : 0d
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * weight
        }.empty
    }

    def "ignores a brightening which extends along the scan"() {
        expect:
        detect(0..59) { x, frameId, angstroms ->
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, FRAME)
        }.empty
    }

    def "detects an Ellerman bomb when the far wings are outside of the frame"() {
        when:
        def flares = detect(0..9, 50) { x, frameId, angstroms ->
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }

        then:
        flares.size() == 1
        flares.first().kind() == Flare.Kind.ELLERMAN_BOMB
        flares.first().sourceX() == COLUMN
    }

    def "uses the far wings of one side when the line is close to the edge of the frame"() {
        expect:
        detect(0..9, HEIGHT, null, 30d) { x, frameId, angstroms ->
            line(angstroms) * (1 + 0.2d * spot(x, frameId))
        }.empty
        detect(0..9, HEIGHT, null, 30d) { x, frameId, angstroms ->
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }.size() == 1
    }

    def "uses the far wings of each column when the line is tilted"() {
        expect:
        detect(0..9, HEIGHT, null, HEIGHT / 2d, 0.2d) { x, frameId, angstroms ->
            line(angstroms) * (1 + 0.2d * spot(x, frameId))
        }.empty
        detect(0..9, HEIGHT, null, HEIGHT / 2d, 0.2d) { x, frameId, angstroms ->
            line(angstroms) + 0.25d * CONTINUUM * wings(angstroms) * spot(x, frameId)
        }.size() == 1
    }

    def "detects an Ellerman bomb close to the limb"() {
        when:
        def flares = detect(295..305, HEIGHT, disk(320, 300, 300)) { x, frameId, angstroms ->
            line(angstroms) + 0.15d * CONTINUUM * wings(angstroms) * spot(x, frameId, 40, 300)
        }

        then:
        flares.size() == 1
        flares.first().sourceX() == 40
    }

    private static double line(double angstroms, double depth = 0.8d) {
        CONTINUUM * (1 - depth * core(angstroms))
    }

    private static double core(double angstroms) {
        Math.exp(-(angstroms / 0.5d) ** 2)
    }

    private static double wings(double angstroms) {
        Math.exp(-((Math.abs(angstroms) - 1.2d) / 0.5d) ** 2)
    }

    private static double spot(int x, int frameId, int column = COLUMN, int frame = FRAME) {
        Math.exp(-((x - column) ** 2 + (frameId - frame) ** 2) / 2d)
    }

    private static Ellipse disk(double centerX, double centerFrame, double radius) {
        def points = (0..<36).collect {
            def angle = Math.toRadians(10 * it)
            new Point2D(centerX + radius * Math.cos(angle), centerFrame + radius * Math.sin(angle))
        }
        new EllipseRegression(points).solve()
    }

    private static List<Flare> detect(IntRange frames = 0..9, int height = HEIGHT, Ellipse disk = null, double center = height / 2d, double tilt = 0d, Closure<Double> intensity) {
        def detector = new EllermanBombDetector(Dispersion.ofAngstromsPerPixel(ANGSTROMS_PER_PIXEL))
        def polynomial = { double x -> center + tilt * (x - COLUMN) } as DoubleUnaryOperator
        frames.each { frameId ->
            def frame = new float[height][WIDTH]
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    frame[y][x] = (float) intensity(x, frameId, (y - polynomial.applyAsDouble(x)) * ANGSTROMS_PER_PIXEL)
                }
            }
            detector.analyzeFrame(frameId, LEFT, RIGHT, height, frame, polynomial)
        }
        detector.findFlares(Optional.ofNullable(disk))
    }
}
