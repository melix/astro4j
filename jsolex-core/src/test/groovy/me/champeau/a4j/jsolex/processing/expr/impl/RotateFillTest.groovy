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
import spock.lang.Specification

class RotateFillTest extends Specification {

    private final Rotate rotate = new Rotate([:], Broadcaster.NO_OP)

    private static ImageWrapper32 image(float background, float centre) {
        int size = 128
        float[][] data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = (x > 40 && x < 88 && y > 40 && y < 88) ? centre : background
            }
        }
        return new ImageWrapper32(size, size, data, [:])
    }

    def "the corners left by a rotation take the background level of the image"() {
        given: "an image whose background is far from black"
        var source = image(6900f, 30000f)

        when: "it is rotated without specifying a fill value"
        var result = (ImageWrapper32) rotate.rotateDegrees([img: source, angle: 30])

        then: "the corners are filled with the background instead of black"
        var corner = result.data()[0][0]
        corner == 6900f
    }

    def "an explicit fill value still wins"() {
        given:
        var source = image(6900f, 30000f)

        when:
        var result = (ImageWrapper32) rotate.rotateDegrees([img: source, angle: 30, bp: 0])

        then:
        result.data()[0][0] == 0f
    }

    def "an image with a black background is still filled with black"() {
        given:
        var source = image(0f, 30000f)

        when:
        var result = (ImageWrapper32) rotate.rotateDegrees([img: source, angle: 30])

        then:
        result.data()[0][0] == 0f
    }
}
