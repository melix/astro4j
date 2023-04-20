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
import me.champeau.a4j.jsolex.processing.stats.ImageStatsComputer;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;

public class SimpleSunEdgeDetector implements SunEdgeDetector {
    private final ImageStatsComputer statsComputer;
    private final ImageConverter<float[]> imageConverter;

    private Integer startEdge;
    private Integer endEdge;
    private ChannelStats[] stats;

    public SimpleSunEdgeDetector(ImageStatsComputer statsComputer, ImageConverter<float[]> imageConverter) {
        this.statsComputer = statsComputer;
        this.imageConverter = imageConverter;
    }

    @Override
    public void detectEdges(SerFileReader reader) {
        int frameCount = reader.header().frameCount();
        ImageGeometry geometry = reader.header().geometry();
        int width = geometry.width();
        int height = geometry.height();
        stats = new ChannelStats[frameCount];
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
                    ChannelStats[] channelStats = statsComputer.computeStats(buffer, width, height);
                    // We assume mono images so we only keep one channel
                    ChannelStats monoStat = channelStats[0];
                    stats[frameId] = monoStat;
                });
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Integer edgeStart = null;
        Integer edgeEnd = null;

        for (int i = 0; i < frameCount; i++) {
            ChannelStats monoStat = stats[i];
            double frameAvg = monoStat.avg();
            double frameStddev = monoStat.stddev();
            if (frameStddev > 0.42 * frameAvg) {
                if (edgeStart == null) {
                    edgeStart = i;
                }
                edgeEnd = i;
            }
            reader.nextFrame();
        }
        startEdge = edgeStart;
        endEdge = edgeEnd;
    }

    @Override
    public void ifEdgesDetected(BiConsumer<Integer, Integer> consumer) {
        if (startEdge != null && endEdge != null) {
            consumer.accept(startEdge, endEdge);
        }
    }

    @Override
    public Optional<ChannelStats[]> getFrameStats() {
        return Optional.ofNullable(stats);
    }
}
