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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.Optional;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * Dialog for configuring spectroheliographic reconstruction sampling options.
 */
public class SamplingOptionsDialog {
    private final ComboBox<Integer> wavelengthCombo;
    private final ComboBox<Integer> spatialCombo;
    private final Label memoryLabel;
    private final Label warningLabel;
    private Button okButton;

    /**
     * Sampling options for spectroheliographic reconstruction.
     *
     * @param wavelengthResolution the wavelength resolution
     * @param spatialResolution the spatial resolution
     */
    public record SamplingOptions(int wavelengthResolution, int spatialResolution) {}

    /**
     * Creates a new sampling options dialog.
     */
    public SamplingOptionsDialog() {
        wavelengthCombo = new ComboBox<>();
        wavelengthCombo.getItems().addAll(32, 64, 96, 128, 256);
        wavelengthCombo.setValue(96);

        spatialCombo = new ComboBox<>();
        spatialCombo.getItems().addAll(64, 128, 256, 512, 1024, 1536, 2048, 3072, 4096);
        spatialCombo.setValue(512);

        memoryLabel = new Label();
        memoryLabel.setStyle("-fx-font-size: 12px;");

        warningLabel = new Label();
        warningLabel.setStyle("-fx-text-fill: #cc0000; -fx-font-weight: bold;");
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);
    }

    /**
     * Shows the dialog and waits for user input.
     *
     * @param owner the owner stage
     * @return the selected sampling options, or empty if cancelled
     */
    public Optional<SamplingOptions> showAndWait(Stage owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18N.string(JSolEx.class, "sampling-options", "title"));

        var root = new BorderPane();
        root.setStyle("-fx-background-color: #f8f9fa;");

        var headerLabel = new Label(I18N.string(JSolEx.class, "sampling-options", "header"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #212529;");

        var descriptionLabel = new Label(I18N.string(JSolEx.class, "sampling-options", "description"));
        descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(400);

        var headerBox = new VBox(8, headerLabel, descriptionLabel);
        headerBox.setPadding(new Insets(15, 20, 15, 20));
        headerBox.setStyle("-fx-background-color: white;");
        root.setTop(headerBox);

        var grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: white;");

        var wavelengthLabel = new Label(I18N.string(JSolEx.class, "sampling-options", "wavelength.resolution"));
        var spatialLabel = new Label(I18N.string(JSolEx.class, "sampling-options", "spatial.resolution"));

        grid.add(wavelengthLabel, 0, 0);
        grid.add(wavelengthCombo, 1, 0);

        grid.add(spatialLabel, 0, 1);
        grid.add(spatialCombo, 1, 1);

        var memoryBox = new HBox(10);
        memoryBox.setAlignment(Pos.CENTER_LEFT);
        var memoryTitleLabel = new Label(I18N.string(JSolEx.class, "sampling-options", "estimated.memory"));
        memoryTitleLabel.setStyle("-fx-font-weight: bold;");
        memoryBox.getChildren().addAll(memoryTitleLabel, memoryLabel);
        grid.add(memoryBox, 0, 3, 2, 1);

        grid.add(warningLabel, 0, 4, 2, 1);

        root.setCenter(grid);

        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        var cancelButton = new Button(I18N.string(JSolEx.class, "sampling-options", "cancel"));
        cancelButton.getStyleClass().add("default-button");
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);

        okButton = new Button(I18N.string(JSolEx.class, "sampling-options", "ok"));
        okButton.getStyleClass().add("primary-button");
        okButton.setDefaultButton(true);
        ButtonBar.setButtonData(okButton, ButtonBar.ButtonData.OK_DONE);

        buttonBar.getButtons().addAll(cancelButton, okButton);
        root.setBottom(buttonBar);

        final SamplingOptions[] result = {null};

        cancelButton.setOnAction(e -> stage.close());
        okButton.setOnAction(e -> {
            result[0] = new SamplingOptions(wavelengthCombo.getValue(), spatialCombo.getValue());
            stage.close();
        });

        wavelengthCombo.setOnAction(e -> updateMemoryEstimate());
        spatialCombo.setOnAction(e -> updateMemoryEstimate());

        updateMemoryEstimate();

        var scene = newScene(root);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.sizeToScene();

        stage.showAndWait();

        return Optional.ofNullable(result[0]);
    }

    /**
     * Updates the memory usage estimate based on current selections.
     */
    private void updateMemoryEstimate() {
        int wavelength = wavelengthCombo.getValue();
        int spatial = spatialCombo.getValue();

        long memoryBytes = estimateMemory(wavelength, spatial);
        long availableMemory = getAvailableMemory();
        memoryLabel.setText(formatMemory(memoryBytes));

        boolean tooMuch = memoryBytes > availableMemory;
        if (tooMuch) {
            memoryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cc0000;");
            warningLabel.setText(I18N.string(JSolEx.class, "sampling-options", "memory.warning"));
            warningLabel.setVisible(true);
            warningLabel.setManaged(true);
        } else {
            memoryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #006600;");
            warningLabel.setVisible(false);
            warningLabel.setManaged(false);
        }
        if (okButton != null) {
            okButton.setDisable(tooMuch);
        }
    }

    /**
     * Returns the available memory for reconstruction.
     *
     * @return the available memory in bytes
     */
    private static long getAvailableMemory() {
        var runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return maxMemory - totalMemory + freeMemory;
    }

    /**
     * Estimates the memory required for reconstruction.
     *
     * @param wavelengthResolution the wavelength resolution
     * @param spatialResolution the spatial resolution
     * @return the estimated memory usage in bytes
     */
    private static long estimateMemory(int wavelengthResolution, int spatialResolution) {
        long infrastructureCost = 2L * 1024 * 1024 * 1024;
        long slitCount = spatialResolution;
        long frameCount = spatialResolution;
        long wavelengthCount = wavelengthResolution;
        long floatSize = 4;
        long dataSize = slitCount * frameCount * wavelengthCount * floatSize;
        return infrastructureCost + dataSize * 2;
    }

    /**
     * Formats a byte count as a human-readable memory size.
     *
     * @param bytes the number of bytes
     * @return the formatted memory string
     */
    private static String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
