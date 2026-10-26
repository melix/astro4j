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
package me.champeau.a4j.math.opencl;

import me.champeau.a4j.math.correlation.CorrelationKernelLauncher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;

/**
 * Times individual OpenCL kernels with all their inputs already resident on
 * the GPU, so that the numbers reflect kernel execution rather than PCIe
 * transfers. Each benchmark enqueues the kernel(s) and waits for completion.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules", "jdk.incubator.vector",
    "--enable-native-access=ALL-UNNAMED"
})
@State(Scope.Benchmark)
public class OpenCLKernelBenchmark {
    private static final int IMAGE_SIZE = 2048;
    private static final int GRID_STEP = 64;
    private static final int FILTER_GRID = 256;
    private static final int KERNEL_15 = 15;

    private OpenCLContext context;
    private final List<Long> buffers = new ArrayList<>();

    private long image;
    private long image2;
    private long output;
    private long temp;
    private long kernel2d;
    private long rowKernel;
    private long colKernel;
    private long gridDx;
    private long gridDy;
    private int gridWidth;
    private int gridHeight;

    private long filterDx;
    private long filterDy;
    private long filterOutDx;
    private long filterOutDy;
    private long filterSampled;
    private long gaussianWeights;
    private int gaussianRadius;

    private long tiles32Ref;
    private long tiles32Tgt;
    private long results32;
    private int numTiles32;

    private long tiles64Ref;
    private long tiles64Tgt;
    private int numTiles64;
    private long complexA;
    private long complexB;
    private long real64;
    private long results64;

    private int[][] positions32;
    private int[][] positions64;
    private int[][] positions128;
    private int[] positions64Flat;

    @Setup(Level.Trial)
    public void setup() {
        context = OpenCLContext.tryCreate();
        if (context == null) {
            throw new IllegalStateException("No OpenCL device available");
        }
        System.out.println("[OpenCL] Using GPU: " + context.getCapabilities().deviceName());
        var random = new Random(42);
        int n = IMAGE_SIZE * IMAGE_SIZE;
        image = upload(randomFloats(random, n, 65535f));
        image2 = upload(randomFloats(random, n, 65535f));
        output = allocate(n);
        temp = allocate(n);
        kernel2d = upload(randomFloats(random, KERNEL_15 * KERNEL_15, 1f));
        rowKernel = upload(randomFloats(random, KERNEL_15, 1f));
        colKernel = upload(randomFloats(random, KERNEL_15, 1f));

        gridWidth = (IMAGE_SIZE + GRID_STEP) / GRID_STEP + 1;
        gridHeight = gridWidth;
        gridDx = upload(randomFloats(random, gridWidth * gridHeight, 4f, -2f));
        gridDy = upload(randomFloats(random, gridWidth * gridHeight, 4f, -2f));

        int filterN = FILTER_GRID * FILTER_GRID;
        filterDx = upload(randomFloats(random, filterN, 4f, -2f));
        filterDy = upload(randomFloats(random, filterN, 4f, -2f));
        filterOutDx = allocate(filterN);
        filterOutDy = allocate(filterN);
        var sampled = new int[filterN];
        for (int i = 0; i < filterN; i++) {
            sampled[i] = random.nextFloat() < 0.7f ? 1 : 0;
        }
        filterSampled = uploadInts(sampled);
        float sigma = 1.5f;
        gaussianRadius = (int) Math.ceil(3 * sigma);
        var weights = new float[2 * gaussianRadius + 1];
        for (int i = -gaussianRadius; i <= gaussianRadius; i++) {
            weights[i + gaussianRadius] = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
        }
        gaussianWeights = upload(weights);

        numTiles32 = 4096;
        tiles32Ref = upload(randomFloats(random, numTiles32 * 32 * 32, 65535f));
        tiles32Tgt = upload(randomFloats(random, numTiles32 * 32 * 32, 65535f));
        results32 = allocate(numTiles32 * 3);

        numTiles64 = 1024;
        tiles64Ref = upload(randomFloats(random, numTiles64 * 64 * 64, 65535f));
        tiles64Tgt = upload(randomFloats(random, numTiles64 * 64 * 64, 65535f));
        complexA = allocate(numTiles64 * 64 * 64 * 2);
        complexB = allocate(numTiles64 * 64 * 64 * 2);
        real64 = allocate(numTiles64 * 64 * 64);
        results64 = allocate(numTiles64 * 3);

        positions32 = positions(32);
        positions64 = positions(64);
        positions128 = positions(128);
        positions64Flat = new int[positions64.length * 2];
        for (int i = 0; i < positions64.length; i++) {
            positions64Flat[2 * i] = positions64[i][0];
            positions64Flat[2 * i + 1] = positions64[i][1];
        }
    }

    private static int[][] positions(int tileSize) {
        var list = new ArrayList<int[]>();
        for (int cy = tileSize / 2; cy + tileSize / 2 <= IMAGE_SIZE; cy += tileSize) {
            for (int cx = tileSize / 2; cx + tileSize / 2 <= IMAGE_SIZE; cx += tileSize) {
                list.add(new int[]{cx, cy});
            }
        }
        return list.toArray(new int[0][]);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        for (var buffer : buffers) {
            context.releaseBuffer(buffer);
        }
        buffers.clear();
        context.close();
    }

    private static float[] randomFloats(Random random, int n, float scale) {
        return randomFloats(random, n, scale, 0f);
    }

    private static float[] randomFloats(Random random, int n, float scale, float offset) {
        var data = new float[n];
        for (int i = 0; i < n; i++) {
            data[i] = random.nextFloat() * scale + offset;
        }
        return data;
    }

    private long allocate(int floats) {
        long buffer = context.allocateBuffer(floats * Float.BYTES, CL_MEM_READ_WRITE);
        buffers.add(buffer);
        return buffer;
    }

    private long upload(float[] data) {
        long buffer = context.allocateBuffer(data.length * Float.BYTES, CL_MEM_READ_ONLY);
        buffers.add(buffer);
        context.writeBuffer(buffer, data);
        return buffer;
    }

    private long uploadInts(int[] data) {
        long buffer = context.allocateBuffer(data.length * Integer.BYTES, CL_MEM_READ_ONLY);
        buffers.add(buffer);
        context.writeBufferInt(buffer, data);
        return buffer;
    }

    private void run(Consumer<GpuOp> body) {
        context.runOp(body);
    }

    // ========== Convolution ==========

    @Benchmark
    public void convolve2d15x15() {
        run(op -> op.kernel("convolution", "convolve2d")
                .arg(image).arg(kernel2d).arg(output)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(KERNEL_15).arg(KERNEL_15).arg(1f / 225f)
                .run(IMAGE_SIZE, IMAGE_SIZE));
    }

    @Benchmark
    public void convolveSeparable15() {
        run(op -> {
            op.kernel("convolution", "convolve1d_horizontal")
                    .arg(image).arg(rowKernel).arg(temp)
                    .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(KERNEL_15)
                    .run(IMAGE_SIZE, IMAGE_SIZE);
            op.kernel("convolution", "convolve1d_vertical")
                    .arg(temp).arg(colKernel).arg(output)
                    .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(KERNEL_15).arg(1f / 225f).arg(0).arg(1)
                    .run(IMAGE_SIZE, IMAGE_SIZE);
        });
    }

    @Benchmark
    public void convolve2d5x5() {
        run(op -> op.kernel("convolution", "convolve2d")
                .arg(image).arg(kernel2d).arg(output)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(5).arg(5).arg(1f / 25f)
                .run(IMAGE_SIZE, IMAGE_SIZE));
    }

    // ========== Dedistort ==========

    private void dedistort(String kernel) {
        run(op -> op.kernel("dedistort", kernel)
                .arg(image).arg(gridDx).arg(gridDy).arg(output)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(gridWidth).arg(gridHeight).arg(GRID_STEP)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(1.0f)
                .run(IMAGE_SIZE, IMAGE_SIZE));
    }

    @Benchmark
    public void dedistortLanczos() {
        dedistort("dedistort_sparse_lanczos");
    }

    @Benchmark
    public void dedistortBilinear() {
        dedistort("dedistort_sparse_bilinear");
    }

    // ========== Transforms ==========

    @Benchmark
    public void rotate45() {
        double angle = Math.toRadians(45);
        run(op -> op.kernel("transform", "rotate")
                .arg(image).arg(output)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(IMAGE_SIZE)
                .arg((float) Math.cos(angle)).arg((float) Math.sin(angle)).arg(0f)
                .run(IMAGE_SIZE, IMAGE_SIZE));
    }

    // ========== Distortion grid filter ==========

    private void madFilter(int halfWindow) {
        run(op -> op.kernel("distortion_filter", "mad_filter_grid_tiled")
                .arg(filterDx).arg(filterDy).arg(filterOutDx).arg(filterOutDy)
                .arg(FILTER_GRID).arg(FILTER_GRID).arg(halfWindow).arg(1.5f)
                .global(FILTER_GRID, FILTER_GRID).local(8, 8)
                .run());
    }

    @Benchmark
    public void madFilterHalfWindow2() {
        madFilter(2);
    }

    @Benchmark
    public void madFilterHalfWindow5() {
        madFilter(5);
    }

    @Benchmark
    public void convolve2d15x15Const() {
        run(op -> op.kernel("convolution", "convolve2d_const")
                .arg(image).arg(kernel2d).arg(output)
                .arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(KERNEL_15).arg(KERNEL_15).arg(1f / 225f)
                .run(IMAGE_SIZE, IMAGE_SIZE));
    }

    @Benchmark
    public void gaussianSmoothWorkGroup1() {
        run(op -> {
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(filterDx).arg(filterOutDx).arg(gaussianWeights)
                    .arg(FILTER_GRID).arg(FILTER_GRID).arg(gaussianRadius)
                    .global(FILTER_GRID, FILTER_GRID).local(1, 1)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(filterOutDx).arg(filterOutDy).arg(gaussianWeights)
                    .arg(FILTER_GRID).arg(FILTER_GRID).arg(gaussianRadius)
                    .global(FILTER_GRID, FILTER_GRID).local(1, 1)
                    .run();
        });
    }

    @Benchmark
    public void gaussianSmoothWorkGroup16() {
        run(op -> {
            op.kernel("distortion_filter", "gaussian_smooth_horizontal")
                    .arg(filterDx).arg(filterOutDx).arg(gaussianWeights)
                    .arg(FILTER_GRID).arg(FILTER_GRID).arg(gaussianRadius)
                    .global(FILTER_GRID, FILTER_GRID).local(16, 16)
                    .run();
            op.kernel("distortion_filter", "gaussian_smooth_vertical")
                    .arg(filterOutDx).arg(filterOutDy).arg(gaussianWeights)
                    .arg(FILTER_GRID).arg(FILTER_GRID).arg(gaussianRadius)
                    .global(FILTER_GRID, FILTER_GRID).local(16, 16)
                    .run();
        });
    }

    @Benchmark
    public void interpolateUnsampled() {
        run(op -> op.kernel("distortion_filter", "interpolate_unsampled")
                .arg(filterOutDx).arg(filterOutDy).arg(filterSampled)
                .arg(FILTER_GRID).arg(FILTER_GRID).arg(3)
                .global(FILTER_GRID, FILTER_GRID).local(16, 16)
                .run());
    }

    // ========== Correlation, 32x32 single kernel ==========

    @Benchmark
    public void batchedCorrelation32Phase() {
        run(op -> op.kernel("correlation", "batched_correlation_32")
                .arg(tiles32Ref).arg(tiles32Tgt).arg(results32).arg(numTiles32).arg(1)
                .global((long) numTiles32 * 32, 32).local(32, 32)
                .run());
    }

    @Benchmark
    public void batchedNcc32() {
        run(op -> op.kernel("correlation", "batched_ncc_32")
                .arg(tiles32Ref).arg(tiles32Tgt).arg(results32).arg(numTiles32)
                .global((long) numTiles32 * 32, 32).local(32, 32)
                .run());
    }

    @Benchmark
    public float[][] residentNcc32() {
        return CorrelationKernelLauncher.residentNcc(context, image, image2, IMAGE_SIZE, IMAGE_SIZE, positions32, 32);
    }

    // ========== Correlation, multi-kernel pipeline ==========

    @Benchmark
    public float[][] residentNcc64() {
        return CorrelationKernelLauncher.residentNcc(context, image, image2, IMAGE_SIZE, IMAGE_SIZE, positions64, 64);
    }

    @Benchmark
    public float[][] residentNcc128() {
        return CorrelationKernelLauncher.residentNcc(context, image, image2, IMAGE_SIZE, IMAGE_SIZE, positions128, 128);
    }

    @Benchmark
    public void phasePipeline64() {
        run(op -> CorrelationKernelLauncher.phasePipeline(op, tiles64Ref, tiles64Tgt,
                complexA, complexB, real64, results64, 64, numTiles64));
    }

    @Benchmark
    public void tileStatsFromImages64() {
        int numTiles = positions64.length;
        run(op -> {
            var posBuf = op.allocateBuffer(numTiles * 2 * Integer.BYTES, CL_MEM_READ_ONLY);
            var means = op.allocateBuffer(numTiles * Float.BYTES, CL_MEM_READ_WRITE);
            var sumSq = op.allocateBuffer(numTiles * Float.BYTES, CL_MEM_READ_WRITE);
            op.writeInts(posBuf, positions64Flat);
            for (long img : new long[]{image, image2}) {
                op.kernel("correlation", "compute_tile_stats_from_images")
                        .arg(img).arg(IMAGE_SIZE).arg(IMAGE_SIZE).arg(posBuf)
                        .arg(means).arg(sumSq).arg(64).arg(numTiles)
                        .global(256, numTiles).local(256, 1).run();
            }
        });
    }

    @Benchmark
    public void allocateResidentBuffers64() {
        int numTiles = positions64.length;
        int complexSize = numTiles * 64 * 64 * 2 * Float.BYTES;
        int realSize = numTiles * 64 * 64 * Float.BYTES;
        run(op -> {
            op.allocateBuffer(numTiles * 2 * Integer.BYTES, CL_MEM_READ_ONLY);
            for (int i = 0; i < 4; i++) {
                op.allocateBuffer(numTiles * Float.BYTES, CL_MEM_READ_WRITE);
            }
            op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            op.allocateBuffer(complexSize, CL_MEM_READ_WRITE);
            op.allocateBuffer(realSize, CL_MEM_READ_WRITE);
            op.allocateBuffer(numTiles * 3 * Float.BYTES, CL_MEM_READ_WRITE);
            op.allocateBuffer(numTiles * 3 * Float.BYTES, CL_MEM_READ_WRITE);
        });
    }

    @Benchmark
    public void shiftRealPreallocated64() {
        run(op -> op.kernel("correlation", "fft_shift_real")
                .arg(complexA).arg(real64).arg(64).arg(numTiles64).arg(1f / 4096f)
                .global(64, 64, numTiles64).run());
    }

    @Benchmark
    public void shiftRealFreshBuffers64() {
        run(op -> {
            var complex = op.allocateBuffer(numTiles64 * 64 * 64 * 2 * Float.BYTES, CL_MEM_READ_WRITE);
            var real = op.allocateBuffer(numTiles64 * 64 * 64 * Float.BYTES, CL_MEM_READ_WRITE);
            op.kernel("correlation", "fft_shift_real")
                    .arg(complex).arg(real).arg(64).arg(numTiles64).arg(1f / 4096f)
                    .global(64, 64, numTiles64).run();
        });
    }

    @Benchmark
    public void fft2d64Forward() {
        run(op -> CorrelationKernelLauncher.fft2D(op, complexA, 64, 6, numTiles64, -1));
    }
}
