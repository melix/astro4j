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

import me.champeau.a4j.math.opencl.GpuOp;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import java.util.Optional;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY;

/**
 * Fits the cloud model on the GPU, one profile per work-group, in single precision.
 * The kernel mirrors {@link CloudModelFitter#fitBest}.
 */
final class GpuCloudFitter {
    private static final String PROGRAM = "cloud_model";
    private static final String KERNEL = "cloud_fit_best";
    private static final int MAX_SAMPLES = 192;
    private static final int MAX_PARAMETERS = CloudModel.parameterCount(3);
    private static final int RESULT_STRIDE = 2 + MAX_PARAMETERS + 1;
    private static final int LANES = 32;

    private final OpenCLContext context;
    private final int samples;
    private final int fineCount;
    private final int maxKernelLength;
    private final float fineStart;
    private final float fineStep;
    private final int[] kernelStart;
    private final int[] kernelLength;
    private final float[] kernelWeights;
    private final float[] constants;

    private GpuCloudFitter(OpenCLContext context, SpectralGrid grid, double lambda0, double tripleThreshold) {
        this.context = context;
        this.samples = grid.sampleCount();
        this.fineCount = grid.fineCount();
        this.fineStart = (float) grid.fineOffsets()[0];
        this.fineStep = (float) grid.fineStep();
        this.kernelStart = grid.kernelStart().clone();
        this.kernelLength = new int[samples];
        var longest = 0;
        for (var i = 0; i < samples; i++) {
            kernelLength[i] = grid.kernelWeights()[i].length;
            longest = Math.max(longest, kernelLength[i]);
        }
        this.maxKernelLength = longest;
        this.kernelWeights = new float[samples * longest];
        for (var i = 0; i < samples; i++) {
            var weights = grid.kernelWeights()[i];
            for (var j = 0; j < weights.length; j++) {
                kernelWeights[i * longest + j] = (float) weights[j];
            }
        }
        this.constants = constants(lambda0, tripleThreshold);
    }

    /**
     * Returns a GPU fitter if OpenCL is enabled and the observation fits the limits of the kernel.
     */
    static Optional<GpuCloudFitter> create(SpectralGrid grid, double lambda0, double tripleThreshold) {
        if (grid.sampleCount() > MAX_SAMPLES) {
            return Optional.empty();
        }
        var context = OpenCLSupport.getContext();
        if (context == null) {
            return Optional.empty();
        }
        return Optional.of(new GpuCloudFitter(context, grid, lambda0, tripleThreshold));
    }

    int samples() {
        return samples;
    }

    int fineCount() {
        return fineCount;
    }

    /**
     * Fits a batch of observations.
     *
     * @param batch the observations, flattened
     * @return the fits, in the order of the batch
     */
    CloudModelFitter.Fit[] fit(Batch batch) {
        var count = batch.count();
        var results = new float[count * RESULT_STRIDE];
        context.runOp(op -> {
            var observed = upload(op, batch.observed());
            var sigma = upload(op, batch.sigma());
            var reference = upload(op, batch.reference());
            var profile = upload(op, batch.profile());
            var shape = upload(op, batch.shape());
            var hints = upload(op, batch.hints());
            var starts = upload(op, kernelStart);
            var lengths = upload(op, kernelLength);
            var weights = upload(op, kernelWeights);
            var constantsBuffer = upload(op, constants);
            var output = op.allocateBuffer(results.length * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.kernel(PROGRAM, KERNEL)
                    .arg(observed).arg(sigma).arg(reference).arg(profile).arg(shape).arg(hints)
                    .arg(starts).arg(lengths).arg(weights)
                    .arg(constantsBuffer)
                    .arg(output)
                    .arg(count).arg(samples).arg(fineCount).arg(maxKernelLength)
                    .arg(fineStart).arg(fineStep)
                    .global((long) count * LANES)
                    .local(LANES)
                    .run();
            op.read(output, results);
        });
        var fits = new CloudModelFitter.Fit[count];
        for (var k = 0; k < count; k++) {
            var offset = k * RESULT_STRIDE;
            var components = (int) results[offset];
            var parameterCount = CloudModelFitter.Fit.parameterCount(components);
            var parameters = new double[parameterCount];
            for (var q = 0; q < parameterCount; q++) {
                parameters[q] = results[offset + 2 + q];
            }
            fits[k] = CloudModelFitter.Fit.of(parameters, components, results[offset + 1], samples, results[offset + RESULT_STRIDE - 1]);
        }
        return fits;
    }

    private static long upload(GpuOp op, float[] data) {
        var buffer = op.allocateBuffer(data.length * Float.BYTES, CL_MEM_READ_ONLY);
        op.write(buffer, data);
        return buffer;
    }

    private static long upload(GpuOp op, int[] data) {
        var buffer = op.allocateBuffer(data.length * Integer.BYTES, CL_MEM_READ_ONLY);
        op.writeInts(buffer, data);
        return buffer;
    }

    /**
     * The constants of the fit, in the order of the indices declared in cloud_model.cl.
     */
    private static float[] constants(double lambda0, double tripleThreshold) {
        var values = new double[]{
                lambda0 / CloudModel.SPEED_OF_LIGHT_KMS,
                CloudModel.MIN_DOPPLER_WIDTH,
                CloudModel.MAX_DOPPLER_WIDTH,
                CloudModel.NEGLIGIBLE_OPACITY,
                CloudModelFitter.BACKGROUND_TOLERANCE,
                CloudModelFitter.PRIOR_DOPPLER_WIDTH,
                CloudModelFitter.PRIOR_DOPPLER_WIDTH_TOLERANCE,
                CloudModelFitter.CONVERGENCE,
                CloudModelFitter.MAX_ITERATIONS,
                CloudModelFitter.INITIAL_DAMPING,
                CloudModelFitter.MAX_DAMPING,
                CloudModelFitter.MIN_DAMPING,
                CloudModelFitter.INITIAL_SOURCE,
                CloudModelFitter.INITIAL_WIDTH,
                CloudModelFitter.INITIAL_OPACITY,
                CloudModelFitter.SPLIT_GAP_KMS,
                CloudModelFitter.WIDE_GAP_KMS,
                tripleThreshold,
                CloudModel.SPLIT_SIDE_FRACTION,
                CloudModel.SPLIT_CENTER_FRACTION,
                CloudModelFitter.WIDE_CENTER_OPACITY_FRACTION
        };
        var result = new float[values.length + 2 * CloudModel.parameterCount(1) + 2 * MAX_PARAMETERS];
        var index = 0;
        for (var value : values) {
            result[index++] = (float) value;
        }
        for (var bounds : new double[][]{CloudModel.lowerBounds(1), CloudModel.upperBounds(1), CloudModel.lowerBounds(3), CloudModel.upperBounds(3)}) {
            for (var value : bounds) {
                result[index++] = (float) value;
            }
        }
        return result;
    }

    /**
     * A batch of observations, flattened: one block of samples (or fine points) per observation.
     */
    record Batch(int count, float[] observed, float[] sigma, float[] reference, float[] profile, float[] shape, float[] hints) {
        static Batch allocate(int count, int samples, int fineCount) {
            return new Batch(count, new float[count * samples], new float[count * samples], new float[count * samples],
                    new float[count * fineCount], new float[count * fineCount], new float[count]);
        }
    }
}
