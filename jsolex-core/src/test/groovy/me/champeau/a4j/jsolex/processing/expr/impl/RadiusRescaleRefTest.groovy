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
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

/**
 * {@code radius_rescale2} can be given a reference image instead of explicit numbers,
 * so that an image downloaded from an observatory can be brought to the scale of the
 * observation it is compared to, without touching the observation itself.
 */
class RadiusRescaleRefTest extends Specification {

    private Scaling scaling() {
        var context = new HashMap<Class<?>, Object>()
        new Scaling(context, Broadcaster.NO_OP, new Crop(context, Broadcaster.NO_OP))
    }

    private static Ellipse circle(double cx, double cy, double radius) {
        Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * cx, -2 * cy, cx * cx + cy * cy - radius * radius))
    }

    private static ImageWrapper32 disk(int size, double radius) {
        var data = new float[size][size]
        var c = (size - 1) / 2d
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = ((x - c) * (x - c) + (y - c) * (y - c) <= radius * radius) ? 30000f : 0f
            }
        }
        var metadata = new LinkedHashMap<Class<?>, Object>()
        metadata.put(Ellipse.class, circle(c, c, radius))
        new ImageWrapper32(size, size, data, metadata)
    }

    def "a reference image provides the target radius and dimensions"() {
        given:
        def source = disk(400, 160)
        def reference = disk(600, 200)

        when:
        def result = scaling().radiusRescale2([img: source, ref: reference])

        then: "the result has the dimensions of the reference"
        result.width() == 600
        result.height() == 600

        and: "and its disk has the radius of the reference"
        def semiAxis = result.findMetadata(Ellipse.class).get().semiAxis()
        Math.abs(semiAxis.a() - 200) < 2
        Math.abs(semiAxis.b() - 200) < 2
    }

    def "the reference image is left untouched"() {
        given:
        def source = disk(400, 160)
        def reference = disk(600, 200)

        when:
        scaling().radiusRescale2([img: source, ref: reference])

        then:
        reference.width() == 600
        reference.findMetadata(Ellipse.class).get().semiAxis().a() == 200
    }

    def "explicit values still win over the reference"() {
        given:
        def source = disk(400, 160)
        def reference = disk(600, 200)

        when:
        def result = scaling().radiusRescale2([img: source, ref: reference, width: 256, height: 256])

        then:
        result.width() == 256
        result.height() == 256
        Math.abs(result.findMetadata(Ellipse.class).get().semiAxis().a() - 200) < 2
    }

    def "a missing radius is reported"() {
        when:
        scaling().radiusRescale2([img: disk(400, 160), width: 256, height: 256])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('radius')
    }
}
