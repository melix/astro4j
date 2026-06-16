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
package me.champeau.a4j.jsolex.processing.session;

import com.google.gson.Gson;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.ImageSerializer;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.MetadataIO;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.FileBasedScatterGatherBackingStore;
import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.apache.commons.compress.parallel.ScatterGatherBackingStoreSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

/**
 * Writes a {@link SessionData} to a compressed session archive, deflating entries in
 * parallel into a standard ZIP that {@link SessionReader} can read back.
 */
public final class SessionWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionWriter.class);
    static final int FORMAT_VERSION = 1;
    static final String MANIFEST_ENTRY = "manifest.json";
    private static final int MAX_COMPRESSION_THREADS = 8;

    private SessionWriter() {
    }

    /**
     * Writes the session to the given file.
     *
     * @param data     the session content
     * @param target   the destination file
     * @param listener an optional progress listener (may be {@code null})
     */
    public static void write(SessionData data, Path target, SessionProgressListener listener) {
        Path tmp = null;
        try {
            var parent = target.toAbsolutePath().getParent();
            tmp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
            writeArchive(data, tmp, listener);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new ProcessingException(e);
        } catch (RuntimeException | Error e) {
            deleteQuietly(tmp);
            throw e;
        }
    }

    private static void writeArchive(SessionData data, Path target, SessionProgressListener listener) throws IOException {
        var gson = new Gson();
        var imageEntries = new ArrayList<SessionManifest.ImageEntry>();
        var mediaEntries = new ArrayList<SessionManifest.MediaEntry>();
        var batchImageCount = countBatchImages(data.reRun());
        var total = Math.max(1, data.images().size() + data.media().size() + batchImageCount);
        var progress = new AtomicInteger();

        var threads = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, MAX_COMPRESSION_THREADS));
        var executor = Executors.newFixedThreadPool(threads);
        var scatterDir = Files.createTempDirectory("jsolex-session-scatter");
        try {
            ScatterGatherBackingStoreSupplier backing = () ->
                    new FileBasedScatterGatherBackingStore(Files.createTempFile(scatterDir, "scatter", ".tmp"));
            var zipCreator = new ParallelScatterZipCreator(executor, backing, Deflater.BEST_COMPRESSION);

            for (int i = 0; i < data.images().size(); i++) {
                var image = data.images().get(i);
                var id = "img-" + i;
                submitReporting(zipCreator, deflatedEntry("images/" + id + ".bin"), pixelSupplier(image.image(), image.title()), progress, total, image.title(), listener);
                zipCreator.addArchiveEntry(deflatedEntry("images/" + id + ".meta.json"), metaSupplier(image.image(), image.title()));
                imageEntries.add(new SessionManifest.ImageEntry(id, image.kind().name(), image.title(), image.baseName(), image.description()));
            }
            for (int i = 0; i < data.media().size(); i++) {
                var media = data.media().get(i);
                if (media.file() == null || !Files.isReadable(media.file())) {
                    LOGGER.warn("Skipping media '{}' because its file is not available", media.title());
                    progress.incrementAndGet();
                    continue;
                }
                var id = "media-" + i;
                var fileName = id + extension(media.file());
                var mediaFile = media.file();
                var title = media.title();
                submitReporting(zipCreator, deflatedEntry("media/" + fileName), () -> {
                    try {
                        return Files.newInputStream(mediaFile);
                    } catch (IOException e) {
                        LOGGER.warn("Could not read media '{}': {}", title, e.getMessage());
                        return emptyStream();
                    }
                }, progress, total, title, listener);
                mediaEntries.add(new SessionManifest.MediaEntry(id, media.kind().name(), media.title(), media.description(), fileName, media.type().name()));
            }

            var reRunManifest = addReRunEntries(zipCreator, data.reRun(), total, progress, listener);
            var manifest = new SessionManifest(FORMAT_VERSION, VersionUtil.getVersion(), imageEntries, mediaEntries, reRunManifest);
            var manifestBytes = gson.toJson(manifest).getBytes(StandardCharsets.UTF_8);
            zipCreator.addArchiveEntry(deflatedEntry(MANIFEST_ENTRY), () -> new ByteArrayInputStream(manifestBytes));

            try (var zaos = new ZipArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
                zipCreator.writeTo(zaos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Session export was interrupted", e);
            } catch (ExecutionException e) {
                var cause = e.getCause();
                throw new IOException("Session export failed", cause != null ? cause : e);
            }
        } finally {
            executor.shutdownNow();
            deleteRecursivelyQuietly(scatterDir);
        }
    }

    private static InputStreamSupplier pixelSupplier(ImageWrapper wrapper, String title) {
        return () -> {
            try {
                var buffer = new ByteArrayOutputStream();
                ImageSerializer.write(new DataOutputStream(buffer), wrapper);
                return new ByteArrayInputStream(buffer.toByteArray());
            } catch (RuntimeException | IOException e) {
                LOGGER.warn("Skipping image '{}' which could not be serialized: {}", title, e.getMessage());
                return emptyStream();
            }
        };
    }

    private static InputStreamSupplier metaSupplier(ImageWrapper wrapper, String title) {
        return () -> {
            try {
                return new ByteArrayInputStream(MetadataIO.serialize(snapshotMetadata(wrapper.metadata())).getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                LOGGER.warn("Could not serialize metadata for '{}': {}", title, e.getMessage());
                return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static int countBatchImages(SessionReRunData reRun) {
        if (reRun == null || reRun.batchOutputs() == null) {
            return 0;
        }
        return reRun.batchOutputs().imagesByLabel().values().stream().mapToInt(List::size).sum();
    }

    private static SessionManifest.ReRun addReRunEntries(ParallelScatterZipCreator zipCreator,
                                                         SessionReRunData reRun,
                                                         int total,
                                                         AtomicInteger progress,
                                                         SessionProgressListener listener) {
        if (reRun == null || reRun.isEmpty()) {
            return null;
        }
        var singleRuns = new ArrayList<SessionManifest.SingleRun>();
        if (reRun.singleRuns() != null) {
            for (int i = 0; i < reRun.singleRuns().size(); i++) {
                var run = reRun.singleRuns().get(i);
                var contextEntry = "rerun/run-" + i + ".json";
                var context = new LinkedHashMap<Class<?>, Object>();
                if (run.params() != null) {
                    context.put(ProcessParams.class, run.params());
                }
                if (run.ellipse() != null) {
                    context.put(Ellipse.class, run.ellipse());
                }
                if (run.pixelShiftRange() != null) {
                    context.put(PixelShiftRange.class, run.pixelShiftRange());
                }
                var json = MetadataIO.serialize(context);
                zipCreator.addArchiveEntry(deflatedEntry(contextEntry),
                        () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                singleRuns.add(new SessionManifest.SingleRun(run.serFilePath(), contextEntry));
            }
        }
        SessionManifest.Batch batch = null;
        var outputs = reRun.batchOutputs();
        if (outputs != null) {
            var labels = new ArrayList<SessionManifest.Label>();
            var counter = new AtomicInteger();
            var values = outputs.valuesByLabel() != null ? outputs.valuesByLabel() : Map.<String, List<Object>>of();
            outputs.imagesByLabel().forEach((label, images) -> {
                var ids = new ArrayList<String>();
                for (var image : images) {
                    var id = "rb-" + counter.getAndIncrement();
                    ids.add(id);
                    submitReporting(zipCreator, deflatedEntry("rerun/batch/" + id + ".bin"), pixelSupplier(image, label), progress, total, label, listener);
                    zipCreator.addArchiveEntry(deflatedEntry("rerun/batch/" + id + ".meta.json"), metaSupplier(image, label));
                }
                labels.add(new SessionManifest.Label(label, ids, sanitizeValues(values.getOrDefault(label, List.of()))));
            });
            values.forEach((label, vals) -> {
                if (!outputs.imagesByLabel().containsKey(label)) {
                    labels.add(new SessionManifest.Label(label, List.of(), sanitizeValues(vals)));
                }
            });
            batch = new SessionManifest.Batch(labels);
        }
        return new SessionManifest.ReRun(singleRuns, batch);
    }

    private static List<Object> sanitizeValues(List<Object> values) {
        var out = new ArrayList<Object>();
        for (var value : values) {
            if (value instanceof Number || value instanceof String || value instanceof Boolean) {
                out.add(value);
            } else {
                LOGGER.debug("Skipping non-scalar batch value of type {}", value == null ? "null" : value.getClass());
            }
        }
        return out;
    }

    /**
     * Submits an entry for parallel compression and reports progress once it completes.
     */
    private static void submitReporting(ParallelScatterZipCreator zipCreator,
                                        ZipArchiveEntry entry,
                                        InputStreamSupplier supplier,
                                        AtomicInteger progress,
                                        int total,
                                        String title,
                                        SessionProgressListener listener) {
        var callable = zipCreator.createCallable(entry, supplier);
        zipCreator.submitStreamAwareCallable(() -> {
            var result = callable.call();
            report(listener, progress.incrementAndGet(), total, title);
            return result;
        });
    }

    private static ZipArchiveEntry deflatedEntry(String name) {
        var entry = new ZipArchiveEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        return entry;
    }

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static void deleteRecursivelyQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(SessionWriter::deleteQuietly);
        } catch (IOException e) {
            LOGGER.debug("Could not clean up scatter directory {}", dir);
        }
    }

    private static Map<Class<?>, Object> snapshotMetadata(Map<Class<?>, Object> metadata) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return new LinkedHashMap<>(metadata);
            } catch (ConcurrentModificationException e) {
                LOGGER.debug("Concurrent metadata modification during export, retrying");
            }
        }
        return Map.of();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            LOGGER.debug("Could not delete temporary session file {}", path);
        }
    }

    private static void report(SessionProgressListener listener, int done, int total, String item) {
        if (listener != null) {
            listener.onProgress((double) done / total, item);
        }
    }

    private static String extension(Path file) {
        var name = file.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
