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
package me.champeau.a4j.math.image;

/**
 * A variable-size sharpening kernel.
 */
public class SharpenKernel implements Kernel {
    private final int n;
    private final float[][] array;
    private final float factor;

    /**
     * Creates a sharpening kernel of the specified size.
     *
     * @param n the kernel size (must be odd and >= 3)
     * @return a sharpening kernel
     */
    public static SharpenKernel of(int n) {
        return new SharpenKernel(n);
    }

    private SharpenKernel(int n) {
        if (n < 3 || n % 2 == 0) {
            throw new IllegalArgumentException("Sharpen kernel size must be an odd number >= 3");
        }
        this.n = n;
        this.array = new float[n][n];

        int center = n / 2;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = (i == center && j == center) ? 2f : -1f / (n * n - 1);
            }
        }

        this.factor = 1f; // Normalization to maintain overall intensity
    }

    @Override
    public int rows() {
        return n;
    }

    @Override
    public int cols() {
        return n;
    }

    @Override
    public float[][] kernel() {
        return array;
    }

    @Override
    public float factor() {
        return factor;
    }
}
