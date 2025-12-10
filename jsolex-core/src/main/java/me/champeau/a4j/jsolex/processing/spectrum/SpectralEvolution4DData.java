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
 * Holds 4D spectral evolution data for animated visualization.
 * The data represents intensity as a function of frame number, wavelength offset,
 * and slit position (the 4th dimension for animation).
 *
 * @param intensities 3D array [slitPositionIndex][frameIndex][wavelengthIndex]
 * @param wavelengthOffsets array of wavelength offsets from line center in Angstroms
 * @param frameIndices array of frame numbers
 * @param slitPositions array of pixel positions along the slit
 * @param minIntensity minimum intensity value in the data
 * @param maxIntensity maximum intensity value in the data
 * @param centerWavelength the reference wavelength (line center)
 * @param dispersion the spectral dispersion
 */
public record SpectralEvolution4DData(
        float[][][] intensities,
        double[] wavelengthOffsets,
        int[] frameIndices,
        int[] slitPositions,
        double minIntensity,
        double maxIntensity,
        Wavelen centerWavelength,
        Dispersion dispersion
) {
    public int slitPositionCount() {
        return slitPositions.length;
    }

    public int frameCount() {
        return frameIndices.length;
    }

    public int wavelengthCount() {
        return wavelengthOffsets.length;
    }

    public SpectralLineSurfaceData toSurfaceData(int slitIndex) {
        return new SpectralLineSurfaceData(
                intensities[slitIndex],
                wavelengthOffsets,
                frameIndices,
                minIntensity,
                maxIntensity,
                centerWavelength,
                dispersion,
                SpectralLineSurfaceData.ViewMode.EVOLUTION
        );
    }

    public SpectralLineSurfaceData toInterpolatedSurfaceData(double slitFraction) {
        if (slitPositions.length == 1) {
            return toSurfaceData(0);
        }

        var exactIndex = slitFraction * (slitPositions.length - 1);
        var lowerIndex = (int) Math.floor(exactIndex);
        var upperIndex = (int) Math.ceil(exactIndex);

        if (lowerIndex == upperIndex || upperIndex >= slitPositions.length) {
            return toSurfaceData(Math.min(lowerIndex, slitPositions.length - 1));
        }

        var t = (float) (exactIndex - lowerIndex);
        var interpolated = new float[frameIndices.length][wavelengthOffsets.length];

        for (var fi = 0; fi < frameIndices.length; fi++) {
            for (var wi = 0; wi < wavelengthOffsets.length; wi++) {
                var lower = intensities[lowerIndex][fi][wi];
                var upper = intensities[upperIndex][fi][wi];
                interpolated[fi][wi] = lower + t * (upper - lower);
            }
        }

        return new SpectralLineSurfaceData(
                interpolated,
                wavelengthOffsets,
                frameIndices,
                minIntensity,
                maxIntensity,
                centerWavelength,
                dispersion,
                SpectralLineSurfaceData.ViewMode.EVOLUTION
        );
    }

    public enum SliceMode {
        SLIT,
        FRAME,
        WAVELENGTH
    }

    public int getSliceCount(SliceMode mode) {
        return switch (mode) {
            case SLIT -> slitPositionCount();
            case FRAME -> frameCount();
            case WAVELENGTH -> wavelengthCount();
        };
    }

    public int getSliceIndex(SliceMode mode, double fraction) {
        var count = getSliceCount(mode);
        var index = (int) Math.round(fraction * (count - 1));
        return Math.max(0, Math.min(index, count - 1));
    }

    public String getSliceLabel(SliceMode mode, int index) {
        return switch (mode) {
            case SLIT -> slitPositions[index] + " px";
            case FRAME -> "Frame " + frameIndices[index];
            case WAVELENGTH -> String.format(java.util.Locale.US, "%.2f Å", centerWavelength.angstroms() + wavelengthOffsets[index]);
        };
    }

    public double getCenterWavelengthFraction() {
        var bestIndex = 0;
        var minAbsOffset = Math.abs(wavelengthOffsets[0]);
        for (var i = 1; i < wavelengthOffsets.length; i++) {
            var absOffset = Math.abs(wavelengthOffsets[i]);
            if (absOffset < minAbsOffset) {
                minAbsOffset = absOffset;
                bestIndex = i;
            }
        }
        return (double) bestIndex / (wavelengthOffsets.length - 1);
    }

    public SpectralLineSurfaceData toSurfaceData(SliceMode mode, double fraction) {
        return switch (mode) {
            case SLIT -> toInterpolatedSurfaceData(fraction);
            case FRAME -> toFrameSlice(fraction);
            case WAVELENGTH -> toWavelengthSlice(fraction);
        };
    }

    private SpectralLineSurfaceData toFrameSlice(double frameFraction) {
        var frameCount = frameIndices.length;
        var exactIndex = frameFraction * (frameCount - 1);
        var lowerIndex = (int) Math.floor(exactIndex);
        var upperIndex = (int) Math.ceil(exactIndex);

        if (lowerIndex == upperIndex || upperIndex >= frameCount) {
            var fi = Math.min(lowerIndex, frameCount - 1);
            var slice = new float[slitPositions.length][wavelengthOffsets.length];
            for (var si = 0; si < slitPositions.length; si++) {
                System.arraycopy(intensities[si][fi], 0, slice[si], 0, wavelengthOffsets.length);
            }
            return new SpectralLineSurfaceData(
                    slice,
                    wavelengthOffsets,
                    slitPositions,
                    minIntensity,
                    maxIntensity,
                    centerWavelength,
                    dispersion,
                    SpectralLineSurfaceData.ViewMode.PROFILE
            );
        }

        var t = (float) (exactIndex - lowerIndex);
        var interpolated = new float[slitPositions.length][wavelengthOffsets.length];

        for (var si = 0; si < slitPositions.length; si++) {
            for (var wi = 0; wi < wavelengthOffsets.length; wi++) {
                var lower = intensities[si][lowerIndex][wi];
                var upper = intensities[si][upperIndex][wi];
                interpolated[si][wi] = lower + t * (upper - lower);
            }
        }

        return new SpectralLineSurfaceData(
                interpolated,
                wavelengthOffsets,
                slitPositions,
                minIntensity,
                maxIntensity,
                centerWavelength,
                dispersion,
                SpectralLineSurfaceData.ViewMode.PROFILE
        );
    }

    private SpectralLineSurfaceData toWavelengthSlice(double wavelengthFraction) {
        var wlCount = wavelengthOffsets.length;
        var exactIndex = wavelengthFraction * (wlCount - 1);
        var lowerIndex = (int) Math.floor(exactIndex);
        var upperIndex = (int) Math.ceil(exactIndex);

        if (lowerIndex == upperIndex || upperIndex >= wlCount) {
            var wi = Math.min(lowerIndex, wlCount - 1);
            var slice = new float[frameIndices.length][slitPositions.length];
            for (var fi = 0; fi < frameIndices.length; fi++) {
                for (var si = 0; si < slitPositions.length; si++) {
                    slice[fi][si] = intensities[si][fi][wi];
                }
            }
            return new SpectralLineSurfaceData(
                    slice,
                    toDoubleArray(slitPositions),
                    frameIndices,
                    minIntensity,
                    maxIntensity,
                    centerWavelength,
                    dispersion,
                    SpectralLineSurfaceData.ViewMode.EVOLUTION
            );
        }

        var t = (float) (exactIndex - lowerIndex);
        var interpolated = new float[frameIndices.length][slitPositions.length];

        for (var fi = 0; fi < frameIndices.length; fi++) {
            for (var si = 0; si < slitPositions.length; si++) {
                var lower = intensities[si][fi][lowerIndex];
                var upper = intensities[si][fi][upperIndex];
                interpolated[fi][si] = lower + t * (upper - lower);
            }
        }

        return new SpectralLineSurfaceData(
                interpolated,
                toDoubleArray(slitPositions),
                frameIndices,
                minIntensity,
                maxIntensity,
                centerWavelength,
                dispersion,
                SpectralLineSurfaceData.ViewMode.EVOLUTION
        );
    }

    private static double[] toDoubleArray(int[] intArray) {
        var result = new double[intArray.length];
        for (var i = 0; i < intArray.length; i++) {
            result[i] = intArray[i];
        }
        return result;
    }

    public double[] getAverageSpectralProfile() {
        var profile = new double[wavelengthOffsets.length];
        var slitCount = slitPositions.length;
        var frameCount = frameIndices.length;
        double total = slitCount * frameCount;

        for (var wi = 0; wi < wavelengthOffsets.length; wi++) {
            double sum = 0;
            for (var si = 0; si < slitCount; si++) {
                for (var fi = 0; fi < frameCount; fi++) {
                    sum += intensities[si][fi][wi];
                }
            }
            profile[wi] = sum / total;
        }
        return profile;
    }

    public double getWavelengthFractionForPixelShift(double pixelShift) {
        // Convert pixel shift to angstrom offset
        var angstromOffset = pixelShift * dispersion.angstromsPerPixel();

        // Find the closest wavelength index
        var bestIndex = 0;
        var minDiff = Math.abs(wavelengthOffsets[0] - angstromOffset);
        for (var i = 1; i < wavelengthOffsets.length; i++) {
            var diff = Math.abs(wavelengthOffsets[i] - angstromOffset);
            if (diff < minDiff) {
                minDiff = diff;
                bestIndex = i;
            }
        }
        return (double) bestIndex / (wavelengthOffsets.length - 1);
    }

}

