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
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * An edge detector which compute the magnitude of the signal and detect edges by comparing it
 * to a threshold.
 */
public class AverageImageCreator {
    private static final int IO_PARALLELISM = 4 * Runtime.getRuntime().availableProcessors();

    private final ImageConverter<float[][]> imageConverter;
    private final Broadcaster broadcaster;

    private float[][] averageImage;

    public AverageImageCreator(ImageConverter<float[][]> imageConverter,
                               Broadcaster broadcaster) {
        this.imageConverter = imageConverter;
        this.broadcaster = broadcaster;
    }

    public void computeAverageImage(SerFileReader reader) {
        int frameCount = reader.header().frameCount();
        ImageGeometry geometry = reader.header().geometry();
        var limbDetectionMessage = message("computing.average.image.limb.detect");
        var imageMath = ImageMath.newInstance();
        averageImage = new float[geometry.height()][geometry.width()];
        var counter = new AtomicInteger(0);
        // we start with sampling frames to find the one with the maximum intensity
        var maxMean = findMaxMeanBySampling(reader, frameCount, limbDetectionMessage, geometry, imageMath);
        // We're going to ignore frames which are too dark
        var threshold = 0.5f * maxMean;
        try (var cpuExecutor = Executors.newFixedThreadPool(1)) {
            try (var ioExecutor = ParallelExecutor.newExecutor(IO_PARALLELISM)) {
                reader.seekFrame(0);
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
                        var frameAvg = imageMath.averageOf(buffer);
                        if (frameAvg > threshold) {
                            cpuExecutor.submit(() -> {
                                // we don't need to synchronize here since averaging is executed on a single thread
                                imageMath.incrementalAverage(buffer, averageImage, counter.incrementAndGet());
                            });
                        }
                    });
                }
            }
        } catch (Exception ex) {
            throw ProcessingException.wrap(ex);
        }
    }

    private float findMaxMeanBySampling(SerFileReader reader, int frameCount, String limbDetectionMessage, ImageGeometry geometry, ImageMath imageMath) {
        var sampling = Math.max(10, frameCount / 100);
        var means = new float[1 + frameCount / sampling];
        broadcaster.broadcast(ProgressEvent.of(0 / (double) frameCount, limbDetectionMessage));
        try (var executor = ParallelExecutor.newExecutor(IO_PARALLELISM)) {
            for (int i = 0; i < frameCount; i += sampling) {
                reader.seekFrame(i);
                var img = imageConverter.createBuffer(geometry);
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                int finalI = i;
                executor.submit(() -> {
                    imageConverter.convert(finalI, ByteBuffer.wrap(copy), geometry, img);
                    means[finalI/sampling] = imageMath.averageOf(img);
                });
            }
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        var maxMean = 0f;
        for (float mean : means) {
            maxMean = Math.max(maxMean, mean);
        }
        return maxMean;
    }

    public float[][] getAverageImage() {
        return averageImage;
    }
}
