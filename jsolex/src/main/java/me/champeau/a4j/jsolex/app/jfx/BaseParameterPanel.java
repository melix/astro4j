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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.JSolEx;

public abstract class BaseParameterPanel extends VBox {

    protected VBox createSection(String title) {
        var localized = I18N.string(JSolEx.class, "process-params", title);
        if (localized.isEmpty()) {
            localized = title;
        }
        var section = new VBox(8);
        section.getStyleClass().add("panel-section");

        var titleLabel = new Label(localized);
        titleLabel.getStyleClass().add("panel-section-title");
        section.getChildren().add(titleLabel);

        return section;
    }

    protected GridPane createGrid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        var labelCol = new ColumnConstraints();
        labelCol.setMinWidth(180);
        labelCol.setPrefWidth(200);
        labelCol.setMaxWidth(220);

        var controlCol = new ColumnConstraints();
        controlCol.setMinWidth(180);
        controlCol.setPrefWidth(200);
        controlCol.setHgrow(Priority.SOMETIMES);

        var helpCol = new ColumnConstraints();
        helpCol.setMinWidth(24);
        helpCol.setPrefWidth(24);
        helpCol.setMaxWidth(24);

        grid.getColumnConstraints().addAll(labelCol, controlCol, helpCol);
        grid.getStyleClass().add("form-grid");
        return grid;
    }

    protected void addGridRow(GridPane grid, int row, String labelText, Node control) {
        addGridRow(grid, row, labelText, control, null);
    }

    protected void addGridRow(GridPane grid, int row, String labelText, Node control, String tooltipKey) {
        var label = new Label(labelText);
        label.getStyleClass().addAll("field-label", "field-label-wrapped");

        grid.add(label, 0, row);
        grid.add(control, 1, row);

        addHelpIcon(grid, row, tooltipKey);
    }

    protected void addGridRow(GridPane grid, int row, Node control, String tooltipKey) {
        addGridRow(grid, row, control);
        addHelpIcon(grid, row, tooltipKey);
    }

    protected void addGridRow(GridPane grid, int row, Node control) {
        grid.add(control, 0, row, 2, 1);
    }

    private void addHelpIcon(GridPane grid, int row, String tooltipKey) {
        if (tooltipKey != null) {
            try {
                var tooltipText = I18N.string(JSolEx.class, "process-params", tooltipKey);
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
    
    protected TextField createTextField(String defaultValue, String tooltipText) {
        var field = new TextField(defaultValue);
        field.getStyleClass().add("modern-text-field");
        if (tooltipText != null) {
            var tooltip = new Tooltip(tooltipText);
            field.setTooltip(tooltip);
        }
        return field;
    }
    
    protected CheckBox createCheckBox(String text, String tooltipText) {
        var checkBox = new CheckBox(text);
        if (tooltipText != null) {
            var tooltip = new Tooltip(tooltipText);
            checkBox.setTooltip(tooltip);
        }
        return checkBox;
    }
    
    protected <T> ChoiceBox<T> createChoiceBox() {
        var choiceBox = new ChoiceBox<T>();
        choiceBox.setMaxWidth(Double.MAX_VALUE);
        return choiceBox;
    }
    
    protected HBox createChoiceBoxWithButton(ChoiceBox<?> choiceBox, Button button) {
        var box = createHBox();
        HBox.setHgrow(choiceBox, Priority.ALWAYS);
        box.getChildren().addAll(choiceBox, button);
        return box;
    }
    
    protected int parseInt(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    protected double parseDouble(String text, double defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    protected Double parseDoubleOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    protected Integer parseIntegerOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected HBox createHBox() {
        var box = new HBox(8);
        VBox.setVgrow(box, Priority.ALWAYS);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}