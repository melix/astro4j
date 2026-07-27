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

class WeightedAverageTest extends Specification {

    @Subject
    Utilities utilities = new Utilities([:], Broadcaster.NO_OP)

    private static ImageWrapper32 image(float value) {
        float[][] data = new float[2][2]
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(2, 2, data, [:])
    }

    def "weighted_avg accepts a single image and a single weight"() {
        when:
        def result = utilities.weightedAverage([images: image(10f), weights: 4d])

        then:
        result instanceof ImageWrapper32
        result.data()[0][0] == 10f
    }

    def "weighted_avg2 accepts a single image and a single weight"() {
        when:
        def result = utilities.weightedAverage2([images: image(10f), weights: 4d, sigma: 2.5d])

        then:
        result instanceof ImageWrapper32
        result.data()[0][0] == 10f
    }

    def "weighted_avg2 accepts single element lists"() {
        when:
        def result = utilities.weightedAverage2([images: [image(10f)], weights: [4d], sigma: 2.5d])

        then:
        result instanceof ImageWrapper32
        result.data()[0][0] == 10f
    }

    def "weighted_avg2 still averages multiple images"() {
        when:
        def result = utilities.weightedAverage2([images: [image(10f), image(20f)], weights: [1d, 3d], sigma: 2.5d])

        then:
        result.data()[0][0] == 17.5f
    }

    def "weighted_avg2 rejects a mismatch between images and weights"() {
        when:
        utilities.weightedAverage2([images: [image(10f), image(20f)], weights: 4d, sigma: 2.5d])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('same number of images and weights')
    }
}
