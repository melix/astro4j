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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /**
     * The file ID for JSol'Ex trimmed SER files.
     */
    public static final String JSOLEX_RECORDER = "JSOLEX-TRIMMED";

    /**
     * Target page size for the sliding-window mmap, in bytes. Each page is
     * rounded down to a whole-frame boundary so that no frame straddles two
     * pages. Configurable via {@code -Dme.champeau.a4j.ser.pageSize=<bytes>}.
     */
    private static final long PAGE_SIZE_BYTES =
            Long.getLong("me.champeau.a4j.ser.pageSize", 256L * 1024 * 1024);

    /**
     * Maximum number of pages kept resident per reader. With the default
     * page size this caps each reader at ~1 GB of mmap'd file pages instead
     * of mapping the entire file.
     */
    private static final int PAGE_CACHE_SIZE = 4;

    private final File backingFile;
    private final RandomAccessFile accessFile;
    private final FileChannel channel;
    private final long dataOffset;
    private final ByteBuffer[] timestampsBuffer;
    private final Header header;
    private final int bytesPerFrame;
    private final int framesPerPage;
    private final ByteOrder imageByteOrder;
    /**
     * Sliding-window page cache: each entry maps {@code framesPerPage} consecutive
     * frames. Oldest entries are evicted when the size exceeds {@link #PAGE_CACHE_SIZE}
     * so that the resident mmap footprint stays bounded regardless of file size.
     * Single-threaded contract — callers must serialize access (matches the
     * existing {@code currentFrame}/{@code seekFrame} contract).
     */
    private final LinkedHashMap<Integer, MappedByteBuffer> pageCache;

    private int previousFrame = -1;
    private int currentFrame = 0;
    private ZonedDateTime currentTimestamp;
    private volatile boolean closed = false;

    private SerFileReader(File backingFile,
                          RandomAccessFile accessFile,
                          FileChannel channel,
                          long dataOffset,
                          int framesPerPage,
                          ByteOrder imageByteOrder,
                          ByteBuffer timestampsBuffer,
                          Header header) {
        this.backingFile = backingFile;
        this.accessFile = accessFile;
        this.channel = channel;
        this.dataOffset = dataOffset;
        this.framesPerPage = framesPerPage;
        this.imageByteOrder = imageByteOrder;
        this.timestampsBuffer = new ByteBuffer[]{timestampsBuffer};
        this.header = header;
        this.bytesPerFrame = header.geometry().getBytesPerFrame();
        this.pageCache = new LinkedHashMap<>(PAGE_CACHE_SIZE + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, MappedByteBuffer> eldest) {
                // Eviction frees the strong reference to the MappedByteBuffer; the
                // Cleaner attached by FileChannel.map will unmap on the next GC.
                return size() > PAGE_CACHE_SIZE;
            }
        };
    }

    private MappedByteBuffer pageFor(int pageIdx) {
        var cached = pageCache.get(pageIdx);
        if (cached != null) {
            return cached;
        }
        long offset = dataOffset + (long) pageIdx * framesPerPage * bytesPerFrame;
        long maxSize = (long) framesPerPage * bytesPerFrame;
        long fileSize;
        try {
            fileSize = channel.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        long size = Math.min(maxSize, fileSize - offset);
        try {
            var buf = channel.map(FileChannel.MapMode.READ_ONLY, offset, size);
            buf.order(imageByteOrder);
            pageCache.put(pageIdx, buf);
            return buf;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
     * returned buffer throws {@link UnsupportedOperationException}.
     *
     * @return the current frame
     */
    public Frame currentFrame() {
        assertNotClosed();
        if (previousFrame != currentFrame) {
            if (timestampsBuffer[0] != null) {
                currentTimestamp = TimestampConverter.of(timestampsBuffer[0].getLong()).map(c -> c.atZone(UTC)).orElse(null);
            }
            previousFrame = currentFrame;
        }
        var pageIdx = currentFrame / framesPerPage;
        var offset = (currentFrame % framesPerPage) * bytesPerFrame;
        var slice = pageFor(pageIdx).slice(offset, bytesPerFrame).asReadOnlyBuffer();
        return new Frame(currentFrame, slice, Optional.ofNullable(currentTimestamp));
    }

    private void assertNotClosed() {
        if (closed) {
            throw new IllegalStateException("Ser file was closed");
        }
    }

    /**
     * Moves to the next frame.
     */
    public void nextFrame() {
        assertNotClosed();
        currentFrame = (currentFrame + 1) % header.frameCount();
        positionTimestampBuffer();
    }

    private void positionTimestampBuffer() {
        if (timestampsBuffer[0] != null) {
            timestampsBuffer[0].position(8 * currentFrame);
        }
    }

    /**
     * Seeks to the specified frame.
     *
     * @param frameNb the frame number
     */
    public void seekFrame(int frameNb) {
        assertNotClosed();
        currentFrame = frameNb % header.frameCount();
        positionTimestampBuffer();
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
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        FileChannel channel = raf.getChannel();
        var headerBuffer = channel
                .map(FileChannel.MapMode.READ_ONLY, 0, Math.min(65536, channel.size()));
        var fileId = readAsciiString(headerBuffer, 14);
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Header header = readHeader(fileId, headerBuffer);
        long headerLength = headerBuffer.position();
        var bytesPerFrame = header.geometry().getBytesPerFrame();
        int framesPerPage = framesPerPage(bytesPerFrame, header.frameCount());
        boolean hasTimestamps = header.metadata().localDateTime() != null;
        long dataLength = header.frameCount() * (long) bytesPerFrame;
        if (headerLength + dataLength + 8L * header.frameCount() > file.length()) {
            // Workaround for some videos where timestamps are truncated
            hasTimestamps = false;
            header = new Header(fileId, header.camera(), header.geometry(), header.frameCount(), header.metadata().withoutTimestamps());
        }
        ByteBuffer timestampsBuffer = hasTimestamps ? channel.map(FileChannel.MapMode.READ_ONLY, headerLength + dataLength, 8L * header.frameCount()).order(ByteOrder.LITTLE_ENDIAN) : null;
        return new SerFileReader(file, raf, channel, headerLength, framesPerPage, header.geometry().imageEndian(), timestampsBuffer, header);
    }

    private static int framesPerPage(int bytesPerFrame, int frameCount) {
        long target = Math.max(PAGE_SIZE_BYTES / bytesPerFrame, 1L);
        // Guard against pathologically small files.
        return (int) Math.min(target, Math.max(frameCount, 1));
    }

    private static void emitOpenEvent(SerFileReader reader) {
        var event = new SerFileOpenEvent();
        if (!event.shouldCommit()) {
            return;
        }
        long perPage = (long) reader.framesPerPage * reader.bytesPerFrame;
        long mapped = perPage * PAGE_CACHE_SIZE;
        if (reader.timestampsBuffer[0] != null) {
            mapped += reader.timestampsBuffer[0].capacity();
        }
        event.path = reader.backingFile.getAbsolutePath();
        event.readerId = System.identityHashCode(reader);
        event.frameCount = reader.header.frameCount();
        event.mappedBytes = mapped;
        event.commit();
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
            int minPixel = Integer.MAX_VALUE;
            int pixelDepth = 0;
            int mid = frameCount / 2;
            boolean goLeft = true;

            outer:
            for (int step = 0; step <= mid; step += goLeft ? 0 : sampling) {
                int i = goLeft ? mid - step : mid + step;
                goLeft = !goLeft;

                if (i < 0 || i >= frameCount) {
                    continue;
                }

                tmpReader.seekFrame(i);
                var data = tmpReader.currentFrame().data();
                var dataLen = width * height * numPlanes;

                for (int j = 0; j < dataLen; j++) {
                    int pixelValue = readColor(data, bytesPerPixel, bitsToDiscard);
                    maxPixel = Math.max(maxPixel, pixelValue);
                    minPixel = Math.min(minPixel, pixelValue);
                    pixelDepth = (int) Math.ceil(Math.log(maxPixel) / Math.log(2));

                    if (pixelDepth == 16) {
                        break outer;
                    }
                }
            }

            pixelDepth = (int) Math.ceil(Math.log(maxPixel) / Math.log(2));
            if (pixelDepth < 1) {
                return tmpReader;
            }
            var newGeometry = new ImageGeometry(geometry.colorMode(), geometry.width(), geometry.height(), pixelDepth, geometry.imageEndian());
            var newHeader = new Header(tmpReader.header.fileId(), tmpReader.header.camera(), newGeometry, tmpReader.header.frameCount(), tmpReader.header.metadata());
            return new SerFileReader(tmpReader.backingFile, tmpReader.accessFile, tmpReader.channel, tmpReader.dataOffset,
                    tmpReader.framesPerPage, newGeometry.imageEndian(), tmpReader.timestampsBuffer[0], newHeader);
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
        if (closed) {
            return;
        }
        closed = true;
        var event = new SerFileCloseEvent();
        if (event.shouldCommit()) {
            event.path = backingFile.getAbsolutePath();
            event.readerId = System.identityHashCode(this);
            event.commit();
        }
        accessFile.close();
        pageCache.clear();
        timestampsBuffer[0] = null;
    }

    /**
     * Determines if the reader is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
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
                baseReader.accessFile,
                baseReader.channel,
                baseReader.dataOffset,
                baseReader.framesPerPage,
                baseReader.imageByteOrder,
                baseReader.timestampsBuffer[0],
                header
        );
        emitOpenEvent(reopened);
        return reopened;
    }
}
