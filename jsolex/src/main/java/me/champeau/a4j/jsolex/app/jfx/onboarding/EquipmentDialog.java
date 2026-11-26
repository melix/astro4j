/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.onboarding;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.FXUtils;
import me.champeau.a4j.jsolex.app.jfx.SetupEditor;
import me.champeau.a4j.jsolex.app.jfx.SpectroHeliographEditor;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SetupsIO;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;

import java.util.function.Function;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.addRow;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formGrid;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.label;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;

/**
 * A compact window to change the equipment used by the direct processing of the home screen.
 */
public final class EquipmentDialog {
    private final Stage stage;
    private final EquipmentForm form = new EquipmentForm();
    private final ChoiceBox<SpectroHeliograph> instrument = new ChoiceBox<>();
    private final ComboBox<Setup> setup = new ComboBox<>();
    private boolean saved;

    private EquipmentDialog(Stage owner) {
        stage = FXUtils.newStage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(message("equipment.title"));
        var params = ProcessParams.loadDefaults();
        instrument.setConverter(labelConverter(SpectroHeliograph::label));
        setup.setConverter(labelConverter(Setup::label));
        setup.setPromptText(message("equipment.setup.prompt"));
        reloadInstruments(params.observationDetails().instrument());
        form.load(params.observationDetails());
        reloadSetups(findMatchingSetup(params.observationDetails()));
        setup.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                form.load(newValue);
            }
        });
        var grid = formGrid();
        addRow(grid, 0, "equipment.setup", "equipment.setup.help", withEditorButton(setup, "equipment.setup.edit", this::openSetupEditor), null);
        addRow(grid, 1, "step.instrument", "instrument.help", withEditorButton(instrument, "equipment.instrument.edit", this::openInstrumentEditor), null);
        form.addCameraRows(grid, 2);
        var optional = new VBox(16, form.scopeGrid(), form.observerGrid(false));
        var optionalPane = new TitledPane(message("equipment.optional"), optional);
        optionalPane.setExpanded(false);
        optionalPane.setAnimated(false);
        optionalPane.expandedProperty().addListener((obs, wasExpanded, expanded) -> Platform.runLater(stage::sizeToScene));
        var content = new VBox(16, label("equipment.subtitle", "onboarding-subtitle"), grid, optionalPane);
        content.setPadding(new Insets(22, 28, 22, 28));
        var cancel = new Button(message("cancel"));
        cancel.getStyleClass().add("default-button");
        cancel.setOnAction(e -> stage.close());
        var save = new Button(message("save"));
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());
        save.disableProperty().bind(instrument.valueProperty().isNull());
        var buttons = new HBox(10, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getStyleClass().add("onboarding-footer");
        var root = new BorderPane(content);
        root.setBottom(buttons);
        root.getStyleClass().add("onboarding-root");
        root.setPrefWidth(760);
        stage.setScene(JSolEx.newScene(root));
    }

    /**
     * Shows the window and waits until it is closed.
     *
     * @param owner the owner window
     * @return true if the equipment was saved
     */
    public static boolean show(Stage owner) {
        var dialog = new EquipmentDialog(owner);
        dialog.stage.showAndWait();
        return dialog.saved;
    }

    private static <T> StringConverter<T> labelConverter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
    }

    private static HBox withEditorButton(Control control, String tooltipKey, Runnable openEditor) {
        control.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(control, Priority.ALWAYS);
        var button = new Button("...");
        button.getStyleClass().add("default-button");
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.setOnAction(e -> openEditor.run());
        var box = new HBox(8, control, button);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Setup findMatchingSetup(ObservationDetails details) {
        return SetupsIO.loadDefaults().stream()
                .filter(candidate -> candidate.equals(Equipment.toSetup(candidate.label(), details)))
                .findFirst()
                .orElse(null);
    }

    private void reloadSetups(Setup selected) {
        var setups = FXCollections.observableArrayList(SetupsIO.loadDefaults());
        setup.setItems(setups);
        if (selected != null) {
            setups.stream()
                    .filter(candidate -> candidate.label().equals(selected.label()))
                    .findFirst()
                    .ifPresent(setup::setValue);
        }
    }

    private void reloadInstruments(SpectroHeliograph selected) {
        var instruments = FXCollections.observableArrayList(SpectroHeliographsIO.loadDefaults());
        instrument.setItems(instruments);
        if (selected != null) {
            instruments.stream()
                    .filter(candidate -> candidate.label().equals(selected.label()))
                    .findFirst()
                    .ifPresentOrElse(instrument::setValue, () -> {
                        instruments.add(selected);
                        instrument.setValue(selected);
                    });
        }
    }

    private Stage editorStage() {
        var editorStage = new Stage();
        editorStage.initStyle(StageStyle.DECORATED);
        editorStage.initOwner(stage);
        editorStage.initModality(Modality.WINDOW_MODAL);
        return editorStage;
    }

    private void openInstrumentEditor() {
        var editorStage = editorStage();
        SpectroHeliographEditor.openEditor(editorStage, editor -> {
            reloadInstruments(editor.getSelected().orElse(instrument.getValue()));
            editorStage.close();
        });
    }

    private void openSetupEditor() {
        var editorStage = editorStage();
        SetupEditor.openEditor(editorStage, editor -> {
            reloadSetups(editor.getSelected().orElse(setup.getValue()));
            editorStage.close();
        });
    }

    private void save() {
        var params = ProcessParams.loadDefaults();
        var details = form.applyTo(params.observationDetails());
        ProcessParams.saveDefaults(Equipment.withInstrument(params.withObservationDetails(details), instrument.getValue()));
        saved = true;
        stage.close();
    }
}
