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
public class OpenCLImageMathBenchmark {

    @Param({"512", "1024", "2048", "4096"})
    private int size;

    private Image image;
    private Image image2;
    private OpenCLContext openclContext;
    private ImageMath cpuMath;
    private ImageMath gpuMath;
    private Kernel blurKernel;
    private Kernel sharpenKernel;
    private float[][] gridDx;
    private float[][] gridDy;
    private int gridStep;

    @Setup(Level.Trial)
    public void setupContext() {
        openclContext = OpenCLContext.tryCreate();
        cpuMath = new VectorApiImageMath();
        if (openclContext != null) {
            System.out.println("[OpenCL] Using GPU: " + openclContext.getCapabilities().deviceName());
            gpuMath = new OpenCLImageMath(openclContext, 0, 0, false);
        } else {
            System.out.println("[OpenCL] No GPU available, GPU benchmarks will be skipped");
        }
        blurKernel = BlurKernel.of(5);
        sharpenKernel = SharpenKernel.of(3);
    }

    @TearDown(Level.Trial)
    public void teardownContext() {
        if (openclContext != null) {
            openclContext.close();
        }
    }

    @Setup(Level.Invocation)
    public void setup() {
        var rand = new Random(42);
        float[][] data = new float[size][size];
        float[][] data2 = new float[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = rand.nextFloat() * 65535f;
                data2[y][x] = rand.nextFloat() * 65535f;
            }
        }
        image = new Image(size, size, data);
        image2 = new Image(size, size, data2);

        // Setup distortion grid for dedistort benchmarks
        gridStep = 64;
        int gridWidth = (size + gridStep) / gridStep + 1;
        int gridHeight = (size + gridStep) / gridStep + 1;
        gridDx = new float[gridHeight][gridWidth];
        gridDy = new float[gridHeight][gridWidth];
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                // Simulate typical atmospheric distortion (small random displacements)
                gridDx[gy][gx] = (rand.nextFloat() - 0.5f) * 4.0f;
                gridDy[gy][gx] = (rand.nextFloat() - 0.5f) * 4.0f;
            }
        }
    }

    // ========== Convolution Benchmarks ==========

    @Benchmark
    public Image cpuConvolveBlur5x5() {
        return cpuMath.convolve(image, blurKernel);
    }

    @Benchmark
    public Image gpuConvolveBlur5x5() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.convolve(image, blurKernel);
    }

    @Benchmark
    public Image cpuConvolveSharpen() {
        return cpuMath.convolve(image, sharpenKernel);
    }

    @Benchmark
    public Image gpuConvolveSharpen() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.convolve(image, sharpenKernel);
    }

    // ========== Rotation Benchmarks ==========

    @Benchmark
    public Image cpuRotate45() {
        return cpuMath.rotate(image, Math.toRadians(45), 0f, true);
    }

    @Benchmark
    public Image gpuRotate45() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.rotate(image, Math.toRadians(45), 0f, true);
    }

    // ========== Scale Benchmarks ==========

    @Benchmark
    public Image cpuRescaleUp() {
        int newSize = (int) (size * 1.5);
        return cpuMath.rescale(image, newSize, newSize);
    }

    @Benchmark
    public Image gpuRescaleUp() {
        if (gpuMath == null) {
            return null;
        }
        int newSize = (int) (size * 1.5);
        return gpuMath.rescale(image, newSize, newSize);
    }

    @Benchmark
    public Image cpuRescaleDown() {
        int newSize = size / 2;
        return cpuMath.rescale(image, newSize, newSize);
    }

    @Benchmark
    public Image gpuRescaleDown() {
        if (gpuMath == null) {
            return null;
        }
        int newSize = size / 2;
        return gpuMath.rescale(image, newSize, newSize);
    }

    // ========== Rotate + Scale Combined ==========

    @Benchmark
    public Image cpuRotateAndScale() {
        return cpuMath.rotateAndScale(image, Math.toRadians(5), 0f, 1.0, 1.1);
    }

    @Benchmark
    public Image gpuRotateAndScale() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.rotateAndScale(image, Math.toRadians(5), 0f, 1.0, 1.1);
    }

    // ========== Arithmetic Operations ==========

    @Benchmark
    public Image cpuMultiplyScalar() {
        return cpuMath.multiply(image, 1.5f);
    }

    @Benchmark
    public Image gpuMultiplyScalar() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.multiply(image, 1.5f);
    }

    @Benchmark
    public Image cpuDivideImages() {
        return cpuMath.divide(image, image2);
    }

    @Benchmark
    public Image gpuDivideImages() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.divide(image, image2);
    }

    @Benchmark
    public Image cpuMultiplyImages() {
        return cpuMath.multiply(image, image2);
    }

    @Benchmark
    public Image gpuMultiplyImages() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.multiply(image, image2);
    }

    // ========== Dedistort Benchmarks ==========

    @Benchmark
    public Image cpuDedistortLanczos() {
        return cpuMath.dedistort(image, gridDx, gridDy, gridStep, true);
    }

    @Benchmark
    public Image gpuDedistortLanczos() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.dedistort(image, gridDx, gridDy, gridStep, true);
    }

    @Benchmark
    public Image cpuDedistortBilinear() {
        return cpuMath.dedistort(image, gridDx, gridDy, gridStep, false);
    }

    @Benchmark
    public Image gpuDedistortBilinear() {
        if (gpuMath == null) {
            return null;
        }
        return gpuMath.dedistort(image, gridDx, gridDy, gridStep, false);
    }
}
