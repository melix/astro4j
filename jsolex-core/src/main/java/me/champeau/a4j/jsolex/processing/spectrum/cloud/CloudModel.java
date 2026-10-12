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

/**
 * The cloud model of Chae et al. (A&amp;A 706, A123, 2026): a cloud with up to three velocity
 * components, sharing the same Doppler width and source function, lying over a background
 * atmosphere. The observed intensity is
 * {@code I = I0 exp(-tau) + S (1 - exp(-tau))}, convolved by the instrumental profile.
 * The background {@code I0} is the reference profile {@code R}, scaled by a factor {@code s}
 * and broadened or narrowed by a factor {@code d}: {@code I0 = s (R + d x R')}, where
 * {@code x} is the offset from the line center.
 *
 * <p>The parameters are transformed so that the fit is unconstrained: the source function
 * and the optical thicknesses are exponentials, the Doppler width is a logistic function
 * between {@link #MIN_DOPPLER_WIDTH} and {@link #MAX_DOPPLER_WIDTH}, and the blue and red
 * velocities are expressed as positive gaps from the central velocity, which keeps the
 * components ordered.</p>
 *
 * <p>Instances hold work buffers and must not be shared between threads.</p>
 */
public final class CloudModel {
    public static final double SPEED_OF_LIGHT_KMS = 299_792.458;
    public static final double MIN_DOPPLER_WIDTH = 0.25;
    public static final double MAX_DOPPLER_WIDTH = 0.44;

    static final int SOURCE = 0;
    static final int WIDTH = 1;
    static final int SINGLE_TAU = 2;
    static final int SINGLE_VELOCITY = 3;
    static final int SINGLE_SCALE = 4;
    static final int SINGLE_BACKGROUND_WIDTH = 5;
    static final int TAU_BLUE = 2;
    static final int TAU_CENTER = 3;
    static final int TAU_RED = 4;
    static final int VELOCITY = 5;
    static final int BLUE_GAP = 6;
    static final int RED_GAP = 7;
    static final int SCALE = 8;
    static final int BACKGROUND_WIDTH = 9;
    static final double MAX_BACKGROUND_WIDTH = 0.3;
    static final double MAX_SOURCE_FUNCTION = 0.4;
    static final double SPLIT_SIDE_FRACTION = 0.15;
    static final double SPLIT_CENTER_FRACTION = 0.8;

    private static final double[] LOWER_SINGLE = {Math.log(0.01), -8, -7, -80, 0.5, -MAX_BACKGROUND_WIDTH};
    private static final double[] UPPER_SINGLE = {Math.log(MAX_SOURCE_FUNCTION), 8, Math.log(20), 80, 2, MAX_BACKGROUND_WIDTH};
    private static final double[] LOWER_TRIPLE = {Math.log(0.01), -8, -7, -7, -7, -80, Math.log(0.5), Math.log(0.5), 0.5, -MAX_BACKGROUND_WIDTH};
    private static final double[] UPPER_TRIPLE = {Math.log(MAX_SOURCE_FUNCTION), 8, Math.log(20), Math.log(20), Math.log(20), 80, Math.log(60), Math.log(60), 2, MAX_BACKGROUND_WIDTH};

    static final double NEGLIGIBLE_OPACITY = 1e-9;

    private final SpectralGrid grid;
    private final double lambda0;
    private final double[] fineModel;
    private final double[][] fineJacobian;
    private final double[] convolvedProfile;
    private final double[] convolvedShape;
    private final double[] convolvedDerivative;
    private Background prepared;
    private final double[] opacities = new double[3];
    private final double[] centers = new double[3];
    private final double[] weighted = new double[3];
    private final double[] reduced = new double[3];
    private final int[] componentFrom = new int[3];
    private final int[] componentTo = new int[3];
    private final double[][] gaussians;
    private int affectedFrom;
    private int affectedTo = -1;

    public CloudModel(SpectralGrid grid, double lambda0) {
        this.grid = grid;
        this.lambda0 = lambda0;
        this.fineModel = new double[grid.fineCount()];
        this.fineJacobian = new double[parameterCount(3)][grid.fineCount()];
        this.convolvedProfile = new double[grid.sampleCount()];
        this.convolvedShape = new double[grid.sampleCount()];
        this.convolvedDerivative = new double[grid.sampleCount()];
        this.gaussians = new double[3][grid.fineCount()];
    }

    /**
     * Convolves the background and its derivatives, unless they were already convolved.
     * The arrays of a background must not change once it was prepared.
     */
    void prepare(Background background) {
        if (prepared != background) {
            grid.sample(background.profile(), convolvedProfile);
            grid.sample(background.widthShape(), convolvedShape);
            grid.sample(background.derivative(), convolvedDerivative);
            prepared = background;
        }
    }

    double[] convolvedProfile() {
        return convolvedProfile;
    }

    double[] convolvedShape() {
        return convolvedShape;
    }

    double[] convolvedDerivative() {
        return convolvedDerivative;
    }

    /**
     * Returns the index of the first sample which the cloud affected in the last evaluation of
     * the derivatives. Outside of the affected samples, the derivatives of the model with
     * regards to the parameters of the cloud are zero.
     */
    int affectedFrom() {
        return affectedFrom;
    }

    /**
     * Returns the index of the last sample which the cloud affected in the last evaluation of the derivatives.
     */
    int affectedTo() {
        return affectedTo;
    }

    public SpectralGrid grid() {
        return grid;
    }

    /**
     * The background, on the fine grid.
     *
     * @param profile    the reference profile {@code R}
     * @param widthShape the change of the profile for a relative narrowing, {@code x R'}
     * @param derivative the derivative of the profile, {@code R'}, which gives the change of the profile for a wavelength shift
     */
    public record Background(double[] profile, double[] widthShape, double[] derivative) {
        /**
         * Creates a background from a reference profile on the fine grid.
         */
        public static Background of(double[] profile, double[] fineOffsets) {
            var n = profile.length;
            var shape = new double[n];
            var derivatives = new double[n];
            for (var j = 0; j < n; j++) {
                var previous = Math.max(0, j - 1);
                var next = Math.min(n - 1, j + 1);
                var derivative = (profile[next] - profile[previous]) / (fineOffsets[next] - fineOffsets[previous]);
                derivatives[j] = derivative;
                shape[j] = fineOffsets[j] * derivative;
            }
            return new Background(profile, shape, derivatives);
        }
    }

    /**
     * Returns the background before scaling, {@code R + d x R'}, at one point of a profile and of its width shape.
     */
    static double unscaledBackground(double[] profile, double[] widthShape, double width, int index) {
        return profile[index] + width * widthShape[index];
    }

    public static int parameterCount(int components) {
        return components == 1 ? 6 : 10;
    }

    public static int scaleIndex(int components) {
        return components == 1 ? SINGLE_SCALE : SCALE;
    }

    public static int backgroundWidthIndex(int components) {
        return components == 1 ? SINGLE_BACKGROUND_WIDTH : BACKGROUND_WIDTH;
    }

    static double[] lowerBounds(int components) {
        return components == 1 ? LOWER_SINGLE : LOWER_TRIPLE;
    }

    static double[] upperBounds(int components) {
        return components == 1 ? UPPER_SINGLE : UPPER_TRIPLE;
    }

    /**
     * Returns the internal parameter vector of a single component cloud.
     */
    public static double[] single(double sourceFunction, double dopplerWidth, double opacity, double velocity, double backgroundScale) {
        return new double[]{Math.log(sourceFunction), widthToInternal(dopplerWidth), Math.log(opacity), velocity, backgroundScale, 0};
    }

    /**
     * Returns the internal parameter vector of a three component cloud. The velocities must be ordered.
     */
    public static double[] triple(double sourceFunction, double dopplerWidth, double[] opacities, double[] velocities, double backgroundScale) {
        return new double[]{
                Math.log(sourceFunction),
                widthToInternal(dopplerWidth),
                Math.log(opacities[CloudParameters.BLUE]),
                Math.log(opacities[CloudParameters.CENTER]),
                Math.log(opacities[CloudParameters.RED]),
                velocities[CloudParameters.CENTER],
                Math.log(velocities[CloudParameters.CENTER] - velocities[CloudParameters.BLUE]),
                Math.log(velocities[CloudParameters.RED] - velocities[CloudParameters.CENTER]),
                backgroundScale,
                0
        };
    }

    /**
     * Returns the internal parameters of a three component cloud starting from a single component fit.
     */
    static double[] splitSingle(double[] single, double gap) {
        var tau = single[SINGLE_TAU];
        return new double[]{
                single[SOURCE],
                single[WIDTH],
                tau + Math.log(SPLIT_SIDE_FRACTION),
                tau + Math.log(SPLIT_CENTER_FRACTION),
                tau + Math.log(SPLIT_SIDE_FRACTION),
                single[SINGLE_VELOCITY],
                Math.log(gap),
                Math.log(gap),
                single[SINGLE_SCALE],
                single[SINGLE_BACKGROUND_WIDTH]
        };
    }

    private static double widthToInternal(double width) {
        var t = Math.clamp((width - MIN_DOPPLER_WIDTH) / (MAX_DOPPLER_WIDTH - MIN_DOPPLER_WIDTH), 1e-6, 1 - 1e-6);
        return Math.log(t / (1 - t));
    }

    static double dopplerWidth(double internalWidth) {
        return MIN_DOPPLER_WIDTH + (MAX_DOPPLER_WIDTH - MIN_DOPPLER_WIDTH) * logistic(internalWidth);
    }

    static double dopplerWidthDerivative(double internalWidth) {
        var sigmoid = logistic(internalWidth);
        return (MAX_DOPPLER_WIDTH - MIN_DOPPLER_WIDTH) * sigmoid * (1 - sigmoid);
    }

    /**
     * Returns true if a Doppler width lies at one of its bounds, meaning that the profile did not constrain it.
     */
    public static boolean isAtWidthBound(double dopplerWidth) {
        var margin = 0.01 * (MAX_DOPPLER_WIDTH - MIN_DOPPLER_WIDTH);
        return dopplerWidth <= MIN_DOPPLER_WIDTH + margin || dopplerWidth >= MAX_DOPPLER_WIDTH - margin;
    }

    private static double logistic(double u) {
        return 1 / (1 + Math.exp(-u));
    }

    /**
     * Decodes an internal parameter vector.
     */
    public static CloudParameters decode(double[] p, int components) {
        var source = Math.exp(p[SOURCE]);
        var width = dopplerWidth(p[WIDTH]);
        if (components == 1) {
            return new CloudParameters(source, width,
                    new double[]{0, Math.exp(p[SINGLE_TAU]), 0},
                    new double[]{p[SINGLE_VELOCITY], p[SINGLE_VELOCITY], p[SINGLE_VELOCITY]},
                    p[SINGLE_SCALE], p[SINGLE_BACKGROUND_WIDTH], 1);
        }
        var velocity = p[VELOCITY];
        return new CloudParameters(source, width,
                new double[]{Math.exp(p[TAU_BLUE]), Math.exp(p[TAU_CENTER]), Math.exp(p[TAU_RED])},
                new double[]{velocity - Math.exp(p[BLUE_GAP]), velocity, velocity + Math.exp(p[RED_GAP])},
                p[SCALE], p[BACKGROUND_WIDTH], 3);
    }

    /**
     * Computes {@code exp(-u²)} over the range of a component, using the ratio of two
     * consecutive values of a gaussian sampled at a constant step, which avoids an
     * exponential per point.
     */
    private void computeGaussian(int component, double[] fine, double width) {
        var delta = grid.fineStep() / width;
        var first = componentFrom[component];
        var u = (fine[first] - centers[component]) / width;
        var value = Math.exp(-u * u);
        var ratio = Math.exp(-(2 * u * delta + delta * delta));
        var factor = Math.exp(-2 * delta * delta);
        var values = gaussians[component];
        for (var j = first; j <= componentTo[component]; j++) {
            values[j] = value;
            value *= ratio;
            ratio *= factor;
        }
    }

    /**
     * Evaluates the model at the observed wavelengths.
     *
     * @param p          the internal parameters
     * @param components the number of components
     * @param background the background, before scaling and broadening
     * @param samples    the model at the observed wavelengths
     * @param jacobian   if not null, receives the derivatives of the model with regards to each internal parameter, indexed by parameter then sample
     */
    public void evaluate(double[] p, int components, Background background, double[] samples, double[][] jacobian) {
        var source = Math.exp(p[SOURCE]);
        var width = dopplerWidth(p[WIDTH]);
        var widthDerivative = dopplerWidthDerivative(p[WIDTH]);
        var velocityToAngstroms = lambda0 / SPEED_OF_LIGHT_KMS;
        int count;
        double scale;
        double backgroundWidth;
        var blueGap = 0d;
        var redGap = 0d;
        if (components == 1) {
            count = 1;
            opacities[0] = Math.exp(p[SINGLE_TAU]);
            centers[0] = p[SINGLE_VELOCITY] * velocityToAngstroms;
            scale = p[SINGLE_SCALE];
            backgroundWidth = p[SINGLE_BACKGROUND_WIDTH];
        } else {
            count = 3;
            blueGap = Math.exp(p[BLUE_GAP]);
            redGap = Math.exp(p[RED_GAP]);
            opacities[0] = Math.exp(p[TAU_BLUE]);
            opacities[1] = Math.exp(p[TAU_CENTER]);
            opacities[2] = Math.exp(p[TAU_RED]);
            centers[0] = (p[VELOCITY] - blueGap) * velocityToAngstroms;
            centers[1] = p[VELOCITY] * velocityToAngstroms;
            centers[2] = (p[VELOCITY] + redGap) * velocityToAngstroms;
            scale = p[SCALE];
            backgroundWidth = p[BACKGROUND_WIDTH];
        }
        prepare(background);
        var fine = grid.fineOffsets();
        var from = grid.fineCount();
        var to = -1;
        for (var k = 0; k < count; k++) {
            if (opacities[k] > NEGLIGIBLE_OPACITY) {
                var halfWidth = width * Math.sqrt(Math.log(opacities[k] / NEGLIGIBLE_OPACITY));
                componentFrom[k] = grid.fineIndexOf(centers[k] - halfWidth);
                componentTo[k] = grid.fineIndexOf(centers[k] + halfWidth);
                computeGaussian(k, fine, width);
                from = Math.min(from, componentFrom[k]);
                to = Math.max(to, componentTo[k]);
            } else {
                componentFrom[k] = 1;
                componentTo[k] = 0;
            }
        }
        var withJacobian = jacobian != null;
        if (withJacobian) {
            affectedFrom = grid.firstSampleReaching(from);
            affectedTo = grid.lastSampleReaching(to);
        }
        var profile = background.profile();
        var shape = background.widthShape();
        for (var j = from; j <= to; j++) {
            var x = fine[j];
            var tau = 0d;
            for (var k = 0; k < count; k++) {
                var contribution = j >= componentFrom[k] && j <= componentTo[k] ? opacities[k] * gaussians[k][j] : 0;
                weighted[k] = contribution;
                reduced[k] = (x - centers[k]) / width;
                tau += contribution;
            }
            var absorbed = -Math.expm1(-tau);
            var transmission = 1 - absorbed;
            var unscaled = unscaledBackground(profile, shape, backgroundWidth, j);
            var background0 = scale * unscaled;
            fineModel[j] = (source - background0) * absorbed;
            if (withJacobian) {
                var dTau = -(background0 - source) * transmission;
                fineJacobian[SOURCE][j] = source * absorbed;
                var widthTerm = 0d;
                for (var k = 0; k < count; k++) {
                    widthTerm += weighted[k] * 2 * reduced[k] * reduced[k] / width;
                }
                fineJacobian[WIDTH][j] = dTau * widthTerm * widthDerivative;
                if (count == 1) {
                    fineJacobian[SINGLE_TAU][j] = dTau * weighted[0];
                    fineJacobian[SINGLE_VELOCITY][j] = dTau * weighted[0] * 2 * reduced[0] / width * velocityToAngstroms;
                    fineJacobian[SINGLE_SCALE][j] = -unscaled * absorbed;
                    fineJacobian[SINGLE_BACKGROUND_WIDTH][j] = -scale * shape[j] * absorbed;
                } else {
                    var shiftBlue = dTau * weighted[0] * 2 * reduced[0] / width * velocityToAngstroms;
                    var shiftCenter = dTau * weighted[1] * 2 * reduced[1] / width * velocityToAngstroms;
                    var shiftRed = dTau * weighted[2] * 2 * reduced[2] / width * velocityToAngstroms;
                    fineJacobian[TAU_BLUE][j] = dTau * weighted[0];
                    fineJacobian[TAU_CENTER][j] = dTau * weighted[1];
                    fineJacobian[TAU_RED][j] = dTau * weighted[2];
                    fineJacobian[VELOCITY][j] = shiftBlue + shiftCenter + shiftRed;
                    fineJacobian[BLUE_GAP][j] = -shiftBlue * blueGap;
                    fineJacobian[RED_GAP][j] = shiftRed * redGap;
                    fineJacobian[SCALE][j] = -unscaled * absorbed;
                    fineJacobian[BACKGROUND_WIDTH][j] = -scale * shape[j] * absorbed;
                }
            }
        }
        grid.sample(fineModel, from, to, samples);
        for (var i = 0; i < samples.length; i++) {
            samples[i] += scale * unscaledBackground(convolvedProfile, convolvedShape, backgroundWidth, i);
        }
        if (withJacobian) {
            var n = parameterCount(components);
            for (var q = 0; q < n; q++) {
                grid.sample(fineJacobian[q], from, to, jacobian[q]);
            }
            var scaleIndex = scaleIndex(components);
            var widthIndex = backgroundWidthIndex(components);
            for (var i = 0; i < samples.length; i++) {
                jacobian[scaleIndex][i] += unscaledBackground(convolvedProfile, convolvedShape, backgroundWidth, i);
                jacobian[widthIndex][i] += scale * convolvedShape[i];
            }
        }
    }
}
