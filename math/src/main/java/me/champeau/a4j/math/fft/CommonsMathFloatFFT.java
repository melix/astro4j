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

class CommonsMathFloatFFT implements FloatFFT {
    private final float[] real;
    private final float[] imaginary;

    CommonsMathFloatFFT(float[] real, float[] imaginary) {
        this.real = real;
        this.imaginary = imaginary;
    }

    @Override
    public void transform() {
        if (!FFTSupport.isPowerOf2(real.length)) {
            throw new IllegalArgumentException("Array length must be a power of 2");
        }

        double[] doubleData = new double[real.length];
        for (int i = 0; i < real.length; i++) {
            doubleData[i] = real[i];
        }

        var transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        var result = transformer.transform(doubleData, TransformType.FORWARD);

        for (int i = 0; i < result.length; i++) {
            real[i] = (float) result[i].getReal();
            imaginary[i] = (float) result[i].getImaginary();
        }
    }

    @Override
    public void inverseTransform() {
        if (!FFTSupport.isPowerOf2(real.length)) {
            throw new IllegalArgumentException("Array length must be a power of 2");
        }

        Complex[] complexData = new Complex[real.length];
        for (int i = 0; i < real.length; i++) {
            complexData[i] = new Complex(real[i], imaginary[i]);
        }

        var transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        var result = transformer.transform(complexData, TransformType.INVERSE);

        for (int i = 0; i < result.length; i++) {
            real[i] = (float) result[i].getReal();
            imaginary[i] = (float) result[i].getImaginary();
        }
    }

    @Override
    public float[] real() {
        return real;
    }

    @Override
    public float[] imaginary() {
        return imaginary;
    }
}
