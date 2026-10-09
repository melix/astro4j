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
package me.champeau.a4j.jsolex.processing.sun.tasks

import me.champeau.a4j.jsolex.processing.event.ProgressOperation
import me.champeau.a4j.jsolex.processing.params.ProcessParams
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.Constants
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.MutableMap
import spock.lang.Specification

class EllipseFittingTaskTest extends Specification {
    private static final int SIZE = 500
    private static final double RADIUS = 160
    private static final double CENTER = SIZE / 2.0d
    private static final double HALO_SCALE = 60
    private static final double HALO_LEVEL = 0.6

    def "fits the plateau of a saturated disk surrounded by a halo in saturated disk mode (halo level #haloLevel, scale #haloScale)"() {
        given:
        def image = saturatedDiskWithHalo(haloLevel, haloScale)
        def params = withSaturatedDiskMode(true)

        when:
        def result = fit(image, params)
        def center = result.ellipse().center()
        def semiAxis = result.ellipse().semiAxis()

        then:
        Math.abs(center.a() - CENTER) < 2
        Math.abs(center.b() - CENTER) < 2
        Math.abs(semiAxis.a() - RADIUS) < 3
        Math.abs(semiAxis.b() - RADIUS) < 3

        where:
        haloLevel | haloScale
        0.6d      | 60d
        0.8d      | 60d
        0.9d      | 30d
        0.9d      | 120d
        0.6d      | 20d
        0.6d      | 120d
    }

    def "ignores a hot spot brighter than the saturated plateau in saturated disk mode"() {
        given:
        def image = scene { double x, double y ->
            def r = Math.hypot(x - CENTER, y - CENTER)
            if (Math.hypot(x - CENTER - 60, y - CENTER + 40) <= 12) {
                return Constants.MAX_PIXEL_VALUE
            }
            return plateauWithHalo(r, 0.7d)
        }
        def params = withSaturatedDiskMode(true)

        when:
        def result = fit(image, params)

        then:
        fitsPlateau(result)
    }

    def "handles an unevenly lit plateau in saturated disk mode"() {
        given:
        def image = scene { double x, double y ->
            def r = Math.hypot(x - CENTER, y - CENTER)
            def level = 0.75d + 0.25d * (x - CENTER + RADIUS) / (2 * RADIUS)
            return plateauWithHalo(r, Math.max(0.75d, Math.min(1.0d, level)))
        }
        def params = withSaturatedDiskMode(true)

        when:
        def result = fit(image, params)

        then:
        fitsPlateau(result)
    }

    def "fits the limb of a disk whose saturated area does not reach the limb in saturated disk mode"() {
        given:
        def image = radialScene { double r ->
            if (r > RADIUS) {
                return 0.02 * Constants.MAX_PIXEL_VALUE
            }
            return Math.min(1.0d, 0.55d + 0.45d * (RADIUS - r) / 70) * Constants.MAX_PIXEL_VALUE
        }
        def params = withSaturatedDiskMode(true)

        when:
        def result = fit(image, params)
        def center = result.ellipse().center()
        def semiAxis = result.ellipse().semiAxis()

        then:
        Math.abs(center.a() - CENTER) < 2
        Math.abs(center.b() - CENTER) < 2
        Math.abs(semiAxis.a() - RADIUS) < 3
        Math.abs(semiAxis.b() - RADIUS) < 3
    }

    def "fits a normally exposed disk with the default sensitivity"() {
        given:
        def image = limbDarkenedDisk()
        def params = withSaturatedDiskMode(false)

        when:
        def result = fit(image, params)
        def center = result.ellipse().center()
        def semiAxis = result.ellipse().semiAxis()

        then:
        Math.abs(center.a() - CENTER) < 2
        Math.abs(center.b() - CENTER) < 2
        Math.abs(semiAxis.a() - RADIUS) < 4
        Math.abs(semiAxis.b() - RADIUS) < 4
    }

    private static EllipseFittingTask.Result fit(ImageWrapper32 image, ProcessParams params) {
        new EllipseFittingTask(Broadcaster.NO_OP, ProgressOperation.root("test", {}), { image }, params, null).call()
    }

    private static ProcessParams withSaturatedDiskMode(boolean enabled) {
        def defaults = ProcessParamsIO.createNewDefaults()
        defaults.withGeometryParams(defaults.geometryParams().withSaturatedDiskMode(enabled))
    }

    private static boolean fitsPlateau(EllipseFittingTask.Result result) {
        def center = result.ellipse().center()
        def semiAxis = result.ellipse().semiAxis()
        assert Math.abs(center.a() - CENTER) < 2
        assert Math.abs(center.b() - CENTER) < 2
        assert semiAxis.a() < RADIUS + 1 && semiAxis.a() > RADIUS - 5
        assert semiAxis.b() < RADIUS + 1 && semiAxis.b() > RADIUS - 5
        true
    }

    private static double plateauWithHalo(double r, double plateauLevel) {
        if (r <= RADIUS) {
            return plateauLevel * Constants.MAX_PIXEL_VALUE
        }
        return HALO_LEVEL * 0.7d * Constants.MAX_PIXEL_VALUE * Math.exp(-(r - RADIUS) / HALO_SCALE)
    }

    private static ImageWrapper32 saturatedDiskWithHalo(double haloLevel, double haloScale) {
        radialScene { double r ->
            if (r <= RADIUS) {
                return Constants.MAX_PIXEL_VALUE
            }
            return haloLevel * Constants.MAX_PIXEL_VALUE * Math.exp(-(r - RADIUS) / haloScale)
        }
    }

    private static ImageWrapper32 limbDarkenedDisk() {
        radialScene { double r ->
            if (r > RADIUS) {
                return 0.02 * Constants.MAX_PIXEL_VALUE
            }
            def mu = Math.sqrt(1 - (r / RADIUS) * (r / RADIUS))
            return 0.6 * Constants.MAX_PIXEL_VALUE * (0.4 + 0.6 * mu)
        }
    }

    private static ImageWrapper32 radialScene(Closure<Double> profile) {
        scene { double x, double y -> profile.call(Math.hypot(x - CENTER, y - CENTER)) }
    }

    private static ImageWrapper32 scene(Closure<Double> profile) {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) profile.call((double) x, (double) y)
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, MutableMap.of())
    }
}
