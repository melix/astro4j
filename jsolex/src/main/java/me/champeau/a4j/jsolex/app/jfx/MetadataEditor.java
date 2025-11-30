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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.expr.ImageMathParser;
import me.champeau.a4j.jsolex.expr.ast.ImageMathScript;
import me.champeau.a4j.jsolex.expr.ast.MetaBlock;
import me.champeau.a4j.jsolex.processing.params.ChoiceParameter;
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor;
import me.champeau.a4j.jsolex.processing.params.NumberParameter;
import me.champeau.a4j.jsolex.processing.params.OutputMetadata;
import me.champeau.a4j.jsolex.processing.params.ParameterType;
import me.champeau.a4j.jsolex.processing.params.ScriptParameter;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

public class MetadataEditor {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");

    @FXML
    private TextField authorField;
    @FXML
    private TextField versionField;
    @FXML
    private TextField requiresField;
    @FXML
    private TextField titleEnField;
    @FXML
    private TextField titleFrField;
    @FXML
    private TextArea descriptionEnField;
    @FXML
    private TextArea descriptionFrField;
    @FXML
    private ListView<ParameterModel> parametersList;
    @FXML
    private Button addParameterButton;
    @FXML
    private Button removeParameterButton;
    @FXML
    private TextField parameterNameField;
    @FXML
    private ChoiceBox<ParameterType> parameterTypeChoice;
    @FXML
    private TextField parameterNameEnField;
    @FXML
    private TextField parameterNameFrField;
    @FXML
    private TextArea parameterDescEnField;
    @FXML
    private TextArea parameterDescFrField;
    @FXML
    private TextField defaultValueField;
    @FXML
    private TextField minValueField;
    @FXML
    private TextField maxValueField;
    @FXML
    private ListView<String> choicesList;
    @FXML
    private Button addChoiceButton;
    @FXML
    private Button removeChoiceButton;
    @FXML
    private TextField choiceValueField;
    @FXML
    private VBox parameterFormContainer;
    @FXML
    private GridPane generalInfoGrid;
    @FXML
    private GridPane parameterBasicGrid;
    @FXML
    private VBox numberFieldsBox;
    @FXML
    private VBox choicesBox;
    @FXML
    private ListView<OutputModel> outputsList;
    @FXML
    private Button addOutputButton;
    @FXML
    private Button removeOutputButton;
    @FXML
    private TextField outputNameField;
    @FXML
    private TextField outputTitleEnField;
    @FXML
    private TextField outputTitleFrField;
    @FXML
    private TextArea outputDescEnField;
    @FXML
    private TextArea outputDescFrField;
    @FXML
    private VBox outputFormContainer;

    private Stage stage;
    private String originalScript;
    private BiConsumer<String, String> onSave;
    private final ObservableList<ParameterModel> parameters = FXCollections.observableArrayList();
    private final ObservableList<OutputModel> outputs = FXCollections.observableArrayList();
    private ParameterModel selectedParameter;
    private OutputModel selectedOutput;

    @FXML
    private void initialize() {
        if (requiresField.getText().trim().isEmpty()) {
            requiresField.setText(VersionUtil.getVersion());
        }

        parametersList.setItems(parameters);
        parametersList.setCellFactory(_ -> new ListCell<ParameterModel>() {
            @Override
            protected void updateItem(ParameterModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });

        parametersList.getSelectionModel().selectedItemProperty().addListener((_, oldValue, newValue) -> {
            if (oldValue != null) {
                saveParameterChanges(oldValue);
            }
            selectedParameter = newValue;
            updateParameterForm();
        });

        parameterTypeChoice.setItems(FXCollections.observableArrayList(
            ParameterType.NUMBER,
            ParameterType.CHOICE,
            ParameterType.STRING
        ));

        parameterTypeChoice.getSelectionModel().selectedItemProperty().addListener((_, _, newType) -> {
            if (selectedParameter != null) {
                selectedParameter.setType(newType);
                updateParameterFormVisibility();
            }
        });

        choicesList.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> {
            removeChoiceButton.setDisable(choicesList.getSelectionModel().getSelectedItem() == null);
        });

        outputsList.setItems(outputs);
        outputsList.setCellFactory(_ -> new ListCell<OutputModel>() {
            @Override
            protected void updateItem(OutputModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });

        outputsList.getSelectionModel().selectedItemProperty().addListener((_, oldValue, newValue) -> {
            if (oldValue != null) {
                saveOutputChanges(oldValue);
            }
            selectedOutput = newValue;
            updateOutputForm();
        });

        setupValidationListeners();
        setupHelpIcons();
        updateButtonStates();
    }

    private void setupValidationListeners() {
        authorField.textProperty().addListener((_, _, _) -> validateFieldVisually(authorField));
        versionField.textProperty().addListener((_, _, _) -> validateFieldVisually(versionField));
        requiresField.textProperty().addListener((_, _, _) -> validateRequiresField());
        titleEnField.textProperty().addListener((_, _, _) -> validateTitleFields());
        titleFrField.textProperty().addListener((_, _, _) -> validateTitleFields());
        parameterNameField.textProperty().addListener((_, _, _) -> validateFieldVisually(parameterNameField));
    }

    private void validateRequiresField() {
        var text = requiresField.getText().trim();
        boolean isValid = !text.isEmpty() && VERSION_PATTERN.matcher(text).matches();
        updateFieldValidationStyle(requiresField, isValid);
    }

    private void validateTitleFields() {
        boolean hasTitle = !titleEnField.getText().trim().isEmpty() || !titleFrField.getText().trim().isEmpty();
        if (hasTitle) {
            updateFieldValidationStyle(titleEnField, true);
            updateFieldValidationStyle(titleFrField, true);
        } else {
            updateFieldValidationStyle(titleEnField, false);
            updateFieldValidationStyle(titleFrField, false);
        }
    }

    private void validateFieldVisually(TextField field) {
        boolean isValid = !field.getText().trim().isEmpty();
        updateFieldValidationStyle(field, isValid);
    }

    private void updateFieldValidationStyle(TextField field, boolean isValid) {
        var currentStyle = field.getStyle() != null ? field.getStyle() : "";

        if (isValid) {
            if (currentStyle.contains("-fx-border-color: red")) {
                currentStyle = currentStyle.replaceAll("-fx-border-color: red;", "")
                        .replaceAll("-fx-border-width: 2px;", "")
                        .trim();
                currentStyle = currentStyle.replaceAll("\\s+", " ").replaceAll(";+", ";");
                if (currentStyle.endsWith(";")) {
                    currentStyle = currentStyle.substring(0, currentStyle.length() - 1);
                }
                field.setStyle(currentStyle);
            }
            field.setTooltip(null);
        } else {
            if (!currentStyle.contains("-fx-border-color: red")) {
                if (!currentStyle.isEmpty() && !currentStyle.endsWith(";")) {
                    currentStyle += "; ";
                }
                currentStyle += "-fx-border-color: red; -fx-border-width: 2px;";
                field.setStyle(currentStyle);
            }
            String tooltipText = getValidationTooltipText(field);
            if (tooltipText != null) {
                var tooltip = new Tooltip(tooltipText);
                tooltip.setShowDelay(Duration.millis(100));
                field.setTooltip(tooltip);
            }
        }
    }

    private String getValidationTooltipText(TextField field) {
        if (field == authorField) {
            return I18N.string(JSolEx.class, "metadata-editor", "validation.author.empty");
        } else if (field == titleEnField || field == titleFrField) {
            return I18N.string(JSolEx.class, "metadata-editor", "validation.title.empty");
        } else if (field == versionField) {
            return I18N.string(JSolEx.class, "metadata-editor", "validation.version.empty");
        } else if (field == requiresField) {
            return I18N.string(JSolEx.class, "metadata-editor", "validation.requires.invalid");
        } else if (field == parameterNameField) {
            return I18N.string(JSolEx.class, "metadata-editor", "validation.parameter.name.empty");
        }
        return null;
    }

    private void setupHelpIcons() {
        addHelpIcon(generalInfoGrid, 0, "tooltip.author");
        addHelpIcon(generalInfoGrid, 1, "tooltip.version");
        addHelpIcon(generalInfoGrid, 2, "tooltip.requires");
        addHelpIcon(parameterBasicGrid, 0, "tooltip.parameter.name");
        addHelpIcon(parameterBasicGrid, 1, "tooltip.parameter.type");
    }

    private void addHelpIcon(GridPane grid, int row, String tooltipKey) {
        if (tooltipKey != null) {
            try {
                var tooltipText = I18N.string(JSolEx.class, "metadata-editor", tooltipKey);
                if (!tooltipText.equals(tooltipKey)) {
                    var helpIcon = new Label("?");
                    helpIcon.getStyleClass().addAll("help-icon");

                    var customTooltip = new CustomTooltip(tooltipText);
                    customTooltip.attachTo(helpIcon);

                    grid.add(helpIcon, 2, row);
                }
            } catch (Exception e) {
            }
        }
    }

    public static void openEditor(Stage stage, String scriptText, BiConsumer<String, String> onSave) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "metadata-editor");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (MetadataEditor) fxmlLoader.getController();
            controller.setup(stage, scriptText, onSave);
            Scene scene = newScene(node);
            var currentScene = stage.getScene();
            stage.setTitle(I18N.string(JSolEx.class, "metadata-editor", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setup(Stage stage, String scriptText, BiConsumer<String, String> onSave) {
        this.stage = stage;
        this.originalScript = scriptText;
        this.onSave = onSave;
        parseMetadata(scriptText);
    }

    private void parseMetadata(String scriptText) {
        try {
            var parser = new ImageMathParser(scriptText);
            parser.parse();
            var script = (ImageMathScript) parser.rootNode();
            var metaBlock = findMetaBlock(script);

            if (metaBlock != null) {
                var extractionResult = new ImageMathParameterExtractor().extractParametersFromAST(script, "");

                authorField.setText(extractionResult.getAuthor());
                versionField.setText(extractionResult.getVersion());
                requiresField.setText(extractionResult.getRequiredVersion());

                extractionResult.getTitle().forEach((lang, text) -> {
                    if ("en".equals(lang) || "default".equals(lang)) {
                        titleEnField.setText(text);
                    } else if ("fr".equals(lang)) {
                        titleFrField.setText(text);
                    }
                });

                extractionResult.getDescription().forEach((lang, text) -> {
                    if ("en".equals(lang) || "default".equals(lang)) {
                        descriptionEnField.setText(text);
                    } else if ("fr".equals(lang)) {
                        descriptionFrField.setText(text);
                    }
                });

                extractionResult.getParameters().forEach(param -> {
                    var model = new ParameterModel(param);
                    parameters.add(model);
                });

                extractionResult.getOutputsMetadata().forEach((name, metadata) -> {
                    var model = new OutputModel(metadata);
                    outputs.add(model);
                });
            }
        } catch (Exception e) {
        }
    }

    private MetaBlock findMetaBlock(Object node) {
        if (node instanceof MetaBlock) {
            return (MetaBlock) node;
        }
        if (node instanceof javafx.scene.Node) {
            return null;
        }
        try {
            var method = node.getClass().getMethod("children");
            var children = (List<?>) method.invoke(node);
            for (var child : children) {
                var result = findMetaBlock(child);
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private void updateParameterForm() {
        if (selectedParameter == null) {
            parameterFormContainer.setDisable(true);
            parameterNameField.clear();
            parameterTypeChoice.getSelectionModel().clearSelection();
            parameterNameEnField.clear();
            parameterNameFrField.clear();
            parameterDescEnField.clear();
            parameterDescFrField.clear();
            defaultValueField.clear();
            minValueField.clear();
            maxValueField.clear();
            choicesList.getItems().clear();
        } else {
            parameterFormContainer.setDisable(false);
            parameterNameField.setText(selectedParameter.getName());
            parameterTypeChoice.getSelectionModel().select(selectedParameter.getType());
            parameterNameEnField.setText(selectedParameter.getDisplayNameEn());
            parameterNameFrField.setText(selectedParameter.getDisplayNameFr());
            parameterDescEnField.setText(selectedParameter.getDescriptionEn());
            parameterDescFrField.setText(selectedParameter.getDescriptionFr());

            if (selectedParameter.getDefaultValue() != null) {
                defaultValueField.setText(selectedParameter.getDefaultValue().toString());
            } else {
                defaultValueField.clear();
            }

            if (selectedParameter.getType() == ParameterType.NUMBER) {
                minValueField.setText(selectedParameter.getMinValue() != null ? selectedParameter.getMinValue().toString() : "");
                maxValueField.setText(selectedParameter.getMaxValue() != null ? selectedParameter.getMaxValue().toString() : "");
            }

            if (selectedParameter.getType() == ParameterType.CHOICE) {
                choicesList.setItems(selectedParameter.getChoices());
            }

            updateParameterFormVisibility();
            validateFieldVisually(parameterNameField);
        }
        updateButtonStates();
    }

    private void updateParameterFormVisibility() {
        if (selectedParameter == null) {
            return;
        }

        var type = selectedParameter.getType();
        var isNumber = type == ParameterType.NUMBER;
        var isChoice = type == ParameterType.CHOICE;

        numberFieldsBox.setVisible(isNumber);
        numberFieldsBox.setManaged(isNumber);

        choicesBox.setVisible(isChoice);
        choicesBox.setManaged(isChoice);

        if (isChoice) {
            choicesList.setItems(selectedParameter.getChoices());
            addDefaultValueToChoices();
            removeChoiceButton.setDisable(choicesList.getSelectionModel().getSelectedItem() == null);
        }
    }

    private void addDefaultValueToChoices() {
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.CHOICE) {
            var defaultValue = defaultValueField.getText().trim();
            if (!defaultValue.isEmpty() && !selectedParameter.getChoices().contains(defaultValue)) {
                selectedParameter.getChoices().add(defaultValue);
            }
        }
    }

    private void updateButtonStates() {
        removeParameterButton.setDisable(selectedParameter == null);
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.CHOICE) {
            removeChoiceButton.setDisable(choicesList.getSelectionModel().getSelectedItem() == null);
        }
        removeOutputButton.setDisable(selectedOutput == null);
    }

    @FXML
    private void handleAddParameter() {
        var dialog = new TextInputDialog();
        dialog.setTitle(I18N.string(JSolEx.class, "metadata-editor", "add.parameter"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18N.string(JSolEx.class, "metadata-editor", "parameter.name.prompt"));
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(inputName -> {
            var name = inputName.trim();
            if (name.isEmpty()) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "parameter.name.empty"));
                return;
            }
            if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "parameter.name.invalid"));
                return;
            }
            if (parameters.stream().anyMatch(p -> p.getName().equals(name))) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "parameter.name.duplicate"));
                return;
            }

            var param = new ParameterModel(name);
            parameters.add(param);
            parametersList.getSelectionModel().select(param);
        });
    }

    @FXML
    private void handleRemoveParameter() {
        if (selectedParameter != null) {
            parameters.remove(selectedParameter);
            if (!parameters.isEmpty()) {
                parametersList.getSelectionModel().selectFirst();
            } else {
                selectedParameter = null;
                updateParameterForm();
            }
        }
    }

    @FXML
    private void handleParameterNameChanged() {
        if (selectedParameter != null) {
            var newName = parameterNameField.getText().trim();
            if (!newName.isEmpty() && IDENTIFIER_PATTERN.matcher(newName).matches()) {
                if (parameters.stream().noneMatch(p -> p != selectedParameter && p.getName().equals(newName))) {
                    selectedParameter.setName(newName);
                    parametersList.refresh();
                }
            }
        }
    }

    @FXML
    private void handleParameterNameEnChanged() {
        if (selectedParameter != null) {
            selectedParameter.setDisplayNameEn(parameterNameEnField.getText());
        }
    }

    @FXML
    private void handleParameterNameFrChanged() {
        if (selectedParameter != null) {
            selectedParameter.setDisplayNameFr(parameterNameFrField.getText());
        }
    }

    @FXML
    private void handleParameterDescEnChanged() {
        if (selectedParameter != null) {
            selectedParameter.setDescriptionEn(parameterDescEnField.getText());
        }
    }

    @FXML
    private void handleParameterDescFrChanged() {
        if (selectedParameter != null) {
            selectedParameter.setDescriptionFr(parameterDescFrField.getText());
        }
    }

    @FXML
    private void handleDefaultValueChanged() {
        if (selectedParameter != null) {
            selectedParameter.setDefaultValue(defaultValueField.getText());
            addDefaultValueToChoices();
        }
    }

    @FXML
    private void handleMinValueChanged() {
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.NUMBER) {
            try {
                var value = minValueField.getText().trim();
                selectedParameter.setMinValue(value.isEmpty() ? null : Double.parseDouble(value));
            } catch (NumberFormatException e) {
            }
        }
    }

    @FXML
    private void handleMaxValueChanged() {
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.NUMBER) {
            try {
                var value = maxValueField.getText().trim();
                selectedParameter.setMaxValue(value.isEmpty() ? null : Double.parseDouble(value));
            } catch (NumberFormatException e) {
            }
        }
    }

    @FXML
    private void handleAddChoice() {
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.CHOICE) {
            var value = choiceValueField.getText().trim();
            if (value.isEmpty()) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "choice.value.empty"));
                return;
            }
            if (!selectedParameter.getChoices().contains(value)) {
                selectedParameter.getChoices().add(value);
                choiceValueField.clear();
            }
        }
    }

    private void showError(String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    @FXML
    private void handleRemoveChoice() {
        if (selectedParameter != null && selectedParameter.getType() == ParameterType.CHOICE) {
            var selected = choicesList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedParameter.getChoices().remove(selected);
            }
        }
    }

    private void updateOutputForm() {
        if (selectedOutput == null) {
            outputFormContainer.setDisable(true);
            outputNameField.clear();
            outputTitleEnField.clear();
            outputTitleFrField.clear();
            outputDescEnField.clear();
            outputDescFrField.clear();
        } else {
            outputFormContainer.setDisable(false);
            outputNameField.setText(selectedOutput.getName());
            outputTitleEnField.setText(selectedOutput.getTitleEn());
            outputTitleFrField.setText(selectedOutput.getTitleFr());
            outputDescEnField.setText(selectedOutput.getDescriptionEn());
            outputDescFrField.setText(selectedOutput.getDescriptionFr());
        }
        updateButtonStates();
    }

    @FXML
    private void handleAddOutput() {
        var dialog = new TextInputDialog();
        dialog.setTitle(I18N.string(JSolEx.class, "metadata-editor", "add.output"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18N.string(JSolEx.class, "metadata-editor", "output.name.prompt"));
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(inputName -> {
            var name = inputName.trim();
            if (name.isEmpty()) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "output.name.empty"));
                return;
            }
            if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "output.name.invalid"));
                return;
            }
            if (outputs.stream().anyMatch(o -> o.getName().equals(name))) {
                showError(I18N.string(JSolEx.class, "metadata-editor", "output.name.duplicate"));
                return;
            }

            var output = new OutputModel(name);
            outputs.add(output);
            outputsList.getSelectionModel().select(output);
        });
    }

    @FXML
    private void handleRemoveOutput() {
        if (selectedOutput != null) {
            outputs.remove(selectedOutput);
            if (!outputs.isEmpty()) {
                outputsList.getSelectionModel().selectFirst();
            } else {
                selectedOutput = null;
                updateOutputForm();
            }
        }
    }

    @FXML
    private void handleOutputNameChanged() {
        if (selectedOutput != null) {
            var newName = outputNameField.getText().trim();
            if (!newName.isEmpty() && IDENTIFIER_PATTERN.matcher(newName).matches()) {
                if (outputs.stream().noneMatch(o -> o != selectedOutput && o.getName().equals(newName))) {
                    selectedOutput.setName(newName);
                    outputsList.refresh();
                }
            }
        }
    }

    @FXML
    private void handleOutputTitleEnChanged() {
        if (selectedOutput != null) {
            selectedOutput.setTitleEn(outputTitleEnField.getText());
        }
    }

    @FXML
    private void handleOutputTitleFrChanged() {
        if (selectedOutput != null) {
            selectedOutput.setTitleFr(outputTitleFrField.getText());
        }
    }

    @FXML
    private void handleOutputDescEnChanged() {
        if (selectedOutput != null) {
            selectedOutput.setDescriptionEn(outputDescEnField.getText());
        }
    }

    @FXML
    private void handleOutputDescFrChanged() {
        if (selectedOutput != null) {
            selectedOutput.setDescriptionFr(outputDescFrField.getText());
        }
    }

    @FXML
    private void handleOk() {
        saveCurrentParameterChanges();
        saveCurrentOutputChanges();
        if (!validateMetadata()) {
            return;
        }
        var newMetaBlock = generateMetaBlock();
        var newScript = replaceMetaBlock(originalScript, newMetaBlock);
        onSave.accept(originalScript, newScript);
        stage.close();
    }

    private void saveCurrentParameterChanges() {
        if (selectedParameter != null) {
            saveParameterChanges(selectedParameter);
        }
    }

    private void saveParameterChanges(ParameterModel param) {
        var newName = parameterNameField.getText().trim();
        if (!newName.isEmpty() && IDENTIFIER_PATTERN.matcher(newName).matches()) {
            if (parameters.stream().noneMatch(p -> p != param && p.getName().equals(newName))) {
                param.setName(newName);
            }
        }

        param.setDisplayNameEn(parameterNameEnField.getText());
        param.setDisplayNameFr(parameterNameFrField.getText());
        param.setDescriptionEn(parameterDescEnField.getText());
        param.setDescriptionFr(parameterDescFrField.getText());
        param.setDefaultValue(defaultValueField.getText());

        if (param.getType() == ParameterType.NUMBER) {
            try {
                var minText = minValueField.getText().trim();
                param.setMinValue(minText.isEmpty() ? null : Double.parseDouble(minText));
            } catch (NumberFormatException e) {
            }
            try {
                var maxText = maxValueField.getText().trim();
                param.setMaxValue(maxText.isEmpty() ? null : Double.parseDouble(maxText));
            } catch (NumberFormatException e) {
            }
        }

        if (param.getType() == ParameterType.CHOICE) {
            addDefaultValueToChoices();
        }
    }

    private void saveCurrentOutputChanges() {
        if (selectedOutput != null) {
            saveOutputChanges(selectedOutput);
        }
    }

    private void saveOutputChanges(OutputModel output) {
        var newName = outputNameField.getText().trim();
        if (!newName.isEmpty() && IDENTIFIER_PATTERN.matcher(newName).matches()) {
            if (outputs.stream().noneMatch(o -> o != output && o.getName().equals(newName))) {
                output.setName(newName);
            }
        }

        output.setTitleEn(outputTitleEnField.getText());
        output.setTitleFr(outputTitleFrField.getText());
        output.setDescriptionEn(outputDescEnField.getText());
        output.setDescriptionFr(outputDescFrField.getText());
    }

    private boolean validateMetadata() {
        validateFieldVisually(authorField);
        validateFieldVisually(versionField);
        validateRequiresField();
        validateTitleFields();

        if (authorField.getText().trim().isEmpty()) {
            authorField.requestFocus();
            return false;
        }

        if (titleEnField.getText().trim().isEmpty() && titleFrField.getText().trim().isEmpty()) {
            titleEnField.requestFocus();
            return false;
        }

        if (versionField.getText().trim().isEmpty()) {
            versionField.requestFocus();
            return false;
        }

        var requiresText = requiresField.getText().trim();
        if (requiresText.isEmpty() || !VERSION_PATTERN.matcher(requiresText).matches()) {
            requiresField.requestFocus();
            return false;
        }

        for (var param : parameters) {
            if (param.getName().trim().isEmpty()) {
                parametersList.getSelectionModel().select(param);
                parameterNameField.requestFocus();
                return false;
            }
        }

        return true;
    }

    @FXML
    private void handleCancel() {
        stage.close();
    }

    private String generateMetaBlock() {
        var writer = new IndentedWriter();
        writer.writeLine("meta {");
        writer.indent();

        writeLocalizedBlock(writer, "title", titleEnField.getText(), titleFrField.getText());
        writeLocalizedBlock(writer, "description", descriptionEnField.getText(), descriptionFrField.getText());
        writeStringProperty(writer, "author", authorField.getText());
        writeStringProperty(writer, "version", versionField.getText());
        writeStringProperty(writer, "requires", requiresField.getText());

        if (!parameters.isEmpty()) {
            writer.writeLine("params {");
            writer.indent();
            for (var param : parameters) {
                writeParameter(writer, param);
            }
            writer.dedent();
            writer.writeLine("}");
        }

        if (!outputs.isEmpty()) {
            writer.writeLine("outputs {");
            writer.indent();
            for (var output : outputs) {
                writeOutput(writer, output);
            }
            writer.dedent();
            writer.writeLine("}");
        }

        writer.dedent();
        writer.writeLine("}");
        return writer.toString();
    }

    private void writeLocalizedBlock(IndentedWriter writer, String blockName, String enValue, String frValue) {
        var enText = enValue.trim();
        var frText = frValue.trim();
        if (!enText.isEmpty() || !frText.isEmpty()) {
            writer.writeLine(blockName + " {");
            writer.indent();
            if (!enText.isEmpty()) {
                writer.writeProperty("en", escapeString(enText));
            }
            if (!frText.isEmpty()) {
                writer.writeProperty("fr", escapeString(frText));
            }
            writer.dedent();
            writer.writeLine("}");
        }
    }

    private void writeStringProperty(IndentedWriter writer, String name, String value) {
        var text = value.trim();
        if (!text.isEmpty()) {
            writer.writeProperty(name, escapeString(text));
        }
    }

    private void writeParameter(IndentedWriter writer, ParameterModel param) {
        writer.writeLine(param.getName() + " {");
        writer.indent();

        writer.writeProperty("type", param.getType().name().toLowerCase());
        writeLocalizedBlock(writer, "name", param.getDisplayNameEn(), param.getDisplayNameFr());
        writeLocalizedBlock(writer, "description", param.getDescriptionEn(), param.getDescriptionFr());

        if (param.getDefaultValue() != null) {
            if (param.getType() == ParameterType.STRING || param.getType() == ParameterType.CHOICE) {
                writer.writeProperty("default", escapeString(param.getDefaultValue().toString()));
            } else {
                writer.writeRawProperty("default", param.getDefaultValue().toString());
            }
        }

        if (param.getType() == ParameterType.NUMBER) {
            if (param.getMinValue() != null) {
                writer.writeRawProperty("min", param.getMinValue().toString());
            }
            if (param.getMaxValue() != null) {
                writer.writeRawProperty("max", param.getMaxValue().toString());
            }
        }

        if (param.getType() == ParameterType.CHOICE && !param.getChoices().isEmpty()) {
            var choices = new StringBuilder();
            for (int i = 0; i < param.getChoices().size(); i++) {
                if (i > 0) {
                    choices.append(",");
                }
                choices.append(param.getChoices().get(i));
            }
            writer.writeProperty("choices", choices.toString());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void writeOutput(IndentedWriter writer, OutputModel output) {
        writer.writeLine(output.getName() + " {");
        writer.indent();

        writeLocalizedBlock(writer, "title", output.getTitleEn(), output.getTitleFr());
        writeLocalizedBlock(writer, "description", output.getDescriptionEn(), output.getDescriptionFr());

        writer.dedent();
        writer.writeLine("}");
    }

    private static class IndentedWriter {
        private final StringBuilder sb = new StringBuilder();
        private int indentLevel = 0;
        private static final String INDENT = "  ";

        void indent() {
            indentLevel++;
        }

        void dedent() {
            if (indentLevel > 0) {
                indentLevel--;
            }
        }

        void writeLine(String line) {
            writeIndent();
            sb.append(line).append("\n");
        }

        void writeProperty(String name, String value) {
            writeIndent();
            sb.append(name).append(" = \"").append(value).append("\"\n");
        }

        void writeRawProperty(String name, String value) {
            writeIndent();
            sb.append(name).append(" = ").append(value).append("\n");
        }

        private void writeIndent() {
            for (int i = 0; i < indentLevel; i++) {
                sb.append(INDENT);
            }
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String replaceMetaBlock(String script, String newMetaBlock) {
        try {
            var parser = new ImageMathParser(script);
            parser.parse();
            var ast = parser.rootNode();
            var metaBlock = findMetaBlock(ast);

            if (metaBlock != null) {
                var start = metaBlock.getBeginOffset();
                var end = metaBlock.getEndOffset();

                if (end < script.length() && script.charAt(end) == '\n') {
                    end++;
                }

                return script.substring(0, start) + newMetaBlock + script.substring(end);
            } else {
                return newMetaBlock + "\n" + script;
            }
        } catch (Exception e) {
            return newMetaBlock + "\n" + script;
        }
    }

    public static class ParameterModel {
        private final StringProperty name = new SimpleStringProperty();
        private final ObjectProperty<ParameterType> type = new SimpleObjectProperty<>(ParameterType.NUMBER);
        private final StringProperty displayNameEn = new SimpleStringProperty("");
        private final StringProperty displayNameFr = new SimpleStringProperty("");
        private final StringProperty descriptionEn = new SimpleStringProperty("");
        private final StringProperty descriptionFr = new SimpleStringProperty("");
        private final ObjectProperty<Object> defaultValue = new SimpleObjectProperty<>();
        private final ObjectProperty<Double> minValue = new SimpleObjectProperty<>();
        private final ObjectProperty<Double> maxValue = new SimpleObjectProperty<>();
        private final ObservableList<String> choices = FXCollections.observableArrayList();

        public ParameterModel(String name) {
            this.name.set(name);
        }

        public ParameterModel(ScriptParameter param) {
            this.name.set(param.getName());
            this.type.set(param.getType());
            this.defaultValue.set(param.getDefaultValue());

            param.getDisplayName().forEach((lang, text) -> {
                if ("en".equals(lang) || "default".equals(lang)) {
                    displayNameEn.set(text);
                } else if ("fr".equals(lang)) {
                    displayNameFr.set(text);
                }
            });

            param.getDescription().forEach((lang, text) -> {
                if ("en".equals(lang) || "default".equals(lang)) {
                    descriptionEn.set(text);
                } else if ("fr".equals(lang)) {
                    descriptionFr.set(text);
                }
            });

            if (param instanceof NumberParameter numberParam) {
                minValue.set(numberParam.getMin());
                maxValue.set(numberParam.getMax());
            } else if (param instanceof ChoiceParameter choiceParam) {
                choices.setAll(choiceParam.getChoices());
            }
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public ParameterType getType() {
            return type.get();
        }

        public void setType(ParameterType type) {
            this.type.set(type);
        }

        public String getDisplayNameEn() {
            return displayNameEn.get();
        }

        public void setDisplayNameEn(String displayNameEn) {
            this.displayNameEn.set(displayNameEn);
        }

        public String getDisplayNameFr() {
            return displayNameFr.get();
        }

        public void setDisplayNameFr(String displayNameFr) {
            this.displayNameFr.set(displayNameFr);
        }

        public String getDescriptionEn() {
            return descriptionEn.get();
        }

        public void setDescriptionEn(String descriptionEn) {
            this.descriptionEn.set(descriptionEn);
        }

        public String getDescriptionFr() {
            return descriptionFr.get();
        }

        public void setDescriptionFr(String descriptionFr) {
            this.descriptionFr.set(descriptionFr);
        }

        public Object getDefaultValue() {
            return defaultValue.get();
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue.set(defaultValue);
        }

        public Double getMinValue() {
            return minValue.get();
        }

        public void setMinValue(Double minValue) {
            this.minValue.set(minValue);
        }

        public Double getMaxValue() {
            return maxValue.get();
        }

        public void setMaxValue(Double maxValue) {
            this.maxValue.set(maxValue);
        }

        public ObservableList<String> getChoices() {
            return choices;
        }
    }

    public static class OutputModel {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty titleEn = new SimpleStringProperty("");
        private final StringProperty titleFr = new SimpleStringProperty("");
        private final StringProperty descriptionEn = new SimpleStringProperty("");
        private final StringProperty descriptionFr = new SimpleStringProperty("");

        public OutputModel(String name) {
            this.name.set(name);
        }

        public OutputModel(OutputMetadata metadata) {
            this.name.set(metadata.name());

            metadata.title().forEach((lang, text) -> {
                if ("en".equals(lang) || "default".equals(lang)) {
                    titleEn.set(text);
                } else if ("fr".equals(lang)) {
                    titleFr.set(text);
                }
            });

            metadata.description().forEach((lang, text) -> {
                if ("en".equals(lang) || "default".equals(lang)) {
                    descriptionEn.set(text);
                } else if ("fr".equals(lang)) {
                    descriptionFr.set(text);
                }
            });
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public String getTitleEn() {
            return titleEn.get();
        }

        public void setTitleEn(String titleEn) {
            this.titleEn.set(titleEn);
        }

        public String getTitleFr() {
            return titleFr.get();
        }

        public void setTitleFr(String titleFr) {
            this.titleFr.set(titleFr);
        }

        public String getDescriptionEn() {
            return descriptionEn.get();
        }

        public void setDescriptionEn(String descriptionEn) {
            this.descriptionEn.set(descriptionEn);
        }

        public String getDescriptionFr() {
            return descriptionFr.get();
        }

        public void setDescriptionFr(String descriptionFr) {
            this.descriptionFr.set(descriptionFr);
        }
    }
}
