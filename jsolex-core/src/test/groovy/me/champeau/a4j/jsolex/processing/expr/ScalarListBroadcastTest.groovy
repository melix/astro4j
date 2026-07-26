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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

/**
 * Multiplying or dividing a list of numbers by a scalar broadcasts over the list, which is
 * what makes it possible to derive a list of weights from a list of per image measurements.
 */
class ScalarListBroadcastTest extends Specification {

    @Subject
    ImageExpressionEvaluator evaluator = new ShiftCollectingImageExpressionEvaluator({ new ImageWrapper32(0, 0, new float[0][], [:]) })

    def "divides a scalar by a list"() {
        expect:
        evaluator.div(1d, [2d, 4d, 5d]) == [0.5d, 0.25d, 0.2d]
    }

    def "divides a list by a scalar"() {
        expect:
        evaluator.div([2d, 4d, 6d], 2d) == [1d, 2d, 3d]
    }

    def "multiplies a list by a scalar in both orders"() {
        expect:
        evaluator.mul([1d, 2d, 3d], 10d) == [10d, 20d, 30d]
        evaluator.mul(10d, [1d, 2d, 3d]) == [10d, 20d, 30d]
    }

    def "an inverse variance weight list can be derived from a list of noise measurements"() {
        given:
        var sigmas = [10d, 20d, 40d]

        when:
        var weights = evaluator.div(1d, evaluator.mul(sigmas, sigmas))

        then:
        weights == [1d / 100, 1d / 400, 1d / 1600]
    }

    def "element-wise list operations are unchanged"() {
        expect:
        evaluator.div([10d, 20d], [2d, 4d]) == [5d, 5d]
        evaluator.mul([10d, 20d], [2d, 4d]) == [20d, 80d]
    }

    def "lists of different sizes are not broadcast"() {
        when:
        evaluator.div([1d, 2d, 3d], [1d, 2d])

        then:
        thrown(IllegalArgumentException)
    }
}
