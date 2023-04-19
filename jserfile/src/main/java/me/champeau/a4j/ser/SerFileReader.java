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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * A ser file reader. The reader is backed with in-memory
 * mapped file, making it possible to process very large
 * ser files. This means that this should usually be used
 * in a try-with-resources block, in order for the backing
 * file to be automatically closed.
 */
public class SerFileReader implements AutoCloseable {
    private static final String LUCAM_RECORDER = "LUCAM-RECORDER";
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final RandomAccessFile accessFile;
    private final ByteBuffer buffer;
    private final ByteBuffer timestampsBuffer;
    private final Header header;
    private final byte[] frameBuffer;
    private final int bytesPerFrame;

    private int previousFrame = -1;
    private int currentFrame = 0;
    private ZonedDateTime currentTimestamp;
    private volatile boolean closed = false;

    private SerFileReader(RandomAccessFile accessFile, ByteBuffer buffer, ByteBuffer timestampsBuffer, Header header) {
        this.accessFile = accessFile;
        this.buffer = buffer;
        this.timestampsBuffer = timestampsBuffer;
        this.header = header;
        this.bytesPerFrame = header.geometry().getBytesPerFrame();
        this.frameBuffer = new byte[bytesPerFrame];
    }

    public Header header() {
        return header;
    }

    public Frame currentFrame() {
        assertNotClosed();
        if (previousFrame != currentFrame) {
            buffer.slice().limit(bytesPerFrame).get(frameBuffer);
            if (timestampsBuffer != null) {
                currentTimestamp = TimestampConverter.of(timestampsBuffer.getLong()).map(c -> c.atZone(UTC)).orElse(null);
            }
            previousFrame = currentFrame;
        }
        return new Frame(currentFrame, ByteBuffer.wrap(frameBuffer), Optional.ofNullable(currentTimestamp));
    }

    private void assertNotClosed() {
        if (closed) {
            throw new IllegalStateException("Ser file was closed");
        }
    }

    public void nextFrame() {
        assertNotClosed();
        currentFrame = (currentFrame + 1) % header.frameCount();
        positionBuffers();
    }

    private void positionBuffers() {
        buffer.position(currentFrame * bytesPerFrame);
        if (timestampsBuffer != null) {
            timestampsBuffer.position(8 * currentFrame);
        }
    }

    public void seekFrame(int frameNb) {
        assertNotClosed();
        currentFrame = frameNb % header.frameCount();
        positionBuffers();
    }

    public void seekLast() {
        seekFrame(header.frameCount() - 1);
    }

    public void seekFirst() {
        seekFrame(0);
    }

    public static SerFileReader of(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        FileChannel channel = raf.getChannel();
        ByteBuffer buffer = channel
                .map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        String marker = readAsciiString(buffer, 14);
        if (!LUCAM_RECORDER.equals(marker)) {
            throw new IOException("Invalid ser file, header is invalid");
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        Header header = readHeader(buffer);
        ByteBuffer imageBuffer = buffer.slice().order(header.geometry().imageEndian());
        boolean hasTimestamps = header.metadata().localDateTime() != null;
        ByteBuffer timestampsBuffer = hasTimestamps ? buffer.slice().position(header.frameCount() * header.geometry().getBytesPerFrame()).slice().order(ByteOrder.LITTLE_ENDIAN) : null;
        return new SerFileReader(raf, imageBuffer, timestampsBuffer, header);
    }

    private static Header readHeader(ByteBuffer buffer) throws IOException {
        Camera camera = new Camera(buffer.getInt());
        Optional<ColorMode> colorMode = ColorMode.of(buffer.getInt());
        if (colorMode.isEmpty()) {
            throw new IOException("Invalid color mode");
        }
        ByteOrder imageByteOrder = readEndian(buffer);
        int imageWidth = buffer.getInt();
        int imageHeight = buffer.getInt();
        int pixelDepthPerPlane = buffer.getInt();
        int frameCount = buffer.getInt();
        String observer = readAsciiString(buffer, 40);
        String instrument = readAsciiString(buffer, 40);
        String telescope = readAsciiString(buffer, 40);
        LocalDateTime localDate = TimestampConverter.of(buffer.getLong()).orElse(null);
        ZonedDateTime utcDate = TimestampConverter.of(buffer.getLong()).map(e -> e.atZone(UTC)).orElseThrow(() -> new IllegalStateException("Unable to read UTC timestamp"));
        return new Header(
                camera,
                new ImageGeometry(colorMode.get(), imageWidth, imageHeight, pixelDepthPerPlane, imageByteOrder),
                frameCount,
                new ImageMetadata(
                        observer,
                        instrument,
                        telescope,
                        localDate,
                        utcDate
                )
        );
    }


    private static ByteOrder readEndian(ByteBuffer buffer) throws IOException {
        return switch (buffer.getInt()) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IOException("Invalid endian mode");
        };
    }

    private static String readAsciiString(ByteBuffer buffer, int len) {
        byte[] str = new byte[len];
        buffer.get(str);
        return new String(str);
    }

    @Override
    public void close() throws Exception {
        closed = true;
        accessFile.close();
    }

    public boolean isClosed() {
        return closed;
    }
}
