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
 * A negative coordinate given to {@code draw_text} is counted from the opposite edge,
 * and the text is aligned against that edge so that it stays inside the image.
 */
class DrawTextAnchorTest extends Specification {

    private static final int WIDTH = 400
    private static final int HEIGHT = 300

    private ImageDraw imageDraw() {
        new ImageDraw(Map.of(), Broadcaster.NO_OP, () -> Map.of())
    }

    private static ImageWrapper32 blank() {
        new ImageWrapper32(WIDTH, HEIGHT, new float[HEIGHT][WIDTH], [:])
    }

    /** Bounding box of the pixels which were painted, as [minX, minY, maxX, maxY]. */
    private static List<Integer> inkBounds(RGBImage image) {
        var minX = WIDTH, minY = HEIGHT, maxX = -1, maxY = -1
        var data = image.r()
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (data[y][x] > 0) {
                    minX = Math.min(minX, x)
                    minY = Math.min(minY, y)
                    maxX = Math.max(maxX, x)
                    maxY = Math.max(maxY, y)
                }
            }
        }
        [minX, minY, maxX, maxY]
    }

    private List<Integer> draw(int x, int y, String text) {
        inkBounds((RGBImage) imageDraw().drawText(blank(), text, x, y, 'FFFFFF', 20))
    }

    def "a positive coordinate anchors to the top left"() {
        when:
        def (minX, minY, maxX, maxY) = draw(20, 40, "Hello")

        then: "the text starts at x and sits above the baseline y"
        minX >= 20
        minX < 40
        maxY <= 40
        minY > 40 - 30
    }

    def "a negative y anchors the text to the bottom"() {
        when:
        def (minX, minY, maxX, maxY) = draw(20, -20, "Hello")

        then: "the bottom of the text is about 20px above the bottom edge"
        maxY < HEIGHT - 20
        maxY > HEIGHT - 20 - 12

        and: "it is still left aligned"
        minX >= 20
        minX < 40
    }

    def "a negative x anchors the text to the right"() {
        when:
        def (minX, minY, maxX, maxY) = draw(-20, 40, "Hello")

        then: "the right end of the text is about 20px from the right edge"
        maxX <= WIDTH - 20
        maxX > WIDTH - 20 - 12
    }

    def "a multiline block anchored to the bottom stays inside the image"() {
        when:
        def (minX, minY, maxX, maxY) = draw(20, -20, "first\nsecond\nthird")

        then:
        minY >= 0
        maxY < HEIGHT - 20

        and: "it is taller than a single line, so all three lines were drawn"
        (maxY - minY) > 40
    }

    def "a multiline block anchored to the bottom would overflow without the shift"() {
        given: "the same block anchored from the top at the same distance"
        def fromBottom = draw(20, -20, "first\nsecond\nthird")
        def naive = draw(20, HEIGHT - 20, "first\nsecond\nthird")

        expect: "anchoring from the bottom keeps everything visible"
        fromBottom[3] < HEIGHT

        and: "whereas a naive top anchor at the same place loses lines"
        (naive[3] - naive[1]) < (fromBottom[3] - fromBottom[1])
    }

    def "both coordinates negative anchors to the bottom right"() {
        when:
        def (minX, minY, maxX, maxY) = draw(-20, -20, "Hello")

        then:
        maxX <= WIDTH - 20
        maxX > WIDTH - 20 - 12
        maxY < HEIGHT - 20
        maxY > HEIGHT - 20 - 12
    }
}
