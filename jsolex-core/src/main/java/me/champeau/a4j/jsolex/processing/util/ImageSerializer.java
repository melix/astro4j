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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Reads and writes the raw pixel data of an {@link ImageWrapper} using a compact
 * binary representation. The layout groups the {@link Float#BYTES} bytes of each
 * float by significance position (byte-plane shuffle): the high-order plane
 * (sign + exponent) is highly repetitive across a smooth solar image, so grouping
 * like-bytes together lets a downstream deflater find much longer matches than it
 * would on the natural interleaved layout.
 * <p>
 * This serializer performs no compression itself: callers wrap the streams with
 * the compression scheme of their choice (a {@link java.util.zip.DeflaterOutputStream}
 * for raw image files, a zip entry for session exports, etc.).
 */
public final class ImageSerializer {
    private static final byte MONO = 0;
    private static final byte RGB = 2;

    private ImageSerializer() {
    }

    /**
     * Writes the pixel data of the given image to the stream. Only the dimensions
     * and channel values are written; metadata is the caller's responsibility.
     *
     * @param out   the destination stream
     * @param image the image to write (must be an {@link ImageWrapper32} or {@link RGBImage})
     */
    public static void write(DataOutputStream out, ImageWrapper image) throws IOException {
        if (image instanceof ImageWrapper32 mono) {
            var width = mono.width();
            var height = mono.height();
            out.writeByte(MONO);
            out.writeInt(height);
            out.writeInt(width);
            var pixels = mono.pixels();
            var row = new float[width];
            var rowBytes = new byte[width * Float.BYTES];
            var shuffled = new byte[rowBytes.length];
            var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
            for (int y = 0; y < height; y++) {
                pixels.getRow(y, row);
                rowFloats.clear();
                rowFloats.put(row);
                shuffleBytes(rowBytes, shuffled, width);
                out.write(shuffled);
            }
        } else if (image instanceof RGBImage rgb) {
            var width = rgb.width();
            var height = rgb.height();
            out.writeByte(RGB);
            out.writeInt(height);
            out.writeInt(width);
            var red = rgb.red();
            var green = rgb.green();
            var blue = rgb.blue();
            var rRow = new float[width];
            var gRow = new float[width];
            var bRow = new float[width];
            var rowBytes = new byte[width * 3 * Float.BYTES];
            var shuffled = new byte[rowBytes.length];
            var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
            for (int y = 0; y < height; y++) {
                red.getRow(y, rRow);
                green.getRow(y, gRow);
                blue.getRow(y, bRow);
                rowFloats.clear();
                for (int x = 0; x < width; x++) {
                    rowFloats.put(rRow[x]).put(gRow[x]).put(bRow[x]);
                }
                shuffleBytes(rowBytes, shuffled, width * 3);
                out.write(shuffled);
            }
        } else {
            throw new IllegalArgumentException("Unsupported image type: " + image.getClass());
        }
    }

    /**
     * Reads pixel data previously written by {@link #write} and rebuilds an image,
     * attaching the supplied metadata map.
     *
     * @param in       the source stream
     * @param metadata the metadata map to attach to the rebuilt image
     * @return the rebuilt {@link ImageWrapper32} or {@link RGBImage}
     */
    public static ImageWrapper read(DataInputStream in, Map<Class<?>, Object> metadata) throws IOException {
        int kind = in.readByte();
        int height = in.readInt();
        int width = in.readInt();
        return switch (kind) {
            case MONO -> {
                var pixels = ImagePlane.allocate(width, height);
                var row = new float[width];
                var rowBytes = new byte[width * Float.BYTES];
                var shuffled = new byte[rowBytes.length];
                var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                for (int y = 0; y < height; y++) {
                    in.readFully(shuffled);
                    unshuffleBytes(shuffled, rowBytes, width);
                    rowFloats.clear();
                    rowFloats.get(row);
                    pixels.setRow(y, row);
                }
                yield new ImageWrapper32(pixels, metadata);
            }
            case RGB -> {
                var red = ImagePlane.allocate(width, height);
                var green = ImagePlane.allocate(width, height);
                var blue = ImagePlane.allocate(width, height);
                var rRow = new float[width];
                var gRow = new float[width];
                var bRow = new float[width];
                var rowBytes = new byte[width * 3 * Float.BYTES];
                var shuffled = new byte[rowBytes.length];
                var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                for (int y = 0; y < height; y++) {
                    in.readFully(shuffled);
                    unshuffleBytes(shuffled, rowBytes, width * 3);
                    rowFloats.clear();
                    for (int x = 0; x < width; x++) {
                        rRow[x] = rowFloats.get();
                        gRow[x] = rowFloats.get();
                        bRow[x] = rowFloats.get();
                    }
                    red.setRow(y, rRow);
                    green.setRow(y, gRow);
                    blue.setRow(y, bRow);
                }
                yield new RGBImage(red, green, blue, metadata);
            }
            default -> throw new IllegalStateException("Undefined image kind " + kind);
        };
    }

    private static void shuffleBytes(byte[] src, byte[] dst, int floatCount) {
        for (int plane = 0; plane < Float.BYTES; plane++) {
            int base = plane * floatCount;
            for (int i = 0; i < floatCount; i++) {
                dst[base + i] = src[i * Float.BYTES + plane];
            }
        }
    }

    private static void unshuffleBytes(byte[] src, byte[] dst, int floatCount) {
        for (int plane = 0; plane < Float.BYTES; plane++) {
            int base = plane * floatCount;
            for (int i = 0; i < floatCount; i++) {
                dst[i * Float.BYTES + plane] = src[base + i];
            }
        }
    }
}