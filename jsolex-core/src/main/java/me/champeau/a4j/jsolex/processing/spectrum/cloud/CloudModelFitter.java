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

import me.champeau.a4j.math.regression.LeastSquares;

/**
 * Fits the cloud model to an observed profile with a bounded Levenberg-Marquardt
 * least squares. The minimized quantity follows Chae et al. (2026): the observed
 * profile is fitted tightly, while the background is tied loosely to the reference
 * profile, with an uncertainty five times larger at each wavelength. The Doppler width is
 * weakly tied to 0.30 Å, the width of a 7500 K cloud with a non-thermal speed of 8 km/s,
 * so that it stays defined when the profile does not constrain it.
 *
 * <p>Instances hold work buffers and must not be shared between threads.</p>
 */
public final class CloudModelFitter {
    static final int MAX_ITERATIONS = 60;
    static final double CONVERGENCE = 1e-3;
    static final double INITIAL_DAMPING = 1e-2;
    static final double MAX_DAMPING = 1e10;
    static final double MIN_DAMPING = 1e-10;
    static final double BACKGROUND_TOLERANCE = 5;
    static final double SPLIT_GAP_KMS = 10;
    static final double WIDE_GAP_KMS = 30;
    static final double INITIAL_SOURCE = 0.15;
    static final double INITIAL_WIDTH = 0.33;
    static final double INITIAL_OPACITY = 0.5;
    static final double WIDE_CENTER_OPACITY_FRACTION = 0.25;
    private static final int NO_CLOUD_PARAMETERS = 2;
    static final double PRIOR_DOPPLER_WIDTH = 0.30;
    static final double PRIOR_DOPPLER_WIDTH_TOLERANCE = 0.05;

    private final CloudModel model;
    private final int samples;
    private final double[] modelValues;
    private final double[] backgroundValues;
    private final double[] trialValues;
    private final double[][] jacobian;
    private final double[] residuals;
    private final double[][] normal;
    private final double[] gradient;
    private final double[] step;
    private final double[][] cholesky;
    private final double[] trial;
    private double[] convolvedProfile;
    private double[] convolvedShape;
    private double[] convolvedDerivative;

    public CloudModelFitter(CloudModel model) {
        this.model = model;
        this.samples = model.grid().sampleCount();
        this.modelValues = new double[samples];
        this.backgroundValues = new double[samples];
        this.trialValues = new double[samples];
        var n = CloudModel.parameterCount(3);
        this.jacobian = new double[n][samples];
        this.residuals = new double[samples];
        this.normal = new double[n][n];
        this.gradient = new double[n];
        this.step = new double[n];
        this.cholesky = new double[n][n];
        this.trial = new double[n];
    }

    /**
     * The result of a fit.
     *
     * @param parameters the internal parameters, or the background scale and width when there are no components
     * @param components the number of components, 0 when no significant absorption was found
     * @param cost the sum of squared weighted residuals, including the term which ties the background to the reference, but not the prior on the Doppler width
     * @param degreesOfFreedom the number of samples minus the number of parameters
     * @param noCloudCost the cost of the fit without any cloud, or NaN when it is unknown
     */
    public record Fit(double[] parameters, int components, double cost, int degreesOfFreedom, double noCloudCost) {
        static Fit of(double[] parameters, int components, double cost, int samples, double noCloudCost) {
            return new Fit(parameters, components, cost, samples - parameterCount(components), noCloudCost);
        }

        public double reducedChi2() {
            return cost / Math.max(1, degreesOfFreedom);
        }

        /**
         * Returns the signal to noise ratio of the absorption: the square root of the decrease of
         * the chi2 brought by the cloud, compared with the background alone.
         */
        public double detection() {
            if (components == 0 || Double.isNaN(noCloudCost)) {
                return 0;
            }
            return Math.sqrt(Math.max(0, noCloudCost - cost));
        }

        Fit withNoCloudCost(double value) {
            return new Fit(parameters, components, cost, degreesOfFreedom, value);
        }

        public double bic(int samples) {
            return samples * Math.log(Math.max(cost, 1e-300) / samples) + parameterCount(components) * Math.log(samples);
        }

        public CloudParameters decode() {
            if (components == 0) {
                return CloudParameters.noCloud(parameters[0], parameters[1]);
            }
            return CloudModel.decode(parameters, components);
        }

        static int parameterCount(int components) {
            return components == 0 ? NO_CLOUD_PARAMETERS : CloudModel.parameterCount(components);
        }
    }

    /**
     * The observation to fit.
     *
     * @param observed the observed profile at the sampled wavelengths
     * @param sigma the uncertainty of each sample
     * @param reference the reference profile at the sampled wavelengths, which the background is tied to
     * @param background the background on the fine grid, deconvolved from the instrumental profile
     */
    public record Observation(double[] observed, double[] sigma, double[] reference, CloudModel.Background background) {
    }

    /**
     * Fits the profile without any cloud and with a single component cloud. If the best of both
     * leaves residuals above the threshold, a three component cloud is also tried. The model with
     * the lowest Bayesian information criterion is kept. Each cloud model is fitted from several
     * starting points to avoid the local minima of the least squares.
     *
     * @param observation the observation
     * @param velocityHint an estimate of the velocity of the absorbing material, in km/s
     * @param tripleThreshold the reduced chi2 above which three components are tried
     * @return the best fit
     */
    public Fit fitBest(Observation observation, double velocityHint, double tripleThreshold) {
        var none = fitNoCloud(observation);
        var single = best(
                fit(observation, CloudModel.single(INITIAL_SOURCE, INITIAL_WIDTH, INITIAL_OPACITY, velocityHint, 1), 1),
                fit(observation, CloudModel.single(INITIAL_SOURCE, INITIAL_WIDTH, INITIAL_OPACITY, 0, 1), 1)
        );
        var selected = single.bic(samples) < none.bic(samples) ? single : none;
        if (selected.reducedChi2() <= tripleThreshold) {
            return selected.withNoCloudCost(none.cost());
        }
        var triple = best(
                fit(observation, CloudModel.splitSingle(single.parameters(), SPLIT_GAP_KMS), 3),
                fit(observation, CloudModel.triple(INITIAL_SOURCE, INITIAL_WIDTH,
                        new double[]{INITIAL_OPACITY, INITIAL_OPACITY * WIDE_CENTER_OPACITY_FRACTION, INITIAL_OPACITY},
                        new double[]{velocityHint - WIDE_GAP_KMS, velocityHint, velocityHint + WIDE_GAP_KMS}, 1), 3)
        );
        return (triple.bic(samples) < selected.bic(samples) ? triple : selected).withNoCloudCost(none.cost());
    }

    /**
     * Estimates the velocity of the absorbing (or emitting) material from the centroid of the
     * difference between the observed and the reference profiles.
     *
     * @param observation the observation
     * @param sampleOffsets the observed wavelengths, as offsets in angstroms from the line center
     * @param lambda0 the rest wavelength of the line, in angstroms
     * @param halfWidth the half width of the window used for the centroid, in angstroms
     * @return the velocity, in km/s
     */
    public static double estimateVelocity(Observation observation, double[] sampleOffsets, double lambda0, double halfWidth) {
        var observed = observation.observed();
        var reference = observation.reference();
        var sum = 0d;
        var weights = 0d;
        for (var i = 0; i < sampleOffsets.length; i++) {
            if (Math.abs(sampleOffsets[i]) <= halfWidth) {
                var weight = Math.abs(reference[i] - observed[i]);
                sum += weight * sampleOffsets[i];
                weights += weight;
            }
        }
        if (weights == 0) {
            return 0;
        }
        return Math.clamp(sum / weights / lambda0 * CloudModel.SPEED_OF_LIGHT_KMS, -60, 60);
    }

    private static Fit best(Fit first, Fit second) {
        return second.cost() < first.cost() ? second : first;
    }

    /**
     * Fits the background alone, without any cloud. The model is linear in the background
     * scale and in the product of the scale and the width, so the fit is exact.
     */
    private Fit fitNoCloud(Observation observation) {
        prepare(observation);
        var observed = observation.observed();
        var reference = observation.reference();
        var sigma = observation.sigma();
        var aa = 0d;
        var ab = 0d;
        var bb = 0d;
        var ay = 0d;
        var by = 0d;
        var tolerance2 = BACKGROUND_TOLERANCE * BACKGROUND_TOLERANCE;
        for (var i = 0; i < samples; i++) {
            var weight = 1 / (sigma[i] * sigma[i]);
            var backgroundWeight = weight / tolerance2;
            var a = convolvedProfile[i];
            var b = convolvedShape[i];
            var total = weight + backgroundWeight;
            aa += total * a * a;
            ab += total * a * b;
            bb += total * b * b;
            ay += a * (weight * observed[i] + backgroundWeight * reference[i]);
            by += b * (weight * observed[i] + backgroundWeight * reference[i]);
        }
        var determinant = aa * bb - ab * ab;
        double scale;
        double product;
        if (determinant > 1e-12 * aa * bb) {
            scale = (ay * bb - by * ab) / determinant;
            product = (aa * by - ab * ay) / determinant;
        } else {
            scale = ay / aa;
            product = 0;
        }
        var width = Math.clamp(product / scale, -CloudModel.MAX_BACKGROUND_WIDTH, CloudModel.MAX_BACKGROUND_WIDTH);
        var parameters = new double[]{scale, width};
        evaluateNoCloud(scale, width, modelValues);
        var cost = cost(observation, modelValues, scale, width);
        return Fit.of(parameters, 0, cost, samples, cost);
    }

    /**
     * Estimates the wavelength shift of the whole observed profile compared with the background,
     * by fitting the background scale, width and shift without any cloud.
     *
     * @param observation the observation
     * @return the shift, in angstroms, or NaN if it cannot be determined
     */
    public double estimateShift(Observation observation) {
        prepare(observation);
        var observed = observation.observed();
        var sigma = observation.sigma();
        var fit = new LeastSquares(3);
        var basis = new double[3];
        for (var i = 0; i < samples; i++) {
            basis[0] = convolvedProfile[i];
            basis[1] = convolvedShape[i];
            basis[2] = convolvedDerivative[i];
            fit.add(basis, observed[i], 1 / (sigma[i] * sigma[i]));
        }
        var solution = fit.solve();
        if (solution == null || solution[0] <= 0) {
            return Double.NaN;
        }
        return -solution[2] / solution[0];
    }

    /**
     * Fits the model with a fixed number of components.
     */
    public Fit fit(Observation observation, double[] initial, int components) {
        prepare(observation);
        var n = CloudModel.parameterCount(components);
        var lower = CloudModel.lowerBounds(components);
        var upper = CloudModel.upperBounds(components);
        var scaleIndex = CloudModel.scaleIndex(components);
        var widthIndex = CloudModel.backgroundWidthIndex(components);
        var p = initial.clone();
        clamp(p, lower, upper, n);
        model.evaluate(p, components, observation.background(), modelValues, jacobian);
        var cost = cost(observation, modelValues, p[scaleIndex], p[widthIndex]) + widthPrior(p[CloudModel.WIDTH]);
        var damping = INITIAL_DAMPING;
        for (var iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            buildNormalEquations(observation, n, scaleIndex, widthIndex, p[scaleIndex], p[widthIndex]);
            addWidthPrior(p[CloudModel.WIDTH]);
            var improved = false;
            while (damping < MAX_DAMPING) {
                if (!solve(n, damping)) {
                    damping *= 10;
                    continue;
                }
                for (var q = 0; q < n; q++) {
                    trial[q] = p[q] + step[q];
                }
                clamp(trial, lower, upper, n);
                model.evaluate(trial, components, observation.background(), trialValues, null);
                var trialCost = cost(observation, trialValues, trial[scaleIndex], trial[widthIndex]) + widthPrior(trial[CloudModel.WIDTH]);
                if (trialCost < cost) {
                    improved = cost - trialCost > CONVERGENCE;
                    System.arraycopy(trial, 0, p, 0, n);
                    cost = trialCost;
                    damping = Math.max(MIN_DAMPING, damping * 0.3);
                    break;
                }
                damping *= 10;
            }
            if (!improved) {
                break;
            }
            model.evaluate(p, components, observation.background(), modelValues, jacobian);
        }
        return Fit.of(p, components, cost - widthPrior(p[CloudModel.WIDTH]), samples, Double.NaN);
    }

    /**
     * Evaluates the model of a fit at the sampled wavelengths.
     */
    public double[] evaluate(Fit fit, Observation observation) {
        prepare(observation);
        var result = new double[samples];
        if (fit.components() == 0) {
            evaluateNoCloud(fit.parameters()[0], fit.parameters()[1], result);
        } else {
            model.evaluate(fit.parameters(), fit.components(), observation.background(), result, null);
        }
        return result;
    }

    /**
     * Returns the light added by the cloud to the observed profile, summed over the samples, in
     * units of the continuum. It is negative when the cloud absorbs more light than it emits,
     * and positive when it makes the line brighter than its background, as in plages.
     */
    public double cloudContribution(Fit fit, Observation observation) {
        if (fit.components() == 0) {
            return 0;
        }
        prepare(observation);
        var parameters = fit.decode();
        model.evaluate(fit.parameters(), fit.components(), observation.background(), modelValues, null);
        evaluateNoCloud(parameters.backgroundScale(), parameters.backgroundWidth(), backgroundValues);
        var sum = 0d;
        for (var i = 0; i < samples; i++) {
            sum += modelValues[i] - backgroundValues[i];
        }
        return sum;
    }

    /**
     * Evaluates the background of a fit at the sampled wavelengths.
     */
    public double[] evaluateBackground(CloudParameters parameters, Observation observation) {
        prepare(observation);
        var result = new double[samples];
        evaluateNoCloud(parameters.backgroundScale(), parameters.backgroundWidth(), result);
        return result;
    }

    private void prepare(Observation observation) {
        model.prepare(observation.background());
        convolvedProfile = model.convolvedProfile();
        convolvedShape = model.convolvedShape();
        convolvedDerivative = model.convolvedDerivative();
    }

    private void evaluateNoCloud(double scale, double width, double[] out) {
        for (var i = 0; i < samples; i++) {
            out[i] = scale * CloudModel.unscaledBackground(convolvedProfile, convolvedShape, width, i);
        }
    }

    private static double widthPriorResidual(double internalWidth) {
        return (CloudModel.dopplerWidth(internalWidth) - PRIOR_DOPPLER_WIDTH) / PRIOR_DOPPLER_WIDTH_TOLERANCE;
    }

    private static double widthPrior(double internalWidth) {
        var r = widthPriorResidual(internalWidth);
        return r * r;
    }

    private void addWidthPrior(double internalWidth) {
        var r = widthPriorResidual(internalWidth);
        var derivative = CloudModel.dopplerWidthDerivative(internalWidth) / PRIOR_DOPPLER_WIDTH_TOLERANCE;
        normal[CloudModel.WIDTH][CloudModel.WIDTH] += derivative * derivative;
        gradient[CloudModel.WIDTH] += derivative * r;
    }

    private double cost(Observation observation, double[] values, double scale, double width) {
        var observed = observation.observed();
        var reference = observation.reference();
        var sigma = observation.sigma();
        var sum = 0d;
        for (var i = 0; i < samples; i++) {
            var r = (values[i] - observed[i]) / sigma[i];
            var b = (scale * CloudModel.unscaledBackground(convolvedProfile, convolvedShape, width, i) - reference[i]) / (BACKGROUND_TOLERANCE * sigma[i]);
            sum += r * r + b * b;
        }
        return sum;
    }

    private void buildNormalEquations(Observation observation, int n, int scaleIndex, int widthIndex, double scale, double width) {
        var observed = observation.observed();
        var reference = observation.reference();
        var sigma = observation.sigma();
        for (var i = 0; i < samples; i++) {
            var inverse = 1 / sigma[i];
            residuals[i] = (modelValues[i] - observed[i]) * inverse;
            for (var q = 0; q < n; q++) {
                jacobian[q][i] *= inverse;
            }
        }
        var affectedFrom = model.affectedFrom();
        var affectedTo = model.affectedTo();
        for (var a = 0; a < n; a++) {
            var ja = jacobian[a];
            var fullA = a == scaleIndex || a == widthIndex;
            var g = 0d;
            for (var i = fullA ? 0 : affectedFrom; i <= (fullA ? samples - 1 : affectedTo); i++) {
                g += ja[i] * residuals[i];
            }
            gradient[a] = g;
            for (var b = 0; b <= a; b++) {
                var jb = jacobian[b];
                var full = fullA && (b == scaleIndex || b == widthIndex);
                var sum = 0d;
                for (var i = full ? 0 : affectedFrom; i <= (full ? samples - 1 : affectedTo); i++) {
                    sum += ja[i] * jb[i];
                }
                normal[a][b] = sum;
                normal[b][a] = sum;
            }
        }
        for (var i = 0; i < samples; i++) {
            var inverse = 1 / (BACKGROUND_TOLERANCE * sigma[i]);
            var unscaled = CloudModel.unscaledBackground(convolvedProfile, convolvedShape, width, i);
            var residual = (scale * unscaled - reference[i]) * inverse;
            var ds = unscaled * inverse;
            var dw = scale * convolvedShape[i] * inverse;
            normal[scaleIndex][scaleIndex] += ds * ds;
            normal[widthIndex][widthIndex] += dw * dw;
            normal[scaleIndex][widthIndex] += ds * dw;
            normal[widthIndex][scaleIndex] += ds * dw;
            gradient[scaleIndex] += ds * residual;
            gradient[widthIndex] += dw * residual;
        }
    }

    private boolean solve(int n, double damping) {
        for (var a = 0; a < n; a++) {
            for (var b = 0; b <= a; b++) {
                var value = normal[a][b];
                if (a == b) {
                    value += damping * Math.max(normal[a][a], 1e-12);
                }
                for (var k = 0; k < b; k++) {
                    value -= cholesky[a][k] * cholesky[b][k];
                }
                if (a == b) {
                    if (value <= 0 || !Double.isFinite(value)) {
                        return false;
                    }
                    cholesky[a][a] = Math.sqrt(value);
                } else {
                    cholesky[a][b] = value / cholesky[b][b];
                }
            }
        }
        for (var a = 0; a < n; a++) {
            var value = -gradient[a];
            for (var k = 0; k < a; k++) {
                value -= cholesky[a][k] * step[k];
            }
            step[a] = value / cholesky[a][a];
        }
        for (var a = n - 1; a >= 0; a--) {
            var value = step[a];
            for (var k = a + 1; k < n; k++) {
                value -= cholesky[k][a] * step[k];
            }
            step[a] = value / cholesky[a][a];
        }
        for (var a = 0; a < n; a++) {
            if (!Double.isFinite(step[a])) {
                return false;
            }
        }
        return true;
    }

    private static void clamp(double[] p, double[] lower, double[] upper, int n) {
        for (var q = 0; q < n; q++) {
            p[q] = Math.clamp(p[q], lower[q], upper[q]);
        }
    }
}
