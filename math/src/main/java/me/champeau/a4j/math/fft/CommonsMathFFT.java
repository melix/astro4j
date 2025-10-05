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

final class CommonsMathFFT implements FastFourierTransform {
    private final double[] dataR;
    private final double[] dataI;

    CommonsMathFFT(double[] real, double[] imaginary) {
        this.dataR = real;
        this.dataI = imaginary;
    }

    @Override
    public double[] real() {
        return dataR;
    }

    @Override
    public double[] imaginary() {
        return dataI;
    }

    @Override
    public void transform() {
        var transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        var complex = new Complex[dataR.length];
        for (int i = 0; i < dataR.length; i++) {
            complex[i] = new Complex(dataR[i], dataI[i]);
        }
        complex = transformer.transform(complex, TransformType.FORWARD);
        for (int i = 0; i < complex.length; i++) {
            dataR[i] = complex[i].getReal();
            dataI[i] = complex[i].getImaginary();
        }
    }

    @Override
    public void inverseTransform() {
        var transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        var complex = new Complex[dataR.length];
        for (int i = 0; i < dataR.length; i++) {
            complex[i] = new Complex(dataR[i], dataI[i]);
        }
        complex = transformer.transform(complex, TransformType.INVERSE);
        for (int i = 0; i < complex.length; i++) {
            dataR[i] = complex[i].getReal();
            dataI[i] = complex[i].getImaginary();
        }
    }
}
