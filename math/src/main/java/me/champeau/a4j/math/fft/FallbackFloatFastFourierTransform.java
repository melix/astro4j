/*
 * Copyright 2003-2021 the original author or authors.
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

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Implementation of the Fast Fourier transform which implements the
 * Cooley-Tukey algorith, which assumes that data length is a power of
 * 2.
 */
public class FallbackFloatFastFourierTransform implements FastFourierTransform {
    private final float[] real;
    private final float[] imaginary;
    private final int n;

    FallbackFloatFastFourierTransform(float[] real, float[] imaginary) {
        this.real = real;
        this.imaginary = imaginary;
        this.n = real.length;
    }

    @Override
    public float[] real() {
        return real;
    }

    @Override
    public float[] imaginary() {
        return imaginary;
    }

    @Override
    public void transform() {
        if (n <= 1) {
            return;
        }

        float[] evenReal = new float[n / 2];
        float[] evenImaginary = new float[n / 2];
        float[] oddReal = new float[n / 2];
        float[] oddImaginary = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            evenReal[i] = real[2 * i];
            evenImaginary[i] = imaginary[2 * i];
            oddReal[i] = real[2 * i + 1];
            oddImaginary[i] = imaginary[2 * i + 1];
        }

        var evenTransform = new FallbackFloatFastFourierTransform(evenReal, evenImaginary);
        var oddTransform = new FallbackFloatFastFourierTransform(oddReal, oddImaginary);
        evenTransform.transform();
        oddTransform.transform();

        float ro = (float) (-2 * PI / n);
        for (int k = 0; k < n / 2; k++) {
            double fcos = cos(ro * k);
            double fsin = sin(ro * k);
            double kthReal = evenReal[k] + fcos * oddReal[k] - fsin * oddImaginary[k];
            double kthImaginary = evenImaginary[k] + fcos * oddImaginary[k] + fsin * oddReal[k];
            double kthPlusNOver2Real = evenReal[k] - fcos * oddReal[k] + fsin * oddImaginary[k];
            double kthPlusNOver2Imaginary = evenImaginary[k] - fcos * oddImaginary[k] - fsin * oddReal[k];
            real[k] = (float) kthReal;
            imaginary[k] = (float) kthImaginary;
            real[k + n / 2] = (float) kthPlusNOver2Real;
            imaginary[k + n / 2] = (float) kthPlusNOver2Imaginary;
        }
    }

    @Override
    public void inverseTransform() {
        for (int i = 0; i < imaginary.length; i++) {
            imaginary[i] = -imaginary[i];
        }
        transform();
        for (int i = 0; i < n; i++) {
            real[i] /= n;
            imaginary[i] = -(imaginary[i] / n);
        }
    }

}
