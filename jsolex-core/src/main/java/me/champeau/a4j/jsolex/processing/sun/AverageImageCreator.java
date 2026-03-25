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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Computes an average image from multiple frames in a SER file.
 * This class processes frames in parallel, filters out frames that are too dark,
 * and computes an incremental average to produce a single averaged image.
 */
public class AverageImageCreator {
    private static final int IO_PARALLELISM = Runtime.getRuntime().availableProcessors();

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
        int height = geometry.height();
        int width = geometry.width();
        var progressOperation = rootOperation.createChild(limbDetectionMessage);
        var maxMean = findMaxMeanBySampling(reader, frameCount, progressOperation, geometry, imageMath);
        var threshold = 0.5f * maxMean;

        // Per-thread sum accumulators (double precision to avoid overflow) to eliminate lock contention
        var accumulators = new ConcurrentLinkedQueue<double[][]>();
        var counters = new ConcurrentLinkedQueue<int[]>();
        var localAccumulator = ThreadLocal.withInitial(() -> {
            var sum = new double[height][width];
            accumulators.add(sum);
            var count = new int[]{0};
            counters.add(count);
            return new Object[]{sum, count};
        });

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
                        var local = localAccumulator.get();
                        var sum = (double[][]) local[0];
                        var count = (int[]) local[1];
                        for (int y = 0; y < height; y++) {
                            var sumRow = sum[y];
                            var bufRow = buffer[y];
                            for (int x = 0; x < width; x++) {
                                sumRow[x] += bufRow[x];
                            }
                        }
                        count[0]++;
                    }
                });
            }
        } catch (Exception ex) {
            throw ProcessingException.wrap(ex);
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }

        // Merge per-thread sums into the final average
        averageImage = new float[height][width];
        int totalCount = 0;
        for (var count : counters) {
            totalCount += count[0];
        }
        if (totalCount > 0) {
            // Accumulate all thread sums in double precision
            var merged = new double[height][width];
            for (var sum : accumulators) {
                for (int y = 0; y < height; y++) {
                    var mergedRow = merged[y];
                    var sumRow = sum[y];
                    for (int x = 0; x < width; x++) {
                        mergedRow[x] += sumRow[x];
                    }
                }
            }
            // Convert to float average
            double invCount = 1.0 / totalCount;
            for (int y = 0; y < height; y++) {
                var avgRow = averageImage[y];
                var mergedRow = merged[y];
                for (int x = 0; x < width; x++) {
                    avgRow[x] = (float) (mergedRow[x] * invCount);
                }
            }
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
