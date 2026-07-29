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

    def "weighted_avg2 rejects a #description weight instead of returning an empty image"() {
        when:
        utilities.weightedAverage2([images: [image(10f), image(20f)], weights: [1d, weight], sigma: 2.5d])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('expects finite weights')

        where:
        description | weight
        'infinite'  | Double.POSITIVE_INFINITY
        'NaN'       | Double.NaN
    }

    def "weighted_avg rejects an infinite weight"() {
        when:
        utilities.weightedAverage([images: [image(10f), image(20f)], weights: [1d, Double.POSITIVE_INFINITY]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('expects finite weights')
    }

    def "weighted_avg2 rejects outliers beyond the sigma threshold"() {
        given:
        def images = [image(100f), image(100f), image(100f), image(100f), image(1000f)]
        def weights = [1d, 1d, 1d, 1d, 1d]

        when:
        def result = utilities.weightedAverage2([images: images, weights: weights, sigma: 1d])

        then:
        result.data()[0][0] == 100f
    }

    def "weighted_avg matches a reference implementation"() {
        given:
        def random = new Random(42)
        def images = (0..<12).collect { noisyImage(random) }
        def weights = (0..<12).collect { 0.5d + random.nextDouble() * 3 }

        when:
        def result = utilities.weightedAverage([images: images, weights: weights])

        then:
        var expected = referenceWeightedAverage(images, weights)
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assert Math.abs(result.data()[y][x] - expected[y][x]) < 1e-2
            }
        }
    }

    def "weighted_avg2 matches a reference implementation"() {
        given:
        def random = new Random(1234)
        def images = (0..<12).collect { noisyImage(random) }
        def weights = (0..<12).collect { 0.5d + random.nextDouble() * 3 }

        when:
        def result = utilities.weightedAverage2([images: images, weights: weights, sigma: 2.5d])

        then:
        var expected = referenceWeightedAverage2(images, weights, 2.5d)
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assert Math.abs(result.data()[y][x] - expected[y][x]) < 1e-2
            }
        }
    }

    private static ImageWrapper32 noisyImage(Random random) {
        float[][] data = new float[8][8]
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                data[y][x] = (float) (20000 + random.nextGaussian() * 500)
            }
        }
        new ImageWrapper32(8, 8, data, [:])
    }

    private static double[][] referenceWeightedAverage(List<ImageWrapper32> images, List<Double> weights) {
        double[][] expected = new double[8][8]
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double sum = 0
                double totalWeight = 0
                for (int i = 0; i < images.size(); i++) {
                    sum += weights[i] * images[i].data()[y][x]
                    totalWeight += weights[i]
                }
                expected[y][x] = totalWeight == 0 ? 0 : sum / totalWeight
            }
        }
        expected
    }

    private static double[][] referenceWeightedAverage2(List<ImageWrapper32> images, List<Double> weights, double sigma) {
        double[][] expected = new double[8][8]
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double sum = 0
                double totalWeight = 0
                for (int i = 0; i < images.size(); i++) {
                    sum += weights[i] * images[i].data()[y][x]
                    totalWeight += weights[i]
                }
                var mean = sum / totalWeight
                double variance = 0
                for (int i = 0; i < images.size(); i++) {
                    var delta = images[i].data()[y][x] - mean
                    variance += weights[i] * delta * delta
                }
                var threshold = sigma * Math.sqrt(variance / totalWeight)
                double keptSum = 0
                double keptWeight = 0
                for (int i = 0; i < images.size(); i++) {
                    if (Math.abs(images[i].data()[y][x] - mean) <= threshold) {
                        keptSum += weights[i] * images[i].data()[y][x]
                        keptWeight += weights[i]
                    }
                }
                expected[y][x] = keptWeight == 0 ? mean : keptSum / keptWeight
            }
        }
        expected
    }
}
