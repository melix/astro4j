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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class DeghostTest extends Specification {

    static final int SIZE = 400
    static final double CX = 200
    static final double CY = 200
    static final double R = 120
    // the reflection is a same-radius disk shifted to the right
    static final double OX = CX + 30
    static final double OY = CY
    static final float GHOST = 800

    private static double background(double x, double y) {
        double r = Math.hypot(x - CX, y - CY)
        return 100 + 1500 * Math.exp(-Math.max(0, r - R) / 30.0)
    }

    // reflection amplitude at a point: constant inside the ghost disk, smoothly fading to 0 at its edge
    private static double ghostAt(double x, double y) {
        double t = (R - Math.hypot(x - OX, y - OY)) / (0.08 * R)
        t = Math.max(0, Math.min(1, t))
        return GHOST * t * t * (3 - 2 * t)
    }

    private static Ellipse diskEllipse() {
        double a = 1.0 / (R * R), c = 1.0 / (R * R), d = -2.0 * CX / (R * R), e = -2.0 * CY / (R * R)
        double f = CX * CX / (R * R) + CY * CY / (R * R) - 1.0
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private ImageWrapper32 syntheticImage() {
        float[][] data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double v = background(x, y)
                if (Math.hypot(x - CX, y - CY) > R) {
                    v += ghostAt(x, y)
                }
                // a sharp high-frequency feature sitting inside the reflection band
                v += 300 * Math.exp(-((x - 330) * (x - 330) + (y - 215) * (y - 215)) / (2 * 4))
                data[y][x] = (float) v
            }
        }
        def meta = new HashMap<Class<?>, Object>()
        meta.put(Ellipse.class, diskEllipse())
        return new ImageWrapper32(SIZE, SIZE, data, meta)
    }

    private ImageWrapper32 backgroundImage() {
        float[][] data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) background(x, y)
            }
        }
        def meta = new HashMap<Class<?>, Object>()
        meta.put(Ellipse.class, diskEllipse())
        return new ImageWrapper32(SIZE, SIZE, data, meta)
    }

    // reflection side (326,200) minus its clean mirror (74,200), both at radius 126
    private static double crescentExcess(float[][] data) {
        return data[200][326] - data[200][74]
    }

    private static Deghost deghost() {
        new Deghost([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    def "attenuates the reflection while preserving the sharp feature and untouched regions"() {
        given:
        def before = syntheticImage().data()

        when:
        def after = (deghost().deghost([img: syntheticImage()]) as ImageWrapper32).data()

        then: "the reflection band is strongly attenuated"
        double excessBefore = crescentExcess(before)
        double excessAfter = crescentExcess(after)
        excessBefore > 600
        excessAfter < 0.4 * excessBefore

        and: "the sharp feature remains a local peak (fine detail preserved)"
        after[215][330] > after[209][330] + 80
        after[215][330] > after[221][330] + 80

        and: "no reflection-side pixel is dug catastrophically below the background (no hard dark ring)"
        // the synthetic ghost has sharp azimuthal edges the smoothing over-shoots slightly; a real ring is thousands deep
        double worst = Double.POSITIVE_INFINITY
        for (int y = 40; y < SIZE - 40; y++) {
            for (int x = (int) CX; x < SIZE; x++) {
                double r = Math.hypot(x - CX, y - CY)
                if (r > R && r < 1.3 * R) {
                    worst = Math.min(worst, after[y][x] - background(x, y))
                }
            }
        }
        worst > -0.35 * GHOST

        and: "the anti-reflection side and the interior are left untouched"
        Math.abs(after[200][74] - before[200][74]) < 1e-3
        Math.abs(after[100][100] - before[100][100]) < 1e-3
    }

    def "strength 0 is a no-op"() {
        given:
        def before = syntheticImage().data()

        when:
        def after = (deghost().deghost([img: syntheticImage(), strength: 0d]) as ImageWrapper32).data()

        then:
        Math.abs(after[200][335] - before[200][335]) < 1e-3
    }

    def "more iterations attenuate the reflection further"() {
        given:
        double before = crescentExcess(syntheticImage().data())

        when:
        double one = crescentExcess((deghost().deghost([img: syntheticImage(), strength: 0.5d, iterations: 1]) as ImageWrapper32).data())
        double three = crescentExcess((deghost().deghost([img: syntheticImage(), strength: 0.5d, iterations: 3]) as ImageWrapper32).data())

        then:
        one < before
        three < one
        three > 0
    }

    def "no reflection leaves the image unchanged"() {
        given:
        def before = backgroundImage().data()

        when:
        def after = (deghost().deghost([img: backgroundImage()]) as ImageWrapper32).data()

        then:
        Math.abs(after[200][326] - before[200][326]) < 1e-3
        Math.abs(after[100][100] - before[100][100]) < 1e-3
    }

    def "debug shows the estimated reflection instead of subtracting it"() {
        when:
        def dbg = (deghost().deghost([img: syntheticImage(), debug: 1]) as ImageWrapper32).data()

        then: "the reflection side carries the estimate, the anti-reflection side is zero"
        dbg[200][326] > 300
        dbg[200][74] < 1e-3
    }

    def "handles a disk large relative to the frame (mirror sampling off-image) without crashing"() {
        given: "a disk whose 1.35R sampling radius runs off the left edge (mirror point x < 0)"
        int size = 300
        double r = 130, cx = 150, cy = 150
        float[][] data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dist = Math.hypot(x - cx, y - cy)
                data[y][x] = (float) (1000 + (dist > r ? 400 * Math.exp(-(dist - r) / 20.0) : 0))
            }
        }
        double a = 1.0 / (r * r), c = 1.0 / (r * r), d = -2.0 * cx / (r * r), e = -2.0 * cy / (r * r)
        double f = cx * cx / (r * r) + cy * cy / (r * r) - 1.0
        def meta = new HashMap<Class<?>, Object>()
        meta.put(Ellipse.class, Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f)))
        def img = new ImageWrapper32(size, size, data, meta)

        when:
        def result = deghost().deghost([img: img])

        then:
        noExceptionThrown()
        result instanceof ImageWrapper32
    }

    def "rejects RGB images"() {
        given:
        def plane = new float[SIZE][SIZE]
        def rgb = new RGBImage(SIZE, SIZE, plane, plane, plane, new HashMap<Class<?>, Object>())

        when:
        deghost().deghost([img: rgb])

        then:
        thrown(IllegalArgumentException)
    }
}
