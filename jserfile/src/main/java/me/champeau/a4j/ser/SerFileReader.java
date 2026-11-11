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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A ser file reader. The reader is backed with in-memory
 * mapped file, making it possible to process very large
 * ser files. This means that this should usually be used
 * in a try-with-resources block, in order for the backing
 * file to be automatically unmapped.
 */
public class SerFileReader implements AutoCloseable {
    private static final ZoneId UTC = ZoneId.of("UTC");
    /**
     * The file ID for JSol'Ex trimmed SER files.
     */
    public static final String JSOLEX_RECORDER = "JSOLEX-TRIMMED";

    private static final int MAX_HEADER_SIZE = 65536;
    private static final long NO_TIMESTAMPS = -1;
    private static final ValueLayout.OfLong TIMESTAMP_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private final File backingFile;
    private final Arena arena;
    private final MemorySegment segment;
    private final long dataOffset;
    private final long timestampsOffset;
    private final Header header;
    private final int bytesPerFrame;
    private final AtomicBoolean closed = new AtomicBoolean();

    private int previousFrame = -1;
    private int currentFrame = 0;
    private ZonedDateTime currentTimestamp;

    private SerFileReader(File backingFile,
                          Arena arena,
                          MemorySegment segment,
                          long dataOffset,
                          long timestampsOffset,
                          Header header) {
        this.backingFile = backingFile;
        this.arena = arena;
        this.segment = segment;
        this.dataOffset = dataOffset;
        this.timestampsOffset = timestampsOffset;
        this.header = header;
        this.bytesPerFrame = header.geometry().getBytesPerFrame();
    }

    /**
     * Gets the SER file header.
     *
     * @return the header
     */
    public Header header() {
        return header;
    }

    /**
     * Gets the current frame. The returned {@link Frame#data()} is a read-only
     * view of the underlying memory-mapped file region (no heap copy). The view
     * is invalidated by the next call to {@link #nextFrame()}, {@link #seekFrame(int)}
     * or {@link #close()}; callers that need to retain the bytes across those
     * calls must copy them explicitly. Calling {@link ByteBuffer#array()} on the
     * returned buffer throws {@link UnsupportedOperationException}, and reading
     * it after {@link #close()} throws {@link IllegalStateException}.
     *
     * @return the current frame
     */
    public Frame currentFrame() {
        assertNotClosed();
        if (previousFrame != currentFrame) {
            if (timestampsOffset != NO_TIMESTAMPS) {
                var timestamp = segment.get(TIMESTAMP_LAYOUT, timestampsOffset + 8L * currentFrame);
                currentTimestamp = TimestampConverter.of(timestamp).map(c -> c.atZone(UTC)).orElse(null);
            }
            previousFrame = currentFrame;
        }
        var data = segment.asSlice(dataOffset + (long) currentFrame * bytesPerFrame, bytesPerFrame).asByteBuffer();
        return new Frame(currentFrame, data, Optional.ofNullable(currentTimestamp));
    }

    private void assertNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Ser file was closed");
        }
    }

    /**
     * Moves to the next frame.
     */
    public void nextFrame() {
        assertNotClosed();
        currentFrame = (currentFrame + 1) % header.frameCount();
    }

    /**
     * Seeks to the specified frame.
     *
     * @param frameNb the frame number
     */
    public void seekFrame(int frameNb) {
        assertNotClosed();
        currentFrame = frameNb % header.frameCount();
    }

    /**
     * Seeks to the last frame.
     */
    public void seekLast() {
        seekFrame(header.frameCount() - 1);
    }

    /**
     * Seeks to the first frame.
     */
    public void seekFirst() {
        seekFrame(0);
    }

    /**
     * Opens a SER file for reading.
     *
     * @param file the file to read
     * @return a new SerFileReader
     * @throws IOException if an I/O error occurs
     */
    public static SerFileReader of(File file) throws IOException {
        return of(file, false);
    }

    /**
     * Opens a SER file for reading.
     *
     * @param file                  the file to read
     * @param trustDeclaredBitDepth if true, the pixel depth declared in the SER header is used as-is
     *                              and the heuristic detection of the actual pixel depth is skipped
     * @return a new SerFileReader
     * @throws IOException if an I/O error occurs
     */
    public static SerFileReader of(File file, boolean trustDeclaredBitDepth) throws IOException {
        var tmpReader = createBaseReader(file);
        var result = trustDeclaredBitDepth ? tmpReader : fixReader(tmpReader);
        emitOpenEvent(result);
        return result;
    }

    private static SerFileReader createBaseReader(File file) throws IOException {
        var arena = Arena.ofShared();
        try {
            MemorySegment segment;
            try (var channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            }
            var headerBuffer = segment.asSlice(0, Math.min(MAX_HEADER_SIZE, segment.byteSize())).asByteBuffer();
            var fileId = readAsciiString(headerBuffer, 14);
            headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
            Header header = readHeader(fileId, headerBuffer);
            long headerLength = headerBuffer.position();
            long dataLength = header.frameCount() * (long) header.geometry().getBytesPerFrame();
            long timestampsOffset = header.metadata().localDateTime() != null ? headerLength + dataLength : NO_TIMESTAMPS;
            if (headerLength + dataLength + 8L * header.frameCount() > segment.byteSize()) {
                // Workaround for some videos where timestamps are truncated
                timestampsOffset = NO_TIMESTAMPS;
                header = new Header(fileId, header.camera(), header.geometry(), header.frameCount(), header.metadata().withoutTimestamps());
            }
            return new SerFileReader(file, arena, segment, headerLength, timestampsOffset, header);
        } catch (IOException | RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    private static void emitOpenEvent(SerFileReader reader) {
        var event = new SerFileOpenEvent();
        if (!event.shouldCommit()) {
            return;
        }
        event.path = reader.backingFile.getAbsolutePath();
        event.readerId = System.identityHashCode(reader);
        event.frameCount = reader.header.frameCount();
        event.mappedBytes = reader.segment.byteSize();
        event.commit();
    }

    /**
     * It appears that some software lie about the true pixel depth of the images,
     * which means we cannot rely on the pixel depth provided in the header.
     * This will scan a few frames evenly spread across the video and determine
     * the true pixel depth by looking at the maximum pixel value.
     *
     * @param tmpReader the reader to fix
     * @return a fixed reader
     */
    private static SerFileReader fixReader(SerFileReader tmpReader) {
        if (JSOLEX_RECORDER.equals(tmpReader.header.fileId())) {
            // Trust JSol'Ex rewritten files
            return tmpReader;
        }
        var frameCount = tmpReader.header.frameCount();
        var geometry = tmpReader.header.geometry();
        var width = geometry.width();
        var height = geometry.height();
        var sampling = Math.max(10, 10 * frameCount / 100);
        int bytesPerPixel = geometry.getBytesPerPixel();
        if (bytesPerPixel > 1) {
            int numPlanes = geometry.colorMode().getNumberOfPlanes();
            int bitsToDiscard = 16 - geometry.pixelDepthPerPlane();
            int maxPixel = 0;

            outer:
            for (int i = 0; i < frameCount; i += sampling) {
                tmpReader.seekFrame(i);
                var data = tmpReader.currentFrame().data();
                var dataLen = width * height * numPlanes;

                for (int j = 0; j < dataLen; j++) {
                    maxPixel = Math.max(maxPixel, readColor(data, bytesPerPixel, bitsToDiscard));
                    if (maxPixel > 1 << 15) {
                        break outer;
                    }
                }
            }

            int pixelDepth = maxPixel <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(maxPixel - 1);
            if (pixelDepth < 1) {
                return tmpReader;
            }
            var newGeometry = new ImageGeometry(geometry.colorMode(), geometry.width(), geometry.height(), pixelDepth, geometry.imageEndian());
            var newHeader = new Header(tmpReader.header.fileId(), tmpReader.header.camera(), newGeometry, tmpReader.header.frameCount(), tmpReader.header.metadata());
            return new SerFileReader(tmpReader.backingFile, tmpReader.arena, tmpReader.segment, tmpReader.dataOffset,
                    tmpReader.timestampsOffset, newHeader);
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

    /**
     * Estimates the frames per second of the video.
     *
     * @return the estimated FPS, or empty if it cannot be determined
     */
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
        var sequenceDuration = computeSequenceDuration();
        if (sequenceDuration.isPresent()) {
            long seconds = sequenceDuration.get().getSeconds();
            value = Optional.ofNullable(seconds > 0 ? (double) header.frameCount() / seconds : null);
        }
        return value;
    }

    /**
     * Computes the time elapsed between the first and the last frame,
     * from the frame timestamps. The reader is left on the first frame.
     *
     * @return the duration of the sequence, or empty if the file has no timestamps
     */
    public Optional<Duration> computeSequenceDuration() {
        if (!header.metadata().hasTimestamps()) {
            return Optional.empty();
        }
        seekLast();
        var lastFrameTimestamp = currentFrame().timestamp();
        seekFirst();
        var firstFrameTimestamp = currentFrame().timestamp();
        if (firstFrameTimestamp.isEmpty() || lastFrameTimestamp.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(firstFrameTimestamp.get(), lastFrameTimestamp.get()));
    }

    /**
     * Reads the SER file header.
     *
     * @param fileId the file ID
     * @param buffer the buffer containing header data
     * @return the parsed header
     * @throws IOException if an I/O error occurs
     */
    private static Header readHeader(String fileId, ByteBuffer buffer) throws IOException {
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
        ZonedDateTime utcDate = TimestampConverter.of(buffer.getLong()).map(e -> e.atZone(UTC)).orElseGet(() -> {
            if (localDate != null) {
                return localDate.atZone(UTC);
            }
            return LocalDateTime.now().atZone(UTC);
        });
        return new Header(
                fileId,
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        var event = new SerFileCloseEvent();
        if (event.shouldCommit()) {
            event.path = backingFile.getAbsolutePath();
            event.readerId = System.identityHashCode(this);
            event.commit();
        }
        arena.close();
    }

    /**
     * Determines if the reader is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Reopens the SER file.
     *
     * @return a new SerFileReader for the same file
     * @throws IOException if an I/O error occurs
     */
    public SerFileReader reopen() throws IOException {
        var baseReader = createBaseReader(backingFile);
        var reopened = new SerFileReader(
                baseReader.backingFile,
                baseReader.arena,
                baseReader.segment,
                baseReader.dataOffset,
                baseReader.timestampsOffset,
                header
        );
        emitOpenEvent(reopened);
        return reopened;
    }
}
