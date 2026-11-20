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
package me.champeau.a4j.jsolex.processing

import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet

final class EquivalenceSupport {
    static final List<List<Integer>> SIZES = [[1, 1], [7, 3], [3, 7], [513, 257], [1031, 777]]

    private EquivalenceSupport() {
    }

    static float[][] randomImage(int width, int height, long seed, float max = 65535f) {
        def random = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def roll = random.nextInt(50)
                if (roll == 0) {
                    data[y][x] = 0f
                } else if (roll == 1) {
                    data[y][x] = max
                } else {
                    data[y][x] = (float) (random.nextDouble() * max)
                }
            }
        }
        data
    }

    static Ellipse centeredDisk(int width, int height) {
        double cx = width / 2.0d
        double cy = height / 2.0d
        double rx = Math.max(0.5d, width * 0.3d)
        double ry = Math.max(0.5d, height * 0.35d)
        double a = 1.0d / (rx * rx)
        double c = 1.0d / (ry * ry)
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, -2.0d * cx * a, -2.0d * cy * c, cx * cx * a + cy * cy * c - 1.0d))
    }

    static void assertSameBits(float[][] expected, float[][] actual) {
        assert expected.length == actual.length
        for (int y = 0; y < expected.length; y++) {
            assert expected[y].length == actual[y].length
            for (int x = 0; x < expected[y].length; x++) {
                if (Float.floatToRawIntBits(expected[y][x]) != Float.floatToRawIntBits(actual[y][x])) {
                    assert false: "Pixel ($x, $y) differs: expected ${expected[y][x]} but was ${actual[y][x]}"
                }
            }
        }
    }

    static void assertSameBits(float[][][] expected, float[][][] actual) {
        assert expected.length == actual.length
        for (int c = 0; c < expected.length; c++) {
            assertSameBits(expected[c], actual[c])
        }
    }
}
