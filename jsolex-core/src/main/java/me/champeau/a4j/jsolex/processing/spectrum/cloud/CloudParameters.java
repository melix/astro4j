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
 * The parameters of the cloud model at one position. A single component cloud is
 * represented by its central component, with the blue and red components having
 * no opacity.
 *
 * @param sourceFunction the source function of the cloud, in units of the disk center continuum
 * @param dopplerWidth the Doppler width of the cloud, in angstroms
 * @param opacities the optical thickness of the blue, central and red components
 * @param velocities the line of sight velocity of the blue, central and red components, in km/s, negative towards the observer
 * @param backgroundScale the scale applied to the reference profile to obtain the background profile
 * @param backgroundWidth the relative narrowing of the background profile compared with the reference profile
 * @param components the number of components: 0 when no significant absorption was found, 1 or 3
 */
public record CloudParameters(
        double sourceFunction,
        double dopplerWidth,
        double[] opacities,
        double[] velocities,
        double backgroundScale,
        double backgroundWidth,
        int components
) {
    public static final int BLUE = 0;
    public static final int CENTER = 1;
    public static final int RED = 2;

    /**
     * Returns the parameters of a position where no significant absorption was found.
     */
    public static CloudParameters noCloud(double backgroundScale, double backgroundWidth) {
        return new CloudParameters(0, 0, new double[3], new double[3], backgroundScale, backgroundWidth, 0);
    }

    public double totalOpacity() {
        return opacities[BLUE] + opacities[CENTER] + opacities[RED];
    }
}
