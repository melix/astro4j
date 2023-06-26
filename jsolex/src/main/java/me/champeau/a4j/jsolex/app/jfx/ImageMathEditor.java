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

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.expr.Scanner;
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ImageMathProfileIO;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImageMathEditor {
    private static final Pattern VAR_PATTERN = Pattern.compile(Scanner.VARIABLE_REGEX);
    private static final String MATH_EXTENSION = ".math";
    private static final FileChooser.ExtensionFilter MATH_PROFILE_EXTENSION_FILTER = new FileChooser.ExtensionFilter("ImageMath Profile", "*" + MATH_EXTENSION);

    @FXML
    public ListView<ImageMathEntry> elements;
    @FXML
    private TextField label;
    @FXML
    private TextArea expression;
    @FXML
    private TextField pixelShifts;
    @FXML
    private CheckBox output;
    @FXML
    private Group group;
    @FXML
    private Button exportButton;

    private Stage stage;

    private ImageMathConfiguration configuration;

    public void setup(Stage stage, ImageMathParams imageMathParams) {
        this.stage = stage;
        this.configuration = null;
        this.pixelShifts.setEditable(false);
        this.label.setTextFormatter(new TextFormatter<>(change -> {
            if (VAR_PATTERN.matcher(change.getControlNewText()).matches()) {
                return change;
            } else {
                return null;
            }
        }));
        var items = elements.getItems();
        if (imageMathParams != null) {
            populateFromParams(imageMathParams);
        }
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (updating.compareAndSet(false, true)) {
                var index = newValue.intValue();
                var item = items.get(index);
                label.setText(item.label());
                expression.setText(item.expression());
                output.setSelected(item.output());
                updatePixelShifts(item);
                updating.set(false);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newExpression = expression.getText();
                var selectedIndex = selectionModel.getSelectedIndex();
                if (selectedIndex != -1) {
                    var e = new ImageMathEntry(newLabel, newExpression, output.isSelected());
                    items.set(selectedIndex, e);
                    elements.getSelectionModel().select(e);
                    updatePixelShifts(e);
                }
                exportButton.setDisable(false);
                updating.set(false);
            }
        };
        label.textProperty().addListener(updateValueListener);
        expression.textProperty().addListener(updateValueListener);
        output.selectedProperty().addListener(updateValueListener);
        var enabled = !items.isEmpty();
        if (enabled) {
            selectionModel.select(0);
        }
        group.setDisable(!enabled);
        pixelShifts.setDisable(true);
        exportButton.setDisable(true);
    }

    private void populateFromParams(ImageMathParams imageMathParams) {
        var items = elements.getItems();
        items.clear();
        var imagesToGenerate = imageMathParams.imagesToGenerate();
        for (Map.Entry<String, String> entry : imageMathParams.expressions().entrySet()) {
            var key = entry.getKey();
            items.add(new ImageMathEntry(key, entry.getValue(), imagesToGenerate.contains(key)));
        }
    }

    private void updatePixelShifts(ImageMathEntry current) {
        var evaluator = createEvaluator();
        Set<ImageMathEntry> invalidExpressions = new HashSet<>();
        for (ImageMathEntry item : elements.getItems()) {
            if (current != item) {
                try {
                    evaluator.putVariable(item.label(), item.expression());
                } catch (Exception ex) {
                    invalidExpressions.add(item);
                }
            }
        }
        var allShifts = new TreeSet<Integer>();
        for (ImageMathEntry item : elements.getItems()) {
            try {
                evaluator.evaluate(item.expression);
                allShifts.addAll(evaluator.getShifts());
            } catch (Exception ex) {
                invalidExpressions.add(item);
            }
        }
        pixelShifts.setText(allShifts.stream().map(String::valueOf).collect(Collectors.joining(";")));
    }

    static ShiftCollectingImageExpressionEvaluator createEvaluator() {
        var images = new HashMap<Integer, ImageWrapper32>();
        return new ShiftCollectingImageExpressionEvaluator(i -> images.computeIfAbsent(i, unused -> new ImageWrapper32(0, 0, new float[0])));
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    private void cancel() {
        requestClose();
    }

    @FXML
    private void ok() {
        Map<ImageMathEntry, String> invalidExpressions = new HashMap<>();
        var evaluator = createEvaluator();
        for (ImageMathEntry item : elements.getItems()) {
            try {
                evaluator.putVariable(item.label(), item.expression());
            } catch (Exception ex) {
                invalidExpressions.put(item, ex.getMessage());
            }
        }
        var allShifts = new TreeSet<Integer>();
        var outputShifts = new TreeSet<Integer>();
        for (ImageMathEntry item : elements.getItems()) {
            evaluator.clearShifts();
            if (!invalidExpressions.containsKey(item)) {
                evaluator.evaluate(item.expression());
                var expressionShifts = evaluator.getShifts();
                allShifts.addAll(expressionShifts);
                if (item.output()) {
                    outputShifts.addAll(expressionShifts);
                }
            }
        }
        if (invalidExpressions.isEmpty()) {
            var internalShifts = new TreeSet<Integer>();
            internalShifts.addAll(allShifts);
            internalShifts.removeAll(outputShifts);
            configuration = new ImageMathConfiguration(
                    Collections.unmodifiableSet(internalShifts),
                    allShifts.stream().toList(),
                    elements.getItems()
                            .stream()
                            .collect(Collectors.toMap(ImageMathEntry::label, ImageMathEntry::expression)),
                    elements.getItems()
                            .stream()
                            .filter(ImageMathEntry::output)
                            .map(ImageMathEntry::label)
                            .collect(Collectors.toSet())
            );
            if (!exportButton.isDisabled()) {
                var alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "question.export.profile"));
                alert.setHeaderText(I18N.string(JSolEx.class, "imagemath-editor", "question.export.profile.long"));
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                alert.showAndWait().ifPresent(buttonType -> {
                    if (buttonType == ButtonType.YES) {
                        exportProfile();
                    }
                });
            }
            requestClose();
        } else {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid expressions");
            alert.setHeaderText("Some expressions are invalid. You must correct them before saving.");
            alert.setContentText(
                    invalidExpressions.entrySet().stream().map(entry -> {
                        var expr = entry.getKey();
                        var message = entry.getValue();
                        return " - " + expr.label() + " : " + expr.expression() + " [" + message + "]";
                    }).collect(Collectors.joining("\n"))
            );
            alert.showAndWait();
        }
    }


    @FXML
    public void removeSelectedItem() {
        var idx = elements.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            var items = elements.getItems();
            items.remove(idx);
            if (items.isEmpty()) {
                group.setDisable(true);
            }
        }
        exportButton.setDisable(false);
    }

    @FXML
    public void addNewItem() {
        var newElement = new ImageMathEntry("expression_" + elements.getItems().size(), "img(0)", true);
        elements.getItems().add(newElement);
        elements.getSelectionModel().select(newElement);
        group.setDisable(false);
        exportButton.setDisable(false);
    }

    @FXML
    private void exportProfile() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(ImageMathEditor.class, "imagemath-editor", "export.title"));
        var initial = Path.of(System.getProperty("user.home")).resolve(".jsolex");
        try {
            Files.createDirectories(initial);
            fileChooser.setInitialDirectory(initial.toFile());
            fileChooser.getExtensionFilters().add(MATH_PROFILE_EXTENSION_FILTER);
            var selected = fileChooser.showSaveDialog(stage);
            if (selected != null) {
                if (!selected.getName().endsWith(MATH_EXTENSION)) {
                    selected = new File(selected.getParentFile(), selected.getName() + MATH_EXTENSION);
                }
                ImageMathProfileIO.export(convertToParams(), selected.toPath());
                exportButton.setDisable(true);
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    @FXML
    private void importProfile() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(ImageMathEditor.class, "imagemath-editor", "import.title"));
        var initial = Path.of(System.getProperty("user.home")).resolve(".jsolex");
        try {
            fileChooser.setInitialDirectory(initial.toFile());
            fileChooser.getExtensionFilters().add(MATH_PROFILE_EXTENSION_FILTER);
            var selected = fileChooser.showOpenDialog(stage);
            if (selected != null) {
                populateFromParams(ImageMathProfileIO.importFrom(selected.toPath()));
            }
            var items = elements.getItems();
            group.setDisable(items.isEmpty());
            exportButton.setDisable(true);
            if (!items.isEmpty()) {
                elements.getSelectionModel().select(0);
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private ImageMathParams convertToParams() {
        return new ImageMathParams(
                elements.getItems()
                        .stream()
                        .collect(Collectors.toMap(ImageMathEntry::label, ImageMathEntry::expression)),
                elements.getItems()
                        .stream()
                        .filter(ImageMathEntry::output)
                        .map(ImageMathEntry::label)
                        .collect(Collectors.toSet())
        );
    }

    public Optional<ImageMathConfiguration> getConfiguration() {
        return Optional.ofNullable(configuration);
    }

    private record ImageMathEntry(String label, String expression, boolean output) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record ImageMathConfiguration(
            Set<Integer> internalShifts,
            List<Integer> probedPixelShifts,
            Map<String, String> expressions,
            Set<String> output
    ) {

    }
}
