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

import me.champeau.a4j.jsolex.processing.spectrum.ReferenceIntensities;
import me.champeau.a4j.jsolex.processing.spectrum.TelluricTransmission;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

/**
 * Calibrates an observed mean profile against the BASS2000 atlas. The observed profile is
 * modeled as {@code a (B T^e * G)(λ0 + k s D + δ) + b}, where {@code B} is the atlas,
 * {@code T} the transmission of the atmosphere, {@code e} a factor which accounts for the
 * humidity and the air mass, {@code G} a gaussian instrumental profile, {@code s} the pixel
 * shift, {@code D} the nominal dispersion, {@code k} a dispersion correction, {@code δ} a
 * wavelength offset, {@code a} the continuum level and {@code b} an additive stray light
 * level. A negative dispersion correction means that the pixel shifts increase towards
 * shorter wavelengths.
 */
public final class InstrumentProfileEstimator {
    private static final double ATLAS_STEP = 0.01;
    private static final double ATLAS_CONTINUUM = 10000;
    private static final double MARGIN = 2;
    private static final double MIN_DISPERSION_SCALE = 0.5;
    private static final double MAX_DISPERSION_SCALE = 2.0;
    private static final double MAX_TELLURIC_EXPONENT = 4;
    private static final double TELLURIC_EXPONENT_STEP = 0.1;

    private InstrumentProfileEstimator() {
    }

    /**
     * The calibration of an observation.
     *
     * @param continuum the continuum level, in the units of the observation
     * @param strayLight the additive stray light, as a fraction of the continuum
     * @param instrumentSigma the standard deviation of the gaussian instrumental profile, in angstroms
     * @param offset the wavelength offset, in angstroms
     * @param dispersionScale the correction applied to the nominal dispersion
     * @param telluricExponent the exponent applied to the transmission of the atmosphere
     * @param rmsResidual the root mean square of the residuals, as a fraction of the continuum
     */
    public record Calibration(
            double continuum,
            double strayLight,
            double instrumentSigma,
            double offset,
            double dispersionScale,
            double telluricExponent,
            double rmsResidual
    ) {
        public double instrumentFwhm() {
            return instrumentSigma * 2 * Math.sqrt(2 * Math.log(2));
        }

        /**
         * Returns the wavelength of a pixel shift, as an offset in angstroms from the line center.
         */
        public double offsetOf(double pixelShift, double dispersion) {
            return pixelShift * dispersion * dispersionScale + offset;
        }

        /**
         * Returns the transmission of the atmosphere at the given offset from the line center.
         */
        public double transmissionAt(double lambda0, double offset) {
            return InstrumentProfileEstimator.transmissionAt(lambda0 + offset, telluricExponent);
        }
    }

    /**
     * Calibrates the observed profile.
     *
     * @param pixelShifts the pixel shifts of the samples
     * @param observed the observed intensity of each sample
     * @param dispersion the nominal dispersion, in angstroms per pixel
     * @param lambda0 the wavelength of the line, in angstroms
     * @return the calibration
     */
    public static Calibration calibrate(double[] pixelShifts, double[] observed, double dispersion, double lambda0) {
        var extent = 0d;
        for (var shift : pixelShifts) {
            extent = Math.max(extent, Math.abs(shift * dispersion));
        }
        var grid = AtlasGrid.around(lambda0, extent * MAX_DISPERSION_SCALE + MARGIN);
        var search = new Search(pixelShifts, observed, dispersion, lambda0, grid);
        search.scan(1, 0.02, 0.40, 0.01, MIN_DISPERSION_SCALE, MAX_DISPERSION_SCALE, 0.01, -1.0, 1.0, 0.02);
        search.scan(1, 0.02, 0.40, 0.01, -MAX_DISPERSION_SCALE, -MIN_DISPERSION_SCALE, 0.01, -1.0, 1.0, 0.02);
        for (var exponent = 0d; exponent <= MAX_TELLURIC_EXPONENT + 1e-9; exponent += TELLURIC_EXPONENT_STEP) {
            search.scan(exponent, search.bestSigma, search.bestSigma, 1, search.bestScale, search.bestScale, 1, search.bestOffset, search.bestOffset, 1);
        }
        var sigma = search.bestSigma;
        var scale = search.bestScale;
        var offset = search.bestOffset;
        search.scan(search.bestExponent, Math.max(0.005, sigma - 0.012), sigma + 0.012, 0.002, scale - 0.012, scale + 0.012, 0.002, offset - 0.024, offset + 0.024, 0.004);
        var rms = Math.sqrt(search.bestRss / pixelShifts.length) / search.bestA;
        return new Calibration(search.bestA, search.bestB / search.bestA, search.bestSigma, search.bestOffset, search.bestScale, search.bestExponent, rms);
    }

    /**
     * Returns the atlas, convolved by a gaussian instrumental profile, at the given wavelengths.
     *
     * @param offsets the wavelengths, as offsets in angstroms from the line center
     * @param sigma the standard deviation of the instrumental profile, in angstroms
     * @param telluricExponent the exponent applied to the transmission of the atmosphere
     * @param lambda0 the wavelength of the line center, in angstroms
     * @return the convolved atlas, in units of the continuum
     */
    public static double[] convolvedAtlas(double[] offsets, double sigma, double telluricExponent, double lambda0) {
        var extent = 0d;
        for (var offset : offsets) {
            extent = Math.max(extent, Math.abs(offset));
        }
        var grid = AtlasGrid.around(lambda0, extent + MARGIN);
        var convolved = Search.convolve(grid.attenuatedAtlas(telluricExponent), sigma / ATLAS_STEP);
        var result = new double[offsets.length];
        for (var i = 0; i < offsets.length; i++) {
            result[i] = Search.interpolate(convolved, (lambda0 + offsets[i] - grid.start()) / ATLAS_STEP);
        }
        return result;
    }

    /**
     * The atlas and the transmission of the atmosphere, sampled with a regular step around the line.
     */
    private record AtlasGrid(double start, double[] solar, double[] transmission) {
        static AtlasGrid around(double lambda0, double extent) {
            var count = (int) Math.ceil(2 * extent / ATLAS_STEP) + 1;
            var start = lambda0 - extent;
            var solar = new double[count];
            var transmission = new double[count];
            for (var i = 0; i < count; i++) {
                var wavelength = start + i * ATLAS_STEP;
                solar[i] = atlasAt(wavelength);
                transmission[i] = transmissionAt(wavelength, 1);
            }
            return new AtlasGrid(start, solar, transmission);
        }

        /**
         * Returns the atlas seen through the atmosphere, with the given exponent applied to the transmission.
         */
        double[] attenuatedAtlas(double telluricExponent) {
            var atlas = new double[solar.length];
            for (var i = 0; i < atlas.length; i++) {
                atlas[i] = solar[i] * Math.pow(transmission[i], telluricExponent);
            }
            return atlas;
        }
    }

    /**
     * Returns the atlas intensity at the given wavelength, in units of the continuum.
     */
    public static double atlasAt(double wavelength) {
        return ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wavelength)) / ATLAS_CONTINUUM;
    }

    /**
     * Returns the transmission of the atmosphere at the given wavelength.
     *
     * @param wavelength the wavelength, in angstroms
     * @param exponent the exponent which scales the depth of the telluric lines
     */
    public static double transmissionAt(double wavelength, double exponent) {
        if (exponent == 0) {
            return 1;
        }
        return Math.pow(TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(wavelength)), exponent);
    }

    private static final class Search {
        private final double[] pixelShifts;
        private final double[] observed;
        private final double dispersion;
        private final double lambda0;
        private final AtlasGrid grid;
        private final double[] model;
        private double bestRss = Double.MAX_VALUE;
        private double bestSigma;
        private double bestScale = 1;
        private double bestOffset;
        private double bestExponent = 1;
        private double bestA = 1;
        private double bestB;

        private Search(double[] pixelShifts, double[] observed, double dispersion, double lambda0, AtlasGrid grid) {
            this.pixelShifts = pixelShifts;
            this.observed = observed;
            this.dispersion = dispersion;
            this.lambda0 = lambda0;
            this.grid = grid;
            this.model = new double[pixelShifts.length];
        }

        private void scan(double exponent,
                          double sigmaMin, double sigmaMax, double sigmaStep,
                          double scaleMin, double scaleMax, double scaleStep,
                          double offsetMin, double offsetMax, double offsetStep) {
            var atlas = grid.attenuatedAtlas(exponent);
            for (var sigma = sigmaMin; sigma <= sigmaMax + 1e-9; sigma += sigmaStep) {
                var convolved = convolve(atlas, sigma / ATLAS_STEP);
                for (var scale = scaleMin; scale <= scaleMax + 1e-9; scale += scaleStep) {
                    for (var offset = offsetMin; offset <= offsetMax + 1e-9; offset += offsetStep) {
                        for (var i = 0; i < pixelShifts.length; i++) {
                            model[i] = interpolate(convolved, (lambda0 + pixelShifts[i] * dispersion * scale + offset - grid.start()) / ATLAS_STEP);
                        }
                        fitLinear(exponent, sigma, scale, offset);
                    }
                }
            }
        }

        private void fitLinear(double exponent, double sigma, double scale, double offset) {
            var n = model.length;
            var sx = 0d;
            var sy = 0d;
            var sxx = 0d;
            var sxy = 0d;
            for (var i = 0; i < n; i++) {
                sx += model[i];
                sy += observed[i];
                sxx += model[i] * model[i];
                sxy += model[i] * observed[i];
            }
            var determinant = n * sxx - sx * sx;
            if (determinant <= 0) {
                return;
            }
            var a = (n * sxy - sx * sy) / determinant;
            var b = (sy - a * sx) / n;
            if (b < 0) {
                b = 0;
                a = sxy / sxx;
            }
            if (a <= 0) {
                return;
            }
            var rss = 0d;
            for (var i = 0; i < n; i++) {
                var r = observed[i] - a * model[i] - b;
                rss += r * r;
            }
            if (rss < bestRss) {
                bestRss = rss;
                bestSigma = sigma;
                bestScale = scale;
                bestOffset = offset;
                bestExponent = exponent;
                bestA = a;
                bestB = b;
            }
        }

        private static double interpolate(double[] values, double index) {
            if (index <= 0) {
                return values[0];
            }
            if (index >= values.length - 1) {
                return values[values.length - 1];
            }
            var i = (int) index;
            var t = index - i;
            return values[i] + t * (values[i + 1] - values[i]);
        }

        private static double[] convolve(double[] values, double sigma) {
            var radius = (int) Math.ceil(4 * sigma);
            var kernel = new double[2 * radius + 1];
            var sum = 0d;
            for (var j = -radius; j <= radius; j++) {
                var weight = Math.exp(-0.5 * (j / sigma) * (j / sigma));
                kernel[j + radius] = weight;
                sum += weight;
            }
            var result = new double[values.length];
            var last = values.length - 1;
            for (var i = 0; i < values.length; i++) {
                var value = 0d;
                for (var j = -radius; j <= radius; j++) {
                    value += kernel[j + radius] * values[Math.clamp(i + j, 0, last)];
                }
                result[i] = value / sum;
            }
            return result;
        }
    }
}
