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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

import java.util.stream.DoubleStream

class SimpleFunctionCallTest extends Specification {

    @Subject
    SimpleFunctionCall functions = new SimpleFunctionCall([:], Broadcaster.NO_OP)

    private static ImageWrapper32 image(Random random) {
        float[][] data = new float[4][4]
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                data[y][x] = (float) (10000 + random.nextGaussian() * 1000)
            }
        }
        new ImageWrapper32(4, 4, data, [:])
    }

    def "#name streaming reduction matches the stream based one"() {
        given:
        def random = new Random(7)
        def images = (0..<10).collect { image(random) }

        when:
        def streamed = functions.applyFunction(name, [list: images], operator, reduction)
        def reference = functions.applyFunction(name, [list: images], operator)

        then:
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assert Math.abs(streamed.data()[y][x] - reference.data()[y][x]) < 1e-2
            }
        }

        where:
        name  | operator                | reduction
        'avg' | DoubleStream::average   | SimpleFunctionCall.StreamingReduction.AVERAGE
        'max' | DoubleStream::max       | SimpleFunctionCall.StreamingReduction.MAX
        'min' | DoubleStream::min       | SimpleFunctionCall.StreamingReduction.MIN
    }

    def "avg streams file backed images without unwrapping them all"() {
        given:
        def random = new Random(11)
        def images = (0..<6).collect { image(random) }
        def fileBacked = images.collect { FileBackedImage.wrap(it) }

        when:
        def result = functions.applyFunction('avg', [list: fileBacked], DoubleStream::average, SimpleFunctionCall.StreamingReduction.AVERAGE)

        then:
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                double expected = images.sum { it.data()[y][x] } / images.size()
                assert Math.abs(result.data()[y][x] - expected) < 1e-2
            }
        }
    }

    def "avg rejects images of different dimensions"() {
        given:
        def images = [new ImageWrapper32(4, 4, new float[4][4], [:]), new ImageWrapper32(2, 2, new float[2][2], [:])]

        when:
        functions.applyFunction('avg', [list: images], DoubleStream::average, SimpleFunctionCall.StreamingReduction.AVERAGE)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('same dimensions')
    }
}
