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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exercises the hottest image-processing operations on small synthetic data at startup so the
 * JIT compiles (and intrinsifies the Vector API code) before the first real reconstruction.
 */
public final class JitWarmup {
    private static final Logger LOGGER = LoggerFactory.getLogger(JitWarmup.class);
    private static final long BUDGET_NANOS = 2_000_000_000L;
    private static final int MAX_ITERATIONS = 200_000;

    @SuppressWarnings("unused")
    private static volatile double sink;

    private JitWarmup() {
    }

    public static void warmup() {
        try {
            doWarmup();
        } catch (Throwable t) {
            LOGGER.debug("JIT warmup skipped: {}", t.toString());
        }
    }

    private static void doWarmup() {
        var imageMath = ImageMath.newCpuInstance();
        int width = 512;
        int height = 256;
        var data = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (x * 31 + y * 17) % 60000 + 1;
            }
        }
        var image = new Image(width, height, data);
        var output = new float[height][width];
        var tmp = new float[height][width];
        var average = new float[height][width];

        double acc = 0;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int iterations = 0;
        while (iterations < MAX_ITERATIONS && System.nanoTime() < deadline) {
            imageMath.gaussianBlur(image, output, tmp, 0, width);
            acc += output[height / 2][width / 2];
            acc += imageMath.averageOf(data);
            imageMath.incrementalAverage(data, average, iterations + 1);
            acc += average[0][0];
            iterations++;
        }
        sink = acc;
        LOGGER.debug("JIT warmup completed {} iterations", iterations);
    }
}
