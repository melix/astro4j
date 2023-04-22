/*
 * Copyright 2003-2021 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.stats.ChannelStats;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.fft.FastFourierTransform;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;

/**
 * An edge detector which performs an FFT of each line to compute
 * the magnitude of the signal and detect edges by comparing it
 * to a threshold.
 */
public class MagnitudeBasedSunEdgeDetector implements SunEdgeDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagnitudeBasedSunEdgeDetector.class);

    private final ImageConverter<float[]> imageConverter;

    private Integer startEdge;
    private Integer endEdge;

    public MagnitudeBasedSunEdgeDetector(ImageConverter<float[]> imageConverter) {
        this.imageConverter = imageConverter;
    }

    @Override
    public void detectEdges(SerFileReader reader) {
        int frameCount = reader.header().frameCount();
        ImageGeometry geometry = reader.header().geometry();
        int width = geometry.width();
        var magnitudes = new double[frameCount];
        try (var executor = ParallelExecutor.newExecutor()) {
            for (int i = 0; i < frameCount; i++) {
                int frameId = i;
                // Because we're processing each frame concurrently we need
                // to copy the frame buffer into a new array
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                reader.nextFrame();
                executor.submit(() -> {
                    var buffer = imageConverter.createBuffer(geometry);
                    imageConverter.convert(frameId, ByteBuffer.wrap(copy), geometry, buffer);
                    float[] line = new float[nextPowerOf2(width)];
                    int padding = line.length - width;
                    System.arraycopy(buffer, 0, line, padding, width);
                    var fft = FastFourierTransform.ofReal(line);
                    double max = Double.MIN_VALUE;
                    var im = fft.imaginary();
                    for (int k = 0; k < line.length; k++) {
                        double magnitude = Math.sqrt(line[k] * line[k] + im[k] * im[k]);
                        if (magnitude > max) {
                            max = magnitude;
                        }
                    }
                    magnitudes[frameId] = max;
                });
            }
        } catch (Exception ex) {
            throw new ProcessingException(ex);
        }
        double min = Arrays.stream(magnitudes).min().orElse(0d);
        double max = Arrays.stream(magnitudes).max().orElse(0d);
        double amplitude = max - min;
        double threshold = amplitude / 50d;
        for (int i = 0; i < magnitudes.length; i++) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold && startEdge == null) {
                startEdge = i;
                break;
            }
        }
        for (int i = magnitudes.length - 1; i >= 0; i--) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold && endEdge == null) {
                endEdge = i;
                break;
            }
        }
    }

    @Override
    public void ifEdgesDetected(BiConsumer<Integer, Integer> consumer) {
        if (startEdge != null && endEdge != null) {
            consumer.accept(startEdge, endEdge);
        }
    }

    @Override
    public Optional<ChannelStats[]> getFrameStats() {
        return Optional.empty();
    }
}
