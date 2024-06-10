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
package me.champeau.a4j.jsolex.app.jfx.stacking;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;

import java.io.File;
import java.util.List;
import java.util.Locale;

class PanelCard extends Card {
    private final ListView<File> listView = new ListView<>();

    public PanelCard(StackingAndMosaicController stackingAndMosaicController) {
        super(stackingAndMosaicController);
        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(listView);
        listView.setStyle("-fx-background-color: transparent");
        listView.setEditable(false);
        listView.setDisable(true);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        setCenter(stackPane);
        setOnDragDropped(this::handleDragDropped);
        setOnDragOver(evt -> {
            var hasFiles = evt.getDragboard().hasFiles();
            if (hasFiles) {
                evt.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                setStyle(
                    "-fx-border-insets:6; -fx-background-insets:6; -fx-border-radius: 12;  -fx-background-radius: 12; -fx-border-color: grey; -fx-border-width: 2; -fx-min-width: 200; -fx-min-height: 200; -fx-max-width:200; -fx-max-height:200; -fx-background-color: #99ffcc;");
            }
        });
        setOnDragExited(evt -> setDefaultStyle());
        var addLabel = new Label("➕");
        addLabel.setStyle("-fx-font-size: 24; -fx-text-fill: green;");
        var deleteLabel = new Label("❌");
        deleteLabel.setStyle("-fx-font-size: 24; -fx-text-fill: red;");
        stackPane.getChildren().addAll(addLabel, deleteLabel);
        StackPane.setMargin(addLabel, new Insets(0, 8, 0, 0));
        StackPane.setMargin(deleteLabel, new Insets(24, 8, 0, 0));
        stackPane.setAlignment(Pos.TOP_RIGHT);
        deleteLabel.setOnMouseClicked(event -> {
            var alert = AlertFactory.confirmation(I18N.string(JSolEx.class, "mosaic-params", "delete.panel.header"));
            alert.setTitle(I18N.string(JSolEx.class, "mosaic-params", "delete.panel.title"));
            alert.showAndWait().ifPresent(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    stackingAndMosaicController.getCardsPane().getChildren().remove(this);
                }
            });
        });
        addLabel.setOnMouseClicked(event -> {
            var files = selectFilesUsingFileChooser();
            if (!files.isEmpty()) {
                addFilesToPanel(files);
            }
        });
    }

    void handleDragDropped(DragEvent event) {
        var db = event.getDragboard();
        var files = db.getFiles();
        addFilesToPanel(files);
        event.setDropCompleted(true);
        event.consume();
    }

    private void addFilesToPanel(List<File> files) {
        listView.getItems().addAll(
            files.stream()
                .filter(File::isFile)
                .filter(f -> {
                    var name = f.getName().toLowerCase(Locale.US);
                    return JSolEx.IMAGE_FILE_EXTENSIONS.stream().anyMatch(ext -> name.endsWith("." + ext));
                })
                .filter(f -> !listView.getItems().contains(f))
                .toList()
        );
    }

    public ListView<File> getListView() {
        return listView;
    }
}
