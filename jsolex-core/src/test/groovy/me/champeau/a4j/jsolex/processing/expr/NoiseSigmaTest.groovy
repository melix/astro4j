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

import me.champeau.a4j.jsolex.processing.expr.impl.ImageStatistics
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RangeMask
import spock.lang.Specification
import spock.lang.Subject

class NoiseSigmaTest extends Specification {

    private static final int SIZE = 200

    @Subject
    ImageStatistics statistics = new ImageStatistics([:], Broadcaster.NO_OP)

    def "measures the standard deviation of gaussian noise"() {
        given:
        var image = noisy(1000f, sigma, 42)

        when:
        var measured = statistics.noiseSigma(["img": image]) as double

        then: "the estimate is within 10% of the injected noise"
        Math.abs(measured - sigma) / sigma < 0.1

        where:
        sigma << [10f, 50f, 200f]
    }

    def "a noise free image has a zero noise estimate"() {
        expect:
        statistics.noiseSigma(["img": constant(1000f)]) == 0d
    }

    def "a smooth gradient is not counted as noise"() {
        given: "a steep linear ramp, which a percentile span would report as a huge spread"
        var image = gradient()

        expect: "the Laplacian cancels the locally linear component"
        (statistics.noiseSigma(["img": image]) as double) < 0.01d
    }

    def "the estimate is insensitive to isolated outliers"() {
        given:
        var clean = noisy(1000f, 20f, 7)
        var spiked = noisy(1000f, 20f, 7)
        20.times { i -> spiked.data()[10 + i][10 + i] = 60000f }

        when:
        var cleanSigma = statistics.noiseSigma(["img": clean]) as double
        var spikedSigma = statistics.noiseSigma(["img": spiked]) as double

        then: "unlike a percentile span anchored on the tails, the median is barely moved"
        Math.abs(spikedSigma - cleanSigma) / cleanSigma < 0.05
    }

    def "the estimate is independent of the image offset"() {
        given:
        var low = noisy(500f, 30f, 11)
        var high = noisy(40000f, 30f, 11)

        expect:
        Math.abs((statistics.noiseSigma(["img": low]) as double) - (statistics.noiseSigma(["img": high]) as double)) < 1e-3
    }

    def "the estimate scales linearly with an affine gain"() {
        given: "the same frame normalized with two different gains"
        var image = noisy(1000f, 25f, 3)
        var scaled = new ImageWrapper32(SIZE, SIZE, image.data().collect { row -> row.collect { (float) (3 * it) } as float[] } as float[][], [:])

        when:
        var base = statistics.noiseSigma(["img": image]) as double
        var amplified = statistics.noiseSigma(["img": scaled]) as double

        then:
        Math.abs(amplified / base - 3) < 0.01
    }

    def "a mask restricts the measurement"() {
        given: "a quiet half and a noisy half, separated by value range"
        float[][] data = new float[SIZE][SIZE]
        var random = new Random(5)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = y < SIZE / 2 ? (float) (1000 + 5 * random.nextGaussian()) : (float) (40000 + 300 * random.nextGaussian())
            }
        }
        var image = new ImageWrapper32(SIZE, SIZE, data, [:])

        when:
        var quiet = statistics.noiseSigma(["img": image, "mask": new RangeMask(0, 20000)]) as double
        var loud = statistics.noiseSigma(["img": image, "mask": new RangeMask(20000, 65535)]) as double

        then:
        Math.abs(quiet - 5) / 5 < 0.2
        Math.abs(loud - 300) / 300 < 0.2
    }

    def "returns one value per image when given a list"() {
        when:
        var result = statistics.noiseSigma(["img": [noisy(1000f, 10f, 1), noisy(1000f, 40f, 2)]])

        then:
        result instanceof List
        result.size() == 2
        result[0] < result[1]
    }

    private static ImageWrapper32 noisy(float level, float sigma, long seed) {
        float[][] data = new float[SIZE][SIZE]
        var random = new Random(seed)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) (level + sigma * random.nextGaussian())
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, [:])
    }

    private static ImageWrapper32 constant(float value) {
        float[][] data = new float[SIZE][SIZE]
        data.each { Arrays.fill(it, value) }
        new ImageWrapper32(SIZE, SIZE, data, [:])
    }

    private static ImageWrapper32 gradient() {
        float[][] data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) (100 * x + 50 * y)
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, [:])
    }
}
