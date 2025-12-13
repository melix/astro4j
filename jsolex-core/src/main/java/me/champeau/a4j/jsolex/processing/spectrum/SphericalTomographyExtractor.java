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

import me.champeau.a4j.jsolex.processing.params.AutoStretchParams;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts data for spherical tomography visualization from shift images.
 */
public final class SphericalTomographyExtractor {

    private SphericalTomographyExtractor() {
    }

    /**
     * Extracts spherical tomography data from a map of shift images.
     *
     * @param shiftImages        map of pixel shift to image wrapper
     * @param dispersion         the spectral dispersion
     * @param centerWavelength   the center wavelength
     * @param radialExaggeration factor to multiply radius differences (typically 1.0-3.0)
     * @return the tomography data
     */
    public static SphericalTomographyData extract(
            Map<PixelShift, ImageWrapper> shiftImages,
            Dispersion dispersion,
            Wavelen centerWavelength,
            double radialExaggeration
    ) {
        if (shiftImages.isEmpty()) {
            throw new IllegalArgumentException("No shift images available");
        }

        var sortedShifts = shiftImages.keySet().stream()
                .sorted(Comparator.comparingDouble(PixelShift::pixelShift))
                .toList();

        var minShift = sortedShifts.getFirst().pixelShift();
        var maxShift = sortedShifts.getLast().pixelShift();


        var maxAbsShift = Math.max(Math.abs(minShift), Math.abs(maxShift));

        var shells = sortedShifts.stream().map(pixelShift -> {
                    var image = shiftImages.get(pixelShift).unwrapToMemory();
                    if (image instanceof ImageWrapper32 i32) {
                        var shift = pixelShift.pixelShift();
                        var wavelengthOffset = shift * dispersion.angstromsPerPixel();

                        // Line core (shift ~0) forms highest in chromosphere, should be outermost
                        // Wings (larger |shift|) form lower, should be innermost
                        var absShift = Math.abs(shift);
                        var normalizedPosition = maxAbsShift > 0 ? 1.0 - (absShift / maxAbsShift) : 0.5;
                        var normalizedRadius = 1.0 + normalizedPosition * 0.2 * radialExaggeration;

                        return new SphericalTomographyData.ShellData(
                                i32,
                                shift,
                                wavelengthOffset,
                                normalizedRadius
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        return new SphericalTomographyData(shells, centerWavelength);
    }

    /**
     * Extracts spherical tomography data from a map of shift images with contrast enhancement.
     * Enhanced images are created during extraction, not lazily.
     */
    public static SphericalTomographyData extract(
            Map<PixelShift, ImageWrapper> shiftImages,
            Dispersion dispersion,
            Wavelen centerWavelength,
            double radialExaggeration,
            ContrastEnhancement contrastEnhancement,
            ClaheParams claheParams,
            AutoStretchParams autoStretchParams
    ) {
        var basicData = extract(shiftImages, dispersion, centerWavelength, radialExaggeration);

        if (contrastEnhancement == null) {
            return basicData;
        }

        // Create enhanced versions of all shell images
        var enhancedShells = basicData.shells().stream()
                .parallel()
                .map(shell -> {
                    var enhancedImage = createEnhancedImage(shell.image(), contrastEnhancement, claheParams, autoStretchParams);
                    if (enhancedImage instanceof ImageWrapper32 i32) {
                        return new SphericalTomographyData.ShellData(
                                i32,
                                shell.pixelShift(),
                                shell.wavelengthOffset(),
                                shell.normalizedRadius()
                        );
                    }
                    return null;
                }).filter(Objects::nonNull).toList();

        return new SphericalTomographyData(basicData.shells(), enhancedShells, centerWavelength);
    }

    private static ImageWrapper createEnhancedImage(
            ImageWrapper original,
            ContrastEnhancement contrastEnhancement,
            ClaheParams claheParams,
            AutoStretchParams autoStretchParams
    ) {
        if (!(original.unwrapToMemory() instanceof ImageWrapper32(var width, var height, var data, var metadata))) {
            return original;
        }

        // Create a deep copy of the image data
        var copyData = new float[height][width];
        for (var y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, copyData[y], 0, width);
        }

        var copy = new ImageWrapper32(width, height, copyData, metadata);

        // Apply the appropriate stretching strategy
        if (contrastEnhancement == ContrastEnhancement.CLAHE && claheParams != null) {
            ClaheStrategy.of(claheParams).stretch(copy);
        } else if (contrastEnhancement == ContrastEnhancement.AUTOSTRETCH && autoStretchParams != null) {
            new AutohistogramStrategy(
                    autoStretchParams.gamma(),
                    true,
                    autoStretchParams.bgThreshold(),
                    autoStretchParams.protusStretch()
            ).stretch(copy);
        }

        return copy;
    }

}
