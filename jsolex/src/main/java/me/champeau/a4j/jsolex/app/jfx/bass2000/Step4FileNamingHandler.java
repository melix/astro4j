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

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

class Step4FileNamingHandler implements StepHandler {
    private final FileNameGenerator fileNameGenerator;
    private final ImageDataSupplier imageDataSupplier;
    private final Consumer<Boolean> nextButtonDisabler;

    private final TextField lineCenterFilenameField;
    private final TextField offBandFilenameField;
    private File savedFilePath;
    private File savedOffBandFilePath;

    interface ImageDataSupplier {
        ImageWrapper getGeneratedBass2000Image();
        ImageWrapper getGeneratedOffBandImage();
        String getGeneratedFilename();
    }

    Step4FileNamingHandler(FileNameGenerator fileNameGenerator,
                          ImageDataSupplier imageDataSupplier,
                          Consumer<Boolean> nextButtonDisabler) {
        this.fileNameGenerator = fileNameGenerator;
        this.imageDataSupplier = imageDataSupplier;
        this.nextButtonDisabler = nextButtonDisabler;

        this.lineCenterFilenameField = new TextField();
        this.offBandFilenameField = new TextField();
    }

    @Override
    public VBox createContent() {
        var content = new VBox(12);
        content.setStyle("-fx-padding: 12;");

        var headerLabel = new Label(message("filename.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var instructionLabel = new Label(message("filename.instruction"));
        instructionLabel.setWrapText(true);
        instructionLabel.setStyle("-fx-padding: 0 0 10 0;");

        var warningLabel = new Label(message("filename.warning"));
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold; -fx-padding: 10; -fx-background-color: #fff3cd; -fx-border-color: #ffc107; -fx-border-radius: 5;");

        var lineCenterContainer = new VBox(5);
        var lineCenterLabel = new Label(message("filename.linecenter.label"));
        lineCenterLabel.setStyle("-fx-font-weight: bold;");

        lineCenterFilenameField.getStyleClass().add("text-field");
        lineCenterFilenameField.setPromptText(message("filename.linecenter.prompt"));
        lineCenterFilenameField.setPrefWidth(600);

        lineCenterFilenameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!oldValue.equals(newValue)) {
                savedFilePath = null;
            }
        });

        var lineCenterDescription = new Label(message("filename.linecenter.description"));
        lineCenterDescription.setWrapText(true);
        lineCenterDescription.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        lineCenterContainer.getChildren().addAll(lineCenterLabel, lineCenterFilenameField, lineCenterDescription);

        var offBandContainer = new VBox(5);
        var offBandLabel = new Label(message("filename.offband.label"));
        offBandLabel.setStyle("-fx-font-weight: bold;");

        offBandFilenameField.getStyleClass().add("text-field");
        offBandFilenameField.setPromptText(message("filename.offband.prompt"));
        offBandFilenameField.setPrefWidth(600);

        offBandFilenameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!oldValue.equals(newValue)) {
                savedOffBandFilePath = null;
            }
        });

        var offBandDescription = new Label(message("filename.offband.description"));
        offBandDescription.setWrapText(true);
        offBandDescription.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        offBandContainer.getChildren().addAll(offBandLabel, offBandFilenameField, offBandDescription);

        content.getChildren().addAll(headerLabel, instructionLabel, warningLabel, lineCenterContainer, offBandContainer);
        return content;
    }

    @Override
    public void load() {
        var generatedFilename = imageDataSupplier.getGeneratedFilename();

        if (generatedFilename != null) {
            lineCenterFilenameField.setText(generatedFilename);
        }

        if (generatedFilename != null) {
            try {
                var maybeOffBandFileName = generateOffBandFileName();
                if (maybeOffBandFileName.isEmpty()) {
                    // Note: In the original, it would loadStep3() which we can't do here
                    // The controller should handle this error case if needed
                    AlertFactory.error(message("filename.error")).showAndWait();
                    return;
                }
                var offBandFilename = maybeOffBandFileName.get();
                offBandFilenameField.setText(offBandFilename);
                nextButtonDisabler.accept(false);
            } catch (Exception e) {
                offBandFilenameField.setText(generatedFilename);
            }
        }
    }

    @Override
    public void cleanup() {
        // No cleanup needed for step 4
    }

    @Override
    public boolean validate() {
        return true; // Step 4 doesn't have validation
    }

    private Optional<String> generateOffBandFileName() {
        var generatedOffBandImage = imageDataSupplier.getGeneratedOffBandImage();
        if (generatedOffBandImage != null) {
            return fileNameGenerator.generateOffBandFileName(generatedOffBandImage);
        }
        return Optional.empty();
    }

    public String getLineCenterFilename() {
        return lineCenterFilenameField.getText().trim();
    }

    public String getOffBandFilename() {
        return offBandFilenameField.getText().trim();
    }

    public File getSavedFilePath() {
        return savedFilePath;
    }

    public void setSavedFilePath(File savedFilePath) {
        this.savedFilePath = savedFilePath;
    }

    public File getSavedOffBandFilePath() {
        return savedOffBandFilePath;
    }

    public void setSavedOffBandFilePath(File savedOffBandFilePath) {
        this.savedOffBandFilePath = savedOffBandFilePath;
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }
}