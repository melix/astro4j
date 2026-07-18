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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.ReferenceIntensities;
import me.champeau.a4j.jsolex.processing.spectrum.SerFileTrimmer;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.CancellationSupport;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

/**
 * Helper class for batch processing of SER files.
 * Handles the full orchestration of batch file processing, including UI setup,
 * thread management, and progress tracking.
 */
public final class BatchProcessingHelper {

    /**
     * Context interface for batch processing operations.
     * Implemented by the main application to provide necessary callbacks.
     */
    public interface BatchContext {
        void newSession();

        TabPane getMainPane();

        Button addInterruptButton();

        void removeInterruptButton(Button button);

        void setImageMathRunDisabled(boolean disabled);

        void updateProgress(ProgressOperation operation);

        void hideProgress();

        void suppressProgress();

        void processSingleFile(ProcessParams params,
                               ProgressOperation operation,
                               File selectedFile,
                               int sequenceNumber,
                               BatchProcessingContext context,
                               Header header,
                               Runnable onReconstructionComplete,
                               Runnable onComplete);

        TrimmingParameters getTrimmingParameters();

        File toTrimmedFile(File serFile);

        void updateProgress(double progress, String message);

        List<File> chooseAdditionalBatchFiles();

        File chooseBatchWatchDirectory();

        void startBatchComplementRun();
    }

    /**
     * Starts batch processing with full UI orchestration.
     *
     * @param context           the batch context providing callbacks
     * @param header            the SER file header
     * @param progressOperation the root progress operation
     * @param params            the processing parameters
     * @param selectedFiles     the files to process
     * @param autoTrimSerFile   whether to auto-trim SER files
     */
    public void startBatchProcess(BatchContext context,
                                  Header header,
                                  ProgressOperation progressOperation,
                                  ProcessParams params,
                                  List<File> selectedFiles,
                                  boolean autoTrimSerFile) {
        context.newSession();
        LOGGER.info(message("batch.mode.info"));

        var orderedFiles = maybeSortByCaptureDate(selectedFiles, params);
        var batchItems = createBatchItems(orderedFiles);
        var batchContext = createBatchContext(batchItems, orderedFiles.getFirst().getParentFile(), header);
        var table = BatchTableFactory.createBatchTable(batchItems, params);
        Runnable onAddFiles = () -> {
            var additionalFiles = context.chooseAdditionalBatchFiles();
            if (additionalFiles != null && !additionalFiles.isEmpty()) {
                complementBatch(context, batchContext, params, progressOperation, header, autoTrimSerFile, additionalFiles);
            }
        };
        var watcher = new BatchDirectoryWatcher(
                Configuration.getInstance().getWatchModeWaitTimeMilis(),
                () -> isBatchIdle(batchContext),
                detectedFiles -> {
                    var additionalFiles = removeFilesAlreadyInBatch(batchContext, detectedFiles);
                    if (!additionalFiles.isEmpty()) {
                        LOGGER.info(message("batch.watch.adding.files"), additionalFiles.size());
                        complementBatch(context, batchContext, params, progressOperation, header, autoTrimSerFile, additionalFiles);
                    }
                });
        var dashboard = BatchDashboard.create(batchItems, batchContext.batchScriptsRunning(), onAddFiles, watcher, context::chooseBatchWatchDirectory);
        var drawer = new BatchDetailDrawer(params);
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldItem, newItem) -> drawer.setItem(newItem));

        var splitPane = new SplitPane(table, drawer.getNode());
        splitPane.setDividerPositions(0.72);
        SplitPane.setResizableWithParent(drawer.getNode(), false);

        var content = new BorderPane();
        content.setTop(dashboard);
        content.setCenter(splitPane);

        var tab = new Tab(message("batch.process"));
        tab.setContent(content);

        var mainPane = context.getMainPane();
        var tabs = mainPane.getTabs();
        tabs.clear();
        tabs.add(tab);
        mainPane.getSelectionModel().select(0);

        var interruptButton = context.addInterruptButton();
        var interrupted = new AtomicBoolean();
        FxUtils.runLater(() -> context.setImageMathRunDisabled(true));

        var batchThread = runBatchProcessing(
                batchItems,
                batchContext,
                params,
                progressOperation,
                header,
                context,
                interrupted,
                autoTrimSerFile,
                () -> FxUtils.runLater(() -> {
                    context.removeInterruptButton(interruptButton);
                    context.setImageMathRunDisabled(false);
                })
        );

        interruptButton.setOnAction(e -> {
            interrupted.set(true);
            interruptButton.setDisable(true);
            BackgroundOperations.interrupt();
            batchThread.interrupt();
            context.suppressProgress();
            FxUtils.runLater(() -> context.setImageMathRunDisabled(false));
        });
    }

    /**
     * When scans are flipped alternately, the parity of a file only makes sense if files are
     * ordered by capture time, which the file chooser does not guarantee. Files whose header
     * cannot be read are sorted last, by name.
     */
    private static List<File> maybeSortByCaptureDate(List<File> files, ProcessParams params) {
        if (!params.extraParams().alternateScanDirection()) {
            return files;
        }
        var dates = new HashMap<File, ZonedDateTime>();
        for (var file : files) {
            try (var reader = SerFileReader.of(file, true)) {
                dates.put(file, reader.header().metadata().utcDateTime());
            } catch (Exception e) {
                LOGGER.warn(message("batch.unreadable.capture.date"), file.getName());
            }
        }
        return files.stream()
                .sorted(Comparator.comparing((File file) -> dates.get(file), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(File::getName))
                .toList();
    }

    /**
     * In back-and-forth scanning, every other scan runs in the opposite direction and must be
     * mirrored along the scan axis, which is horizontal at the stage where flips are applied.
     */
    private static ProcessParams adjustForScanDirection(ProcessParams params, int sequenceNumber) {
        if (!params.extraParams().alternateScanDirection() || sequenceNumber % 2 == 0) {
            return params;
        }
        var geometry = params.geometryParams();
        return params.withGeometryParams(geometry.withHorizontalMirror(!geometry.isHorizontalMirror()));
    }

    private ObservableList<BatchItem> createBatchItems(List<File> selectedFiles) {
        var batchItems = FXCollections.<BatchItem>observableArrayList();
        for (int i = 0; i < selectedFiles.size(); i++) {
            batchItems.add(createBatchItem(i, selectedFiles.get(i)));
        }
        return batchItems;
    }

    private BatchItem createBatchItem(int id, File selectedFile) {
        return new BatchItem(
                id,
                selectedFile,
                new SimpleDoubleProperty(0),
                FXCollections.synchronizedObservableList(FXCollections.observableArrayList()),
                new SimpleStringProperty(message("batch.pending")),
                new SimpleIntegerProperty(),
                new SimpleDoubleProperty(),
                new SimpleIntegerProperty(),
                new SimpleIntegerProperty(),
                new StringBuilder()
        );
    }

    /**
     * A batch is idle once all its files are processed and the end of batch handling
     * (images review, batch scripts) is done. Files must only be added to an idle batch.
     */
    private static boolean isBatchIdle(BatchProcessingContext batchContext) {
        return batchContext.batchFinished().get() && !batchContext.batchPostProcessing().get();
    }

    private static List<File> removeFilesAlreadyInBatch(BatchProcessingContext batchContext, List<File> files) {
        var known = batchContext.items()
                .stream()
                .map(item -> item.file().getAbsoluteFile())
                .collect(Collectors.toSet());
        return files.stream()
                .filter(file -> !known.contains(file.getAbsoluteFile()))
                .toList();
    }

    /**
     * Adds more files to an already-completed batch without reprocessing the
     * files already processed. The new files are processed with the original
     * batch's parameters into the same batch context, then the end-of-batch
     * scripts run again over the full set of results.
     */
    private void complementBatch(BatchContext context,
                                 BatchProcessingContext batchContext,
                                 ProcessParams params,
                                 ProgressOperation progressOperation,
                                 Header header,
                                 boolean autoTrimSerFile,
                                 List<File> additionalFiles) {
        var base = batchContext.items().size();
        var orderedFiles = maybeSortByCaptureDate(additionalFiles, params);
        var newItems = new ArrayList<BatchItem>(orderedFiles.size());
        for (int i = 0; i < orderedFiles.size(); i++) {
            newItems.add(createBatchItem(base + i, orderedFiles.get(i)));
        }
        batchContext.items().addAll(newItems);
        batchContext.batchFinished().set(false);
        context.startBatchComplementRun();

        var interruptButton = context.addInterruptButton();
        var interrupted = new AtomicBoolean();
        FxUtils.runLater(() -> context.setImageMathRunDisabled(true));

        var batchThread = runBatchProcessing(
                newItems,
                batchContext,
                params,
                progressOperation,
                header,
                context,
                interrupted,
                autoTrimSerFile,
                () -> FxUtils.runLater(() -> {
                    context.removeInterruptButton(interruptButton);
                    context.setImageMathRunDisabled(false);
                })
        );

        interruptButton.setOnAction(e -> {
            interrupted.set(true);
            interruptButton.setDisable(true);
            BackgroundOperations.interrupt();
            batchThread.interrupt();
            context.suppressProgress();
            FxUtils.runLater(() -> context.setImageMathRunDisabled(false));
        });
    }

    private BatchProcessingContext createBatchContext(List<BatchItem> batchItems,
                                                      File outputDirectory,
                                                      Header header) {
        return new BatchProcessingContext(
                batchItems,
                new HashSet<>(),
                new HashSet<>(),
                new AtomicBoolean(),
                outputDirectory,
                LocalDateTime.now(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                header,
                new HashMap<>(),
                new HashMap<>(),
                new ReentrantReadWriteLock(),
                System.nanoTime(),
                new AtomicBoolean(),
                new AtomicBoolean()
        );
    }

    private Thread runBatchProcessing(List<BatchItem> itemsToProcess,
                                      BatchProcessingContext batchContext,
                                      ProcessParams params,
                                      ProgressOperation progressOperation,
                                      Header header,
                                      BatchContext context,
                                      AtomicBoolean interrupted,
                                      boolean autoTrimSerFile,
                                      Runnable onComplete) {
        var batchThread = new Thread(() -> CancellationSupport.runWith(interrupted, () -> {
            // Eagerly initialize ReferenceIntensities to avoid virtual thread
            // pinning when all batch threads trigger class loading simultaneously
            var unused = ReferenceIntensities.INSTANCE;
            try {
                var parallelism = capParallelismForMemory(Configuration.getInstance().getBatchParallelism());
                var reconstructionSemaphore = new Semaphore(parallelism, true);
                var totalInFlightSemaphore = new Semaphore(parallelism + 1, true);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (int position = 0; position < itemsToProcess.size(); position++) {
                        if (Thread.currentThread().isInterrupted() || interrupted.get()) {
                            Thread.currentThread().interrupt();
                            interrupted.set(true);
                            break;
                        }
                        var batchItem = itemsToProcess.get(position);
                        var selectedFile = batchItem.file();
                        var sequenceNumber = batchItem.id();
                        var singleOperation = progressOperation.createChild(selectedFile.getName());
                        context.updateProgress(singleOperation);
                        int finalPosition = position;
                        try {
                            totalInFlightSemaphore.acquire();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            interrupted.set(true);
                            break;
                        }
                        try {
                            reconstructionSemaphore.acquire();
                        } catch (InterruptedException ie) {
                            totalInFlightSemaphore.release();
                            Thread.currentThread().interrupt();
                            interrupted.set(true);
                            break;
                        }
                        var reconstructionReleased = new AtomicBoolean(false);
                        Runnable releaseReconstructionOnce = () -> {
                            if (reconstructionReleased.compareAndSet(false, true)) {
                                reconstructionSemaphore.release();
                            }
                        };
                        executor.submit(() -> CancellationSupport.runWith(interrupted, () -> {
                            try {
                                processFile(
                                        context,
                                        params,
                                        singleOperation.update(((double) finalPosition) / itemsToProcess.size()),
                                        selectedFile,
                                        sequenceNumber,
                                        batchContext,
                                        header,
                                        interrupted,
                                        autoTrimSerFile,
                                        releaseReconstructionOnce
                                );
                            } finally {
                                releaseReconstructionOnce.run();
                                totalInFlightSemaphore.release();
                                context.updateProgress(singleOperation.complete());
                            }
                        }));
                    }
                }
            } finally {
                onComplete.run();
            }
        }));
        batchThread.start();
        return batchThread;
    }

    private void processFile(BatchContext context,
                             ProcessParams params,
                             ProgressOperation operation,
                             File selectedFile,
                             int sequenceNumber,
                             BatchProcessingContext batchContext,
                             Header header,
                             AtomicBoolean interrupted,
                             boolean autoTrimSerFile,
                             Runnable onReconstructionComplete) {
        if (interrupted.get()) {
            return;
        }
        var fileParams = adjustForScanDirection(params, sequenceNumber);
        context.processSingleFile(fileParams, operation, selectedFile, sequenceNumber, batchContext, header, onReconstructionComplete, () -> {
            if (autoTrimSerFile && !interrupted.get()) {
                var trimmingParams = context.getTrimmingParameters();
                if (trimmingParams != null) {
                    var outputFile = context.toTrimmedFile(trimmingParams.serFile());
                    SerFileTrimmer.trimFile(
                            trimmingParams.serFile(),
                            outputFile,
                            trimmingParams.firstFrame(),
                            trimmingParams.lastFrame(),
                            trimmingParams.pixelsUp(),
                            trimmingParams.pixelsDown(),
                            trimmingParams.minX(),
                            trimmingParams.maxX(),
                            trimmingParams.polynomial(),
                            trimmingParams.verticalFlip(),
                            progress -> context.updateProgress(progress, message("trimming"))
                    );
                    SerFileTrimmerController.maybeCopyMetadata(trimmingParams.serFile());
                }
            }
        });
    }

    /**
     * Caps batch parallelism to at most one worker per 4 GB of JVM heap, with
     * a floor of 1. The cap exists because each parallel file processing
     * holds a substantial in-flight working set (raw frames, reconstructed
     * images, async writers) — running more files concurrently than the
     * heap can comfortably hold leads to thrashing and OOMs regardless of
     * the per-process throttling we apply elsewhere. If the user-configured
     * parallelism exceeds the memory-derived cap we log a warning and use
     * the cap; otherwise the user's setting wins.
     */
    private static int capParallelismForMemory(int configured) {
        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        long bytesPerWorker = 4L * 1024 * 1024 * 1024;
        int memoryCap = (int) Math.max(1, maxHeapBytes / bytesPerWorker);
        if (configured > memoryCap) {
            long heapGb = maxHeapBytes / (1024 * 1024 * 1024);
            LOGGER.warn(message("batch.parallelism.capped"), configured, memoryCap, heapGb);
            return memoryCap;
        }
        return configured;
    }
}
