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

import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public class SpectrumAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectrumAnalyzer.class);

    private static final double TOTAL_ANGLE = Math.toRadians(34);
    public static final int DEFAULT_ORDER = 1;
    public static final int DEFAULT_DENSITY = 2400;
    public static final int DEFAULT_FOCAL_LEN = 125;

    /**
     * Computes the beta angle
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @return the beta angle (in radians)
     */
    private static double computeAngleBeta(int order, int density, double lambda0) {
        return computeAlphaAngle(order, density, lambda0) - TOTAL_ANGLE;
    }

    /**
     * Computes the alpha angle
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @return the alpha angle (in radians)
     */
    private static double computeAlphaAngle(int order, int density, double lambda0) {
        return Math.asin(order * density * lambda0 / (2_000_000 * Math.cos(TOTAL_ANGLE / 2))) + TOTAL_ANGLE / 2;
    }

    /**
     * Returns the spectral dispersion, in nanometers/pixel
     *
     * @param order the grating order
     * @param density the grating density, in lines/mm
     * @param lambda0 the wavelength in nanometers
     * @param pixelSize the pixel size, in micrometers
     * @param focalLength the lens focal length in mm
     * @return the spectral dispersion, in nanometers/pixel
     */
    public static double computeSpectralDispersion(int order, int density, double lambda0, double pixelSize, double focalLength) {
        var beta = computeAngleBeta(order, density, lambda0);
        return 1000 * pixelSize * Math.cos(beta) / density / focalLength;
    }

    /**
     * Returns the spectral dispersion, in nanometers/pixel, using the default
     * Sol'Ex parameters.
     *
     * @param lambda0 the wavelength in nanometers
     * @param pixelSize the pixel size, in micrometers
     * @return the spectral dispersion, in nanometers/pixel
     */
    public static double computeSpectralDispersion(double lambda0, double pixelSize) {
        return computeSpectralDispersion(DEFAULT_ORDER, DEFAULT_DENSITY, lambda0, pixelSize, DEFAULT_FOCAL_LEN);
    }

    /**
     * Returns, for a list of measured data points, the reference data points from BASS2000.
     * The resulting datapoints are normalized according to the maximum intensity of the measured data points.
     *
     * @param measuredDataPoints the measured data points
     * @return the reference data points
     */
    public static List<DataPoint> findReferenceDatapoints(List<DataPoint> measuredDataPoints) {
        var referenceDataPoints = findReferenceDatapointsNoNormalization(measuredDataPoints);
        double maxVal = measuredDataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(0);
        var maxRef = referenceDataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(0);
        return referenceDataPoints.stream().map(dataPoint -> new DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), dataPoint.intensity() * maxVal / maxRef)).toList();
    }

    private static List<DataPoint> findReferenceDatapointsNoNormalization(List<DataPoint> measuredDataPoints) {
        var referenceDataPoints = new ArrayList<DataPoint>();
        for (var dataPoint : measuredDataPoints) {
            referenceDataPoints.add(new DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), ReferenceIntensities.intensityAt(dataPoint.wavelen())));
        }
        return referenceDataPoints;
    }

    /**
     * Computes the data points for a given average image, using the given query details.
     *
     * @param details the query details
     * @param start the start column
     * @param end the end column
     * @param height the image height
     * @param width the image width
     * @param data the image data
     * @return the data points
     */
    public static List<DataPoint> computeDataPoints(QueryDetails details, DoubleUnaryOperator polynomial, int start, int end, int width, int height, float[] data) {
        var lambda0 = details.line().wavelength();
        var pixelSize = details.pixelSize();
        var binning = details.binning();
        double dispersion = computeSpectralDispersion(lambda0, pixelSize * binning);
        var dataPoints = new ArrayList<DataPoint>();
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int x = start; x < end; x++) {
            var v = polynomial.applyAsDouble(x);
            min = Math.min(v, min);
            max = Math.max(v, max);
        }
        if (min == Double.MAX_VALUE) {
            min = 0;
        }
        if (max == Double.MIN_VALUE) {
            max = height;
        }
        min = Math.max(0, min);
        max = Math.min(height, max);
        double mid = (max + min) / 2.0;
        double range = (max - min) / 2.0;
        for (int y = (int) range; y < height - range; y++) {
            double cpt = 0;
            double val = 0;
            for (int x = start; x < end; x++) {
                var v = polynomial.applyAsDouble(x);
                var shift = v - mid;
                int ny = (int) Math.round(y + shift);
                if (ny >= 0 && ny < height) {
                    val += data[width * ny + x];
                    cpt++;
                }
            }
            if (cpt > 0) {
                var pixelShift = y - mid;
                var wl = computeWavelength(pixelShift, lambda0, dispersion);
                dataPoints.add(new DataPoint(wl, pixelShift, val / cpt));
            }
        }
        return dataPoints;
    }

    /**
     * Given a map of measurements, finds the list of datapoints which best match the reference data points.
     */
    public static QueryDetails findBestMatch(Map<QueryDetails, List<DataPoint>> measurements) {
        record Solution(QueryDetails query, double distance) {
        }
        QueryDetails best = null;
        // the stddev of all measurements is going to be the same, so we only need to compute it once

        if (!measurements.isEmpty()) {
            var firstEntry = measurements.values().stream().findFirst().get();
            var stdDev = gaussianFit(firstEntry)[2];
            best = measurements.entrySet().stream().parallel().map(entry -> {
                var query = entry.getKey();
                var dataPoints = entry.getValue();
                var referenceDataPoints = findReferenceDatapointsNoNormalization(dataPoints);

                // Calculate min and max values for data points
                double minValue = dataPoints.stream().mapToDouble(DataPoint::intensity).min().orElse(0);
                double maxValue = dataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(1);
                double ratio = minValue / maxValue;

                // Calculate min and max values for reference data points
                double refMinValue = referenceDataPoints.stream().mapToDouble(DataPoint::intensity).min().orElse(0);
                double refMaxValue = referenceDataPoints.stream().mapToDouble(DataPoint::intensity).max().orElse(1);
                double refRatio = refMinValue / refMaxValue;
                var normalizedMinValue = refMinValue * maxValue / refMaxValue;
                double weight = 3*Math.sqrt(1d - (refRatio > ratio ? ratio / refRatio : refRatio / ratio));
                weight += 1d - Math.sqrt(normalizedMinValue > minValue ? minValue / normalizedMinValue : normalizedMinValue / minValue);
                weight /= 4;
                double distance = 0;
                for (int i = 0; i < dataPoints.size(); i++) {
                    var dp = dataPoints.get(i);
                    var ref = referenceDataPoints.get(i);
                    var normalizedIntensity = (dp.intensity() - minValue) / (maxValue - minValue);
                    var normalizedRefIntensity = (ref.intensity() - refMinValue) / (refMaxValue - refMinValue);
                    var diff = normalizedIntensity - normalizedRefIntensity;
                    distance += diff * diff;
                }
                if (stdDev > 0) {
                    // estimate gaussian around line center
                    var referenceFit = gaussianFit(referenceDataPoints);
                    // adjust weight according to the std deviation of the gaussian
                    double refStdDev = referenceFit[2];
                    if (refStdDev > 0) {
                        weight += (1d - Math.sqrt(stdDev > refStdDev ? refStdDev / stdDev : stdDev / refStdDev));
                    }
                }
                distance = weight * Math.sqrt(distance);
                LOGGER.debug("Line {} binning {} has distance {}", query.line(), query.binning(), distance);
                return new Solution(query, distance);
            }).min(Comparator.comparingDouble(s -> s.distance)).map(s -> s.query).orElse(null);
        }
        if (best == null) {
            // not a good solution, so we'll assume h-alpha or the closest to h-alpha
            double min = Double.MAX_VALUE;
            var ha = SpectralRay.H_ALPHA.wavelength();
            for (var entry : measurements.entrySet()) {
                var query = entry.getKey();
                var diff = Math.abs(query.line.wavelength() - ha);
                if (diff < min) {
                    min = diff;
                    best = query;
                }
            }
        }
        return best;
    }

    private static double[] gaussianFit(List<DataPoint> datapoints) {
        var observations = new ArrayList<WeightedObservedPoint>();
        // find datapoint with minimal intensity
        var min = datapoints.stream().min(Comparator.comparingDouble(DataPoint::intensity)).orElse(null);
        if (min == null) {
            return new double[]{-1, -1, -1};
        }
        var i = datapoints.indexOf(min);
        var intensity = min.intensity();
        observations.add(new WeightedObservedPoint(1, min.wavelen(), intensity));
        for (int j = i + 1; j < Math.min(i+11, datapoints.size()); j++) {
            var dp = datapoints.get(j);
            observations.add(new WeightedObservedPoint(1, dp.wavelen(), dp.intensity()));
        }
        for (int j = i - 1; j >= Math.max(i-11, 0); j--) {
            var dp = datapoints.get(j);
            observations.add(new WeightedObservedPoint(1, dp.wavelen(), dp.intensity()));
        }
        try {
            var fitter = GaussianCurveFitter.create().withMaxIterations(131072);
            var fit = fitter.fit(observations);
            return fit;
        } catch (Exception e) {
            return new double[]{0, 0, 500};
        }
    }

    private static double computeWavelength(double pixelShift, double lambda, double dispersion) {
        return 10 * (lambda + pixelShift * dispersion);
    }

    public record QueryDetails(SpectralRay line, double pixelSize, int binning) {
    }

    public record DataPoint(double wavelen, double pixelShift, double intensity) {
    }

}
