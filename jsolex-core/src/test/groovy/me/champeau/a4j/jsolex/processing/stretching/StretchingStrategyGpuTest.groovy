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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.opencl.OpenCLContext
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Requires({ OpenCLSupport.isAvailable() })
class StretchingStrategyGpuTest extends Specification {

    @Shared
    OpenCLContext context

    def setupSpec() {
        context = OpenCLContext.tryCreate()
    }

    def cleanupSpec() {
        if (context != null) {
            context.close()
        }
    }

    @Unroll
    def "GammaStrategy GPU matches CPU for gamma=#gamma"() {
        given:
        def cpuData = createRandomImage(512, 512)
        def gpuData = copyData(cpuData)
        def cpuImage = new ImageWrapper32(512, 512, cpuData, [:])
        def gpuImage = new ImageWrapper32(512, 512, gpuData, [:])
        def strategy = new GammaStrategy(gamma)

        when:
        strategy.stretchCPU(cpuImage)
        if (context != null) {
            strategy.stretchGPU(context, gpuImage)
        }

        then:
        if (context != null) {
            compareImages(cpuData, gpuData, 1e-2f)
        }

        where:
        gamma << [0.5, 0.7, 1.0, 1.5, 2.0]
    }

    @Unroll
    def "ArcsinhStretchingStrategy GPU matches CPU for blackPoint=#bp stretch=#str"() {
        given:
        def cpuData = createRandomImage(512, 512)
        def gpuData = copyData(cpuData)
        def cpuImage = new ImageWrapper32(512, 512, cpuData, [:])
        def gpuImage = new ImageWrapper32(512, 512, gpuData, [:])
        def strategy = new ArcsinhStretchingStrategy(bp as float, str as float, 100)

        when:
        strategy.stretchCPU(cpuImage)
        if (context != null) {
            strategy.stretchGPU(context, gpuImage)
        }

        then:
        if (context != null) {
            compareImages(cpuData, gpuData, 1e-2f)
        }

        where:
        bp   | str
        500  | 10
        1000 | 50
        2000 | 100
    }

    private static float[][] createRandomImage(int width, int height) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * 65535
            }
        }
        return data
    }

    private static float[][] copyData(float[][] data) {
        int height = data.length
        int width = data[0].length
        var copy = new float[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, copy[y], 0, width)
        }
        return copy
    }

    private static boolean compareImages(float[][] cpuData, float[][] gpuData, float tolerance) {
        int height = cpuData.length
        int width = cpuData[0].length
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float cpu = cpuData[y][x]
                float gpu = gpuData[y][x]
                float diff = Math.abs(cpu - gpu)
                float relDiff = cpu != 0 ? diff / Math.abs(cpu) : diff
                if (relDiff > tolerance && diff > 1.0f) {
                    throw new AssertionError("Mismatch at ($x, $y): CPU=$cpu, GPU=$gpu, diff=$diff, relDiff=$relDiff")
                }
            }
        }
        return true
    }
}
