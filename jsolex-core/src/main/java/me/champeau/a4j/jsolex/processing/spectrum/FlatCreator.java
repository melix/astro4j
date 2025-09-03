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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.FloatPrecisionImageConverter;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.apache.commons.math3.complex.Complex;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class FlatCreator {
    private static final int MAX_FRAME_COUNT = 50;

    public static final double DEFAULT_CUTOFF = 1d / 32d;
    public static final double DEFAULT_SLIT_DETECTION_SIGMA = 1.0;

    private final ImageConverter<float[][]> imageConverter;
    private final ProgressOperation rootOperation;
    private final Broadcaster broadcaster;

    public FlatCreator(ImageConverter<float[][]> imageConverter,
                       ProgressOperation rootOperation,
                       Broadcaster broadcaster) {
        this.imageConverter = imageConverter;
        this.rootOperation = rootOperation;
        this.broadcaster = broadcaster;
    }

    public ImageWrapper32 createMasterFlat(Path serFile) {
        try (var reader = SerFileReader.of(serFile.toFile())) {
            int frameCount = reader.header().frameCount();
            ImageGeometry geometry = reader.header().geometry();
            var imageMath = ImageMath.newInstance();
            int height = geometry.height();
            int width = geometry.width();
            var averageImage = new float[height][width];
            var counter = new AtomicInteger(0);
            var progressOperation = rootOperation.createChild(message("creating.master.flat"));
            var steps = Math.min(MAX_FRAME_COUNT, frameCount);
            float[][][] buffers = new float[steps][height][width];
            try {
                reader.seekFrame(0);
                for (int i = 0; i < steps; i++) {
                    broadcaster.broadcast(progressOperation.update(i / (double) steps));
                    var currentFrame = reader.currentFrame().data().array();
                    byte[] copy = new byte[currentFrame.length];
                    System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                    reader.nextFrame();
                    var buffer = imageConverter.createBuffer(geometry);
                    imageConverter.convert(i, ByteBuffer.wrap(copy), geometry, buffer);
                    buffers[i] = buffer;
                }
                // compute average image with sigma clipping
                computeAverageImageWithSigmaClipping(height, width, steps, buffers, averageImage);

            } catch (Exception ex) {
                throw ProcessingException.wrap(ex);
            } finally {
                broadcaster.broadcast(progressOperation.complete());
            }
            return new ImageWrapper32(width, height, averageImage, MutableMap.of());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void computeAverageImageWithSigmaClipping(int height,
                                                             int width,
                                                             int steps,
                                                             float[][][] buffers,
                                                             float[][] averageImage) {
        float nSigma = 3.0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float[] values = new float[steps];
                for (int i = 0; i < steps; i++) {
                    values[i] = buffers[i][y][x];
                }

                float sum = 0f;
                for (float v : values) sum += v;
                float mean = sum / steps;

                float variance = 0f;
                for (float v : values) variance += (v - mean) * (v - mean);
                float stdDev = (float) Math.sqrt(variance / steps);

                float lower = mean - nSigma * stdDev;
                float upper = mean + nSigma * stdDev;

                float clippedSum = 0f;
                int clippedCount = 0;
                for (float v : values) {
                    if (v >= lower && v <= upper) {
                        clippedSum += v;
                        clippedCount++;
                    }
                }

                averageImage[y][x] = clippedCount > 0 ? clippedSum / clippedCount : mean;
            }
        }
    }

    public static float[] prepareFlatFromAverage(float[][] averageImage, int width, int height, double cutoff, double slitDetectionSigma) {
        int paddedWidth = FFTSupport.nextPowerOf2(width);
        int paddedHeight = FFTSupport.nextPowerOf2(height);
        var paddedData = FFTSupport.padFromFloatArray(averageImage, width, height);

        var frequencyDomain = FFTSupport.fft2(paddedData);

        // Apply low-pass filter
        int limit = (int) (paddedWidth * cutoff);
        for (int y = 0; y < paddedHeight; y++) {
            for (int x = limit; x < paddedWidth - limit; x++) {
                frequencyDomain[y][x] = Complex.ZERO;
            }
        }

        var ifft = FFTSupport.ifft2(frequencyDomain);

        // Back to original size
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                averageImage[y][x] = (float) ifft[y][x].getReal();
            }
        }
        var totalAverage = 0d;
        for (int x = 0; x < width; x++) {
            var avg = 0d;
            for (int y = 0; y < height; y++) {
                avg += averageImage[y][x];
            }
            avg /= height;
            // compute stddev
            var stddev = 0d;
            for (int y = 0; y < height; y++) {
                var diff = averageImage[y][x] - avg;
                stddev += diff * diff;
            }
            stddev = Math.sqrt(stddev / height);
            // now perform new average with sigma clipping
            var prevAvg = avg;
            avg = 0d;
            int count = 0;
            for (int y = 0; y < height; y++) {
                var v = averageImage[y][x];
                if (v < prevAvg - 2.5 * stddev || v > prevAvg + 2.5 * stddev) {
                    continue;
                }
                count++;
                avg += v;
            }
            avg /= count;
            for (int y = 0; y < height; y++) {
                averageImage[y][x] = (float) avg;
            }
            totalAverage += avg;
        }
        totalAverage /= width;
        // compute the stddev
        var stddev = 0d;
        for (int x = 0; x < width; x++) {
            var diff = averageImage[0][x] - totalAverage;
            stddev += diff * diff;
        }
        stddev = Math.sqrt(stddev / width);
        var threshold = slitDetectionSigma * stddev;
        // now replace columns which are too far from the average with the average
        for (int x = 0; x < width; x++) {
            var v = averageImage[0][x];
            if (v < totalAverage - threshold || v > totalAverage + threshold) {
                for (int y = 0; y < height; y++) {
                    averageImage[y][x] = (float) totalAverage;
                }
            }
        }

        return averageImage[0];
    }

    public static void main(String[] args) {
        var converter = new FloatPrecisionImageConverter(
                new ChannelExtractingConverter(
                        new DemosaicingRGBImageConverter(
                                new BilinearDemosaicingStrategy(),
                                ColorMode.MONO
                        ),
                        GREEN
                )
        );
        var flatCreator = new FlatCreator(converter, ProgressOperation.root("", _ -> {
        }), Broadcaster.NO_OP);
        var serFile = Path.of("/home/cchampeau/Astro/flat-sky2/12_44_01.ser");
        var flat = flatCreator.createMasterFlat(serFile);
        ProcessParams processParams = ProcessParamsIO.loadDefaults();
        processParams = processParams.withExtraParams(processParams.extraParams().withImageFormats(Set.of(ImageFormat.FITS, ImageFormat.JPG)));
        new ImageSaver(LinearStrechingStrategy.DEFAULT, processParams).save(flat, Path.of("/home/cchampeau/Astro/flat/out2").toFile());
    }
}
