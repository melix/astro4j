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
package me.champeau.a4j.math.image.analysis;

import me.champeau.a4j.math.image.Kernel;

/**
 * Utility class for Gaussian function operations and kernel generation.
 */
public class GaussianSupport {
    private GaussianSupport() {

    }

    /**
     * Generates a square Gaussian matrix.
     *
     * @param n the size of the square matrix
     * @param sigma the standard deviation of the Gaussian
     * @return a square matrix containing Gaussian values
     */
    public static double[][] gaussianSquare(int n, float sigma) {
        var gaussian = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                gaussian[y][x] = gaussian(x, y, sigma);
            }
        }
        return gaussian;
    }

    /**
     * Generates a square Gaussian matrix as float values.
     *
     * @param n the size of the square matrix
     * @param sigma the standard deviation of the Gaussian
     * @return a square matrix containing Gaussian values as floats
     */
    public static float[][] gaussianSquareAsFloat(int n, float sigma) {
        var gaussian = new float[n][n];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                gaussian[y][x] = (float) gaussian(x, y, sigma);
            }
        }
        return gaussian;
    }

    /**
     * Creates a Gaussian blur kernel.
     *
     * @param n the size of the kernel
     * @param sigma the standard deviation of the Gaussian
     * @return a Gaussian kernel
     */
    public static Kernel gaussianKernel(int n, float sigma) {
        var kernel = gaussianSquareAsFloat(n, sigma);
        return new Kernel() {
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
                return kernel;
            }

            @Override
            public float factor() {
                return 1;
            }
        };
    }

    /**
     * Computes the 2D Gaussian function value at the given coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param sigma the standard deviation of the Gaussian
     * @return the Gaussian value at (x, y)
     */
    public static double gaussian(double x, double y, double sigma) {
        return (1.0f / (2.0f * Math.PI * sigma * sigma)) * Math.exp(-(x * x + y * y) / (2.0f * sigma * sigma));
    }
}
