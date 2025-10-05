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
package me.champeau.a4j.math.fft

import spock.lang.Specification
import spock.lang.Unroll

class FFTComparisonTest extends Specification {
    private static final double EPSILON = 0.001

    @Unroll
    def "forward and inverse FFT are exact inverses"() {
        given:
        def size = 1024
        def originalReal = new double[size]
        def originalImag = new double[size]
        for (int i = 0; i < size; i++) {
            originalReal[i] = Math.sin(2 * Math.PI * i / size) * Math.exp(-i / 100.0)
            originalImag[i] = Math.cos(4 * Math.PI * i / size) * Math.exp(-i / 200.0)
        }

        when:
        def fft = FastFourierTransform.ofComplex(originalReal.clone(), originalImag.clone())
        fft.transform()
        fft.inverseTransform()

        then:
        arraysEqual(fft.real(), originalReal, "Real part after round trip")
        arraysEqual(fft.imaginary(), originalImag, "Imaginary part after round trip")
    }

    @Unroll
    def "2D FFT produces consistent results for size #size"() {
        given:
        def data = new double[size * size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y * size + x] = Math.sin(2 * Math.PI * x / size) * Math.cos(2 * Math.PI * y / size)
            }
        }
        def originalData = data.clone()

        when:
        def fft = FastFourierTransform.ofArray2D(data, size, size)
        fft.transform()
        fft.inverseTransform()

        then:
        arraysEqual(fft.real(), originalData, "2D FFT round trip")
        fft.imaginary().every { Math.abs(it) < EPSILON }

        where:
        size << [4, 8, 16, 32, 64, 128]
    }

    @Unroll
    def "float FFT forward and inverse are exact inverses"() {
        given:
        def size = 1024
        def originalReal = new float[size]
        def originalImag = new float[size]
        for (int i = 0; i < size; i++) {
            originalReal[i] = (float) (Math.sin(2 * Math.PI * i / size) * Math.exp(-i / 100.0))
            originalImag[i] = (float) (Math.cos(4 * Math.PI * i / size) * Math.exp(-i / 200.0))
        }

        when:
        def fft = FastFourierTransform.ofComplex(originalReal.clone(), originalImag.clone())
        fft.transform()
        fft.inverseTransform()

        then:
        arraysEqual(fft.real(), originalReal, "Float real part after round trip")
        arraysEqual(fft.imaginary(), originalImag, "Float imaginary part after round trip")
    }

    @Unroll
    def "float and double FFT produce equivalent results for size #size"() {
        given:
        def floatReal = new float[size]
        def floatImag = new float[size]
        def doubleReal = new double[size]
        def doubleImag = new double[size]

        for (int i = 0; i < size; i++) {
            double value = Math.sin(2 * Math.PI * i / size) * Math.exp(-i / 100.0)
            floatReal[i] = (float) value
            doubleReal[i] = value
        }

        when:
        def floatFFT = FastFourierTransform.ofComplex(floatReal, floatImag)
        def doubleFFT = FastFourierTransform.ofComplex(doubleReal, doubleImag)
        floatFFT.transform()
        doubleFFT.transform()

        then:
        for (int i = 0; i < size; i++) {
            def errorReal = Math.abs(floatFFT.real()[i] - (float) doubleFFT.real()[i])
            def errorImag = Math.abs(floatFFT.imaginary()[i] - (float) doubleFFT.imaginary()[i])
            assert errorReal < EPSILON : "Real part mismatch at index $i: float=${floatFFT.real()[i]}, double=${doubleFFT.real()[i]}"
            assert errorImag < EPSILON : "Imag part mismatch at index $i: float=${floatFFT.imaginary()[i]}, double=${doubleFFT.imaginary()[i]}"
        }

        where:
        size << [256, 512, 1024, 2048]
    }

    private static boolean arraysEqual(double[] actual, double[] expected, String description) {
        if (actual.length != expected.length) {
            throw new AssertionError("$description: lengths differ - expected ${expected.length}, got ${actual.length}")
        }
        for (int i = 0; i < actual.length; i++) {
            def error = Math.abs(actual[i] - expected[i])
            if (error > EPSILON) {
                throw new AssertionError("$description: at index $i, expected ${expected[i]}, got ${actual[i]} (error: $error)")
            }
        }
        return true
    }

    private static boolean arraysEqual(float[] actual, float[] expected, String description) {
        if (actual.length != expected.length) {
            throw new AssertionError("$description: lengths differ - expected ${expected.length}, got ${actual.length}")
        }
        for (int i = 0; i < actual.length; i++) {
            def error = Math.abs(actual[i] - expected[i])
            if (error > EPSILON) {
                throw new AssertionError("$description: at index $i, expected ${expected[i]}, got ${actual[i]} (error: $error)")
            }
        }
        return true
    }
}
