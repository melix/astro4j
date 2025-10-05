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
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules", "jdk.incubator.vector"
})
@State(Scope.Benchmark)
public class FFT2DBenchmark {

    @Param({"64", "128", "256", "512"})
    private int size;

    private double[] data;

    @Setup(Level.Invocation)
    public void setup() {
        data = new double[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y * size + x] = Math.sin(2 * Math.PI * x / size) * Math.cos(2 * Math.PI * y / size);
            }
        }
    }

    @Benchmark
    public void vectorizedFFT2D() {
        var dataCopy = data.clone();
        var fft = new FastFourierTransform2DWrapper(dataCopy, size, size, true);
        fft.transform();
    }

    @Benchmark
    public void commonsMathFFT2D() {
        var dataCopy = data.clone();
        var fft = new FastFourierTransform2DWrapper(dataCopy, size, size, false);
        fft.transform();
    }

    @Benchmark
    public void vectorizedFFT2DInverse() {
        var dataCopy = data.clone();
        var fft = new FastFourierTransform2DWrapper(dataCopy, size, size, true);
        fft.transform();
        fft.inverseTransform();
    }

    @Benchmark
    public void commonsMathFFT2DInverse() {
        var dataCopy = data.clone();
        var fft = new FastFourierTransform2DWrapper(dataCopy, size, size, false);
        fft.transform();
        fft.inverseTransform();
    }

    private static class FastFourierTransform2DWrapper implements FastFourierTransform {
        private final double[] real;
        private final double[] imaginary;
        private final int width;
        private final int height;
        private final boolean useVectorized;

        FastFourierTransform2DWrapper(double[] real, int width, int height, boolean useVectorized) {
            this.real = real;
            this.imaginary = new double[real.length];
            this.width = width;
            this.height = height;
            this.useVectorized = useVectorized;
        }

        @Override
        public void transform() {
            transformRows();
            transformColumns();
        }

        @Override
        public void inverseTransform() {
            inverseColumns();
            inverseRows();
        }

        @Override
        public double[] real() {
            return real;
        }

        @Override
        public double[] imaginary() {
            return imaginary;
        }

        private void transformRows() {
            double[] r = new double[width];
            double[] i = new double[width];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                System.arraycopy(real, offset, r, 0, width);
                System.arraycopy(imaginary, offset, i, 0, width);
                FastFourierTransform fft = useVectorized
                    ? new VectorizedDoubleFFT(r, i)
                    : new CommonsMathFFT(r, i);
                fft.transform();
                System.arraycopy(r, 0, real, offset, width);
                System.arraycopy(i, 0, imaginary, offset, width);
            }
        }

        private void inverseRows() {
            double[] r = new double[width];
            double[] i = new double[width];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                System.arraycopy(real, offset, r, 0, width);
                for (int x = 0; x < width; x++) {
                    i[x] = -imaginary[x + offset];
                }
                FastFourierTransform fft = useVectorized
                    ? new VectorizedDoubleFFT(r, i)
                    : new CommonsMathFFT(r, i);
                fft.transform();
                for (int x = 0; x < width; x++) {
                    real[x + offset] = r[x] / width;
                    imaginary[x + offset] = -(i[x] / width);
                }
            }
        }

        private void transformColumns() {
            var r = new double[height];
            var i = new double[height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int offset = y * width + x;
                    r[y] = real[offset];
                    i[y] = imaginary[offset];
                }
                FastFourierTransform fft = useVectorized
                    ? new VectorizedDoubleFFT(r, i)
                    : new CommonsMathFFT(r, i);
                fft.transform();
                for (int y = 0; y < height; y++) {
                    int offset = y * width + x;
                    real[offset] = r[y];
                    imaginary[offset] = i[y];
                }
            }
        }

        private void inverseColumns() {
            double[] r = new double[height];
            double[] i = new double[height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int offset = y * width + x;
                    r[y] = real[offset];
                    i[y] = -imaginary[offset];
                }
                FastFourierTransform fft = useVectorized
                    ? new VectorizedDoubleFFT(r, i)
                    : new CommonsMathFFT(r, i);
                fft.transform();
                for (int y = 0; y < height; y++) {
                    int offset = y * width + x;
                    real[offset] = r[y] / height;
                    imaginary[offset] = -(i[y] / height);
                }
            }
        }
    }
}
