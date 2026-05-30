/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.WriteColorizedImageTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.WriteMonoImageTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.WriteRGBImageTask;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ProcessingLogContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

/**
 * An image emitter is a utility tool to generate
 * mono or color images with transformations.
 *
 * <p>Emissions are submitted asynchronously to the supplied {@link Executor}.
 * Each emission's metadata is snapshot at submission time so concurrent
 * emissions can't race on the input image's metadata map. Callers must
 * invoke {@link #await()} before proceeding past the workflow boundary
 * to ensure all submitted writes have completed (and any
 * {@code ImageGeneratedEvent} listeners have run).
 *
 */
public class DefaultImageEmitter implements ImageEmitter {
    private static final int N_THREADS = Math.max(2, Math.min(
            Runtime.getRuntime().availableProcessors() / 2,
            (int) (Runtime.getRuntime().maxMemory() / (1L << 30))));
    private static final int IN_FLIGHT_LIMIT = N_THREADS * 2;
    private static final Semaphore DEFAULT_PERMITS = new Semaphore(IN_FLIGHT_LIMIT);
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(N_THREADS, r -> {
        var t = new Thread(r, "DefaultImageEmitter-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private static final Executor VIRTUAL_EXECUTOR = command -> Thread.startVirtualThread(command);

    private final Broadcaster broadcaster;
    private final ProgressOperation operation;
    private final File outputDir;
    private final Executor executor;
    private final AtomicInteger outstanding = new AtomicInteger();
    private final Lock awaitLock = new ReentrantLock();
    private final Condition idle = awaitLock.newCondition();
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    public DefaultImageEmitter(Broadcaster broadcaster,
                               ProgressOperation operation,
                               File outputDir) {
        this(broadcaster, operation, outputDir, DEFAULT_EXECUTOR);
    }

    public DefaultImageEmitter(Broadcaster broadcaster,
                               ProgressOperation operation,
                               File outputDir,
                               Executor executor) {
        this.broadcaster = broadcaster;
        this.operation = operation;
        this.outputDir = outputDir;
        this.executor = executor;
    }

    private static FileBackedImage snapshot(ImageWrapper32 image, GeneratedImageKind kind, String title, String name) {
        var metadata = new HashMap<>(image.metadata());
        metadata.put(GeneratedImageMetadata.class, new GeneratedImageMetadata(kind, title, name));
        return FileBackedImage.wrap(new ImageWrapper32(image.width(), image.height(), image.data(), metadata));
    }

    private void track(Runnable task, Executor exec) {
        var submissionSite = new Throwable("submission site");
        outstanding.incrementAndGet();
        ProcessingLogContext.runAsync(exec, () -> {
            try {
                task.run();
            } catch (Throwable t) {
                t.addSuppressed(submissionSite);
                throw t;
            }
        })
                .whenComplete((r, t) -> {
                    if (t != null) {
                        firstFailure.compareAndSet(null, t);
                    }
                    if (outstanding.decrementAndGet() == 0) {
                        awaitLock.lock();
                        try {
                            idle.signalAll();
                        } finally {
                            awaitLock.unlock();
                        }
                    }
                });
    }

    private void submit(Runnable task) {
        var permits = (executor == DEFAULT_EXECUTOR) ? DEFAULT_PERMITS : null;
        if (permits != null) {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessingException(e);
            }
        }
        track(() -> {
            try {
                task.run();
            } finally {
                if (permits != null) {
                    permits.release();
                }
            }
        }, executor);
    }

    private void prepareOutput(String name) {
        var file = outputDir.toPath().resolve(name);
        try {
            Path parent = file.getParent();
            createDirectoriesIfNeeded(parent);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image) {
        prepareOutput(name);
        var snapshot = snapshot(image, kind, title, name);
        // Bypass the bounded queue (mono writes are cheap) but still track
        // outstanding so await() is a true barrier for these writes too.
        track(() -> new WriteMonoImageTask(broadcaster,
                operation,
                () -> (ImageWrapper32) snapshot.unwrapToMemory(),
                outputDir,
                title,
                name,
                description,
                kind).get(), VIRTUAL_EXECUTOR);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier) {
        prepareOutput(name);
        var snapshot = snapshot(image, kind, title, name);
        submit(() -> new WriteColorizedImageTask(broadcaster,
                operation,
                () -> (ImageWrapper32) snapshot.unwrapToMemory(),
                outputDir,
                title,
                name,
                description,
                kind,
                rgbSupplier).get());
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier, BiConsumer<Graphics2D, ? super ImageWrapper> painter) {
        newColorImage(kind, null, title, name, description, image, img -> {
            var rgb = rgbSupplier.apply(img);
            var copy = new RGBImage(img.width(), img.height(), rgb[0], rgb[1], rgb[2], new HashMap<>(image.metadata()));
            ImageDraw.drawOnImageInPlace(copy, painter);
            return new float[][][]{copy.r(), copy.g(), copy.b()};
        });
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, int width, int height, Map<Class<?>, Object> metadata, Supplier<float[][][]> rgbSupplier) {
        prepareOutput(name);
        // The supplier here builds the source image lazily; capture by reference.
        var capturedMetadata = new HashMap<>(metadata);
        capturedMetadata.put(GeneratedImageMetadata.class, new GeneratedImageMetadata(kind, title, name));
        submit(() -> new WriteRGBImageTask(broadcaster,
                operation,
                () -> new ImageWrapper32(width, height, new float[height][width], capturedMetadata),
                outputDir,
                title,
                name,
                description,
                kind,
                rgbSupplier).get());
    }

    @Override
    public void newGenericFile(GeneratedImageKind kind, String category, String title, String name, String description, Path file) {
        prepareOutput(name);
        var fileName = file.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf("."));
        var targetFile = outputDir.toPath().resolve(name + extension);
        // File moves are cheap and small; keep them on the calling thread to
        // avoid races with subsequent operations that depend on the move.
        try {
            Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        broadcaster.broadcast(FileGeneratedEvent.of(kind, title, targetFile));
    }

    @Override
    public void await() {
        awaitLock.lock();
        try {
            while (outstanding.get() > 0) {
                idle.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        } finally {
            awaitLock.unlock();
        }
        var t = firstFailure.getAndSet(null);
        if (t != null) {
            throw ProcessingException.wrap(t.getCause() != null ? t.getCause() : t);
        }
    }
}
