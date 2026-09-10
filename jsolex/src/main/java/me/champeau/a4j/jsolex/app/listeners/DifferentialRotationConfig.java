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

import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;

import java.util.Locale;

/**
 * Configuration parameters for differential rotation measurement.
 */
public record DifferentialRotationConfig(
    double limbLongitudeDeg,
    double longitudeHalfRangeDeg,
    double longitudeStepDeg,
    double latitudeStepDeg,
    double smoothingWindowDeg,
    double voigtFitHalfWidthAngstroms,
    SampleRejectionMethod sampleRejectionMethod,
    NoiseReductionMethod noiseReductionMethod
) {
    /**
     * Default heliographic longitude (degrees) of the East/West limb columns used
     * for spectral profile extraction.
     */
    public static final double DEFAULT_LIMB_LONGITUDE_DEG = 75.0;
    /**
     * Default half-range (degrees) around the limb longitude over which multiple
     * longitudes are sampled.
     */
    public static final double DEFAULT_LONGITUDE_HALF_RANGE_DEG = 13.0;
    /**
     * Default step size (degrees) between consecutive longitude samples.
     */
    public static final double DEFAULT_LONGITUDE_STEP_DEG = 2.0;
    /**
     * Default latitude resolution (degrees) of the scan.
     */
    public static final double DEFAULT_LATITUDE_STEP_DEG = 2.0;
    /**
     * Default half-width (degrees) of the smoothing window applied to the velocity curve.
     */
    public static final double DEFAULT_SMOOTHING_WINDOW_DEG = 5.0;
    /**
     * Default half-width (Angstroms) of the spectral window for Voigt profile fitting.
     */
    public static final double DEFAULT_VOIGT_FIT_HALF_WIDTH_ANGSTROMS = 2.0;

    /**
     * Default configuration with reasonable values.
     */
    public static DifferentialRotationConfig defaultConfig() {
        return new DifferentialRotationConfig(
            DEFAULT_LIMB_LONGITUDE_DEG,
            DEFAULT_LONGITUDE_HALF_RANGE_DEG,
            DEFAULT_LONGITUDE_STEP_DEG,
            DEFAULT_LATITUDE_STEP_DEG,
            DEFAULT_SMOOTHING_WINDOW_DEG,
            DEFAULT_VOIGT_FIT_HALF_WIDTH_ANGSTROMS,
            new SampleRejectionMethod.None(),
            NoiseReductionMethod.MEDIAN
        );
    }

    /**
     * Validates the configuration and returns an error message if invalid, or null if valid.
     */
    public String validate() {
        if (limbLongitudeDeg < 10 || limbLongitudeDeg > 85) {
            return common("differential.rotation.config.error.limb.longitude");
        }
        var maxHalfRange = 90.0 - limbLongitudeDeg;
        if (longitudeHalfRangeDeg < 1 || longitudeHalfRangeDeg > maxHalfRange) {
            return String.format(Locale.US, common("differential.rotation.config.error.longitude.halfrange"),
                maxHalfRange, limbLongitudeDeg);
        }
        if (longitudeStepDeg < 0.5 || longitudeStepDeg > longitudeHalfRangeDeg) {
            return common("differential.rotation.config.error.longitude.step");
        }
        if (latitudeStepDeg < 0.5 || latitudeStepDeg > 10) {
            return common("differential.rotation.config.error.latitude.step");
        }
        if (smoothingWindowDeg < 1 || smoothingWindowDeg > 20) {
            return common("differential.rotation.config.error.smoothing");
        }
        if (voigtFitHalfWidthAngstroms < 0.5 || voigtFitHalfWidthAngstroms > 5) {
            return common("differential.rotation.config.error.voigt.halfwidth");
        }
        return null;
    }

    /**
     * Returns a warning message if the configuration is valid but suboptimal, or null if optimal.
     */
    public String warning() {
        if (smoothingWindowDeg < 2 * latitudeStepDeg) {
            return String.format(Locale.US,
                common("differential.rotation.config.warning.smoothing"),
                smoothingWindowDeg, latitudeStepDeg);
        }
        return null;
    }

    /**
     * Computes the maximum allowed longitude half-range for a given limb longitude.
     */
    public static double maxHalfRangeFor(double limbLongitude) {
        return 90.0 - limbLongitude;
    }

    private static String common(String key) {
        return I18N.string(JSolEx.class, "common", key);
    }
}
