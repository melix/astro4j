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

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.SerFileWriter;
import me.champeau.a4j.ser.bayer.RGBImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A class which is responsible for rewriting a SER file, centered on the
 * detected line, and cropped to a given height.
 */
public class SerFileTrimmer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerFileTrimmer.class);

    public static void trimFile(File inputSerFile,
                                File outputSerFile,
                                int firstFrame,
                                int lastFrame,
                                int pixelsUp,
                                int pixelsDown,
                                int minX,
                                int maxX,
                                DoubleUnaryOperator polynomial,
                                boolean verticalFlip,
                                Consumer<? super Double> progressListener) {
        var sd = System.nanoTime();
        try (var reader = SerFileReader.of(inputSerFile)) {
            var header = reader.header();
            int newHeight = pixelsUp + pixelsDown + 1;
            var converter = new RGBImageConverter();
            var geometry = header.geometry();
            var buffer = converter.createBuffer(geometry);
            var width = geometry.width();
            var height = geometry.height();
            var newWidth = maxX - minX + 1;
            var frameCount = header.frameCount();
            try (var writer = createWriter(outputSerFile, header, newWidth, newHeight)) {
                var first = Math.max(0, firstFrame);
                reader.seekFrame(first);
                var last = Math.min(lastFrame, frameCount - 1);

                for (int frameIdx = first; frameIdx <= last; frameIdx++) {
                    progressListener.accept((double) (frameIdx - first) / (last - first));
                    var frame = reader.currentFrame();
                    converter.convert(frameIdx, frame.data(), geometry, buffer);
                    if (verticalFlip) {
                        short[] flipped = converter.createBuffer(geometry);
                        for (int y = 0; y < height; y++) {
                            System.arraycopy(buffer, (height - y - 1) * width * 3, flipped, y * width * 3, width * 3);
                        }
                        buffer = flipped;
                    }
                    short[] finalBuffer = buffer;
                    writer.writeMonoFrame(frame.timestamp().orElse(null), new SerFileWriter.MonoFrameReader() {
                        private final int shift = verticalFlip ? pixelsDown : pixelsUp;
                        
                        @Override
                        public boolean isVFlip() {
                            return verticalFlip;
                        }

                        @Override
                        public short getPixel(int x, int y) {
                            double value = 0;
                            double weightSum = 0;
                            for (double dy = -1.0; dy <= 1.0; dy += 0.5) {
                                // The polynomial gives us the y coordinate in the source image for a given x in the target image
                                var sourceX = x + minX;
                                var centerYInSource = polynomial.applyAsDouble(sourceX);
                                var weight = Math.exp(-0.5 * dy * dy);  // Gaussian decay
                                var yInSource = centerYInSource - shift + y + dy;

                                int yFloor = Math.clamp((int) yInSource, 0, height - 1);
                                var frac = yInSource - yFloor;

                                int index1 = (yFloor * width + sourceX) * 3;
                                int index2 = (Math.min(yFloor + 1, height - 1) * width + sourceX) * 3;

                                // Perform interpolation
                                double v1 = finalBuffer[index1] & 0xFFFF;
                                double v2 = finalBuffer[index2] & 0xFFFF;
                                var interpolated = v1 * (1 - frac) + v2 * frac;
                                value += weight * Math.clamp(interpolated, 0, Constants.MAX_PIXEL_VALUE);
                                weightSum += weight;
                            }
                            if (weightSum > 0) {
                                value /= weightSum;
                            }
                            return (short) Math.round(value);
                        }
                    });

                    reader.nextFrame();
                }
            }
        } catch (Exception ex) {
            throw new ProcessingException("Error while rewriting SER file", ex);
        }
        var ed = System.nanoTime();
        var dur = Duration.ofNanos(ed - sd);
        LOGGER.info(message("ser.trim.duration"), dur.toSeconds(), dur.toMillisPart());
    }

    private static SerFileWriter createWriter(File outputSerFile, Header header, int newWidth, int newHeight) throws IOException {
        var initialGeometry = header.geometry();
        var newGeometry = new ImageGeometry(
            ColorMode.MONO,
            newWidth,
            newHeight,
            16,
            initialGeometry.imageEndian()
        );
        return new SerFileWriter(outputSerFile, header.metadata().utcDateTime(), header.camera(), newGeometry, header.metadata());
    }
}
