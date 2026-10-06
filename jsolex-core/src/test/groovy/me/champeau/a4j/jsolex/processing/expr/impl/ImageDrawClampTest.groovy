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
import me.champeau.a4j.jsolex.processing.util.RGBImage
import spock.lang.Specification

/**
 * Pixels outside the 16-bit range, such as the slight undershoot of a resampling,
 * must be clamped when an image is drawn on, not wrapped around to white.
 */
class ImageDrawClampTest extends Specification {
    private static final int SIZE = 64

    def "out of range pixels are clamped when drawing on a mono image"() {
        given:
        def data = new float[SIZE][SIZE]
        data[40][10] = -20f
        data[40][20] = 70000f
        data[40][30] = 1000f
        def image = new ImageWrapper32(SIZE, SIZE, data, [:])

        when:
        def drawn = (RGBImage) new ImageDraw(Map.of(), Broadcaster.NO_OP, () -> Map.of()).drawText(image, "x", 2, 2, 'FFFFFF', 8)

        then:
        drawn.r()[40][10] == 0f
        drawn.r()[40][20] == 65280f
        drawn.r()[40][30] == 768f
    }

    def "out of range pixels are clamped when drawing on an RGB image"() {
        given:
        def plane = { -> new float[SIZE][SIZE] }
        def r = plane()
        r[40][10] = -20f
        r[40][20] = 70000f
        def image = new RGBImage(SIZE, SIZE, r, plane(), plane(), [:])

        when:
        def drawn = (RGBImage) new ImageDraw(Map.of(), Broadcaster.NO_OP, () -> Map.of()).drawText(image, "x", 2, 2, 'FFFFFF', 8)

        then:
        drawn.r()[40][10] == 0f
        drawn.r()[40][20] == 65280f
    }
}
