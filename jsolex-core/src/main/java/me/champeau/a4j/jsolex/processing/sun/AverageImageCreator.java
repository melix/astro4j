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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Computes an average image from multiple frames in a SER file.
 * This class processes frames in parallel, filters out frames that are too dark,
 * and computes an incremental average to produce a single averaged image.
 */
public class AverageImageCreator {
    private static final int IO_PARALLELISM = 4 * Runtime.getRuntime().availableProcessors();

    private final ImageConverter<float[][]> imageConverter;
    private final ProgressOperation rootOperation;
    private final Broadcaster broadcaster;

    private float[][] averageImage;

    /**
     * Creates a new average image creator.
     *
     * @param imageConverter the converter for processing raw frame data
     * @param rootOperation the root progress operation for reporting progress
     * @param broadcaster the broadcaster for publishing progress events
     */
    public AverageImageCreator(ImageConverter<float[][]> imageConverter,
                               ProgressOperation rootOperation,
                               Broadcaster broadcaster) {
        this.imageConverter = imageConverter;
        this.rootOperation = rootOperation;
        this.broadcaster = broadcaster;
    }

    /**
     * Computes the average image from all frames in the SER file.
     * Frames with mean intensity below 50% of the maximum are excluded.
     * Processing is performed in parallel for efficiency.
     *
     * @param reader the SER file reader containing the frames to average
     */
    public void computeAverageImage(SerFileReader reader) {
        int frameCount = reader.header().frameCount();
        ImageGeometry geometry = reader.header().geometry();
        var limbDetectionMessage = message("computing.average.image.limb.detect");
        var imageMath = ImageMath.newInstance();
        averageImage = new float[geometry.height()][geometry.width()];
        var counter = new AtomicInteger(0);
        var averageLock = new ReentrantLock();
        var progressOperation = rootOperation.createChild(limbDetectionMessage);
        var maxMean = findMaxMeanBySampling(reader, frameCount, progressOperation, geometry, imageMath);
        var threshold = 0.5f * maxMean;
        try (var ioExecutor = ParallelExecutor.newExecutor(IO_PARALLELISM)) {
            reader.seekFrame(0);
            for (int i = 0; i < frameCount; i++) {
                int frameId = i;
                broadcaster.broadcast(progressOperation.update(frameId / (double) frameCount));
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                reader.nextFrame();
                ioExecutor.submit(() -> {
                    var buffer = imageConverter.createBuffer(geometry);
                    imageConverter.convert(frameId, ByteBuffer.wrap(copy), geometry, buffer);
                    var frameAvg = imageMath.averageOf(buffer);
                    if (frameAvg > threshold) {
                        averageLock.lock();
                        try {
                            imageMath.incrementalAverage(buffer, averageImage, counter.incrementAndGet());
                        } finally {
                            averageLock.unlock();
                        }
                    }
                });
            }
        } catch (Exception ex) {
            throw ProcessingException.wrap(ex);
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private float findMaxMeanBySampling(SerFileReader reader, int frameCount, ProgressOperation progressOperation, ImageGeometry geometry, ImageMath imageMath) {
        var sampling = Math.max(10, frameCount / 100);
        var maxMean = new float[]{0f};
        var maxLock = new ReentrantLock();
        broadcaster.broadcast(progressOperation.update(0 / (double) frameCount));
        try (var executor = ParallelExecutor.newExecutor(IO_PARALLELISM)) {
            for (int i = 0; i < frameCount; i += sampling) {
                reader.seekFrame(i);
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                int finalI = i;
                executor.submit(() -> {
                    var img = imageConverter.createBuffer(geometry);
                    imageConverter.convert(finalI, ByteBuffer.wrap(copy), geometry, img);
                    var mean = imageMath.averageOf(img);
                    maxLock.lock();
                    try {
                        if (mean > maxMean[0]) {
                            maxMean[0] = mean;
                        }
                    } finally {
                        maxLock.unlock();
                    }
                });
            }
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        return maxMean[0];
    }

    /**
     * Returns the computed average image.
     *
     * @return the average image as a 2D float array
     */
    public float[][] getAverageImage() {
        return averageImage;
    }
}
