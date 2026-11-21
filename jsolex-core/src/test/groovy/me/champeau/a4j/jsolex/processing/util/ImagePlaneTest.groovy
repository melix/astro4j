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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.math.image.ImageMemory
import me.champeau.a4j.math.image.ImagePlane
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ImagePlaneTest extends Specification {

    def "a new plane of #width x #height is filled with zeros"() {
        when:
        def plane = ImagePlane.allocate(width, height)

        then:
        plane.width() == width
        plane.height() == height
        plane.toArray().every { row -> row.every { it == 0f } }

        where:
        width | height
        16    | 8
        1024  | 1024
    }

    def "pixels are addressed by column, then row"() {
        given:
        def plane = ImagePlane.allocate(5, 3)

        when:
        plane.set(4, 1, 7f)

        then:
        plane.get(4, 1) == 7f
        plane.toArray()[1][4] == 7f
        plane.toArray()[4 % 3][1] == 0f
    }

    def "arrays given to a plane or obtained from it are copies"() {
        given:
        def data = gradient(4, 3)
        def plane = ImagePlane.of(4, 3, data)

        when:
        data[0][0] = 1000f
        def copy = plane.toArray()
        copy[1][1] = 2000f

        then:
        plane.get(0, 0) == 0f
        plane.get(1, 1) == gradient(4, 3)[1][1]
    }

    def "copies of a plane are independent"() {
        given:
        def plane = ImagePlane.of(4, 3, gradient(4, 3))

        when:
        def copy = plane.copy()
        copy.set(2, 2, -1f)

        then:
        copy.toArray()[2][2] == -1f
        plane.toArray() == gradient(4, 3)
    }

    def "parts of rows can be read and written"() {
        given:
        def plane = ImagePlane.of(6, 2, gradient(6, 2))
        def source = [9f, 8f, 7f, 6f] as float[]
        def target = new float[5]

        when:
        plane.setRow(1, 2, source, 1, 3)
        plane.getRow(1, 1, target, 2, 3)

        then:
        plane.toArray()[1] == [gradient(6, 2)[1][0], gradient(6, 2)[1][1], 8f, 7f, 6f, gradient(6, 2)[1][5]] as float[]
        target == [0f, 0f, gradient(6, 2)[1][1], 8f, 7f] as float[]
    }

    def "rows outside of the plane cannot be accessed"() {
        given:
        def plane = ImagePlane.allocate(4, 3)

        when:
        plane.get(x, y)

        then:
        thrown(IndexOutOfBoundsException)

        where:
        x  | y
        4  | 0
        -1 | 0
        0  | 3
    }

    def "copying pixels of different dimensions fails"() {
        given:
        def plane = ImagePlane.allocate(4, 3)

        when:
        plane.copyFrom(source)

        then:
        thrown(IllegalArgumentException)

        where:
        source << [new float[3][5], new float[2][4], ImagePlane.allocate(4, 2)]
    }

    def "row operations visit every row"() {
        given:
        def plane = ImagePlane.of(300, 200, gradient(300, 200))

        when:
        plane.updateRows { y, row ->
            for (int x = 0; x < row.length; x++) {
                row[x] = row[x] * 2 + y
            }
        }
        def mapped = plane.mapRows { y, row -> Arrays.fill(row, (float) y) }

        then:
        plane.get(10, 20) == gradient(300, 200)[20][10] * 2 + 20
        mapped.get(299, 199) == 199f
        mapped.width() == 300
        mapped.height() == 200
        plane.min() == 0
        plane.max() == gradient(300, 200)[199][299] * 2 + 199
    }

    def "the memory of images which are no longer used is given back"() {
        given:
        def conditions = new PollingConditions(timeout: 30, delay: 0.2)
        def before = ImageMemory.used()
        def plane = ImagePlane.allocate(4096, 2048)

        expect:
        ImageMemory.used() >= before + 4096L * 2048 * Float.BYTES

        when:
        plane = null

        then:
        conditions.eventually {
            System.gc()
            assert ImageMemory.used() < before + 4096L * 2048 * Float.BYTES
        }
    }

    private static float[][] gradient(int width, int height) {
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = y * width + x
            }
        }
        data
    }
}
