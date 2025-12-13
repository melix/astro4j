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
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.List;

/**
 * Holds data for spherical tomography visualization.
 * Each shell represents a wavelength offset from the line center,
 * displayed as a concentric sphere in 3D space.
 *
 * @param shells list of shell data, ordered from innermost (far wing) to outermost (line core)
 * @param enhancedShells list of contrast-enhanced shell data (may be null if no enhancement)
 * @param centerWavelength the reference wavelength (line center)
 */
public record SphericalTomographyData(
        List<ShellData> shells,
        List<ShellData> enhancedShells,
        Wavelen centerWavelength
) {

    public SphericalTomographyData(List<ShellData> shells, Wavelen centerWavelength) {
        this(shells, null, centerWavelength);
    }

    public boolean hasEnhancedShells() {
        return enhancedShells != null && !enhancedShells.isEmpty();
    }

    /**
     * Data for a single shell in the tomography visualization.
     *
     * @param image the image for this wavelength offset
     * @param pixelShift the pixel shift from line center (negative = blue wing, positive = red wing)
     * @param wavelengthOffset the wavelength offset in Angstroms
     * @param normalizedRadius the normalized radius for display (1.0 = innermost, increasing outward)
     */
    public record ShellData(
            ImageWrapper32 image,
            double pixelShift,
            double wavelengthOffset,
            double normalizedRadius
    ) {
    }

    public int shellCount() {
        return shells.size();
    }

    public double minPixelShift() {
        return shells.stream()
                .mapToDouble(ShellData::pixelShift)
                .min()
                .orElse(0);
    }

    public double maxPixelShift() {
        return shells.stream()
                .mapToDouble(ShellData::pixelShift)
                .max()
                .orElse(0);
    }
}
