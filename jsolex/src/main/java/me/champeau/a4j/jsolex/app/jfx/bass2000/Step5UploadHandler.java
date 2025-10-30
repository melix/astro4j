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

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.jfx.ExplorerSupport;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.WritableImageSupport;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.processing.util.Bass2000UploadHistoryService;

import java.text.MessageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.function.Consumer;

class Step5UploadHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step5UploadHandler.class);

    private final Step4FileNamingHandler step4Handler;
    private final ImageDataSupplier imageDataSupplier;
    private final UploadDataProvider uploadDataProvider;
    private final Scaling scaling;

    private Label step5DuplicateWarningLabel;
    private CheckBox lineCenterUploadCheckbox;
    private CheckBox wingImageUploadCheckbox;
    private Button uploadButton;
    private VBox contentPane;

    interface ImageDataSupplier {
        ImageWrapper getGeneratedBass2000Image();
        ImageWrapper getGeneratedOffBandImage();
    }

    interface UploadDataProvider {
        SpectralRay getSelectedWavelength();
        boolean maybeConfirmDuplicateUpload(double wavelengthAngstroms);
        void recordSuccessfulUploads();
        LocalDate getObservationDate();
        void exportFilesForUpload();
        void closeWizard();
    }

    Step5UploadHandler(Step4FileNamingHandler step4Handler, ImageDataSupplier imageDataSupplier, UploadDataProvider uploadDataProvider, Scaling scaling) {
        this.step4Handler = step4Handler;
        this.imageDataSupplier = imageDataSupplier;
        this.uploadDataProvider = uploadDataProvider;
        this.scaling = scaling;
    }

    @Override
    public VBox createContent() {
        var content = new VBox(12);
        content.setStyle("-fx-padding: 12;");

        var headerLabel = new Label(message("upload.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var submissionWarningLabel = new Label(message("upload.submission.warning"));
        submissionWarningLabel.setWrapText(true);
        submissionWarningLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
        submissionWarningLabel.setMinHeight(Region.USE_PREF_SIZE);
        submissionWarningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-padding: 10; -fx-background-color: #ffebee; -fx-border-color: #c62828; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");

        var fileSavedBox = createFileSavedSection();

        var uploadBox = createUploadSection();

        step5DuplicateWarningLabel = new Label();
        step5DuplicateWarningLabel.setWrapText(true);
        step5DuplicateWarningLabel.setVisible(false);
        step5DuplicateWarningLabel.setManaged(false);
        step5DuplicateWarningLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-color: orange; -fx-border-width: 1px; -fx-background-color: #fff3cd;");

        content.getChildren().addAll(headerLabel, submissionWarningLabel, fileSavedBox, step5DuplicateWarningLabel, uploadBox);

        this.contentPane = content;
        return content;
    }

    @Override
    public void load() {
        checkForDuplicateOnStep5();

        if (step4Handler.getSavedFilePath() == null) {
            exportBass2000ImageForUpload();
        }
    }

    @Override
    public void cleanup() {
        // No cleanup needed for step 5
    }

    @Override
    public boolean validate() {
        return true; // Step 5 doesn't have validation
    }

    boolean isLineCenterUploadSelected() {
        return lineCenterUploadCheckbox != null && lineCenterUploadCheckbox.isSelected();
    }

    boolean isWingImageUploadSelected() {
        return wingImageUploadCheckbox != null && wingImageUploadCheckbox.isSelected();
    }

    void setUploadButtonEnabled(boolean enabled) {
        uploadButton.setDisable(!enabled);
    }

    private VBox createFileSavedSection() {
        var section = new VBox(6);

        var savedFilePath = step4Handler.getSavedFilePath();
        if (savedFilePath != null) {
            var filesContainer = new VBox(3);

            var lineCenterRow = new HBox(8);
            lineCenterRow.setStyle("-fx-alignment: center-left;");
            var lineCenterLabel = new Label(message("linecenter.file"));
            lineCenterLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            lineCenterLabel.setMinWidth(80);
            var lineCenterFilePathLink = new Hyperlink(savedFilePath.getName());
            lineCenterFilePathLink.setStyle("-fx-font-size: 12px;");
            lineCenterFilePathLink.setOnAction(e -> ExplorerSupport.openInExplorer(savedFilePath.toPath()));
            lineCenterRow.getChildren().addAll(lineCenterLabel, lineCenterFilePathLink);
            filesContainer.getChildren().add(lineCenterRow);

            var savedOffBandFilePath = step4Handler.getSavedOffBandFilePath();
            if (savedOffBandFilePath != null) {
                var offBandRow = new HBox(8);
                offBandRow.setStyle("-fx-alignment: center-left;");
                var offBandLabel = new Label(message("offband.file"));
                offBandLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                offBandLabel.setMinWidth(80);
                var offBandFilePathLink = new Hyperlink(savedOffBandFilePath.getName());
                offBandFilePathLink.setStyle("-fx-font-size: 12px;");
                offBandFilePathLink.setOnAction(e -> ExplorerSupport.openInExplorer(savedOffBandFilePath.toPath()));
                offBandRow.getChildren().addAll(offBandLabel, offBandFilePathLink);
                filesContainer.getChildren().add(offBandRow);
            }

            section.getChildren().add(filesContainer);
        } else {
            var savingLabel = new Label(message("saving.file"));
            section.getChildren().add(savingLabel);
        }

        return section;
    }

    private VBox createUploadSection() {
        var section = new VBox(6);
        section.setStyle("-fx-border-color: #2196F3; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 10;");

        var sectionHeader = new Label(message("image.preview.upload"));
        sectionHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2196F3;");

        var imagesContainer = new HBox(20);
        imagesContainer.setStyle("-fx-alignment: center;");
        VBox.setVgrow(imagesContainer, Priority.ALWAYS);

        var lineCenterContainer = createImagePreviewContainer(message("upload.line.center"), imageDataSupplier.getGeneratedBass2000Image());
        lineCenterUploadCheckbox = getCheckboxFromContainer(lineCenterContainer);
        imagesContainer.getChildren().add(lineCenterContainer);

        var generatedOffBandImage = imageDataSupplier.getGeneratedOffBandImage();
        if (generatedOffBandImage != null) {
            var wingContainer = createImagePreviewContainer(message("upload.wing.image"), generatedOffBandImage);
            wingImageUploadCheckbox = getCheckboxFromContainer(wingContainer);
            imagesContainer.getChildren().add(wingContainer);
        }

        var uploadInfoLabel = new Label(message("upload.upload.info"));
        uploadInfoLabel.setWrapText(true);

        uploadButton = new Button(message("upload.button.upload"));
        uploadButton.getStyleClass().add("primary-button");
        uploadButton.setPrefWidth(200);
        uploadButton.setOnAction(e -> uploadToBass2000());
        uploadButton.setDisable(true);

        var uploadButtonContainer = new HBox();
        uploadButtonContainer.setStyle("-fx-alignment: center;");
        uploadButtonContainer.getChildren().add(uploadButton);

        var uploadStatusLabel = new Label("");
        uploadStatusLabel.setId("uploadStatusLabel");
        uploadStatusLabel.setStyle("-fx-font-weight: bold; -fx-alignment: center;");
        uploadStatusLabel.setVisible(false);
        uploadStatusLabel.setManaged(false);

        var uploadProgressBar = new ProgressBar();
        uploadProgressBar.setId("uploadProgressBar");
        uploadProgressBar.setPrefWidth(300);
        uploadProgressBar.setVisible(false);
        uploadProgressBar.setManaged(false);

        var uploadProgressContainer = new HBox();
        uploadProgressContainer.setStyle("-fx-alignment: center;");
        uploadProgressContainer.getChildren().add(uploadProgressBar);

        section.getChildren().addAll(sectionHeader, imagesContainer, uploadInfoLabel, uploadButtonContainer, uploadProgressContainer, uploadStatusLabel);
        return section;
    }

    private VBox createImagePreviewContainer(String title, ImageWrapper imageWrapper) {
        var container = new VBox(8);
        container.setStyle("-fx-alignment: center;");

        var checkbox = new CheckBox(title);
        checkbox.getStyleClass().add("check-box");
        checkbox.setSelected(true);

        var imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        VBox.setVgrow(imageView, Priority.ALWAYS);
        imageView.setFitWidth(300);
        imageView.setFitHeight(300);

        BackgroundOperations.async(() -> {
            try {
                var displayImage = imageWrapper.copy();
                displayImage = scaling.rescaleToRadius(displayImage, 150, 300, 400);
                LinearStrechingStrategy.DEFAULT.stretch(displayImage);
                if (displayImage instanceof ImageWrapper32 mono) {
                    displayImage = RGBImage.toRGB(mono);
                }
                var writableImage = WritableImageSupport.asWritable(displayImage);
                Platform.runLater(() -> imageView.setImage(writableImage));
            } catch (Exception e) {
                LOGGER.error("Failed to create preview image", e);
            }
        });

        container.getChildren().addAll(imageView, checkbox);
        return container;
    }

    private CheckBox getCheckboxFromContainer(VBox container) {
        return container.getChildren().stream()
                .filter(node -> node instanceof CheckBox)
                .map(node -> (CheckBox) node)
                .findFirst()
                .orElse(null);
    }

    private void checkForDuplicateOnStep5() {
        BackgroundOperations.async(() -> {
            var selectedWavelength = uploadDataProvider.getSelectedWavelength();
            if (selectedWavelength != null) {
                var duplicate = Bass2000UploadHistoryService.getInstance().checkForDuplicateUpload(
                    uploadDataProvider.getObservationDate(),
                    selectedWavelength.wavelength().angstroms()
                );
                Platform.runLater(() -> updateStep5DuplicateWarning(duplicate.orElse(null)));
            }
        });
    }

    private void exportBass2000ImageForUpload() {
        uploadDataProvider.exportFilesForUpload();
    }

    private void updateStep5DuplicateWarning(Bass2000UploadHistoryService.UploadRecord duplicate) {
        if (duplicate != null) {
            step5DuplicateWarningLabel.setText(MessageFormat.format(message("duplicate.warning"), duplicate.sourceFilename()));
            step5DuplicateWarningLabel.setVisible(true);
            step5DuplicateWarningLabel.setManaged(true);
        } else {
            step5DuplicateWarningLabel.setVisible(false);
            step5DuplicateWarningLabel.setManaged(false);
        }
    }

    private void uploadToBass2000() {
        var uploadLineCenter = lineCenterUploadCheckbox != null && lineCenterUploadCheckbox.isSelected();
        var uploadWingImage = wingImageUploadCheckbox != null && wingImageUploadCheckbox.isSelected();

        if (!uploadLineCenter && !uploadWingImage) {
            Platform.runLater(() -> {
                var alert = AlertFactory.error("Please select at least one image to upload.");
                alert.setTitle("No Images Selected");
                alert.setHeaderText("Upload Error");
                alert.showAndWait();
            });
            return;
        }

        var selectedWavelength = uploadDataProvider.getSelectedWavelength();
        if (selectedWavelength != null) {
            if (!uploadDataProvider.maybeConfirmDuplicateUpload(selectedWavelength.wavelength().angstroms())) {
                return;
            }
        }

        if (uploadLineCenter && step4Handler.getSavedFilePath() == null) {
            Platform.runLater(() -> {
                var alert = AlertFactory.error(message("upload.error.no.file.message"));
                alert.setTitle(message("upload.error.no.file.title"));
                alert.setHeaderText(message("upload.error.no.file.header"));
                alert.showAndWait();
            });
            return;
        }

        if (uploadWingImage && step4Handler.getSavedOffBandFilePath() == null) {
            Platform.runLater(() -> {
                var alert = AlertFactory.error("Wing image file not available for upload.");
                alert.setTitle("File Not Available");
                alert.setHeaderText("Upload Error");
                alert.showAndWait();
            });
            return;
        }

        BackgroundOperations.async(() -> {
            try {
                var lineCenterSize = uploadLineCenter && step4Handler.getSavedFilePath() != null ? step4Handler.getSavedFilePath().length() : 0;
                var wingSize = uploadWingImage && step4Handler.getSavedOffBandFilePath() != null ? step4Handler.getSavedOffBandFilePath().length() : 0;
                final var totalBytes = lineCenterSize + wingSize;

                var bytesUploaded = 0L;

                if (uploadLineCenter && step4Handler.getSavedFilePath() != null) {
                    Platform.runLater(() -> {
                        updateUploadStatus(message("upload.uploading") + " (line center)");
                    });

                    FTPUploader.uploadFileToFTP(step4Handler.getSavedFilePath(), totalBytes, bytesUploaded,
                        progress -> Platform.runLater(() -> updateUploadProgress(progress)));
                    bytesUploaded += step4Handler.getSavedFilePath().length();
                }

                if (uploadWingImage && step4Handler.getSavedOffBandFilePath() != null) {
                    Platform.runLater(() -> {
                        updateUploadStatus(message("upload.uploading") + " (wing image)");
                    });

                    FTPUploader.uploadFileToFTP(step4Handler.getSavedOffBandFilePath(), totalBytes, bytesUploaded,
                        progress -> Platform.runLater(() -> updateUploadProgress(progress)));
                }

                Platform.runLater(() -> {
                    updateUploadStatus(message("upload.success"));
                    hideUploadProgress();
                    uploadDataProvider.recordSuccessfulUploads();

                    var alert = AlertFactory.info();
                    alert.setTitle(message("upload.success.title"));
                    alert.setHeaderText(message("upload.success.header"));
                    alert.setContentText(message("upload.success.message"));
                    alert.showAndWait();

                    uploadDataProvider.closeWizard();
                });
            } catch (Exception e) {
                LOGGER.error("Failed to upload file to BASS2000", e);
                Platform.runLater(() -> {
                    updateUploadStatus(message("upload.failed"));
                    hideUploadProgress();
                    var alert = AlertFactory.error(MessageFormat.format(
                            message("upload.error.message"),
                            e.getMessage()
                    ));
                    alert.setTitle(message("upload.error.title"));
                    alert.setHeaderText(message("upload.error.header"));
                    alert.showAndWait();
                });
            }
        });
    }

    private void updateUploadStatus(String status) {
        var uploadLabel = contentPane.lookup("#uploadStatusLabel");
        if (uploadLabel instanceof Label label) {
            label.setText(status);
            label.setVisible(true);
            label.setManaged(true);
        }
    }

    private void updateUploadProgress(double progress) {
        var uploadProgressBar = contentPane.lookup("#uploadProgressBar");
        if (uploadProgressBar instanceof ProgressBar progressBar) {
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(progress);
            progressBar.setStyle("-fx-accent: #4CAF50;");
        }
    }

    private void hideUploadProgress() {
        var uploadProgressBar = contentPane.lookup("#uploadProgressBar");
        if (uploadProgressBar instanceof ProgressBar progressBar) {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
        }
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }
}