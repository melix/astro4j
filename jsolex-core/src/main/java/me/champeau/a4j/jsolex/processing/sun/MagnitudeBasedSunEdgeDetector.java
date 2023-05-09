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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.stats.ChannelStats;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;

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
        var magnitudes = new float[frameCount];
        int imageSize = geometry.width() * geometry.height() * 5; // 4 bytes per pixel for float[] + 1 byte for the intermediate buffer
        int maxMemoryUse = 100 * 1024 * 1024;
        int maxParalleism = Math.max(1, Math.min(maxMemoryUse/imageSize, 16 * Runtime.getRuntime().availableProcessors()));
        try (var executor = ParallelExecutor.newExecutor(maxParalleism)) {
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
                    magnitudes[frameId] = MagnitudeDetectorSupport.minMax(buffer).b();
                });
            }
        } catch (Exception ex) {
            throw new ProcessingException(ex);
        }
        var edges = MagnitudeDetectorSupport.findEdges(magnitudes, 50d);
        if (edges.a() >= 0) {
            startEdge = edges.a();
        }
        if (edges.b() >= 0) {
            endEdge = edges.b();
        }
    }

    @Override
    public void ifEdgesDetected(BiConsumer<Integer, Integer> consumer, Runnable orElse) {
        if (startEdge != null && endEdge != null) {
            consumer.accept(startEdge, endEdge);
        } else {
            orElse.run();
        }
    }

    @Override
    public Optional<ChannelStats[]> getFrameStats() {
        return Optional.empty();
    }
}
