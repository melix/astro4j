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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.EllipseFit;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.EllipseFittingMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Handles loading of image files (PNG, JPG, TIF, FITS) and performs
 * ellipse fitting on images that lack ellipse metadata.
 */
public class StandaloneImagesLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneImagesLoader.class);

    private final Stage stage;
    private final Configuration config;
    private final MultipleImagesViewer multipleImagesViewer;
    private final FileChooser.ExtensionFilter imageFilesFilter;
    private final BiConsumer<Double, String> progressConsumer;
    private final ProcessParams processParams;

    public StandaloneImagesLoader(Stage stage,
                                  Configuration config,
                                  MultipleImagesViewer multipleImagesViewer,
                                  FileChooser.ExtensionFilter imageFilesFilter,
                                  BiConsumer<Double, String> progressConsumer,
                                  ProcessParams processParams) {
        this.stage = stage;
        this.config = config;
        this.multipleImagesViewer = multipleImagesViewer;
        this.imageFilesFilter = imageFilesFilter;
        this.progressConsumer = progressConsumer;
        this.processParams = processParams;
    }

    /**
     * Opens a file chooser to select image files, loads them, and displays them
     * in the viewer. Performs ellipse fitting on images without ellipse metadata.
     */
    public void loadImages() {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(imageFilesFilter);
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        LOGGER.info(message("selected.files"), selectedFiles);
        config.loadedSerFile(selectedFiles.getFirst().toPath());

        var listener = new ProcessingEventListener() {
            @Override
            public void onProgress(ProgressEvent e) {
                var operation = e.getPayload();
                progressConsumer.accept(operation.progress(), operation.taskPath());
            }
        };
        Broadcaster broadcaster = event -> {
            if (event instanceof ProgressEvent progressEvent) {
                listener.onProgress(progressEvent);
            }
        };
        var rootOperation = ProgressOperation.root(message("load.images"), _ -> {});
        int totalFiles = selectedFiles.size();
        var isManualMode = processParams.geometryParams().ellipseFittingMode() == EllipseFittingMode.MANUAL;

        BackgroundOperations.async(() -> {
            for (int i = 0; i < totalFiles; i++) {
                var file = selectedFiles.get(i);
                double progress = (double) i / totalFiles;
                var fileOperation = rootOperation.createChild(file.getName());
                broadcaster.broadcast(rootOperation.update(progress, message("loading.image") + " " + file.getName()));
                loadAndDisplayImage(file, listener, fileOperation, broadcaster, isManualMode, i + 1, totalFiles);
                broadcaster.broadcast(fileOperation.complete());
            }
            broadcaster.broadcast(rootOperation.complete(message("loading.complete")));
        });
    }

    private void loadAndDisplayImage(File file,
                                     ProcessingEventListener listener,
                                     ProgressOperation parentOperation,
                                     Broadcaster broadcaster,
                                     boolean isManualMode,
                                     int currentFileIndex,
                                     int totalFiles) {
        try {
            var loadOperation = parentOperation.createChild(message("loading.image"));
            broadcaster.broadcast(loadOperation.update(0.0));
            var imageWrapper = Loader.loadImage(file);
            broadcaster.broadcast(loadOperation.complete());

            var existingEllipse = imageWrapper.findMetadata(Ellipse.class);

            if (existingEllipse.isEmpty()) {
                var ellipseFitMessage = message("performing.ellipse.fit").replace("{}", file.getName());
                var ellipseOperation = parentOperation.createChild(ellipseFitMessage);
                broadcaster.broadcast(ellipseOperation.update(0.0));
                LOGGER.info(ellipseFitMessage);
                imageWrapper = performEllipseFitting(imageWrapper, broadcaster, isManualMode, file.getName(), currentFileIndex, totalFiles);
                broadcaster.broadcast(ellipseOperation.complete());
            }

            var fileName = file.getName();
            var baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            var fileWithBaseName = new File(file.getParentFile(), baseName);

            ImageWrapper finalImage = imageWrapper;
            Platform.runLater(() -> multipleImagesViewer.addImage(
                listener,
                parentOperation,
                baseName,
                baseName,
                GeneratedImageKind.IMAGE_MATH,
                null,
                finalImage,
                fileWithBaseName,
                processParams,
                Map.of(),
                new PixelShift(0),
                viewer -> viewer,
                _ -> {}
            ));
        } catch (Exception e) {
            LOGGER.error("Error loading image: {}", file.getName(), e);
        }
    }

    private ImageWrapper performEllipseFitting(ImageWrapper imageWrapper,
                                               Broadcaster broadcaster,
                                               boolean isManualMode,
                                               String fileName,
                                               int currentFileIndex,
                                               int totalFiles) {
        var context = MutableMap.<Class<?>, Object>of();
        var ellipseFit = new EllipseFit(context, broadcaster);

        if (imageWrapper instanceof FileBackedImage fbi) {
            imageWrapper = fbi.unwrapToMemory();
        }

        ImageWrapper32 monoImage;
        if (imageWrapper instanceof ImageWrapper32 mono) {
            monoImage = mono;
        } else if (imageWrapper instanceof RGBImage rgb) {
            monoImage = rgb.toMono();
        } else {
            return imageWrapper;
        }

        var fittedMono = ellipseFit.performEllipseFitting(monoImage);
        var initialEllipse = fittedMono.findMetadata(Ellipse.class).orElse(null);

        Ellipse finalEllipse = initialEllipse;
        if (isManualMode && initialEllipse != null) {
            finalEllipse = showManualEllipseFittingDialog(monoImage, initialEllipse, fileName, currentFileIndex, totalFiles);
        }

        if (imageWrapper instanceof ImageWrapper32) {
            if (finalEllipse != null && finalEllipse != initialEllipse) {
                monoImage.metadata().put(Ellipse.class, finalEllipse);
                return monoImage;
            }
            return fittedMono;
        } else if (imageWrapper instanceof RGBImage rgb) {
            var rgbCopy = rgb.copy();
            if (finalEllipse != null) {
                rgbCopy.metadata().put(Ellipse.class, finalEllipse);
            }
            return rgbCopy;
        }

        return imageWrapper;
    }

    private Ellipse showManualEllipseFittingDialog(ImageWrapper32 image,
                                                   Ellipse initialEllipse,
                                                   String fileName,
                                                   int currentFileIndex,
                                                   int totalFiles) {
        var resultRef = new AtomicReference<>(initialEllipse);
        var latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            var future = AssistedEllipseFittingController.showDialog(
                stage, image, initialEllipse, fileName, currentFileIndex, totalFiles);
            future.whenComplete((ellipse, throwable) -> {
                if (ellipse != null) {
                    resultRef.set(ellipse);
                }
                latch.countDown();
            });
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for ellipse fitting dialog", e);
        }

        return resultRef.get();
    }

    private static String message(String key) {
        return I18N.string(JSolEx.class, "messages", key);
    }
}
