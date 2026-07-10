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
import javafx.collections.ObservableList;
import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Factory for creating and configuring the batch processing table.
 */
public final class BatchTableFactory {

    private BatchTableFactory() {
    }

    /**
     * Creates and configures a TableView for batch processing.
     *
     * @param batchItems the list of batch items to display
     * @param params the processing parameters
     * @return the configured TableView
     */
    public static TableView<BatchItem> createBatchTable(ObservableList<BatchItem> batchItems, ProcessParams params) {
        var table = new TableView<BatchItem>();
        table.setItems(batchItems);

        var idColumn = createIdColumn();
        var stateIconColumn = createStateIconColumn();
        var fnColumn = createFilenameColumn();
        var progressColumn = createProgressColumn();
        var images = createImagesColumn();
        var statusColumn = createStatusColumn();
        var detectedActiveRegions = createDetectedActiveRegionsColumn();
        var maxRedshiftKmPerSec = createMaxRedshiftColumn();
        var ellermanBombs = createEllermanBombsColumn();
        var flares = createFlaresColumn();

        images.setPrefWidth(80);
        images.setMinWidth(70);
        images.setMaxWidth(120);

        NumberBinding fixedColumnsWidth = idColumn.widthProperty()
                .add(stateIconColumn.widthProperty())
                .add(progressColumn.widthProperty())
                .add(images.widthProperty())
                .add(statusColumn.widthProperty())
                .add(20);

        if (params.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS)) {
            fixedColumnsWidth = fixedColumnsWidth.add(detectedActiveRegions.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)) {
            fixedColumnsWidth = fixedColumnsWidth.add(maxRedshiftKmPerSec.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.ELLERMAN_BOMBS)) {
            fixedColumnsWidth = fixedColumnsWidth.add(ellermanBombs.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.FLARES)) {
            fixedColumnsWidth = fixedColumnsWidth.add(flares.widthProperty());
        }
        fnColumn.prefWidthProperty().bind(table.widthProperty().subtract(fixedColumnsWidth));

        table.setRowFactory(new TintedRowFactory());
        table.setFixedCellSize(28);
        table.setStyle("-fx-selection-bar: #cfe0f3; -fx-selection-bar-text: #1a1a1a; -fx-selection-bar-non-focused: #e0e8f0;");

        // Add base columns
        var columns = table.getColumns();
        columns.setAll(idColumn, stateIconColumn, fnColumn, progressColumn, images);

        // Add optional columns based on enabled features
        if (params.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS)) {
            columns.add(detectedActiveRegions);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)) {
            columns.add(maxRedshiftKmPerSec);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.ELLERMAN_BOMBS)) {
            columns.add(ellermanBombs);
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.FLARES)) {
            columns.add(flares);
        }
        columns.add(statusColumn);

        return table;
    }

    private static TableColumn<BatchItem, String> createIdColumn() {
        var column = new TableColumn<BatchItem, String>();
        column.setText("#");
        column.setCellValueFactory(param -> new SimpleStringProperty(String.format("%04d", param.getValue().id())));
        return column;
    }

    private static TableColumn<BatchItem, String> createStateIconColumn() {
        var column = new TableColumn<BatchItem, String>();
        column.setText("");
        column.setMinWidth(32);
        column.setPrefWidth(32);
        column.setMaxWidth(32);
        column.setResizable(false);
        column.setSortable(false);
        column.setCellValueFactory(param -> param.getValue().status());
        column.setCellFactory(new StateIconCellFactory());
        return column;
    }

    private static TableColumn<BatchItem, String> createFilenameColumn() {
        var column = new TableColumn<BatchItem, String>();
        column.setText(message("filename"));
        column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().file().getName()));
        return column;
    }

    private static TableColumn<BatchItem, Number> createProgressColumn() {
        var column = new TableColumn<BatchItem, Number>();
        column.setText(message("reconstruction"));
        column.setCellValueFactory(param -> param.getValue().reconstructionProgress());
        column.setCellFactory(new ProgressCellFactory());
        return column;
    }

    private static TableColumn<BatchItem, List<File>> createImagesColumn() {
        var column = new TableColumn<BatchItem, List<File>>();
        column.setText(message("images"));
        column.setCellValueFactory(param -> Bindings.createObjectBinding(
                () -> new ArrayList<>(param.getValue().generatedFiles()),
                param.getValue().generatedFiles()
        ));
        column.setCellFactory(new ImageLinksFactory());
        return column;
    }

    private static TableColumn<BatchItem, String> createStatusColumn() {
        var column = new TableColumn<BatchItem, String>();
        column.setText(message("status"));
        column.setCellValueFactory(param -> param.getValue().status());
        return column;
    }

    private static TableColumn<BatchItem, Integer> createDetectedActiveRegionsColumn() {
        var column = new TableColumn<BatchItem, Integer>();
        column.setText(message("detected.active.regions"));
        column.setCellValueFactory(param -> param.getValue().detectedActiveRegions().asObject());
        return column;
    }

    private static TableColumn<BatchItem, Double> createMaxRedshiftColumn() {
        var column = new TableColumn<BatchItem, Double>();
        column.setText(message("max.redshift.km.per.sec"));
        column.setCellValueFactory(param -> param.getValue().maxRedshiftKmPerSec().asObject());
        column.setCellFactory(new RedshiftCellFactory());
        return column;
    }

    private static TableColumn<BatchItem, Integer> createEllermanBombsColumn() {
        var column = new TableColumn<BatchItem, Integer>();
        column.setText(message("ellerman.bombs"));
        column.setCellValueFactory(param -> param.getValue().ellermanBombs().asObject());
        return column;
    }

    private static TableColumn<BatchItem, Integer> createFlaresColumn() {
        var column = new TableColumn<BatchItem, Integer>();
        column.setText(message("flares"));
        column.setCellValueFactory(param -> param.getValue().flares().asObject());
        return column;
    }

    private static class ProgressCellFactory implements Callback<TableColumn<BatchItem, Number>, TableCell<BatchItem, Number>> {
        @Override
        public TableCell<BatchItem, Number> call(TableColumn<BatchItem, Number> param) {
            return new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else if (getGraphic() instanceof ProgressBar progress) {
                        progress.setProgress(item.doubleValue());
                    } else {
                        var progress = new ProgressBar(item.doubleValue());
                        progress.setStyle("-fx-accent: #2c5aa0;");
                        setGraphic(progress);
                    }
                }
            };
        }
    }

    private static class RedshiftCellFactory implements Callback<TableColumn<BatchItem, Double>, TableCell<BatchItem, Double>> {
        @Override
        public TableCell<BatchItem, Double> call(TableColumn<BatchItem, Double> column) {
            return new TableCell<>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else if (getGraphic() instanceof Label label) {
                        label.setText(String.format("%.2f km/s", item));
                    } else {
                        setGraphic(new Label(String.format("%.2f km/s", item)));
                    }
                }
            };
        }
    }

    static File pickRevealTarget(List<File> files) {
        File firstImage = null;
        File firstNonLog = null;
        for (var file : files) {
            var name = file.getName().toLowerCase();
            if (firstImage == null && isImageName(name)) {
                firstImage = file;
            }
            if (firstNonLog == null && !name.endsWith(".log") && !name.endsWith(".txt")) {
                firstNonLog = file;
            }
        }
        if (firstImage != null) {
            return firstImage;
        }
        if (firstNonLog != null) {
            return firstNonLog;
        }
        return files.isEmpty() ? null : files.getFirst();
    }

    private static boolean isImageName(String lowerName) {
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".fits") || lowerName.endsWith(".fit")
                || lowerName.endsWith(".tif") || lowerName.endsWith(".tiff")
                || lowerName.endsWith(".gif");
    }

    static String iconFor(BatchItemState state) {
        return switch (state) {
            case DONE -> "✓";
            case ERROR -> "✗";
            case RUNNING -> "▶";
            case QUEUED -> "⏳";
        };
    }

    static String iconStyleFor(BatchItemState state) {
        return switch (state) {
            case DONE -> "-fx-text-fill: #2c662c; -fx-font-weight: bold;";
            case ERROR -> "-fx-text-fill: #802c2c; -fx-font-weight: bold;";
            case RUNNING -> "-fx-text-fill: #2c4d80; -fx-font-weight: bold;";
            case QUEUED -> "-fx-text-fill: #888;";
        };
    }

    private static String rowTintFor(BatchItemState state) {
        return switch (state) {
            case DONE -> "-fx-background-color: #eef9ee;";
            case ERROR -> "-fx-background-color: #fbecec;";
            case RUNNING -> "-fx-background-color: #eef3fb;";
            case QUEUED -> "";
        };
    }

    private static class StateIconCellFactory implements Callback<TableColumn<BatchItem, String>, TableCell<BatchItem, String>> {
        @Override
        public TableCell<BatchItem, String> call(TableColumn<BatchItem, String> param) {
            return new TableCell<>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    var state = BatchItemState.of(status);
                    var label = getGraphic() instanceof Label existing ? existing : new Label();
                    label.setText(iconFor(state));
                    label.setStyle(iconStyleFor(state));
                    setGraphic(label);
                    setText(null);
                }
            };
        }
    }

    private static class TintedRowFactory implements Callback<TableView<BatchItem>, TableRow<BatchItem>> {
        @Override
        public TableRow<BatchItem> call(TableView<BatchItem> tableView) {
            var row = new TableRow<BatchItem>() {
                private final ChangeListener<String> statusListener = (obs, oldV, newV) -> {
                    if (Platform.isFxApplicationThread()) {
                        updateRowStyle();
                    } else {
                        FxUtils.runLater(this::updateRowStyle);
                    }
                };
                private SimpleStringProperty boundStatus;

                @Override
                protected void updateItem(BatchItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (boundStatus != null) {
                        boundStatus.removeListener(statusListener);
                        boundStatus = null;
                    }
                    if (empty || item == null) {
                        setStyle("");
                        return;
                    }
                    boundStatus = item.status();
                    boundStatus.addListener(statusListener);
                    updateRowStyle();
                }

                void updateRowStyle() {
                    if (boundStatus == null || isSelected()) {
                        setStyle("");
                        return;
                    }
                    setStyle(rowTintFor(BatchItemState.of(boundStatus.get())));
                }
            };
            row.selectedProperty().addListener((obs, was, is) -> row.updateRowStyle());
            return row;
        }
    }

    private static class ImageLinksFactory implements Callback<TableColumn<BatchItem, List<File>>, TableCell<BatchItem, List<File>>> {
        @Override
        public TableCell<BatchItem, List<File>> call(TableColumn<BatchItem, List<File>> param) {
            return new TableCell<>() {
                @Override
                protected void updateItem(List<File> item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    var link = getGraphic() instanceof Hyperlink h ? h : new Hyperlink();
                    link.setText("📁 " + item.size());
                    link.setStyle("-fx-text-fill: #2c5aa0;");
                    link.setOnAction(e -> {
                        var target = pickRevealTarget(item);
                        if (target != null) {
                            ExplorerSupport.openInExplorer(target.toPath());
                        }
                    });
                    link.setDisable(item.isEmpty());
                    setGraphic(link);
                    setText(null);
                }
            };
        }
    }
}
