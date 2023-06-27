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
package me.champeau.a4j.jsolex.processing.expr

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

class ImageExpressionEvaluatorTest extends Specification {
    @Subject
    ImageExpressionEvaluator evaluator

    private Map<Integer, ImageWrapper32> images = [:].withDefault { new ImageWrapper32(0, 0, new float[0]) }

    def "can collect image shifts from expression"() {
        given:
        evaluator = new ShiftCollectingImageExpressionEvaluator(images::get)

        when:
        evaluator.evaluate("img(0)")

        then:
        evaluator.shifts ==~ [0]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("(img(1) + img(-1))/2")

        then:
        evaluator.shifts ==~ [1, -1]

        when:
        evaluator.clearShifts()
        evaluator.putVariable("a", "5")
        evaluator.putVariable("b", "6")
        evaluator.evaluate("max(img(a), img(b))")

        then:
        evaluator.shifts ==~ [5, 6]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-5, 5)")

        then:
        evaluator.shifts ==~ [-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-6,6,3)")

        then:
        evaluator.shifts ==~ [-6, -3, 0, 3, 6]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-2,0) + range(0,2)")

        then:
        evaluator.shifts ==~ [-2, -1, 0, 1, 2]

        when:
        evaluator.clearShifts()
        def list = evaluator.evaluate("range(-2,0) - range(0,2)")

        then:
        evaluator.shifts ==~ [-2, -1, 0, 1, 2]
        list.size() == 2
    }
}
