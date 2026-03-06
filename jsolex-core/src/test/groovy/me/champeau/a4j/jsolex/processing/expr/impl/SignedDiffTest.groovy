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
import spock.lang.Specification
import spock.lang.Subject

class SignedDiffTest extends Specification {

    @Subject
    SignedDiff signedDiff = new SignedDiff([:], Broadcaster.NO_OP)

    private static ImageWrapper32 createImage(int width, int height, float value) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }

    private static RGBImage createRGBImage(int width, int height, float r, float g, float b) {
        float[][] rd = new float[height][width]
        float[][] gd = new float[height][width]
        float[][] bd = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rd[y][x] = r
                gd[y][x] = g
                bd[y][x] = b
            }
        }
        new RGBImage(width, height, rd, gd, bd, [:])
    }

    def "mono - mono preserves negative values"() {
        given:
        def a = createImage(2, 2, 100.0f)
        def b = createImage(2, 2, 200.0f)

        when:
        ImageWrapper32 result = signedDiff.signedDiff([a: a, b: b]) as ImageWrapper32

        then:
        result.data()[0][0] == -100.0f
        result.data()[1][1] == -100.0f
    }

    def "rgb - rgb per-channel signed diff"() {
        given:
        def a = createRGBImage(2, 2, 100.0f, 200.0f, 50.0f)
        def b = createRGBImage(2, 2, 150.0f, 100.0f, 50.0f)

        when:
        RGBImage result = signedDiff.signedDiff([a: a, b: b]) as RGBImage

        then:
        result.r()[0][0] == -50.0f
        result.g()[0][0] == 100.0f
        result.b()[0][0] == 0.0f
    }

    def "mono - scalar preserves negative values"() {
        given:
        def img = createImage(2, 2, 50.0f)

        when:
        ImageWrapper32 result = signedDiff.signedDiff([a: img, b: 100.0d]) as ImageWrapper32

        then:
        result.data()[0][0] == -50.0f
    }

    def "scalar - mono correct operand order"() {
        given:
        def img = createImage(2, 2, 200.0f)

        when:
        ImageWrapper32 result = signedDiff.signedDiff([a: 5.0d, b: img]) as ImageWrapper32

        then:
        result.data()[0][0] == -195.0f
    }

    def "scalar - scalar returns double"() {
        when:
        def result = signedDiff.signedDiff([a: 10.0d, b: 3.0d])

        then:
        result == 7.0d
    }

    def "mismatched dimensions throws"() {
        given:
        def a = createImage(2, 2, 100.0f)
        def b = createImage(3, 3, 200.0f)

        when:
        signedDiff.signedDiff([a: a, b: b])

        then:
        thrown(IllegalArgumentException)
    }

    def "mixed RGB and mono throws"() {
        given:
        def mono = createImage(2, 2, 100.0f)
        def rgb = createRGBImage(2, 2, 100.0f, 100.0f, 100.0f)

        when:
        signedDiff.signedDiff([a: mono, b: rgb])

        then:
        thrown(IllegalArgumentException)
    }
}
