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

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Implementation of the Fast Fourier transform which implements the
 * Cooley-Tukey algorith, which assumes that data length is a power of
 * 2. This implementation makes use of the Vector API.
 */
public class VectorizedFloatFastFourierTransform implements FastFourierTransform {
    private final VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

    private final float[] real;
    private final float[] imaginary;
    private final int n;

    VectorizedFloatFastFourierTransform(float[] real, float[] imaginary) {
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
        // If n < vector size, then nothing will be done using the vector API
        // so we have a shortcut to use the traditional path
        if (n < species.length()) {
            new FallbackFloatFastFourierTransform(real, imaginary).transform();
            return;
        }

        int half = n / 2;
        float[] evenReal = new float[half];
        float[] evenImaginary = new float[half];
        float[] oddReal = new float[half];
        float[] oddImaginary = new float[half];
        for (int i = 0; i < half; i++) {
            evenReal[i] = real[2 * i];
            evenImaginary[i] = imaginary[2 * i];
            oddReal[i] = real[2 * i + 1];
            oddImaginary[i] = imaginary[2 * i + 1];
        }

        var evenTransform = new VectorizedFloatFastFourierTransform(evenReal, evenImaginary);
        var oddTransform = new VectorizedFloatFastFourierTransform(oddReal, oddImaginary);
        evenTransform.transform();
        oddTransform.transform();
        var indices = new float[half];
        for (int i = 0; i < half; i++) {
            indices[i] = i;
        }
        int k = 0;
        float ro = (float) (-2 * PI / n);
        for (; k < species.loopBound(half); k += species.length()) {
            var mask = species.indexInRange(k, half);
            var kv = FloatVector.fromArray(species, indices, k, mask);
            var mul = kv.mul(ro);
            var cos = mul.lanewise(VectorOperators.COS, mask);
            var sin = mul.lanewise(VectorOperators.SIN, mask);
            var evenRealV = FloatVector.fromArray(species, evenReal, k, mask);
            var oddRealV = FloatVector.fromArray(species, oddReal, k, mask);
            var evenImaginaryV = FloatVector.fromArray(species, evenImaginary, k, mask);
            var oddImaginaryV = FloatVector.fromArray(species, oddImaginary, k, mask);
            // kthReal = evenReal[k] + fcos * oddReal[k] - fsin * oddImaginary[k]
            var kthReal = evenRealV.add(cos.mul(oddRealV)).sub(sin.mul(oddImaginaryV));
            // kthImaginary = evenImaginary[k] + fcos * oddImaginary[k] + fsin * oddReal[k]
            var kthImaginary = evenImaginaryV.add(cos.mul(oddImaginaryV)).add(sin.mul(oddRealV));
            // evenReal[k] - fcos * oddReal[k] + fsin * oddImaginary[k]
            var kthPlusNOver2Real = evenRealV.sub(cos.mul(oddRealV)).add(sin.mul(oddImaginaryV));
            // evenImaginary[k] - fcos * oddImaginary[k] - fsin * oddReal[k]
            var kthPlusNOver2Imaginary = evenImaginaryV.sub(cos.mul(oddImaginaryV)).sub(sin.mul(oddRealV));
            kthReal.intoArray(real, k, mask);
            kthImaginary.intoArray(imaginary, k, mask);
            kthPlusNOver2Real.intoArray(real, k + half, mask);
            kthPlusNOver2Imaginary.intoArray(imaginary, k + half, mask);
        }
        for (; k < half; k++) {
            double fcos = cos(ro * k);
            double fsin = sin(ro * k);
            double kthReal = evenReal[k] + fcos * oddReal[k] - fsin * oddImaginary[k];
            double kthImaginary = evenImaginary[k] + fcos * oddImaginary[k] + fsin * oddReal[k];
            double kthPlusNOver2Real = evenReal[k] - fcos * oddReal[k] + fsin * oddImaginary[k];
            double kthPlusNOver2Imaginary = evenImaginary[k] - fcos * oddImaginary[k] - fsin * oddReal[k];
            real[k] = (float) kthReal;
            imaginary[k] = (float) kthImaginary;
            real[k + half] = (float) kthPlusNOver2Real;
            imaginary[k + half] = (float) kthPlusNOver2Imaginary;
        }
    }

    @Override
    public void inverseTransform() {
        var len = imaginary.length;
        int i = 0;
        for (; i < species.loopBound(len); i+=species.length()) {
            var mask = species.indexInRange(i, len);
            var im = FloatVector.fromArray(species, imaginary, i, mask).neg();
            im.intoArray(imaginary, i, mask);
        }
        for (; i < len; i++) {
            imaginary[i] = -imaginary[i];
        }
        transform();
        i = 0;
        for (; i < species.loopBound(len); i += species.length()) {
            var mask = species.indexInRange(i, len);
            var r = FloatVector.fromArray(species, real, i, mask).div(n);
            r.intoArray(real, i, mask);
            var im = FloatVector.fromArray(species, imaginary, i, mask).div(n).neg();
            im.intoArray(imaginary, i, mask);
        }
        for (; i < n; i++) {
            real[i] /= n;
            imaginary[i] = -(imaginary[i] / n);
        }
    }

}
