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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.opencl.OpenCLContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules", "jdk.incubator.vector",
    "--enable-native-access=ALL-UNNAMED"
})
@State(Scope.Benchmark)
public class StretchingBenchmark {

    @Param({"512", "1024", "2048"})
    private int size;

    private OpenCLContext context;
    private float[][] templateData;

    @Setup(Level.Trial)
    public void setupContext() {
        context = OpenCLContext.tryCreate();
        if (context != null) {
            System.out.println("[OpenCL] Using GPU: " + context.getCapabilities().deviceName());
        } else {
            System.out.println("[OpenCL] No GPU available, GPU benchmarks will be skipped");
        }

        var random = new Random(42);
        templateData = new float[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                templateData[y][x] = random.nextFloat() * 65535;
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private ImageWrapper32 createFreshImage() {
        var data = new float[size][size];
        for (int y = 0; y < size; y++) {
            System.arraycopy(templateData[y], 0, data[y], 0, size);
        }
        return new ImageWrapper32(size, size, data, new HashMap<>());
    }

    // ===== GammaStrategy (GPU beneficial) =====

    @Benchmark
    public void gammaCPU() {
        var image = createFreshImage();
        new GammaStrategy(0.7).stretchCPU(image);
    }

    @Benchmark
    public void gammaGPU() {
        if (context == null) {
            return;
        }
        var image = createFreshImage();
        new GammaStrategy(0.7).stretchGPU(context, image);
    }

    // ===== ArcsinhStretchingStrategy (GPU beneficial) =====

    @Benchmark
    public void arcsinhCPU() {
        var image = createFreshImage();
        new ArcsinhStretchingStrategy(1000, 50, 100).stretchCPU(image);
    }

    @Benchmark
    public void arcsinhGPU() {
        if (context == null) {
            return;
        }
        var image = createFreshImage();
        new ArcsinhStretchingStrategy(1000, 50, 100).stretchGPU(context, image);
    }
}
