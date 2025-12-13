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

import me.champeau.a4j.math.VectorApiSupport;

import static me.champeau.a4j.math.fft.FFTSupport.isPowerOf2;
import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;

/**
 * Interface for Fast Fourier transforms.
 * Implementations will mutate the arguments.
 */
public interface FastFourierTransform {

    /**
     * Returns a single dimensional Fourier transform.
     * @param real the array of real numbers to transform.
     * @return the Fourier transform
     */
    static FastFourierTransform ofReal(double[] real) {
        return ofComplex(real, new double[real.length]);
    }

    /**
     * Returns a single dimensional Fourier transform
     * @param real the real part of complex numbers
     * @param imaginary the imaginary part of complex numbers
     * @return the Fourier transform
     */
    static FastFourierTransform ofComplex(double[] real, double[] imaginary) {
        int n = real.length;
        if (imaginary.length != n) {
            throw new IllegalArgumentException("Both real and imaginary parts must have the same length");
        }
        if (!isPowerOf2(n)) {
            throw new IllegalArgumentException("Input arrays length must be a power of 2");
        }
        if (VectorApiSupport.isEnabled()) {
            return new VectorizedDoubleFFT(real, imaginary);
        }
        return new CommonsMathFFT(real, imaginary);
    }

    /**
     * Returns a single dimensional Fourier transform for float arrays.
     * @param real the real part of complex numbers
     * @param imaginary the imaginary part of complex numbers
     * @return the Fourier transform
     */
    static FloatFFT ofComplex(float[] real, float[] imaginary) {
        int n = real.length;
        if (imaginary.length != n) {
            throw new IllegalArgumentException("Both real and imaginary parts must have the same length");
        }
        if (!isPowerOf2(n)) {
            throw new IllegalArgumentException("Input arrays length must be a power of 2");
        }
        if (VectorApiSupport.isEnabled()) {
            return new VectorizedFloatFFT(real, imaginary);
        }
        return new CommonsMathFloatFFT(real, imaginary);
    }

    /**
     * Returns a 2D Fourier transform
     * @param array the array representing the data
     * @param width the width
     * @param height the height
     * @return the Fourier transform
     */
    static FastFourierTransform ofArray2D(double[] array, int width, int height) {
        if (!isPowerOf2(width)) {
            throw new IllegalArgumentException("Width must be a power of 2");
        }
        if (!isPowerOf2(height)) {
            throw new IllegalArgumentException("Height must be a power of 2");
        }
        return new FastFourierTransform2D(array, width, height);
    }

    /**
     * Pads data so that its length is a power of 2
     * @param data the data
     * @return the padded data
     */
    static double[] pad(double[] data) {
        int length = data.length;
        if (isPowerOf2(length)) {
            return data;
        }
        int nextPowerOf2 = nextPowerOf2(length);
        double[] padded = new double[(int) Math.pow(nextPowerOf2, 2)];
        System.arraycopy(data, 0, padded, 0, length);
        return padded;
    }

    /**
     * Pads 2d data so that its width and height are powers of 2.
     * The original data is centered in the padded version.
     * @param data the data
     * @param width the width of the data
     * @return the padded data
     */
    static double[] pad(double[] data, int width) {
        int length = data.length;
        int height = length / width;
        if (isPowerOf2(width) && isPowerOf2(height)) {
            return data;
        }
        int padWidth = nextPowerOf2(width);
        int padHeight = nextPowerOf2(height);
        int padLength = padWidth * padHeight;
        double[] padded = new double[padLength];
        int xoffset = (padWidth - width) / 2;
        int yoffset = (padHeight - height) / 2;
        for (int y = 0; y < height; y++) {
            System.arraycopy(data, y * width, padded, (y + yoffset) * padWidth + xoffset, width);
        }
        return padded;
    }

    /**
     * Performs the Fourier transform
     */
    void transform();

    /**
     * Performs the inverse Fourier transform
     */
    void inverseTransform();

    /**
     * Returns the real part of the complex numbers.
     * @return the real part of the complex numbers
     */
    double[] real();

    /**
     * Returns the imaginary part of the complex numbers.
     * @return the imaginary part of the complex numbers
     */
    double[] imaginary();
}
