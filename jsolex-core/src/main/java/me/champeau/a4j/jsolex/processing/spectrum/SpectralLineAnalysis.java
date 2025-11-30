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

import java.util.List;
import java.util.stream.IntStream;

/**
 * Provides spectral line analysis algorithms including line statistics,
 * equivalent width, and center-to-limb variation.
 */
public final class SpectralLineAnalysis {

    private static final double FIT_WINDOW_HALF_WIDTH_ANGSTROMS = 2.5;

    private SpectralLineAnalysis() {
    }

    /**
     * Statistics computed from a spectral line profile.
     *
     * @param continuum            the continuum intensity level
     * @param lineCenterWavelength the wavelength at line minimum (Angstroms)
     * @param lineMinIntensity     the intensity at line minimum
     * @param lineDepth            the normalized depth (0-1)
     * @param fwhm                 the full width at half maximum (Angstroms), 0 if not measurable
     * @param halfMaxIntensity     the intensity at half maximum
     * @param blueHalfMaxWavelength the wavelength at blue half-maximum crossing (null if not found)
     * @param redHalfMaxWavelength  the wavelength at red half-maximum crossing (null if not found)
     * @param asymmetryIndex       the asymmetry index at half-maximum
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
        double asymmetryIndex
    ) {
        public static LineStatistics empty() {
            return new LineStatistics(0, 0, 0, 0, 0, 0, null, null, 1.0);
        }

        public boolean hasFWHMData() {
            return blueHalfMaxWavelength != null && redHalfMaxWavelength != null;
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
        return computeStatistics(profile, null);
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
        if (profile == null || profile.size() < 3) {
            return LineStatistics.empty();
        }

        // Smooth the profile to reduce noise effects
        var smoothedProfile = smoothProfile(profile, 5);

        // Find line center: prefer reference (BASS2000) for accuracy, else use smoothed profile
        double lineCenterWavelength;
        if (referenceData != null && !referenceData.isEmpty()) {
            var refMinPoint = findMinimumPoint(referenceData);
            lineCenterWavelength = refMinPoint.wavelen().angstroms();
        } else {
            var minPoint = findMinimumPoint(smoothedProfile);
            lineCenterWavelength = minPoint.wavelen().angstroms();
        }

        // Estimate continuum from the maximum intensity in the smoothed profile.
        // This works for both narrow (Ha) and broad (Ca K) lines since the profile edges
        // should be at or near continuum level.
        double continuum = smoothedProfile.stream()
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .max()
            .orElse(0);

        // Find minimum intensity from the raw profile within a window around line center.
        // Use raw profile (not smoothed) to get the actual observed minimum at the line core.
        // Use ±2 Å window to account for wavelength calibration uncertainties.
        double minIntensity = profile.stream()
            .filter(p -> Math.abs(p.wavelen().angstroms() - lineCenterWavelength) < 2.0)
            .mapToDouble(SpectrumAnalyzer.DataPoint::intensity)
            .min()
            .orElseGet(() -> findMinimumPoint(profile).intensity());

        double lineDepth = continuum > 0 ? (continuum - minIntensity) / continuum : 0;

        // For absorption lines, half-depth is midpoint between continuum and minimum
        double halfMaxIntensity = (continuum + minIntensity) / 2.0;
        // Use smoothed profile for crossings to avoid noise-induced false crossings
        Double blueHalfMax = findCrossing(smoothedProfile, halfMaxIntensity, true);
        Double redHalfMax = findCrossing(smoothedProfile, halfMaxIntensity, false);

        double measuredFwhm = 0;
        double asymmetryIndex = 1.0;
        // FWHM is only measured when both crossings are found
        if (blueHalfMax != null && redHalfMax != null) {
            measuredFwhm = redHalfMax - blueHalfMax;
            double blueWidth = lineCenterWavelength - blueHalfMax;
            double redWidth = redHalfMax - lineCenterWavelength;
            if (blueWidth > 0) {
                asymmetryIndex = redWidth / blueWidth;
            }
        }

        return new LineStatistics(
            continuum,
            lineCenterWavelength,
            minIntensity,
            lineDepth,
            measuredFwhm,
            halfMaxIntensity,
            blueHalfMax,
            redHalfMax,
            asymmetryIndex
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

        int halfWindow = windowSize / 2;
        var smoothed = new java.util.ArrayList<SpectrumAnalyzer.DataPoint>(profile.size());

        for (int i = 0; i < profile.size(); i++) {
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(profile.size(), i + halfWindow + 1);

            double sum = 0;
            int count = 0;
            for (int j = start; j < end; j++) {
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

        double cx = ellipse.center().a();
        double cy = ellipse.center().b();
        double semiA = ellipse.semiAxis().a();
        double semiB = ellipse.semiAxis().b();

        var data = image.data();
        int height = image.height();
        int width = image.width();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = (x - cx) / semiA;
                double dy = (y - cy) / semiB;
                double rhoSquared = dx * dx + dy * dy;

                if (rhoSquared < 1.0) {
                    double mu = Math.sqrt(1 - rhoSquared);
                    int bin = Math.min((int) (mu * numBins), numBins - 1);
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

    private static Double findCrossing(List<SpectrumAnalyzer.DataPoint> profile, double targetIntensity, boolean blueWing) {
        var minPoint = findMinimumPoint(profile);
        int minIndex = profile.indexOf(minPoint);
        double minWavelength = minPoint.wavelen().angstroms();

        // Limit search to within ±2.5 Å of the line center to avoid finding crossings
        // in adjacent spectral features (e.g., Ca H near Ca K)
        double searchLimit = FIT_WINDOW_HALF_WIDTH_ANGSTROMS;

        // Find where the profile crosses the target intensity while moving away from
        // the line minimum. Only consider upward crossings (intensity increasing).
        if (blueWing) {
            for (int i = minIndex; i > 0; i--) {
                var p0 = profile.get(i);
                var p1 = profile.get(i - 1);
                // Stop if we've gone beyond the search window
                if (minWavelength - p1.wavelen().angstroms() > searchLimit) {
                    break;
                }
                if (p0.intensity() <= targetIntensity && p1.intensity() >= targetIntensity) {
                    return interpolateCrossing(p1, p0, targetIntensity);
                }
            }
        } else {
            for (int i = minIndex; i < profile.size() - 1; i++) {
                var p0 = profile.get(i);
                var p1 = profile.get(i + 1);
                // Stop if we've gone beyond the search window
                if (p1.wavelen().angstroms() - minWavelength > searchLimit) {
                    break;
                }
                if (p0.intensity() <= targetIntensity && p1.intensity() >= targetIntensity) {
                    return interpolateCrossing(p0, p1, targetIntensity);
                }
            }
        }

        return null;
    }

    private static double interpolateCrossing(SpectrumAnalyzer.DataPoint p0, SpectrumAnalyzer.DataPoint p1, double targetIntensity) {
        double intensityDiff = p1.intensity() - p0.intensity();
        if (Math.abs(intensityDiff) < 1e-10) {
            return (p0.wavelen().angstroms() + p1.wavelen().angstroms()) / 2.0;
        }
        double t = (targetIntensity - p0.intensity()) / intensityDiff;
        return p0.wavelen().angstroms() + t * (p1.wavelen().angstroms() - p0.wavelen().angstroms());
    }
}
