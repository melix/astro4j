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

import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import me.champeau.a4j.jsolex.processing.params.ChoiceParameter;
import me.champeau.a4j.jsolex.processing.params.NumberParameter;
import me.champeau.a4j.jsolex.processing.params.ScriptParameter;
import me.champeau.a4j.jsolex.processing.params.StringParameter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public class ScriptParameterUIBuilder {

    public static void buildParameterGrid(
            GridPane grid,
            List<ScriptParameter> parameters,
            String currentLanguage,
            Map<String, Object> initialValues,
            BiConsumer<String, Boolean> validationCallback,
            BiConsumer<String, Object> valueChangeCallback) {

        int row = 0;
        for (var param : parameters) {
            var displayName = param.getDisplayName(currentLanguage);
            var label = new Label(displayName + ":");
            label.getStyleClass().addAll("field-label", "field-label-wrapped");
            grid.add(label, 0, row);

            var initialValue = initialValues != null ? initialValues.get(param.getName()) : null;
            var control = createParameterControl(param, initialValue, validationCallback, valueChangeCallback);
            javafx.scene.layout.GridPane.setHgrow(control, javafx.scene.layout.Priority.ALWAYS);
            grid.add(control, 1, row);

            var description = param.getDescription(currentLanguage);
            if (description != null && !description.isEmpty()) {
                var helpIcon = new Label("?");
                helpIcon.getStyleClass().addAll("help-icon");

                var customTooltip = new CustomTooltip(description);
                customTooltip.attachTo(helpIcon);

                grid.add(helpIcon, 2, row);
            }

            row++;
        }
    }

    private static Node createParameterControl(
            ScriptParameter param,
            Object initialValue,
            BiConsumer<String, Boolean> validationCallback,
            BiConsumer<String, Object> valueChangeCallback) {

        var paramKey = param.getName();

        switch (param) {
            case ChoiceParameter choiceParam -> {
                var choiceBox = new ChoiceBox<String>();
                choiceBox.getItems().addAll(choiceParam.getChoices());
                if (initialValue != null) {
                    choiceBox.setValue(initialValue.toString());
                }

                var initialValidationResult = choiceParam.validate(initialValue != null ? initialValue : choiceParam.getDefaultValue());
                validationCallback.accept(paramKey, initialValidationResult.isValid());

                if (!initialValidationResult.isValid()) {
                    choiceBox.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    choiceBox.setTooltip(new Tooltip(initialValidationResult.getErrorMessage()));
                }

                choiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        var validationResult = choiceParam.validate(newVal);
                        validationCallback.accept(paramKey, validationResult.isValid());

                        if (!validationResult.isValid()) {
                            choiceBox.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                            choiceBox.setTooltip(new Tooltip(validationResult.getErrorMessage()));
                        } else {
                            choiceBox.setStyle("");
                            choiceBox.setTooltip(null);
                        }

                        valueChangeCallback.accept(param.getName(), newVal);
                    }
                });
                if (initialValue != null) {
                    valueChangeCallback.accept(param.getName(), initialValue);
                }
                return choiceBox;
            }
            case NumberParameter numberParam -> {
                var textField = new TextField();
                var min = numberParam.getMin() != null ? numberParam.getMin().doubleValue() : Double.NEGATIVE_INFINITY;
                var max = numberParam.getMax() != null ? numberParam.getMax().doubleValue() : Double.POSITIVE_INFINITY;
                var value = initialValue instanceof Number num ? num.doubleValue() :
                           (numberParam.getDefaultValue() instanceof Number defNum ? defNum.doubleValue() : 0.0);

                var formatter = new DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.US));
                textField.setText(formatter.format(value));

                var initialValidationResult = numberParam.validate(value);
                validationCallback.accept(paramKey, initialValidationResult.isValid());

                if (!initialValidationResult.isValid()) {
                    textField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    textField.setTooltip(new Tooltip(initialValidationResult.getErrorMessage()));
                }

                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    boolean isValid = false;
                    String errorMessage = null;
                    try {
                        var number = formatter.parse(newVal);
                        var doubleValue = number.doubleValue();
                        var validationResult = numberParam.validate(doubleValue);

                        if (validationResult.isValid()) {
                            textField.setStyle("");
                            textField.setTooltip(null);
                            valueChangeCallback.accept(param.getName(), doubleValue);
                            isValid = true;
                        } else {
                            errorMessage = validationResult.getErrorMessage();
                        }
                    } catch (ParseException e) {
                        errorMessage = "Invalid number format";
                    }

                    if (errorMessage != null) {
                        textField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                        textField.setTooltip(new Tooltip(errorMessage));
                    }

                    validationCallback.accept(paramKey, isValid);
                });

                valueChangeCallback.accept(param.getName(), value);
                return textField;
            }
            case StringParameter stringParam -> {
                var textField = new TextField();
                var textValue = initialValue != null ? initialValue.toString() :
                               (stringParam.getDefaultValue() != null ? stringParam.getDefaultValue().toString() : "");
                textField.setText(textValue);

                var initialValidationResult = stringParam.validate(textValue);
                validationCallback.accept(paramKey, initialValidationResult.isValid());

                if (!initialValidationResult.isValid()) {
                    textField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    textField.setTooltip(new Tooltip(initialValidationResult.getErrorMessage()));
                }

                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    var validationResult = stringParam.validate(newVal);
                    boolean isValid = validationResult.isValid();

                    if (isValid) {
                        textField.setStyle("");
                        textField.setTooltip(null);
                    } else {
                        textField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                        textField.setTooltip(new Tooltip(validationResult.getErrorMessage()));
                    }

                    validationCallback.accept(paramKey, isValid);
                    valueChangeCallback.accept(param.getName(), newVal);
                });
                valueChangeCallback.accept(param.getName(), textValue);
                return textField;
            }
            default -> {
                return null;
            }
        }
    }
}
