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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversion;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversionResult;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMap;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMapRenderer;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudModel;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudModelCharts;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Performs the cloud model inversion of H-alpha profiles on demand, and emits its maps and charts.
 */
public final class CloudModelProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudModelProcessor.class);

    private static final double SHIFT_HALF_WIDTH_ANGSTROMS = 3.0;
    private static final double MAX_RECOMMENDED_DISPERSION = 0.12;
    private static final int MIN_SHIFTS = 31;
    private static final double ARCSECONDS_PER_RADIAN = 206264.8;
    private static final double TARGET_RESOLUTION_ARCSECONDS = 1.5;
    public static final int MAX_BINNING = 4;
    private static final String CATEGORY = "cloud";
    private static final List<CloudMap> DEFAULT_MAPS = List.of(
            CloudMap.MASS_FLUX_VELOCITY,
            CloudMap.FASTEST_VELOCITY,
            CloudMap.TEMPERATURE,
            CloudMap.COLUMN_MASS,
            CloudMap.INTEGRATED_OPACITY,
            CloudMap.REDUCED_CHI2
    );
    private static final List<CloudMap> DEBUG_MAPS = List.of(
            CloudMap.KINETIC_ENERGY_VELOCITY,
            CloudMap.KINETIC_ENERGY_FLUX_VELOCITY,
            CloudMap.MASS_FLUX,
            CloudMap.KINETIC_ENERGY,
            CloudMap.KINETIC_ENERGY_FLUX,
            CloudMap.SOURCE_FUNCTION,
            CloudMap.DOPPLER_WIDTH,
            CloudMap.BACKGROUND_SCALE,
            CloudMap.BACKGROUND_WIDTH,
            CloudMap.COMPONENTS,
            CloudMap.DETECTION
    );

    private CloudModelProcessor() {
    }

    /**
     * Returns true if the cloud model applies to the observed line.
     */
    private static boolean isApplicable(ProcessParams params) {
        return params.spectrumParams().ray().label().equalsIgnoreCase(SpectralRay.H_ALPHA.label());
    }

    /**
     * Returns true if the inversion can be performed on the observation: an H-alpha observation of a disk which is not saturated.
     */
    public static boolean canInvert(ProcessParams params) {
        return isApplicable(params) && !params.geometryParams().isSaturatedDiskMode();
    }

    /**
     * Returns the largest turbulence which keeps the temperature positive for the narrowest cloud line, in km/s.
     */
    private static double maxTurbulence(double lambda0) {
        return CloudModel.SPEED_OF_LIGHT_KMS * CloudModel.MIN_DOPPLER_WIDTH / lambda0;
    }

    /**
     * Returns the nominal dispersion of the observation, in angstroms per pixel.
     */
    private static double dispersionOf(ProcessParams params) {
        return AbstractImageExpressionEvaluator.computeDispersion(params, params.spectrumParams().ray().wavelength()).angstromsPerPixel();
    }

    /**
     * Returns the size of the blocks of pixels to average before the inversion, so that a block
     * covers about the resolution allowed by the seeing. Returns 1 when the focal length or the
     * pixel size is unknown.
     */
    public static int defaultBinning(ProcessParams params) {
        var details = params.observationDetails();
        var focalLength = details.focalLength();
        var pixelSize = details.pixelSize();
        if (focalLength == null || focalLength <= 0 || pixelSize == null || pixelSize <= 0) {
            return 1;
        }
        var cameraBinning = details.binning() == null ? 1 : Math.max(1, details.binning());
        var arcsecondsPerPixel = ARCSECONDS_PER_RADIAN * pixelSize * cameraBinning / (focalLength * 1000d);
        return (int) Math.clamp(Math.round(TARGET_RESOLUTION_ARCSECONDS / arcsecondsPerPixel), 1, MAX_BINNING);
    }

    /**
     * Returns the pixel shifts which must be reconstructed for the inversion.
     *
     * @param params the process parameters
     * @param range the range of pixel shifts available in the observation
     * @return the pixel shifts
     */
    public static List<Double> computePixelShifts(ProcessParams params, PixelShiftRange range) {
        var dispersion = dispersionOf(params);
        var count = (int) Math.floor(SHIFT_HALF_WIDTH_ANGSTROMS / dispersion);
        var shifts = new ArrayList<Double>();
        for (var shift = -count; shift <= count; shift++) {
            if (range.includes(shift)) {
                shifts.add((double) shift);
            }
        }
        return shifts;
    }

    /**
     * Returns the images needed by the inversion, among those available.
     *
     * @param params the process parameters
     * @param range the range of pixel shifts available in the observation
     * @param lookup returns the image reconstructed at a pixel shift, or null if there is none
     * @return the images which are available
     */
    public static List<ImageWrapper> collectImages(ProcessParams params, PixelShiftRange range, Function<PixelShift, ImageWrapper> lookup) {
        return computePixelShifts(params, range).stream()
                .map(shift -> lookup.apply(new PixelShift(shift)))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Performs the inversion.
     *
     * @param params the process parameters
     * @param images the images reconstructed at different pixel shifts, each carrying its {@link PixelShift}
     * @param options the options of the inversion
     * @param broadcaster the broadcaster which receives the progress
     * @param operation the operation the progress is attached to
     * @return the result of the inversion
     * @throws IllegalStateException if the inversion cannot be performed on this observation
     */
    public static CloudInversionResult invert(ProcessParams params,
                                              List<ImageWrapper> images,
                                              CloudInversion.Options options,
                                              Broadcaster broadcaster,
                                              ProgressOperation operation) {
        if (!isApplicable(params)) {
            throw new IllegalStateException(message("cloud.model.not.halpha"));
        }
        if (params.geometryParams().isSaturatedDiskMode()) {
            throw new IllegalStateException(message("cloud.model.saturated.disk"));
        }
        var sorted = new ArrayList<ImageWrapper>();
        for (var image : images) {
            var shift = pixelShiftOf(image);
            if (sorted.stream().noneMatch(other -> pixelShiftOf(other) == shift)) {
                sorted.add(image);
            }
        }
        sorted.sort(Comparator.comparingDouble(CloudModelProcessor::pixelShiftOf));
        if (sorted.size() < MIN_SHIFTS) {
            throw new IllegalStateException(String.format(Locale.US, message("cloud.model.not.enough.shifts"), sorted.size(), MIN_SHIFTS));
        }
        var shifts = new double[sorted.size()];
        for (var i = 0; i < shifts.length; i++) {
            shifts[i] = pixelShiftOf(sorted.get(i));
        }
        var ellipse = sorted.getFirst().findMetadata(Ellipse.class)
                .orElseThrow(() -> new IllegalStateException(message("cloud.model.function.no.disk")));
        var dispersion = dispersionOf(params);
        if (dispersion > MAX_RECOMMENDED_DISPERSION) {
            LOGGER.warn(String.format(Locale.US, message("cloud.model.coarse.dispersion"), dispersion, MAX_RECOMMENDED_DISPERSION));
        }
        var lambda0 = params.spectrumParams().ray().wavelength().angstroms();
        var maxTurbulence = maxTurbulence(lambda0);
        if (options.nonThermalSpeed() < 0 || options.nonThermalSpeed() >= maxTurbulence) {
            throw new IllegalArgumentException(String.format(Locale.US, message("cloud.model.invalid.turbulence"), options.nonThermalSpeed(), maxTurbulence));
        }
        var child = operation.createChild(message("cloud.model.inversion"));
        broadcaster.broadcast(child);
        LOGGER.info(message("cloud.model.starting"), sorted.size());
        var start = System.nanoTime();
        try {
            var input = new CloudInversion.Input(sorted, shifts, ellipse, lambda0, dispersion);
            var result = CloudInversion.invert(input, options, progress -> broadcaster.broadcast(child.update(progress)));
            logSummary(result, (System.nanoTime() - start) / 1e9);
            return result;
        } finally {
            broadcaster.broadcast(child.complete());
        }
    }

    private static double pixelShiftOf(ImageWrapper image) {
        return image.findMetadata(PixelShift.class)
                .map(PixelShift::pixelShift)
                .orElseThrow(() -> new IllegalArgumentException(message("cloud.model.function.no.shift")));
    }

    private static void logSummary(CloudInversionResult result, double seconds) {
        var stats = result.statistics();
        var summary = summarize(result);
        LOGGER.info(summary.getFirst());
        LOGGER.info(String.format(Locale.US, message("cloud.model.fit.summary"),
                stats.fittedPixels(), stats.diskPixels(), 100d * stats.threeComponentPixels() / Math.max(1, stats.fittedPixels()),
                stats.detectedPixels(), stats.noCloudPixels(), stats.brighterPixels(), stats.invalidPixels(), stats.poorFitPixels(), stats.medianReducedChi2(), 100 * stats.noise(), seconds));
        LOGGER.info(summary.getLast());
    }

    private static List<String> summarize(CloudInversionResult result) {
        var calibration = result.calibration();
        var stats = result.statistics();
        return List.of(
                String.format(Locale.US, message("cloud.model.calibration.summary"),
                        calibration.instrumentFwhm(), 100 * calibration.strayLight(), calibration.dispersionScale(), calibration.offset(), calibration.telluricExponent(), 100 * calibration.rmsResidual()),
                String.format(Locale.US, message("cloud.model.physics.summary"),
                        stats.meanTemperature(), stats.meanColumnMass(), stats.meanAbsoluteMassFluxVelocity(), stats.meanKineticEnergyVelocity(),
                        stats.meanAbsoluteKineticEnergyFluxVelocity(), stats.kineticEnergyFluxEstimate())
        );
    }

    /**
     * Emits the maps and the charts of an inversion.
     *
     * @param result the result
     * @param emitter the image emitter
     * @param debug whether to emit the debug maps
     */
    public static void emit(CloudInversionResult result, ImageEmitter emitter, boolean debug) {
        var maps = new ArrayList<>(DEFAULT_MAPS);
        if (debug) {
            maps.addAll(DEBUG_MAPS);
        }
        for (var map : maps) {
            var image = result.image(map);
            image.metadata().put(CloudInversionResult.class, result);
            image.metadata().put(CloudMap.class, map);
            emitter.newColorImage(GeneratedImageKind.CLOUD_MODEL, CATEGORY, CloudMapRenderer.titleOf(map), "cloud-" + map.id(), message("cloud.model.map." + map.id() + ".description"),
                    image,
                    mono -> CloudMapRenderer.colorize(result, map),
                    (g, img) -> CloudMapRenderer.paintLegend(g, result, map));
        }
        emitter.newColorImage(GeneratedImageKind.CLOUD_MODEL, CATEGORY, message("cloud.model.chart.velocities.title"), "cloud-velocities",
                message("cloud.model.chart.velocities.description"), CloudModelCharts.WIDTH, CloudModelCharts.HEIGHT, MutableMap.of(),
                () -> CloudModelCharts.velocityDistribution(result));
        emitter.newColorImage(GeneratedImageKind.CLOUD_MODEL, CATEGORY, message("cloud.model.chart.calibration.title"), "cloud-calibration",
                message("cloud.model.chart.calibration.description"), CloudModelCharts.WIDTH, CloudModelCharts.HEIGHT, MutableMap.of(),
                () -> CloudModelCharts.calibration(result));
    }
}
