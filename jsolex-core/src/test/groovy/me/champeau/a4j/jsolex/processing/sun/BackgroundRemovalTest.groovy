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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.opencl.OpenCLSupport
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Requires
import spock.lang.Specification

class BackgroundRemovalTest extends Specification {

    def "background model produces valid results without ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def image = new ImageWrapper32(width, height, data, [:])

        when:
        def result = BackgroundRemoval.backgroundModel(image, degree, 2.0)

        then:
        result.isPresent()
        def bgData = result.get().data()
        !hasNaN(bgData, width, height)
        !hasInfinity(bgData, width, height)

        where:
        degree << [1, 2, 3]
    }

    def "background model produces valid results with ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def ellipse = createTestEllipse(width, height)
        def image = new ImageWrapper32(width, height, data, [(Ellipse): ellipse])

        when:
        def result = BackgroundRemoval.backgroundModel(image, degree, 2.0)

        then:
        result.isPresent()
        def bgData = result.get().data()
        !hasNaN(bgData, width, height)
        !hasInfinity(bgData, width, height)

        where:
        degree << [1, 2, 3]
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU background model matches CPU implementation degree #degree"() {
        given:
        def width = 512
        def height = 512
        def cpuData = createTestImage(width, height)
        def gpuData = copyData(cpuData)
        def cpuImage = new ImageWrapper32(width, height, cpuData, [:])
        def gpuImage = new ImageWrapper32(width, height, gpuData, [:])

        when:
        def cpuResult = runCpuOnly {
            BackgroundRemoval.backgroundModel(cpuImage, degree, 2.0)
        }
        def gpuResult = runGpuEnabled {
            BackgroundRemoval.backgroundModel(gpuImage, degree, 2.0)
        }

        then:
        cpuResult.isPresent()
        gpuResult.isPresent()
        compareData(cpuResult.get().data(), gpuResult.get().data(), width, height, 1.0f)

        where:
        degree << [1, 2, 3]
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "GPU background model matches CPU implementation with ellipse"() {
        given:
        def width = 512
        def height = 512
        def cpuData = createTestImage(width, height)
        def gpuData = copyData(cpuData)
        def ellipse = createTestEllipse(width, height)
        def cpuImage = new ImageWrapper32(width, height, cpuData, [(Ellipse): ellipse])
        def gpuImage = new ImageWrapper32(width, height, gpuData, [(Ellipse): ellipse])

        when:
        def cpuResult = runCpuOnly {
            BackgroundRemoval.backgroundModel(cpuImage, 2, 2.0)
        }
        def gpuResult = runGpuEnabled {
            BackgroundRemoval.backgroundModel(gpuImage, 2, 2.0)
        }

        then:
        cpuResult.isPresent()
        gpuResult.isPresent()
        compareData(cpuResult.get().data(), gpuResult.get().data(), width, height, 1.0f)
    }

    private static float[][] createTestImage(int width, int height) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create a gradient background with some noise
                float bgGradient = (float) (1000 + 500 * (x / (double) width) + 300 * (y / (double) height))
                data[y][x] = bgGradient + random.nextFloat() * 100
            }
        }
        return data
    }

    private static float[][] copyData(float[][] src) {
        var height = src.length
        var width = src[0].length
        var copy = new float[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(src[y], 0, copy[y], 0, width)
        }
        return copy
    }

    private static Ellipse createTestEllipse(int width, int height) {
        double cx = width / 2.0
        double cy = height / 2.0
        double rx = width * 0.4
        double ry = height * 0.4
        double a = 1.0 / (rx * rx)
        double c = 1.0 / (ry * ry)
        double d = -2.0 * cx / (rx * rx)
        double e = -2.0 * cy / (ry * ry)
        double f = cx * cx / (rx * rx) + cy * cy / (ry * ry) - 1.0
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static boolean hasNaN(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isNaN(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean hasInfinity(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isInfinite(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean compareData(float[][] a, float[][] b, int width, int height, float tolerance) {
        int mismatches = 0
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def diff = Math.abs(a[y][x] - b[y][x])
                if (diff > tolerance) {
                    if (mismatches < 5) {
                        System.err.println("Mismatch at ($x, $y): CPU=${a[y][x]}, GPU=${b[y][x]}, diff=$diff")
                    }
                    mismatches++
                }
            }
        }
        if (mismatches > 0) {
            System.err.println("Total mismatches: $mismatches out of ${width * height}")
            return false
        }
        return true
    }

    private static <T> T runCpuOnly(Closure<T> closure) {
        def oldProp = System.getProperty("opencl.enabled")
        try {
            System.setProperty("opencl.enabled", "false")
            return closure.call()
        } finally {
            if (oldProp != null) {
                System.setProperty("opencl.enabled", oldProp)
            } else {
                System.clearProperty("opencl.enabled")
            }
        }
    }

    private static <T> T runGpuEnabled(Closure<T> closure) {
        def oldProp = System.getProperty("opencl.enabled")
        try {
            System.setProperty("opencl.enabled", "true")
            return closure.call()
        } finally {
            if (oldProp != null) {
                System.setProperty("opencl.enabled", oldProp)
            } else {
                System.clearProperty("opencl.enabled")
            }
        }
    }
}
