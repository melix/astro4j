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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An image wrapper where data is stored on disk instead of
 * memory.
 */
public final class FileBackedImage implements ImageWrapper {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Map<Path, Integer> REF_COUNT = new HashMap<>();
    private static final Lock LOCK = new ReentrantLock();
    private static final byte MONO = 0;
    private static final byte RGB = 2;

    private final int width;
    private final int height;
    private final Path backingFile;
    private final Map<Class<?>, Object> metadata;
    private final Object keptInMemory;
    private final WeakReference<ImageWrapper> unwrapped;

    private FileBackedImage(int width, int height, Path backingFile, Map<Class<?>, Object> metadata, Object keptInMemory, ImageWrapper source) {
        this.width = width;
        this.height = height;
        this.backingFile = backingFile;
        this.metadata = metadata;
        this.keptInMemory = keptInMemory;
        this.unwrapped = new WeakReference<>(source.unwrapToMemory());
        LOCK.lock();
        try {
            REF_COUNT.compute(backingFile, (k, v) -> v == null ? 1 : v + 1);
        } finally {
            LOCK.unlock();
        }
        CLEANER.register(this, () -> clean(backingFile));
    }

    public static FileBackedImage wrap(ImageWrapper wrapper) {
        if (wrapper instanceof FileBackedImage fbi) {
            return fbi;
        }
        var width = wrapper.width();
        var height = wrapper.height();
        try {
            var tempDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("jsolex");
            Files.createDirectories(tempDir);
            var backingFile = Files.createTempFile(tempDir, "jsolex", ".img");
            backingFile.toFile().deleteOnExit();
            try (var raf = new RandomAccessFile(backingFile.toFile(), "rw")) {
                var channel = raf.getChannel();
                if (wrapper instanceof ImageWrapper32 mono) {
                    var data = mono.data();
                    var length = mono.width() * mono.height();
                    var byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1 + 4 + 4 + 4L * length);
                    byteBuffer.put(MONO);
                    byteBuffer.putInt(height);
                    byteBuffer.putInt(width);
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        floatBuffer.put(data[y]);
                    }
                    return new FileBackedImage(width, height, backingFile, mono.metadata(), null, wrapper);
                }
                if (wrapper instanceof RGBImage rgb) {
                    var r = rgb.r();
                    var g = rgb.g();
                    var b = rgb.b();
                    var length = rgb.width() * rgb.height();
                    var byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1 + 4 + 4 + 3 * 4L * length);
                    byteBuffer.put(RGB);
                    byteBuffer.putInt(rgb.height());
                    byteBuffer.putInt(rgb.width());
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        floatBuffer.put(r[y]);
                        floatBuffer.put(g[y]);
                        floatBuffer.put(b[y]);
                    }
                    return new FileBackedImage(width, height, backingFile, rgb.metadata(), null, wrapper);
                }
                throw new ProcessingException("Unexpected image type " + wrapper);
            }
        } catch (IOException ex) {
            throw new ProcessingException(ex);
        }
    }

    public ImageWrapper unwrapToMemory() {
        var cached = unwrapped.get();
        if (cached != null) {
            return cached;
        }
        try (var raf = new RandomAccessFile(backingFile.toFile(), "r")) {
            var channel = raf.getChannel();
            var byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            int kind = byteBuffer.get();
            int height = byteBuffer.getInt();
            int width = byteBuffer.getInt();
            return switch (kind) {
                case MONO -> {
                    // Mono image
                    float[][] data = new float[height][width];
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        data[y] = new float[width];
                        floatBuffer.get(data[y]);
                    }
                    yield new ImageWrapper32(width, height, data, metadata);
                }
                case RGB -> {
                    // RGB image
                    float[][] r = new float[height][width];
                    float[][] g = new float[height][width];
                    float[][] b = new float[height][width];
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    for (int y = 0; y < height; y++) {
                        r[y] = new float[width];
                        g[y] = new float[width];
                        b[y] = new float[width];
                        floatBuffer.get(r[y]);
                        floatBuffer.get(g[y]);
                        floatBuffer.get(b[y]);
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
        return new FileBackedImage(width, height, backingFile, new LinkedHashMap<>(metadata), keptInMemory, this);
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
                Files.delete(backingFile);
                REF_COUNT.remove(backingFile);
            }
        } catch (IOException e) {
            // ignore
        } finally {
            LOCK.unlock();
        }
    }
}
