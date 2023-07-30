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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * An image wrapper where data is stored on disk instead of
 * memory.
 */
public final class FileBackedImage implements ImageWrapper {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final byte MONO = 0;
    private static final byte COLORIZED = 1;
    private static final byte RGB = 2;

    private final int width;
    private final int height;
    private final Path backingFile;
    private final Object keptInMemory;

    private FileBackedImage(int width, int height, Path backingFile, Object keptInMemory) {
        this.width = width;
        this.height = height;
        this.backingFile = backingFile;
        this.keptInMemory = keptInMemory;
        CLEANER.register(this, () -> clean(backingFile));
    }

    public static FileBackedImage wrap(ImageWrapper wrapper) {
        if (wrapper instanceof FileBackedImage fbi) {
            return fbi;
        }
        var width = wrapper.width();
        var height = wrapper.height();
        try {
            var backingFile = Files.createTempFile("jsolex", ".img");
            backingFile.toFile().deleteOnExit();
            try (var raf = new RandomAccessFile(backingFile.toFile(), "rw")) {
                var channel = raf.getChannel();
                if (wrapper instanceof ImageWrapper32 mono) {
                    var data = mono.data();
                    var byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1 + 4 + 4L * data.length);
                    byteBuffer.put(MONO);
                    byteBuffer.putInt(data.length);
                    byteBuffer.asFloatBuffer().put(data);
                    return new FileBackedImage(width, height, backingFile, null);
                }
                if (wrapper instanceof ColorizedImageWrapper colorized) {
                    var data = colorized.mono().data();
                    var byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1 + 4 + 4L * data.length);
                    byteBuffer.put(COLORIZED);
                    byteBuffer.putInt(data.length);
                    byteBuffer.asFloatBuffer().put(data);
                    return new FileBackedImage(width, height, backingFile, colorized.converter());
                }
                if (wrapper instanceof RGBImage rgb) {
                    var r = rgb.r();
                    var g = rgb.g();
                    var b = rgb.b();
                    var byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1 + 4 + 3 * 4L * r.length);
                    byteBuffer.put(RGB);
                    byteBuffer.putInt(r.length);
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    floatBuffer.put(r);
                    floatBuffer.put(g);
                    floatBuffer.put(b);
                    return new FileBackedImage(width, height, backingFile, null);
                }
                throw new ProcessingException("Unexpected image type " + wrapper);
            }
        } catch (IOException ex) {
            throw new ProcessingException(ex);
        }
    }

    public ImageWrapper unwrapToMemory() {
        try (var raf = new RandomAccessFile(backingFile.toFile(), "r")) {
            var channel = raf.getChannel();
            var byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            int kind = byteBuffer.get();
            int size = byteBuffer.getInt();
            return switch (kind) {
                case MONO -> {
                    // Mono image
                    float[] data = new float[size];
                    byteBuffer.asFloatBuffer().get(data);
                    yield new ImageWrapper32(width, height, data);
                }
                case COLORIZED -> {
                    // Colorized image
                    float[] data = new float[size];
                    byteBuffer.asFloatBuffer().get(data);
                    yield new ColorizedImageWrapper(new ImageWrapper32(width, height, data), (Function<float[], float[][]>) keptInMemory);
                }
                case RGB -> {
                    // RGB image
                    float[] r = new float[size];
                    float[] g = new float[size];
                    float[] b = new float[size];
                    var floatBuffer = byteBuffer.asFloatBuffer();
                    floatBuffer.get(r);
                    floatBuffer.get(g);
                    floatBuffer.get(b);
                    yield new RGBImage(width, height, r, g, b);
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
    public ImageWrapper copy() {
        throw new UnsupportedOperationException();
    }

    private static void clean(Path backingFile) {
        try {
            Files.delete(backingFile);
        } catch (IOException e) {
            // ignore
        }
    }
}
