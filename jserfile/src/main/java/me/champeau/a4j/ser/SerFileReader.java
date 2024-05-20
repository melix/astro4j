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
import java.time.Duration;
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
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final RandomAccessFile accessFile;
    private final ByteBuffer[] imageBuffers;
    private final ByteBuffer timestampsBuffer;
    private final Header header;
    private final byte[] frameBuffer;
    private final int bytesPerFrame;
    private final int maxFramesPerBuffer;

    private int previousFrame = -1;
    private int currentFrame = 0;
    private ZonedDateTime currentTimestamp;
    private volatile boolean closed = false;

    private SerFileReader(RandomAccessFile accessFile, ByteBuffer[] imageBuffers, int maxFramesPerBuffer, ByteBuffer timestampsBuffer, Header header) {
        this.accessFile = accessFile;
        this.imageBuffers = imageBuffers;
        this.maxFramesPerBuffer = maxFramesPerBuffer;
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
            findBuffer().slice().limit(bytesPerFrame).get(frameBuffer);
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

    private ByteBuffer findBuffer() {
        return imageBuffers[currentFrame / maxFramesPerBuffer];
    }

    private void positionBuffers() {
        findBuffer().position((currentFrame % maxFramesPerBuffer) * bytesPerFrame);
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
        var headerBuffer = channel
            .map(FileChannel.MapMode.READ_ONLY, 0, Math.min(65536, channel.size()));
        readAsciiString(headerBuffer, 14);
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Header header = readHeader(headerBuffer);
        long headerLength = headerBuffer.position();
        var bytesPerFrame = header.geometry().getBytesPerFrame();
        long maxFramesInBuffer = (long) Math.floor(Integer.MAX_VALUE / (double) bytesPerFrame);
        int numBuffers = (int) Math.ceil(header.frameCount() / (double) maxFramesInBuffer);
        ByteBuffer[] imageBuffers = new ByteBuffer[numBuffers];
        long remainingFrameCount = header.frameCount();
        for (int i = 0; i < numBuffers; i++) {
            long nFramesInBuffer = Math.min(remainingFrameCount, maxFramesInBuffer);
            long offset = headerLength + i * maxFramesInBuffer * bytesPerFrame;
            long size = nFramesInBuffer * bytesPerFrame;
            imageBuffers[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, size).order(header.geometry().imageEndian());
            remainingFrameCount -= maxFramesInBuffer;
        }
        boolean hasTimestamps = header.metadata().localDateTime() != null;
        long dataLength = header.frameCount() * (long) bytesPerFrame;
        if (headerLength + dataLength * 8L * header.frameCount() > file.length()) {
            // Workaround for some videos where timestamps are truncated
            hasTimestamps = false;
            header = new Header(header.camera(), header.geometry(), header.frameCount(), header.metadata().withoutTimestamps());
        }
        ByteBuffer timestampsBuffer = hasTimestamps ? channel.map(FileChannel.MapMode.READ_ONLY, headerLength + dataLength, 8L * header.frameCount()).order(ByteOrder.LITTLE_ENDIAN) : null;
        var tmpReader = new SerFileReader(raf, imageBuffers, (int) maxFramesInBuffer, timestampsBuffer, header);
        return fixReader(tmpReader);
    }

    /**
     * It appears that some software lie about the true pixel depth of the images,
     * which means we cannot rely on the pixel depth provided in the header.
     * This will select a few frames in the middle of the video and determine the
     * true pixel depth by looking at the maximum pixel value.
     *
     * @param tmpReader the reader to fix
     * @return a fixed reader
     */
    private static SerFileReader fixReader(SerFileReader tmpReader) {
        var frameCount = tmpReader.header.frameCount();
        var geometry = tmpReader.header.geometry();
        var width = geometry.width();
        var height = geometry.height();
        var min = Math.max(0, frameCount / 2 - 5);
        var max = Math.min(frameCount, frameCount / 2 + 5);
        int bytesPerPixel = geometry.getBytesPerPixel();
        if (bytesPerPixel > 1) {
            int numPlanes = geometry.colorMode().getNumberOfPlanes();
            int bitsToDiscard = 16 - geometry.pixelDepthPerPlane();
            int maxPixel = 0;
            int minPixel = Integer.MAX_VALUE;
            for (int i = min; i < max; i++) {
                tmpReader.seekFrame(i);
                var data = tmpReader.currentFrame().data();
                for (int j = 0; j < width * height * numPlanes; j++) {
                    int pixelValue = readColor(data, bytesPerPixel, bitsToDiscard);
                    maxPixel = Math.max(maxPixel, pixelValue);
                    minPixel = Math.min(minPixel, pixelValue);
                }
            }

            int pixelDepth = 8;
            for (int x = 15; x >= 8; x--) {
                if (maxPixel >= (1 << x)) {
                    pixelDepth = x + 1;
                    break;
                }
            }
            var newGeometry = new ImageGeometry(geometry.colorMode(), geometry.width(), geometry.height(), pixelDepth, geometry.imageEndian());
            return new SerFileReader(tmpReader.accessFile, tmpReader.imageBuffers, tmpReader.maxFramesPerBuffer, tmpReader.timestampsBuffer,
                new Header(tmpReader.header.camera(), newGeometry, tmpReader.header.frameCount(), tmpReader.header.metadata()));
        }
        return tmpReader;
    }

    private static int readColor(ByteBuffer frameData, int bytesPerPixel, int bitsToDiscard) {
        int next;
        if (bytesPerPixel == 1) {
            // Data of between 1 and 8 bits should be stored aligned with the most significant bit
            int v = frameData.get() >> bitsToDiscard;
            next = (v & 0xFF) << 8;
        } else {
            next = (frameData.getShort() << 8 << bitsToDiscard) & 0xFFFF;
        }
        return next;
    }

    public Optional<Double> estimateFps() {
        Optional<Double> value = Optional.empty();
        if (header.metadata().telescope().contains("fps=")) {
            var i = header.metadata().telescope().indexOf("fps=");
            // last index is either the end of the string or the first letter after the fps value
            var end = i + 4;
            while (end < header.metadata().telescope().length() && !Character.isLetter(header.metadata().telescope().charAt(end))) {
                end++;
            }
            String fps = header.metadata().telescope().substring(i + 4, end);
            try {
                return Optional.of(Double.parseDouble(fps));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (header.metadata().hasTimestamps()) {
            seekLast();
            ZonedDateTime lastFrameTimestamp = currentFrame().timestamp().orElseThrow();
            seekFirst();
            ZonedDateTime firstFrameTimestamp = currentFrame().timestamp().orElseThrow();
            Duration sequenceDuration = Duration.between(firstFrameTimestamp, lastFrameTimestamp);
            long seconds = sequenceDuration.getSeconds();
            value = Optional.ofNullable(seconds > 0 ? (double) header.frameCount() / seconds : null);
        }
        return value;
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
                localDate != null,
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
