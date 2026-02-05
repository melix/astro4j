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

import java.util.ArrayList;
import java.util.List;

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
        // Use sub-pixel interpolation for more accurate center detection, especially
        // important when comparing profiles with small Doppler shifts
        double detectedLineCenter;
        if (referenceData != null && !referenceData.isEmpty()) {
            detectedLineCenter = findMinimumWavelengthSubPixel(referenceData);
        } else {
            detectedLineCenter = findMinimumWavelengthSubPixel(smoothedProfile);
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
     * Computes statistics specifically for Doppler measurements.
     * This method uses a narrower fitting window than the standard computeStatistics() to
     * focus on the line core for more accurate Doppler shift measurements.
     *
     * @param profile                  the list of data points (wavelength, intensity)
     * @param windowHalfWidthAngstroms half-width of the fitting window around the line center
     * @return the computed line statistics with Voigt fit centered on the line core
     */
    public static LineStatistics computeStatisticsForDoppler(List<SpectrumAnalyzer.DataPoint> profile,
                                                               double windowHalfWidthAngstroms) {
        if (profile == null || profile.size() < 3) {
            return LineStatistics.empty();
        }

        // Find initial line center estimate from RAW profile (not smoothed) for accuracy
        var initialCenterEstimate = findMinimumWavelengthSubPixel(profile);

        // Estimate continuum from the maximum intensity in the profile
        var continuum = profile.stream()
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .max()
            .orElse(0);

        // Find minimum intensity within the narrow window
        var minIntensity = profile.stream()
            .filter(p -> Math.abs(p.wavelen().angstroms() - initialCenterEstimate) < windowHalfWidthAngstroms)
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .min()
            .orElseGet(() -> findMinimumPoint(profile).intensity());

        var lineDepth = continuum > 0 ? (continuum - minIntensity) / continuum : 0;
        var halfMaxIntensity = (continuum + minIntensity) / 2.0;

        var fittingProfile = profile.stream()
            .filter(p -> Math.abs(p.wavelen().angstroms() - initialCenterEstimate) <= windowHalfWidthAngstroms)
            .toList();

        var voigtFit = VoigtFitter.fit(fittingProfile, continuum, initialCenterEstimate);

        double measuredFwhm = 0;
        Double blueHalfMax = null;
        Double redHalfMax = null;
        // Use Voigt-fitted center if available, otherwise fall back to initial estimate
        double finalLineCenter = initialCenterEstimate;

        if (voigtFit.converged()) {
            measuredFwhm = voigtFit.fwhm();
            var halfFwhm = measuredFwhm / 2.0;
            finalLineCenter = voigtFit.center();
            blueHalfMax = finalLineCenter - halfFwhm;
            redHalfMax = finalLineCenter + halfFwhm;
        }

        return new LineStatistics(
            continuum,
            finalLineCenter,
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
     * Computes the center of gravity (first moment) of an absorption line profile.
     * The profile is inverted (continuum - intensity) so that the absorption dip
     * becomes a peak, then the weighted mean wavelength is computed.
     * Only points within the given window around the initial center estimate are used.
     *
     * @param profile the spectral line profile
     * @param windowHalfWidthAngstroms half-width of the window around the line center
     * @return the center-of-gravity wavelength in Angstroms, or NaN if computation fails
     */
    public static double computeCenterOfGravity(List<SpectrumAnalyzer.DataPoint> profile, double windowHalfWidthAngstroms) {
        if (profile == null || profile.size() < 3) {
            return Double.NaN;
        }
        var centerEstimate = findMinimumWavelengthSubPixel(profile);
        var continuum = profile.stream()
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .max()
            .orElse(0);
        if (continuum <= 0) {
            return Double.NaN;
        }
        double sumWeight = 0;
        double sumWeightedWl = 0;
        for (var point : profile) {
            var wl = point.wavelen().angstroms();
            if (Math.abs(wl - centerEstimate) > windowHalfWidthAngstroms) {
                continue;
            }
            var weight = continuum - point.intensity();
            if (weight > 0) {
                sumWeight += weight;
                sumWeightedWl += weight * wl;
            }
        }
        if (sumWeight <= 0) {
            return Double.NaN;
        }
        return sumWeightedWl / sumWeight;
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

    private static SpectrumAnalyzer.DataPoint findMinimumPoint(List<SpectrumAnalyzer.DataPoint> profile) {
        return profile.stream()
            .min((a, b) -> Double.compare(a.intensity(), b.intensity()))
            .orElse(profile.getFirst());
    }

    /**
     * Finds the wavelength of the minimum intensity with sub-pixel precision using parabolic interpolation.
     * This is more accurate than just taking the wavelength of the data point with minimum intensity.
     *
     * @param profile the spectral profile
     * @return the interpolated wavelength at the true minimum
     */
    private static double findMinimumWavelengthSubPixel(List<SpectrumAnalyzer.DataPoint> profile) {
        if (profile.size() < 3) {
            return findMinimumPoint(profile).wavelen().angstroms();
        }

        // Find the index of the minimum intensity point
        int minIndex = 0;
        double minIntensity = Double.MAX_VALUE;
        for (int i = 0; i < profile.size(); i++) {
            double intensity = profile.get(i).intensity();
            if (intensity < minIntensity) {
                minIntensity = intensity;
                minIndex = i;
            }
        }

        // If minimum is at the edge, can't interpolate
        if (minIndex == 0 || minIndex == profile.size() - 1) {
            return profile.get(minIndex).wavelen().angstroms();
        }

        // Parabolic interpolation using the three points around the minimum
        var p0 = profile.get(minIndex - 1);
        var p1 = profile.get(minIndex);
        var p2 = profile.get(minIndex + 1);

        double y0 = p0.intensity();
        double y1 = p1.intensity();
        double y2 = p2.intensity();

        double wl0 = p0.wavelen().angstroms();
        double wl1 = p1.wavelen().angstroms();
        double wl2 = p2.wavelen().angstroms();

        // Parabolic fit: the offset from wl1 to the true minimum
        // Using formula: offset = (y0 - y2) / (2 * (y0 - 2*y1 + y2))
        // where the offset is in units of the wavelength step
        double denominator = 2 * (y0 - 2 * y1 + y2);
        if (Math.abs(denominator) > 1e-10) {
            // The offset is a fraction of the wavelength step
            double wavelengthStep = (wl2 - wl0) / 2.0;  // Average step size
            double offset = (y0 - y2) / denominator;
            // Clamp offset to avoid extrapolation beyond neighboring points
            offset = Math.max(-1.0, Math.min(1.0, offset));
            return wl1 + offset * wavelengthStep;
        }

        return wl1;
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
