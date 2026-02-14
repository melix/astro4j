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

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;

/**
 * Controller for displaying the advanced parameters dialog.
 */
public class AdvancedParamsController {
    private AdvancedParamsController() {
    }

    /**
     * Opens the advanced parameters dialog.
     * @param owner the owner window
     */
    public static void openDialog(Stage owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18N.string(JSolEx.class, "advanced-params", "frame.title"));

        var panel = new AdvancedParamsPanel();
        var scrollPane = new ScrollPane(panel);
        scrollPane.getStyleClass().add("content-scroll-pane");
        scrollPane.setFitToWidth(true);

        var okButton = new Button(I18N.string(JSolEx.class, "advanced-params", "ok"));
        okButton.getStyleClass().add("primary-button");
        okButton.setPrefWidth(80);
        okButton.setOnAction(e -> {
            panel.saveConfiguration();
            if (panel.requiresRestart()) {
                AlertFactory.info(I18N.string(JSolEx.class, "advanced-params", "must.restart"))
                        .showAndWait();
            }
            stage.close();
        });

        var cancelButton = new Button(I18N.string(JSolEx.class, "advanced-params", "cancel"));
        cancelButton.getStyleClass().add("default-button");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> stage.close());

        var buttonBar = new HBox(8);
        buttonBar.getStyleClass().add("editor-button-bar");
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getChildren().addAll(cancelButton, okButton);

        var root = new BorderPane();
        root.getStyleClass().add("params-dialog");
        root.setCenter(scrollPane);
        root.setBottom(buttonBar);

        var scene = new Scene(root, 700, 600);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
