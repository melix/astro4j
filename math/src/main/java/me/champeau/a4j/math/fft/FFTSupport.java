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
        var x = number;
        x = x - 1;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    public static class FFT2DResult {
        public final double[][] real;
        public final double[][] imaginary;

        public FFT2DResult(double[][] real, double[][] imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }

        public void zeroFrequencies(int rowStart, int rowEnd, int colStart, int colEnd) {
            for (var y = rowStart; y < rowEnd; y++) {
                for (var x = colStart; x < colEnd; x++) {
                    real[y][x] = 0;
                    imaginary[y][x] = 0;
                }
            }
        }
    }

    public static class FloatFFT2DResult {
        public final float[][] real;
        public final float[][] imaginary;

        public FloatFFT2DResult(float[][] real, float[][] imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }

        public void zeroFrequencies(int rowStart, int rowEnd, int colStart, int colEnd) {
            for (var y = rowStart; y < rowEnd; y++) {
                for (var x = colStart; x < colEnd; x++) {
                    real[y][x] = 0;
                    imaginary[y][x] = 0;
                }
            }
        }
    }

    public static FFT2DResult fft2(float[][] data) {
        var rows = data.length;
        var cols = data[0].length;

        var flatReal = new double[rows * cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                flatReal[i * cols + j] = data[i][j];
            }
        }

        var fft = FastFourierTransform.ofArray2D(flatReal, cols, rows);
        fft.transform();

        var realFlat = fft.real();
        var imagFlat = fft.imaginary();

        var real2D = new double[rows][cols];
        var imag2D = new double[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                real2D[i][j] = realFlat[idx];
                imag2D[i][j] = imagFlat[idx];
            }
        }

        return new FFT2DResult(real2D, imag2D);
    }

    public static FFT2DResult fft2(double[][] data) {
        var rows = data.length;
        var cols = data[0].length;

        var flatReal = new double[rows * cols];
        for (var i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flatReal, i * cols, cols);
        }

        var fft = FastFourierTransform.ofArray2D(flatReal, cols, rows);
        fft.transform();

        var realFlat = fft.real();
        var imagFlat = fft.imaginary();

        var real2D = new double[rows][cols];
        var imag2D = new double[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                real2D[i][j] = realFlat[idx];
                imag2D[i][j] = imagFlat[idx];
            }
        }

        return new FFT2DResult(real2D, imag2D);
    }

    public static FFT2DResult ifft2(FFT2DResult frequencyDomain) {
        var rows = frequencyDomain.real.length;
        var cols = frequencyDomain.real[0].length;

        var flatReal = new double[rows * cols];
        var flatImag = new double[rows * cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                flatReal[idx] = frequencyDomain.real[i][j];
                flatImag[idx] = frequencyDomain.imaginary[i][j];
            }
        }

        var fft = new FastFourierTransform2D(flatReal, flatImag, cols, rows);
        fft.inverseTransform();

        var realFlat = fft.real();
        var imagFlat = fft.imaginary();

        var real2D = new double[rows][cols];
        var imag2D = new double[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                real2D[i][j] = realFlat[idx];
                imag2D[i][j] = imagFlat[idx];
            }
        }

        return new FFT2DResult(real2D, imag2D);
    }

    public static FFT2DResult crossCorrelation(FFT2DResult fftRef, FFT2DResult fftDef) {
        var rows = fftRef.real.length;
        var cols = fftRef.real[0].length;

        var flatReal = new double[rows * cols];
        var flatImag = new double[rows * cols];

        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                var refR = fftRef.real[i][j];
                var refI = fftRef.imaginary[i][j];
                var defR = fftDef.real[i][j];
                var defI = fftDef.imaginary[i][j];

                // Complex multiply: ref * conj(def) = (refR + i*refI) * (defR - i*defI)
                flatReal[idx] = refR * defR + refI * defI;
                flatImag[idx] = refI * defR - refR * defI;
            }
        }

        var fft = new FastFourierTransform2D(flatReal, flatImag, cols, rows);
        fft.inverseTransform();

        var realFlat = fft.real();
        var imagFlat = fft.imaginary();

        var real2D = new double[rows][cols];
        var imag2D = new double[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var idx = i * cols + j;
                real2D[i][j] = realFlat[idx];
                imag2D[i][j] = imagFlat[idx];
            }
        }

        return new FFT2DResult(real2D, imag2D);
    }

    public static double[][] pad(double[][] data, int width, int height) {
        var paddedWidth = nextPowerOf2(width);
        var paddedHeight = nextPowerOf2(height);

        var paddedData = new double[paddedHeight][paddedWidth];
        for (var y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, paddedData[y], 0, width);
        }
        return paddedData;
    }

    public static double[][] padFromFloatArray(float[][] data, int width, int height) {
        var paddedWidth = nextPowerOf2(width);
        var paddedHeight = nextPowerOf2(height);

        var paddedData = new double[paddedHeight][paddedWidth];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                paddedData[y][x] = data[y][x];
            }
        }
        return paddedData;
    }

    public static float[][] padFloatArray(float[][] data, int width, int height) {
        var paddedWidth = nextPowerOf2(width);
        var paddedHeight = nextPowerOf2(height);

        var paddedData = new float[paddedHeight][paddedWidth];
        for (var y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, paddedData[y], 0, width);
        }
        return paddedData;
    }

    public static FloatFFT2DResult fft2Float(float[][] data) {
        var rows = data.length;
        var cols = data[0].length;

        var realRows = new float[rows][cols];
        var imagRows = new float[rows][cols];
        for (var i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, realRows[i], 0, cols);
        }

        for (var i = 0; i < rows; i++) {
            var fft = FastFourierTransform.ofComplex(realRows[i], imagRows[i]);
            fft.transform();
        }

        var realCols = new float[cols][rows];
        var imagCols = new float[cols][rows];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                realCols[j][i] = realRows[i][j];
                imagCols[j][i] = imagRows[i][j];
            }
        }

        for (var j = 0; j < cols; j++) {
            var fft = FastFourierTransform.ofComplex(realCols[j], imagCols[j]);
            fft.transform();
        }

        var real2D = new float[rows][cols];
        var imag2D = new float[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                real2D[i][j] = realCols[j][i];
                imag2D[i][j] = imagCols[j][i];
            }
        }

        return new FloatFFT2DResult(real2D, imag2D);
    }

    public static FloatFFT2DResult ifft2Float(FloatFFT2DResult frequencyDomain) {
        var rows = frequencyDomain.real.length;
        var cols = frequencyDomain.real[0].length;

        var realCols = new float[cols][rows];
        var imagCols = new float[cols][rows];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                realCols[j][i] = frequencyDomain.real[i][j];
                imagCols[j][i] = frequencyDomain.imaginary[i][j];
            }
        }

        for (var j = 0; j < cols; j++) {
            var fft = FastFourierTransform.ofComplex(realCols[j], imagCols[j]);
            fft.inverseTransform();
        }

        var realRows = new float[rows][cols];
        var imagRows = new float[rows][cols];
        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                realRows[i][j] = realCols[j][i];
                imagRows[i][j] = imagCols[j][i];
            }
        }

        for (var i = 0; i < rows; i++) {
            var fft = FastFourierTransform.ofComplex(realRows[i], imagRows[i]);
            fft.inverseTransform();
        }

        return new FloatFFT2DResult(realRows, imagRows);
    }

    public static FloatFFT2DResult crossCorrelationFloat(FloatFFT2DResult fftRef, FloatFFT2DResult fftDef) {
        var rows = fftRef.real.length;
        var cols = fftRef.real[0].length;

        var realResult = new float[rows][cols];
        var imagResult = new float[rows][cols];

        for (var i = 0; i < rows; i++) {
            for (var j = 0; j < cols; j++) {
                var refR = fftRef.real[i][j];
                var refI = fftRef.imaginary[i][j];
                var defR = fftDef.real[i][j];
                var defI = fftDef.imaginary[i][j];

                realResult[i][j] = refR * defR + refI * defI;
                imagResult[i][j] = refI * defR - refR * defI;
            }
        }

        return ifft2Float(new FloatFFT2DResult(realResult, imagResult));
    }


}
