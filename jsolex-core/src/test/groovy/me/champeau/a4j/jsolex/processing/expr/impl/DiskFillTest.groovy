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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class DiskFillTest extends Specification {
    private static final int SIZE = 128
    private static final double CENTER = 64
    private static final double RADIUS = 40

    private final DiskFill diskFill = new DiskFill([:], Broadcaster.NO_OP)

    def "fills the disk"() {
        when:
        var result = (ImageWrapper32) diskFill.fill([img: image(), fill: 0])

        then:
        valueAt(result, RADIUS - 2) == 0f
        valueAt(result, RADIUS + 2) == 1000f
    }

    def "a radius larger than 1 also fills a band around the disk"() {
        when:
        var result = (ImageWrapper32) diskFill.fill([img: image(), fill: 0, radius: 1.1])

        then:
        valueAt(result, RADIUS + 2) == 0f
        valueAt(result, RADIUS * 1.1 + 2) == 1000f

        and: "the image still describes the same disk"
        Math.abs(result.findMetadata(Ellipse).get().semiAxis().a() - RADIUS) < 1e-6
    }

    private static float valueAt(ImageWrapper32 image, double distance) {
        image.data()[(int) CENTER][(int) Math.round(CENTER + distance)]
    }

    private static ImageWrapper32 image() {
        var data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            Arrays.fill(data[y], 1000f)
        }
        var ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * CENTER, -2 * CENTER, 2 * CENTER * CENTER - RADIUS * RADIUS))
        new ImageWrapper32(SIZE, SIZE, data, [(Ellipse): ellipse])
    }
}
