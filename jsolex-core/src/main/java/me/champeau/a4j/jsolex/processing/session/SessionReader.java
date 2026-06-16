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
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageSerializer;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.MetadataIO;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads a session archive produced by {@link SessionWriter} back into a
 * {@link SessionData}. Images are loaded into memory; media files are extracted
 * to the temporary folder. Entries referring to image kinds unknown to this
 * version are skipped, so a session can still be opened by an older build.
 */
public final class SessionReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionReader.class);

    private SessionReader() {
    }

    /**
     * Reads the session stored in the given file.
     *
     * @param source   the session file
     * @param listener an optional progress listener (may be {@code null})
     * @return the restored session content
     */
    public static SessionData read(Path source, SessionProgressListener listener) {
        var gson = new Gson();
        var images = new ArrayList<SessionImage>();
        var media = new ArrayList<SessionMedia>();
        try (var zip = new ZipFile(source.toFile())) {
            var manifestEntry = zip.getEntry(SessionWriter.MANIFEST_ENTRY);
            if (manifestEntry == null) {
                throw new ProcessingException("Not a valid JSol'Ex session file (missing manifest)");
            }
            SessionManifest manifest;
            try (var reader = new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)) {
                manifest = gson.fromJson(reader, SessionManifest.class);
            }
            if (manifest.version() > SessionWriter.FORMAT_VERSION) {
                throw new ProcessingException("This session was created with a newer version of JSol'Ex and cannot be opened");
            }
            var total = Math.max(1, manifest.images().size() + manifest.media().size());
            var done = 0;
            for (var entry : manifest.images()) {
                report(listener, ++done, total, entry.title());
                var kind = parseKind(entry.kind());
                if (kind != null) {
                    var image = readImage(zip, entry, kind);
                    if (image != null) {
                        images.add(image);
                    }
                }
            }
            for (var entry : manifest.media()) {
                report(listener, ++done, total, entry.title());
                var kind = parseKind(entry.kind());
                if (kind != null) {
                    var extracted = extractMedia(zip, entry);
                    if (extracted != null) {
                        media.add(new SessionMedia(kind, entry.title(), entry.description(), extracted, parseType(entry.type())));
                    }
                }
            }
            var reRun = readReRun(zip, manifest.reRun());
            return new SessionData(images, media, reRun);
        } catch (ZipException e) {
            throw new ProcessingException("This file is not a valid JSol'Ex session file");
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static SessionReRunData readReRun(ZipFile zip, SessionManifest.ReRun reRun) {
        if (reRun == null) {
            return null;
        }
        var singleRuns = new ArrayList<SessionSingleRun>();
        if (reRun.singleRuns() != null) {
            for (var run : reRun.singleRuns()) {
                try {
                    var context = MetadataIO.deserialize(readString(zip, run.contextEntry()));
                    var params = (ProcessParams) context.get(ProcessParams.class);
                    var ellipse = (Ellipse) context.get(Ellipse.class);
                    var range = (PixelShiftRange) context.get(PixelShiftRange.class);
                    singleRuns.add(new SessionSingleRun(run.serFilePath(), params, ellipse, range));
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Could not read re-run context {}: {}", run.contextEntry(), e.getMessage());
                }
            }
        }
        SessionBatchOutputs batchOutputs = null;
        if (reRun.batch() != null) {
            var imagesByLabel = new LinkedHashMap<String, List<ImageWrapper>>();
            var valuesByLabel = new LinkedHashMap<String, List<Object>>();
            for (var label : reRun.batch().labels()) {
                if (label.imageIds() != null && !label.imageIds().isEmpty()) {
                    var list = new ArrayList<ImageWrapper>();
                    for (var id : label.imageIds()) {
                        var image = readImageWrapper(zip, "rerun/batch/" + id);
                        if (image != null) {
                            list.add(image);
                        }
                    }
                    imagesByLabel.put(label.label(), list);
                }
                if (label.values() != null && !label.values().isEmpty()) {
                    valuesByLabel.put(label.label(), new ArrayList<>(label.values()));
                }
            }
            batchOutputs = new SessionBatchOutputs(imagesByLabel, valuesByLabel);
        }
        return new SessionReRunData(singleRuns, batchOutputs);
    }

    private static SessionImage readImage(ZipFile zip, SessionManifest.ImageEntry entry, GeneratedImageKind kind) {
        var image = readImageWrapper(zip, "images/" + entry.id());
        if (image == null) {
            return null;
        }
        return new SessionImage(kind, entry.title(), entry.baseName(), entry.description(), image);
    }

    private static ImageWrapper readImageWrapper(ZipFile zip, String entryPrefix) {
        var binEntry = zip.getEntry(entryPrefix + ".bin");
        if (binEntry == null) {
            LOGGER.warn("Missing image data for entry {} in session file", entryPrefix);
            return null;
        }
        try {
            var metadata = MetadataIO.deserialize(readString(zip, entryPrefix + ".meta.json"));
            try (var dis = new DataInputStream(new BufferedInputStream(zip.getInputStream(binEntry)))) {
                return ImageSerializer.read(dis, metadata);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read image {} from session file: {}", entryPrefix, e.getMessage());
            return null;
        }
    }

    private static Path extractMedia(ZipFile zip, SessionManifest.MediaEntry entry) throws IOException {
        var zipEntry = zip.getEntry("media/" + entry.fileName());
        if (zipEntry == null) {
            LOGGER.warn("Missing media entry {} in session file", entry.fileName());
            return null;
        }
        var dot = entry.fileName().lastIndexOf('.');
        var suffix = dot >= 0 ? entry.fileName().substring(dot) : "";
        var target = TemporaryFolder.newTempFile("jsolex-session", suffix);
        try (var in = zip.getInputStream(zipEntry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static String readString(ZipFile zip, String entryName) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null) {
            return "";
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static GeneratedImageKind parseKind(String name) {
        try {
            return GeneratedImageKind.valueOf(name);
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Skipping entry with unknown image kind {}", name);
            return null;
        }
    }

    private static SessionMedia.Type parseType(String name) {
        try {
            return SessionMedia.Type.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return SessionMedia.Type.VIDEO;
        }
    }

    private static void report(SessionProgressListener listener, int done, int total, String item) {
        if (listener != null) {
            listener.onProgress((double) done / total, item);
        }
    }
}
