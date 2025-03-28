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

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class FFTSupport {
    private FFTSupport() {

    }

    public static boolean isPowerOf2(int number) {
        return (number & -number) == number;
    }

    public static int nextPowerOf2(int number) {
        if (isPowerOf2(number)) {
            return number;
        }
        int x = number;
        x = x - 1;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    public static Complex[][] fft2(float[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        var result = new Complex[rows][cols];
        var fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform 1D FFT on rows
        for (int i = 0; i < rows; i++) {
            var row = new Complex[cols];
            for (int j = 0; j < cols; j++) {
                row[j] = new Complex(data[i][j], 0);
            }
            row = fft.transform(row, TransformType.FORWARD);
            result[i] = row;
        }

        // Perform 1D FFT on columns
        for (int j = 0; j < cols; j++) {
            var column = new Complex[rows];
            for (int i = 0; i < rows; i++) {
                column[i] = result[i][j];
            }
            column = fft.transform(column, TransformType.FORWARD);
            for (int i = 0; i < rows; i++) {
                result[i][j] = column[i];
            }
        }

        return result;
    }

    public static Complex[][] fft2(double[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        var result = new Complex[rows][cols];
        var fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform 1D FFT on rows
        for (int i = 0; i < rows; i++) {
            var row = new Complex[cols];
            for (int j = 0; j < cols; j++) {
                row[j] = new Complex(data[i][j], 0);
            }
            row = fft.transform(row, TransformType.FORWARD);
            result[i] = row;
        }

        // Perform 1D FFT on columns
        for (int j = 0; j < cols; j++) {
            var column = new Complex[rows];
            for (int i = 0; i < rows; i++) {
                column[i] = result[i][j];
            }
            column = fft.transform(column, TransformType.FORWARD);
            for (int i = 0; i < rows; i++) {
                result[i][j] = column[i];
            }
        }

        return result;
    }

    public static Complex[][] ifft2(Complex[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        var result = new Complex[rows][cols];
        var fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform 1D IFFT on rows
        for (int i = 0; i < rows; i++) {
            var row = data[i];
            row = fft.transform(row, TransformType.INVERSE);
            result[i] = row;
        }

        // Perform 1D IFFT on columns
        for (int j = 0; j < cols; j++) {
            var column = new Complex[rows];
            for (int i = 0; i < rows; i++) {
                column[i] = result[i][j];
            }
            column = fft.transform(column, TransformType.INVERSE);
            for (int i = 0; i < rows; i++) {
                result[i][j] = column[i];
            }
        }

        return result;
    }

    /*
     * Compute the cross-correlation (inverse FFT of the product of the reference and defined FFT)
     */
    public static Complex[][] crossCorrelation(Complex[][] fftRef, Complex[][] fftDef) {
        int rows = fftRef.length;
        int cols = fftRef[0].length;
        var result = new Complex[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = fftRef[i][j].multiply(fftDef[i][j].conjugate());
            }
        }

        return ifft2(result);
    }

    public static double[][] pad(double[][] data, int width, int height) {
        int paddedWidth = nextPowerOf2(width);
        int paddedHeight = nextPowerOf2(height);

        var paddedData = new double[paddedHeight][paddedWidth];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, paddedData[y], 0, width);
        }
        return paddedData;
    }

    public static double[][] padFromFloatArray(float[][] data, int width, int height) {
        int paddedWidth = nextPowerOf2(width);
        int paddedHeight = nextPowerOf2(height);

        var paddedData = new double[paddedHeight][paddedWidth];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                paddedData[y][x] = data[y][x];
            }
        }
        return paddedData;
    }


}
