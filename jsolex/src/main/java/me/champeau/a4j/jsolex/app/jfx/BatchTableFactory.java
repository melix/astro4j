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
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
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
    public static TableView<BatchItem> createBatchTable(List<BatchItem> batchItems, ProcessParams params) {
        var table = new TableView<BatchItem>();
        table.getItems().addAll(batchItems);

        var idColumn = createIdColumn();
        var fnColumn = createFilenameColumn();
        var progressColumn = createProgressColumn();
        var images = createImagesColumn();
        var statusColumn = createStatusColumn();
        var detectedActiveRegions = createDetectedActiveRegionsColumn();
        var maxRedshiftKmPerSec = createMaxRedshiftColumn();
        var ellermanBombs = createEllermanBombsColumn();
        var flares = createFlaresColumn();

        // Calculate width for images column
        NumberBinding firstColumnsWidth = idColumn.widthProperty()
                .add(fnColumn.widthProperty())
                .add(progressColumn.widthProperty())
                .add(statusColumn.widthProperty())
                .add(20);

        if (params.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS)) {
            firstColumnsWidth = firstColumnsWidth.add(detectedActiveRegions.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)) {
            firstColumnsWidth = firstColumnsWidth.add(maxRedshiftKmPerSec.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.ELLERMAN_BOMBS)) {
            firstColumnsWidth = firstColumnsWidth.add(ellermanBombs.widthProperty());
        }
        if (params.requestedImages().isEnabled(GeneratedImageKind.FLARES)) {
            firstColumnsWidth = firstColumnsWidth.add(flares.widthProperty());
        }
        images.prefWidthProperty().bind(table.widthProperty().subtract(firstColumnsWidth));

        // Add base columns
        var columns = table.getColumns();
        columns.setAll(idColumn, fnColumn, progressColumn, images);

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
                        setGraphic(new ProgressBar(item.doubleValue()));
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

    private static class ImageLinksFactory implements Callback<TableColumn<BatchItem, List<File>>, TableCell<BatchItem, List<File>>> {
        @Override
        public TableCell<BatchItem, List<File>> call(TableColumn<BatchItem, List<File>> param) {
            return new TableCell<>() {
                @Override
                protected void updateItem(List<File> item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isEmpty()) {
                        setGraphic(null);
                        return;
                    }
                    var vbox = getGraphic() instanceof VBox existing ? existing : new VBox();
                    vbox.getChildren().clear();
                    for (File file : item) {
                        var link = new Hyperlink(file.getName());
                        link.setOnAction(e -> ExplorerSupport.openInExplorer(file.toPath()));
                        vbox.getChildren().add(link);
                    }
                    setGraphic(vbox);
                }
            };
        }
    }
}
