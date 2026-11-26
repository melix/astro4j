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

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.CustomTooltip;
import me.champeau.a4j.jsolex.app.jfx.I18N;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

final class OnboardingUi {
    private static final String BUNDLE = "onboarding";

    private OnboardingUi() {

    }

    static String message(String key) {
        return I18N.string(JSolEx.class, BUNDLE, key);
    }

    static Label label(String key, String... styleClasses) {
        return styled(new Label(message(key)), styleClasses);
    }

    static Label text(String text, String... styleClasses) {
        return styled(new Label(text), styleClasses);
    }

    private static Label styled(Label label, String... styleClasses) {
        label.getStyleClass().addAll(styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static TextField smallField() {
        var field = new TextField();
        field.setMinWidth(90);
        field.setPrefWidth(90);
        field.setMaxWidth(90);
        return field;
    }

    static HBox withUnit(Node field, String unit) {
        var box = new HBox(6, field, text(unit, "onboarding-muted"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static Node helpIcon(String key) {
        var icon = new Label("?");
        icon.getStyleClass().add("help-icon");
        new CustomTooltip(message(key)).attachTo(icon);
        return icon;
    }

    static GridPane formGrid() {
        var grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        var labels = new ColumnConstraints();
        labels.setMinWidth(170);
        labels.setPrefWidth(190);
        labels.setHalignment(HPos.RIGHT);
        var controls = new ColumnConstraints();
        controls.setMinWidth(200);
        controls.setPrefWidth(300);
        var notes = new ColumnConstraints();
        notes.setMinWidth(120);
        notes.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, controls, notes);
        return grid;
    }

    static void addRow(GridPane grid, int row, String labelKey, String helpKey, Node control, Node note) {
        var labelBox = new HBox(6, new Label(message(labelKey)));
        labelBox.setAlignment(Pos.CENTER_RIGHT);
        if (helpKey != null) {
            labelBox.getChildren().add(helpIcon(helpKey));
        }
        grid.add(labelBox, 0, row);
        grid.add(control, 1, row);
        if (note != null) {
            grid.add(note, 2, row);
        }
    }

    static String formatNumber(double value) {
        var format = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    static Optional<Double> parsePositive(String text) {
        return parseNumber(text).filter(value -> value > 0);
    }

    static Optional<Double> parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            var value = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isSerFile(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".ser");
    }

    static void acceptSerFileDrop(Node target, Consumer<File> onDrop) {
        target.setOnDragOver(event -> {
            if (droppedSerFile(event).isPresent()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        target.setOnDragDropped(event -> {
            var file = droppedSerFile(event);
            file.ifPresent(onDrop);
            event.setDropCompleted(file.isPresent());
            event.consume();
        });
    }

    private static Optional<File> droppedSerFile(DragEvent event) {
        var dragboard = event.getDragboard();
        if (!dragboard.hasFiles() || dragboard.getFiles().size() != 1) {
            return Optional.empty();
        }
        var file = dragboard.getFiles().getFirst();
        return isSerFile(file) ? Optional.of(file) : Optional.empty();
    }
}
