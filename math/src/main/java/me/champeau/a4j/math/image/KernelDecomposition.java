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
package me.champeau.a4j.math.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Low-rank decomposition of a convolution kernel into a sum of separable
 * (rank-1) terms, each being the outer product of a column vector and a row
 * vector. A k×k convolution with an r-term decomposition costs r·2k
 * multiply-adds per pixel instead of k², which is a large win for kernels
 * such as Gaussian blurs (rank 1) or sharpening kernels (rank 2) as soon as
 * the kernel is bigger than a few pixels.
 *
 * @param terms the rank-1 terms whose sum reconstructs the kernel
 */
public record KernelDecomposition(List<Term> terms) {

    private static final int MAX_TERMS = 2;
    private static final double TOLERANCE = 1e-4;

    /**
     * A rank-1 term: {@code kernel[y][x] = col[y] * row[x]}.
     *
     * @param col the column vector (one weight per kernel row)
     * @param row the row vector (one weight per kernel column)
     */
    public record Term(float[] col, float[] row) {
    }

    /**
     * Attempts to decompose a kernel into at most {@value MAX_TERMS} rank-1
     * terms using greedy pivot-based peeling. Returns an empty optional when
     * the kernel has a higher rank (within tolerance), in which case the
     * caller should use a regular 2D convolution.
     *
     * @param kernel the kernel to decompose
     * @return the decomposition, or empty if the kernel is not low-rank
     */
    public static Optional<KernelDecomposition> decompose(Kernel kernel) {
        var source = kernel.kernel();
        int rows = source.length;
        int cols = source[0].length;
        var residual = new double[rows][cols];
        double maxAbs = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                residual[y][x] = source[y][x];
                maxAbs = Math.max(maxAbs, Math.abs(residual[y][x]));
            }
        }
        if (maxAbs == 0) {
            return Optional.empty();
        }
        double threshold = TOLERANCE * maxAbs;
        var terms = new ArrayList<Term>();
        for (int t = 0; t < MAX_TERMS && residualExceeds(residual, threshold); t++) {
            int pivotY = 0;
            int pivotX = 0;
            double pivot = 0;
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (Math.abs(residual[y][x]) > Math.abs(pivot)) {
                        pivot = residual[y][x];
                        pivotY = y;
                        pivotX = x;
                    }
                }
            }
            var col = new double[rows];
            var row = new double[cols];
            for (int y = 0; y < rows; y++) {
                col[y] = residual[y][pivotX];
            }
            for (int x = 0; x < cols; x++) {
                row[x] = residual[pivotY][x] / pivot;
            }
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    residual[y][x] -= col[y] * row[x];
                }
            }
            var colF = new float[rows];
            var rowF = new float[cols];
            for (int y = 0; y < rows; y++) {
                colF[y] = (float) col[y];
            }
            for (int x = 0; x < cols; x++) {
                rowF[x] = (float) row[x];
            }
            terms.add(new Term(colF, rowF));
        }
        if (residualExceeds(residual, threshold)) {
            return Optional.empty();
        }
        return Optional.of(new KernelDecomposition(List.copyOf(terms)));
    }

    /**
     * Returns whether convolving with this decomposition is cheaper than the
     * direct 2D convolution for the given kernel dimensions.
     *
     * @param kernelWidth  the kernel width
     * @param kernelHeight the kernel height
     * @return true if the separable passes cost fewer multiply-adds per pixel
     */
    public boolean cheaperThan2D(int kernelWidth, int kernelHeight) {
        return terms.size() * (kernelWidth + kernelHeight) < kernelWidth * kernelHeight;
    }

    private static boolean residualExceeds(double[][] residual, double threshold) {
        for (var row : residual) {
            for (var value : row) {
                if (Math.abs(value) > threshold) {
                    return true;
                }
            }
        }
        return false;
    }
}
