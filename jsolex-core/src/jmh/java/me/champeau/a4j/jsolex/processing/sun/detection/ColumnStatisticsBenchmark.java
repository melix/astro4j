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
package me.champeau.a4j.jsolex.processing.sun.detection;

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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Performance guard for the row-major column-statistics helpers used during phenomena
 * detection at a representative frame size.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ColumnStatisticsBenchmark {

    @Param({"1456"})
    private int width;

    @Param({"200"})
    private int height;

    private float[][] data;
    private double[] averages;
    private int left;
    private int right;

    @Setup(Level.Trial)
    public void setup() {
        var random = new Random(42);
        data = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * 65535f;
            }
        }
        left = width / 40;
        right = width - width / 40;
        averages = new double[width];
        PhenomenaDetector.computeColumnAverages(data, left, right, height, averages);
    }

    @Benchmark
    public double[] averages() {
        var result = new double[width];
        PhenomenaDetector.computeColumnAverages(data, left, right, height, result);
        return result;
    }

    @Benchmark
    public double[] stddevs() {
        var result = new double[width];
        PhenomenaDetector.computeColumnStddevs(data, left, right, height, averages, result);
        return result;
    }
}
