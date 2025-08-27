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

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.CandidateImageDescriptor;
import me.champeau.a4j.jsolex.app.jfx.Corrector;
import me.champeau.a4j.jsolex.app.jfx.ImageInspectorController;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.EllipseFittingRequestEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.TrimmingParametersDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.BestImages;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.NamingStrategyAwareImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class BatchModeEventListener implements ProcessingEventListener, ImageMathScriptExecutor {

    private static final ReentrantLock ellipseFittingLock = new ReentrantLock();
    private static final Condition ellipseFittingCondition = ellipseFittingLock.newCondition();
    private static volatile boolean ellipseFittingInProgress = false;

    private final JSolExInterface owner;
    private final SingleModeProcessingEventListener delegate;
    private final ProcessParams processParams;
    private final BatchItem item;
    private final Set<Integer> completed;
    private final AtomicBoolean batchFinished;
    private final Set<Integer> errors;
    private final double totalItems;
    private final File outputDirectory;
    private final LocalDateTime processingDate;
    private final AtomicBoolean hasCustomImages = new AtomicBoolean();
    private final ProgressOperation rootOperation;

    private final Header referenceHeader;

    private final int sequenceNumber;
    private DefaultImageScriptExecutor batchScriptExecutor;

    private Header header;
    private final List<BatchItem> allItems;
    private final Map<String, List<ImageWrapper>> imagesByLabel;
    private final Map<Integer, List<ImageWrapper>> imageWrappersByIndex;
    private final Map<Integer, List<CandidateImageDescriptor>> imagesByIndex;
    private final Map<Integer, List<File>> filesByIndex;
    private final Map<Integer, File> serFilesByIndex;
    private final Map<Integer, SolarParameters> solarParametersByIndex;
    private final Map<Integer, ProcessParams> processParamsByIndex;
    private final long sd = System.nanoTime();
    private ProcessParams adjustedParams;
    private final java.util.concurrent.locks.ReentrantReadWriteLock dataLock;

    public BatchModeEventListener(JSolExInterface owner,
                                  ProgressOperation rootOperation,
                                  SingleModeProcessingEventListener delegate,
                                  int sequenceNumber,
                                  BatchProcessingContext context,
                                  ProcessParams processParams) {
        this.owner = owner;
        this.rootOperation = rootOperation;
        this.delegate = delegate;
        this.processParams = processParams;
        this.completed = context.progress();
        this.errors = context.errors();
        this.batchFinished = context.batchFinished();
        this.outputDirectory = context.outputDirectory();
        this.processingDate = context.processingDate();
        this.imagesByLabel = context.imagesByLabel();
        this.imageWrappersByIndex = context.imageWrappersByIndex();
        this.imagesByIndex = context.imagesByIndex();
        this.filesByIndex = context.filesByIndex();
        this.serFilesByIndex = context.serFilesByIndex();
        this.solarParametersByIndex = context.solarParametersByIndex();
        this.processParamsByIndex = context.processParamsByIndex();
        this.referenceHeader = context.referenceHeader();
        this.item = context.items().stream().filter(batchItem -> batchItem.id() == sequenceNumber).findFirst().get();
        this.totalItems = context.items().size();
        this.allItems = context.items();
        this.sequenceNumber = sequenceNumber;
        this.dataLock = context.dataLock();
    }
    
    private SolarParameters computeAverageSolarParameters() {
        if (solarParametersByIndex.isEmpty()) {
            return null;
        }
        
        double sumB0 = 0.0;
        double sumL0 = 0.0;
        double sumP = 0.0;
        double sumApparentSize = 0.0;
        int sumCarringtonRotation = 0;
        int count = 0;
        
        for (SolarParameters params : solarParametersByIndex.values()) {
            if (params != null) {
                sumB0 += params.b0();
                sumL0 += params.l0();
                sumP += params.p();
                sumApparentSize += params.apparentSize();
                sumCarringtonRotation += params.carringtonRotation();
                count++;
            }
        }
        
        if (count == 0) {
            return null;
        }
        
        return new SolarParameters(
            Math.round((float) sumCarringtonRotation / count),
            sumB0 / count,
            sumL0 / count,
            sumP / count,
            sumApparentSize / count
        );
    }
    
    private ProcessParams getFirstProcessParams() {
        if (processParamsByIndex.isEmpty()) {
            return null;
        }
        
        // Find the first file's ProcessParams (lowest index)
        return processParamsByIndex.entrySet().stream()
            .min(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    @Override
    public void onAverageImageComputed(AverageImageComputedEvent e) {
        this.adjustedParams = e.getPayload().adjustedParams();
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var image = payload.image();
        var kind = payload.kind();
        var target = payload.path().toFile();
        var params = adjustedParams != null ? adjustedParams : processParams;
        var img = image;
        double correction = 0;
        if (!kind.cannotPerformManualRotation()) {
            if (params.geometryParams().isAutocorrectAngleP()) {
                correction += SolarParametersUtils.computeSolarParams(params.observationDetails().date().toLocalDateTime()).p();
            }
        }
        if (correction != 0) {
            img = Corrector.rotate(img, correction, params.geometryParams().autocropMode() == AutocropMode.OFF);
        }
        var strategy = kind == GeneratedImageKind.IMAGE_MATH ? CutoffStretchingStrategy.DEFAULT : RangeExpansionStrategy.DEFAULT;
        var saved = new ImageSaver(strategy, params).save(img, target);
        for (var file : saved) {
            item.generatedFiles().add(file);
            dataLock.writeLock().lock();
            try {
                filesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(file);
            } finally {
                dataLock.writeLock().unlock();
            }
        }
        dataLock.writeLock().lock();
        try {
            imagesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(new CandidateImageDescriptor(
                payload.kind(),
                payload.title(),
                payload.path(),
                payload.image().findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0d)
            ));
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public void onFileGenerated(FileGeneratedEvent event) {
        item.generatedFiles().add(event.getPayload().path().toFile());
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        item.reconstructionProgress().setValue(payload.line() / (double) payload.totalLines());
    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        owner.setTrimmingParameters(e.getPayload());
    }

    @Override
    public void onEllipseFittingRequest(EllipseFittingRequestEvent e) {
        ellipseFittingLock.lock();
        try {
            // Wait if another ellipse fitting is in progress
            while (ellipseFittingInProgress) {
                try {
                    ellipseFittingCondition.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    e.resultFuture().completeExceptionally(ie);
                    return;
                }
            }
            
            // Mark ellipse fitting as in progress
            ellipseFittingInProgress = true;
            
            // Show dialog on FX thread
            Platform.runLater(() -> {
                owner.showEllipseFittingDialog(
                    e.image(), 
                    e.initialEllipse(),
                    item.file().getName(),
                    sequenceNumber + 1, // Display as 1-based
                    (int) totalItems
                ).thenAccept(result -> {
                    // Complete the future with result
                    e.resultFuture().complete(result);
                    
                    // Signal that ellipse fitting is done
                    ellipseFittingLock.lock();
                    try {
                        ellipseFittingInProgress = false;
                        ellipseFittingCondition.signalAll();
                    } finally {
                        ellipseFittingLock.unlock();
                    }
                }).exceptionally(throwable -> {
                    // Handle error case
                    e.resultFuture().completeExceptionally(throwable);
                    
                    // Signal that ellipse fitting is done
                    ellipseFittingLock.lock();
                    try {
                        ellipseFittingInProgress = false;
                        ellipseFittingCondition.signalAll();
                    } finally {
                        ellipseFittingLock.unlock();
                    }
                    return null;
                });
            });
            
        } finally {
            ellipseFittingLock.unlock();
        }
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.info(message("dimensions.determined"), event.getLabel(), event.getWidth(), event.getHeight());
        item.reconstructionProgress().setValue(1.0);
    }

    @Override
    public void onVideoMetadataAvailable(VideoMetadataEvent event) {
        var payload = event.getPayload();
        if (payload != null) {
            this.header = payload;
        }
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        item.status().set(message("batch.started"));
        updateProgressStatus(false);
        dataLock.writeLock().lock();
        try {
            serFilesByIndex.put(sequenceNumber, item.file());
            var params = adjustedParams != null ? adjustedParams : processParams;
            var solarParams = SolarParametersUtils.computeSolarParams(params.observationDetails().date().toLocalDateTime());
            solarParametersByIndex.put(sequenceNumber, solarParams);
            // Store ProcessParams for this file
            processParamsByIndex.put(sequenceNumber, params);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        updateProgressStatus(true);
        maybeWriteLogs();
        item.detectedActiveRegions().set(e.getPayload().detectedActiveRegions());
        item.maxRedshiftKmPerSec().set(e.getPayload().redshifts().stream().map(RedshiftArea::kmPerSec).max(Double::compareTo).orElse(0.0));
        item.ellermanBombs().set(e.getPayload().ellermanBombs());
        item.flares().set(e.getPayload().flares());
        if (item.status().get().equals(message("batch.error"))) {
            return;
        }
        item.status().set(message("batch.ok"));
        maybeExecuteEndOfBatch();
    }

    private void maybeFilterImages(Consumer<? super FilteringResult> onClose) {
        if (processParams.extraParams().reviewImagesAfterBatch()) {
            // Create defensive copies of shared data under lock protection
            Map<Integer, List<CandidateImageDescriptor>> imagesByIndexCopy;
            Map<Integer, List<File>> filesByIndexCopy;
            Map<Integer, File> serFilesByIndexCopy;
            dataLock.readLock().lock();
            try {
                imagesByIndexCopy = new HashMap<>();
                for (var entry : imagesByIndex.entrySet()) {
                    imagesByIndexCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                filesByIndexCopy = new HashMap<>();
                for (var entry : filesByIndex.entrySet()) {
                    filesByIndexCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                serFilesByIndexCopy = new HashMap<>(serFilesByIndex);
            } finally {
                dataLock.readLock().unlock();
            }
            Platform.runLater(() -> ImageInspectorController.create(processParams, imagesByIndexCopy, filesByIndexCopy, serFilesByIndexCopy, outputDirectory, controller -> {
                var deletedFiles = controller.getDeletedFiles();
                var movedFiles = controller.getMovedFiles();
                adjustDeletedAndMovedFilesList(deletedFiles, movedFiles);
                Thread.startVirtualThread(() -> onClose.accept(new FilteringResult(controller.getDiscardedImages(), controller.getBestImage().orElse(null))));
            }));
        } else {
            Thread.startVirtualThread(() -> onClose.accept(new FilteringResult(List.of(), null)));
        }
    }

    /**
     * After the user has filtered images, we need to adjust the list of generated files because the links
     * in the UI are based on the original list of files.
     *
     * @param deletedFiles the list of deleted files
     * @param movedFiles the list of moved files
     */
    private void adjustDeletedAndMovedFilesList(Set<File> deletedFiles, Map<File, File> movedFiles) {
        for (var curItem : allItems) {
            curItem.generatedFiles().removeIf(deletedFiles::contains);
        }
        var newFileList = new ArrayList<File>();
        for (var curItem : allItems) {
            for (var file : curItem.generatedFiles()) {
                newFileList.add(movedFiles.getOrDefault(file, file));
            }
        }
        item.generatedFiles().setAll(newFileList);
    }

    private void maybeExecuteEndOfBatch() {
        boolean shouldExecute;
        int success;
        dataLock.readLock().lock();
        try {
            shouldExecute = completed.size() == totalItems;
            success = completed.size() - errors.size();
        } finally {
            dataLock.readLock().unlock();
        }
        if (shouldExecute && batchFinished.compareAndSet(false, true)) {
            boolean hasErrors;
            dataLock.readLock().lock();
            try {
                hasErrors = !errors.isEmpty();
            } finally {
                dataLock.readLock().unlock();
            }
            if (success > 0 && hasErrors && hasBatchScriptExpressions()) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.warning(message("incomplete.batch.message"));
                    alert.setTitle(message("incomplete.batch"));
                    alert.getButtonTypes().clear();
                    alert.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.YES);
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            maybeFilterImages(r -> executeBatchScriptExpressions(r, rootOperation));
                        } else {
                            batchFinished();
                        }
                    });
                });
            } else if (hasErrors) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.warning(message("incomplete.batch.error"));
                    alert.setTitle(message("incomplete.batch"));
                    alert.showAndWait();
                    batchFinished();
                });
            } else {
                maybeFilterImages(r -> executeBatchScriptExpressions(r, rootOperation));
            }
        }
    }

    private void batchFinished() {
        var ed = System.nanoTime();
        var duration = Duration.ofNanos(ed - sd);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        var millis = duration.minusHours(hours).minusMinutes(minutes).minusSeconds(seconds).toMillis();
        var sb = new StringBuffer();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m ");
        sb.append(seconds).append("s ");
        sb.append(millis).append("ms");
        Platform.runLater(() -> owner.updateProgress(1, String.format(message("batch.finished"), sb.toString())));
    }

    private boolean hasBatchScriptExpressions() {
        var scriptFiles = processParams.combinedImageMathParams().scriptFiles();
        if (scriptFiles.isEmpty()) {
            return false;
        }
        return scriptFiles.stream().anyMatch(file -> {
            try {
                return Files.readString(file.toPath()).contains("[[batch]]");
            } catch (IOException e) {
                return false;
            }
        });
    }

    private void executeBatchScriptExpressions(FilteringResult result, ProgressOperation rootOperation) {
        try {
            var scriptFiles = processParams.combinedImageMathParams().scriptFiles();
            if (scriptFiles.isEmpty() || result.discarded().size() == totalItems) {
                return;
            }
            var imageEmitter = new NamingStrategyAwareImageEmitter(new RenamingImageEmitter(new DefaultImageEmitter(delegate, rootOperation, outputDirectory), name -> name, name -> name), createNamingStrategy(), sequenceNumber, computeSerFileBasename(item.file()));
            var ctx = new HashMap<Class, Object>();
            ctx.put(ImageEmitter.class, imageEmitter);

            dataLock.readLock().lock();
            try {
                // Add averaged solar parameters to batch script context.
                // This is not accurate, but a convenience for example when processing
                // a series of SER files which are "close" in time
                var avgSolarParams = computeAverageSolarParameters();
                if (avgSolarParams != null) {
                    ctx.put(SolarParameters.class, avgSolarParams);
                }
                
                // Add first file's ProcessParams to batch script context
                var firstProcessParams = getFirstProcessParams();
                if (firstProcessParams != null) {
                    ctx.put(ProcessParams.class, firstProcessParams);
                }
            } finally {
                dataLock.readLock().unlock();
            }
            batchScriptExecutor = new JSolExScriptExecutor(
                idx -> {
                    throw new IllegalStateException("Cannot call img() in batch outputs. Use variables to store images instead");
                },
                ctx,
                delegate,
                null
            );
            var discarded = new HashSet<ImageWrapper>();
            dataLock.readLock().lock();
            try {
                for (var index : result.discarded()) {
                    var images = imageWrappersByIndex.get(index);
                    if (images != null) {
                        discarded.addAll(images);
                    }
                }
            } finally {
                dataLock.readLock().unlock();
            }
            dataLock.readLock().lock();
            try {
                for (Map.Entry<String, List<ImageWrapper>> entry : imagesByLabel.entrySet()) {
                    var images = entry.getValue().stream().filter(img -> !discarded.contains(img)).toList();
                    batchScriptExecutor.putVariable(entry.getKey(), images);
                }
            } finally {
                dataLock.readLock().unlock();
            }
            if (result.best != null) {
                SourceInfo bestSource = null;
                dataLock.readLock().lock();
                try {
                    var bestImages = imageWrappersByIndex.get(result.best);
                    if (bestImages != null) {
                        bestSource = bestImages.stream()
                            .findFirst()
                            .flatMap(i -> i.findMetadata(SourceInfo.class))
                            .orElse(null);
                    }
                } finally {
                    dataLock.readLock().unlock();
                }
                batchScriptExecutor.putInContext(BestImages.class, new BestImages(bestSource));
            }
            var namingStrategy = createNamingStrategy();
            boolean initial = true;
            for (File scriptFile : scriptFiles) {
                if (initial) {
                    owner.prepareForScriptExecution(this, processParams, rootOperation, ImageMathScriptExecutor.SectionKind.BATCH);
                    initial = false;
                }
                executeBatchScript(namingStrategy, scriptFile);
            }
        } finally {
            batchFinished();
        }
    }


    private void executeBatchScript(FileNamingStrategy namingStrategy, File scriptFile) {
        Platform.runLater(() -> owner.updateProgress(0, String.format(message("executing.script"), scriptFile)));
        ImageMathScriptResult result;
        try {
            result = batchScriptExecutor.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.BATCH);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        try {
            processScriptErrors(result);
            renderBatchOutputs(namingStrategy, result);
        } finally {
            Platform.runLater(() -> owner.updateProgress(1, String.format(message("executing.script"), scriptFile)));
        }
    }

    private void renderBatchOutputs(FileNamingStrategy namingStrategy, ImageMathScriptResult result) {
        if (result.imagesByLabel().isEmpty() && result.filesByLabel().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
                var tabPane = owner.getTabs();
                var imagesViewerTab = owner.getImagesViewerTab();
                tabPane.getTabs().add(imagesViewerTab);
                tabPane.getSelectionModel().select(imagesViewerTab);
        });
        result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch", entry.getValue());
            var outputFile = new File(outputDirectory, name);
            delegate.onImageGenerated(new ImageGeneratedEvent(
                new GeneratedImage(GeneratedImageKind.IMAGE_MATH, entry.getKey(), outputFile.toPath(), entry.getValue(), null)
            ));
            hasCustomImages.set(true);
        });
        result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch", null);
            try {
                var fileName = entry.getValue().toFile().getName();
                var ext = fileName.substring(fileName.lastIndexOf("."));
                var targetPath = new File(outputDirectory, name + ext).toPath();
                createDirectoriesIfNeeded(targetPath.getParent());
                Files.move(entry.getValue(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                delegate.onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, entry.getKey(), targetPath));
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        });
    }

    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        dataLock.writeLock().lock();
        try {
            var images = e.getPayload().imagesByLabel();
            for (Map.Entry<String, ImageWrapper> entry : images.entrySet()) {
                imagesByLabel.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
                    .add(entry.getValue());
                imageWrappersByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>())
                    .add(entry.getValue());
            }
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    private void maybeWriteLogs() {
        if (item.log().length() > 0 && header != null) {
            var namingStrategy = createNamingStrategy();
            var fileName = item.file().getName();
            var logFileName = namingStrategy.render(sequenceNumber, null, "log", "notifications", fileName.substring(0, fileName.lastIndexOf(".")), null) + ".txt";
            try {
                var logFilePath = outputDirectory.toPath().resolve(logFileName);
                Files.writeString(logFilePath, item.log().toString(), Charset.defaultCharset());
                item.generatedFiles().add(logFilePath.toFile());
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private FileNamingStrategy createNamingStrategy() {
        return new FileNamingStrategy(
            processParams.extraParams().fileNamePattern(),
            processParams.extraParams().datetimeFormat(),
            processParams.extraParams().dateFormat(),
            processingDate,
            header != null ? header : referenceHeader
        );
    }

    private void updateProgressStatus(boolean increment) {
        int done;
        dataLock.writeLock().lock();
        try {
            if (increment) {
                completed.add(sequenceNumber);
            }
            done = completed.size();
        } finally {
            dataLock.writeLock().unlock();
        }
        BatchOperations.submitOneOfAKind("progress", () -> {
            var prog = done / totalItems;
            if (done == (int) totalItems) {
                owner.showProgress();
            } else {
                owner.showProgress();
                owner.updateProgress(prog, String.format(message("batch.progress"), done, (int) totalItems));
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
            item.status().set(message("batch.error"));
            dataLock.writeLock().lock();
            try {
                errors.add(sequenceNumber);
            } finally {
                dataLock.writeLock().unlock();
            }
            updateProgressStatus(true);
            maybeExecuteEndOfBatch();
        }
    }

    @Override
    public void setIncludesDir(Path includesDir) {
        batchScriptExecutor.setIncludesDir(includesDir);
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        var result = batchScriptExecutor.execute(script, SectionKind.BATCH);
        processScriptErrors(result);
        renderBatchOutputs(createNamingStrategy(), result);
        return result;
    }

    @Override
    public <T> Optional<T> getVariable(String name) {
        return batchScriptExecutor.getVariable(name);
    }

    private void processScriptErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
            String details = invalidExpressions.stream()
                .map(invalidExpression -> {
                    var sb = new StringWriter();
                    invalidExpression.error().printStackTrace(new java.io.PrintWriter(sb));
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
            delegate.onNotification(new NotificationEvent(new Notification(
                Notification.AlertType.ERROR,
                message("error.processing.script"),
                message("script.errors." + (errorCount == 1 ? "single" : "many")),
                message
            )));
        }
    }

    public void onProcessingFailed() {
        item.status().set(message("batch.error"));
        dataLock.writeLock().lock();
        try {
            errors.add(sequenceNumber);
        } finally {
            dataLock.writeLock().unlock();
        }
        updateProgressStatus(true);
        maybeExecuteEndOfBatch();
    }

    private record FilteringResult(
        List<Integer> discarded,
        Integer best
    ) {

    }
}
