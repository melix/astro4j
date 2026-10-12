/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.spectrum.cloud;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * The wavelength sampling used by the cloud model. The model is evaluated on a fine,
 * regular grid, multiplied by the transmission of the atmosphere, then convolved by the
 * instrumental profile and sampled at the observed wavelengths. The transmission is folded
 * into the kernels, so that the telluric lines stay at the same wavelength whatever the
 * shift of the solar profile.
 *
 * @param sampleOffsets the observed wavelengths, as offsets in angstroms from the line center
 * @param fineOffsets the fine grid, as offsets in angstroms from the line center
 * @param kernelStart for each observed sample, the index of the first fine point of its kernel
 * @param kernelWeights for each observed sample, the weights of its kernel, which include the transmission of the atmosphere
 */
public record SpectralGrid(
        double[] sampleOffsets,
        double[] fineOffsets,
        int[] kernelStart,
        double[][] kernelWeights
) {
    private static final double KERNEL_EXTENT = 4;
    private static final double MAX_FINE_STEP = 0.05;
    private static final double MIN_FINE_STEP = 0.01;
    private static final int TEMPLATE_ITERATIONS = 30;

    public int sampleCount() {
        return sampleOffsets.length;
    }

    public int fineCount() {
        return fineOffsets.length;
    }

    /**
     * Creates a grid for the given observed wavelengths.
     *
     * @param sampleOffsets the observed wavelengths, as offsets in angstroms from the line center, in increasing order
     * @param instrumentSigma the standard deviation of the instrumental profile, in angstroms
     * @return the grid
     */
    public static SpectralGrid of(double[] sampleOffsets, double instrumentSigma) {
        return of(sampleOffsets, instrumentSigma, offset -> 1);
    }

    /**
     * Creates a grid for the given observed wavelengths, observed through the atmosphere.
     *
     * @param sampleOffsets the observed wavelengths, as offsets in angstroms from the line center, in increasing order
     * @param instrumentSigma the standard deviation of the instrumental profile, in angstroms
     * @param transmission the transmission of the atmosphere, as a function of the offset from the line center
     * @return the grid
     */
    public static SpectralGrid of(double[] sampleOffsets, double instrumentSigma, DoubleUnaryOperator transmission) {
        var step = Math.clamp(instrumentSigma / 2, MIN_FINE_STEP, MAX_FINE_STEP);
        var extent = KERNEL_EXTENT * Math.max(instrumentSigma, step);
        var start = sampleOffsets[0] - extent;
        var end = sampleOffsets[sampleOffsets.length - 1] + extent;
        var count = (int) Math.ceil((end - start) / step) + 1;
        var fine = new double[count];
        for (var i = 0; i < count; i++) {
            fine[i] = start + i * step;
        }
        var kernelStart = new int[sampleOffsets.length];
        var kernelWeights = new double[sampleOffsets.length][];
        var sigma = Math.max(instrumentSigma, step / 2);
        for (var i = 0; i < sampleOffsets.length; i++) {
            var x = sampleOffsets[i];
            var first = Math.max(0, (int) Math.floor((x - extent - start) / step));
            var last = Math.min(count - 1, (int) Math.ceil((x + extent - start) / step));
            var weights = new double[last - first + 1];
            var sum = 0d;
            for (var j = first; j <= last; j++) {
                var u = (fine[j] - x) / sigma;
                var weight = Math.exp(-0.5 * u * u);
                weights[j - first] = weight;
                sum += weight;
            }
            for (var j = 0; j < weights.length; j++) {
                weights[j] *= transmission.applyAsDouble(fine[first + j]) / sum;
            }
            kernelStart[i] = first;
            kernelWeights[i] = weights;
        }
        return new SpectralGrid(sampleOffsets, fine, kernelStart, kernelWeights);
    }

    /**
     * Multiplies a profile defined on the fine grid by the transmission of the atmosphere,
     * convolves it and samples it at the observed wavelengths.
     *
     * @param fine the profile on the fine grid
     * @param samples the output, one value per observed sample
     */
    public void sample(double[] fine, double[] samples) {
        sample(fine, 0, fine.length - 1, samples);
    }

    /**
     * Same as {@link #sample(double[], double[])}, for a profile which is zero outside of a
     * range of the fine grid.
     *
     * @param fine the profile on the fine grid
     * @param from the index of the first fine point which may be different from zero
     * @param to the index of the last fine point which may be different from zero
     * @param samples the output, one value per observed sample
     */
    public void sample(double[] fine, int from, int to, double[] samples) {
        for (var i = 0; i < sampleOffsets.length; i++) {
            var weights = kernelWeights[i];
            var first = kernelStart[i];
            var start = Math.max(first, from);
            var end = Math.min(first + weights.length - 1, to);
            var sum = 0d;
            for (var j = start; j <= end; j++) {
                sum += weights[j - first] * fine[j];
            }
            samples[i] = sum;
        }
    }

    /**
     * Returns the index of the fine point nearest to the given offset, clamped to the grid.
     */
    public int fineIndexOf(double offset) {
        return Math.clamp(Math.round((offset - fineOffsets[0]) / fineStep()), 0, fineOffsets.length - 1);
    }

    /**
     * Returns the spacing of the fine grid, in angstroms.
     */
    public double fineStep() {
        return fineOffsets[1] - fineOffsets[0];
    }

    /**
     * Returns the index of the first observed sample whose kernel reaches the given fine point or a later one.
     */
    public int firstSampleReaching(int fineIndex) {
        for (var i = 0; i < kernelStart.length; i++) {
            if (kernelStart[i] + kernelWeights[i].length > fineIndex) {
                return i;
            }
        }
        return kernelStart.length;
    }

    /**
     * Returns the index of the last observed sample whose kernel reaches the given fine point or an earlier one.
     */
    public int lastSampleReaching(int fineIndex) {
        for (var i = kernelStart.length - 1; i >= 0; i--) {
            if (kernelStart[i] <= fineIndex) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Linearly interpolates a profile known at the observed wavelengths onto the fine grid.
     * Values outside the observed range are clamped to the nearest observed value.
     *
     * @param samples the profile at the observed wavelengths
     * @return the profile on the fine grid
     */
    private double[] interpolateToFine(double[] samples) {
        var fine = new double[fineOffsets.length];
        var last = sampleOffsets.length - 1;
        var k = 0;
        for (var j = 0; j < fineOffsets.length; j++) {
            var x = fineOffsets[j];
            if (x <= sampleOffsets[0]) {
                fine[j] = samples[0];
            } else if (x >= sampleOffsets[last]) {
                fine[j] = samples[last];
            } else {
                while (sampleOffsets[k + 1] < x) {
                    k++;
                }
                var t = (x - sampleOffsets[k]) / (sampleOffsets[k + 1] - sampleOffsets[k]);
                fine[j] = samples[k] + t * (samples[k + 1] - samples[k]);
            }
        }
        return fine;
    }

    /**
     * Estimates the solar profile, on the fine grid, which gives the observed profile once
     * seen through the atmosphere and convolved by the instrumental profile, using a high
     * resolution template. The template keeps the
     * narrow features which the observation cannot resolve. It is multiplied by a smooth
     * correction, refined iteratively until the convolved result matches the observation
     * at the observed wavelengths.
     *
     * @param observed the observed profile, at the observed wavelengths
     * @param template the high resolution template on the fine grid, aligned with the observation
     * @return the estimated profile on the fine grid
     */
    public double[] correctTemplate(double[] observed, double[] template) {
        var ratio = new double[sampleOffsets.length];
        Arrays.fill(ratio, 1);
        var convolved = new double[sampleOffsets.length];
        var result = new double[fineOffsets.length];
        for (var iteration = 0; iteration < TEMPLATE_ITERATIONS; iteration++) {
            applyRatio(template, ratio, result);
            sample(result, convolved);
            for (var i = 0; i < ratio.length; i++) {
                if (convolved[i] > 0) {
                    ratio[i] *= observed[i] / convolved[i];
                }
            }
        }
        applyRatio(template, ratio, result);
        return result;
    }

    private void applyRatio(double[] template, double[] ratio, double[] result) {
        var fineRatio = interpolateToFine(ratio);
        for (var j = 0; j < result.length; j++) {
            result[j] = template[j] * fineRatio[j];
        }
    }
}
