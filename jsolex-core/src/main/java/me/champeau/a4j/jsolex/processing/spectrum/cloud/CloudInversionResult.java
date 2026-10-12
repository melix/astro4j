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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.HeliographicProjection;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of a cloud model inversion: one map per {@link CloudMap}, the calibration of the
 * observation, global statistics, and what is needed to show the fit at any position.
 */
public final class CloudInversionResult {
    /**
     * The minimum signal to noise ratio of the absorption for the velocities, the temperature,
     * the source function and the Doppler width to be considered as measured.
     */
    public static final double MIN_DETECTION = 5;

    private final int width;
    private final int height;
    private final Map<Class<?>, Object> metadata;
    private final EnumMap<CloudMap, float[][]> maps;
    private final InstrumentProfileEstimator.Calibration calibration;
    private final Statistics statistics;
    private final CalibrationProfile calibrationProfile;
    private final FitContext context;

    /**
     * Creates a result.
     *
     * @param width the width of the maps, in pixels of the images
     * @param height the height of the maps, in pixels of the images
     * @param metadata the metadata of the maps
     * @param maps the maps, one value per block of pixels when the images were averaged by blocks
     * @param calibration the calibration of the observation
     * @param statistics the statistics, in pixels of the images
     * @param calibrationProfile the profile at disk center compared with the atlas
     * @param context what is needed to fit a position again
     */
    CloudInversionResult(int width,
                         int height,
                         Map<Class<?>, Object> metadata,
                         EnumMap<CloudMap, float[][]> maps,
                         InstrumentProfileEstimator.Calibration calibration,
                         Statistics statistics,
                         CalibrationProfile calibrationProfile,
                         FitContext context) {
        this.width = width;
        this.height = height;
        this.metadata = metadata;
        this.maps = maps;
        this.calibration = calibration;
        this.statistics = statistics;
        this.calibrationProfile = calibrationProfile;
        this.context = context;
    }

    /**
     * Returns this result, computed on images averaged by blocks of pixels, at the size of the
     * original images: each pixel takes the value of the block it belongs to.
     *
     * @param fullWidth the width of the original images
     * @param fullHeight the height of the original images
     * @param fullMetadata the metadata of the maps
     * @param sources the original images, from which the blocks are averaged again to fit a position
     * @return the result at the size of the original images
     */
    CloudInversionResult atFullSize(int fullWidth, int fullHeight, Map<Class<?>, Object> fullMetadata, List<ImageWrapper> sources) {
        var area = binning() * binning();
        return new CloudInversionResult(fullWidth, fullHeight, fullMetadata, maps, calibration, statistics.scaled(area), calibrationProfile, context.withSources(sources));
    }

    /**
     * Returns the size of the blocks of pixels which were averaged before the inversion, 1 if none.
     */
    public int binning() {
        return context.binning();
    }

    /**
     * Global statistics, in pixels of the images.
     *
     * @param diskPixels the number of pixels of the disk above the minimum μ
     * @param fittedPixels the number of pixels where a cloud was found
     * @param detectedPixels the number of pixels where the absorption is strong enough to measure the velocities and the temperature
     * @param noCloudPixels the number of pixels without significant absorption
     * @param brighterPixels the number of pixels where the line is brighter than its background, as in plages, where the cloud model does not apply
     * @param threeComponentPixels the number of pixels where three components were retained
     * @param invalidPixels the number of pixels with an invalid profile, for example in sunspots
     * @param poorFitPixels the number of pixels rejected because of a poor fit
     * @param medianReducedChi2 the median reduced chi2 of the fitted pixels
     * @param noise the estimated noise, as a fraction of the continuum, at the continuum level
     * @param meanTemperature the mean temperature, in K, over the pixels where it could be measured
     * @param meanColumnMass the mean column mass, in g cm⁻², over the pixels where the temperature could be measured
     * @param meanAbsoluteMassFluxVelocity the mean absolute value of v_M, in km/s, over the detected pixels
     * @param meanKineticEnergyVelocity the mean value of v_E, in km/s, over the detected pixels
     * @param meanAbsoluteKineticEnergyFluxVelocity the mean absolute value of v_F, in km/s, over the detected pixels
     * @param surfaceKineticEnergy the column kinetic energy averaged over the surface, in erg cm⁻², counting the pixels without
     * absorbing material as zero, which is the quantity compared with radiative losses
     */
    public record Statistics(
            int diskPixels,
            int fittedPixels,
            int detectedPixels,
            int noCloudPixels,
            int brighterPixels,
            int threeComponentPixels,
            int invalidPixels,
            int poorFitPixels,
            double medianReducedChi2,
            double noise,
            double meanTemperature,
            double meanColumnMass,
            double meanAbsoluteMassFluxVelocity,
            double meanKineticEnergyVelocity,
            double meanAbsoluteKineticEnergyFluxVelocity,
            double surfaceKineticEnergy
    ) {
        public static final double KINETIC_ENERGY_REPLENISHMENT_SECONDS = 300;

        /**
         * Returns the kinetic energy flux obtained by assuming that the kinetic energy is
         * replenished every 5 minutes, as done by Chae et al. (2026), in erg cm⁻² s⁻¹.
         */
        public double kineticEnergyFluxEstimate() {
            return surfaceKineticEnergy / KINETIC_ENERGY_REPLENISHMENT_SECONDS;
        }

        Statistics scaled(int pixelsPerBlock) {
            return new Statistics(diskPixels * pixelsPerBlock, fittedPixels * pixelsPerBlock, detectedPixels * pixelsPerBlock,
                    noCloudPixels * pixelsPerBlock, brighterPixels * pixelsPerBlock, threeComponentPixels * pixelsPerBlock,
                    invalidPixels * pixelsPerBlock, poorFitPixels * pixelsPerBlock, medianReducedChi2, noise, meanTemperature,
                    meanColumnMass, meanAbsoluteMassFluxVelocity, meanKineticEnergyVelocity, meanAbsoluteKineticEnergyFluxVelocity,
                    surfaceKineticEnergy);
        }
    }

    /**
     * The mean profile at disk center compared with the calibrated atlas.
     *
     * @param offsets the wavelength of each sample, as an offset in angstroms from the line center
     * @param observed the observed profile, in units of the continuum
     * @param atlas the atlas convolved by the instrumental profile, plus stray light, in units of the continuum
     */
    public record CalibrationProfile(double[] offsets, double[] observed, double[] atlas) {
    }

    /**
     * The fit at one position.
     *
     * @param offsets the wavelength of each fitted sample, as an offset in angstroms from the line center
     * @param observed the observed profile, in units of the continuum, stray light removed
     * @param fitted the fitted model
     * @param background the background profile of the model
     * @param reference the reference profile at this position
     * @param parameters the parameters of the cloud
     * @param quantities the derived physical quantities, or null when no significant absorption was found
     * @param reducedChi2 the reduced chi2 of the fit
     * @param detection the signal to noise ratio of the absorption
     * @param brighter true if the line is brighter than its background, in which case the cloud model does not apply
     * @param rejected true if the position was rejected from the maps because the fit is too poor
     */
    public record PixelFit(
            double[] offsets,
            double[] observed,
            double[] fitted,
            double[] background,
            double[] reference,
            CloudParameters parameters,
            CloudPhysics.Quantities quantities,
            double reducedChi2,
            double detection,
            boolean brighter,
            boolean rejected
    ) {
        /**
         * Returns true if the absorption is strong enough for the velocities and the temperature to be measured.
         */
        public boolean isDetected() {
            return quantities != null && CloudInversionResult.isDetected(detection);
        }

        /**
         * Returns true if the temperature, and the quantities derived from it, could be measured.
         */
        public boolean isTemperatureMeasured() {
            return isDetected() && CloudInversionResult.isTemperatureMeasured(parameters.dopplerWidth(), quantities.temperature());
        }

        /**
         * Returns the velocity of the most shifted component which absorbs a noticeable amount of light, in km/s.
         */
        public double fastestVelocity() {
            return CloudInversion.fastestVelocity(parameters);
        }

        PixelFit markRejected() {
            return new PixelFit(offsets, observed, fitted, background, reference, parameters, quantities, reducedChi2, detection, brighter, true);
        }
    }

    /**
     * What is needed to fit a position again.
     *
     * @param sources the images, from which the profiles are read
     * @param disk the solar disk, in the grid of the inversion
     * @param continuum the continuum intensity, which the profiles are divided by
     * @param strayLight the stray light, as a fraction of the continuum, which is removed from the profiles
     * @param binning the size of the blocks of pixels of the sources which make one position of the grid of the inversion
     */
    record FitContext(
            double lambda0,
            double nonThermalSpeed,
            SpectralGrid grid,
            int[] fitIndices,
            List<ImageWrapper> sources,
            Ellipse disk,
            CloudInversion.Rings rings,
            CloudInversion.ShiftField shiftField,
            double continuum,
            double strayLight,
            double tripleThreshold,
            int binning
    ) {
        FitContext withShiftField(CloudInversion.ShiftField value) {
            return new FitContext(lambda0, nonThermalSpeed, grid, fitIndices, sources, disk, rings, value, continuum, strayLight, tripleThreshold, binning);
        }

        FitContext withRings(CloudInversion.Rings value) {
            return new FitContext(lambda0, nonThermalSpeed, grid, fitIndices, sources, disk, value, shiftField, continuum, strayLight, tripleThreshold, binning);
        }

        FitContext withSources(List<ImageWrapper> value) {
            return new FitContext(lambda0, nonThermalSpeed, grid, fitIndices, value, disk, rings, shiftField, continuum, strayLight, tripleThreshold, binning);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Map<Class<?>, Object> metadata() {
        return metadata;
    }

    /**
     * Returns the values of a map, one per pixel of the images.
     */
    public float[][] data(CloudMap map) {
        var source = maps.get(map);
        if (binning() == 1) {
            return source;
        }
        var expanded = new float[height][width];
        for (var y = 0; y < height; y++) {
            var row = source[gridRow(y)];
            for (var x = 0; x < width; x++) {
                expanded[y][x] = row[gridColumn(x)];
            }
        }
        return expanded;
    }

    /**
     * Returns the value of a map at one pixel of the images, without checking whether it is defined.
     */
    public float valueAt(CloudMap map, int x, int y) {
        return maps.get(map)[gridRow(y)][gridColumn(x)];
    }

    private int gridColumn(int x) {
        return Math.min(x / binning(), maps.get(CloudMap.STATUS)[0].length - 1);
    }

    private int gridRow(int y) {
        return Math.min(y / binning(), maps.get(CloudMap.STATUS).length - 1);
    }

    /**
     * Returns a map as an image, with 0 where the value is not defined.
     */
    public ImageWrapper32 image(CloudMap map) {
        var copy = new float[height][width];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (isDefined(map, x, y)) {
                    copy[y][x] = valueAt(map, x, y);
                }
            }
        }
        return new ImageWrapper32(width, height, copy, new HashMap<>(metadata));
    }

    /**
     * Returns true if the given map has a meaningful value at the given position. Where the line is
     * brighter than its background, only the background is defined, since the cloud model does not
     * apply. Velocities, temperature, source function and Doppler width are undefined where no
     * significant absorption was found or where the absorption is too weak to constrain them. The
     * temperature and the Doppler width are also undefined where the Doppler width stayed at one of
     * its bounds, and the temperature where it is not positive. Where a cloud was found, the maps
     * derived from the temperature, such as the column mass, are undefined where the temperature is.
     */
    public boolean isDefined(CloudMap map, int x, int y) {
        var status = valueAt(CloudMap.STATUS, x, y);
        if (map == CloudMap.REDUCED_CHI2 || map == CloudMap.STATUS) {
            return status != CloudMap.STATUS_NOT_FITTED;
        }
        if (status == CloudMap.STATUS_BRIGHTER) {
            return map.describesBackground();
        }
        if (status == CloudMap.STATUS_NO_CLOUD) {
            return !map.requiresCloud();
        }
        if (status != CloudMap.STATUS_FITTED) {
            return false;
        }
        if (map.requiresCloud() && !isDetected(valueAt(CloudMap.DETECTION, x, y))) {
            return false;
        }
        if (map == CloudMap.DOPPLER_WIDTH) {
            return !CloudModel.isAtWidthBound(valueAt(CloudMap.DOPPLER_WIDTH, x, y));
        }
        if (map == CloudMap.TEMPERATURE || map.requiresTemperature()) {
            return isTemperatureMeasured(valueAt(CloudMap.DOPPLER_WIDTH, x, y), valueAt(CloudMap.TEMPERATURE, x, y));
        }
        return true;
    }

    static boolean isDetected(double detection) {
        return detection >= MIN_DETECTION;
    }

    static boolean isTemperatureMeasured(double dopplerWidth, double temperature) {
        return !CloudModel.isAtWidthBound(dopplerWidth) && temperature > 0;
    }

    /**
     * Returns the heliographic coordinates of a position, with the same convention as the
     * heliographic grid drawn over the images: north up and east to the left once rotated by
     * the P angle. The maps are never rotated by the P angle.
     *
     * @param x the column
     * @param y the row
     * @return the coordinates, or empty if the position is outside of the disk or the disk is unknown
     */
    public Optional<HeliographicProjection.Coordinates> heliographicAt(int x, int y) {
        var ellipse = metadata.get(Ellipse.class) instanceof Ellipse e ? e : null;
        var solarParameters = metadata.get(SolarParameters.class) instanceof SolarParameters sp ? sp
                : metadata.get(ProcessParams.class) instanceof ProcessParams params && params.observationDetails().date() != null
                ? SolarParametersUtils.computeSolarParams(params.observationDetails().date().toLocalDateTime())
                : null;
        if (ellipse == null || solarParameters == null) {
            return Optional.empty();
        }
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        return HeliographicProjection.toHeliographic(x, y, center.a(), center.b(), (semiAxis.a() + semiAxis.b()) / 2,
                solarParameters.p(), solarParameters.b0());
    }

    public InstrumentProfileEstimator.Calibration calibration() {
        return calibration;
    }

    public Statistics statistics() {
        return statistics;
    }

    public CalibrationProfile calibrationProfile() {
        return calibrationProfile;
    }

    public double lambda0() {
        return context.lambda0();
    }

    public double nonThermalSpeed() {
        return context.nonThermalSpeed();
    }

    /**
     * Fits the profile at the given position again and returns the details of the fit. When the
     * images were averaged by blocks, the fit is the one of the block containing the position.
     *
     * @param x the column
     * @param y the row
     * @return the fit, or empty if the position was not fitted
     */
    public Optional<PixelFit> fitAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Optional.empty();
        }
        var status = valueAt(CloudMap.STATUS, x, y);
        if (!CloudMap.isAnalyzed(status) && status != CloudMap.STATUS_POOR_FIT) {
            return Optional.empty();
        }
        var fit = CloudInversion.refit(context, gridColumn(x), gridRow(y));
        return status == CloudMap.STATUS_POOR_FIT ? fit.map(PixelFit::markRejected) : fit;
    }
}
