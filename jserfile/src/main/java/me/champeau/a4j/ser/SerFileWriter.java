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
package me.champeau.a4j.ser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static me.champeau.a4j.ser.SerFileReader.JSOLEX_RECORDER;

public class SerFileWriter implements AutoCloseable {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final int LITTLE_ENDIAN = 1;

    private final File file;
    private final BufferedOutputStream outputStream;
    private final Camera camera;
    private final ImageGeometry geometry;
    private final ImageMetadata metadata;

    private final List<Long> timestamps = new ArrayList<>();
    private int frameCount;

    public SerFileWriter(File file,
                         ZonedDateTime date,
                         Camera camera,
                         ImageGeometry geometry,
                         ImageMetadata metadata) throws IOException {
        this.file = file;
        this.outputStream = new BufferedOutputStream(new FileOutputStream(file), 65536);
        this.camera = camera;
        this.geometry = geometry;
        this.metadata = metadata;
        writeHeader(ByteBuffer.allocate(178).order(ByteOrder.LITTLE_ENDIAN), date);
    }

    public void writeFrame(ZonedDateTime date, FrameReader reader) throws IOException {
        frameCount++;
        var buffer = ByteBuffer.allocate(geometry.getBytesPerFrame()).order(geometry.imageEndian());
        var bytesPerPixel = geometry.getBytesPerPixel();
        int bitsToDiscard = bytesPerPixel == 1 ? 8 - geometry.pixelDepthPerPlane() : 16 - geometry.pixelDepthPerPlane();
        for (int y = 0; y < geometry.height(); y++) {
            for (int x = 0; x < geometry.width(); x++) {
                short[] rgb = reader.getRGB(x, y);
                writePixel(buffer, rgb[0], rgb[1], rgb[2], bytesPerPixel, bitsToDiscard);
            }
        }
        outputStream.write(buffer.array(), 0, buffer.position());
        if (date != null) {
            timestamps.add(TimestampConverter.toTimestamp(date.withZoneSameInstant(UTC).toLocalDateTime()));
        }
    }

    /**
     * An optimized version when we know we're writing a mono image
     */
    public void writeMonoFrame(ZonedDateTime date, MonoFrameReader reader) throws IOException {
        frameCount++;
        var width = geometry.width();
        var height = geometry.height();
        var buffer = ByteBuffer.allocate(geometry.getBytesPerFrame()).order(geometry.imageEndian());
        var bytesPerPixel = geometry.getBytesPerPixel();
        int bitsToDiscard = bytesPerPixel == 1 ? 8 - geometry.pixelDepthPerPlane() : 16 - geometry.pixelDepthPerPlane();
        short[][] frame = new short[height][width];
        for (int y = 0; y < height; y++) {
            short[] line = frame[y];
            int finalY = y;
            IntStream.range(0, width)
                .parallel()
                .forEach(x -> line[x] = reader.getPixel(x, finalY));
        }
        boolean vFlip = reader.isVFlip();
        for (int y = 0; y < height; y++) {
            var yy = vFlip ? height - y - 1 : y;
            short[] line = frame[yy];
            for (int x = 0; x < width; x++) {
                var v = line[x];
                writePixel(buffer, v, v, v, bytesPerPixel, bitsToDiscard);
            }
        }
        outputStream.write(buffer.array(), 0, buffer.position());
        if (date != null) {
            timestamps.add(TimestampConverter.toTimestamp(date.withZoneSameInstant(UTC).toLocalDateTime()));
        }
    }

    private void writePixel(ByteBuffer buffer, short r, short g, short b, int bytesPerPixel, int bitsToDiscard) {
        switch (geometry.colorMode()) {
            case MONO:
                writeColor(buffer, bytesPerPixel, bitsToDiscard, r);
                break;
            case RGB:
                writeColor(buffer, bytesPerPixel, bitsToDiscard, r);
                writeColor(buffer, bytesPerPixel, bitsToDiscard, g);
                writeColor(buffer, bytesPerPixel, bitsToDiscard, b);
                break;
            case BGR:
                writeColor(buffer, bytesPerPixel, bitsToDiscard, b);
                writeColor(buffer, bytesPerPixel, bitsToDiscard, g);
                writeColor(buffer, bytesPerPixel, bitsToDiscard, r);
                break;
            default:
                throw new IllegalArgumentException("Unsupported color mode: " + geometry.colorMode());
        }
    }

    private static void writeColor(ByteBuffer frameData, int bytesPerPixel, int bitsToDiscard, short color) {
        if (bytesPerPixel == 1) {
            // Align the most significant bits back into the 8-bit range
            int v = (color >> 8) & 0xFF;
            v = v << bitsToDiscard;
            frameData.put((byte) v);
        } else {
            short v = (short) (((int) color & 0xFFFF) >> bitsToDiscard);
            frameData.putShort(Short.reverseBytes(v));

        }
    }

    private void writeAsciiString(ByteBuffer buffer, String value) {
        writeAsciiString(buffer, value, value.length());
    }

    private void writeAsciiString(ByteBuffer buffer, String value, int length) {
        byte[] buf = new byte[length];
        if (value != null) {
            byte[] data = value.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(data, 0, buf, 0, Math.min(data.length, length));
        }
        buffer.put(buf);
    }

    private void writeHeader(ByteBuffer buffer, ZonedDateTime date) throws IOException {
        writeAsciiString(buffer, JSOLEX_RECORDER);
        buffer.putInt(camera.id());
        buffer.putInt(geometry.colorMode().getValue());
        buffer.putInt(geometry.imageEndian() == ByteOrder.LITTLE_ENDIAN ? LITTLE_ENDIAN : 0);
        buffer.putInt(geometry.width());
        buffer.putInt(geometry.height());
        buffer.putInt(geometry.pixelDepthPerPlane());
        buffer.putInt(0); // frame count will be overwritten later
        writeAsciiString(buffer, metadata.observer(), 40);
        writeAsciiString(buffer, metadata.instrument(), 40);
        writeAsciiString(buffer, metadata.telescope(), 40);
        if (date != null) {
            buffer.putLong(TimestampConverter.toTimestamp(date.toLocalDateTime()));
        } else {
            buffer.putLong(-1);
        }
        if (date != null) {
            buffer.putLong(TimestampConverter.toTimestamp(date.withZoneSameInstant(UTC).toLocalDateTime()));
        } else {
            buffer.putLong(-1);
        }
        outputStream.write(buffer.array(), 0, buffer.position());
    }

    @Override
    public void close() throws IOException {
        // write timestamps
        var buffer = ByteBuffer.allocate(8 * timestamps.size()).order(ByteOrder.LITTLE_ENDIAN);
        for (long timestamp : timestamps) {
            buffer.putLong(timestamp);
        }
        outputStream.write(buffer.array(), 0, buffer.position());
        outputStream.flush();
        outputStream.close();
        // write frame count
        var raf = new RandomAccessFile(file, "rw");
        raf.seek(38);
        // write framecount as little endian
        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(frameCount).array());
        raf.close();
    }

    @FunctionalInterface
    public interface FrameReader {
        short[] getRGB(int x, int y);
    }

    @FunctionalInterface
    public interface MonoFrameReader {
        default boolean isVFlip() {
            return false;
        }

        short getPixel(int x, int y);
    }
}
