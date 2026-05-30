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
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.util.FxUtils;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Side drawer displaying details for the selected batch item. Bindings to the
 * item's observable properties are re-attached every time the selected item
 * changes via {@link #setItem(BatchItem)}.
 */
public final class BatchDetailDrawer {

    private final VBox root;
    private final Label placeholderLabel;
    private final VBox detailsBox;

    private final Label stateIcon;
    private final Label statusLabel;
    private final Label filenameLabel;
    private final Label folderLabel;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Label activeRegionsLabel;
    private final Label redshiftLabel;
    private final Label ellermanBombsLabel;
    private final Label flaresLabel;
    private final Label outputsHeader;
    private final FlowPane outputsList;
    private final Button openFolderButton;

    private BatchItem boundItem;
    private int thumbnailCount;
    private final ListChangeListener<File> outputsListener;
    private final ChangeListener<Object> propertyListener;

    public BatchDetailDrawer(ProcessParams params) {
        var header = new Label(message("batch.drawer.title"));
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        stateIcon = new Label();
        stateIcon.setMinWidth(20);
        statusLabel = new Label();
        var statusRow = new HBox(8, stateIcon, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        filenameLabel = new Label();
        filenameLabel.setStyle("-fx-font-weight: bold;");
        filenameLabel.setWrapText(true);
        folderLabel = new Label();
        folderLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        folderLabel.setWrapText(true);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressLabel = new Label();
        progressLabel.setStyle("-fx-text-fill: #666;");
        var progressRow = new HBox(8, progressBar, progressLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        activeRegionsLabel = new Label("—");
        redshiftLabel = new Label("—");
        ellermanBombsLabel = new Label("—");
        flaresLabel = new Label("—");

        var activeRegionsRow = metricRow(message("detected.active.regions"), activeRegionsLabel);
        var redshiftRow = metricRow(message("max.redshift.km.per.sec"), redshiftLabel);
        var ellermanBombsRow = metricRow(message("ellerman.bombs"), ellermanBombsLabel);
        var flaresRow = metricRow(message("flares"), flaresLabel);

        var metricsBox = new VBox(4);
        if (params.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS)) {
            metricsBox.getChildren().add(activeRegionsRow);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)) {
            metricsBox.getChildren().add(redshiftRow);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.ELLERMAN_BOMBS)) {
            metricsBox.getChildren().add(ellermanBombsRow);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.FLARES)) {
            metricsBox.getChildren().add(flaresRow);
        }

        outputsHeader = new Label();
        outputsHeader.setStyle("-fx-font-weight: bold;");
        openFolderButton = new Button(message("batch.drawer.open.folder"));
        openFolderButton.getStyleClass().add("default-button");
        openFolderButton.setOnAction(e -> {
            if (boundItem == null) {
                return;
            }
            var files = boundItem.generatedFiles();
            List<File> snapshot;
            synchronized (files) {
                snapshot = new ArrayList<>(files);
            }
            var target = BatchTableFactory.pickRevealTarget(snapshot);
            if (target != null) {
                ExplorerSupport.openInExplorer(target.toPath());
            }
        });
        var outputsHeaderRow = new HBox(8, outputsHeader);
        outputsHeaderRow.setAlignment(Pos.CENTER_LEFT);
        var headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        outputsHeaderRow.getChildren().addAll(headerSpacer, openFolderButton);

        outputsList = new FlowPane(8, 8);
        outputsList.setPadding(new Insets(2));
        var outputsScroll = new ScrollPane(outputsList);
        outputsScroll.setFitToWidth(true);
        outputsScroll.setPrefViewportHeight(260);
        outputsScroll.setStyle("-fx-background-color: transparent;");

        detailsBox = new VBox(10,
                filenameLabel,
                folderLabel,
                new Separator(),
                statusRow,
                progressRow,
                new Separator(),
                metricsBox,
                new Separator(),
                outputsHeaderRow,
                outputsScroll);
        VBox.setVgrow(outputsScroll, Priority.ALWAYS);

        placeholderLabel = new Label(message("batch.drawer.placeholder"));
        placeholderLabel.setStyle("-fx-text-fill: #888;");

        root = new VBox(10, header, new Separator(), placeholderLabel, detailsBox);
        root.setPadding(new Insets(12, 14, 12, 14));
        root.setMinWidth(280);
        root.setPrefWidth(360);
        root.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border; -fx-border-width: 0 0 0 1;");

        detailsBox.setVisible(false);
        detailsBox.setManaged(false);
        VBox.setVgrow(detailsBox, Priority.ALWAYS);

        outputsListener = change -> {
            var added = new ArrayList<File>();
            var hasRemoval = false;
            while (change.next()) {
                if (change.wasAdded()) {
                    added.addAll(change.getAddedSubList());
                }
                if (change.wasRemoved()) {
                    hasRemoval = true;
                }
            }
            if (hasRemoval) {
                runOnFx(this::refreshOutputs);
                return;
            }
            if (!added.isEmpty()) {
                runOnFx(() -> appendTiles(added));
            }
        };
        propertyListener = (obs, oldV, newV) -> runOnFx(this::applyProperties);
    }

    private static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            FxUtils.runLater(r);
        }
    }

    public Node getNode() {
        return root;
    }

    public void setItem(BatchItem item) {
        unbindCurrent();
        boundItem = item;
        if (item == null) {
            placeholderLabel.setVisible(true);
            placeholderLabel.setManaged(true);
            detailsBox.setVisible(false);
            detailsBox.setManaged(false);
            return;
        }
        placeholderLabel.setVisible(false);
        placeholderLabel.setManaged(false);
        detailsBox.setVisible(true);
        detailsBox.setManaged(true);
        filenameLabel.setText(item.file().getName());
        var parent = item.file().getParentFile();
        folderLabel.setText(parent != null ? parent.getAbsolutePath() : "");

        item.status().addListener(propertyListener);
        item.reconstructionProgress().addListener(propertyListener);
        item.detectedActiveRegions().addListener(propertyListener);
        item.maxRedshiftKmPerSec().addListener(propertyListener);
        item.ellermanBombs().addListener(propertyListener);
        item.flares().addListener(propertyListener);
        item.generatedFiles().addListener(outputsListener);

        applyProperties();
        refreshOutputs();
    }

    private void unbindCurrent() {
        if (boundItem == null) {
            return;
        }
        boundItem.status().removeListener(propertyListener);
        boundItem.reconstructionProgress().removeListener(propertyListener);
        boundItem.detectedActiveRegions().removeListener(propertyListener);
        boundItem.maxRedshiftKmPerSec().removeListener(propertyListener);
        boundItem.ellermanBombs().removeListener(propertyListener);
        boundItem.flares().removeListener(propertyListener);
        boundItem.generatedFiles().removeListener(outputsListener);
    }

    private void applyProperties() {
        if (boundItem == null) {
            return;
        }
        var status = boundItem.status().get();
        var state = BatchItemState.of(status);
        statusLabel.setText(status);
        stateIcon.setText(BatchTableFactory.iconFor(state));
        stateIcon.setStyle(BatchTableFactory.iconStyleFor(state));
        var progress = boundItem.reconstructionProgress().get();
        progressBar.setProgress(progress);
        progressLabel.setText(Math.round(progress * 100) + "%");
        activeRegionsLabel.setText(Integer.toString(boundItem.detectedActiveRegions().get()));
        redshiftLabel.setText(String.format("%.2f km/s", boundItem.maxRedshiftKmPerSec().get()));
        ellermanBombsLabel.setText(Integer.toString(boundItem.ellermanBombs().get()));
        flaresLabel.setText(Integer.toString(boundItem.flares().get()));
    }

    private void refreshOutputs() {
        outputsList.getChildren().clear();
        thumbnailCount = 0;
        if (boundItem == null) {
            outputsHeader.setText(message("batch.drawer.outputs") + " (0)");
            openFolderButton.setDisable(true);
            return;
        }
        var files = boundItem.generatedFiles();
        List<File> snapshot;
        synchronized (files) {
            snapshot = new ArrayList<>(files);
        }
        for (var file : snapshot) {
            outputsList.getChildren().add(buildTile(file));
        }
        updateOutputsCount(snapshot.size());
    }

    private void appendTiles(List<File> newFiles) {
        if (boundItem == null) {
            return;
        }
        for (var file : newFiles) {
            outputsList.getChildren().add(buildTile(file));
        }
        updateOutputsCount(outputsList.getChildren().size());
    }

    private void updateOutputsCount(int count) {
        outputsHeader.setText(message("batch.drawer.outputs") + " (" + count + ")");
        openFolderButton.setDisable(count == 0);
    }

    private static final double TILE_SIZE = 96;
    private static final int MAX_THUMBNAILS = 60;

    private Node buildTile(File file) {
        var name = file.getName();
        Node graphic;
        if (isLoadableImage(name) && thumbnailCount < MAX_THUMBNAILS) {
            graphic = buildThumbnail(file);
            thumbnailCount++;
        } else {
            graphic = buildGenericIcon(name);
        }

        var label = new Label(name);
        label.setMaxWidth(TILE_SIZE + 16);
        label.setStyle("-fx-font-size: 10px;");
        label.setWrapText(false);

        var tile = new VBox(4, graphic, label);
        tile.setAlignment(Pos.TOP_CENTER);
        tile.setPadding(new Insets(4));
        tile.setStyle("-fx-cursor: hand;");
        tile.setOnMouseClicked(e -> ExplorerSupport.openInExplorer(file.toPath()));
        Tooltip.install(tile, new Tooltip(name));
        return tile;
    }

    private static Node buildThumbnail(File file) {
        var imageView = new ImageView();
        imageView.setFitWidth(TILE_SIZE);
        imageView.setFitHeight(TILE_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        var image = new Image(file.toURI().toString(), TILE_SIZE, TILE_SIZE, true, true, true);
        imageView.setImage(image);

        var container = new StackPane(imageView);
        container.setMinSize(TILE_SIZE, TILE_SIZE);
        container.setPrefSize(TILE_SIZE, TILE_SIZE);
        container.setMaxSize(TILE_SIZE, TILE_SIZE);
        container.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #d0d0d0; -fx-border-width: 1;");
        return container;
    }

    private static Node buildGenericIcon(String name) {
        var extension = extensionOf(name);
        var label = new Label(extension.isEmpty() ? "FILE" : extension.toUpperCase());
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #555;");
        var pane = new StackPane(label);
        pane.setMinSize(TILE_SIZE, TILE_SIZE);
        pane.setPrefSize(TILE_SIZE, TILE_SIZE);
        pane.setMaxSize(TILE_SIZE, TILE_SIZE);
        pane.setStyle(extensionStyleFor(extension));
        return pane;
    }

    private static boolean isLoadableImage(String name) {
        var lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private static String extensionOf(String name) {
        var dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String extensionStyleFor(String extension) {
        var base = "-fx-border-color: #c0c0c0; -fx-border-width: 1;";
        var ext = extension.toLowerCase();
        if (ext.equals("fits") || ext.equals("fit")) {
            return base + " -fx-background-color: #e6efdb;";
        }
        if (ext.equals("tif") || ext.equals("tiff")) {
            return base + " -fx-background-color: #efe6db;";
        }
        if (ext.equals("mp4") || ext.equals("mov") || ext.equals("avi") || ext.equals("webm")) {
            return base + " -fx-background-color: #dbe1ef;";
        }
        if (ext.equals("log") || ext.equals("txt")) {
            return base + " -fx-background-color: #f0f0f0;";
        }
        return base + " -fx-background-color: #ececec;";
    }

    private static HBox metricRow(String labelText, Label valueLabel) {
        var name = new Label(labelText);
        name.setStyle("-fx-text-fill: #555;");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var row = new HBox(8, name, spacer, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
