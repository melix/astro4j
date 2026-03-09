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
package me.champeau.a4j.jsolex.app.jfx.spectrosolhub;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import com.google.gson.Gson;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SpectroSolHubClient;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.ImageMetadata;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.SpectroSolHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Supplier;

public class SpectroSolHubSubmissionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectroSolHubSubmissionController.class);

    public SpectroSolHubSubmissionController() {
    }

    @FXML
    private ProgressBar wizardProgress;

    @FXML
    private Label stepLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private Button previousButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button cancelButton;

    private Stage stage;
    private int currentStep = 1;
    private static final int TOTAL_STEPS = 4;

    private final Gson gson = new Gson();
    private Step1AuthenticationHandler step1Handler;
    private Step2ImageSelectionHandler step2Handler;
    private Step3OrientationHandler step3Handler;
    private Step4SessionMetadataHandler step4Handler;

    public void setup(Stage stage,
                      ProcessParams processParams,
                      SpectralRay detectedSpectralRay,
                      Supplier<List<MultipleImagesViewer.ImageInfo>> availableImagesSupplier) {
        this.stage = stage;

        this.step1Handler = new Step1AuthenticationHandler(authenticated ->
                Platform.runLater(() -> {
                    if (currentStep == 1) {
                        nextButton.setDisable(!authenticated);
                        if (authenticated) {
                            onNext();
                        }
                    }
                })
        );
        this.step2Handler = new Step2ImageSelectionHandler(availableImagesSupplier, detectedSpectralRay);
        this.step3Handler = new Step3OrientationHandler(processParams, availableImagesSupplier);
        this.step4Handler = new Step4SessionMetadataHandler(processParams, detectedSpectralRay, this.step2Handler, this.step1Handler);

        Platform.runLater(this::initializeStyles);
        initializeWizard();
    }

    private void initializeStyles() {
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
        }
    }

    private void initializeWizard() {
        updateStepIndicator();
        loadCurrentStep();
        updateButtons();
    }

    private void updateStepIndicator() {
        var progress = (double) currentStep / TOTAL_STEPS;
        wizardProgress.setProgress(progress);
        var stepLabelPattern = message("step.label");
        stepLabel.setText(MessageFormat.format(stepLabelPattern, currentStep, TOTAL_STEPS));
    }

    @FXML
    private void onPrevious() {
        if (currentStep > 1) {
            currentStep--;
            updateStepIndicator();
            loadCurrentStep();
            updateButtons();
        }
    }

    @FXML
    private void onNext() {
        var handler = getCurrentStepHandler();
        if (handler != null && !handler.validate()) {
            return;
        }
        if (currentStep < TOTAL_STEPS) {
            currentStep++;
            updateStepIndicator();
            loadCurrentStep();
            updateButtons();
        } else {
            performUpload();
        }
    }

    @FXML
    private void onCancel() {
        stage.close();
    }

    private void updateButtons() {
        previousButton.setDisable(currentStep == 1);
        if (currentStep == TOTAL_STEPS) {
            nextButton.setText(message("button.submit"));
        } else {
            nextButton.setText(message("button.next"));
        }
    }

    private void loadCurrentStep() {
        var handler = getCurrentStepHandler();
        if (handler != null) {
            var content = handler.createContent();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(content);
            if (handler == step1Handler) {
                nextButton.setDisable(!step1Handler.isAuthenticated());
            }
            handler.load();
        }
        updateButtons();
    }

    private StepHandler getCurrentStepHandler() {
        return switch (currentStep) {
            case 1 -> step1Handler;
            case 2 -> step2Handler;
            case 3 -> step3Handler;
            case 4 -> step4Handler;
            default -> null;
        };
    }

    private void performUpload() {
        nextButton.setDisable(true);
        previousButton.setDisable(true);
        cancelButton.setDisable(true);
        nextButton.setText(message("upload.in.progress"));

        BackgroundOperations.async(() -> {
            try {
                var config = Configuration.getInstance();
                var url = config.getSpectroSolHubUrl();
                var token = config.getSpectroSolHubToken().orElseThrow();
                var client = new SpectroSolHubClient(url, token);

                Platform.runLater(() -> wizardProgress.setProgress(-1));

                var sessionRequest = step4Handler.buildSessionRequest();
                var session = client.createSession(sessionRequest);
                var sessionId = session.id();

                var images = step2Handler.getSelectedImages();
                int total = images.size();

                for (int i = 0; i < total; i++) {
                    var imageInfo = images.get(i);
                    int idx = i;

                    Platform.runLater(() -> {
                        nextButton.setText(MessageFormat.format(message("upload.progress"), idx + 1, total));
                        wizardProgress.setProgress((double) idx / total);
                    });

                    var wrapper = imageInfo.image();
                    var jpegBytes = imageToJpeg(wrapper);
                    var imageKind = imageInfo.kind().name();
                    var imageTitle = imageInfo.title();
                    var metadata = ImageMetadata.fromImage(wrapper, imageInfo.kind());
                    var metadataJson = metadata != null ? gson.toJson(metadata) : null;

                    client.uploadImage(sessionId, imageTitle, imageKind, metadataJson, jpegBytes, (partCompleted, totalParts) -> {
                        double partProgress = (double) partCompleted / totalParts;
                        double overall = ((double) idx + partProgress) / total;
                        Platform.runLater(() -> wizardProgress.setProgress(overall));
                    });
                }

                var sessionUrl = url + "/observation/" + sessionId;

                if (step4Handler.shouldPublish()) {
                    Platform.runLater(() -> nextButton.setText(message("upload.publishing")));
                    try {
                        client.publishSession(sessionId);
                    } catch (SpectroSolHubException pubEx) {
                        LOGGER.warn("Publishing failed", pubEx);
                        Platform.runLater(() -> {
                            wizardProgress.setProgress(1.0);
                            stage.close();
                            AlertFactory.warning(message("upload.publish.failed")).showAndWait();
                        });
                        openInBrowser(sessionUrl);
                        return;
                    }
                }

                Platform.runLater(() -> {
                    wizardProgress.setProgress(1.0);
                    stage.close();
                });
                openInBrowser(sessionUrl);

            } catch (SpectroSolHubException | IOException ex) {
                LOGGER.error("Upload failed", ex);
                Platform.runLater(() -> {
                    wizardProgress.setProgress(0);
                    nextButton.setText(message("button.submit"));
                    nextButton.setDisable(false);
                    previousButton.setDisable(false);
                    cancelButton.setDisable(false);
                    var alert = AlertFactory.error(
                            MessageFormat.format(message("upload.failed"), ex.getMessage()));
                    alert.showAndWait();
                });
            }
        });
    }

    private static void openInBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            LOGGER.error("Failed to open browser", ex);
        }
    }

    static byte[] imageToJpeg(ImageWrapper image) throws IOException {
        if (image instanceof FileBackedImage fbi) {
            image = fbi.unwrapToMemory();
        }

        BufferedImage buffered;
        if (image instanceof ImageWrapper32 mono) {
            buffered = new BufferedImage(mono.width(), mono.height(), BufferedImage.TYPE_USHORT_GRAY);
            short[] data = ((DataBufferUShort) buffered.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < mono.height(); y++) {
                for (int x = 0; x < mono.width(); x++) {
                    data[y * mono.width() + x] = (short) Math.round(mono.data()[y][x]);
                }
            }
            var rgb = new BufferedImage(mono.width(), mono.height(), BufferedImage.TYPE_INT_RGB);
            short[] src = ((DataBufferUShort) buffered.getRaster().getDataBuffer()).getData();
            int[] dest = ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < src.length; i++) {
                int v = (src[i] >> 8) & 0xFF;
                dest[i] = (v << 16) | (v << 8) | v;
            }
            buffered = rgb;
        } else if (image instanceof RGBImage rgb) {
            buffered = new BufferedImage(rgb.width(), rgb.height(), BufferedImage.TYPE_INT_RGB);
            int[] data = ((DataBufferInt) buffered.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < rgb.height(); y++) {
                for (int x = 0; x < rgb.width(); x++) {
                    int r = Math.round(rgb.r()[y][x]) >> 8 & 0xFF;
                    int g = Math.round(rgb.g()[y][x]) >> 8 & 0xFF;
                    int b = Math.round(rgb.b()[y][x]) >> 8 & 0xFF;
                    data[y * rgb.width() + x] = (r << 16) | (g << 8) | b;
                }
            }
        } else {
            throw new IOException("Unsupported image type: " + image.getClass().getName());
        }

        var baos = new ByteArrayOutputStream();
        ImageIO.write(buffered, "jpg", baos);
        return baos.toByteArray();
    }

    static String message(String messageKey) {
        return I18N.string(JSolEx.class, "spectrosolhub-submission", messageKey);
    }
}
