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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.MutableMap
import me.champeau.a4j.math.regression.Ellipse
import spock.lang.Specification

class LimbCorrectionTest extends Specification {
    private static final int SIZE = 512
    private static final double RADIUS = 160
    private static final double CENTER = SIZE / 2.0d
    private static final float DISK = 40000f
    private static final float SKY = 500f
    private static final float LEVEL = 8000f
    private static final int ANGLES = 72

    private LimbCorrection correction() {
        def context = new HashMap<Class<?>, Object>()
        new LimbCorrection(context, Broadcaster.NO_OP, new EllipseFit(context, Broadcaster.NO_OP))
    }

    def "a deformed limb is brought back onto a circle"() {
        given:
        def deformed = disk(CENTER, CENTER) { double angle -> 3 * Math.cos(2 * angle) + 2 * Math.sin(3 * angle) }

        when:
        def corrected = (ImageWrapper32) correction().correctLimb(Map.of("img", deformed))

        then: "the limb was not round and becomes round"
        limbSpread(deformed) > 2
        limbSpread(corrected) < 0.6

        and: "the image carries the circle it was brought onto"
        def ellipse = corrected.findMetadata(Ellipse).get()
        Math.abs(ellipse.center().a() - CENTER) < 0.5
        Math.abs(ellipse.center().b() - CENTER) < 0.5
        Math.abs(ellipse.semiAxis().a() - RADIUS) < 1
        Math.abs(ellipse.semiAxis().b() - RADIUS) < 1
    }

    def "a series of images is aligned on a common circle"() {
        given:
        def shifted = [disk(CENTER + 3, CENTER), disk(CENTER, CENTER - 2), disk(CENTER - 3, CENTER + 2)]

        when:
        def aligned = ((List<ImageWrapper>) correction().correctLimb(Map.of("img", shifted))).collect { (ImageWrapper32) it.unwrapToMemory() }
        def centers = aligned.collect { centroid(it) }

        then:
        centers.every { Math.abs(it[0] - CENTER) < 0.4 && Math.abs(it[1] - CENTER) < 0.4 }
        aligned.collect { it.findMetadata(Ellipse).get().center() }.toSet().size() == 1
    }

    def "an image without a disk is left unchanged"() {
        given:
        def flat = new ImageWrapper32(SIZE, SIZE, new float[SIZE][SIZE], MutableMap.of())

        when:
        def result = correction().correctLimb(Map.of("img", flat))

        then:
        result.is(flat)
    }

    private static ImageWrapper32 disk(double cx, double cy) {
        disk(cx, cy) { double angle -> 0d }
    }

    private static ImageWrapper32 disk(double cx, double cy, Closure<Double> deviation) {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def r = Math.hypot(x - cx, y - cy)
                def limb = RADIUS + deviation.call(Math.atan2(y - cy, x - cx))
                if (r < limb) {
                    def mu = Math.sqrt(Math.max(0, 1 - (r / limb) * (r / limb)))
                    data[y][x] = (float) (DISK * (0.4 + 0.6 * mu))
                } else {
                    data[y][x] = SKY
                }
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, MutableMap.of())
    }

    /** Standard deviation of the limb radius measured around the true center. */
    private static double limbSpread(ImageWrapper32 image) {
        def radii = []
        for (int k = 0; k < ANGLES; k++) {
            def angle = 2 * Math.PI * k / ANGLES
            def previous = Double.NaN
            for (double r = RADIUS + 20; r > RADIUS - 20; r -= 0.25) {
                def value = sample(image, CENTER + r * Math.cos(angle), CENTER + r * Math.sin(angle))
                if (value >= LEVEL) {
                    radii << r
                    break
                }
                previous = value
            }
        }
        def mean = radii.sum() / radii.size()
        Math.sqrt(radii.collect { (it - mean) * (it - mean) }.sum() / radii.size())
    }

    private static double[] centroid(ImageWrapper32 image) {
        double sx = 0, sy = 0, n = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (image.data()[y][x] >= LEVEL) {
                    sx += x
                    sy += y
                    n++
                }
            }
        }
        [sx / n, sy / n] as double[]
    }

    private static double sample(ImageWrapper32 image, double x, double y) {
        def x0 = (int) Math.floor(x)
        def y0 = (int) Math.floor(y)
        def fx = x - x0
        def fy = y - y0
        def data = image.data()
        def top = data[y0][x0] * (1 - fx) + data[y0][x0 + 1] * fx
        def bottom = data[y0 + 1][x0] * (1 - fx) + data[y0 + 1][x0 + 1] * fx
        top * (1 - fy) + bottom * fy
    }
}
