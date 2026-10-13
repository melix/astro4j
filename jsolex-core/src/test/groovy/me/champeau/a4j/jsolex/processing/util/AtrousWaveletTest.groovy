/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification

import java.util.function.BiPredicate

class AtrousWaveletTest extends Specification {

    private static final int SIZE = 256
    private static final double NOISE = 10

    def "noise factors match the B3 spline starlet"() {
        when:
        var factors = AtrousWavelet.noiseFactors(5)

        then:
        [factors, [0.889d, 0.200d, 0.086d, 0.041d, 0.020d]].transpose().every { a, b -> Math.abs(a - b) < 0.003 }
    }

    def "the scales and the residual add up to the image"() {
        given:
        var image = addNoise(smoothImage())

        when:
        var scales = AtrousWavelet.decompose(SIZE, SIZE, image, null, 4)

        then:
        scales.size() == 5
        rmsError(sum(scales), image) < 1e-3
    }

    def "a masked decomposition keeps the pixels outside the mask in the residual"() {
        given:
        var image = addNoise(smoothImage())
        BiPredicate<Integer, Integer> mask = { int x, int y -> x >= SIZE.intdiv(2) }

        when:
        var scales = AtrousWavelet.decompose(SIZE, SIZE, image, mask, 3)

        then:
        scales[0][10][10] == 0f
        scales.last()[10][10] == image[10][10]
        rmsError(sum(scales), image) < 1e-3
    }

    def "removes most of the noise without biasing the structures"() {
        given:
        var truth = smoothImage()
        var noisy = addNoise(truth)

        when:
        var denoised = AtrousWavelet.denoise(SIZE, SIZE, noisy, null, 0)
        var bias = mean(denoised) - mean(truth)

        then:
        rmsError(noisy, truth) > 0.9 * NOISE
        rmsError(denoised, truth) < 0.3 * NOISE
        Math.abs(bias) < 0.05 * NOISE
    }

    def "grain keeps a fraction of the noise"() {
        given:
        var truth = smoothImage()
        var noisy = addNoise(truth)

        when:
        var smooth = AtrousWavelet.denoise(SIZE, SIZE, noisy, null, 0)
        var grainy = AtrousWavelet.denoise(SIZE, SIZE, noisy, null, 0.5)
        var unchanged = AtrousWavelet.denoise(SIZE, SIZE, noisy, null, 1)

        then:
        rmsError(grainy, truth) > rmsError(smooth, truth)
        rmsError(grainy, truth) < rmsError(noisy, truth)
        rmsError(unchanged, noisy) < 1e-3
    }

    def "pixels outside the mask are left unchanged and ignored"() {
        given:
        var noisy = addNoise(smoothImage())
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE.intdiv(2); x++) {
                noisy[y][x] = 50000
            }
        }
        BiPredicate<Integer, Integer> mask = { int x, int y -> x >= SIZE.intdiv(2) }

        when:
        var denoised = AtrousWavelet.denoise(SIZE, SIZE, noisy, mask, 0)

        then:
        denoised[10][10] == 50000f
        Math.abs(denoised[100][SIZE.intdiv(2)] - smoothImage()[100][SIZE.intdiv(2)]) < 3 * NOISE
    }

    def "follows a noise level which rises near the edge of the mask"() {
        given:
        var edge = SIZE.intdiv(2)
        var random = new Random(1)
        var noisy = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                var sigma = NOISE * (1 + 2 * Math.exp(-Math.abs(x - edge) / 10d))
                noisy[y][x] = (float) (1000 + sigma * random.nextGaussian())
            }
        }
        BiPredicate<Integer, Integer> mask = { int x, int y -> x >= edge }

        when:
        var denoised = AtrousWavelet.denoise(SIZE, SIZE, noisy, mask, 0)
        var spots = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = edge; x < edge + 30; x++) {
                if (Math.abs(denoised[y][x] - 1000) > NOISE) {
                    spots++
                }
            }
        }

        then:
        spots < 6
    }

    private static float[][] sum(List<float[][]> scales) {
        var result = new float[SIZE][SIZE]
        scales.each { scale ->
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    result[y][x] += scale[y][x]
                }
            }
        }
        result
    }

    private static float[][] smoothImage() {
        var data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                var dx = x - SIZE / 2
                var dy = y - SIZE / 3
                data[y][x] = (float) (1000 + 0.5 * x + 200 * Math.exp(-(dx * dx + dy * dy) / 800d))
            }
        }
        data
    }

    private static float[][] addNoise(float[][] data) {
        var random = new Random(1)
        var result = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                result[y][x] = (float) (data[y][x] + NOISE * random.nextGaussian())
            }
        }
        result
    }

    private static double rmsError(float[][] a, float[][] b) {
        double sum = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                sum += (a[y][x] - b[y][x])**2
            }
        }
        Math.sqrt(sum / (SIZE * SIZE))
    }

    private static double mean(float[][] data) {
        double sum = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                sum += data[y][x]
            }
        }
        sum / (SIZE * SIZE)
    }
}
