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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32

final class StretchingTestImages {
    private StretchingTestImages() {
    }

    static ImageWrapper32 image(float[][] data) {
        new ImageWrapper32(data[0].length, data.length, data, [:] as Map<Class<?>, Object>)
    }

    static ImageWrapper32 noise(long seed, int size = 64, double amplitude = 30000) {
        def random = new Random(seed)
        def data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = (float) (1000 + amplitude * random.nextDouble())
            }
        }
        image(data)
    }

    static ImageWrapper32 halves(float left, float right, int size = 64) {
        def data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = x < size / 2 ? left : right
            }
        }
        image(data)
    }

    static float[][] copyOf(float[][] data) {
        data.collect { it.clone() } as float[][]
    }

    static double maxDifference(float[][] a, float[][] b) {
        double max = 0
        for (int y = 0; y < a.length; y++) {
            for (int x = 0; x < a[y].length; x++) {
                max = Math.max(max, Math.abs(a[y][x] - b[y][x]))
            }
        }
        max
    }
}
