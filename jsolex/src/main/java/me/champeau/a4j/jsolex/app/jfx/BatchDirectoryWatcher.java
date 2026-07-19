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
package me.champeau.a4j.jsolex.app.jfx;

import me.champeau.a4j.jsolex.app.util.FxUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

/**
 * Watches a directory for new SER files and hands them over so that they can be added
 * to a batch. A file is only considered ready once its size stopped changing, and files
 * are only handed over when no batch processing is in progress, so that files are never
 * added to a batch which is still running. Files detected while the batch is running are
 * kept on hold and added as soon as it is finished.
 * The start and stop methods, as well as the ready files consumer, are called on the FX thread.
 */
public final class BatchDirectoryWatcher {
    private final int waitTimeMillis;
    private final BooleanSupplier batchIdle;
    private final Supplier<Set<Path>> batchFiles;
    private final Consumer<List<File>> onFilesReady;

    private final Map<Path, Long> growingFiles = new HashMap<>();
    private final Set<Path> knownFiles = ConcurrentHashMap.newKeySet();
    private final Set<Path> readyFiles = new LinkedHashSet<>();

    private WatchService watchService;
    private Thread watcherThread;
    private Path directory;

    public BatchDirectoryWatcher(int waitTimeMillis,
                                 BooleanSupplier batchIdle,
                                 Supplier<Set<Path>> batchFiles,
                                 Consumer<List<File>> onFilesReady) {
        this.waitTimeMillis = waitTimeMillis;
        this.batchIdle = batchIdle;
        this.batchFiles = batchFiles;
        this.onFilesReady = onFilesReady;
    }

    public boolean isWatching() {
        return watcherThread != null;
    }

    /**
     * Starts watching the given directory. Any previously watched directory is released.
     * Files which are already present when watching starts, as well as the files which are
     * already part of the batch, are recorded as known so that they are never added again.
     *
     * @param directoryToWatch the directory to watch
     * @return true if watching started
     */
    public boolean start(Path directoryToWatch) {
        stop();
        var dir = directoryToWatch.toAbsolutePath().normalize();
        WatchService service;
        try {
            service = FileSystems.getDefault().newWatchService();
            dir.register(service, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException e) {
            LOGGER.error(message("error.cannot.create.watch.service"), e);
            return false;
        }
        knownFiles.clear();
        growingFiles.clear();
        knownFiles.addAll(batchFiles.get());
        try (Stream<Path> existing = Files.list(dir)) {
            existing.filter(BatchDirectoryWatcher::isCandidate).forEach(knownFiles::add);
        } catch (IOException e) {
            LOGGER.error(message("error.cannot.create.watch.service"), e);
        }
        directory = dir;
        watchService = service;
        watcherThread = new Thread(() -> watchLoop(service, dir), "batch-directory-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        LOGGER.info(message("watching"), dir);
        return true;
    }

    /**
     * Stops watching. Files which were detected but not added to the batch yet are dropped.
     */
    public void stop() {
        if (watcherThread == null) {
            return;
        }
        watcherThread.interrupt();
        watcherThread = null;
        try {
            watchService.close();
        } catch (IOException e) {
            // ignore
        }
        watchService = null;
        synchronized (readyFiles) {
            readyFiles.clear();
        }
        LOGGER.info(message("stopped.watching"), directory);
        directory = null;
    }

    private void watchLoop(WatchService service, Path dir) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                collectStableFiles();
                collectNewFiles(service, dir);
                boolean hasReadyFiles;
                synchronized (readyFiles) {
                    hasReadyFiles = !readyFiles.isEmpty();
                }
                if (hasReadyFiles) {
                    FxUtils.runLater(this::addReadyFilesIfIdle);
                }
                Thread.sleep(waitTimeMillis);
            } catch (ClosedWatchServiceException e) {
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void collectStableFiles() {
        var iterator = growingFiles.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var path = entry.getKey();
            try {
                var currentSize = Files.size(path);
                if (currentSize == entry.getValue()) {
                    iterator.remove();
                    synchronized (readyFiles) {
                        readyFiles.add(path);
                    }
                    LOGGER.info(message("no.change.on.file"), path.getFileName());
                } else {
                    entry.setValue(currentSize);
                }
            } catch (IOException e) {
                iterator.remove();
                LOGGER.error(message("error.unable.determine.size"), path);
            }
        }
    }

    private void collectNewFiles(WatchService service, Path dir) {
        for (var key = service.poll(); key != null; key = service.poll()) {
            for (var event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                if (!(event.context() instanceof Path relative)) {
                    continue;
                }
                var path = dir.resolve(relative);
                if (!isCandidate(path) || !knownFiles.add(path)) {
                    continue;
                }
                try {
                    growingFiles.put(path, Files.size(path));
                    LOGGER.info(message("file.added.wait.list"), path.getFileName());
                } catch (IOException e) {
                    knownFiles.remove(path);
                    LOGGER.error(message("error.unable.determine.size"), path);
                }
            }
            if (!key.reset()) {
                key.cancel();
                break;
            }
        }
    }

    private static boolean isCandidate(Path path) {
        var file = path.toFile();
        return Files.isRegularFile(path)
               && file.getName().toLowerCase(Locale.US).endsWith(".ser")
               && !SerFileTrimmerController.isTrimmedFile(file);
    }

    private void addReadyFilesIfIdle() {
        if (!batchIdle.getAsBoolean()) {
            return;
        }
        List<File> files;
        synchronized (readyFiles) {
            if (readyFiles.isEmpty()) {
                return;
            }
            files = readyFiles.stream().map(Path::toFile).toList();
            readyFiles.clear();
        }
        onFilesReady.accept(files);
    }
}
