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
import spock.lang.Subject

class ConditionalsTest extends Specification {

    @Subject
    Conditionals conditionals = new Conditionals([:], Broadcaster.NO_OP)

    // Helper to create a simple test image with uniform value
    private static ImageWrapper32 createImage(int width, int height, float value) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }

    // Helper to create an image with varying values (0, 1, 2, 3, ...)
    private static ImageWrapper32 createImageWithPattern(int width, int height) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (x + y * width)
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }

    // ==================== IFEQ Tests ====================

    def "ifeq returns then value when scalars are equal"() {
        when:
        def result = conditionals.ifeq([subject: 5.0, value: 5.0, then: "yes", else: "no"])

        then:
        result == "yes"
    }

    def "ifeq returns else value when scalars are not equal"() {
        when:
        def result = conditionals.ifeq([subject: 5.0, value: 3.0, then: "yes", else: "no"])

        then:
        result == "no"
    }

    def "ifeq works with string comparison"() {
        when:
        def resultEqual = conditionals.ifeq([subject: "hello", value: "hello", then: 1, else: 0])
        def resultNotEqual = conditionals.ifeq([subject: "hello", value: "world", then: 1, else: 0])

        then:
        resultEqual == 1
        resultNotEqual == 0
    }

    def "ifeq with uniform images returns then when all pixels equal"() {
        given:
        def subject = createImage(2, 2, 10.0f)
        def value = createImage(2, 2, 10.0f)
        def thenImg = createImage(2, 2, 100.0f)
        def elseImg = createImage(2, 2, 0.0f)

        when:
        def result = conditionals.ifeq([subject: subject, value: value, then: thenImg, else: elseImg])

        then:
        result.is(thenImg)
    }

    def "ifeq with different images returns else"() {
        given:
        def subject = createImage(2, 2, 10.0f)
        def value = createImage(2, 2, 5.0f)
        def thenImg = createImage(2, 2, 100.0f)
        def elseImg = createImage(2, 2, 0.0f)

        when:
        def result = conditionals.ifeq([subject: subject, value: value, then: thenImg, else: elseImg])

        then:
        result.is(elseImg)
    }

    // ==================== IFNEQ Tests ====================

    def "ifneq returns then value when scalars are not equal"() {
        when:
        def result = conditionals.ifneq([subject: 5.0, value: 3.0, then: "yes", else: "no"])

        then:
        result == "yes"
    }

    def "ifneq returns else value when scalars are equal"() {
        when:
        def result = conditionals.ifneq([subject: 5.0, value: 5.0, then: "yes", else: "no"])

        then:
        result == "no"
    }

    def "ifneq works with string comparison"() {
        when:
        def resultNotEqual = conditionals.ifneq([subject: "hello", value: "world", then: 1, else: 0])
        def resultEqual = conditionals.ifneq([subject: "hello", value: "hello", then: 1, else: 0])

        then:
        resultNotEqual == 1
        resultEqual == 0
    }

    // ==================== IFGT Tests ====================

    def "ifgt returns then value when subject > value"() {
        when:
        def result = conditionals.ifgt([subject: 10.0, value: 5.0, then: "greater", else: "not greater"])

        then:
        result == "greater"
    }

    def "ifgt returns else value when subject <= value"() {
        when:
        def resultEqual = conditionals.ifgt([subject: 5.0, value: 5.0, then: "greater", else: "not greater"])
        def resultLess = conditionals.ifgt([subject: 3.0, value: 5.0, then: "greater", else: "not greater"])

        then:
        resultEqual == "not greater"
        resultLess == "not greater"
    }

    def "ifgt with image where all pixels > scalar returns then"() {
        given:
        def subject = createImage(2, 2, 10.0f)

        when:
        def result = conditionals.ifgt([subject: subject, value: 5.0, then: "yes", else: "no"])

        then:
        result == "yes"
    }

    def "ifgt with image where not all pixels > scalar returns else"() {
        given:
        def subject = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = conditionals.ifgt([subject: subject, value: 1.5, then: "yes", else: "no"])

        then:
        result == "no"  // 0 and 1 are not > 1.5
    }

    // ==================== IFGTE Tests ====================

    def "ifgte returns then value when subject >= value"() {
        when:
        def resultGreater = conditionals.ifgte([subject: 10.0, value: 5.0, then: "yes", else: "no"])
        def resultEqual = conditionals.ifgte([subject: 5.0, value: 5.0, then: "yes", else: "no"])

        then:
        resultGreater == "yes"
        resultEqual == "yes"
    }

    def "ifgte returns else value when subject < value"() {
        when:
        def result = conditionals.ifgte([subject: 3.0, value: 5.0, then: "yes", else: "no"])

        then:
        result == "no"
    }

    def "ifgte with image where all pixels >= scalar returns then"() {
        given:
        def subject = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = conditionals.ifgte([subject: subject, value: 0.0, then: "yes", else: "no"])

        then:
        result == "yes"  // all pixels >= 0
    }

    // ==================== IFLT Tests ====================

    def "iflt returns then value when subject < value"() {
        when:
        def result = conditionals.iflt([subject: 3.0, value: 5.0, then: "less", else: "not less"])

        then:
        result == "less"
    }

    def "iflt returns else value when subject >= value"() {
        when:
        def resultEqual = conditionals.iflt([subject: 5.0, value: 5.0, then: "less", else: "not less"])
        def resultGreater = conditionals.iflt([subject: 10.0, value: 5.0, then: "less", else: "not less"])

        then:
        resultEqual == "not less"
        resultGreater == "not less"
    }

    def "iflt with image where all pixels < scalar returns then"() {
        given:
        def subject = createImage(2, 2, 3.0f)

        when:
        def result = conditionals.iflt([subject: subject, value: 5.0, then: "yes", else: "no"])

        then:
        result == "yes"
    }

    def "iflt with image where not all pixels < scalar returns else"() {
        given:
        def subject = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = conditionals.iflt([subject: subject, value: 2.0, then: "yes", else: "no"])

        then:
        result == "no"  // 2 and 3 are not < 2
    }

    // ==================== IFLTE Tests ====================

    def "iflte returns then value when subject <= value"() {
        when:
        def resultLess = conditionals.iflte([subject: 3.0, value: 5.0, then: "yes", else: "no"])
        def resultEqual = conditionals.iflte([subject: 5.0, value: 5.0, then: "yes", else: "no"])

        then:
        resultLess == "yes"
        resultEqual == "yes"
    }

    def "iflte returns else value when subject > value"() {
        when:
        def result = conditionals.iflte([subject: 10.0, value: 5.0, then: "yes", else: "no"])

        then:
        result == "no"
    }

    def "iflte with image where all pixels <= scalar returns then"() {
        given:
        def subject = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = conditionals.iflte([subject: subject, value: 3.0, then: "yes", else: "no"])

        then:
        result == "yes"  // all pixels <= 3
    }

    // ==================== Image-to-Image Comparison Tests ====================

    def "ifgt with two images compares all pixels"() {
        given:
        def subject = createImage(2, 2, 10.0f)
        def value = createImage(2, 2, 5.0f)

        when:
        def result = conditionals.ifgt([subject: subject, value: value, then: "yes", else: "no"])

        then:
        result == "yes"  // all 10.0 > 5.0
    }

    def "ifgt with two images returns else when not all pixels satisfy"() {
        given:
        def subject = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3
        def value = createImage(2, 2, 1.5f)

        when:
        def result = conditionals.ifgt([subject: subject, value: value, then: "yes", else: "no"])

        then:
        result == "no"  // 0 and 1 are not > 1.5
    }

    def "image comparison requires same dimensions"() {
        given:
        def subject = createImage(2, 2, 10.0f)
        def value = createImage(3, 3, 5.0f)

        when:
        conditionals.ifgt([subject: subject, value: value, then: "yes", else: "no"])

        then:
        thrown(IllegalArgumentException)
    }

    // ==================== Scalar-to-Image Comparison Tests ====================

    def "comparing scalar subject with image value works"() {
        given:
        def value = createImage(2, 2, 5.0f)

        when:
        def resultTrue = conditionals.ifgt([subject: 10.0, value: value, then: "yes", else: "no"])
        def resultFalse = conditionals.ifgt([subject: 3.0, value: value, then: "yes", else: "no"])

        then:
        resultTrue == "yes"   // 10 > all 5.0 pixels
        resultFalse == "no"   // 3 is not > any 5.0 pixel
    }

    // ==================== Then/Else Returns Unmodified ====================

    def "then/else values are returned as-is without modification"() {
        given:
        def thenImg = createImage(2, 2, 100.0f)
        def elseImg = createImage(2, 2, 0.0f)

        when:
        def resultThen = conditionals.ifeq([subject: "a", value: "a", then: thenImg, else: elseImg])
        def resultElse = conditionals.ifeq([subject: "a", value: "b", then: thenImg, else: elseImg])

        then:
        resultThen.is(thenImg)  // exact same object
        resultElse.is(elseImg)  // exact same object
    }

    def "can return any type in then/else"() {
        when:
        def resultString = conditionals.ifgt([subject: 10.0, value: 5.0, then: "greater", else: "not"])
        def resultNumber = conditionals.ifgt([subject: 10.0, value: 5.0, then: 100, else: 0])
        def resultList = conditionals.ifgt([subject: 10.0, value: 5.0, then: [1, 2, 3], else: []])

        then:
        resultString == "greater"
        resultNumber == 100
        resultList == [1, 2, 3]
    }

    // ==================== List Expansion Tests ====================

    def "conditionals expand lists correctly"() {
        given:
        def img1 = createImage(2, 2, 5.0f)   // all pixels 5.0
        def img2 = createImage(2, 2, 15.0f)  // all pixels 15.0

        when:
        List result = conditionals.ifgt([subject: [img1, img2], value: 10.0, then: "above", else: "below"])

        then:
        result.size() == 2
        result[0] == "below"  // img1 (5.0) is not > 10
        result[1] == "above"  // img2 (15.0) is > 10
    }

    // ==================== Edge Cases ====================

    def "ifgt throws exception for non-numeric non-image comparison"() {
        when:
        conditionals.ifgt([subject: "hello", value: "world", then: 1, else: 0])

        then:
        thrown(IllegalArgumentException)
    }

    def "ifeq handles null comparison correctly"() {
        when:
        def resultBothNull = conditionals.ifeq([subject: null, value: null, then: "yes", else: "no"])
        def resultOneNull = conditionals.ifeq([subject: null, value: "hello", then: "yes", else: "no"])

        then:
        resultBothNull == "yes"
        resultOneNull == "no"
    }
}
