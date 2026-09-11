/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.server;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns the raw pixels of a frame, as a capture software holds them in memory,
 * into a mono image. Colour frames are averaged over their channels; frames
 * carrying a Bayer mosaic are kept at full resolution, each phase of the mosaic
 * being brought to the same mean so that the mosaic does not ripple the spectrum.
 */
final class FrameDecoder {
    private static final float EIGHT_TO_SIXTEEN_BITS = 256f;

    private FrameDecoder() {
    }

    /**
     * The pixel formats a frame can be sent in, named after the colour spaces of
     * the capture softwares. Multi-byte samples are little-endian.
     */
    enum PixelFormat {
        MONO8(1, 1, 1, false),
        MONO12(2, 2, 1, false),
        MONO16(2, 2, 1, false),
        RGB24(3, 1, 3, false),
        RGB32(4, 1, 3, false),
        RGB48(6, 2, 3, false),
        RAW8(1, 1, 1, true),
        RAW12(2, 2, 1, true),
        RAW16(2, 2, 1, true);

        private final int bytesPerPixel;
        private final int bytesPerSample;
        private final int channels;
        private final boolean bayer;

        PixelFormat(int bytesPerPixel, int bytesPerSample, int channels, boolean bayer) {
            this.bytesPerPixel = bytesPerPixel;
            this.bytesPerSample = bytesPerSample;
            this.channels = channels;
            this.bayer = bayer;
        }

        int bytesPerPixel() {
            return bytesPerPixel;
        }

        static Optional<PixelFormat> parse(String name) {
            if (name == null) {
                return Optional.empty();
            }
            var normalized = name.trim().toUpperCase(Locale.US);
            return Arrays.stream(values())
                    .filter(format -> format.name().equals(normalized))
                    .findFirst();
        }

        static String supported() {
            return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
        }
    }

    /**
     * Decodes a frame.
     *
     * @param body the pixels, row after row, without padding between rows
     * @param width the frame width
     * @param height the frame height
     * @param format the pixel format
     * @return the mono image, in the 16-bit range
     * @throws IllegalArgumentException when the dimensions or the body length do not match the format
     */
    static ImageWrapper32 decode(byte[] body, int width, int height, PixelFormat format) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid frame dimensions " + width + "x" + height);
        }
        var expected = (long) width * height * format.bytesPerPixel;
        if (body.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " bytes for a " + width + "x" + height + " " + format + " frame but received " + body.length);
        }
        var buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        var padding = format.bytesPerPixel - format.channels * format.bytesPerSample;
        var scale = format.bytesPerSample == 1 ? EIGHT_TO_SIXTEEN_BITS : 1f;
        var data = new float[height][width];
        for (int y = 0; y < height; y++) {
            var row = data[y];
            for (int x = 0; x < width; x++) {
                var sum = 0f;
                for (int c = 0; c < format.channels; c++) {
                    sum += format.bytesPerSample == 1 ? buffer.get() & 0xFF : buffer.getShort() & 0xFFFF;
                }
                buffer.position(buffer.position() + padding);
                row[x] = scale * sum / format.channels;
            }
        }
        if (format.bayer) {
            equalizeMosaic(data, width, height);
        }
        return new ImageWrapper32(width, height, data, MutableMap.of());
    }

    private static void equalizeMosaic(float[][] data, int width, int height) {
        var sums = new double[4];
        var counts = new long[4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var phase = 2 * (y & 1) + (x & 1);
                sums[phase] += data[y][x];
                counts[phase]++;
            }
        }
        var total = 0d;
        var count = 0L;
        for (int phase = 0; phase < 4; phase++) {
            total += sums[phase];
            count += counts[phase];
        }
        if (count == 0 || total <= 0) {
            return;
        }
        var mean = total / count;
        var gains = new float[4];
        for (int phase = 0; phase < 4; phase++) {
            var phaseMean = counts[phase] == 0 ? 0 : sums[phase] / counts[phase];
            gains[phase] = phaseMean > 0 ? (float) (mean / phaseMean) : 1f;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] *= gains[2 * (y & 1) + (x & 1)];
            }
        }
    }
}
