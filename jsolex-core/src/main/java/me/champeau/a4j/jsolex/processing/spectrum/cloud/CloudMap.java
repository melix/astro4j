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

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * The maps produced by the cloud model inversion.
 */
public enum CloudMap {
    MASS_FLUX_VELOCITY("vm", "km/s"),
    FASTEST_VELOCITY("vmax", "km/s"),
    KINETIC_ENERGY_VELOCITY("ve", "km/s"),
    KINETIC_ENERGY_FLUX_VELOCITY("vf", "km/s"),
    TEMPERATURE("t", "K"),
    COLUMN_MASS("m", "g/cm2"),
    MASS_FLUX("fm", "g/cm/s"),
    KINETIC_ENERGY("ek", "erg/cm2"),
    KINETIC_ENERGY_FLUX("fk", "erg/cm/s"),
    INTEGRATED_OPACITY("tauw", "Angstrom"),
    SOURCE_FUNCTION("s", ""),
    DOPPLER_WIDTH("w", "Angstrom"),
    BACKGROUND_SCALE("bg", ""),
    BACKGROUND_WIDTH("bgw", ""),
    OPACITY_BLUE("tau_b", ""),
    OPACITY_CENTER("tau_c", ""),
    OPACITY_RED("tau_r", ""),
    VELOCITY_BLUE("v_b", "km/s"),
    VELOCITY_CENTER("v_c", "km/s"),
    VELOCITY_RED("v_r", "km/s"),
    COMPONENTS("ncomp", ""),
    REDUCED_CHI2("chi2", ""),
    DETECTION("snr", ""),
    STATUS("status", "");

    public static final float STATUS_NOT_FITTED = 0;
    public static final float STATUS_FITTED = 1;
    public static final float STATUS_INVALID_PROFILE = 2;
    public static final float STATUS_POOR_FIT = 3;
    public static final float STATUS_NO_CLOUD = 4;
    public static final float STATUS_BRIGHTER = 5;

    private final String id;
    private final String unit;

    CloudMap(String id, String unit) {
        this.id = id;
        this.unit = unit;
    }

    public String id() {
        return id;
    }

    public String unit() {
        return unit;
    }

    /**
     * Returns true if the map has no meaning where no significant absorption was found.
     */
    public boolean requiresCloud() {
        return switch (this) {
            case MASS_FLUX_VELOCITY, FASTEST_VELOCITY, KINETIC_ENERGY_VELOCITY, KINETIC_ENERGY_FLUX_VELOCITY, TEMPERATURE,
                 SOURCE_FUNCTION, DOPPLER_WIDTH, VELOCITY_BLUE, VELOCITY_CENTER, VELOCITY_RED -> true;
            default -> false;
        };
    }

    /**
     * Returns true if the map is derived from the temperature, through the fraction of hydrogen
     * atoms which can absorb H-alpha light, and therefore has no meaning where the temperature
     * could not be measured.
     */
    public boolean requiresTemperature() {
        return switch (this) {
            case COLUMN_MASS, MASS_FLUX, KINETIC_ENERGY, KINETIC_ENERGY_FLUX -> true;
            default -> false;
        };
    }

    /**
     * Returns true if the map describes the background rather than the cloud.
     */
    public boolean describesBackground() {
        return this == BACKGROUND_SCALE || this == BACKGROUND_WIDTH;
    }

    /**
     * Returns true if the status is the one of a position where the profile was analyzed: a cloud
     * was found, no significant absorption was found, or the line is brighter than its background.
     */
    public static boolean isAnalyzed(float status) {
        return status == STATUS_FITTED || status == STATUS_NO_CLOUD || status == STATUS_BRIGHTER;
    }

    public static CloudMap fromId(String id) {
        for (var map : values()) {
            if (map.id.equalsIgnoreCase(id)) {
                return map;
            }
        }
        throw new IllegalArgumentException(String.format(message("cloud.model.unknown.map"), id));
    }
}
