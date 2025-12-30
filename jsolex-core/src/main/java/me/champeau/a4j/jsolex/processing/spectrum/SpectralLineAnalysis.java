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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Provides spectral line analysis algorithms including line statistics,
 * equivalent width, and center-to-limb variation.
 */
public final class SpectralLineAnalysis {

    private static final double MIN_FIT_WINDOW_HALF_WIDTH_ANGSTROMS = 2.5;
    private static final double WING_THRESHOLD_FRACTION = 0.85;

    private SpectralLineAnalysis() {
    }

    /**
     * Statistics computed from a spectral line profile.
     *
     * @param continuum             the continuum intensity level
     * @param lineCenterWavelength  the wavelength at line minimum (Angstroms)
     * @param lineMinIntensity      the intensity at line minimum
     * @param lineDepth             the normalized depth (0-1)
     * @param fwhm                  the full width at half maximum (Angstroms), 0 if not measurable
     * @param halfMaxIntensity      the intensity at half maximum
     * @param blueHalfMaxWavelength the wavelength at blue half-maximum crossing (null if not found)
     * @param redHalfMaxWavelength  the wavelength at red half-maximum crossing (null if not found)
     * @param voigtFit              the Voigt profile fit parameters (null if fitting failed)
     */
    public record LineStatistics(
        double continuum,
        double lineCenterWavelength,
        double lineMinIntensity,
        double lineDepth,
        double fwhm,
        double halfMaxIntensity,
        Double blueHalfMaxWavelength,
        Double redHalfMaxWavelength,
        VoigtFitter.VoigtParameters voigtFit
    ) {
        public static LineStatistics empty() {
            return new LineStatistics(0, 0, 0, 0, 0, 0, null, null, null);
        }

        public boolean hasFWHMData() {
            return fwhm > 0 && blueHalfMaxWavelength != null && redHalfMaxWavelength != null;
        }

    }

    /**
     * A data point for center-to-limb variation.
     *
     * @param mu        cos(theta) where theta is the heliocentric angle (1 = disk center, 0 = limb)
     * @param intensity the average intensity at this mu value
     */
    public record CLVDataPoint(double mu, double intensity) {
    }

    /**
     * Result of limb darkening fit.
     *
     * @param coefficient the limb darkening coefficient u
     * @param centerIntensity the intensity at disk center I(1)
     */
    public record LimbDarkeningFit(double coefficient, double centerIntensity) {
    }

    /**
     * Computes comprehensive statistics for a spectral line profile.
     *
     * @param profile the list of data points (wavelength, intensity)
     * @return the computed line statistics
     */
    public static LineStatistics computeStatistics(List<SpectrumAnalyzer.DataPoint> profile) {
        return computeStatistics(profile, null, null);
    }

    /**
     * Computes comprehensive statistics for a spectral line profile.
     * When reference data is provided, uses it for line center estimation.
     *
     * @param profile       the list of data points (wavelength, intensity)
     * @param referenceData optional reference spectrum (e.g., BASS2000) for line center estimation
     * @return the computed line statistics
     */
    public static LineStatistics computeStatistics(List<SpectrumAnalyzer.DataPoint> profile,
                                                   List<SpectrumAnalyzer.DataPoint> referenceData) {
        return computeStatistics(profile, referenceData, null);
    }

    /**
     * Computes comprehensive statistics for a spectral line profile.
     * When reference data is provided, uses it for line center estimation.
     * When a real line center is provided, it is used as the reported line center wavelength.
     *
     * @param profile            the list of data points (wavelength, intensity)
     * @param referenceData      optional reference spectrum (e.g., BASS2000) for line center estimation
     * @param realLineCenterNano the real line center wavelength in nanometers (e.g., from selected spectral ray),
     *                           or null to use the detected line center
     * @return the computed line statistics
     */
    public static LineStatistics computeStatistics(List<SpectrumAnalyzer.DataPoint> profile,
                                                   List<SpectrumAnalyzer.DataPoint> referenceData,
                                                   Double realLineCenterNano) {
        if (profile == null || profile.size() < 3) {
            return LineStatistics.empty();
        }

        // Smooth the profile to reduce noise effects
        var smoothedProfile = smoothProfile(profile, 5);

        // Find detected line center for internal calculations (window filtering, etc.)
        double detectedLineCenter;
        if (referenceData != null && !referenceData.isEmpty()) {
            var refMinPoint = findMinimumPoint(referenceData);
            detectedLineCenter = refMinPoint.wavelen().angstroms();
        } else {
            var minPoint = findMinimumPoint(smoothedProfile);
            detectedLineCenter = minPoint.wavelen().angstroms();
        }

        // Use real line center (if provided) for reporting, otherwise use detected
        double reportedLineCenter = realLineCenterNano != null
            ? realLineCenterNano * 10.0  // convert nm to Angstroms
            : detectedLineCenter;

        // Estimate continuum from the maximum intensity in the smoothed profile.
        // This works for both narrow (Ha) and broad (Ca K) lines since the profile edges
        // should be at or near continuum level.
        var continuum = smoothedProfile.stream()
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .max()
            .orElse(0);

        // Find minimum intensity from the raw profile within a window around detected line center.
        // Use raw profile (not smoothed) to get the actual observed minimum at the line core.
        // Use ±2 Å window to account for wavelength calibration uncertainties.
        var minIntensity = profile.stream()
            .filter(p -> Math.abs(p.wavelen().angstroms() - detectedLineCenter) < 2.0)
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .min()
            .orElseGet(() -> findMinimumPoint(profile).intensity());

        var lineDepth = continuum > 0 ? (continuum - minIntensity) / continuum : 0;

        // For absorption lines, half-depth is midpoint between continuum and minimum
        var halfMaxIntensity = (continuum + minIntensity) / 2.0;

        // Determine adaptive fitting window based on profile shape

        // Filter profile to fitting window around detected line center to avoid fitting secondary features
        var finalFitWindowHalfWidth = computeAdaptiveWindowHalfWidth(
            smoothedProfile, detectedLineCenter, continuum, minIntensity);
        var fittingProfile = profile.stream()
            .filter(p -> Math.abs(p.wavelen().angstroms() - detectedLineCenter) <= finalFitWindowHalfWidth)
            .toList();

        var voigtFit = VoigtFitter.fit(fittingProfile, continuum, detectedLineCenter);

        double measuredFwhm = 0;
        Double blueHalfMax = null;
        Double redHalfMax = null;

        if (voigtFit.converged()) {
            measuredFwhm = voigtFit.fwhm();
            var halfFwhm = measuredFwhm / 2.0;
            blueHalfMax = voigtFit.center() - halfFwhm;
            redHalfMax = voigtFit.center() + halfFwhm;
        }

        return new LineStatistics(
            continuum,
            reportedLineCenter,
            minIntensity,
            lineDepth,
            measuredFwhm,
            halfMaxIntensity,
            blueHalfMax,
            redHalfMax,
            voigtFit.converged() ? voigtFit : null
        );
    }

    /**
     * Smooths a profile using a moving average.
     *
     * @param profile    the profile to smooth
     * @param windowSize the number of points in the moving average window (should be odd)
     * @return the smoothed profile
     */
    private static List<SpectrumAnalyzer.DataPoint> smoothProfile(List<SpectrumAnalyzer.DataPoint> profile, int windowSize) {
        if (profile.size() < windowSize) {
            return profile;
        }

        var halfWindow = windowSize / 2;
        var smoothed = new ArrayList<SpectrumAnalyzer.DataPoint>(profile.size());

        for (var i = 0; i < profile.size(); i++) {
            var start = Math.max(0, i - halfWindow);
            var end = Math.min(profile.size(), i + halfWindow + 1);

            double sum = 0;
            var count = 0;
            for (var j = start; j < end; j++) {
                sum += profile.get(j).intensity();
                count++;
            }

            var original = profile.get(i);
            smoothed.add(new SpectrumAnalyzer.DataPoint(original.wavelen(), original.pixelShift(), sum / count));
        }

        return smoothed;
    }

    /**
     * Computes center-to-limb variation from a solar disk image.
     *
     * @param image   the solar disk image
     * @param ellipse the fitted ellipse describing the disk
     * @param numBins the number of mu bins
     * @return the list of CLV data points
     */
    public static List<CLVDataPoint> computeCLV(ImageWrapper32 image, Ellipse ellipse, int numBins) {
        if (image == null || ellipse == null || numBins < 2) {
            return List.of();
        }

        var bins = new double[numBins];
        var counts = new long[numBins];

        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var semiA = ellipse.semiAxis().a();
        var semiB = ellipse.semiAxis().b();

        var data = image.data();
        var height = image.height();
        var width = image.width();

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var dx = (x - cx) / semiA;
                var dy = (y - cy) / semiB;
                var rhoSquared = dx * dx + dy * dy;

                if (rhoSquared < 1.0) {
                    var mu = Math.sqrt(1 - rhoSquared);
                    var bin = Math.min((int) (mu * numBins), numBins - 1);
                    bins[bin] += data[y][x];
                    counts[bin]++;
                }
            }
        }

        return IntStream.range(0, numBins)
            .filter(i -> counts[i] > 0)
            .mapToObj(i -> new CLVDataPoint(
                (i + 0.5) / numBins,
                bins[i] / counts[i]
            ))
            .toList();
    }

    private static SpectrumAnalyzer.DataPoint findMinimumPoint(List<SpectrumAnalyzer.DataPoint> profile) {
        return profile.stream()
            .min((a, b) -> Double.compare(a.intensity(), b.intensity()))
            .orElse(profile.getFirst());
    }

    private static double computeAdaptiveWindowHalfWidth(
        List<SpectrumAnalyzer.DataPoint> profile,
        double lineCenterWavelength,
        double continuum,
        double minIntensity
    ) {
        // Find where the line wings approach the continuum level.
        // We look for the point where intensity reaches WING_THRESHOLD_FRACTION of continuum
        // on both sides of the line center, then add some margin.
        var wingThreshold = minIntensity + WING_THRESHOLD_FRACTION * (continuum - minIntensity);

        Double blueWingEdge = null;
        Double redWingEdge = null;

        // Search for blue wing edge (going from center toward blue)
        for (var i = profile.size() - 1; i >= 0; i--) {
            var p = profile.get(i);
            var wl = p.wavelen().angstroms();
            if (wl < lineCenterWavelength && p.intensity() >= wingThreshold) {
                blueWingEdge = wl;
                break;
            }
        }

        // Search for red wing edge (going from center toward red)
        for (var p : profile) {
            var wl = p.wavelen().angstroms();
            if (wl > lineCenterWavelength && p.intensity() >= wingThreshold) {
                redWingEdge = wl;
                break;
            }
        }

        // Calculate window half-width based on detected edges
        var blueHalfWidth = blueWingEdge != null
            ? lineCenterWavelength - blueWingEdge
            : MIN_FIT_WINDOW_HALF_WIDTH_ANGSTROMS;
        var redHalfWidth = redWingEdge != null
            ? redWingEdge - lineCenterWavelength
            : MIN_FIT_WINDOW_HALF_WIDTH_ANGSTROMS;

        // Use the larger of the two half-widths, with 20% margin, but at least the minimum
        var estimatedHalfWidth = Math.max(blueHalfWidth, redHalfWidth) * 1.2;
        return Math.max(MIN_FIT_WINDOW_HALF_WIDTH_ANGSTROMS, estimatedHalfWidth);
    }
}
