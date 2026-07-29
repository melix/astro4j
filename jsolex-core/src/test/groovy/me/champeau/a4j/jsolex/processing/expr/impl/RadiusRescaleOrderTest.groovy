/*
 * Copyright 2023-2026 the original author or authors.
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

class RadiusRescaleOrderTest extends Specification {

    Scaling scaling = new Scaling([:], Broadcaster.NO_OP, new Crop([:], Broadcaster.NO_OP))

    /** Circle of the given radius centred in a 200x200 frame, as cartesian conic coefficients. */
    private static Ellipse circle(double radius) {
        double cx = 100, cy = 100
        // (x-cx)^2 + (y-cy)^2 - r^2 = 0
        Ellipse.ofCartesian(new DoubleSextuplet(
                1d, 0d, 1d,
                -2 * cx, -2 * cy,
                cx * cx + cy * cy - radius * radius))
    }

    /** Each image is tagged by a unique constant pixel value so its identity survives rescaling. */
    private static ImageWrapper32 tagged(int tag, double radius) {
        float[][] data = new float[200][200]
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                data[y][x] = (float) tag
            }
        }
        new ImageWrapper32(200, 200, data, [(Ellipse): circle(radius)])
    }

    def "radius_rescale preserves the order of its input list"() {
        given: "images whose radii are deliberately not in list order"
        def radii = [70d, 55d, 90d, 60d, 85d, 50d, 75d, 65d, 95d, 58d, 80d, 62d]
        def images = (0..<radii.size()).collect { tagged(it, radii[it]) }

        when:
        def result = scaling.performRadiusRescale(images)

        then: "the tag of each output must still be its input position"
        result.size() == images.size()
        def tags = result.collect { (int) Math.round(it.unwrapToMemory().data()[0][0]) }
        println "input  order = ${(0..<radii.size()).toList()}"
        println "output order = $tags"
        tags == (0..<radii.size()).toList()
    }
}
