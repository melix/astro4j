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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.listeners.DifferentialRotationConfig;
import me.champeau.a4j.jsolex.app.listeners.NoiseReductionMethod;
import me.champeau.a4j.jsolex.app.listeners.SampleRejectionMethod;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

public class DifferentialRotationConfigDialog {

    private final Spinner<Double> limbLongitudeSpinner;
    private final Spinner<Double> longitudeHalfRangeSpinner;
    private final Spinner<Double> longitudeStepSpinner;
    private final Spinner<Double> latitudeStepSpinner;
    private final Spinner<Double> smoothingWindowSpinner;
    private final Spinner<Double> voigtFitHalfWidthSpinner;
    private final CheckBox sigmaRejectionCheckBox;
    private final Spinner<Double> sigmaValueSpinner;
    private final ComboBox<NoiseReductionMethod> noiseReductionCombo;
    private final Label noiseDescLabel;
    private final Stage stage;
    private final AtomicReference<DifferentialRotationConfig> result = new AtomicReference<>();

    public DifferentialRotationConfigDialog(Window owner, DifferentialRotationConfig currentConfig) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(message("differential.rotation.config.title"));

        var root = new BorderPane();

        // Create spinners with current values
        limbLongitudeSpinner = createDoubleSpinner(10, 85, currentConfig.limbLongitudeDeg(), 5);

        var maxHalfRange = DifferentialRotationConfig.maxHalfRangeFor(currentConfig.limbLongitudeDeg());
        longitudeHalfRangeSpinner = createDoubleSpinner(1, maxHalfRange, Math.min(currentConfig.longitudeHalfRangeDeg(), maxHalfRange), 5);

        // Update max half-range when limb longitude changes
        limbLongitudeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            var newMaxHalfRange = DifferentialRotationConfig.maxHalfRangeFor(newVal);
            var factory = (SpinnerValueFactory.DoubleSpinnerValueFactory) longitudeHalfRangeSpinner.getValueFactory();
            factory.setMax(newMaxHalfRange);
            if (longitudeHalfRangeSpinner.getValue() > newMaxHalfRange) {
                longitudeHalfRangeSpinner.getValueFactory().setValue(newMaxHalfRange);
            }
        });

        longitudeStepSpinner = createDoubleSpinner(0.5, 10, currentConfig.longitudeStepDeg(), 0.5);
        latitudeStepSpinner = createDoubleSpinner(0.5, 10, currentConfig.latitudeStepDeg(), 0.5);
        smoothingWindowSpinner = createDoubleSpinner(1, 20, currentConfig.smoothingWindowDeg(), 1);
        voigtFitHalfWidthSpinner = createDoubleSpinner(0.5, 5, currentConfig.voigtFitHalfWidthAngstroms(), 0.5);

        // Sample rejection
        var isSigmaRejection = currentConfig.sampleRejectionMethod() instanceof SampleRejectionMethod.Sigma;
        var sigmaValue = isSigmaRejection ? ((SampleRejectionMethod.Sigma) currentConfig.sampleRejectionMethod()).threshold() : 3.0;

        sigmaRejectionCheckBox = new CheckBox(message("differential.rotation.config.sigma.rejection"));
        sigmaRejectionCheckBox.setSelected(isSigmaRejection);

        sigmaValueSpinner = createDoubleSpinner(1.5, 5, sigmaValue, 0.5);
        sigmaValueSpinner.setDisable(!isSigmaRejection);

        sigmaRejectionCheckBox.selectedProperty().addListener((obs, oldVal, newVal) ->
            sigmaValueSpinner.setDisable(!newVal));

        // Noise reduction method
        noiseReductionCombo = new ComboBox<>();
        noiseReductionCombo.getItems().addAll(NoiseReductionMethod.values());
        noiseReductionCombo.setValue(currentConfig.noiseReductionMethod());
        noiseReductionCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(NoiseReductionMethod method) {
                if (method == null) {
                    return "";
                }
                return switch (method) {
                    case MEDIAN -> message("differential.rotation.config.noise.median");
                    case AVERAGE -> message("differential.rotation.config.noise.average");
                    case WEIGHTED_AVERAGE -> message("differential.rotation.config.noise.weighted");
                };
            }

            @Override
            public NoiseReductionMethod fromString(String string) {
                return null;
            }
        });

        // Description for noise reduction methods
        noiseDescLabel = new Label();
        noiseDescLabel.setWrapText(true);
        noiseDescLabel.getStyleClass().add("help-text");
        noiseDescLabel.setMaxWidth(400);
        updateNoiseDescription(noiseReductionCombo.getValue());
        noiseReductionCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateNoiseDescription(newVal));

        // Build content
        var content = new VBox(8);
        content.setPadding(new Insets(10));

        // Measurement geometry section
        var geometryLabel = new Label(message("differential.rotation.config.geometry.section"));
        geometryLabel.getStyleClass().add("section-header");

        var geometryGrid = new GridPane();
        geometryGrid.setHgap(10);
        geometryGrid.setVgap(5);
        geometryGrid.setPadding(new Insets(2, 0, 2, 10));

        addRow(geometryGrid, 0, message("differential.rotation.config.limb.longitude"), limbLongitudeSpinner, "°", message("differential.rotation.config.limb.longitude.tooltip"));
        addRow(geometryGrid, 1, message("differential.rotation.config.longitude.halfrange"), longitudeHalfRangeSpinner, "°", message("differential.rotation.config.longitude.halfrange.tooltip"));
        addRow(geometryGrid, 2, message("differential.rotation.config.longitude.step"), longitudeStepSpinner, "°", message("differential.rotation.config.longitude.step.tooltip"));
        addRow(geometryGrid, 3, message("differential.rotation.config.latitude.step"), latitudeStepSpinner, "°", message("differential.rotation.config.latitude.step.tooltip"));
        addRow(geometryGrid, 4, message("differential.rotation.config.voigt.halfwidth"), voigtFitHalfWidthSpinner, "Å", message("differential.rotation.config.voigt.halfwidth.tooltip"));

        // Noise reduction section
        var noiseLabel = new Label(message("differential.rotation.config.noise.section"));
        noiseLabel.getStyleClass().add("section-header");

        var noiseGrid = new GridPane();
        noiseGrid.setHgap(10);
        noiseGrid.setVgap(5);
        noiseGrid.setPadding(new Insets(2, 0, 2, 10));

        addRow(noiseGrid, 0, message("differential.rotation.config.smoothing"), smoothingWindowSpinner, "°", message("differential.rotation.config.smoothing.tooltip"));

        var sigmaBox = new HBox(10, sigmaRejectionCheckBox, sigmaValueSpinner, new Label("σ"));
        sigmaBox.setAlignment(Pos.CENTER_LEFT);
        addRowWithHelp(noiseGrid, 1, sigmaBox, message("differential.rotation.config.sigma.rejection.tooltip"));

        addRow(noiseGrid, 2, message("differential.rotation.config.aggregation"), noiseReductionCombo, null, message("differential.rotation.config.noise.tooltip"));

        content.getChildren().addAll(
            geometryLabel,
            geometryGrid,
            new Separator(),
            noiseLabel,
            noiseGrid,
            noiseDescLabel
        );

        root.setCenter(content);

        // Button bar with Reset, Cancel, OK
        var resetButton = new Button(message("reset"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> resetToDefaults());
        ButtonBar.setButtonData(resetButton, ButtonBar.ButtonData.LEFT);

        var cancelButton = new Button(message("cancel"));
        cancelButton.getStyleClass().add("default-button");
        cancelButton.setOnAction(e -> stage.close());
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);

        var okButton = new Button("OK");
        okButton.getStyleClass().add("primary-button");
        okButton.setDefaultButton(true);
        okButton.setOnAction(e -> {
            var config = buildConfig();
            var error = config.validate();
            if (error != null) {
                var alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(message("error"));
                alert.setHeaderText(null);
                alert.setContentText(error);
                alert.showAndWait();
                return;
            }
            var warning = config.warning();
            if (warning != null) {
                var alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(message("warning"));
                alert.setHeaderText(null);
                alert.setContentText(warning);
                alert.getButtonTypes().setAll(
                    ButtonType.OK,
                    ButtonType.CANCEL
                );
                var choice = alert.showAndWait();
                if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                    return;
                }
            }
            result.set(config);
            stage.close();
        });
        ButtonBar.setButtonData(okButton, ButtonBar.ButtonData.OK_DONE);

        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(10, 15, 10, 15));
        buttonBar.getButtons().addAll(resetButton, cancelButton, okButton);
        root.setBottom(buttonBar);

        var scene = newScene(root);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setMinWidth(450);
        stage.setResizable(false);
    }

    private void updateNoiseDescription(NoiseReductionMethod method) {
        if (method == null) {
            noiseDescLabel.setText("");
            return;
        }
        noiseDescLabel.setText(switch (method) {
            case MEDIAN -> message("differential.rotation.config.noise.median.desc");
            case AVERAGE -> message("differential.rotation.config.noise.average.desc");
            case WEIGHTED_AVERAGE -> message("differential.rotation.config.noise.weighted.desc");
        });
    }

    private void resetToDefaults() {
        var defaults = DifferentialRotationConfig.defaultConfig();
        limbLongitudeSpinner.getValueFactory().setValue(defaults.limbLongitudeDeg());
        longitudeHalfRangeSpinner.getValueFactory().setValue(defaults.longitudeHalfRangeDeg());
        longitudeStepSpinner.getValueFactory().setValue(defaults.longitudeStepDeg());
        latitudeStepSpinner.getValueFactory().setValue(defaults.latitudeStepDeg());
        smoothingWindowSpinner.getValueFactory().setValue(defaults.smoothingWindowDeg());
        voigtFitHalfWidthSpinner.getValueFactory().setValue(defaults.voigtFitHalfWidthAngstroms());
        sigmaRejectionCheckBox.setSelected(defaults.sampleRejectionMethod() instanceof SampleRejectionMethod.Sigma);
        noiseReductionCombo.setValue(defaults.noiseReductionMethod());
    }

    private DifferentialRotationConfig buildConfig() {
        SampleRejectionMethod rejectionMethod = sigmaRejectionCheckBox.isSelected()
            ? new SampleRejectionMethod.Sigma(sigmaValueSpinner.getValue())
            : new SampleRejectionMethod.None();

        return new DifferentialRotationConfig(
            limbLongitudeSpinner.getValue(),
            longitudeHalfRangeSpinner.getValue(),
            longitudeStepSpinner.getValue(),
            latitudeStepSpinner.getValue(),
            smoothingWindowSpinner.getValue(),
            voigtFitHalfWidthSpinner.getValue(),
            rejectionMethod,
            noiseReductionCombo.getValue()
        );
    }

    private static Spinner<Double> createDoubleSpinner(double min, double max, double initial, double step) {
        var spinner = new Spinner<Double>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
        spinner.setEditable(true);
        spinner.setPrefWidth(90);
        return spinner;
    }

    private static void addRow(GridPane grid, int row, String labelText, Node control, String unit, String tooltipText) {
        var label = new Label(labelText);
        label.getStyleClass().add("field-label");
        grid.add(label, 0, row);
        grid.add(control, 1, row);
        if (unit != null) {
            grid.add(new Label(unit), 2, row);
        }
        if (tooltipText != null) {
            var helpIcon = new Label("?");
            helpIcon.getStyleClass().add("help-icon");
            var customTooltip = new CustomTooltip(tooltipText);
            customTooltip.attachTo(helpIcon);
            grid.add(helpIcon, 3, row);
        }
    }

    private static void addRowWithHelp(GridPane grid, int row, Node control, String tooltipText) {
        grid.add(control, 0, row, 3, 1);
        if (tooltipText != null) {
            var helpIcon = new Label("?");
            helpIcon.getStyleClass().add("help-icon");
            var customTooltip = new CustomTooltip(tooltipText);
            customTooltip.attachTo(helpIcon);
            grid.add(helpIcon, 3, row);
        }
    }

    public Optional<DifferentialRotationConfig> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(result.get());
    }

    public static Optional<DifferentialRotationConfig> show(Window owner, DifferentialRotationConfig currentConfig) {
        var dialog = new DifferentialRotationConfigDialog(owner, currentConfig);
        return dialog.showAndWait();
    }
}
