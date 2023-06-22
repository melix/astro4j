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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class BatchModeEventListener implements ProcessingEventListener {

    private final JSolExInterface owner;
    private final ProcessParams processParams;
    private final BatchItem item;
    private final AtomicInteger completed;
    private final double totalItems;
    private final File outputDirectory;
    private final LocalDateTime processingDate;
    private final int sequenceNumber;

    private Header header;

    public BatchModeEventListener(JSolExInterface owner,
                                  int sequenceNumber,
                                  BatchProcessingContext context,
                                  ProcessParams processParams) {
        this.owner = owner;
        this.processParams = processParams;
        this.completed = context.progress();
        this.outputDirectory = context.outputDirectory();
        this.processingDate = context.processingDate();
        this.item = context.items().stream().filter(batchItem -> batchItem.id() == sequenceNumber).findFirst().get();
        this.totalItems = context.items().size();
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var stretchingStrategy = payload.stretchingStrategy();
        var image = payload.image();
        var target = payload.path().toFile();
        owner.getCpuExecutor().async(() -> {
            new ImageSaver(stretchingStrategy, processParams).save(image, target);
            item.generatedFiles().add(target);
        });
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        item.reconstructionProgress().setValue(payload.line() / (double) payload.totalLines());
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.info(JSolEx.message("dimensions.determined"), event.getLabel(), event.getWidth(), event.getHeight());
        item.reconstructionProgress().setValue(1.0);
    }

    @Override
    public void onVideoMetadataAvailable(VideoMetadataEvent event) {
        this.header = event.getPayload();
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        item.status().set(JSolEx.message("batch.started"));
        updateProgressStatus(false);
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        updateProgressStatus(true);
        maybeWriteLogs();
        if (item.status().get().equals(JSolEx.message("batch.error"))) {
            return;
        }
        item.status().set(JSolEx.message("batch.ok"));
    }

    private void maybeWriteLogs() {
        if (item.log().length() > 0 && header != null) {
            var namingStrategy = new FileNamingStrategy(
                    processParams.debugParams().fileNamePattern(),
                    processingDate,
                    header
            );
            var fileName = item.file().getName();
            var logFileName = namingStrategy.render(sequenceNumber, "log", "notifications", fileName.substring(0, fileName.lastIndexOf("."))) + ".txt";
            try {
                var logFilePath = outputDirectory.toPath().resolve(logFileName);
                Files.writeString(logFilePath, item.log().toString(), Charset.defaultCharset());
                item.generatedFiles().add(logFilePath.toFile());
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void updateProgressStatus(boolean increment) {
        var done = increment ? completed.incrementAndGet() : completed.get();
        BatchOperations.submitOneOfAKind("progress", () -> {
            var prog = done / totalItems;
            if (completed.get() == (int) totalItems) {
                owner.showProgress();
                owner.updateProgress(1.0, String.format(JSolEx.message("batch.finished"), done, (int) totalItems));
            } else {
                owner.showProgress();
                owner.updateProgress(prog, String.format(JSolEx.message("batch.progress"), done, (int) totalItems));
            }
        });
    }

    @Override
    public void onNotification(NotificationEvent e) {
        synchronized (item) {
            item.log()
                    .append(e.type()).append(": ")
                    .append(e.title()).append(" ")
                    .append(e.header()).append(" ")
                    .append(e.message()).append("\n");
        }
        if (e.type() == Notification.AlertType.ERROR) {
            item.status().set(JSolEx.message("batch.error"));
        }
    }
}
