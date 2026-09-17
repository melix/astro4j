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

    def "fits the plateau of a saturated disk surrounded by a halo in saturated disk mode"() {
        given:
        def image = saturatedDiskWithHalo()
        def params = withSaturatedDiskMode(true)

        when:
        def result = fit(image, params)
        def center = result.ellipse().center()
        def semiAxis = result.ellipse().semiAxis()

        then:
        Math.abs(center.a() - CENTER) < 2
        Math.abs(center.b() - CENTER) < 2
        semiAxis.a() < RADIUS + 1 && semiAxis.a() > RADIUS - 5
        semiAxis.b() < RADIUS + 1 && semiAxis.b() > RADIUS - 5
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

    private static ImageWrapper32 saturatedDiskWithHalo() {
        scene { double r ->
            if (r <= RADIUS) {
                return Constants.MAX_PIXEL_VALUE
            }
            return HALO_LEVEL * Constants.MAX_PIXEL_VALUE * Math.exp(-(r - RADIUS) / HALO_SCALE)
        }
    }

    private static ImageWrapper32 limbDarkenedDisk() {
        scene { double r ->
            if (r > RADIUS) {
                return 0.02 * Constants.MAX_PIXEL_VALUE
            }
            def mu = Math.sqrt(1 - (r / RADIUS) * (r / RADIUS))
            return 0.6 * Constants.MAX_PIXEL_VALUE * (0.4 + 0.6 * mu)
        }
    }

    private static ImageWrapper32 scene(Closure<Double> profile) {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def r = Math.hypot(x - CENTER, y - CENTER)
                data[y][x] = (float) profile.call(r)
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, MutableMap.of())
    }
}
