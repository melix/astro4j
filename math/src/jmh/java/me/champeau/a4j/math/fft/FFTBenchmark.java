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
package me.champeau.a4j.math.fft;

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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules", "jdk.incubator.vector"
})
@State(Scope.Benchmark)
public class FFTBenchmark {

    @Param({"256", "512", "1024", "2048", "4096", "8192"})
    private int size;

    private double[] realData;
    private double[] imaginaryData;

    @Setup(Level.Invocation)
    public void setup() {
        realData = new double[size];
        imaginaryData = new double[size];
        for (int i = 0; i < size; i++) {
            realData[i] = Math.sin(2 * Math.PI * i / size);
            imaginaryData[i] = 0.0;
        }
    }

    @Benchmark
    public void vectorizedDoubleImplementation() {
        var fft = new VectorizedDoubleFFT(realData, imaginaryData);
        fft.transform();
    }

    @Benchmark
    public void commonsMathImplementation() {
        var fft = new CommonsMathFFT(realData, imaginaryData);
        fft.transform();
    }

    @Benchmark
    public void vectorizedDoubleInverse() {
        var fft = new VectorizedDoubleFFT(realData, imaginaryData);
        fft.transform();
        fft.inverseTransform();
    }

    @Benchmark
    public void commonsMathInverse() {
        var fft = new CommonsMathFFT(realData, imaginaryData);
        fft.transform();
        fft.inverseTransform();
    }
}
