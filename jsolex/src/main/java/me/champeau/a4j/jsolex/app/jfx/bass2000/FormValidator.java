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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FormValidator {

    private final Map<Node, Label> errorLabels = new HashMap<>();

    interface ValidationCallback {
        void onValidationChanged(boolean isValid);
    }

    private TextField siteLatitudeField;
    private TextField siteLongitudeField;
    private TextField apertureField;
    private TextField pixelSizeField;
    private TextField collimatorFocalLengthField;
    private TextField cameraLensFocalLengthField;
    private TextField gratingDensityField;
    private TextField totalAngleField;
    private TextField slitWidthField;
    private TextField slitHeightField;
    private TextField orderField;
    private TextField observerEmailField;
    private TextField mountNameField;
    private TextField cameraNameField;
    private TextField telescopeNameField;
    private TextField spectrographNameField;

    private List<TextField> requiredFields;
    private List<CheckBox> requiredCheckboxes;
    private List<ComboBox<String>> requiredComboBoxes;

    FormValidator() {
    }

    Label createErrorLabel() {
        var label = new Label();
        label.getStyleClass().add("field-error-label");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    void registerFieldWithErrorLabel(Node field, Label errorLabel) {
        errorLabels.put(field, errorLabel);
    }

    String computeErrorMessage(TextField field) {
        if (field.getText() == null || field.getText().trim().isEmpty()) {
            return message("validation.error.required");
        }
        if (field == mountNameField || field == cameraNameField || field == telescopeNameField) {
            return message("validation.error.brand.model");
        }
        if (field == spectrographNameField) {
            return message("validation.error.spectrograph");
        }
        if (field == siteLatitudeField) {
            return message("validation.error.latitude");
        }
        if (field == siteLongitudeField) {
            return message("validation.error.longitude");
        }
        if (field == observerEmailField) {
            return message("validation.error.email");
        }
        if (field == orderField) {
            return message("validation.error.positive.integer");
        }
        return message("validation.error.positive.number");
    }

    void setRequiredFields(List<TextField> requiredFields, List<CheckBox> requiredCheckboxes, List<ComboBox<String>> requiredComboBoxes) {
        this.requiredFields = requiredFields;
        this.requiredCheckboxes = requiredCheckboxes;
        this.requiredComboBoxes = requiredComboBoxes;
    }

    void setSpecialFields(TextField siteLatitudeField, TextField siteLongitudeField,
                         TextField apertureField, TextField pixelSizeField,
                         TextField collimatorFocalLengthField, TextField cameraLensFocalLengthField,
                         TextField gratingDensityField, TextField totalAngleField,
                         TextField slitWidthField, TextField slitHeightField,
                         TextField orderField, TextField observerEmailField,
                         TextField mountNameField, TextField cameraNameField,
                         TextField telescopeNameField, TextField spectrographNameField) {
        this.siteLatitudeField = siteLatitudeField;
        this.siteLongitudeField = siteLongitudeField;
        this.apertureField = apertureField;
        this.pixelSizeField = pixelSizeField;
        this.collimatorFocalLengthField = collimatorFocalLengthField;
        this.cameraLensFocalLengthField = cameraLensFocalLengthField;
        this.gratingDensityField = gratingDensityField;
        this.totalAngleField = totalAngleField;
        this.slitWidthField = slitWidthField;
        this.slitHeightField = slitHeightField;
        this.orderField = orderField;
        this.observerEmailField = observerEmailField;
        this.mountNameField = mountNameField;
        this.cameraNameField = cameraNameField;
        this.telescopeNameField = telescopeNameField;
        this.spectrographNameField = spectrographNameField;
    }

    boolean validateForm() {
        boolean allValid = true;

        for (var field : requiredFields) {
            if (!isFieldValid(field)) {
                allValid = false;
                break;
            }
        }

        if (allValid) {
            for (var checkBox : requiredCheckboxes) {
                if (!checkBox.isSelected()) {
                    allValid = false;
                    break;
                }
            }
        }

        if (allValid) {
            for (var comboBox : requiredComboBoxes) {
                if (comboBox.getValue() == null) {
                    allValid = false;
                    break;
                }
            }
        }

        return allValid;
    }

    void validateAllFieldsVisually() {
        for (var field : requiredFields) {
            var isValid = isFieldValid(field);
            updateFieldValidationStyle(field, isValid);
        }

        for (var checkBox : requiredCheckboxes) {
            var isValid = checkBox.isSelected();
            updateFieldValidationStyle(checkBox, isValid);
        }

        for (var comboBox : requiredComboBoxes) {
            var isValid = comboBox.getValue() != null;
            updateFieldValidationStyle(comboBox, isValid);
        }
    }

    boolean isFieldValid(TextField field) {
        var text = field.getText();
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        if (field == siteLatitudeField || field == siteLongitudeField) {
            try {
                var value = Double.parseDouble(text.trim());
                if (field == siteLatitudeField) {
                    return value >= -90 && value <= 90;
                } else {
                    return value >= -180 && value <= 180;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (field == apertureField || field == pixelSizeField ||
                field == collimatorFocalLengthField || field == cameraLensFocalLengthField ||
                field == gratingDensityField || field == totalAngleField ||
                field == slitWidthField || field == slitHeightField) {
            try {
                var value = Double.parseDouble(text.trim());
                return value > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (field == orderField) {
            try {
                var value = Integer.parseInt(text.trim());
                return value > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (field == observerEmailField) {
            return text.trim().contains("@") && text.trim().contains(".");
        }

        if (field == mountNameField || field == cameraNameField || field == telescopeNameField) {
            if (text.trim().contains(" ")) {
                var parts = text.trim().split("\\s+");
                return parts.length > 1
                        && parts[0].length() > 2
                        && parts[1].length() > 1;
            }
            return false;
        } else if (field == spectrographNameField) {
            return !"UNKNOWN".equals(SpectroHeliograph.bass2000Id(spectrographNameField.getText()));
        }

        return true;
    }

    void updateFieldValidationStyle(Node field, boolean isValid) {
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
            if (field instanceof TextField textField) {
                textField.setTooltip(null);
            }
        } else {
            if (!currentStyle.contains("-fx-border-color: red")) {
                if (!currentStyle.isEmpty() && !currentStyle.endsWith(";")) {
                    currentStyle += "; ";
                }
                currentStyle += "-fx-border-color: red; -fx-border-width: 2px;";
                field.setStyle(currentStyle);
            }
            if (field instanceof TextField textField) {
                if (textField == mountNameField || textField == cameraNameField || textField == telescopeNameField) {
                    var key = textField == mountNameField ? "mount" :
                            textField == cameraNameField ? "camera" : "telescope";
                    var tooltip = new Tooltip(message("validation.tooltip." + key));
                    tooltip.setShowDelay(Duration.millis(100));
                    textField.setTooltip(tooltip);
                }
                if (textField == spectrographNameField) {
                    var tooltip = new Tooltip(message("validation.tooltip.spectrograph"));
                    tooltip.setShowDelay(Duration.millis(100));
                    textField.setTooltip(tooltip);
                }
            }
        }

        var errorLabel = errorLabels.get(field);
        if (errorLabel != null) {
            if (isValid) {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            } else if (field instanceof TextField textField) {
                errorLabel.setText(computeErrorMessage(textField));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        }
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }
}