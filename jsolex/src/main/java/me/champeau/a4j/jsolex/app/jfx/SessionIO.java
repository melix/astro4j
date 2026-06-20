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

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.RectangleSelectionListener.ActionKind;
import me.champeau.a4j.jsolex.app.util.FxUtils;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.session.Session;
import me.champeau.a4j.jsolex.processing.session.SessionData;
import me.champeau.a4j.jsolex.processing.session.SessionImage;
import me.champeau.a4j.jsolex.processing.session.SessionMedia;
import me.champeau.a4j.jsolex.processing.session.SessionReRunData;
import me.champeau.a4j.jsolex.processing.session.SessionReader;
import me.champeau.a4j.jsolex.processing.session.SessionWriter;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles export and import of the current viewer state (generated images and
 * media) to and from a compressed session file.
 */
public class SessionIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionIO.class);

    private final Stage stage;
    private final Configuration config;
    private final MultipleImagesViewer multipleImagesViewer;
    private final ProcessParams defaultParams;
    private final Map<String, ImageViewer> popupViewers;
    private final File baseDirectory;
    private final Runnable ensureViewerVisible;
    private final BooleanSupplier hasExistingContent;
    private final Supplier<SessionReRunData> reRunSupplier;
    private final Consumer<SessionReRunData> reRunRestorer;
    private String importGroupId;
    private String importGroupTitle;
    private final AtomicInteger cropCount = new AtomicInteger();

    public SessionIO(Stage stage,
                     Configuration config,
                     MultipleImagesViewer multipleImagesViewer,
                     ProcessParams defaultParams,
                     Map<String, ImageViewer> popupViewers,
                     File baseDirectory,
                     Runnable ensureViewerVisible,
                     BooleanSupplier hasExistingContent,
                     Supplier<SessionReRunData> reRunSupplier,
                     Consumer<SessionReRunData> reRunRestorer) {
        this.stage = stage;
        this.config = config;
        this.multipleImagesViewer = multipleImagesViewer;
        this.defaultParams = defaultParams;
        this.popupViewers = popupViewers;
        this.baseDirectory = baseDirectory;
        this.ensureViewerVisible = ensureViewerVisible;
        this.hasExistingContent = hasExistingContent;
        this.reRunSupplier = reRunSupplier;
        this.reRunRestorer = reRunRestorer;
    }

    /**
     * Prompts for a destination file and exports the current viewer content.
     */
    public void exportSession() {
        if (!multipleImagesViewer.hasContent()) {
            AlertFactory.info(message("session.nothing.to.export")).showAndWait();
            return;
        }
        var includeReRun = askExportOptions();
        if (includeReRun == null) {
            return;
        }
        var fileChooser = new FileChooser();
        fileChooser.setTitle(message("export.session"));
        fileChooser.getExtensionFilters().add(sessionFilter());
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selected = fileChooser.showSaveDialog(stage);
        if (selected == null) {
            return;
        }
        var target = selected.getName().endsWith(Session.FILE_EXTENSION)
                ? selected
                : new File(selected.getParentFile(), selected.getName() + Session.FILE_EXTENSION);
        config.updateLastOpenDirectory(target.toPath().getParent());
        var viewerData = multipleImagesViewer.collectSessionData();
        SessionReRunData reRun = null;
        if (includeReRun && reRunSupplier != null) {
            try {
                reRun = reRunSupplier.get();
            } catch (RuntimeException e) {
                LOGGER.error("Could not gather re-run data", e);
            }
        }
        var data = new SessionData(viewerData.images(), viewerData.media(), reRun);
        var dialog = new SessionProgressDialog(stage, message("exporting.session"));
        dialog.show();
        BackgroundOperations.async(() -> {
            try {
                SessionWriter.write(data, target.toPath(),
                        (fraction, item) -> dialog.update(fraction, progressMessage("exporting.session", item)));
                dialog.close();
                FxUtils.runLater(() -> AlertFactory.info(message("session.export.complete")).showAndWait());
            } catch (Exception e) {
                LOGGER.error("Could not export session", e);
                dialog.close();
                FxUtils.runLater(() -> AlertFactory.error(message("session.export.error") + "\n" + e.getMessage()).showAndWait());
            }
        });
    }

    /**
     * Prompts for a session file and imports its content into the viewer.
     */
    public void importSession() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(message("import.session"));
        fileChooser.getExtensionFilters().add(sessionFilter());
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selected = fileChooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        config.updateLastOpenDirectory(selected.toPath().getParent());
        if (hasExistingContent.getAsBoolean()) {
            var replace = askReplaceOrMerge();
            if (replace == null) {
                return;
            }
            if (replace) {
                multipleImagesViewer.clear();
            } else {
                importGroupId = selected.getAbsolutePath() + "#" + System.nanoTime();
                importGroupTitle = baseName(selected);
            }
        }
        ensureViewerVisible.run();
        var listener = new ProcessingEventListener() {
        };
        var rootOperation = ProgressOperation.root(message("importing.session"), _ -> {
        });
        var dialog = new SessionProgressDialog(stage, message("importing.session"));
        dialog.show();
        BackgroundOperations.async(() -> {
            try {
                var data = SessionReader.read(selected.toPath(),
                        (fraction, item) -> dialog.update(fraction, progressMessage("importing.session", item)));
                for (var image : data.images()) {
                    addImage(listener, rootOperation, image);
                }
                for (var media : data.media()) {
                    addMedia(media);
                }
                if (!data.images().isEmpty() || !data.media().isEmpty()) {
                    FxUtils.runLater(() -> multipleImagesViewer.setCloseAllEnabled(true));
                }
                if (data.reRun() != null && reRunRestorer != null) {
                    var reRun = data.reRun();
                    FxUtils.runLater(() -> reRunRestorer.accept(reRun));
                }
                dialog.close();
            } catch (Exception e) {
                LOGGER.error("Could not import session", e);
                dialog.close();
                FxUtils.runLater(() -> AlertFactory.error(message("session.import.error") + "\n" + e.getMessage()).showAndWait());
            }
        });
    }

    private static String progressMessage(String key, String item) {
        var base = message(key);
        return item == null || item.isBlank() ? base : base + "\n" + item;
    }

    private void addImage(ProcessingEventListener listener, ProgressOperation operation, SessionImage image) {
        addImage(listener, operation, image.title(), image.baseName(), image.kind(),
                image.description(), image.image(), importGroupId, importGroupTitle);
    }

    private void addImage(ProcessingEventListener listener,
                          ProgressOperation operation,
                          String title,
                          String baseName,
                          GeneratedImageKind kind,
                          String description,
                          ImageWrapper imageWrapper,
                          String groupId,
                          String groupTitle) {
        var params = imageWrapper.findMetadata(ProcessParams.class).orElse(defaultParams);
        params = params.withExtraParams(params.extraParams().withAutosave(false));
        var pixelShift = imageWrapper.findMetadata(PixelShift.class).orElse(new PixelShift(0));
        var imageName = new File(baseDirectory, baseName);
        var finalParams = params;
        FxUtils.runLater(() -> {
            Consumer<ImageViewer> installCrop = viewer ->
                    viewer.getImageView().setRectangleSelectionListener(new RectangleSelectionListener() {
                        @Override
                        public boolean supports(ActionKind actionKind) {
                            return actionKind == ActionKind.CROP;
                        }

                        @Override
                        public void onSelectRegion(ActionKind actionKind, int x, int y, int width, int height) {
                            if (actionKind == ActionKind.CROP) {
                                performCrop(listener, operation, viewer, groupId, groupTitle, x, y, width, height);
                            }
                        }
                    });
            if (groupId != null) {
                multipleImagesViewer.addImageToGroup(groupId, groupTitle, listener, operation,
                        title, baseName, kind, description, imageWrapper, imageName,
                        finalParams, popupViewers, pixelShift, viewer -> viewer, installCrop);
            } else {
                multipleImagesViewer.addImage(listener, operation, title, baseName, kind,
                        description, imageWrapper, imageName, finalParams, popupViewers, pixelShift,
                        viewer -> viewer, installCrop);
            }
        });
    }

    private void performCrop(ProcessingEventListener listener,
                             ProgressOperation operation,
                             ImageViewer viewer,
                             String groupId,
                             String groupTitle,
                             int x, int y, int width, int height) {
        BackgroundOperations.async(() -> {
            var source = viewer.getStretchedImageWithOverlays();
            if (source == null) {
                return;
            }
            var crop = new Crop(Map.of(), Broadcaster.NO_OP);
            var cropped = crop.crop(Map.of("img", source, "left", x, "top", y, "width", width, "height", height));
            if (cropped instanceof ImageWrapper croppedImage) {
                var id = cropCount.getAndIncrement();
                var baseName = "cropped-" + id;
                var title = String.format(message("cropped.image"), id);
                addImage(listener, operation, title, baseName, GeneratedImageKind.CROPPED, null,
                        croppedImage, groupId, groupTitle);
            }
        });
    }

    private static String baseName(File file) {
        var name = file.getName();
        var dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void addMedia(SessionMedia media) {
        FxUtils.runLater(() -> {
            if (media.type() == SessionMedia.Type.GIF) {
                multipleImagesViewer.addAnimatedGif(media.kind(), media.title(), media.file(), media.description());
            } else {
                multipleImagesViewer.addVideo(media.kind(), media.title(), media.file(), media.description());
            }
        });
    }

    /**
     * Shows the export options dialog. Returns whether to include re-run data, or
     * {@code null} if the user cancelled.
     */
    private Boolean askExportOptions() {
        var checkbox = new CheckBox(message("session.export.include.rerun"));
        var note = new Label(message("session.export.include.rerun.note"));
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.getStyleClass().add("help-text");
        var content = new VBox(8, checkbox, note);
        content.setPadding(new Insets(10, 5, 5, 5));
        var alert = AlertFactory.confirmation(message("session.export.options.content"));
        alert.setHeaderText(message("session.export.options.header"));
        alert.setGraphic(null);
        var pane = alert.getDialogPane();
        pane.setContent(content);
        pane.setPrefWidth(480);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        var result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return null;
        }
        return checkbox.isSelected();
    }

    private Boolean askReplaceOrMerge() {
        var alert = AlertFactory.confirmation(message("session.import.replace.content"));
        alert.setHeaderText(message("session.import.replace.header"));
        var replaceButton = new ButtonType(message("session.import.replace"), ButtonBar.ButtonData.OK_DONE);
        var mergeButton = new ButtonType(message("session.import.merge"), ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(replaceButton, mergeButton, ButtonType.CANCEL);
        var result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return null;
        }
        return result.get() == replaceButton;
    }

    private static FileChooser.ExtensionFilter sessionFilter() {
        return new FileChooser.ExtensionFilter(message("session.files"), "*" + Session.FILE_EXTENSION);
    }

    private static String message(String key) {
        return JSolEx.message(key);
    }
}
