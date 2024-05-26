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

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.DisplayCategory;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class MultipleImagesViewer extends Pane {
    // Ordered list to determine the default image to show
    private static final List<GeneratedImageKind> DEFAULT_IMAGE_TO_SHOW = List.of(
        GeneratedImageKind.IMAGE_MATH,
        GeneratedImageKind.COMPOSITION,
        GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
        GeneratedImageKind.GEOMETRY_CORRECTED,
        GeneratedImageKind.RAW,
        GeneratedImageKind.MIXED,
        GeneratedImageKind.COLORIZED,
        GeneratedImageKind.CONTINUUM,
        GeneratedImageKind.TECHNICAL_CARD
    );

    private final Set<ImageViewer> imageViews = Collections.synchronizedSet(new HashSet<>());
    private final List<CategoryPane> safeCategories = Collections.synchronizedList(new ArrayList<>());
    private final ObservableList<Node> categories;
    private final BorderPane borderPane;
    private Hyperlink selected = null;
    private GeneratedImageKind selectedKind = null;

    public MultipleImagesViewer() {
        getStyleClass().add("multiple-images-viewer");
        borderPane = new BorderPane();
        borderPane.prefWidthProperty().bind(widthProperty());
        borderPane.prefHeightProperty().bind(heightProperty());
        var sideBar = new VBox();
        categories = sideBar.getChildren();
        var scrollPane = new ScrollPane(sideBar);
        scrollPane.setFitToWidth(true);
        scrollPane.visibleProperty().bind(Bindings.size(categories).greaterThan(0));
        borderPane.setLeft(scrollPane);
        getChildren().add(borderPane);
    }

    public void clear() {
        BatchOperations.submit(() -> {
            safeCategories.clear();
            categories.clear();
            borderPane.setCenter(null);
            imageViews.clear();
        });
        selected = null;
        selectedKind = null;
    }

    private ImageViewer newImageViewer() {
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
    }

    public ImageViewer addImage(ProcessingEventListener listener,
                                String title,
                                String baseName,
                                GeneratedImageKind kind,
                                ImageWrapper imageWrapper,
                                File file,
                                ProcessParams params,
                                Map<String, ImageViewer> popupViews,
                                PixelShift pixelShift,
                                Consumer<? super ImageViewer> onShow) {
        var category = getOrCreateCategory(kind);
        var viewer = newImageViewer();
        viewer.setup(
            listener,
            title,
            baseName,
            kind,
            imageWrapper,
            file,
            params,
            popupViews,
            imageViews
        );
        var hyperlink = category.addImage(title, pixelShift, link -> {
            categories().forEach(CategoryPane::clearSelection);
            BatchOperations.submit(() -> borderPane.setCenter(viewer.getRoot()));
            selected = link;
            selectedKind = kind;
            onShow.accept(viewer);
            viewer.display();
        }, this::onClose);
        if (selected == null) {
            category.selectFirst();
            hyperlink.fire();
        } else if (shouldSelectAutomatically(params, kind, pixelShift)) {
            hyperlink.fire();
        }
        return viewer;
    }

    private void onClose(Hyperlink link) {
        if (selected == link) {
            borderPane.setCenter(null);
            selected = null;
        }
    }

    public MediaPlayer addVideo(GeneratedImageKind kind, String title,
                                Path filePath) {
        var category = getOrCreateCategory(kind);
        var media = new Media(filePath.toUri().toString());
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
        }, this::onClose);
        hyperlink.fire();
        return mediaPlayer;
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

    private CategoryPane getOrCreateCategory(GeneratedImageKind kind) {
        var category = kind.displayCategory();
        return categories()
            .filter(t -> t.getProperties().get(DisplayCategory.class) == category)
            .findFirst()
            .orElseGet(() -> addCategory(category));
    }

    private Stream<CategoryPane> categories() {
        return safeCategories.stream()
            .map(CategoryPane.class::cast);
    }

    private CategoryPane addCategory(DisplayCategory category) {
        var categoryPane = new CategoryPane(message("displayCategory." + category.name()), categories::remove);
        categoryPane.setMinWidth(190);
        categoryPane.getProperties().put(DisplayCategory.class, category);
        safeCategories.add(categoryPane);
        safeCategories.sort(Comparator.comparingInt(t -> categoryOf(t).ordinal()));
        BatchOperations.submit(() -> categories.setAll(safeCategories));
        return categoryPane;
    }

    private static DisplayCategory categoryOf(CategoryPane pane) {
        return (DisplayCategory) pane.getProperties().get(DisplayCategory.class);
    }

}
