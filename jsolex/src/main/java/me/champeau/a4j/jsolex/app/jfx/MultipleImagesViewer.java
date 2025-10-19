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
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.DisplayCategory;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class MultipleImagesViewer extends Pane {
    // Ordered list to determine the default image to show
    private static final List<GeneratedImageKind> DEFAULT_IMAGE_TO_SHOW = List.of(
            GeneratedImageKind.IMAGE_MATH,
            GeneratedImageKind.COLLAGE,
            GeneratedImageKind.COMPOSITION,
            GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
            GeneratedImageKind.GEOMETRY_CORRECTED,
            GeneratedImageKind.RAW,
            GeneratedImageKind.MIXED,
            GeneratedImageKind.COLORIZED,
            GeneratedImageKind.CONTINUUM,
            GeneratedImageKind.TECHNICAL_CARD
    );

    private final Set<ImageViewer> imageViews = new HashSet<>();
    private final List<CategoryPane> safeCategories = new ArrayList<>();
    private final ObservableList<Node> categories;
    private final BorderPane borderPane;
    private final ReentrantLock lock = new ReentrantLock();
    private Map<Object, Runnable> onShowHooks = new HashMap<>();
    private Map<ImageViewer, String> viewerTitles = new HashMap<>();
    private ObservableMap<ImageViewer, GeneratedImageKind> viewerKinds = FXCollections.observableHashMap();
    private Map<Hyperlink, ImageViewer> linkToViewer = new HashMap<>();
    private Hyperlink selected = null;
    private GeneratedImageKind selectedKind = null;
    private Object selectedView;

    private JSolExInterface owner;
    private ProcessParams processParams;
    private Path outputDirectory;

    public MultipleImagesViewer() {
        getStyleClass().add("multiple-images-viewer");
        borderPane = new BorderPane();
        borderPane.prefWidthProperty().bind(widthProperty());
        borderPane.prefHeightProperty().bind(heightProperty());

        var sideBar = new VBox();
        var categoriesContainer = new VBox();
        categories = categoriesContainer.getChildren();

        var actionsSection = createActionsSection();

        sideBar.getChildren().addAll(categoriesContainer, actionsSection);

        var scrollPane = new ScrollPane(sideBar);
        scrollPane.setFitToWidth(true);
        scrollPane.visibleProperty().bind(Bindings.size(categories).greaterThan(0));
        borderPane.setLeft(scrollPane);
        getChildren().add(borderPane);
    }

    private VBox createActionsSection() {
        var actionsSection = new VBox();
        actionsSection.getStyleClass().add("category-pane");

        var nonReconstructionImageCount = Bindings.createIntegerBinding(() -> (int) viewerKinds.values().stream()
                .filter(kind -> kind != GeneratedImageKind.RECONSTRUCTION)
                .count(), viewerKinds);

        actionsSection.visibleProperty().bind(nonReconstructionImageCount.greaterThanOrEqualTo(2));
        actionsSection.managedProperty().bind(actionsSection.visibleProperty());

        var titleLabel = new Label(message("actions"));
        titleLabel.getStyleClass().add("category-title");
        actionsSection.getChildren().add(titleLabel);

        var collageBox = new HBox();
        collageBox.setAlignment(Pos.CENTER_LEFT);

        var collageLink = new Hyperlink(message("create.collage"));
        collageLink.getStyleClass().add("category-link");
        collageLink.setOnAction(e -> createCollage());

        collageBox.getChildren().add(collageLink);
        actionsSection.getChildren().add(collageBox);

        return actionsSection;
    }


    public void clear() {
        try {
            lock.lock();
            Platform.runLater(() -> {
                safeCategories.clear();
                categories.clear();
                borderPane.setCenter(null);
                imageViews.clear();
                viewerTitles.clear();
                viewerKinds.clear();
                onShowHooks.clear();
                linkToViewer.clear();
            });
            selected = null;
            selectedKind = null;
            selectedView = null;
        } finally {
            lock.unlock();
        }
    }

    private ImageViewer newImageViewer() {
        try {
            lock.lock();

            var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "imageview");
            try {
                var node = (Node) fxmlLoader.load();
                var controller = (ImageViewer) fxmlLoader.getController();
                controller.init(node);
                imageViews.add(controller);
                return controller;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            lock.unlock();
        }
    }

    public void registerOnShowHook(Object view, Runnable action) {
        try {
            lock.lock();
            onShowHooks.put(view, action);
        } finally {
            lock.unlock();
        }
    }

    public <T extends WithRootNode> T addImage(ProcessingEventListener listener,
                                               ProgressOperation operation,
                                               String title,
                                               String baseName,
                                               GeneratedImageKind kind,
                                               String description,
                                               ImageWrapper imageWrapper,
                                               File file,
                                               ProcessParams params,
                                               Map<String, ImageViewer> popupViews,
                                               PixelShift pixelShift,
                                               Function<? super ImageViewer, T> transformer,
                                               Consumer<? super ImageViewer> onShow) {
        try {
            lock.lock();

            var category = getOrCreateCategory(kind);
            var viewer = newImageViewer();
            var transformed = transformer.apply(viewer);
            viewerTitles.put(viewer, title);
            viewerKinds.put(viewer, kind);
            viewer.setup(
                    listener,
                    operation,
                    title,
                    baseName,
                    kind,
                    description,
                    imageWrapper,
                    file,
                    params,
                    popupViews,
                    imageViews
            );
            var hyperlink = category.addImage(title, pixelShift, link -> {
                categories().forEach(CategoryPane::clearSelection);
                Platform.runLater(() -> {
                    borderPane.setCenter(transformed.getRoot());
                    var hook = onShowHooks.get(transformed);
                    if (hook != null) {
                        hook.run();
                    }
                });
                selected = link;
                selectedKind = kind;
                selectedView = viewer;
                onShow.accept(viewer);
                viewer.display();
            }, this::onClose);

            linkToViewer.put(hyperlink, viewer);
            if (selected == null) {
                category.selectFirst();
                hyperlink.fire();
            } else if (shouldSelectAutomatically(params, kind, pixelShift)) {
                hyperlink.fire();
            }
            return transformed;
        } finally {
            lock.unlock();
        }
    }

    private void onClose(Hyperlink link) {
        if (selected == link) {
            borderPane.setCenter(null);
            selected = null;
        }

        var viewer = linkToViewer.remove(link);
        if (viewer != null) {
            viewerTitles.remove(viewer);
            viewerKinds.remove(viewer);
        }
    }

    public MediaPlayer addVideo(GeneratedImageKind kind,
                                String title,
                                Path filePath) {
        try {
            lock.lock();

            var category = getOrCreateCategory(kind);
            var media = createMedia(filePath);
            var mediaPlayer = new MediaPlayer(media);
            var viewer = new MediaView(mediaPlayer);
            // Create the buttons
            var rewindButton = new Button("<<");
            var playButton = new Button("Play");
            var stopButton = new Button("Stop");
            var openButton = new Button(message("open.in.files"));
            openButton.setOnAction(e -> ExplorerSupport.openInExplorer(filePath));
            mediaPlayer.setOnEndOfMedia(() -> mediaPlayer.seek(javafx.util.Duration.ZERO));
            playButton.setOnAction(e -> mediaPlayer.play());
            stopButton.setOnAction(e -> mediaPlayer.stop());
            rewindButton.setOnAction(e -> {
                mediaPlayer.stop();
                mediaPlayer.seek(javafx.util.Duration.ZERO);
            });
            var buttonBox = new HBox(playButton, stopButton, rewindButton, openButton);
            buttonBox.setSpacing(10);
            var contentBox = new VBox(new ScrollPane(viewer), buttonBox);
            contentBox.setAlignment(Pos.CENTER);
            viewer.fitWidthProperty().bind(widthProperty());
            viewer.fitHeightProperty().bind(heightProperty().subtract(buttonBox.heightProperty()));
            var hyperlink = category.addVideo(title, link -> {
                categories().forEach(CategoryPane::clearSelection);
                borderPane.setCenter(contentBox);
                selected = link;
                selectedView = contentBox;
            }, this::onClose);
            hyperlink.fire();
            return mediaPlayer;
        } finally {
            lock.unlock();
        }
    }

    private static Media createMedia(Path filePath) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Under Windows we'll have to copy the file, otherwise if the user tries to overwrite it
            // for example because of re-running a script which writes the file in the same location,
            // file writing will fail because the media player has locked the file
            try {
                var tempFile = TemporaryFolder.newTempFile("jsolex", ".mp4");
                Files.copy(filePath, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return new Media(tempFile.toUri().toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new Media(filePath.toUri().toString());
    }

    private boolean shouldSelectAutomatically(ProcessParams params, GeneratedImageKind kind, PixelShift pixelShift) {
        var idx = DEFAULT_IMAGE_TO_SHOW.indexOf(kind);
        if (idx == -1) {
            return false;
        }
        if (selectedKind == null) {
            return true;
        }
        var selectedIdx = DEFAULT_IMAGE_TO_SHOW.indexOf(selectedKind);
        if (selectedIdx >= 0 && idx > selectedIdx) {
            return false;
        }
        if (pixelShift == null || pixelShift.pixelShift() == params.spectrumParams().pixelShift()) {
            return true;
        }
        return false;
    }

    public ImageViewer getSelectedViewer() {
        if (selectedView instanceof ImageViewer viewer) {
            return viewer;
        }
        return null;
    }

    private CategoryPane getOrCreateCategory(GeneratedImageKind kind) {
        var category = kind.displayCategory();
        return categories()
                .filter(t -> t.getProperties().get(DisplayCategory.class) == category)
                .findFirst()
                .orElseGet(() -> addCategory(category));
    }

    private Stream<CategoryPane> categories() {
        return safeCategories.stream();
    }

    private CategoryPane addCategory(DisplayCategory category) {
        var categoryPane = new CategoryPane(message("displayCategory." + category.name()), e -> {
            categories.remove(e);
            safeCategories.remove(e);
        });
        categoryPane.setMinWidth(190);
        categoryPane.getProperties().put(DisplayCategory.class, category);
        safeCategories.add(categoryPane);
        safeCategories.sort(Comparator.comparingInt(t -> categoryOf(t).ordinal()));
        Platform.runLater(() -> categories.setAll(safeCategories));
        return categoryPane;
    }

    private static DisplayCategory categoryOf(CategoryPane pane) {
        return (DisplayCategory) pane.getProperties().get(DisplayCategory.class);
    }

    public record ImageInfo(ImageWrapper image, String title, GeneratedImageKind kind) {
    }

    public List<ImageInfo> getAllAvailableImagesWithInfo() {
        lock.lock();
        try {
            return imageViews.stream()
                    .filter(viewer -> viewer.getStretchedImage() != null)
                    .map(viewer -> new ImageInfo(
                            viewer.getStretchedImage().wrap(),
                            getImageTitle(viewer),
                            getImageKind(viewer)
                    ))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    private String getImageTitle(ImageViewer viewer) {
        return viewerTitles.getOrDefault(viewer, "Image");
    }

    private GeneratedImageKind getImageKind(ImageViewer viewer) {
        return viewerKinds.get(viewer);
    }

    public void setCollageContext(JSolExInterface owner, ProcessParams processParams, Path outputDirectory) {
        this.owner = owner;
        this.processParams = processParams;
        this.outputDirectory = outputDirectory;
    }

    private void createCollage() {
        if (owner == null) {
            return;
        }

        var availableImagesWithInfo = getAllAvailableImagesWithInfo();
        if (availableImagesWithInfo.isEmpty()) {
            return;
        }

        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "collage");
        try {
            var stage = FXUtils.newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (CollageController) fxmlLoader.getController();
            var params = processParams != null ? processParams : ProcessParams.loadDefaults();
            var outputDir = outputDirectory != null ? outputDirectory : Path.of(".");
            controller.setup(stage, owner, availableImagesWithInfo, outputDir, params);
            Scene scene = JSolEx.newScene(node);
            stage.setTitle(I18N.string(JSolEx.class, "collage", "frame.title"));
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
