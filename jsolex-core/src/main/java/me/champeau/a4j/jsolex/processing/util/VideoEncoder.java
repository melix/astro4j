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
package me.champeau.a4j.jsolex.processing.util;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Utility class for encoding videos from a stream of BufferedImage frames.
 * This class provides a memory-efficient streaming API that processes frames one at a time.
 */
public final class VideoEncoder {

    private VideoEncoder() {
    }

    private static void encodeFrame(SequenceEncoder encoder, BufferedImage frame, int width, int height) throws IOException {
        var rgb = new byte[3 * width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = frame.getRGB(x, y);
                int idx = y * width + x;
                rgb[3 * idx] = (byte) (((argb >> 16) & 0xFF) - 128);
                rgb[3 * idx + 1] = (byte) (((argb >> 8) & 0xFF) - 128);
                rgb[3 * idx + 2] = (byte) ((argb & 0xFF) - 128);
            }
        }
        var pic = new Picture(width, height, new byte[][]{rgb}, null, ColorSpace.RGB, 0, null);
        encoder.encodeNativeFrame(pic);
    }

    /**
     * Encodes frames to multiple formats simultaneously, capturing each frame only once.
     *
     * @param baseFileName base file name without extension
     * @param formats set of animation formats to encode
     * @param frameCount total number of frames
     * @param fps frames per second (for MP4)
     * @param gifDelayMs delay between frames in milliseconds (for GIF)
     * @param frameSupplier function that provides the frame at a given index (returns null to cancel)
     * @param progressCallback optional callback for progress updates (receives fraction 0.0-1.0)
     * @return list of output files created
     * @throws IOException if encoding fails
     */
    public static List<File> encodeToMultipleFormats(String baseFileName,
                                                     Set<AnimationFormat> formats,
                                                     int frameCount,
                                                     int fps,
                                                     int gifDelayMs,
                                                     IntFunction<BufferedImage> frameSupplier,
                                                     Consumer<Double> progressCallback) throws IOException {
        if (frameCount == 0 || formats.isEmpty()) {
            return List.of();
        }

        var outputFiles = new ArrayList<File>();
        SequenceEncoder mp4Encoder = null;
        AnimatedGifWriter gifWriter = null;
        File mp4File;
        File gifFile;
        int width = 0;
        int height = 0;

        try {
            for (int i = 0; i < frameCount; i++) {
                var frame = frameSupplier.apply(i);
                if (frame == null) {
                    break;
                }

                if (i == 0) {
                    width = frame.getWidth();
                    height = frame.getHeight();
                    if (width % 2 == 1) {
                        width--;
                    }
                    if (height % 2 == 1) {
                        height--;
                    }

                    if (formats.contains(AnimationFormat.MP4)) {
                        mp4File = new File(baseFileName + ".mp4");
                        mp4Encoder = SequenceEncoder.createWithFps(NIOUtils.writableChannel(mp4File), new Rational(fps, 1));
                        outputFiles.add(mp4File);
                    }
                    if (formats.contains(AnimationFormat.GIF)) {
                        gifFile = new File(baseFileName + ".gif");
                        var gifOutputStream = new FileImageOutputStream(gifFile);
                        gifWriter = new AnimatedGifWriter(gifOutputStream, BufferedImage.TYPE_INT_RGB, gifDelayMs, true);
                        outputFiles.add(gifFile);
                    }
                }

                if (mp4Encoder != null) {
                    encodeFrame(mp4Encoder, frame, width, height);
                }
                if (gifWriter != null) {
                    gifWriter.writeToSequence(frame);
                }

                if (progressCallback != null) {
                    progressCallback.accept((double) (i + 1) / frameCount);
                }
            }
        } finally {
            try {
                if (mp4Encoder != null) {
                    mp4Encoder.finish();
                }
            } finally {
                if (gifWriter != null) {
                    gifWriter.close();
                }
            }
        }

        return outputFiles;
    }
}
