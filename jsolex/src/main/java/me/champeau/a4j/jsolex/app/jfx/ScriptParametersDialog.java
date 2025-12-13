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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.processing.params.ScriptParameter;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A modal dialog for collecting script parameter values from the user.
 * The dialog presents a form with controls for each parameter type (choice, number, string)
 * and validates user input before allowing submission.
 */
public class ScriptParametersDialog {
    private final List<ScriptParameter> parameters;
    private final Map<String, Object> initialValues;
    private final Map<String, Object> resultValues;
    private final Map<String, Boolean> validationStates = new HashMap<>();
    private final CompletableFuture<Map<String, Object>> resultFuture = new CompletableFuture<>();
    private Button okButton;

    /**
     * Creates a new script parameters dialog.
     *
     * @param parameters the list of script parameters to collect values for
     * @param initialValues the initial values for the parameters, may be null
     */
    public ScriptParametersDialog(List<ScriptParameter> parameters, Map<String, Object> initialValues) {
        this.parameters = parameters;
        this.initialValues = initialValues != null ? initialValues : Map.of();
        this.resultValues = new HashMap<>(this.initialValues);
    }

    /**
     * Displays the dialog and waits for user input.
     *
     * @param owner the owner stage for this dialog
     * @return a map of parameter names to their values, or null if the dialog was cancelled
     */
    public Map<String, Object> showAndWait(Stage owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18N.string(ScriptParametersDialog.class, "script-parameters", "script.parameters.title"));

        var root = new BorderPane();
        root.setStyle("-fx-background-color: #f8f9fa;");

        var headerLabel = new Label(I18N.string(ScriptParametersDialog.class, "script-parameters", "script.parameters.header"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-background-color: white; -fx-text-fill: #212529;");
        headerLabel.setPadding(new Insets(15, 20, 15, 20));
        headerLabel.setMaxWidth(Double.MAX_VALUE);
        root.setTop(headerLabel);

        var grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.setStyle("-fx-background-color: white;");

        var currentLanguage = LocaleUtils.getConfiguredLocale().toString();

        ScriptParameterUIBuilder.buildParameterGrid(
            grid,
            parameters,
            currentLanguage,
            initialValues,
            (paramName, isValid) -> {
                validationStates.put(paramName, isValid);
                updateOkButtonState();
            },
            resultValues::put
        );

        var scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        BorderPane.setMargin(scrollPane, new Insets(10, 10, 10, 10));
        root.setCenter(scrollPane);

        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        var cancelButton = new Button(I18N.string(ScriptParametersDialog.class, "script-parameters", "cancel"));
        cancelButton.getStyleClass().add("default-button");
        cancelButton.setOnAction(e -> {
            resultFuture.complete(null);
            stage.close();
        });
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);

        okButton = new Button(I18N.string(ScriptParametersDialog.class, "script-parameters", "ok"));
        okButton.getStyleClass().add("primary-button");
        okButton.setOnAction(e -> {
            resultFuture.complete(resultValues);
            stage.close();
        });
        ButtonBar.setButtonData(okButton, ButtonBar.ButtonData.OK_DONE);
        okButton.setDefaultButton(true);

        updateOkButtonState();

        buttonBar.getButtons().addAll(cancelButton, okButton);
        root.setBottom(buttonBar);

        var scene = newScene(root);
        stage.setScene(scene);
        stage.setMinWidth(550);
        stage.setMinHeight(350);
        stage.setWidth(600);
        stage.setHeight(400);

        stage.showAndWait();

        try {
            return resultFuture.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateOkButtonState() {
        if (okButton != null) {
            boolean allValid = validationStates.values().stream().allMatch(Boolean::booleanValue);
            okButton.setDisable(!allValid);
        }
    }
}
