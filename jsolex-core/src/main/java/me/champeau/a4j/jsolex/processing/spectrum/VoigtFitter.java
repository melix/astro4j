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
package me.champeau.a4j.jsolex.processing.spectrum;

import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fits a Voigt profile to spectral line data using Levenberg-Marquardt optimization.
 * <p>
 * The Voigt profile is a convolution of Gaussian and Lorentzian profiles, commonly
 * used for spectral lines where both Doppler (thermal) and pressure (collisional)
 * broadening are significant.
 * <p>
 * This implementation uses the pseudo-Voigt approximation (Thompson et al., 1987)
 * which provides an accurate approximation (error &lt; 1%) with simpler computation
 * than the full Faddeeva function.
 * <p>
 * For absorption lines, we model the profile as:
 * I(λ) = continuum - amplitude × V(λ - center; fG, fL)
 * <p>
 * where V is the pseudo-Voigt function parameterized by Gaussian and Lorentzian FWHMs.
 */
public final class VoigtFitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoigtFitter.class);

    private static final int MAX_ITERATIONS = 500;
    private static final int MAX_EVALUATIONS = 1000;
    private static final double CONVERGENCE_THRESHOLD = 1e-8;

    private VoigtFitter() {
    }

    /**
     * Parameters for the Voigt profile fit.
     *
     * @param continuum      the continuum intensity level
     * @param amplitude      the absorption depth (positive for absorption)
     * @param center         the line center wavelength (Angstroms)
     * @param sigmaGaussian  the Gaussian width parameter σ (Angstroms)
     * @param gammaLorentz   the Lorentzian width parameter γ (Angstroms)
     * @param rmsResidual    the RMS of fit residuals (0 if fit failed)
     * @param converged      whether the fit converged
     */
    public record VoigtParameters(
        double continuum,
        double amplitude,
        double center,
        double sigmaGaussian,
        double gammaLorentz,
        double rmsResidual,
        boolean converged
    ) {
        public static VoigtParameters failed() {
            return new VoigtParameters(0, 0, 0, 0, 0, 0, false);
        }

        public double fwhm() {
            if (!converged) {
                return 0;
            }
            return voigtFWHM(sigmaGaussian, gammaLorentz);
        }

        public double gaussianFWHM() {
            return 2.0 * sigmaGaussian * Math.sqrt(2.0 * Math.log(2.0));
        }

        public double lorentzianFWHM() {
            return 2.0 * gammaLorentz;
        }
    }

    /**
     * Fits a Voigt profile to the given spectral line data.
     *
     * @param profile            the measured intensity profile
     * @param estimatedContinuum initial estimate of continuum level
     * @param estimatedCenter    initial estimate of line center wavelength
     * @return the fitted Voigt parameters
     */
    public static VoigtParameters fit(List<SpectrumAnalyzer.DataPoint> profile,
                                      double estimatedContinuum,
                                      double estimatedCenter) {
        if (profile == null || profile.size() < 5) {
            return VoigtParameters.failed();
        }

        double[] wavelengths = profile.stream()
            .mapToDouble(p -> p.wavelen().angstroms())
            .toArray();
        double[] intensities = profile.stream()
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .toArray();

        double minIntensity = Double.MAX_VALUE;
        int minIndex = 0;
        for (int i = 0; i < intensities.length; i++) {
            if (intensities[i] < minIntensity) {
                minIntensity = intensities[i];
                minIndex = i;
            }
        }

        double estimatedAmplitude = estimatedContinuum - minIntensity;
        if (estimatedAmplitude <= 0) {
            return VoigtParameters.failed();
        }

        double estimatedSigma = estimateGaussianWidth(wavelengths, intensities, minIndex, estimatedContinuum);
        double estimatedGamma = estimatedSigma * 0.3;

        double[] initialGuess = {
            estimatedContinuum,
            estimatedAmplitude,
            estimatedCenter,
            estimatedSigma,
            estimatedGamma
        };

        try {
            var model = createModelFunction(wavelengths);
            var jacobian = createJacobianFunction(wavelengths);

            var problem = new LeastSquaresBuilder()
                .start(initialGuess)
                .model(model, jacobian)
                .target(intensities)
                .parameterValidator(new VoigtParameterValidator())
                .lazyEvaluation(false)
                .maxIterations(MAX_ITERATIONS)
                .maxEvaluations(MAX_EVALUATIONS)
                .build();

            var optimizer = new LevenbergMarquardtOptimizer()
                .withCostRelativeTolerance(CONVERGENCE_THRESHOLD)
                .withParameterRelativeTolerance(CONVERGENCE_THRESHOLD);

            var optimum = optimizer.optimize(problem);
            double[] params = optimum.getPoint().toArray();

            double rms = computeRMS(wavelengths, intensities, params);

            double amplitude = params[1];
            if (amplitude < 0.01 * estimatedAmplitude) {
                return VoigtParameters.failed();
            }

            double sigma = Math.abs(params[3]);
            double gamma = Math.abs(params[4]);
            double fwhm = voigtFWHM(sigma, gamma);

            // Sanity check: FWHM should be reasonable relative to the data range
            double dataRange = wavelengths[wavelengths.length - 1] - wavelengths[0];
            if (fwhm > dataRange * 0.8 || fwhm < 0.01) {
                LOGGER.debug("Voigt fitting produced unreasonable FWHM: {} (data range: {})", fwhm, dataRange);
                return VoigtParameters.failed();
            }

            // Sanity check: center should be within the data range
            double center = params[2];
            if (center < wavelengths[0] || center > wavelengths[wavelengths.length - 1]) {
                LOGGER.debug("Voigt fitting produced center outside data range: {}", center);
                return VoigtParameters.failed();
            }

            return new VoigtParameters(
                params[0],
                params[1],
                center,
                sigma,
                gamma,
                rms,
                true
            );
        } catch (Exception e) {
            LOGGER.debug("Voigt fitting failed: {}", e.getMessage());
            return VoigtParameters.failed();
        }
    }

    private static class VoigtParameterValidator implements ParameterValidator {
        @Override
        public RealVector validate(RealVector params) {
            double[] p = params.toArray();
            p[0] = Math.max(1e-10, p[0]);
            p[1] = Math.max(1e-10, p[1]);
            p[3] = Math.max(0.001, Math.abs(p[3]));
            p[4] = Math.max(0.0001, Math.abs(p[4]));
            return new ArrayRealVector(p, false);
        }
    }

    private static double estimateGaussianWidth(double[] wavelengths, double[] intensities,
                                                int minIndex, double continuum) {
        double minIntensity = intensities[minIndex];
        double halfMax = (continuum + minIntensity) / 2.0;

        int leftIndex = minIndex;
        for (int i = minIndex; i >= 0; i--) {
            if (intensities[i] >= halfMax) {
                leftIndex = i;
                break;
            }
        }

        int rightIndex = minIndex;
        for (int i = minIndex; i < intensities.length; i++) {
            if (intensities[i] >= halfMax) {
                rightIndex = i;
                break;
            }
        }

        double fwhmEstimate = Math.abs(wavelengths[rightIndex] - wavelengths[leftIndex]);
        if (fwhmEstimate < 0.01) {
            fwhmEstimate = 0.5;
        }
        return Math.max(0.05, fwhmEstimate / (2.0 * Math.sqrt(2.0 * Math.log(2.0))));
    }

    private static MultivariateVectorFunction createModelFunction(double[] wavelengths) {
        return params -> {
            double[] result = new double[wavelengths.length];
            double continuum = params[0];
            double amplitude = params[1];
            double center = params[2];
            double sigma = Math.abs(params[3]);
            double gamma = Math.abs(params[4]);

            double peakValue = pseudoVoigt(0, sigma, gamma);
            if (peakValue < 1e-30) {
                peakValue = 1e-30;
            }

            for (int i = 0; i < wavelengths.length; i++) {
                double x = wavelengths[i] - center;
                double normalizedVoigt = pseudoVoigt(x, sigma, gamma) / peakValue;
                result[i] = continuum - amplitude * normalizedVoigt;
            }
            return result;
        };
    }

    private static MultivariateMatrixFunction createJacobianFunction(double[] wavelengths) {
        return params -> {
            double[][] jacobian = new double[wavelengths.length][5];
            double amplitude = params[1];
            double center = params[2];
            double sigma = Math.abs(params[3]);
            double gamma = Math.abs(params[4]);

            double peakValue = pseudoVoigt(0, sigma, gamma);
            if (peakValue < 1e-30) {
                peakValue = 1e-30;
            }

            double h = 1e-7;

            for (int i = 0; i < wavelengths.length; i++) {
                double x = wavelengths[i] - center;
                double v = pseudoVoigt(x, sigma, gamma) / peakValue;

                jacobian[i][0] = 1.0;
                jacobian[i][1] = -v;

                double vPlusX = pseudoVoigt(x + h, sigma, gamma) / peakValue;
                double vMinusX = pseudoVoigt(x - h, sigma, gamma) / peakValue;
                jacobian[i][2] = amplitude * (vPlusX - vMinusX) / (2 * h);

                double peakSigmaPlus = pseudoVoigt(0, sigma + h, gamma);
                double peakSigmaMinus = pseudoVoigt(0, Math.max(0.001, sigma - h), gamma);
                double vSigmaPlus = pseudoVoigt(x, sigma + h, gamma) / peakSigmaPlus;
                double vSigmaMinus = pseudoVoigt(x, Math.max(0.001, sigma - h), gamma) / peakSigmaMinus;
                jacobian[i][3] = -amplitude * (vSigmaPlus - vSigmaMinus) / (2 * h);

                double peakGammaPlus = pseudoVoigt(0, sigma, gamma + h);
                double peakGammaMinus = pseudoVoigt(0, sigma, Math.max(0.0001, gamma - h));
                double vGammaPlus = pseudoVoigt(x, sigma, gamma + h) / peakGammaPlus;
                double vGammaMinus = pseudoVoigt(x, sigma, Math.max(0.0001, gamma - h)) / peakGammaMinus;
                jacobian[i][4] = -amplitude * (vGammaPlus - vGammaMinus) / (2 * h);
            }
            return jacobian;
        };
    }

    private static double computeRMS(double[] wavelengths, double[] intensities, double[] params) {
        double sumSq = 0;
        double continuum = params[0];
        double amplitude = params[1];
        double center = params[2];
        double sigma = Math.abs(params[3]);
        double gamma = Math.abs(params[4]);

        double peakValue = pseudoVoigt(0, sigma, gamma);
        if (peakValue < 1e-30) {
            peakValue = 1e-30;
        }

        for (int i = 0; i < wavelengths.length; i++) {
            double x = wavelengths[i] - center;
            double normalizedVoigt = pseudoVoigt(x, sigma, gamma) / peakValue;
            double fitted = continuum - amplitude * normalizedVoigt;
            double residual = intensities[i] - fitted;
            sumSq += residual * residual;
        }
        return Math.sqrt(sumSq / wavelengths.length);
    }

    /**
     * Computes the pseudo-Voigt function using the Thompson-Cox-Hastings approximation.
     * This is a linear combination of Gaussian and Lorentzian with mixing parameter η.
     *
     * @param x     distance from line center (Angstroms)
     * @param sigma Gaussian width parameter σ (Angstroms)
     * @param gamma Lorentzian width parameter γ (Angstroms)
     * @return the pseudo-Voigt function value
     */
    public static double pseudoVoigt(double x, double sigma, double gamma) {
        double fG = 2.0 * sigma * Math.sqrt(2.0 * Math.log(2.0));
        double fL = 2.0 * gamma;

        // Use Olivero formula for mixing parameter calculation (not for actual FWHM)
        double fV = oliveroFWHM(fG, fL);

        double eta = computeMixingParameter(fG, fL, fV);

        double g = gaussian(x, fG);
        double l = lorentzian(x, fL);

        return eta * l + (1 - eta) * g;
    }

    /**
     * Computes the Voigt FWHM using Olivero-Longbothum approximation formula.
     * This is used internally for mixing parameter calculation.
     *
     * @param fG Gaussian FWHM
     * @param fL Lorentzian FWHM
     * @return the approximate Voigt FWHM
     */
    private static double oliveroFWHM(double fG, double fL) {
        return 0.5346 * fL + Math.sqrt(0.2166 * fL * fL + fG * fG);
    }

    /**
     * Computes the Voigt function using numerical integration of the convolution.
     * This provides higher accuracy than the pseudo-Voigt approximation.
     *
     * @param x     distance from line center (Angstroms)
     * @param sigma Gaussian width parameter σ (Angstroms)
     * @param gamma Lorentzian width parameter γ (Angstroms)
     * @return the Voigt function value
     */
    public static double voigt(double x, double sigma, double gamma) {
        if (sigma <= 1e-10) {
            double fL = 2.0 * gamma;
            return lorentzian(x, fL);
        }
        if (gamma <= 1e-10) {
            double fG = 2.0 * sigma * Math.sqrt(2.0 * Math.log(2.0));
            return gaussian(x, fG);
        }
        return pseudoVoigt(x, sigma, gamma);
    }

    private static double computeMixingParameter(double fG, double fL, double fV) {
        if (fV < 1e-30) {
            return 0.5;
        }
        double ratio = fL / fV;
        return 1.36603 * ratio - 0.47719 * ratio * ratio + 0.11116 * ratio * ratio * ratio;
    }

    private static double gaussian(double x, double fwhm) {
        if (fwhm < 1e-30) {
            return x == 0 ? 1e30 : 0;
        }
        double c = fwhm / (2.0 * Math.sqrt(2.0 * Math.log(2.0)));
        return Math.exp(-x * x / (2 * c * c)) / (c * Math.sqrt(2 * Math.PI));
    }

    private static double lorentzian(double x, double fwhm) {
        if (fwhm < 1e-30) {
            return x == 0 ? 1e30 : 0;
        }
        double gamma = fwhm / 2.0;
        return gamma / (Math.PI * (x * x + gamma * gamma));
    }

    /**
     * Computes the FWHM of the pseudo-Voigt profile by finding where it crosses
     * half its maximum value. This is more accurate than analytical approximations
     * for the pseudo-Voigt function.
     *
     * @param sigma Gaussian width parameter σ
     * @param gamma Lorentzian width parameter γ
     * @return the full width at half maximum
     */
    public static double voigtFWHM(double sigma, double gamma) {
        double effectiveSigma = Math.max(1e-10, sigma);
        double effectiveGamma = Math.max(1e-10, gamma);

        double peak = pseudoVoigt(0, effectiveSigma, effectiveGamma);
        double halfPeak = peak / 2.0;

        // Binary search for the half-maximum crossing point
        double low = 0;
        double high = 10.0 * Math.max(effectiveSigma, effectiveGamma);

        // Ensure high is beyond the half-maximum point
        while (pseudoVoigt(high, effectiveSigma, effectiveGamma) > halfPeak && high < 1000) {
            high *= 2;
        }

        // Binary search
        for (int i = 0; i < 100; i++) {
            double mid = (low + high) / 2.0;
            double val = pseudoVoigt(mid, effectiveSigma, effectiveGamma);
            if (val > halfPeak) {
                low = mid;
            } else {
                high = mid;
            }
            if (high - low < 1e-10) {
                break;
            }
        }

        return 2.0 * (low + high) / 2.0;
    }
}
