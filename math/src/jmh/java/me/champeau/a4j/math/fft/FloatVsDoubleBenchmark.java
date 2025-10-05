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
public class FloatVsDoubleBenchmark {

    @Param({"1024", "2048", "4096", "8192"})
    private int size;

    private float[] floatData;
    private double[] doubleData;

    @Setup(Level.Invocation)
    public void setup() {
        floatData = new float[size];
        doubleData = new double[size];
        for (int i = 0; i < size; i++) {
            float value = (float) Math.sin(2 * Math.PI * i / size);
            floatData[i] = value;
            doubleData[i] = value;
        }
    }

    @Benchmark
    public void vectorizedFloatFFT() {
        var floatCopy = floatData.clone();
        var fft = FastFourierTransform.ofComplex(floatCopy, new float[size]);
        fft.transform();
    }

    @Benchmark
    public void commonsMathFloatFFT() {
        var floatCopy = floatData.clone();
        var fft = new CommonsMathFloatFFT(floatCopy, new float[size]);
        fft.transform();
    }

    @Benchmark
    public void vectorizedDoubleFFT() {
        var doubleCopy = doubleData.clone();
        var fft = FastFourierTransform.ofComplex(doubleCopy, new double[size]);
        fft.transform();
    }

    @Benchmark
    public void commonsMathDoubleFFT() {
        var doubleCopy = doubleData.clone();
        var fft = new CommonsMathFFT(doubleCopy, new double[size]);
        fft.transform();
    }
}
