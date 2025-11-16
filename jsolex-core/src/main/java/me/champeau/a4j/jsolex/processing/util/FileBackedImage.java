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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final ReferenceQueue<ImageWrapper> REFERENCE_QUEUE = new ReferenceQueue<>();


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
                    Thread.sleep(AUTOFLUSH_TIMEOUT);
                    long now = System.currentTimeMillis();
                    CACHE_LOCK.lock();
                    try {
                        var toFlush = WRAP_CACHE.values()
                                .stream()
                                .filter(fbi -> fbi.unwrapped.get() != null)
                                .filter(fbi -> now - fbi.lastAccessTime.get() > AUTOFLUSH_TIMEOUT)
                                .toList();
                        if (!toFlush.isEmpty()) {
                            LOGGER.debug("Auto-flushing {} images", toFlush.size());
                            for (var fbi : toFlush) {
                                fbi.flushToDisk(fbi.unwrapped.source);
                            }
                        }
                    } finally {
                        CACHE_LOCK.unlock();
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
    private ImageReference unwrapped;
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
        if (Runtime.getRuntime().freeMemory() < 0.1 * Runtime.getRuntime().totalMemory()) {
            flushToDisk(source);
        }
    }

    public static FileBackedImage wrap(ImageWrapper wrapper) {
        if (wrapper instanceof FileBackedImage fbi) {
            return fbi;
        }
        CACHE_LOCK.lock();
        // if memory is too close to full, flush some images to disk
        if (Runtime.getRuntime().freeMemory() < 0.1 * Runtime.getRuntime().totalMemory()) {
            flushImages();
        }
        try {
            var cached = WRAP_CACHE.get(wrapper);
            if (cached != null) {
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
    }

    public static void flushImages() {
        CACHE_LOCK.lock();
        try {
            System.gc();
            if (Runtime.getRuntime().freeMemory() < 0.25 * Runtime.getRuntime().totalMemory()) {
                // This helps with machines which have very low memory available
                Thread.sleep(1_000);
            }
            var fbiToFlush = WRAP_CACHE.values()
                    .stream()
                    .filter(fbi -> fbi.unwrapped.get() != null)
                    .toList();
            if (fbiToFlush.isEmpty()) {
                return;
            }
            LOGGER.debug("Flushing {} images to disk to free memory", fbiToFlush.size());
            try (var executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))) {
                for (var fbi : fbiToFlush) {
                    executor.submit(() -> {
                        if (Runtime.getRuntime().freeMemory() > 0.5 * Runtime.getRuntime().totalMemory()) {
                            return;
                        }
                        var status = fbi.status;
                        var condition = status.newCondition();
                        fbi.flushToDisk(fbi.unwrapped.source);
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
                    });
                }
            }
            System.gc();
            LOGGER.debug("Flushed images");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private void flushToDisk(ImageWrapper source) {
        if (source == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            status.lock().lock();
            try {
                if (status.saved.get() || REF_COUNT.getOrDefault(backingFile, 0) == 0) {
                    return;
                }
                if (source instanceof ImageWrapper32 mono) {
                    try (var fos = new BufferedOutputStream(new FileOutputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
                         var dos = new DataOutputStream(fos)) {
                        dos.writeByte(MONO);
                        dos.writeInt(height);
                        dos.writeInt(width);
                        var data = mono.data();
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                dos.writeFloat(data[y][x]);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (source instanceof RGBImage rgb) {
                    try (var fos = new BufferedOutputStream(new FileOutputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
                         var dos = new DataOutputStream(fos)) {
                        dos.writeByte(RGB);
                        dos.writeInt(height);
                        dos.writeInt(width);
                        var r = rgb.r();
                        var g = rgb.g();
                        var b = rgb.b();
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                dos.writeFloat(r[y][x]);
                                dos.writeFloat(g[y][x]);
                                dos.writeFloat(b[y][x]);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                fileSaved();
                status.lock().unlock();
            }
        });
    }

    public ImageWrapper unwrapToMemory() {
        lastAccessTime.set(System.currentTimeMillis());
        var cached = unwrapped.get();
        if (cached != null) {
            return cached;
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
        try (var fis = new BufferedInputStream(new FileInputStream(backingFile.toFile()), DEFAULT_RW_BUFFER_SIZE);
             var dis = new DataInputStream(fis)) {
            int kind = dis.readByte();
            int height = dis.readInt();
            int width = dis.readInt();
            return switch (kind) {
                case MONO -> {
                    float[][] data = new float[height][width];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            data[y][x] = dis.readFloat();
                        }
                    }
                    yield new ImageWrapper32(width, height, data, metadata);
                }
                case RGB -> {
                    float[][] r = new float[height][width];
                    float[][] g = new float[height][width];
                    float[][] b = new float[height][width];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            r[y][x] = dis.readFloat();
                            g[y][x] = dis.readFloat();
                            b[y][x] = dis.readFloat();
                        }
                    }
                    yield new RGBImage(width, height, r, g, b, metadata);
                }
                default -> throw new IllegalStateException("Undefined image kind " + kind);
            };
        } catch (IOException ex) {
            throw new ProcessingException(ex);
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
        private ImageWrapper source;

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
            image.flushToDisk(source);
        }
    }
}
