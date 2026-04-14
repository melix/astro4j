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
package me.champeau.a4j.jsolex.processing.event;

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.LiveSessionManager;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class LivePushEventListener implements ProcessingEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LivePushEventListener.class);

    private final LiveSessionManager liveSessionManager;
    private final ImageSaver saver;
    private final AtomicLong counter = new AtomicLong(0);
    private volatile Broadcaster broadcaster = Broadcaster.NO_OP;

    public LivePushEventListener(LiveSessionManager liveSessionManager) {
        this.liveSessionManager = liveSessionManager;
        var params = ProcessParams.loadDefaults();
        this.saver = new ImageSaver(
            RangeExpansionStrategy.DEFAULT,
            params,
            Set.of(ImageFormat.JPG)
        );
    }

    public void setBroadcaster(Broadcaster broadcaster) {
        this.broadcaster = broadcaster != null ? broadcaster : Broadcaster.NO_OP;
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        if (!liveSessionManager.isActive()) {
            return;
        }
        var payload = event.getPayload();
        var imageKind = payload.kind();
        if (imageKind == GeneratedImageKind.RAW || imageKind == GeneratedImageKind.RECONSTRUCTION) {
            return;
        }
        var displayCategory = imageKind.displayCategory();
        var category = Constants.message("displayCategory." + displayCategory.name());
        var kindName = imageKind.name();
        var title = payload.title();
        var observationDate = payload.image()
                .findMetadata(SourceInfo.class)
                .map(s -> s.dateTime().toInstant().toString())
                .orElse(null);

        var tempDir = TemporaryFolder.tempDir().resolve("live-images");
        var id = counter.incrementAndGet();
        var name = "live_" + id + ".jpg";
        var saved = tempDir.resolve(name).toFile();
        saved.deleteOnExit();
        try {
            saver.save(payload.image(), saved);
            var jpgBytes = Files.readAllBytes(saved.toPath());
            var currentBroadcaster = broadcaster;
            var operation = ProgressOperation.root(Constants.message("live.uploading") + " " + title, op -> {});
            liveSessionManager.pushImage(
                category, title, kindName, jpgBytes, observationDate,
                () -> currentBroadcaster.broadcast(ProgressEvent.of(operation.update(0, Constants.message("live.uploading") + " " + title))),
                () -> currentBroadcaster.broadcast(ProgressEvent.of(operation.complete(Constants.message("live.uploaded") + " " + title))));
        } catch (IOException e) {
            LOGGER.warn("Failed to read temporary live image: {}", e.getMessage());
        } finally {
            deleteQuietly(saved);
        }
    }

    private static void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
