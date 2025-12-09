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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
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
public class BackgroundRemovalBenchmark {

    @Param({"512", "1024", "2048"})
    private int size;

    @Param({"1", "2", "3"})
    private int degree;

    private float[][] templateData;

    @Setup(Level.Trial)
    public void setupContext() {
        var random = new Random(42);
        templateData = new float[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                templateData[y][x] = random.nextFloat() * 65535;
            }
        }
    }

    private ImageWrapper32 createFreshImage() {
        var data = new float[size][size];
        for (int y = 0; y < size; y++) {
            System.arraycopy(templateData[y], 0, data[y], 0, size);
        }
        return new ImageWrapper32(size, size, data, new HashMap<>());
    }

    @Benchmark
    public void backgroundModel() {
        var image = createFreshImage();
        BackgroundRemoval.backgroundModel(image, degree, 2.0);
    }
}
