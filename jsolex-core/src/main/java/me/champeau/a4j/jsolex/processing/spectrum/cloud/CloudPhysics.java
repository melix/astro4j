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

/**
 * Computes the physical quantities derived from the cloud model parameters, following
 * equations 5 to 20 of Chae et al. (A&amp;A 706, A123, 2026).
 */
public final class CloudPhysics {
    public static final double DEFAULT_NONTHERMAL_SPEED_KMS = 8;

    private static final double HYDROGEN_MASS_G = 1.6735575e-24;
    private static final double BOLTZMANN_ERG_PER_K = 1.380649e-16;
    private static final double KMS_TO_CMS = 1e5;

    private CloudPhysics() {
    }

    /**
     * The physical quantities at one position.
     *
     * @param temperature the temperature, in K
     * @param columnMass the column mass density, in g cm⁻²
     * @param massFlux the column mass flux, in g cm⁻¹ s⁻¹
     * @param kineticEnergy the column kinetic energy, in erg cm⁻²
     * @param kineticEnergyFlux the column kinetic energy flux, in erg cm⁻¹ s⁻¹
     * @param massFluxVelocity the velocity associated with the mass flux, in km/s
     * @param kineticEnergyVelocity the velocity associated with the kinetic energy, in km/s
     * @param kineticEnergyFluxVelocity the velocity associated with the kinetic energy flux, in km/s
     * @param integratedOpacity the wavelength integrated optical thickness, in angstroms
     */
    public record Quantities(
            double temperature,
            double columnMass,
            double massFlux,
            double kineticEnergy,
            double kineticEnergyFlux,
            double massFluxVelocity,
            double kineticEnergyVelocity,
            double kineticEnergyFluxVelocity,
            double integratedOpacity
    ) {
    }

    /**
     * Computes the temperature from the Doppler width (eq. 5).
     *
     * @param dopplerWidth the Doppler width, in angstroms
     * @param lambda0 the rest wavelength of the line, in angstroms
     * @param nonThermalSpeed the non-thermal speed, in km/s
     * @return the temperature, in K
     */
    public static double temperature(double dopplerWidth, double lambda0, double nonThermalSpeed) {
        var widthSpeed = CloudModel.SPEED_OF_LIGHT_KMS * dopplerWidth / lambda0 * KMS_TO_CMS;
        var xi = nonThermalSpeed * KMS_TO_CMS;
        return HYDROGEN_MASS_G / (2 * BOLTZMANN_ERG_PER_K) * (widthSpeed * widthSpeed - xi * xi);
    }

    /**
     * Returns the ratio of the n = 2 hydrogen population to the total hydrogen population (eq. 11).
     */
    private static double excitedFraction(double temperature) {
        var logT = Math.log10(temperature) - 3.875;
        if (temperature <= 7500) {
            return Math.pow(10, -6.83 + 0.48 * logT);
        }
        return Math.pow(10, -6.83 - 3.06 * logT);
    }

    /**
     * Computes the physical quantities.
     *
     * @param parameters the cloud model parameters
     * @param lambda0 the rest wavelength of the line, in angstroms
     * @param nonThermalSpeed the non-thermal speed, in km/s
     * @return the physical quantities
     */
    public static Quantities compute(CloudParameters parameters, double lambda0, double nonThermalSpeed) {
        var width = parameters.dopplerWidth();
        var temperature = temperature(width, lambda0, nonThermalSpeed);
        var massPerOpacity = 1.60e-4 * (1e-7 / excitedFraction(Math.max(temperature, 1))) * width;
        var xi = nonThermalSpeed * KMS_TO_CMS;
        var xi2 = xi * xi;
        var mass = 0d;
        var massFlux = 0d;
        var energy = 0d;
        var energyFlux = 0d;
        var opacities = parameters.opacities();
        var velocities = parameters.velocities();
        for (var k = 0; k < 3; k++) {
            var m = massPerOpacity * opacities[k];
            var v = velocities[k] * KMS_TO_CMS;
            mass += m;
            massFlux += m * v;
            energy += m * (v * v + xi2 / 2);
            energyFlux += m * v * (v * v + 3 * xi2 / 2);
        }
        var vm = mass > 0 ? massFlux / mass / KMS_TO_CMS : 0;
        var ve = mass > 0 ? Math.sqrt(energy / mass) / KMS_TO_CMS : 0;
        var vf = mass > 0 ? Math.cbrt(energyFlux / mass) / KMS_TO_CMS : 0;
        return new Quantities(temperature, mass, massFlux, energy, energyFlux, vm, ve, vf, parameters.totalOpacity() * width);
    }
}
