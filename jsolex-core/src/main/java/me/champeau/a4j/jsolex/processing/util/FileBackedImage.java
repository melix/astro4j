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

import me.champeau.a4j.utilities.HeapPressure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * An image wrapper where data is stored on disk instead of
 * memory.
 */
public final class FileBackedImage implements ImageWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedImage.class);
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Map<Path, Integer> REF_COUNT = new HashMap<>();
    private static final Map<Path, Status> STATUS = new ConcurrentHashMap<>();
    private static final Lock LOCK = new ReentrantLock();
    private static final Map<ImageWrapper, FileBackedImage> WRAP_CACHE = new WeakHashMap<>();
    private static final Lock CACHE_LOCK = new ReentrantLock();
    private static final byte MONO = 0;
    private static final byte RGB = 2;
    private static final int AUTOFLUSH_TIMEOUT = 10_000;
    private static final int DEFAULT_RW_BUFFER_SIZE = 65536;
    private static final int WRITE_PERMITS = 4;
    private static final Semaphore WRITE_SEMAPHORE = new Semaphore(WRITE_PERMITS);
    private static final ReferenceQueue<ImageWrapper> REFERENCE_QUEUE = new ReferenceQueue<>();
    private static final AtomicBoolean ASYNC_FLUSH_PENDING = new AtomicBoolean();

    private static volatile BooleanSupplier underPressure = () -> HeapPressure.current() > 0.5;

    /**
     * Tells whether the calling thread must never block on disk I/O (typically
     * the UI thread). When it returns {@code true}, pressure-triggered flushes
     * are offloaded to a virtual thread instead of blocking the caller. The
     * default always returns {@code false} (headless and CLI use).
     */
    private static volatile BooleanSupplier mustNotBlock = () -> false;

    static {
        var flushing = new Thread(() -> {
            while (true) {
                try {
                    var ref = (ImageReference) REFERENCE_QUEUE.remove();
                    ref.flushToDisk();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        flushing.setName("FileBackedImage-Flush");
        flushing.setDaemon(true);
        flushing.start();

        var autoFlush = new Thread(() -> {
            while (true) {
                try {
                    System.gc();
                    Thread.sleep(AUTOFLUSH_TIMEOUT);
                    var event = new FileBackedImageAutoFlushEvent();
                    event.begin();
                    var ratio = HeapPressure.current();
                    event.pressureRatio = ratio;
                    if (!underPressure.getAsBoolean()) {
                        event.triggered = false;
                        event.commit();
                        continue;
                    }
                    event.triggered = true;
                    long now = System.currentTimeMillis();
                    // Snapshot under the lock; filter and flush outside it. The
                    // wrap path uses CACHE_LOCK as backpressure under pressure,
                    // so we hold it as briefly as possible here to avoid
                    // serializing wraps for non-throttle reasons.
                    List<FileBackedImage> snapshot;
                    CACHE_LOCK.lock();
                    try {
                        snapshot = new ArrayList<>(WRAP_CACHE.values());
                    } finally {
                        CACHE_LOCK.unlock();
                    }
                    try {
                        var candidates = snapshot.stream()
                                .filter(fbi -> fbi.unwrapped.get() != null)
                                .toList();
                        var toFlush = candidates.stream()
                                .filter(fbi -> now - fbi.lastAccessTime.get() > AUTOFLUSH_TIMEOUT)
                                .toList();
                        event.candidateCount = candidates.size();
                        event.flushedCount = toFlush.size();
                        if (!toFlush.isEmpty()) {
                            LOGGER.debug("Auto-flushing {} images", toFlush.size());
                            for (var fbi : toFlush) {
                                fbi.flushToDisk(fbi.unwrapped.source, "AUTOFLUSH");
                            }
                        }
                    } finally {
                        event.commit();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        autoFlush.setDaemon(true);
        autoFlush.setName("FileBackedImage-AutoFlush");
        autoFlush.start();
    }

    private final Status status;
    private final Condition condition;
    private final int width;
    private final int height;
    private final Path backingFile;
    private final Map<Class<?>, Object> metadata;
    private final Object keptInMemory;
    private volatile ImageReference unwrapped;
    private final AtomicLong lastAccessTime;

    private FileBackedImage(int width, int height, Path backingFile, Map<Class<?>, Object> metadata, Object keptInMemory, ImageWrapper source) {
        if (source instanceof FileBackedImage) {
            throw new IllegalArgumentException("Cannot wrap a FileBackedImage");
        }
        this.width = width;
        this.height = height;
        this.backingFile = backingFile;
        this.metadata = metadata;
        this.keptInMemory = keptInMemory;
        this.unwrapped = new ImageReference(source, this, REFERENCE_QUEUE);
        this.status = STATUS.computeIfAbsent(backingFile, k -> new Status(new AtomicBoolean(false), new ReentrantLock(), new ArrayList<>()));
        this.condition = status.newCondition();
        this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        LOCK.lock();
        try {
            REF_COUNT.compute(backingFile, (k, v) -> v == null ? 1 : v + 1);
        } finally {
            LOCK.unlock();
        }
        CLEANER.register(this, () -> clean(backingFile));
        if (underPressure.getAsBoolean()) {
            flushToDisk(source, "CONSTRUCTOR");
        }
    }

    public static FileBackedImage wrap(ImageWrapper wrapper) {
        var event = new FileBackedImageWrapEvent();
        event.begin();
        try {
            if (wrapper instanceof FileBackedImage fbi) {
                event.cached = true;
                return fbi;
            }
            CACHE_LOCK.lock();
            // if memory is too close to full, flush some images to disk
            if (underPressure.getAsBoolean()) {
                event.triggeredFlush = true;
                if (mustNotBlock.getAsBoolean()) {
                    // Never block the UI thread on disk I/O: offload the flush.
                    flushImagesAsync();
                } else {
                    flushImages();
                }
            }
            try {
                var cached = WRAP_CACHE.get(wrapper);
                if (cached != null) {
                    event.cached = true;
                    return cached;
                }
                var width = wrapper.width();
                var height = wrapper.height();
                try {
                    var backingFile = TemporaryFolder.newTempFile("jsolex", ".img");
                    Files.delete(backingFile);
                    if (wrapper instanceof ImageWrapper32 mono) {
                        var result = new FileBackedImage(width, height, backingFile, mono.metadata(), null, mono);
                        WRAP_CACHE.put(wrapper, result);
                        return result;
                    }
                    if (wrapper instanceof RGBImage rgb) {
                        var fileBackedImage = new FileBackedImage(width, height, backingFile, rgb.metadata(), null, rgb);
                        WRAP_CACHE.put(wrapper, fileBackedImage);
                        return fileBackedImage;
                    }
                    throw new ProcessingException("Unexpected image type " + wrapper);
                } catch (IOException ex) {
                    throw new ProcessingException(ex);
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        } finally {
            event.commit();
        }
    }

    public static void flushImages() {
        // CACHE_LOCK is held only to snapshot the cache, not during the disk
        // I/O: concurrent wrap() calls are therefore not back-pressured.
        List<FileBackedImage> fbiToFlush;
        CACHE_LOCK.lock();
        try {
            fbiToFlush = WRAP_CACHE.values()
                    .stream()
                    .filter(fbi -> fbi.unwrapped.get() != null)
                    .toList();
        } finally {
            CACHE_LOCK.unlock();
        }
        if (fbiToFlush.isEmpty()) {
            return;
        }
        var event = new FileBackedImageFlushEvent();
        event.begin();
        event.imageCount = fbiToFlush.size();
        LOGGER.debug("Flushing {} images to disk to free memory", fbiToFlush.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var fbi : fbiToFlush) {
                executor.submit(() -> {
                    if (!underPressure.getAsBoolean() || fbi.unwrapped.get() == null) {
                        return;
                    }
                    fbi.writeToDisk(fbi.unwrapped.source, "BATCH");
                });
            }
        } finally {
            event.commit();
        }
        LOGGER.debug("Flushed images");
    }

    /**
     * Performs {@link #flushImages()} on a virtual thread so the caller is not
     * blocked on disk I/O. Concurrent requests are coalesced: while a flush is
     * already pending, further calls are no-ops.
     */
    private static void flushImagesAsync() {
        if (ASYNC_FLUSH_PENDING.compareAndSet(false, true)) {
            Thread.startVirtualThread(() -> {
                try {
                    flushImages();
                } finally {
                    ASYNC_FLUSH_PENDING.set(false);
                }
            });
        }
    }

    /**
     * Asynchronous variant: spawns a virtual thread to perform the write.
     * Used by callers that don't want to block — the auto-flush thread, the
     * soft-ref cleaner thread, and the constructor's pressure-triggered flush.
     * Callers that do want to wait should use {@link #writeToDisk} directly
     * rather than spawning-then-waiting.
     */
    private void flushToDisk(ImageWrapper source, String trigger) {
        if (source == null) {
            return;
        }
        Thread.startVirtualThread(() -> writeToDisk(source, trigger));
    }

    /**
     * Synchronous variant: writes the source to disk on the calling thread.
     * Idempotent — subsequent calls short-circuit on {@code status.saved}.
     * Holds {@code status.lock()} for the duration so concurrent writers of
     * the same image serialize safely.
     */
    private void writeToDisk(ImageWrapper source, String trigger) {
        if (source == null) {
            return;
        }
        WRITE_SEMAPHORE.acquireUninterruptibly();
        try {
            var event = new FileBackedImageWriteEvent();
            event.begin();
            event.trigger = trigger;
            status.lock().lock();
            try {
                if (status.saved.get() || REF_COUNT.getOrDefault(backingFile, 0) == 0) {
                    event.skipped = true;
                    return;
                }
                long uncompressed = 0;
                var deflater = new Deflater(Deflater.BEST_SPEED);
                try {
                    if (source instanceof ImageWrapper32 mono) {
                        try (var fos = new BufferedOutputStream(new FileOutputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
                             var dos = new DataOutputStream(new DeflaterOutputStream(fos, deflater, DEFAULT_RW_BUFFER_SIZE))) {
                            dos.writeByte(MONO);
                            dos.writeInt(height);
                            dos.writeInt(width);
                            var data = mono.data();
                            var rowBytes = new byte[width * Float.BYTES];
                            var shuffled = new byte[rowBytes.length];
                            var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                            for (int y = 0; y < height; y++) {
                                rowFloats.clear();
                                rowFloats.put(data[y]);
                                shuffleBytes(rowBytes, shuffled, width);
                                dos.write(shuffled);
                            }
                            uncompressed = 1L + 8L + (long) height * rowBytes.length;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (source instanceof RGBImage rgb) {
                        try (var fos = new BufferedOutputStream(new FileOutputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
                             var dos = new DataOutputStream(new DeflaterOutputStream(fos, deflater, DEFAULT_RW_BUFFER_SIZE))) {
                            dos.writeByte(RGB);
                            dos.writeInt(height);
                            dos.writeInt(width);
                            var r = rgb.r();
                            var g = rgb.g();
                            var b = rgb.b();
                            var rowBytes = new byte[width * 3 * Float.BYTES];
                            var shuffled = new byte[rowBytes.length];
                            var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                            for (int y = 0; y < height; y++) {
                                rowFloats.clear();
                                var rRow = r[y];
                                var gRow = g[y];
                                var bRow = b[y];
                                for (int x = 0; x < width; x++) {
                                    rowFloats.put(rRow[x]).put(gRow[x]).put(bRow[x]);
                                }
                                shuffleBytes(rowBytes, shuffled, width * 3);
                                dos.write(shuffled);
                            }
                            uncompressed = 1L + 8L + (long) height * rowBytes.length;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    event.bytes = deflater.getBytesWritten();
                } finally {
                    deflater.end();
                }
                event.uncompressedBytes = uncompressed;
            } finally {
                fileSaved();
                status.lock().unlock();
                event.commit();
            }
        } finally {
            WRITE_SEMAPHORE.release();
        }
    }

    public ImageWrapper unwrapToMemory() {
        lastAccessTime.set(System.currentTimeMillis());
        var cached = unwrapped.get();
        if (cached != null) {
            return cached;
        }
        var stillAlive = unwrapped.source;
        if (stillAlive != null) {
            return stillAlive;
        }
        LOGGER.debug("Re-reading image from disk: {}", backingFile);
        status.lock().lock();
        try {
            while (!status.saved().get()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            status.lock().unlock();
        }
        // Now that the file is saved, proceed to re-read the image from disk.
        var read = readFromDisk();
        unwrapped = new ImageReference(read, this, REFERENCE_QUEUE);
        CACHE_LOCK.lock();
        try {
            WRAP_CACHE.put(read, new FileBackedImage(width, height, backingFile, metadata, keptInMemory, read));
        } finally {
            CACHE_LOCK.unlock();
        }
        LOGGER.debug("Unwrapped image from disk: {}", backingFile);
        return read;
    }

    private ImageWrapper readFromDisk() {
        var inflater = new Inflater();
        try (var fis = new BufferedInputStream(new FileInputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
             var dis = new DataInputStream(new InflaterInputStream(fis, inflater, DEFAULT_RW_BUFFER_SIZE))) {
            int kind = dis.readByte();
            int height = dis.readInt();
            int width = dis.readInt();
            return switch (kind) {
                case MONO -> {
                    float[][] data = new float[height][width];
                    var rowBytes = new byte[width * Float.BYTES];
                    var shuffled = new byte[rowBytes.length];
                    var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        dis.readFully(shuffled);
                        unshuffleBytes(shuffled, rowBytes, width);
                        rowFloats.clear();
                        rowFloats.get(data[y]);
                    }
                    yield new ImageWrapper32(width, height, data, metadata);
                }
                case RGB -> {
                    float[][] r = new float[height][width];
                    float[][] g = new float[height][width];
                    float[][] b = new float[height][width];
                    var rowBytes = new byte[width * 3 * Float.BYTES];
                    var shuffled = new byte[rowBytes.length];
                    var rowFloats = ByteBuffer.wrap(rowBytes).asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        dis.readFully(shuffled);
                        unshuffleBytes(shuffled, rowBytes, width * 3);
                        rowFloats.clear();
                        var rRow = r[y];
                        var gRow = g[y];
                        var bRow = b[y];
                        for (int x = 0; x < width; x++) {
                            rRow[x] = rowFloats.get();
                            gRow[x] = rowFloats.get();
                            bRow[x] = rowFloats.get();
                        }
                    }
                    yield new RGBImage(width, height, r, g, b, metadata);
                }
                default -> throw new IllegalStateException("Undefined image kind " + kind);
            };
        } catch (IOException ex) {
            throw new ProcessingException(ex);
        } finally {
            inflater.end();
        }
    }

    /**
     * Byte-plane shuffle: regroups the {@code Float.BYTES} bytes of each float
     * by significance position. The high-order plane (sign + exponent) is
     * highly repetitive across a smooth solar image, so grouping like-bytes
     * together lets the deflater find much longer matches than it would on the
     * natural interleaved layout.
     */
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

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Map<Class<?>, Object> metadata() {
        return metadata;
    }

    @Override
    public ImageWrapper copy() {
        var source = unwrapped.get();
        return new FileBackedImage(width, height, backingFile, new LinkedHashMap<>(metadata), keptInMemory, source == null ? null : source.copy());
    }

    private void fileSaved() {
        status.fileSaved();
        unwrapped.source = null;
        unwrapped.clear();
    }

    /**
     * Registers a predicate telling whether the calling thread must never block
     * on disk I/O (typically the JavaFX application thread). When it returns
     * {@code true}, a pressure-triggered flush in {@link #wrap(ImageWrapper)} is
     * offloaded to a virtual thread instead of blocking the caller. Pass
     * {@code null} to restore the default (never offload).
     */
    public static void setMustNotBlockSupplier(BooleanSupplier supplier) {
        mustNotBlock = supplier != null ? supplier : () -> false;
    }


    /**
     * Test-only hook: replace the heap-pressure decision used by all eviction
     * paths. Pass {@code null} to restore the default runtime-based check.
     */
    static void setPressureSupplierForTesting(BooleanSupplier supplier) {
        underPressure = supplier != null ? supplier : () -> {
            var runtime = Runtime.getRuntime();
            return runtime.freeMemory() < 0.1 * runtime.totalMemory();
        };
    }

    /**
     * Test-only hook: forces the soft reference to be cleared and enqueued,
     * simulating a ZGC soft-reference clearing. Returns once the queue daemon
     * has processed the reference (rearmed or flushed).
     */
    void clearSoftReferenceForTesting() throws InterruptedException {
        var ref = unwrapped;
        ref.clear();
        ref.enqueue();
        // Wait for the daemon to process: either a rearm (new unwrapped instance)
        // or a flush (source becomes null).
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (unwrapped != ref) {
                return; // rearmed
            }
            if (ref.source == null) {
                return; // flushed
            }
            Thread.sleep(5);
        }
        throw new IllegalStateException("Daemon did not process the cleared soft reference within 5s");
    }

    private static void clean(Path backingFile) {
        LOCK.lock();
        try {
            int refCount = REF_COUNT.compute(backingFile, (k, v) -> {
                if (v == null) {
                    return 0;
                }
                return v - 1;
            });
            if (refCount == 0) {
                Files.deleteIfExists(backingFile);
                REF_COUNT.remove(backingFile);
                STATUS.remove(backingFile);
            }
        } catch (IOException e) {
            // ignore
        } finally {
            LOCK.unlock();
        }
    }

    private record Status(
            AtomicBoolean saved,
            Lock lock,
            List<Condition> conditions
    ) {
        Condition newCondition() {
            lock.lock();
            try {
                var condition = lock.newCondition();
                conditions.add(condition);
                return condition;
            } finally {
                lock.unlock();
            }
        }

        void fileSaved() {
            lock.lock();
            try {
                saved.set(true);
                for (var condition : conditions) {
                    condition.signalAll();
                }
                conditions.clear();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class ImageReference extends SoftReference<ImageWrapper> {
        private final FileBackedImage image;
        private volatile ImageWrapper source;

        public ImageReference(ImageWrapper referent, FileBackedImage image, ReferenceQueue<ImageWrapper> queue) {
            super(referent, queue);
            this.image = image;
            this.source = switch (referent) {
                case null -> null;
                case ImageWrapper32 mono ->
                        new ImageWrapper32(mono.width(), mono.height(), mono.data(), mono.metadata());
                case RGBImage rgb -> new RGBImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), rgb.metadata());
                default -> throw new IllegalArgumentException("Unexpected image type " + referent);
            };
        }

        public void flushToDisk() {
            var source = this.source;
            this.source = null;
            image.flushToDisk(source, "SOFTREF");
        }
    }
}
