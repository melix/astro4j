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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.stretching.StretchParameter;
import me.champeau.a4j.jsolex.processing.stretching.StretchStep;
import me.champeau.a4j.jsolex.processing.stretching.StretchStepKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controls to edit the parameters of a stretching step. Range parameters are edited
 * with a slider and a text field, parameters with a list of allowed values with a
 * choice box. Changes are reported once the user stops moving a slider.
 */
public final class StretchStepEditor {
    private static final Duration SLIDER_DEBOUNCE = Duration.millis(400);
    private static final double VALUE_FIELD_WIDTH = 64;
    private static final double SLIDER_WIDTH = 120;

    private final Consumer<StretchStep> onChange;
    private final List<ParameterControl> controls = new ArrayList<>();
    private final Map<String, Input> inputs = new HashMap<>();
    private StretchStep step;
    private boolean updating;
    private boolean detached;

    /**
     * The controls of one parameter.
     *
     * @param parameter the parameter
     * @param label the label of the parameter
     * @param input the slider and value field, or the choice box
     */
    public record ParameterControl(StretchParameter parameter, Label label, Node input) {
    }

    /**
     * Creates an editor for a step.
     *
     * @param step the step to edit
     * @param onChange called with the updated step each time the user changes a value
     */
    public StretchStepEditor(StretchStep step, Consumer<StretchStep> onChange) {
        this.step = step;
        this.onChange = onChange;
        for (var parameter : step.kind().parameters()) {
            var label = new Label(parameterLabel(parameter));
            label.setMinWidth(Label.USE_PREF_SIZE);
            var input = parameter.choices().isEmpty() ? createRangeInput(parameter) : createChoiceInput(parameter);
            inputs.put(parameter.name(), input);
            controls.add(new ParameterControl(parameter, label, input.node()));
        }
    }

    /**
     * Returns the label of a kind of step.
     *
     * @param kind the kind of step
     * @return the label
     */
    public static String stepLabel(StretchStepKind kind) {
        return JSolEx.message("stretch.step." + kind.name().toLowerCase(Locale.US));
    }

    /**
     * Returns a short explanation of what a kind of step does.
     *
     * @param kind the kind of step
     * @return the explanation
     */
    public static String stepHelp(StretchStepKind kind) {
        return JSolEx.message("stretch.step." + kind.name().toLowerCase(Locale.US) + ".help");
    }

    private static String parameterLabel(StretchParameter parameter) {
        return JSolEx.message("stretch.param." + parameter.name());
    }

    /**
     * Returns the controls of each parameter, in the order of the parameters.
     *
     * @return the controls
     */
    public List<ParameterControl> controls() {
        return controls;
    }

    /**
     * Returns the step with the values currently entered in the controls, including
     * values which have not been reported yet because the user is still editing them.
     *
     * @return the step
     */
    public StretchStep currentStep() {
        var current = step;
        for (var parameter : step.kind().parameters()) {
            var name = parameter.name();
            current = current.withValue(name, inputs.get(name).enteredValue(current.value(name)));
        }
        return current;
    }

    /**
     * Shows the values of a step of the same kind, without reporting a change.
     *
     * @param newStep the step to show
     */
    public void setStep(StretchStep newStep) {
        if (newStep.kind() != step.kind()) {
            throw new IllegalArgumentException("Expected a step of kind " + step.kind() + " but got " + newStep.kind());
        }
        step = newStep;
        updating = true;
        try {
            for (var parameter : newStep.kind().parameters()) {
                inputs.get(parameter.name()).show(parameter, newStep.value(parameter.name()));
            }
        } finally {
            updating = false;
        }
    }

    /**
     * Lays out all parameters on a single line.
     *
     * @return the line
     */
    public HBox inlineLayout() {
        var box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        for (var control : controls) {
            HBox.setHgrow(control.input(), Priority.ALWAYS);
            box.getChildren().addAll(control.label(), control.input());
        }
        return box;
    }

    private RangeInput createRangeInput(StretchParameter parameter) {
        var initial = step.value(parameter.name());
        var slider = new Slider(parameter.min(), parameter.max(), Math.clamp(initial, parameter.min(), parameter.max()));
        slider.setBlockIncrement(parameter.increment());
        slider.setPrefWidth(SLIDER_WIDTH);
        slider.setMinWidth(SLIDER_WIDTH / 2);
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        var field = new TextField(format(parameter, initial));
        field.setStyle("-fx-min-width: " + VALUE_FIELD_WIDTH + "px; -fx-pref-width: " + VALUE_FIELD_WIDTH + "px; -fx-max-width: " + VALUE_FIELD_WIDTH + "px;");
        var pause = new PauseTransition(SLIDER_DEBOUNCE);
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updating) {
                return;
            }
            var value = parameter.normalize(newValue.doubleValue());
            field.setText(format(parameter, value));
            pause.setOnFinished(e -> commit(parameter, value));
            pause.playFromStart();
        });
        Runnable commitField = () -> {
            var value = parseOrNull(field.getText());
            if (value == null) {
                field.setText(format(parameter, step.value(parameter.name())));
                return;
            }
            var normalized = parameter.normalize(value);
            pause.stop();
            updating = true;
            try {
                slider.setValue(normalized);
            } finally {
                updating = false;
            }
            field.setText(format(parameter, normalized));
            commit(parameter, normalized);
        };
        field.setOnAction(e -> commitField.run());
        field.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                commitField.run();
            }
        });
        var box = new HBox(4, slider, field);
        box.setAlignment(Pos.CENTER_LEFT);
        return new RangeInput(slider, field, box);
    }

    private ChoiceInput createChoiceInput(StretchParameter parameter) {
        var choiceBox = new ChoiceBox<Integer>(FXCollections.observableArrayList(parameter.choices()));
        choiceBox.setValue((int) step.value(parameter.name()));
        choiceBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && newValue != null) {
                commit(parameter, newValue);
            }
        });
        return new ChoiceInput(choiceBox);
    }

    /**
     * Reports the values entered in the controls right away, including the ones which
     * would otherwise only be reported once the user stops moving a slider.
     */
    public void flush() {
        if (detached) {
            return;
        }
        var current = currentStep();
        if (!current.equals(step)) {
            step = current;
            onChange.accept(current);
        }
    }

    /**
     * Stops reporting changes, including the ones still pending, for example when the
     * edited step is replaced.
     */
    public void detach() {
        detached = true;
    }

    private void commit(StretchParameter parameter, double value) {
        if (detached) {
            return;
        }
        var updated = step.withValue(parameter.name(), value);
        if (!updated.equals(step)) {
            step = updated;
            onChange.accept(updated);
        }
    }

    private static Double parseOrNull(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(StretchParameter parameter, double value) {
        if (parameter.isInteger()) {
            return String.valueOf((long) Math.rint(value));
        }
        var decimals = (int) Math.ceil(-Math.log10(parameter.increment()) - 1e-9);
        return String.format(Locale.ROOT, "%." + Math.max(1, decimals) + "f", value);
    }

    private sealed interface Input permits RangeInput, ChoiceInput {
        Node node();

        double enteredValue(double fallback);

        void show(StretchParameter parameter, double value);
    }

    private record RangeInput(Slider slider, TextField field, HBox node) implements Input {
        @Override
        public double enteredValue(double fallback) {
            var typed = parseOrNull(field.getText());
            return typed != null ? typed : slider.getValue();
        }

        @Override
        public void show(StretchParameter parameter, double value) {
            slider.setValue(value);
            field.setText(format(parameter, value));
        }
    }

    private record ChoiceInput(ChoiceBox<Integer> node) implements Input {
        @Override
        public double enteredValue(double fallback) {
            var choice = node.getValue();
            return choice == null ? fallback : choice;
        }

        @Override
        public void show(StretchParameter parameter, double value) {
            node.setValue((int) value);
        }
    }
}
