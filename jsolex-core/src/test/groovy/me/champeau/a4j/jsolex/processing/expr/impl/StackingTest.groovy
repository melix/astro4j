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

import groovy.transform.CompileStatic
import me.champeau.a4j.jsolex.processing.util.ImageIOUtils
import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.Kernel33
import spock.lang.Specification

import static java.lang.Math.round

class StackingTest extends Specification {
    private static final Image REFERENCE = ImageIOUtils.loadImage("meudon-ref.png")
    private static final TOP = ImageIOUtils.loadImage("mosaic_top.png")
    private static final MIDDLE = ImageIOUtils.loadImage("mosaic_middle.png")

    private static final int WIDTH = REFERENCE.width()
    private static final int HEIGHT = REFERENCE.height()

    def "finds best tile by translation"() {
        var translated = translate(REFERENCE, dx, dy)
        var refX = 1000
        var refY = 400
        var lookup = 32

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds best tile by translation and blur"() {
        var translated = ImageMath.newInstance().convolve(translate(REFERENCE, dx, dy), Kernel33.GAUSSIAN_BLUR)
        var refX = 1000
        var refY = 400
        int lookup = 32

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds best tile by translation and sharpen"() {
        var translated = ImageMath.newInstance().convolve(translate(REFERENCE, dx, dy), Kernel33.SHARPEN)
        var refX = 1000
        var refY = 400
        var lookup = 16

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds correspondance between 2 images"() {

        def width = TOP.width()
        def height = TOP.height()

        when:
        var tiles = Dedistort.createTilesForComparison(tileSize, refX, width, refY, height, TOP.data(), MIDDLE.data(), 0f)
        var best = Dedistort.crossCorrelationShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        verifyAll(best) {
            withinTolerance(refY - a(), expectedY, tolerance)
            withinTolerance(refX - b(), expectedX, tolerance)
        }

        where:
        refX | refY | tileSize | expectedX | expectedY | tolerance
        1030 | 1087 | 16       | 1031      | 1078      | 4
//        1030 | 1087 | 32       | 1031      | 1078      | 3
//        1030 | 1087 | 64       | 1031      | 1078      | 3
//        283  | 1137 | 16       | 285       | 1127      | 3
//        283  | 1137 | 32       | 285       | 1127      | 3
        283  | 1137 | 64       | 285       | 1127      | 3
        682  | 739  | 64       | 684       | 734       | 3
//        1894 | 872  | 16       | 1893      | 864       | 3
        1894 | 872  | 64       | 1893      | 864       | 3
//        480  | 1008 | 16       | 482       | 999       | 3
        480  | 1008 | 32       | 482       | 999       | 3
        480  | 1008 | 64       | 482       | 999       | 3
    }

    private static boolean withinTolerance(double a, double b, double tolerance) {
        Math.abs(a - b) <= tolerance
    }

    @CompileStatic
    private static Image translate(Image image, int dx, int dy) {
        var refW = image.width()
        var refH = image.height()
        var translated = new float[refH][refW]
        def source = image.data()
        for (int x = 0; x < refW; x++) {
            for (int y = 0; y < refH; y++) {
                def xx = x + dx
                def yy = y + dy
                if (xx >= 0 && xx < refW && yy >= 0 && yy < refH) {
                    translated[yy][xx] = source[y][x]
                }
            }
        }
        new Image(refW, refH, translated)
    }
}
