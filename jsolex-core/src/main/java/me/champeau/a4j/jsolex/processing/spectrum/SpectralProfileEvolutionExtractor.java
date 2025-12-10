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
     * @param polyLeftBorder left edge where polynomial is valid (for wavelength range calculation)
     * @param polyRightBorder right edge where polynomial is valid (for wavelength range calculation)
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

        var minPolyY = Double.MAX_VALUE;
        var maxPolyY = -Double.MAX_VALUE;
        for (var x = polyLeftBorder; x < polyRightBorder; x++) {
            var y = polynomial.applyAsDouble(x);
            minPolyY = Math.min(minPolyY, y);
            maxPolyY = Math.max(maxPolyY, y);
        }
        var minDistanceToEdge = Math.min(minPolyY, height - maxPolyY);
        double margin = 5;
        var availablePixels = Math.max(1, minDistanceToEdge - margin);
        var wavelengthRangeAngstroms = 2.0 * availablePixels * dispersion.angstromsPerPixel();
        var halfRange = wavelengthRangeAngstroms / 2.0;

        var wavelengthOffsets = new double[wavelengthResolution];
        for (var i = 0; i < wavelengthResolution; i++) {
            var fraction = (double) i / (wavelengthResolution - 1);
            wavelengthOffsets[i] = -halfRange + fraction * wavelengthRangeAngstroms;
        }

        var frameIndices = new int[actualFrameRes];
        var intensities = new float[actualSlitRes][actualFrameRes][wavelengthResolution];

        var converter = new FastImageConverter(verticalFlip);
        var frameBuffer = converter.createBuffer(geometry);

        var minIntensity = Double.MAX_VALUE;
        var maxIntensity = -Double.MAX_VALUE;

        // Process each output frame bin
        for (var fi = 0; fi < actualFrameRes; fi++) {
            var frameBinStart = firstFrame + (int) (fi * frameBinSize);
            var frameBinEnd = firstFrame + (int) ((fi + 1) * frameBinSize);
            frameBinEnd = Math.min(frameBinEnd, lastFrame + 1);
            var framesInBin = frameBinEnd - frameBinStart;

            // Store center frame index for reference
            frameIndices[fi] = (frameBinStart + frameBinEnd) / 2;

            // Accumulate intensities from all frames in this bin
            var binAccum = new float[actualSlitRes][wavelengthResolution];

            for (var frameId = frameBinStart; frameId < frameBinEnd; frameId++) {
                reader.seekFrame(frameId);
                var frame = reader.currentFrame();
                converter.convert(frameId, frame.data(), geometry, frameBuffer);

                for (var si = 0; si < actualSlitRes; si++) {
                    var slitBinStart = slitLeftBorder + (int) (si * slitBinSize);
                    var slitBinEnd = slitLeftBorder + (int) ((si + 1) * slitBinSize);
                    slitBinEnd = Math.min(slitBinEnd, slitRightBorder);
                    var slitsInBin = slitBinEnd - slitBinStart;

                    for (var wi = 0; wi < wavelengthResolution; wi++) {
                        var pixelShift = wavelengthOffsets[wi] / dispersion.angstromsPerPixel();

                        float slitSum = 0;
                        for (var x = slitBinStart; x < slitBinEnd; x++) {
                            var lineCenter = polynomial.applyAsDouble(x);
                            var exactY = lineCenter + pixelShift;
                            slitSum += interpolateIntensity(frameBuffer, x, exactY, height);
                        }
                        binAccum[si][wi] += slitSum / slitsInBin;
                    }
                }
            }

            // Store averaged values
            for (var si = 0; si < actualSlitRes; si++) {
                for (var wi = 0; wi < wavelengthResolution; wi++) {
                    var avgIntensity = binAccum[si][wi] / framesInBin;
                    intensities[si][fi][wi] = avgIntensity;

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

        return new SpectralEvolution4DData(
                intensities,
                wavelengthOffsets,
                frameIndices,
                slitPositions,
                minIntensity,
                maxIntensity,
                lambda0,
                dispersion
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
