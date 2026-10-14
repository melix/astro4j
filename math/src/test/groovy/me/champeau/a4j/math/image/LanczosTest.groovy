/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.math.image

import spock.lang.Specification

class LanczosTest extends Specification {

    private static final int WIDTH = 61
    private static final int HEIGHT = 47

    def "separable interpolation matches the direct 2D kernel, including next to the edges"() {
        given:
        var random = new Random(3)
        var image = new float[HEIGHT][WIDTH]
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image[y][x] = (float) (100 + 50 * Math.sin(x / 5d) * Math.cos(y / 7d) + 10 * random.nextGaussian())
            }
        }
        var points = (1..2000).collect { [random.nextDouble() * WIDTH, random.nextDouble() * HEIGHT] }
        points += [[0d, 0d], [WIDTH - 1d, HEIGHT - 1d], [0.5d, HEIGHT - 0.5d], [WIDTH - 0.01d, 0.01d], [12d, 7d], [30.5d, 20d]]
        var lanczos = new Lanczos()

        expect:
        points.every { p ->
            var expected = direct(image, p[0], p[1])
            Math.abs(lanczos.interpolate(image, p[0], p[1], WIDTH, HEIGHT) - expected) <= 1e-4 * Math.max(1, Math.abs(expected))
        }
    }

    private static float direct(float[][] image, double xx, double yy) {
        int a = 3
        int x0 = (int) Math.floor(xx)
        int y0 = (int) Math.floor(yy)
        double sum = 0
        double weightSum = 0
        for (int j = y0 - a + 1; j <= y0 + a; j++) {
            for (int i = x0 - a + 1; i <= x0 + a; i++) {
                if (i >= 0 && i < WIDTH && j >= 0 && j < HEIGHT) {
                    double w = kernel(xx - i, a) * kernel(yy - j, a)
                    sum += image[j][i] * w
                    weightSum += w
                }
            }
        }
        weightSum > 0 ? (float) (sum / weightSum) : 0f
    }

    private static double kernel(double x, int a) {
        if (x == 0) {
            return 1
        }
        if (Math.abs(x) >= a) {
            return 0
        }
        double pix = Math.PI * x
        a * Math.sin(pix) * Math.sin(pix / a) / (pix * pix)
    }
}
