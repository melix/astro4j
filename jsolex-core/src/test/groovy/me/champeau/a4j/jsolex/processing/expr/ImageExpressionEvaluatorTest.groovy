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
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification
import spock.lang.Subject

class ImageExpressionEvaluatorTest extends Specification {
    public static final Ellipse DUMMY_ELLIPSE = Ellipse.ofCartesian(new DoubleSextuplet(0, 0, 0, 0, 0, 0))
    @Subject
    ImageExpressionEvaluator evaluator

    private Map<Integer, ImageWrapper32> images = [:].withDefault { new ImageWrapper32(0, 0, new float[0][], [:]) }

    def "can collect image shifts from expression"() {
        given:
        evaluator = new ShiftCollectingImageExpressionEvaluator(images::get)

        when:
        evaluator.evaluate("img(0)")

        then:
        evaluator.shifts ==~ [0d]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("(img(1) + img(-1))/2")

        then:
        evaluator.shifts ==~ [1d, -1d]

        when:
        evaluator.clearShifts()
        evaluator.putVariable("a", "5")
        evaluator.putVariable("b", "6")
        evaluator.evaluate("max(img(a), img(b))")

        then:
        evaluator.shifts ==~ [5d, 6d]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-5, 5)")

        then:
        evaluator.shifts ==~ [-5d, -4d, -3d, -2d, -1d, 0d, 1d, 2d, 3d, 4d, 5d]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-6,6,3)")

        then:
        evaluator.shifts ==~ [-6d, -3d, 0d, 3d, 6d]

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-2,0) + range(0,2)")

        then:
        evaluator.shifts ==~ [-2d, -1d, 0d, 1d, 2d]

        when:
        evaluator.clearShifts()
        def list = evaluator.evaluate("range(-2,0) - range(0,2)")

        then:
        evaluator.shifts ==~ [-2d, -1d, 0d, 1d, 2d]
        list.size() == 2

        when:
        evaluator.clearShifts()
        evaluator.evaluate("range(-1,1;.5)")

        then:
        evaluator.shifts ==~ [-1d, -0.5d, 0d, 0.5d, 1d]
    }

    def "can apply #function on a list of images"() {
        given:
        evaluator = new ShiftCollectingImageExpressionEvaluator(images::get)
        evaluator.putInContext(Ellipse, DUMMY_ELLIPSE)

        when:
        var result = evaluator.evaluate("$function($parameters)")
        resultType.isAssignableFrom(result.class)

        then:
        noExceptionThrown()

        where:
        function          | parameters                  | resultType
        "avg"             | "range(0,1)"                | ImageWrapper32
        "min"             | "range(0,1)"                | ImageWrapper32
        "max"             | "range(0,1)"                | ImageWrapper32
        "colorize"        | "range(0,1), \"h-alpha\""   | List
        "colorize"        | "range(0,1),0,0,0,0,0,0"    | List
        "remove_bg"       | "range(0,1)"                | List
        "remove_bg"       | "range(0,1),0.5"            | List
        "invert"          | "range(0,1)"                | List
        "fix_banding"     | "range(0,1), 10, 1"         | List
        "linear_stretch"  | "range(0,1)"                | List
        "ASINH_STRETCH"   | "range(0,1), 0, 10"         | List
        "CLAHE"           | "range(0,1), 1.2"           | List
        "CLAHE"           | "range(0,1), 128, 512, 1.2" | List
        "ADJUST_CONTRAST" | "range(0,1), 0, 255"        | List
        "autocrop"        | "range(0,1)"                | List
    }
}
