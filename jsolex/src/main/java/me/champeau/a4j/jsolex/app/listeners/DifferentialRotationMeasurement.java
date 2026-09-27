/*
 * Copyright 2026-2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.EllipseFittingRequestEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SlitRotationAbsorption;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.LeastSquares;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

/**
 * Measures the solar differential rotation from the Doppler shifts between East and West
 * limb points of one or several scans.
 */
final class DifferentialRotationMeasurement {
    private static final Logger LOGGER = LoggerFactory.getLogger(DifferentialRotationMeasurement.class);

    private static final double SPEED_OF_LIGHT_KM_S = 299792.458;
    private static final double SOLAR_RADIUS_KM = 695700.0;
    // Snodgrass & Ulrich (1990) differential rotation law, made synodic
    // ω(φ) = A + B·sin²(φ) + C·sin⁴(φ) deg/day
    private static final double SNODGRASS_SIDEREAL_A = 14.713;
    private static final double EARTH_ORBITAL_RATE_DEG_PER_DAY = 0.9856;
    static final double SNODGRASS_A = SNODGRASS_SIDEREAL_A - EARTH_ORBITAL_RATE_DEG_PER_DAY;
    static final double SNODGRASS_B = -2.396;
    static final double SNODGRASS_C = -1.787;
    private static final DifferentialRotationCoefficients SNODGRASS = new DifferentialRotationCoefficients(SNODGRASS_A, SNODGRASS_B, SNODGRASS_C, 0, 0, 0);

    /**
     * Minimum heliographic latitude (degrees) for the rotation profile scan.
     */
    static final double ROTATION_PROFILE_MIN_LAT_DEG = -60;
    /**
     * Maximum heliographic latitude (degrees) for the rotation profile scan.
     */
    static final double ROTATION_PROFILE_MAX_LAT_DEG = 60;
    /**
     * Maximum plausible measured velocity (km/s). Points exceeding this
     * threshold are discarded as outliers (e.g. failed Voigt fits).
     */
    private static final double ROTATION_PROFILE_MAX_VELOCITY_KM_S = 10.0;
    /**
     * Number of pixel columns averaged on each side of the target column when
     * extracting a spectral profile from the reconstructed image. A radius of 2
     * means 5 columns are averaged (target ± 2), which reduces noise.
     */
    private static final int ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS = 4;
    /**
     * Minimum fraction of the rotation signal which must remain after the spectral line
     * polynomial is subtracted. Below it, the scan direction is too close to the solar
     * rotation axis for the rotation to be measured.
     */
    static final double ROTATION_PROFILE_MIN_RETAINED_FRACTION = 0.5;

    // Voigt fit is more robust for rotation profile measurement
    private static final DopplerMeasurementMethod ROTATION_PROFILE_DOPPLER_METHOD = DopplerMeasurementMethod.VOIGT_FIT;

    private DifferentialRotationMeasurement() {
    }

    /**
     * The processing results of a scan which are required to measure its rotation profile.
     */
    record Scan(File serFile,
                AverageImageComputedEvent.AverageImage averageImage,
                ReferenceCoords referenceCoords,
                Ellipse ellipse,
                SolarParameters solarParameters) {
    }

    // Record to hold velocity measurement with its position data for weighted averaging
    private record VelocityMeasurement(double velocity, double longitudeFraction, double[] absorbedRatios) {
    }

    /**
     * Measures the rotation profile of a scan.
     *
     * @return the points of the scan, as (latitude, velocity, error, absorbed ratios), or an
     * empty list if the scan cannot be measured
     */
    static List<double[]> measure(Scan scan, DifferentialRotationConfig config, DoubleConsumer progressCallback) {
        var payload = scan.averageImage();
        var params = payload.adjustedParams();
        var lambda0 = params.spectrumParams().ray().wavelength();
        var binning = params.observationDetails().binning();
        var pixelSize = params.observationDetails().pixelSize();
        var canCalibrate = binning != null && pixelSize != null && lambda0.nanos() > 0 && pixelSize > 0 && binning > 0;

        if (!canCalibrate) {
            LOGGER.warn("Cannot generate rotation profile: wavelength calibration not available");
            return List.of();
        }

        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(params.observationDetails().instrument(), lambda0, pixelSize * binning);

        var ellipse = scan.ellipse();
        var centerX = ellipse.center().a();
        var centerY = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
        var b0 = scan.solarParameters().b0();
        var angleP = scan.solarParameters().p();

        LOGGER.info("=== AUTO ROTATION PROFILE ===");
        LOGGER.info("Lambda0: {} Å, Dispersion: {} Å/pixel", lambda0.angstroms(), dispersion.angstromsPerPixel());

        var polynomial = payload.polynomial();
        var range = PixelShiftRange.computePixelShiftRange(payload.leftBorder(), payload.rightBorder(), payload.image().height(), polynomial);

        var refCoords = scan.referenceCoords();
        // Check for HFLIP/VFLIP to determine actual image orientation
        var hasHFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.HFLIP);
        var hasVFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.VFLIP);

        // Longitude sign convention: East limb = negative longitude, West limb = positive
        // After LEFT_ROTATION without HFLIP: orientation is mirrored, so signs swap
        var eastLonSign = hasHFlip ? -1 : 1;
        // Latitude sign: VFLIP swaps north/south, so we need to negate latitude
        var latSign = hasVFlip ? -1 : 1;

        return computeRotationProfile(scan.serFile(), refCoords, range, polynomial, isPolynomialFromScan(params),
                lambda0, dispersion, centerX, centerY, radius, b0, angleP, eastLonSign, latSign,
                config, progressCallback);
    }

    /**
     * Processes a SER file independently of any other processing, keeping only what is
     * required to measure its rotation profile. Nothing is written to disk.
     *
     * @return the scan, or an empty optional if the processing failed
     */
    static Optional<Scan> processScan(File serFile,
                                      ProcessParams baseParams,
                                      Path outputDirectory,
                                      int memoryRestrictionMultiplier,
                                      ProgressOperation operation,
                                      Consumer<EllipseFittingRequestEvent> ellipseFittingHandler) {
        try {
            Header header;
            try (var reader = SerFileReader.of(serFile)) {
                header = reader.header();
            }
            var scanParams = baseParams
                    .withObservationDetails(baseParams.observationDetails().withDate(header.metadata().utcDateTime()))
                    .withRequestedImages(new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED), List.of(0d), Set.of(), Set.of(), ImageMathParams.NONE, false, false))
                    .withExtraParams(baseParams.extraParams().withAutosave(false));
            var averageImage = new AtomicReference<AverageImageComputedEvent.AverageImage>();
            var scanImages = new AtomicReference<Map<PixelShift, ImageWrapper>>(Map.of());
            var processor = new SolexVideoProcessor(serFile, outputDirectory, 0, scanParams, LocalDateTime.now(), false, memoryRestrictionMultiplier, operation);
            processor.addEventListener(new ProcessingEventListener() {
                @Override
                public void onAverageImageComputed(AverageImageComputedEvent e) {
                    averageImage.set(e.getPayload());
                }

                @Override
                public void onProcessingDone(ProcessingDoneEvent e) {
                    scanImages.set(e.getPayload().shiftImages());
                }

                @Override
                public void onEllipseFittingRequest(EllipseFittingRequestEvent e) {
                    ellipseFittingHandler.accept(e);
                }
            });
            processor.process();
            var referenceImage = scanImages.get().entrySet().stream()
                    .min(Comparator.comparingDouble(e -> Math.abs(e.getKey().pixelShift())))
                    .map(Map.Entry::getValue);
            var refCoords = referenceImage.flatMap(image -> image.findMetadata(ReferenceCoords.class));
            var ellipse = referenceImage.flatMap(image -> image.findMetadata(Ellipse.class));
            var solarParams = referenceImage.flatMap(image -> image.findMetadata(SolarParameters.class));
            if (averageImage.get() == null || refCoords.isEmpty() || ellipse.isEmpty() || solarParams.isEmpty()) {
                LOGGER.warn("Cannot measure the rotation profile of {}: processing did not complete", serFile);
                return Optional.empty();
            }
            return Optional.of(new Scan(serFile, averageImage.get(), refCoords.get(), ellipse.get(), solarParams.get()));
        } catch (Exception ex) {
            LOGGER.error("Error processing {} for the rotation profile", serFile, ex);
            return Optional.empty();
        }
    }

    private static boolean isPolynomialFromScan(ProcessParams params) {
        var geometry = params.geometryParams();
        var forced = geometry.isForcePolynomial() && geometry.forcedPolynomial().isPresent();
        var fromReference = geometry.isSaturatedDiskMode() && geometry.referencePolynomialDirectory().isPresent();
        return !forced && !fromReference;
    }

    static double computeRetainedRotationFraction(List<double[]> velocityData) {
        var equatorPoint = velocityData.stream()
                .min(Comparator.comparingDouble(point -> Math.abs(point[0])))
                .orElseThrow();
        return 1 - equatorPoint[3];
    }

    private static List<double[]> computeRotationProfile(File serFile,
                                                         ReferenceCoords refCoords,
                                                         PixelShiftRange range, DoubleUnaryOperator polynomial,
                                                         boolean polynomialFromScan,
                                                         Wavelen lambda0, Dispersion dispersion,
                                                         double centerX, double centerY, double radius,
                                                         double b0, double angleP, int eastLonSign, int latSign,
                                                         DifferentialRotationConfig config,
                                                         DoubleConsumer progressCallback) {
        var velocityData = new ArrayList<double[]>();
        int totalPoints = 0;

        var limbLongitude = config.limbLongitudeDeg();
        var longitudeHalfRange = config.longitudeHalfRangeDeg();
        var longitudeStep = config.longitudeStepDeg();
        var latitudeStep = config.latitudeStepDeg();

        var lonMin = limbLongitude - longitudeHalfRange;
        var lonMax = limbLongitude + longitudeHalfRange;
        var totalLatSteps = (int) ((ROTATION_PROFILE_MAX_LAT_DEG - ROTATION_PROFILE_MIN_LAT_DEG) / latitudeStep) + 1;
        var measurementsByLat = new TreeMap<Integer, List<VelocityMeasurement>>();

        var measurement = ROTATION_PROFILE_DOPPLER_METHOD.createMeasurement(config.voigtFitHalfWidthAngstroms());
        try (var reader = SerFileReader.of(serFile)) {
            var totalFrames = reader.header().frameCount();
            var absorption = polynomialFromScan
                    ? SlitRotationAbsorption.compute(refCoords, centerX, centerY, radius, b0, angleP, totalFrames, reader.header().geometry().width())
                    : SlitRotationAbsorption.none();

            for (double latDeg = ROTATION_PROFILE_MIN_LAT_DEG; latDeg <= ROTATION_PROFILE_MAX_LAT_DEG; latDeg += latitudeStep) {
                totalPoints++;
                progressCallback.accept((double) totalPoints / totalLatSteps);
                // Apply latSign to account for VFLIP: if image is vertically flipped, north/south are swapped
                var effectiveLatDeg = latSign * latDeg;
                var latRad = Math.toRadians(effectiveLatDeg);
                var colatitude = Math.PI / 2 - latRad;

                for (double lonDeg = lonMin; lonDeg <= lonMax; lonDeg += longitudeStep) {
                    var eastLonRad = Math.toRadians(eastLonSign * lonDeg);
                    var westLonRad = Math.toRadians(-eastLonSign * lonDeg);

                    var eastCoords = SlitRotationAbsorption.projectToImage(eastLonRad, colatitude, radius, b0, angleP);
                    var eastImgX = (int) Math.round(centerX + eastCoords[0]);
                    var eastImgY = (int) Math.round(centerY + eastCoords[1]);

                    var westCoords = SlitRotationAbsorption.projectToImage(westLonRad, colatitude, radius, b0, angleP);
                    var westImgX = (int) Math.round(centerX + westCoords[0]);
                    var westImgY = (int) Math.round(centerY + westCoords[1]);

                    var eastOrig = refCoords.determineOriginalCoordinates(new Point2D(eastImgX, eastImgY), ReferenceCoords.NO_LIMIT);
                    var westOrig = refCoords.determineOriginalCoordinates(new Point2D(westImgX, westImgY), ReferenceCoords.NO_LIMIT);

                    var eastColumn = (int) Math.round(eastOrig.x());
                    var eastFrame = (int) Math.round(eastOrig.y());
                    var westColumn = (int) Math.round(westOrig.x());
                    var westFrame = (int) Math.round(westOrig.y());

                    if (eastFrame < 0 || eastFrame >= totalFrames || westFrame < 0 || westFrame >= totalFrames) {
                        continue;
                    }

                    var eastFrameData = readFrameData(reader, eastFrame);
                    if (eastFrameData == null) {
                        continue;
                    }

                    var westFrameData = readFrameData(reader, westFrame);
                    if (westFrameData == null) {
                        continue;
                    }

                    var eastProfile = extractPointProfile(eastColumn, eastFrameData, range, polynomial, lambda0, dispersion, ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS);
                    var westProfile = extractPointProfile(westColumn, westFrameData, range, polynomial, lambda0, dispersion, ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS);

                    if (eastProfile.isEmpty() || westProfile.isEmpty()) {
                        continue;
                    }

                    var eastCenter = measurement.measureLineCenter(eastProfile);
                    var westCenter = measurement.measureLineCenter(westProfile);
                    if (eastCenter.isEmpty() || westCenter.isEmpty()) {
                        continue;
                    }
                    var dopplerShiftAngstroms = westCenter.getAsDouble() - eastCenter.getAsDouble();

                    var measuredVelocity = (dopplerShiftAngstroms / lambda0.angstroms()) * SPEED_OF_LIGHT_KM_S / 2.0;

                    var cosLat = Math.cos(latRad);
                    var sinLon = Math.sin(Math.toRadians(lonDeg));
                    var geometryFactor = cosLat * sinLon * Math.cos(b0);
                    if (Math.abs(geometryFactor) > 0.1) {
                        var equatorialVelocity = Math.abs(measuredVelocity / geometryFactor);
                        if (equatorialVelocity < ROTATION_PROFILE_MAX_VELOCITY_KM_S) {
                            var latBin = (int) latDeg;
                            // longitudeFraction: 0 at limb (best accuracy), 1 at meridian (worst)
                            var longitudeFraction = 1.0 - Math.abs(lonDeg) / limbLongitude;
                            measurementsByLat.computeIfAbsent(latBin, k -> new ArrayList<>())
                                    .add(new VelocityMeasurement(equatorialVelocity, longitudeFraction,
                                            absorption.absorbedRatios(latRad, eastLonRad, westLonRad, eastColumn, westColumn)));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Error reading SER file for rotation profile", ex);
            return List.of();
        }

        var sampleRejection = config.sampleRejectionMethod();
        var noiseReduction = config.noiseReductionMethod();

        for (var entry : measurementsByLat.entrySet()) {
            var latBin = entry.getKey();
            var measurements = entry.getValue();
            var velocities = measurements.stream().map(VelocityMeasurement::velocity).toList();
            // For weighted average: weight = 1 - longitudeFraction (higher weight at limb)
            var weights = measurements.stream()
                    .map(m -> 1.0 - m.longitudeFraction())
                    .toList();
            // Apply sample rejection before aggregation
            var filtered = sampleRejection.filter(velocities, weights);
            var result = noiseReduction.aggregate(filtered.velocities(), filtered.weights());
            var ratios = averageAbsorbedRatios(measurements.stream().map(VelocityMeasurement::absorbedRatios).toList());
            velocityData.add(new double[]{latBin, result.value(), result.error(), ratios[0], ratios[1], ratios[2]});
        }

        if (velocityData.isEmpty()) {
            LOGGER.warn("No valid velocity measurements obtained");
            return List.of();
        }

        return applyLatitudeSmoothingFilter(velocityData, config.smoothingWindowDeg(), noiseReduction);
    }

    private static List<double[]> applyLatitudeSmoothingFilter(List<double[]> velocityData,
                                                               double windowDeg,
                                                               NoiseReductionMethod noiseReduction) {
        var filtered = new ArrayList<double[]>();
        var halfWindow = windowDeg / 2.0;
        for (int i = 0; i < velocityData.size(); i++) {
            var centerPoint = velocityData.get(i);
            var centerLat = centerPoint[0];
            var windowValues = new ArrayList<Double>();
            var windowErrors = new ArrayList<Double>();
            var windowRatios = new ArrayList<double[]>();
            for (var point : velocityData) {
                if (Math.abs(point[0] - centerLat) <= halfWindow) {
                    windowValues.add(point[1]);
                    windowErrors.add(point[2]);
                    windowRatios.add(Arrays.copyOfRange(point, 3, 3 + SlitRotationAbsorption.BASIS_SIZE));
                }
            }
            if (windowValues.size() == 1) {
                // Single point: preserve original value and error
                filtered.add(centerPoint);
            } else {
                // Compute smoothed value using the noise reduction method
                var result = noiseReduction.aggregate(windowValues, null);
                // Propagate errors properly instead of using spread-based error
                var propagatedError = propagateErrors(windowErrors, noiseReduction);
                var ratios = averageAbsorbedRatios(windowRatios);
                filtered.add(new double[]{centerLat, result.value(), propagatedError, ratios[0], ratios[1], ratios[2]});
            }
        }
        return filtered;
    }

    private static double[] averageAbsorbedRatios(List<double[]> ratios) {
        var average = new double[SlitRotationAbsorption.BASIS_SIZE];
        for (var r : ratios) {
            for (int k = 0; k < average.length; k++) {
                average[k] += r[k] / ratios.size();
            }
        }
        return average;
    }

    /**
     * Propagates measurement errors when combining multiple points.
     * For n measurements with errors σ₁, σ₂, ..., σₙ:
     * - MEDIAN: median(σᵢ) / √n (robust estimate)
     * - AVERAGE: √(Σσᵢ²) / n (standard error propagation)
     * - WEIGHTED_AVERAGE: same as AVERAGE (weights already applied in stage 1)
     */
    private static double propagateErrors(List<Double> errors, NoiseReductionMethod method) {
        int n = errors.size();
        if (n == 0) {
            return 0;
        }
        return switch (method) {
            case MEDIAN -> {
                var sorted = errors.stream().sorted().toList();
                var medianError = sorted.get(sorted.size() / 2);
                yield medianError / Math.sqrt(n);
            }
            case AVERAGE, WEIGHTED_AVERAGE -> {
                var sumSquares = errors.stream().mapToDouble(e -> e * e).sum();
                yield Math.sqrt(sumSquares) / n;
            }
        };
    }

    private static float[][] readFrameData(SerFileReader reader, int frameNumber) {
        try {
            var geometry = reader.header().geometry();
            var converter = ImageUtils.createImageConverter(geometry.colorMode());
            reader.seekFrame(frameNumber);
            var currentFrame = reader.currentFrame().data();
            var buffer = converter.createBuffer(geometry);
            converter.convert(frameNumber, currentFrame, geometry, buffer);
            return new Image(geometry.width(), geometry.height(), buffer).data();
        } catch (Exception ex) {
            LOGGER.error("Error reading frame {}", frameNumber, ex);
            return null;
        }
    }

    private static List<SpectrumAnalyzer.DataPoint> extractPointProfile(int column,
                                                                        float[][] frameData,
                                                                        PixelShiftRange range,
                                                                        DoubleUnaryOperator polynomial,
                                                                        Wavelen lambda0,
                                                                        Dispersion dispersion,
                                                                        int columnAveragingRadius) {
        var dataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
        if (frameData == null || frameData.length == 0) {
            return dataPoints;
        }
        var frameHeight = frameData.length;
        var frameWidth = frameData[0].length;

        var centerPolyValue = polynomial.applyAsDouble(column);
        for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
            var centerExactNy = centerPolyValue + pixelShift;
            var centerLowerNy = (int) Math.floor(centerExactNy);
            var centerUpperNy = (int) Math.ceil(centerExactNy);

            if (centerLowerNy >= 0 && centerUpperNy < frameHeight) {
                double sum = 0;
                int count = 0;
                for (int colOffset = -columnAveragingRadius; colOffset <= columnAveragingRadius; colOffset++) {
                    int col = column + colOffset;
                    if (col >= 0 && col < frameWidth) {
                        var colPolyValue = polynomial.applyAsDouble(col);
                        var colExactNy = colPolyValue + pixelShift;
                        var colLowerNy = (int) Math.floor(colExactNy);
                        var colUpperNy = (int) Math.ceil(colExactNy);
                        if (colLowerNy >= 0 && colUpperNy < frameHeight) {
                            var lowerValue = frameData[colLowerNy][col];
                            var upperValue = frameData[colUpperNy][col];
                            sum += lowerValue + (upperValue - lowerValue) * (colExactNy - colLowerNy);
                            count++;
                        }
                    }
                }
                var interpolatedValue = count > 0 ? (float) (sum / count) : 0f;
                var wl = SpectralProfileHelper.computeWavelength(pixelShift, lambda0, dispersion);
                dataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, interpolatedValue));
            }
        }
        return dataPoints;
    }

    /**
     * Fits ω(φ) = A + B·sin²(φ) + C·sin⁴(φ) to the points of all scans. Each measured point lacks
     * the part of the rotation absorbed by the spectral line polynomial, so each basis
     * function is reduced by its absorbed ratio.
     * <p>
     * For a single scan, the standard errors come from the residuals of the fit, scaled by
     * the square root of the number of points in a smoothing window since the latitude
     * smoothing makes neighbouring points dependent. For several scans, they come from the
     * scatter of the coefficients fitted on each scan alone, which also accounts for what
     * changes from one scan to the next.
     */
    static DifferentialRotationCoefficients fit(List<List<double[]>> scans, DifferentialRotationConfig config) {
        var joint = fitWithinScanErrors(scans, config);
        if (scans.size() < 2) {
            return joint;
        }
        var perScan = scans.stream().map(scan -> fitWithinScanErrors(List.of(scan), config)).toList();
        return new DifferentialRotationCoefficients(joint.a(), joint.b(), joint.c(),
                standardErrorOfMean(perScan.stream().mapToDouble(DifferentialRotationCoefficients::a).toArray()),
                standardErrorOfMean(perScan.stream().mapToDouble(DifferentialRotationCoefficients::b).toArray()),
                standardErrorOfMean(perScan.stream().mapToDouble(DifferentialRotationCoefficients::c).toArray()));
    }

    private static double standardErrorOfMean(double[] values) {
        var mean = Arrays.stream(values).average().orElse(Double.NaN);
        var variance = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / (values.length - 1);
        return Math.sqrt(variance / values.length);
    }

    private static DifferentialRotationCoefficients fitWithinScanErrors(List<List<double[]>> scans, DifferentialRotationConfig config) {
        var fit = new LeastSquares(SlitRotationAbsorption.BASIS_SIZE);
        for (var scan : scans) {
            for (var p : scan) {
                var sinLat = Math.sin(Math.toRadians(p[0]));
                var sinLat2 = sinLat * sinLat;
                var mad = p[2];
                fit.add(new double[]{1 - p[3], sinLat2 - p[4], sinLat2 * sinLat2 - p[5]},
                        velocityToAngularVelocity(p[1]),
                        mad > 0 ? 1.0 / mad : 1.0);
            }
        }
        var coeffs = fit.solve();
        if (coeffs == null) {
            return new DifferentialRotationCoefficients(0, 0, 0, Double.NaN, Double.NaN, Double.NaN);
        }
        var errors = fit.standardErrors(coeffs);
        if (errors == null) {
            return new DifferentialRotationCoefficients(coeffs[0], coeffs[1], coeffs[2], Double.NaN, Double.NaN, Double.NaN);
        }
        var binSpacing = Math.max(1, config.latitudeStepDeg());
        var pointsPerWindow = 2 * Math.floor(config.smoothingWindowDeg() / 2 / binSpacing) + 1;
        var scale = Math.sqrt(pointsPerWindow);
        return new DifferentialRotationCoefficients(coeffs[0], coeffs[1], coeffs[2],
                errors[0] * scale, errors[1] * scale, errors[2] * scale);
    }

    static List<double[]> combineCorrected(List<List<double[]>> scans, DifferentialRotationCoefficients coeffs) {
        var sumsByLatitude = new TreeMap<Double, double[]>();
        for (var scan : scans) {
            for (var p : scan) {
                var absorbed = coeffs.a() * p[3] + coeffs.b() * p[4] + coeffs.c() * p[5];
                var velocity = p[1] + angularVelocityToVelocity(absorbed);
                var weight = p[2] > 0 ? 1 / (p[2] * p[2]) : 1;
                var sums = sumsByLatitude.computeIfAbsent(p[0], k -> new double[2]);
                sums[0] += weight * velocity;
                sums[1] += weight;
            }
        }
        return sumsByLatitude.entrySet().stream()
                .map(e -> new double[]{e.getKey(), e.getValue()[0] / e.getValue()[1], 1 / Math.sqrt(e.getValue()[1])})
                .toList();
    }

    static double snodgrassAngularVelocity(double latDeg) {
        return SNODGRASS.angularVelocity(latDeg);
    }

    static double snodgrassVelocity(double latDeg) {
        return angularVelocityToVelocity(snodgrassAngularVelocity(latDeg));
    }

    static double velocityToAngularVelocity(double velocityKmS) {
        return velocityKmS / SOLAR_RADIUS_KM * (180.0 / Math.PI) * 86400.0;
    }

    private static double angularVelocityToVelocity(double degreesPerDay) {
        return Math.toRadians(degreesPerDay) / 86400.0 * SOLAR_RADIUS_KM;
    }

    /**
     * Fitted differential rotation coefficients using the standard formula
     * (often called the "Faye formula"): ω(φ) = A + B·sin²(φ) + C·sin⁴(φ) in deg/day.
     * Reference coefficients are from Snodgrass & Ulrich (1990).
     */
    record DifferentialRotationCoefficients(double a, double b, double c, double aError, double bError, double cError) {
        double angularVelocity(double latDeg) {
            var sinLat = Math.sin(Math.toRadians(latDeg));
            var sinLat2 = sinLat * sinLat;
            var sinLat4 = sinLat2 * sinLat2;
            return a + b * sinLat2 + c * sinLat4;
        }

        double tangentialVelocity(double latDeg) {
            return angularVelocityToVelocity(angularVelocity(latDeg)) * Math.cos(Math.toRadians(latDeg));
        }
    }
}
