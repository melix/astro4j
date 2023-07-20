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

public class BlurKernel implements Kernel {
    private final int n;
    private final float[][] array;
    private final float factor;

    public static BlurKernel of(int n) {
        return new BlurKernel(n);
    }

    private BlurKernel(int n) {
        this.n = n;
        this.array = new float[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = 1;
            }
        }
        this.factor = 1f/(n*n);
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
