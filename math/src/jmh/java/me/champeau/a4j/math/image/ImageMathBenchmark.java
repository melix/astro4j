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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 1)
@Measurement(iterations = 2)
@Fork(1)
public class ImageMathBenchmark {

    @State(Scope.Benchmark)
    public static class Data {
        @Param({"16", "32", "512", "1024", "50", "851"})
        public int width = 851;

        @Param({"16", "128", "50", "851"})
        public int height = 851;

        private final ImageMath fallback = new FallbackImageMath();
        private final ImageMath vector = new VectorApiImageMath();
        private Image image;
        private float[][] output;
        private float[][] tmp;

        @Setup
        public void setup() {
            float[][] data = new float[height][width];
            var random = new java.util.Random(42);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    data[y][x] = random.nextFloat() * 65535f;
                }
            }
            image = new Image(width, height, data);
            output = new float[height][width];
            tmp = new float[height][width];
        }
    }

    @Benchmark
    public void averageOfLineFallback(Blackhole blackhole, Data data) {
        blackhole.consume(data.fallback.lineAverages(data.image));
    }

    @Benchmark
    public void averageOfLineVector(Blackhole blackhole, Data data) {
        blackhole.consume(data.vector.lineAverages(data.image));
    }

    @Benchmark
    public void gaussianConvolveFallback(Blackhole blackhole, Data data) {
        blackhole.consume(data.fallback.convolve(data.image, Kernel33.GAUSSIAN_BLUR, data.output));
    }

    @Benchmark
    public void gaussianConvolveVector(Blackhole blackhole, Data data) {
        blackhole.consume(data.vector.convolve(data.image, Kernel33.GAUSSIAN_BLUR, data.output));
    }

    @Benchmark
    public void gaussianBlurFallback(Blackhole blackhole, Data data) {
        blackhole.consume(data.fallback.gaussianBlur(data.image, data.output));
    }

    @Benchmark
    public void gaussianBlurVector(Blackhole blackhole, Data data) {
        blackhole.consume(data.vector.gaussianBlur(data.image, data.output));
    }

    @Benchmark
    public void gaussianBlurRangedVector(Blackhole blackhole, Data data) {
        // Blur only the central ~75% of columns (representative of the detected sun region).
        int from = data.width / 8;
        int to = data.width - data.width / 8;
        blackhole.consume(data.vector.gaussianBlur(data.image, data.output, from, to));
    }

    @Benchmark
    public void gaussianBlurRangedReuseVector(Blackhole blackhole, Data data) {
        // As above, but reusing a pre-allocated scratch buffer (no per-call allocation).
        int from = data.width / 8;
        int to = data.width - data.width / 8;
        blackhole.consume(data.vector.gaussianBlur(data.image, data.output, data.tmp, from, to));
    }
}
