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

import java.util.function.DoubleUnaryOperator;

/**
 * Extracts surface data for 3D spectral line visualization from an average image.
 * The surface represents intensity as a function of position along the slit
 * and wavelength offset from the line center.
 */
public final class SpectralLineSurfaceDataExtractor {

    private SpectralLineSurfaceDataExtractor() {
    }

    /**
     * Extracts surface data from an average spectrum image using the full available width.
     *
     * @param avgImage the average image data [y][x]
     * @param polynomial the polynomial describing spectral line curvature
     * @param leftBorder left edge of valid spectrum region
     * @param rightBorder right edge of valid spectrum region
     * @param height image height
     * @param lambda0 the reference wavelength (line center)
     * @param dispersion the spectral dispersion
     * @param xResolution number of sample points along the slit
     * @param yResolution number of wavelength offset samples
     * @return the extracted surface data
     */
    public static SpectralLineSurfaceData extractSurfaceData(
            float[][] avgImage,
            DoubleUnaryOperator polynomial,
            int leftBorder,
            int rightBorder,
            int height,
            Wavelen lambda0,
            Dispersion dispersion,
            int xResolution,
            int yResolution
    ) {
        var minPoly = Double.MAX_VALUE;
        var maxPoly = -Double.MAX_VALUE;
        for (var x = leftBorder; x < rightBorder; x++) {
            var v = polynomial.applyAsDouble(x);
            minPoly = Math.min(v, minPoly);
            maxPoly = Math.max(v, maxPoly);
        }
        if (minPoly == Double.MAX_VALUE) {
            minPoly = 0;
        }
        if (maxPoly == -Double.MAX_VALUE) {
            maxPoly = height;
        }
        minPoly = Math.max(0, minPoly);
        maxPoly = Math.min(height, maxPoly);

        var minDistanceToEdge = Math.min(minPoly, height - maxPoly);
        double margin = 5;
        var availablePixels = Math.max(1, minDistanceToEdge - margin);
        var wavelengthRangeAngstroms = 2.0 * availablePixels * dispersion.angstromsPerPixel();

        var slitWidth = rightBorder - leftBorder;
        var step = Math.max(1, slitWidth / xResolution);
        var actualXRes = (slitWidth + step - 1) / step;

        var halfRange = wavelengthRangeAngstroms / 2.0;

        var slitPositions = new int[actualXRes];
        var wavelengthOffsets = new double[yResolution];
        var intensities = new float[actualXRes][yResolution];

        for (var i = 0; i < yResolution; i++) {
            var fraction = (double) i / (yResolution - 1);
            wavelengthOffsets[i] = -halfRange + fraction * wavelengthRangeAngstroms;
        }

        var minIntensity = Double.MAX_VALUE;
        var maxIntensity = -Double.MAX_VALUE;

        for (var xi = 0; xi < actualXRes; xi++) {
            var x = leftBorder + xi * step;
            if (x >= rightBorder) {
                x = rightBorder - 1;
            }
            slitPositions[xi] = x;

            var lineCenter = polynomial.applyAsDouble(x);

            for (var wi = 0; wi < yResolution; wi++) {
                var pixelShift = wavelengthOffsets[wi] / dispersion.angstromsPerPixel();
                var exactY = lineCenter + pixelShift;

                var intensity = interpolateIntensity(avgImage, x, exactY, height);
                intensities[xi][wi] = intensity;

                if (intensity < minIntensity) {
                    minIntensity = intensity;
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }
            }
        }

        return new SpectralLineSurfaceData(
                intensities,
                wavelengthOffsets,
                slitPositions,
                minIntensity,
                maxIntensity,
                lambda0,
                dispersion,
                SpectralLineSurfaceData.ViewMode.PROFILE
        );
    }

    private static float interpolateIntensity(float[][] data, int x, double exactY, int height) {
        if (exactY < 0 || exactY >= height - 1) {
            return 0;
        }
        var lowerY = (int) Math.floor(exactY);
        var upperY = (int) Math.ceil(exactY);
        if (upperY >= height) {
            upperY = height - 1;
        }
        if (lowerY == upperY) {
            return data[lowerY][x];
        }
        var lowerValue = data[lowerY][x];
        var upperValue = data[upperY][x];
        var fraction = (float) (exactY - lowerY);
        return lowerValue + (upperValue - lowerValue) * fraction;
    }
}
