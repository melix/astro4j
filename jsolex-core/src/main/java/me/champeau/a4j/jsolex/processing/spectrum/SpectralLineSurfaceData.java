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

import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

/**
 * Holds data for a 3D spectral line surface visualization.
 * The surface represents intensity as a function of spatial position along the slit
 * (or frame number for evolution view) and wavelength offset from the line center.
 *
 * @param intensities 2D array of intensities [xIndex][wavelengthIndex]
 * @param wavelengthOffsets array of wavelength offsets from line center in Angstroms
 * @param xAxisPositions array of pixel positions (slit positions or frame numbers)
 * @param minIntensity minimum intensity value in the data
 * @param maxIntensity maximum intensity value in the data
 * @param centerWavelength the reference wavelength (line center)
 * @param dispersion the spectral dispersion
 * @param viewMode the visualization mode (PROFILE for slit position, EVOLUTION for frame number)
 */
public record SpectralLineSurfaceData(
        float[][] intensities,
        double[] wavelengthOffsets,
        int[] xAxisPositions,
        double minIntensity,
        double maxIntensity,
        Wavelen centerWavelength,
        Dispersion dispersion,
        ViewMode viewMode
) {
    public enum ViewMode {
        PROFILE,
        EVOLUTION
    }
    public int xAxisCount() {
        return xAxisPositions.length;
    }

    public int wavelengthCount() {
        return wavelengthOffsets.length;
    }

    public float normalizedIntensity(int xIndex, int wavelengthIndex) {
        float range = (float) (maxIntensity - minIntensity);
        if (range == 0) {
            return 0.5f;
        }
        return (intensities[xIndex][wavelengthIndex] - (float) minIntensity) / range;
    }
}
