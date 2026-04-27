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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

class RotateTest extends Specification {

    private static final int W = 101
    private static final int H = 101
    private static final int CX = 50
    private static final int CY = 50
    // bright pixel at (CX + 25, CY) -> 25 px right of center
    private static final int SRC_X = 75
    private static final int SRC_Y = 50

    @Subject
    Rotate rotate = new Rotate([:], Broadcaster.NO_OP)

    private static ImageWrapper32 createTestImage() {
        var data = new float[H][W]
        data[SRC_Y][SRC_X] = 1000f
        return new ImageWrapper32(W, H, data, [:])
    }

    private static int[] findBrightest(ImageWrapper32 img) {
        int bx = -1
        int by = -1
        float bv = -1f
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                var v = img.data()[y][x]
                if (v > bv) {
                    bv = v
                    bx = x
                    by = y
                }
            }
        }
        return [bx, by] as int[]
    }

    private static ImageWrapper32 unwrap(Object o) {
        return (ImageWrapper32) ((FileBackedImage) o).unwrapToMemory()
    }

    def "rotate_deg of a single image rotates by the specified degrees"() {
        when:
        var result = (ImageWrapper32) rotate.rotateDegrees([img: createTestImage(), angle: 180.0d])

        then:
        // 180 degrees: (75, 50) -> mirror across center -> (25, 50)
        var b = findBrightest(result)
        b[0] == 25
        b[1] == 50
    }

    def "rotate_deg of a list rotates each image by the specified degrees"() {
        when:
        var result = (List) rotate.rotateDegrees([img: [createTestImage(), createTestImage()], angle: 180.0d])

        then:
        result.size() == 2
        for (var item : result) {
            var img = unwrap(item)
            var b = findBrightest(img)
            assert b[0] == 25
            assert b[1] == 50
        }
    }

    def "rotate_left of a single image rotates 90 degrees counter-clockwise"() {
        when:
        var result = (ImageWrapper32) rotate.rotateLeft([img: createTestImage()])

        then:
        // -90 degrees: (75, 50) at offset (25, 0) -> offset (0, -25) -> (50, 25)
        var b = findBrightest(result)
        b[0] == 50
        b[1] == 25
    }

    def "rotate_left of a list rotates each image 90 degrees counter-clockwise"() {
        when:
        var result = (List) rotate.rotateLeft([img: [createTestImage(), createTestImage()]])

        then:
        result.size() == 2
        for (var item : result) {
            var img = unwrap(item)
            var b = findBrightest(img)
            assert b[0] == 50
            assert b[1] == 25
        }
    }

    def "rotate_right of a single image rotates 90 degrees clockwise"() {
        when:
        var result = (ImageWrapper32) rotate.rotateRight([img: createTestImage()])

        then:
        // +90 degrees: (75, 50) at offset (25, 0) -> offset (0, 25) -> (50, 75)
        var b = findBrightest(result)
        b[0] == 50
        b[1] == 75
    }

    def "rotate_right of a list rotates each image 90 degrees clockwise"() {
        when:
        var result = (List) rotate.rotateRight([img: [createTestImage(), createTestImage()]])

        then:
        result.size() == 2
        for (var item : result) {
            var img = unwrap(item)
            var b = findBrightest(img)
            assert b[0] == 50
            assert b[1] == 75
        }
    }
}
