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

import me.champeau.a4j.jsolex.processing.ser.FastImageConverter;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;

import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;

/**
 * Extracts spectral profile evolution data for 3D visualization.
 * Shows how the spectral line profile at a fixed slit position changes across frames.
 */
public final class SpectralProfileEvolutionExtractor {

    private SpectralProfileEvolutionExtractor() {
    }

    /**
     * Extracts 4D spectral profile evolution data from a SER file.
     * Returns intensity data for all combinations of (frame, wavelength, slitPosition).
     *
     * @param reader the SER file reader positioned at the start
     * @param geometry the image geometry
     * @param polynomial the polynomial describing spectral line curvature
     * @param slitLeftBorder left edge of slit sampling region (can include prominences)
     * @param slitRightBorder right edge of slit sampling region (can include prominences)
     * @param polyLeftBorder left edge of the reliable polynomial region (used to size the wavelength range)
     * @param polyRightBorder right edge of the reliable polynomial region (used to size the wavelength range)
     * @param firstFrame first frame to process
     * @param lastFrame last frame to process (inclusive)
     * @param lambda0 the reference wavelength (line center)
     * @param dispersion the spectral dispersion
     * @param spatialResolution number of samples for spatial dimensions (slit and frame)
     * @param wavelengthResolution number of wavelength samples
     * @param verticalFlip whether to flip frames vertically
     * @param progressCallback optional callback for progress updates (frame number)
     * @return the extracted 4D evolution data
     */
    public static SpectralEvolution4DData extractEvolution4D(
            SerFileReader reader,
            ImageGeometry geometry,
            DoubleUnaryOperator polynomial,
            int slitLeftBorder,
            int slitRightBorder,
            int polyLeftBorder,
            int polyRightBorder,
            int firstFrame,
            int lastFrame,
            Wavelen lambda0,
            Dispersion dispersion,
            int spatialResolution,
            int wavelengthResolution,
            boolean verticalFlip,
            IntConsumer progressCallback
    ) {
        var height = geometry.height();
        var totalFrameCount = lastFrame - firstFrame + 1;
        var slitWidth = slitRightBorder - slitLeftBorder;

        // Calculate actual output resolutions (capped to input size)
        var actualSlitRes = Math.min(spatialResolution, slitWidth);
        var actualFrameRes = Math.min(spatialResolution, totalFrameCount);

        // Calculate bin sizes for averaging
        var slitBinSize = (double) slitWidth / actualSlitRes;
        var frameBinSize = (double) totalFrameCount / actualFrameRes;

        // Compute slit positions (center of each bin)
        var slitPositions = new int[actualSlitRes];
        for (var i = 0; i < actualSlitRes; i++) {
            var binStart = slitLeftBorder + (int) (i * slitBinSize);
            var binEnd = slitLeftBorder + (int) ((i + 1) * slitBinSize);
            slitPositions[i] = (binStart + binEnd) / 2;
        }

        // Size the wavelength range so that every column in the reliable polynomial region stays
        // within the frame at every sampled shift. Using the nearest-edge distance per side keeps the
        // range asymmetric (matching the real profile) while guaranteeing no slice is left without data.
        var minPolyY = Double.MAX_VALUE;
        var maxPolyY = -Double.MAX_VALUE;
        for (var x = polyLeftBorder; x < polyRightBorder; x++) {
            var y = polynomial.applyAsDouble(x);
            minPolyY = Math.min(minPolyY, y);
            maxPolyY = Math.max(maxPolyY, y);
        }
        double margin = 5;
        var minPixelShift = margin - minPolyY;
        var maxPixelShift = (height - 1 - margin) - maxPolyY;
        if (maxPixelShift <= minPixelShift) {
            minPixelShift = 0;
            maxPixelShift = 0;
        }
        var minOffset = minPixelShift * dispersion.angstromsPerPixel();
        var maxOffset = maxPixelShift * dispersion.angstromsPerPixel();
        var offsetRange = maxOffset - minOffset;

        var wavelengthOffsets = new double[wavelengthResolution];
        for (var i = 0; i < wavelengthResolution; i++) {
            var fraction = (double) i / (wavelengthResolution - 1);
            wavelengthOffsets[i] = minOffset + fraction * offsetRange;
        }

        var frameIndices = new int[actualFrameRes];
        var intensities = new float[actualSlitRes][actualFrameRes][wavelengthResolution];

        var converter = new FastImageConverter(verticalFlip);
        var frameBuffer = converter.createBuffer(geometry);

        var minIntensity = Double.MAX_VALUE;
        var maxIntensity = -Double.MAX_VALUE;

        var profileSum = new double[wavelengthResolution];
        var profileCount = new long[wavelengthResolution];

        // Process each output frame bin
        for (var fi = 0; fi < actualFrameRes; fi++) {
            var frameBinStart = firstFrame + (int) (fi * frameBinSize);
            var frameBinEnd = firstFrame + (int) ((fi + 1) * frameBinSize);
            frameBinEnd = Math.min(frameBinEnd, lastFrame + 1);

            // Store center frame index for reference
            frameIndices[fi] = (frameBinStart + frameBinEnd) / 2;

            // Accumulate intensities from all frames in this bin, counting only in-frame samples
            var binAccum = new double[actualSlitRes][wavelengthResolution];
            var binCount = new int[actualSlitRes][wavelengthResolution];

            for (var frameId = frameBinStart; frameId < frameBinEnd; frameId++) {
                reader.seekFrame(frameId);
                var frame = reader.currentFrame();
                converter.convert(frameId, frame.data(), geometry, frameBuffer);

                for (var si = 0; si < actualSlitRes; si++) {
                    var slitBinStart = slitLeftBorder + (int) (si * slitBinSize);
                    var slitBinEnd = slitLeftBorder + (int) ((si + 1) * slitBinSize);
                    slitBinEnd = Math.min(slitBinEnd, slitRightBorder);

                    for (var wi = 0; wi < wavelengthResolution; wi++) {
                        var pixelShift = wavelengthOffsets[wi] / dispersion.angstromsPerPixel();

                        double slitSum = 0;
                        var slitCount = 0;
                        for (var x = slitBinStart; x < slitBinEnd; x++) {
                            var lineCenter = polynomial.applyAsDouble(x);
                            var exactY = lineCenter + pixelShift;
                            var value = interpolateIntensity(frameBuffer, x, exactY, height);
                            if (!Float.isNaN(value)) {
                                slitSum += value;
                                slitCount++;
                            }
                        }
                        if (slitCount > 0) {
                            binAccum[si][wi] += slitSum / slitCount;
                            binCount[si][wi]++;
                        }
                    }
                }
            }

            // Store averaged values, flagging cells with no in-frame sample for later backfill
            for (var si = 0; si < actualSlitRes; si++) {
                for (var wi = 0; wi < wavelengthResolution; wi++) {
                    if (binCount[si][wi] == 0) {
                        intensities[si][fi][wi] = Float.NaN;
                        continue;
                    }
                    var avgIntensity = (float) (binAccum[si][wi] / binCount[si][wi]);
                    intensities[si][fi][wi] = avgIntensity;
                    profileSum[wi] += avgIntensity;
                    profileCount[wi]++;

                    if (avgIntensity < minIntensity) {
                        minIntensity = avgIntensity;
                    }
                    if (avgIntensity > maxIntensity) {
                        maxIntensity = avgIntensity;
                    }
                }
            }

            if (progressCallback != null) {
                progressCallback.accept(fi);
            }
        }

        if (minIntensity == Double.MAX_VALUE) {
            minIntensity = 0;
            maxIntensity = 0;
        }

        // Backfill empty cells with the floor value so the surface never produces out-of-range
        // normalized intensities (off-disk columns can fall off the frame at the wing extremes).
        var floor = (float) minIntensity;
        for (var si = 0; si < actualSlitRes; si++) {
            for (var fi = 0; fi < actualFrameRes; fi++) {
                var row = intensities[si][fi];
                for (var wi = 0; wi < wavelengthResolution; wi++) {
                    if (Float.isNaN(row[wi])) {
                        row[wi] = floor;
                    }
                }
            }
        }

        var averageSpectralProfile = new double[wavelengthResolution];
        for (var wi = 0; wi < wavelengthResolution; wi++) {
            averageSpectralProfile[wi] = profileCount[wi] > 0 ? profileSum[wi] / profileCount[wi] : minIntensity;
        }

        return new SpectralEvolution4DData(
                intensities,
                wavelengthOffsets,
                frameIndices,
                slitPositions,
                averageSpectralProfile,
                minIntensity,
                maxIntensity,
                lambda0,
                dispersion
        );
    }

    private static float interpolateIntensity(float[][] data, int x, double exactY, int height) {
        if (exactY < 0 || exactY >= height - 1) {
            return Float.NaN;
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
