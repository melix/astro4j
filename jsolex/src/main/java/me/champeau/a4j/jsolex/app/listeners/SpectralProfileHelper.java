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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.List;
import java.util.Locale;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Helper class for spectral profile calculations and formatting.
 */
final class SpectralProfileHelper {

    private SpectralProfileHelper() {
        // utility class
    }

    /**
     * Computes the wavelength for a given pixel shift.
     *
     * @param pixelShift the pixel shift from the reference
     * @param lambda the reference wavelength
     * @param dispersion the spectral dispersion (may be null)
     * @return the computed wavelength
     */
    static Wavelen computeWavelength(double pixelShift, Wavelen lambda, Dispersion dispersion) {
        if (dispersion == null) {
            return lambda;
        }
        return lambda.plus(pixelShift, dispersion);
    }

    /**
     * Normalizes data points to a percentage scale (0-100).
     *
     * @param dataPoints the data points to normalize
     * @param maxIntensity optional maximum intensity for normalization (null to use max from data)
     * @return the normalized data points with the max intensity used
     */
    static NormalizedDataPoints normalizeDatapoints(List<SpectrumAnalyzer.DataPoint> dataPoints, Double maxIntensity) {
        var maxSeriesIntensity = dataPoints.stream().mapToDouble(SpectrumAnalyzer.DataPoint::intensity).max().orElse(0);
        var maxRef = maxIntensity != null ? maxIntensity : maxSeriesIntensity;
        return new NormalizedDataPoints(dataPoints.stream()
                .map(dataPoint -> new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 100 * dataPoint.intensity() / maxRef))
                .toList(),
                maxSeriesIntensity);
    }

    /**
     * Formats wavelength for display.
     *
     * @param pixelShift the pixel shift
     * @param wavelength the wavelength
     * @return formatted string showing wavelength in angstroms or pixel shift
     */
    static String formatWavelength(double pixelShift, Wavelen wavelength) {
        if (wavelength.angstroms() > 0) {
            return String.format(Locale.US, "%.2f", wavelength.angstroms());
        }
        return String.format(Locale.US, "%.2fpx", pixelShift);
    }

    /**
     * Formats the legend for the intensity chart.
     *
     * @param instrument the spectroheliograph instrument
     * @param lambda0 the reference wavelength
     * @param binning the binning value
     * @param pixelSize the pixel size
     * @return formatted legend string
     */
    static String formatLegend(SpectroHeliograph instrument, Wavelen lambda0, Integer binning, Double pixelSize) {
        if (binning != null && pixelSize != null && lambda0 != null) {
            double pixSize = pixelSize;
            if (lambda0.angstroms() > 0 && pixSize > 0) {
                var disp = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, pixelSize * binning).angstromsPerPixel();
                return String.format(message("intensity.legend"), pixelSize, binning, disp);
            }
        }
        return message("intensity");
    }

    /**
     * Container for normalized data points and the max intensity used for normalization.
     */
    record NormalizedDataPoints(List<SpectrumAnalyzer.DataPoint> dataPoints, double maxIntensity) {
    }
}
