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

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.SerFileTrimmer;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

        void processSingleFile(ProcessParams params,
                               ProgressOperation operation,
                               File selectedFile,
                               int sequenceNumber,
                               BatchProcessingContext context,
                               Header header,
                               Runnable onComplete);

        TrimmingParameters getTrimmingParameters();

        File toTrimmedFile(File serFile);

        void updateProgress(double progress, String message);
    }

    /**
     * Starts batch processing with full UI orchestration.
     *
     * @param context the batch context providing callbacks
     * @param header the SER file header
     * @param progressOperation the root progress operation
     * @param params the processing parameters
     * @param selectedFiles the files to process
     * @param autoTrimSerFile whether to auto-trim SER files
     */
    public void startBatchProcess(BatchContext context,
                                   Header header,
                                   ProgressOperation progressOperation,
                                   ProcessParams params,
                                   List<File> selectedFiles,
                                   boolean autoTrimSerFile) {
        context.newSession();
        LOGGER.info(message("batch.mode.info"));

        var batchItems = createBatchItems(selectedFiles);
        var table = BatchTableFactory.createBatchTable(batchItems, params);

        var tab = new Tab(message("batch.process"));
        tab.setContent(table);

        var mainPane = context.getMainPane();
        var tabs = mainPane.getTabs();
        tabs.clear();
        tabs.add(tab);
        mainPane.getSelectionModel().select(0);

        var interruptButton = context.addInterruptButton();
        var interrupted = new AtomicBoolean();
        Platform.runLater(() -> context.setImageMathRunDisabled(true));

        var batchContext = createBatchContext(batchItems, selectedFiles.getFirst().getParentFile(), header);

        var batchThread = runBatchProcessing(
                selectedFiles,
                batchContext,
                params,
                progressOperation,
                header,
                context,
                interrupted,
                autoTrimSerFile,
                () -> Platform.runLater(() -> {
                    context.removeInterruptButton(interruptButton);
                    context.setImageMathRunDisabled(false);
                })
        );

        interruptButton.setOnAction(e -> {
            interrupted.set(true);
            interruptButton.setDisable(true);
            BackgroundOperations.interrupt();
            batchThread.interrupt();
            context.hideProgress();
            Platform.runLater(() -> context.setImageMathRunDisabled(false));
        });
    }

    private List<BatchItem> createBatchItems(List<File> selectedFiles) {
        var batchItems = new ArrayList<BatchItem>(selectedFiles.size());
        for (int i = 0; i < selectedFiles.size(); i++) {
            var selectedFile = selectedFiles.get(i);
            batchItems.add(new BatchItem(
                    i,
                    selectedFile,
                    new SimpleDoubleProperty(0),
                    FXCollections.synchronizedObservableList(FXCollections.observableArrayList()),
                    new SimpleStringProperty(message("batch.pending")),
                    new SimpleIntegerProperty(),
                    new SimpleDoubleProperty(),
                    new SimpleIntegerProperty(),
                    new SimpleIntegerProperty(),
                    new StringBuilder()
            ));
        }
        return batchItems;
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
                new ReentrantReadWriteLock()
        );
    }

    private Thread runBatchProcessing(List<File> selectedFiles,
                                       BatchProcessingContext batchContext,
                                       ProcessParams params,
                                       ProgressOperation progressOperation,
                                       Header header,
                                       BatchContext context,
                                       AtomicBoolean interrupted,
                                       boolean autoTrimSerFile,
                                       Runnable onComplete) {
        var batchThread = new Thread(() -> {
            try {
                try (var executor = Executors.newFixedThreadPool(2)) {
                    for (int fileIdx = 0; fileIdx < selectedFiles.size(); fileIdx++) {
                        if (Thread.currentThread().isInterrupted() || interrupted.get()) {
                            Thread.currentThread().interrupt();
                            interrupted.set(true);
                            break;
                        }
                        var selectedFile = selectedFiles.get(fileIdx);
                        var singleOperation = progressOperation.createChild(selectedFile.getName());
                        context.updateProgress(singleOperation);
                        int finalFileIdx = fileIdx;
                        executor.submit(() -> {
                            try {
                                processFile(
                                        context,
                                        params,
                                        singleOperation.update(((double) finalFileIdx) / selectedFiles.size()),
                                        selectedFile,
                                        finalFileIdx,
                                        batchContext,
                                        header,
                                        interrupted,
                                        autoTrimSerFile
                                );
                            } finally {
                                context.updateProgress(singleOperation.complete());
                            }
                        });
                    }
                }
            } finally {
                onComplete.run();
            }
        });
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
                              boolean autoTrimSerFile) {
        context.processSingleFile(params, operation, selectedFile, sequenceNumber, batchContext, header, () -> {
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
}
