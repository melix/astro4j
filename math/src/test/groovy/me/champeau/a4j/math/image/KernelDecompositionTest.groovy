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

class KernelDecompositionTest extends Specification {

    def "decomposes #name into #expectedTerms terms"() {
        when:
        def decomposition = KernelDecomposition.decompose(kernel)

        then:
        decomposition.present
        decomposition.get().terms().size() == expectedTerms
        reconstructionMatches(kernel, decomposition.get())

        where:
        name              | kernel                  | expectedTerms
        "gaussian blur"   | Kernel33.GAUSSIAN_BLUR  | 1
        "box blur"        | Kernel33.IDENTITY       | 1
        "sobel left"      | Kernel33.SOBEL_LEFT     | 1
        "sobel top"       | Kernel33.SOBEL_TOP      | 1
        "sharpen 3x3"     | Kernel33.SHARPEN        | 2
        "laplacian"       | Kernel33.LAPLACIAN      | 2
        "sharpen 7x7"     | SharpenKernel.of(7)     | 2
        "sharpen 15x15"   | SharpenKernel.of(15)    | 2
        "gaussian psf"    | psfKernel(7.5d, 5.0d)   | 1
    }

    def "rejects kernels with rank above 2"() {
        given:
        def random = new Random(42)
        def data = new float[5][5]
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                data[y][x] = random.nextFloat()
            }
        }
        def kernel = imageKernel(data)

        expect:
        KernelDecomposition.decompose(kernel).empty
    }

    def "rejects all-zero kernels"() {
        expect:
        KernelDecomposition.decompose(imageKernel(new float[3][3])).empty
    }

    def "separable passes must be cheaper than 2D to be used"() {
        expect:
        // 1 term on 3x3: 6 < 9
        KernelDecomposition.decompose(Kernel33.GAUSSIAN_BLUR).get().cheaperThan2D(3, 3)
        // 2 terms on 3x3: 12 > 9
        !KernelDecomposition.decompose(Kernel33.SHARPEN).get().cheaperThan2D(3, 3)
        // 2 terms on 7x7: 28 < 49
        KernelDecomposition.decompose(SharpenKernel.of(7)).get().cheaperThan2D(7, 7)
    }

    private static boolean reconstructionMatches(Kernel kernel, KernelDecomposition decomposition) {
        def source = kernel.kernel()
        int rows = source.length
        int cols = source[0].length
        float maxAbs = 0
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                maxAbs = Math.max(maxAbs, Math.abs(source[y][x]))
            }
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double reconstructed = 0
                for (def term : decomposition.terms()) {
                    reconstructed += term.col()[y] * term.row()[x]
                }
                assert Math.abs(reconstructed - source[y][x]) <= 1e-4 * maxAbs,
                        "Mismatch at ($y, $x): expected ${source[y][x]}, got $reconstructed"
            }
        }
        return true
    }

    private static Kernel psfKernel(double radius, double sigma) {
        return ImageKernel.of(Deconvolution.generateGaussianPSF(radius, sigma))
    }

    private static Kernel imageKernel(float[][] data) {
        return new Kernel() {
            @Override
            int rows() { return data.length }

            @Override
            int cols() { return data[0].length }

            @Override
            float[][] kernel() { return data }

            @Override
            float factor() { return 1f }
        }
    }
}
