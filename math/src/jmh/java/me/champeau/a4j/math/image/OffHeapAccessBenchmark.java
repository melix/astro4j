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
package me.champeau.a4j.math.image;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * Compares the cost of typical image kernels on {@code float[][]}, on a flat
 * {@code float[]} and on {@link MemorySegment}s of various kinds (heap, native,
 * memory mapped file, and kernels which have seen several kinds of segments).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
public class OffHeapAccessBenchmark {
    private static final int HEIGHT = 2048;
    private static final int POLLUTION_SIZE = 96;
    private static final int POLLUTION_ROUNDS = 400;
    private static final float FACTOR = 1.5f;
    private static final float COS = (float) Math.cos(0.3);
    private static final float SIN = (float) Math.sin(0.3);

    @State(Scope.Thread)
    public static class Arrays2D {
        @Param({"2048", "2000"})
        public int width;

        float[][] in;
        float[][] out;

        @Setup
        public void setup() {
            in = new float[HEIGHT][width];
            out = new float[HEIGHT][width];
            var random = new Random(42);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < width; x++) {
                    in[y][x] = random.nextFloat() * 65535f;
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class Arrays1D {
        @Param({"2048", "2000"})
        public int width;

        float[] in;
        float[] out;

        @Setup
        public void setup() {
            in = new float[HEIGHT * width];
            out = new float[HEIGHT * width];
            var random = new Random(42);
            for (int i = 0; i < in.length; i++) {
                in[i] = random.nextFloat() * 65535f;
            }
        }
    }

    @State(Scope.Thread)
    public static class Segments {
        @Param({"heap", "confined", "shared", "auto", "mapped", "sharedAfterHeap", "mappedAfterNative", "mappedPolluted"})
        public String kind;

        @Param({"2048", "2000"})
        public int width;

        MemorySegment in;
        MemorySegment out;
        private final Resources resources = new Resources();

        @Setup
        public void setup() throws IOException {
            pollute(resources, switch (kind) {
                case "sharedAfterHeap" -> List.of("heap");
                case "mappedAfterNative" -> List.of("confined");
                case "mappedPolluted" -> List.of("heap", "confined");
                default -> List.<String>of();
            });
            in = resources.allocate(kind, width, HEIGHT);
            out = resources.allocate(kind, width, HEIGHT);
        }

        @TearDown
        public void tearDown() throws IOException {
            resources.close();
        }
    }

    @State(Scope.Thread)
    public static class NativeSegments {
        @Param({"confined", "shared", "mapped"})
        public String kind;

        @Param({"2048", "2000"})
        public int width;

        MemorySegment in;
        MemorySegment out;
        private final Resources resources = new Resources();

        @Setup
        public void setup() throws IOException {
            in = resources.allocate(kind, width, HEIGHT);
            out = resources.allocate(kind, width, HEIGHT);
        }

        @TearDown
        public void tearDown() throws IOException {
            resources.close();
        }
    }

    private static final class Resources {
        private final List<Arena> arenas = new ArrayList<>();
        private final List<FileChannel> channels = new ArrayList<>();

        MemorySegment allocate(String kind, int width, int height) throws IOException {
            long bytes = (long) width * height * Float.BYTES;
            var segment = switch (kind) {
                case "heap" -> MemorySegment.ofArray(new float[width * height]);
                case "confined" -> closeable(Arena.ofConfined()).allocate(bytes, 64);
                case "shared", "sharedAfterHeap" -> closeable(Arena.ofShared()).allocate(bytes, 64);
                case "auto" -> Arena.ofAuto().allocate(bytes, 64);
                case "mapped", "mappedAfterNative", "mappedPolluted" -> map(bytes);
                default -> throw new IllegalArgumentException(kind);
            };
            var random = new Random(42);
            long count = (long) width * height;
            for (long i = 0; i < count; i++) {
                segment.setAtIndex(JAVA_FLOAT, i, random.nextFloat() * 65535f);
            }
            return segment;
        }

        private Arena closeable(Arena arena) {
            arenas.add(arena);
            return arena;
        }

        private MemorySegment map(long bytes) throws IOException {
            var file = Files.createTempFile("offheap-bench", ".img");
            var channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
            channels.add(channel);
            return channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes, closeable(Arena.ofShared()));
        }

        void close() throws IOException {
            for (var arena : arenas) {
                arena.close();
            }
            for (var channel : channels) {
                channel.close();
            }
        }
    }

    private static void pollute(Resources resources, List<String> kinds) throws IOException {
        for (var kind : kinds) {
            var in = resources.allocate(kind, POLLUTION_SIZE, POLLUTION_SIZE);
            var out = resources.allocate(kind, POLLUTION_SIZE, POLLUTION_SIZE);
            float sink = 0;
            for (int i = 0; i < POLLUTION_ROUNDS; i++) {
                scale(in, out, POLLUTION_SIZE, POLLUTION_SIZE);
                box(in, out, POLLUTION_SIZE, POLLUTION_SIZE);
                rotate(in, out, POLLUTION_SIZE, POLLUTION_SIZE);
                box(new Pixels(in, POLLUTION_SIZE), new Pixels(out, POLLUTION_SIZE), POLLUTION_SIZE, POLLUTION_SIZE);
                rotate(new Pixels(in, POLLUTION_SIZE), new Pixels(out, POLLUTION_SIZE), POLLUTION_SIZE, POLLUTION_SIZE);
                sink += columnSum(in, POLLUTION_SIZE, POLLUTION_SIZE);
            }
            if (Float.isNaN(sink)) {
                throw new IllegalStateException();
            }
        }
    }

    @Benchmark
    public float[][] scaleArray2D(Arrays2D state) {
        scale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[] scaleArray1D(Arrays1D state) {
        scale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment scaleSegment(Segments state) {
        scale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[][] boxArray2D(Arrays2D state) {
        box(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[] boxArray1D(Arrays1D state) {
        box(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment boxSegment(Segments state) {
        box(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[][] rotateArray2D(Arrays2D state) {
        rotate(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[] rotateArray1D(Arrays1D state) {
        rotate(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment rotateSegment(Segments state) {
        rotate(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float columnSumArray2D(Arrays2D state) {
        return columnSum(state.in, state.width, HEIGHT);
    }

    @Benchmark
    public float columnSumArray1D(Arrays1D state) {
        return columnSum(state.in, state.width, HEIGHT);
    }

    @Benchmark
    public float columnSumSegment(Segments state) {
        return columnSum(state.in, state.width, HEIGHT);
    }

    @Benchmark
    public float[][] vectorScaleArray2D(Arrays2D state) {
        VectorKernels.vectorScale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public float[] vectorScaleArray1D(Arrays1D state) {
        VectorKernels.vectorScale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment vectorScaleSegment(NativeSegments state) {
        VectorKernels.vectorScale(state.in, state.out, state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment boxAccessor(Segments state) {
        box(new Pixels(state.in, state.width), new Pixels(state.out, state.width), state.width, HEIGHT);
        return state.out;
    }

    @Benchmark
    public MemorySegment rotateAccessor(Segments state) {
        rotate(new Pixels(state.in, state.width), new Pixels(state.out, state.width), state.width, HEIGHT);
        return state.out;
    }

    private static void scale(float[][] in, float[][] out, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y][x] = in[y][x] * FACTOR;
            }
        }
    }

    private static void scale(float[] in, float[] out, int width, int height) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                out[row + x] = in[row + x] * FACTOR;
            }
        }
    }

    private static void scale(MemorySegment in, MemorySegment out, int width, int height) {
        for (int y = 0; y < height; y++) {
            long row = (long) y * width;
            for (int x = 0; x < width; x++) {
                out.setAtIndex(JAVA_FLOAT, row + x, in.getAtIndex(JAVA_FLOAT, row + x) * FACTOR);
            }
        }
    }

    private static void box(float[][] in, float[][] out, int width, int height) {
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                out[y][x] = (in[y - 1][x - 1] + in[y - 1][x] + in[y - 1][x + 1]
                        + in[y][x - 1] + in[y][x] + in[y][x + 1]
                        + in[y + 1][x - 1] + in[y + 1][x] + in[y + 1][x + 1]) / 9f;
            }
        }
    }

    private static void box(float[] in, float[] out, int width, int height) {
        for (int y = 1; y < height - 1; y++) {
            int up = (y - 1) * width;
            int mid = y * width;
            int down = (y + 1) * width;
            for (int x = 1; x < width - 1; x++) {
                out[mid + x] = (in[up + x - 1] + in[up + x] + in[up + x + 1]
                        + in[mid + x - 1] + in[mid + x] + in[mid + x + 1]
                        + in[down + x - 1] + in[down + x] + in[down + x + 1]) / 9f;
            }
        }
    }

    private static void box(MemorySegment in, MemorySegment out, int width, int height) {
        for (int y = 1; y < height - 1; y++) {
            long up = (long) (y - 1) * width;
            long mid = (long) y * width;
            long down = (long) (y + 1) * width;
            for (int x = 1; x < width - 1; x++) {
                float sum = in.getAtIndex(JAVA_FLOAT, up + x - 1) + in.getAtIndex(JAVA_FLOAT, up + x) + in.getAtIndex(JAVA_FLOAT, up + x + 1)
                        + in.getAtIndex(JAVA_FLOAT, mid + x - 1) + in.getAtIndex(JAVA_FLOAT, mid + x) + in.getAtIndex(JAVA_FLOAT, mid + x + 1)
                        + in.getAtIndex(JAVA_FLOAT, down + x - 1) + in.getAtIndex(JAVA_FLOAT, down + x) + in.getAtIndex(JAVA_FLOAT, down + x + 1);
                out.setAtIndex(JAVA_FLOAT, mid + x, sum / 9f);
            }
        }
    }

    private static void rotate(float[][] in, float[][] out, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sx = COS * (x - cx) - SIN * (y - cy) + cx;
                float sy = SIN * (x - cx) + COS * (y - cy) + cy;
                int x0 = (int) sx;
                int y0 = (int) sy;
                float value = 0;
                if (sx >= 0 && sy >= 0 && x0 < width - 1 && y0 < height - 1) {
                    float fx = sx - x0;
                    float fy = sy - y0;
                    value = (1 - fx) * (1 - fy) * in[y0][x0] + fx * (1 - fy) * in[y0][x0 + 1]
                            + (1 - fx) * fy * in[y0 + 1][x0] + fx * fy * in[y0 + 1][x0 + 1];
                }
                out[y][x] = value;
            }
        }
    }

    private static void rotate(float[] in, float[] out, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float sx = COS * (x - cx) - SIN * (y - cy) + cx;
                float sy = SIN * (x - cx) + COS * (y - cy) + cy;
                int x0 = (int) sx;
                int y0 = (int) sy;
                float value = 0;
                if (sx >= 0 && sy >= 0 && x0 < width - 1 && y0 < height - 1) {
                    float fx = sx - x0;
                    float fy = sy - y0;
                    int source = y0 * width + x0;
                    value = (1 - fx) * (1 - fy) * in[source] + fx * (1 - fy) * in[source + 1]
                            + (1 - fx) * fy * in[source + width] + fx * fy * in[source + width + 1];
                }
                out[row + x] = value;
            }
        }
    }

    private static void rotate(MemorySegment in, MemorySegment out, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        for (int y = 0; y < height; y++) {
            long row = (long) y * width;
            for (int x = 0; x < width; x++) {
                float sx = COS * (x - cx) - SIN * (y - cy) + cx;
                float sy = SIN * (x - cx) + COS * (y - cy) + cy;
                int x0 = (int) sx;
                int y0 = (int) sy;
                float value = 0;
                if (sx >= 0 && sy >= 0 && x0 < width - 1 && y0 < height - 1) {
                    float fx = sx - x0;
                    float fy = sy - y0;
                    long source = (long) y0 * width + x0;
                    value = (1 - fx) * (1 - fy) * in.getAtIndex(JAVA_FLOAT, source) + fx * (1 - fy) * in.getAtIndex(JAVA_FLOAT, source + 1)
                            + (1 - fx) * fy * in.getAtIndex(JAVA_FLOAT, source + width) + fx * fy * in.getAtIndex(JAVA_FLOAT, source + width + 1);
                }
                out.setAtIndex(JAVA_FLOAT, row + x, value);
            }
        }
    }

    private record Pixels(MemorySegment segment, int width) {
        float get(int x, int y) {
            return segment.getAtIndex(JAVA_FLOAT, (long) y * width + x);
        }

        void set(int x, int y, float value) {
            segment.setAtIndex(JAVA_FLOAT, (long) y * width + x, value);
        }
    }

    private static void box(Pixels in, Pixels out, int width, int height) {
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float sum = in.get(x - 1, y - 1) + in.get(x, y - 1) + in.get(x + 1, y - 1)
                        + in.get(x - 1, y) + in.get(x, y) + in.get(x + 1, y)
                        + in.get(x - 1, y + 1) + in.get(x, y + 1) + in.get(x + 1, y + 1);
                out.set(x, y, sum / 9f);
            }
        }
    }

    private static void rotate(Pixels in, Pixels out, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sx = COS * (x - cx) - SIN * (y - cy) + cx;
                float sy = SIN * (x - cx) + COS * (y - cy) + cy;
                int x0 = (int) sx;
                int y0 = (int) sy;
                float value = 0;
                if (sx >= 0 && sy >= 0 && x0 < width - 1 && y0 < height - 1) {
                    float fx = sx - x0;
                    float fy = sy - y0;
                    value = (1 - fx) * (1 - fy) * in.get(x0, y0) + fx * (1 - fy) * in.get(x0 + 1, y0)
                            + (1 - fx) * fy * in.get(x0, y0 + 1) + fx * fy * in.get(x0 + 1, y0 + 1);
                }
                out.set(x, y, value);
            }
        }
    }

    private static float columnSum(float[][] in, int width, int height) {
        float total = 0;
        for (int x = 0; x < width; x++) {
            float sum = 0;
            for (int y = 0; y < height; y++) {
                sum += in[y][x];
            }
            total += sum;
        }
        return total;
    }

    private static float columnSum(float[] in, int width, int height) {
        float total = 0;
        for (int x = 0; x < width; x++) {
            float sum = 0;
            for (int y = 0; y < height; y++) {
                sum += in[y * width + x];
            }
            total += sum;
        }
        return total;
    }

    private static float columnSum(MemorySegment in, int width, int height) {
        float total = 0;
        for (int x = 0; x < width; x++) {
            float sum = 0;
            for (int y = 0; y < height; y++) {
                sum += in.getAtIndex(JAVA_FLOAT, (long) y * width + x);
            }
            total += sum;
        }
        return total;
    }

    private static final class VectorKernels {
        private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
        private static final ByteOrder ORDER = ByteOrder.nativeOrder();

        static void vectorScale(float[][] in, float[][] out, int width, int height) {
            int bound = SPECIES.loopBound(width);
            for (int y = 0; y < height; y++) {
                var source = in[y];
                var target = out[y];
                int x = 0;
                for (; x < bound; x += SPECIES.length()) {
                    FloatVector.fromArray(SPECIES, source, x).mul(FACTOR).intoArray(target, x);
                }
                for (; x < width; x++) {
                    target[x] = source[x] * FACTOR;
                }
            }
        }

        static void vectorScale(float[] in, float[] out, int width, int height) {
            int bound = SPECIES.loopBound(width);
            for (int y = 0; y < height; y++) {
                int row = y * width;
                int x = 0;
                for (; x < bound; x += SPECIES.length()) {
                    FloatVector.fromArray(SPECIES, in, row + x).mul(FACTOR).intoArray(out, row + x);
                }
                for (; x < width; x++) {
                    out[row + x] = in[row + x] * FACTOR;
                }
            }
        }

        static void vectorScale(MemorySegment in, MemorySegment out, int width, int height) {
            int bound = SPECIES.loopBound(width);
            for (int y = 0; y < height; y++) {
                long row = (long) y * width;
                int x = 0;
                for (; x < bound; x += SPECIES.length()) {
                    long offset = (row + x) * Float.BYTES;
                    FloatVector.fromMemorySegment(SPECIES, in, offset, ORDER).mul(FACTOR).intoMemorySegment(out, offset, ORDER);
                }
                for (; x < width; x++) {
                    out.setAtIndex(JAVA_FLOAT, row + x, in.getAtIndex(JAVA_FLOAT, row + x) * FACTOR);
                }
            }
        }
    }
}
