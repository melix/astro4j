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

import me.champeau.a4j.jsolex.processing.expr.impl.SignedDiff
import me.champeau.a4j.jsolex.processing.expr.impl.SimpleFunctionCall
import me.champeau.a4j.jsolex.processing.expr.impl.Stretching
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import java.util.stream.DoubleStream

/**
 * Image operations preserve the full range of their result: values are neither clamped to the
 * displayable range nor shifted to make them positive. Bringing an image back into the
 * displayable range is the job of the clamp function.
 */
class NoAutomaticClampingTest extends Specification {

    private SimpleFunctionCall functions = new SimpleFunctionCall([:], Broadcaster.NO_OP)
    private Stretching stretching = new Stretching([:], Broadcaster.NO_OP)
    private SignedDiff signedDiff = new SignedDiff([:], Broadcaster.NO_OP)
    private ImageExpressionEvaluator evaluator = new ShiftCollectingImageExpressionEvaluator({ image(0f) })

    def "pixel-wise combination preserves values outside the displayable range"() {
        expect:
        pixel(functions.applyFunction("avg", ["list": [image(value), image(value)]], DoubleStream::average)) == value

        where:
        value << [100000f, -100000f, 30000f]
    }

    def "subtracting no longer shifts the result to keep it positive"() {
        when: "a subtraction whose result is negative everywhere"
        var result = evaluator.minus(image(1000f), image(4000f))

        then: "the negative value survives instead of being offset by its own minimum"
        pixel(result) == -3000f
    }

    def "subtraction is now equivalent to signed_diff"() {
        given:
        var a = image(1000f)
        var b = image(4000f)

        expect:
        pixel(evaluator.minus(a, b)) == pixel(signedDiff.signedDiff(["a": a, "b": b]))
    }

    def "adding beyond the white point is preserved"() {
        expect:
        pixel(evaluator.plus(image(60000f), image(30000f))) == 90000f
    }

    def "multiplying by a scalar is preserved"() {
        expect:
        pixel(evaluator.mul(image(40000f), 3d)) == 120000f
    }

    def "clamp brings an image back into the displayable range"() {
        expect:
        pixel(stretching.clamp(["img": image(value)])) == expected

        where:
        value    || expected
        100000f  || 65535f
        -5000f   || 0f
        30000f   || 30000f
    }

    def "clamp accepts explicit bounds"() {
        expect:
        pixel(stretching.clamp(["img": image(5000f), "lo": 1000d, "hi": 4000d])) == 4000f
        pixel(stretching.clamp(["img": image(500f), "lo": 1000d, "hi": 4000d])) == 1000f
    }

    def "clamp rejects an inverted range"() {
        when:
        stretching.clamp(["img": image(100f), "lo": 100d, "hi": 10d])

        then:
        thrown(IllegalArgumentException)
    }

    def "lift shifts an image up only when it contains negative values"() {
        expect:
        pixel(stretching.lift(["img": image(value)])) == expected

        where:
        value    || expected
        -3000f   || 0f
        5000f    || 5000f
        0f       || 0f
    }

    def "lift preserves the relative distribution"() {
        given:
        var image = new ImageWrapper32(2, 1, [[-1000f, 2000f] as float[]] as float[][], [:])

        when:
        var result = (ImageWrapper32) stretching.lift(["img": image])

        then: "the whole image is translated, differences are unchanged"
        result.data()[0][0] == 0f
        result.data()[0][1] == 3000f
    }

    def "a scalar on the left of a non commutative operator keeps its side"() {
        expect: "1000 - image, not image - 1000"
        pixel(evaluator.minus(1000f, image(400f))) == 600f

        and: "1000 / image, not image / 1000"
        pixel(evaluator.div(1000f, image(4f))) == 250f
    }

    def "a scalar on the right of a non commutative operator keeps its side"() {
        expect:
        pixel(evaluator.minus(image(1000f), 400f)) == 600f
        pixel(evaluator.div(image(1000f), 4f)) == 250f
    }

    private static ImageWrapper32 image(float value) {
        float[][] data = new float[1][1]
        data[0][0] = value
        new ImageWrapper32(1, 1, data, [:])
    }

    private static float pixel(Object result) {
        var image = result instanceof List ? result.first() : result
        ((ImageWrapper32) image).data()[0][0]
    }
}
