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

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

class VectorizedFloatFFT implements FloatFFT {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private static final float[] W_SUB_N_R = {
        (float) 0x1.0p0, (float) -0x1.0p0, (float) 0x1.1a62633145c07p-54, (float) 0x1.6a09e667f3bcdp-1,
        (float) 0x1.d906bcf328d46p-1, (float) 0x1.f6297cff75cbp-1, (float) 0x1.fd88da3d12526p-1, (float) 0x1.ff621e3796d7ep-1,
        (float) 0x1.ffd886084cd0dp-1, (float) 0x1.fff62169b92dbp-1, (float) 0x1.fffd8858e8a92p-1, (float) 0x1.ffff621621d02p-1,
        (float) 0x1.ffffd88586ee6p-1, (float) 0x1.fffff62161a34p-1, (float) 0x1.fffffd8858675p-1, (float) 0x1.ffffff621619cp-1,
        (float) 0x1.ffffffd885867p-1, (float) 0x1.fffffff62161ap-1, (float) 0x1.fffffffd88586p-1, (float) 0x1.ffffffff62162p-1,
        (float) 0x1.ffffffffd8858p-1, (float) 0x1.fffffffff6216p-1, (float) 0x1.fffffffffd886p-1, (float) 0x1.ffffffffff621p-1,
        (float) 0x1.ffffffffffd88p-1, (float) 0x1.fffffffffff62p-1, (float) 0x1.fffffffffffd9p-1, (float) 0x1.ffffffffffff6p-1,
        (float) 0x1.ffffffffffffep-1, (float) 0x1.fffffffffffffp-1, (float) 0x1.0p0, (float) 0x1.0p0
    };

    private static final float[] W_SUB_N_I = {
        (float) 0x1.1a62633145c07p-52, (float) -0x1.1a62633145c07p-53, (float) -0x1.0p0, (float) -0x1.6a09e667f3bccp-1,
        (float) -0x1.87de2a6aea963p-2, (float) -0x1.8f8b83c69a60ap-3, (float) -0x1.917a6bc29b42cp-4, (float) -0x1.91f65f10dd814p-5,
        (float) -0x1.92155f7a3667ep-6, (float) -0x1.921d1fcdec784p-7, (float) -0x1.921f0fe670071p-8, (float) -0x1.921f8becca4bap-9,
        (float) -0x1.921faaee6472dp-10, (float) -0x1.921fb2aecb36p-11, (float) -0x1.921fb49ee4ea6p-12, (float) -0x1.921fb51aeb57bp-13,
        (float) -0x1.921fb539ecf31p-14, (float) -0x1.921fb541ad59ep-15, (float) -0x1.921fb5439d73ap-16, (float) -0x1.921fb544197ap-17,
        (float) -0x1.921fb544387bap-18, (float) -0x1.921fb544403c1p-19, (float) -0x1.921fb544422c2p-20, (float) -0x1.921fb54442a83p-21,
        (float) -0x1.921fb54442c73p-22, (float) -0x1.921fb54442cefp-23, (float) -0x1.921fb54442d0ep-24, (float) -0x1.921fb54442d15p-25,
        (float) -0x1.921fb54442d17p-26, (float) -0x1.921fb54442d18p-27, (float) -0x1.921fb54442d18p-28, (float) -0x1.921fb54442d18p-29
    };

    private final float[] dataR;
    private final float[] dataI;

    VectorizedFloatFFT(float[] real, float[] imaginary) {
        this.dataR = real;
        this.dataI = imaginary;
    }

    @Override
    public float[] real() {
        return dataR;
    }

    @Override
    public float[] imaginary() {
        return dataI;
    }

    @Override
    public void transform() {
        transformInPlace(dataR, dataI, false);
    }

    @Override
    public void inverseTransform() {
        transformInPlace(dataR, dataI, true);
        int n = dataR.length;
        float scale = 1.0f / n;
        int i = 0;
        int upperBound = SPECIES.loopBound(n);
        for (; i < upperBound; i += SPECIES.length()) {
            var vr = FloatVector.fromArray(SPECIES, dataR, i);
            var vi = FloatVector.fromArray(SPECIES, dataI, i);
            vr.mul(scale).intoArray(dataR, i);
            vi.mul(scale).intoArray(dataI, i);
        }
        for (; i < n; i++) {
            dataR[i] *= scale;
            dataI[i] *= scale;
        }
    }

    private static void transformInPlace(float[] dataR, float[] dataI, boolean inverse) {
        final int n = dataR.length;

        if (n == 1) {
            return;
        }

        if (n == 2) {
            radix2Butterfly(dataR, dataI);
            return;
        }

        bitReversalShuffle2(dataR, dataI);
        radix4Stage(dataR, dataI, n, inverse);
        radix2Stages(dataR, dataI, n, inverse);
    }

    private static void radix2Butterfly(float[] dataR, float[] dataI) {
        float srcR0 = dataR[0];
        float srcI0 = dataI[0];
        float srcR1 = dataR[1];
        float srcI1 = dataI[1];

        dataR[0] = srcR0 + srcR1;
        dataI[0] = srcI0 + srcI1;
        dataR[1] = srcR0 - srcR1;
        dataI[1] = srcI0 - srcI1;
    }

    private static void radix2Stages(float[] dataR, float[] dataI, int n, boolean inverse) {
        if (inverse) {
            radix2StagesInverse(dataR, dataI, n);
        } else {
            radix2StagesForward(dataR, dataI, n);
        }
    }

    private static void radix2StagesForward(float[] dataR, float[] dataI, int n) {
        int lastN0 = 4;
        int lastLogN0 = 2;

        while (lastN0 < n) {
            int n0 = lastN0 << 1;
            int logN0 = lastLogN0 + 1;
            float wSubN0R = W_SUB_N_R[logN0];
            float wSubN0I = W_SUB_N_I[logN0];

            radix2Stage(dataR, dataI, n, n0, lastN0, wSubN0R, wSubN0I);

            lastN0 = n0;
            lastLogN0 = logN0;
        }
    }

    private static void radix2StagesInverse(float[] dataR, float[] dataI, int n) {
        int lastN0 = 4;
        int lastLogN0 = 2;

        while (lastN0 < n) {
            int n0 = lastN0 << 1;
            int logN0 = lastLogN0 + 1;
            float wSubN0R = W_SUB_N_R[logN0];
            float wSubN0I = -W_SUB_N_I[logN0];

            radix2Stage(dataR, dataI, n, n0, lastN0, wSubN0R, wSubN0I);

            lastN0 = n0;
            lastLogN0 = logN0;
        }
    }

    private static void radix2Stage(float[] dataR,
                                    float[] dataI,
                                    int n,
                                    int n0,
                                    int lastN0,
                                    float wSubN0R,
                                    float wSubN0I) {
        float[] twR = new float[lastN0];
        float[] twI = new float[lastN0];
        float wrr = 1.0f;
        float wri = 0.0f;
        for (int k = 0; k < lastN0; k++) {
            twR[k] = wrr;
            twI[k] = wri;
            float nr = wrr * wSubN0R - wri * wSubN0I;
            float ni = wrr * wSubN0I + wri * wSubN0R;
            wrr = nr;
            wri = ni;
        }

        final int laneCount = SPECIES.length();
        final int loopBound = SPECIES.loopBound(lastN0);

        for (int destEvenStartIndex = 0; destEvenStartIndex < n; destEvenStartIndex += n0) {
            int destOddStartIndex = destEvenStartIndex + lastN0;

            int r = 0;
            for (; r < loopBound; r += laneCount) {
                var grR = FloatVector.fromArray(SPECIES, dataR, destEvenStartIndex + r);
                var grI = FloatVector.fromArray(SPECIES, dataI, destEvenStartIndex + r);
                var hrR = FloatVector.fromArray(SPECIES, dataR, destOddStartIndex + r);
                var hrI = FloatVector.fromArray(SPECIES, dataI, destOddStartIndex + r);
                var twvr = FloatVector.fromArray(SPECIES, twR, r);
                var twvi = FloatVector.fromArray(SPECIES, twI, r);

                var tempR = twvr.mul(hrR).sub(twvi.mul(hrI));
                var tempI = twvr.mul(hrI).add(twvi.mul(hrR));

                grR.add(tempR).intoArray(dataR, destEvenStartIndex + r);
                grI.add(tempI).intoArray(dataI, destEvenStartIndex + r);

                grR.sub(tempR).intoArray(dataR, destOddStartIndex + r);
                grI.sub(tempI).intoArray(dataI, destOddStartIndex + r);
            }

            for (; r < lastN0; r++) {
                float grR = dataR[destEvenStartIndex + r];
                float grI = dataI[destEvenStartIndex + r];
                float hrR = dataR[destOddStartIndex + r];
                float hrI = dataI[destOddStartIndex + r];
                float twr = twR[r];
                float twi = twI[r];

                float tempR = twr * hrR - twi * hrI;
                float tempI = twr * hrI + twi * hrR;

                dataR[destEvenStartIndex + r] = grR + tempR;
                dataI[destEvenStartIndex + r] = grI + tempI;
                dataR[destOddStartIndex + r] = grR - tempR;
                dataI[destOddStartIndex + r] = grI - tempI;
            }
        }
    }

    private static void radix4Stage(float[] dataR, float[] dataI, int n, boolean inverse) {
        if (inverse) {
            for (int i0 = 0; i0 < n; i0 += 4) {
                radix4ButterflyInverse(dataR, dataI, i0);
            }
        } else {
            for (int i0 = 0; i0 < n; i0 += 4) {
                radix4ButterflyForward(dataR, dataI, i0);
            }
        }
    }

    private static void radix4ButterflyForward(float[] dataR, float[] dataI, int i0) {
        final int i1 = i0 + 1;
        final int i2 = i0 + 2;
        final int i3 = i0 + 3;

        final float srcR0 = dataR[i0];
        final float srcI0 = dataI[i0];
        final float srcR1 = dataR[i2];
        final float srcI1 = dataI[i2];
        final float srcR2 = dataR[i1];
        final float srcI2 = dataI[i1];
        final float srcR3 = dataR[i3];
        final float srcI3 = dataI[i3];

        dataR[i0] = srcR0 + srcR1 + srcR2 + srcR3;
        dataI[i0] = srcI0 + srcI1 + srcI2 + srcI3;
        dataR[i2] = srcR0 - srcR1 + srcR2 - srcR3;
        dataI[i2] = srcI0 - srcI1 + srcI2 - srcI3;
        dataR[i1] = srcR0 - srcR2 + (srcI1 - srcI3);
        dataI[i1] = srcI0 - srcI2 + (srcR3 - srcR1);
        dataR[i3] = srcR0 - srcR2 + (srcI3 - srcI1);
        dataI[i3] = srcI0 - srcI2 + (srcR1 - srcR3);
    }

    private static void radix4ButterflyInverse(float[] dataR, float[] dataI, int i0) {
        final int i1 = i0 + 1;
        final int i2 = i0 + 2;
        final int i3 = i0 + 3;

        final float srcR0 = dataR[i0];
        final float srcI0 = dataI[i0];
        final float srcR1 = dataR[i2];
        final float srcI1 = dataI[i2];
        final float srcR2 = dataR[i1];
        final float srcI2 = dataI[i1];
        final float srcR3 = dataR[i3];
        final float srcI3 = dataI[i3];

        dataR[i0] = srcR0 + srcR1 + srcR2 + srcR3;
        dataI[i0] = srcI0 + srcI1 + srcI2 + srcI3;
        dataR[i2] = srcR0 - srcR1 + srcR2 - srcR3;
        dataI[i2] = srcI0 - srcI1 + srcI2 - srcI3;
        dataR[i1] = srcR0 - srcR2 + (srcI3 - srcI1);
        dataI[i1] = srcI0 - srcI2 + (srcR1 - srcR3);
        dataR[i3] = srcR0 - srcR2 + (srcI1 - srcI3);
        dataI[i3] = srcI0 - srcI2 + (srcR3 - srcR1);
    }

    private static void bitReversalShuffle2(float[] a, float[] b) {
        final int n = a.length;
        final int halfOfN = n >> 1;

        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                float temp = a[i];
                a[i] = a[j];
                a[j] = temp;

                temp = b[i];
                b[i] = b[j];
                b[j] = temp;
            }

            int k = halfOfN;
            while (k <= j && k > 0) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }
    }
}
