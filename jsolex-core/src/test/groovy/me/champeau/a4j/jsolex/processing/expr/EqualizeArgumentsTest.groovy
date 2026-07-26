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
package me.champeau.a4j.jsolex.processing.expr

import me.champeau.a4j.jsolex.expr.BuiltinFunction
import me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

/**
 * Whether the images are wrapped in a single list argument, as a positional call produces, or
 * passed directly, as a named call does, equalize must accept them.
 */
class EqualizeArgumentsTest extends Specification {

    @Subject
    AdjustContrast adjustContrast = new AdjustContrast([:], Broadcaster.NO_OP)

    def "accepts images wrapped in a single list argument"() {
        when:
        var result = adjustContrast.equalize(["list": [images()]])

        then:
        result.size() == 2
    }

    def "accepts images passed directly"() {
        when:
        var result = adjustContrast.equalize(["list": images()])

        then:
        result.size() == 2
    }

    def "both forms produce the same result"() {
        when:
        var wrapped = adjustContrast.equalize(["list": [images()]])
        var direct = adjustContrast.equalize(["list": images()])

        then:
        pixel(wrapped[0]) == pixel(direct[0])
        pixel(wrapped[1]) == pixel(direct[1])
    }

    def "a single image is returned untouched"() {
        expect:
        adjustContrast.equalize(["list": [[image(1000f)]]]).size() == 1
    }

    def "rejects arguments which are not images"() {
        when:
        adjustContrast.equalize(["list": [1, 2]])

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects an empty argument list"() {
        when:
        adjustContrast.equalize(["list": []])

        then:
        thrown(IllegalArgumentException)
    }

    def "a positional call collects the images into the spread list"() {
        expect: "which is how equalize(some_images) reaches the function"
        BuiltinFunction.EQUALIZE.mapPositionalArguments([[1, 2, 3]]) == ["list": [[1, 2, 3]]]
    }

    private static List<ImageWrapper32> images() {
        [image(1000f), image(30000f)]
    }

    private static ImageWrapper32 image(float value) {
        float[][] data = new float[4][4]
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                data[y][x] = value + 100 * x
            }
        }
        new ImageWrapper32(4, 4, data, [:])
    }

    private static float pixel(Object img) {
        ((ImageWrapper32) ((ImageWrapper) img).unwrapToMemory()).data()[0][0]
    }
}
