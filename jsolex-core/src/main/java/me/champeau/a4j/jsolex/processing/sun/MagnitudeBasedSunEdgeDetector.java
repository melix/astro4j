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

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.stats.ChannelStats;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * An edge detector which performs an FFT of each line to compute
 * the magnitude of the signal and detect edges by comparing it
 * to a threshold.
 */
public class MagnitudeBasedSunEdgeDetector implements SunEdgeDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagnitudeBasedSunEdgeDetector.class);

    private final ImageConverter<float[]> imageConverter;
    private final Broadcaster broadcaster;

    private Integer startEdge;
    private Integer endEdge;
    private float[] averageImage;

    public MagnitudeBasedSunEdgeDetector(ImageConverter<float[]> imageConverter,
                                         Broadcaster broadcaster) {
        this.imageConverter = imageConverter;
        this.broadcaster = broadcaster;
    }

    @Override
    public void detectEdges(SerFileReader reader) {
        int frameCount = reader.header().frameCount();
        ImageGeometry geometry = reader.header().geometry();
        var magnitudes = new float[frameCount];
        var limbDetectionMessage = message("computing.average.image.limb.detect");
        var imageMath = ImageMath.newInstance();
        averageImage = new float[geometry.width() * geometry.height()];
        var counter = new AtomicInteger(0);
        try (var cpuExecutor = ParallelExecutor.newExecutor(1)) {
            try (var ioExecutor = ParallelExecutor.newExecutor(4 * Runtime.getRuntime().availableProcessors())) {
                for (int i = 0; i < frameCount; i++) {
                    int frameId = i;
                    broadcaster.broadcast(ProgressEvent.of(frameId / (double) frameCount, limbDetectionMessage));
                    // Because we're processing each frame concurrently we need
                    // to copy the frame buffer into a new array
                    var currentFrame = reader.currentFrame().data().array();
                    byte[] copy = new byte[currentFrame.length];
                    System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                    reader.nextFrame();
                    ioExecutor.submit(() -> {
                        var buffer = imageConverter.createBuffer(geometry);
                        imageConverter.convert(frameId, ByteBuffer.wrap(copy), geometry, buffer);
                        magnitudes[frameId] = MagnitudeDetectorSupport.minMax(buffer).b();
                        cpuExecutor.submit(() -> {
                            // we don't need to synchronize here since averaging is executed on a single thread
                            imageMath.incrementalAverage(buffer, averageImage, counter.incrementAndGet());
                        });
                    });
                }
            }
        } catch (Exception ex) {
            throw ProcessingException.wrap(ex);
        }
        var edges = MagnitudeDetectorSupport.findEdges(magnitudes, 50d);
        if (edges.a() >= 0) {
            startEdge = edges.a();
        }
        if (edges.b() >= 0) {
            endEdge = edges.b();
        }
        if (startEdge != null && endEdge != null) {
            // adjust average image value
            float r = (endEdge - startEdge) / (float) frameCount;
            for (int i = 0; i < averageImage.length; i++) {
                averageImage[i] /= r;
            }
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

    public float[] getAverageImage() {
        return averageImage;
    }
}
