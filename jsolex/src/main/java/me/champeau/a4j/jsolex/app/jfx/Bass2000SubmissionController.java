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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Bass2000Compatibility;
import me.champeau.a4j.jsolex.processing.util.Bass2000UploadHistoryService;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.GONG;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoublePair;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static javafx.scene.input.KeyCode.ESCAPE;
import static javafx.stage.Modality.APPLICATION_MODAL;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

public class Bass2000SubmissionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bass2000SubmissionController.class);

    private static final String DUMMY_FTP = "ftp://dummy";

    private static final SpectralRay BASS2000_HA = SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6562.762));
    private static final SpectralRay BASS2000_CALCIUM_K = SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3933.663));
    private static final SpectralRay BASS2000_CALCIUM_H = SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3968.469));

    private static final SpectralRay BASS2000_HA_WING = SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6561.262));
    private static final SpectralRay BASS2000_CALCIUM_K_WING = SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3932.163));
    private static final SpectralRay BASS2000_CALCIUM_H_WING = SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3966.968));

    private static final Map<SpectralRay, String> ACCEPTED_SPECTRAL_RAYS = Map.of(
            // Use official wavelengths from the BASS2000 documentation
            BASS2000_HA, "Ha",
            BASS2000_CALCIUM_K, "Cak",
            BASS2000_CALCIUM_H, "Cah"
    );

    private static final Map<SpectralRay, String> RAY_TO_NAME = Map.of(
            BASS2000_HA_WING, "Ha2cb",
            BASS2000_CALCIUM_H_WING, "Cah1v",
            BASS2000_CALCIUM_K_WING, "Cak1v"
    );

    private static final Map<SpectralRay, SpectralRay> RAY_TO_WING = Map.of(
            BASS2000_HA, BASS2000_HA_WING,
            BASS2000_CALCIUM_K, BASS2000_CALCIUM_K_WING,
            BASS2000_CALCIUM_H, BASS2000_CALCIUM_H_WING
    );

    private static final double TOLERANCE_ANGSTROMS = 0.1;
    private static final int IMAGE_VIEW_SIZE = 480;

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

    private Stage stage;
    private int currentStep = 1;
    private static final int TOTAL_STEPS = 5;

    private JSolExInterface mainController;

    private ImageWrapper originalImage;
    private ImageWrapper generatedBass2000Image;
    private ImageWrapper originalOffBandImage;
    private ImageWrapper generatedOffBandImage;
    private Image gongReferenceImage;
    private ZoomableImageView userImageView;
    private ZoomableImageView gongImageView;
    private VBox gongImageContainer;

    private enum ComparisonMode {
        NORMAL, BLINK, BLEND
    }

    private ComparisonMode currentComparisonMode = ComparisonMode.NORMAL;
    private Timeline blinkTimeline;
    private Image userDisplayImage;
    private Image gongDisplayImage;
    private HBox originalImageComparisonBox;
    private VBox blinkContainer;
    private VBox blendContainer;
    private HBox angleAdjustmentBox;
    private Slider mainAngleSlider;
    private boolean showingUserImage = true;
    private double blinkDurationMs = 500.0;
    private double opacityValue = 50.0;

    private ZoomableImageView blinkImageView;
    private StackPane opacityStackPane;
    private ZoomableImageView opacityUserImageView;
    private ZoomableImageView opacityGongImageView;
    private Button comparisonModeButton;
    private Label comparisonModeLabel;
    private Slider comparisonModeSlider;
    private Button fullscreenComparisonModeButton;

    private int rotation = 0;
    private boolean horizontalFlip = false;
    private boolean verticalFlip = false;
    private double angleAdjustment = 0.0;
    private File savedFilePath;
    private File savedOffBandFilePath;

    private TextField observerNameField;
    private TextField observerEmailField;
    private TextField siteLatitudeField;
    private TextField siteLongitudeField;
    private CheckBox focalReducerCheckbox;
    private ChoiceBox<SpectralRay> wavelengthField;
    private TextField telescopeNameField;
    private TextField mountNameField;
    private TextField telescopeFocalLengthField;
    private TextField apertureField;
    private TextField stopField;
    private TextField erfField;
    private TextField cameraNameField;
    private TextField pixelSizeField;
    private ComboBox<String> binningField;
    private TextField spectrographNameField;
    private TextField collimatorFocalLengthField;
    private TextField cameraLensFocalLengthField;
    private TextField gratingDensityField;
    private TextField orderField;
    private TextField totalAngleField;
    private TextField slitWidthField;
    private TextField slitHeightField;

    // Step 4 - Filename generation
    private TextField lineCenterFilenameField;
    private TextField offBandFilenameField;
    private String generatedFilename;

    private final List<TextField> requiredFields = new ArrayList<>();
    private final List<CheckBox> requiredCheckboxes = new ArrayList<>();
    private final List<ComboBox<String>> requiredComboBoxes = new ArrayList<>();

    private CheckBox lineCenterUploadCheckbox;
    private CheckBox wingImageUploadCheckbox;
    private Label step1DuplicateWarningLabel;
    private Label step5DuplicateWarningLabel;

    private Stage fullscreenStage;
    private ImageView fullscreenImageView;
    private StackPane fullscreenBlendPane;
    private ImageView fullscreenUserImageView;
    private ImageView fullscreenGongImageView;
    private Timeline fullscreenBlinkTimeline;
    private boolean fullscreenShowingUserImage = true;

    private Scaling scaling;
    private Path outputDirectory;

    public void setup(Stage stage, JSolExInterface mainController, Path outputDirectory) {
        this.stage = stage;
        this.mainController = mainController;
        this.scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));
        this.outputDirectory = outputDirectory;
        
        Platform.runLater(this::initializeStyles);
        
        initializeWizard();
        loadUserImage();
    }

    private void initializeStyles() {
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
        }
    }


    private void initializeWizard() {
        updateStepIndicator();
        loadFirstStep();

        previousButton.setDisable(true);
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
            if (currentStep == 5) {
                savedFilePath = null;
            }

            currentStep--;
            updateStepIndicator();
            loadCurrentStep();
            updateButtons();
        }
    }

    @FXML
    private void onNext() {
        if (currentStep < TOTAL_STEPS) {
            if (currentStep == 3) {
                if (!isWavelengthValid()) {
                    showWavelengthError();
                    return;
                }

                createUpdatedProcessParams();

                // Generate wing image based on confirmed wavelength selection
                var centerRay = wavelengthField.getValue();
                var wingRay = RAY_TO_WING.get(centerRay);

                if (wingRay != null) {
                    // Show loading indicator and disable buttons
                    nextButton.setDisable(true);
                    previousButton.setDisable(true);
                    wizardProgress.setProgress(-1.0); // Indeterminate progress
                    try {
                        nextButton.setText(message("processing.wing.image"));
                    } catch (Exception e) {
                        nextButton.setText("Processing wing image...");
                    }

                    BackgroundOperations.async(() -> {
                        try {
                            var centerWavelength = centerRay.wavelength().angstroms();
                            var wingWavelength = wingRay.wavelength().angstroms();
                            var shift = centerWavelength - wingWavelength;

                            var originalOffBandImage = getGeometryCorrectedImage(wavelengthField.getSelectionModel().getSelectedItem().wavelength().angstroms(), shift);
                            if (originalOffBandImage != null) {
                                this.originalOffBandImage = originalOffBandImage;

                                var processParams = findProcessParams();
                                var observationDate = processParams.observationDetails().date();
                                var solarParams = SolarParametersUtils.computeSolarParams(observationDate.toLocalDateTime());
                                var pAngle = solarParams.p();

                                this.generatedOffBandImage = applyTransformationsToImage(originalOffBandImage, pAngle);

                                var updatedProcessParams = processParams.withSpectrumParams(
                                        processParams.spectrumParams().withRay(wingRay)
                                );
                                generatedOffBandImage.metadata().put(ProcessParams.class, updatedProcessParams);
                                generatedOffBandImage.metadata().put(Bass2000Compatibility.class,
                                        new Bass2000Compatibility(wingRay.wavelength().angstroms()));
                            }

                            Platform.runLater(() -> {
                                // Reset progress and button state
                                var progress = (double) (currentStep + 1) / TOTAL_STEPS;
                                wizardProgress.setProgress(progress);

                                var maybeFileName = generateFileName();
                                if (maybeFileName.isEmpty()) {
                                    loadStep3();
                                    AlertFactory.error(message("filename.error")).showAndWait();
                                    return;
                                }
                                generatedFilename = maybeFileName.get();
                                savedFilePath = null;

                                // Continue to next step
                                currentStep++;
                                updateStepIndicator();
                                loadCurrentStep();
                                updateButtons();
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                // Reset progress and button state
                                var progress = (double) currentStep / TOTAL_STEPS;
                                wizardProgress.setProgress(progress);
                                nextButton.setDisable(false);
                                previousButton.setDisable(false);
                                updateButtons();
                                AlertFactory.error("Failed to generate off-band image: " + e.getMessage()).showAndWait();
                            });
                        }
                    });
                    return; // Exit early since we're processing asynchronously
                }

                var maybeFileName = generateFileName();
                if (maybeFileName.isEmpty()) {
                    loadStep3();
                    AlertFactory.error(message("filename.error")).showAndWait();
                    return;
                }
                generatedFilename = maybeFileName.get();
                savedFilePath = null;
            }

            currentStep++;
            updateStepIndicator();
            loadCurrentStep();
            updateButtons();
        } else {
            finishWizard();
        }
    }

    @FXML
    private void onCancel() {
        if (currentComparisonMode != ComparisonMode.NORMAL) {
            exitComparisonMode();
        }
        if (fullscreenStage != null) {
            closeFullscreenComparison();
        }
        stage.close();
    }

    private void updateButtons() {
        previousButton.setDisable(currentStep == 1);

        if (currentStep == TOTAL_STEPS) {
            nextButton.setText(message("button.finish"));
        } else {
            nextButton.setText(message("button.next"));
        }
    }

    private void loadCurrentStep() {
        if (currentComparisonMode != ComparisonMode.NORMAL && currentStep != 2) {
            exitComparisonMode();
        }

        switch (currentStep) {
            case 1 -> loadFirstStep();
            case 2 -> loadStep2();
            case 3 -> loadStep3();
            case 4 -> loadStep4();
            case 5 -> loadStep5();
        }
    }

    private void loadFirstStep() {
        var step1Content = createFirstStepContent();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step1Content);
    }

    private void loadStep2() {
        var step2Content = createStep2Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step2Content);

        loadGongReferenceImage();

        if (generatedBass2000Image != null) {
            updateUserImageDisplay();
        }

        validateBass2000Image();
    }

    private void loadStep3() {
        requiredFields.clear();
        requiredCheckboxes.clear();
        requiredComboBoxes.clear();

        var step3Content = createStep3Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step3Content);

        Platform.runLater(this::validateAllFieldsVisually);
    }

    private void loadStep4() {
        var step4Content = createStep4Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step4Content);

        if (lineCenterFilenameField != null && generatedFilename != null) {
            lineCenterFilenameField.setText(generatedFilename);
        }

        if (offBandFilenameField != null && generatedFilename != null) {
            try {
                var maybeOffBandFileName = generateOffBandFileName();
                if (maybeOffBandFileName.isEmpty()) {
                    loadStep3();
                    AlertFactory.error(message("filename.error")).showAndWait();
                    return;
                }
                var offBandFilename = maybeOffBandFileName.get();
                offBandFilenameField.setText(offBandFilename);
                nextButton.setDisable(false);
            } catch (Exception e) {
                offBandFilenameField.setText(generatedFilename);
            }
        }
    }

    private void loadStep5() {
        var step5Content = createStep5Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step5Content);

        checkForDuplicateOnStep5();

        if (savedFilePath == null) {
            exportBass2000ImageForUpload();
        }
    }

    private void exportBass2000ImageForUpload() {
        BackgroundOperations.async(() -> {
            try {
                var baseFileName = (lineCenterFilenameField != null && !lineCenterFilenameField.getText().trim().isEmpty())
                        ? lineCenterFilenameField.getText().trim()
                        : generateFileName().orElseThrow();

                if (baseFileName.toLowerCase().endsWith(".fits")) {
                    baseFileName = baseFileName.substring(0, baseFileName.length() - 5);
                }

                var lineCenterFileName = baseFileName + ".fits";
                Files.createDirectories(outputDirectory);
                var lineCenterFile = outputDirectory.resolve(lineCenterFileName).toFile();
                FitsUtils.writeFitsFile(generatedBass2000Image, lineCenterFile, findProcessParams(), false);
                savedFilePath = lineCenterFile;

                if (generatedOffBandImage != null) {
                    var offBandBaseFileName = (offBandFilenameField != null && !offBandFilenameField.getText().trim().isEmpty())
                            ? offBandFilenameField.getText().trim()
                            : generateOffBandFileName().orElseThrow();

                    if (offBandBaseFileName.toLowerCase().endsWith(".fits")) {
                        offBandBaseFileName = offBandBaseFileName.substring(0, offBandBaseFileName.length() - 5);
                    }

                    var offBandFileName = offBandBaseFileName + ".fits";
                    var offBandFile = outputDirectory.resolve(offBandFileName).toFile();

                    FitsUtils.writeFitsFile(generatedOffBandImage, offBandFile, generatedOffBandImage.findMetadata(ProcessParams.class).orElseThrow(), false);
                    savedOffBandFilePath = offBandFile;
                }

                Platform.runLater(() -> {
                    if (currentStep == 5) {
                        loadStep5();
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Failed to save BASS2000 file automatically", e);
                Platform.runLater(() -> {
                    var alert = AlertFactory.error("Could not save BASS2000 file: " + e.getMessage());
                    alert.setTitle("Save Error");
                    alert.setHeaderText("File Save Failed");
                    alert.showAndWait();
                });
            }
        });
    }

    private VBox createFirstStepContent() {
        var content = new VBox(12);

        var headerLabel = new Label(message("requirements.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var warningLabel = new Label(message("requirements.warning"));
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

        var requirementsFlow = createRequirementsTextFlow();

        var processLabel = new Label(message("requirements.process"));
        processLabel.setWrapText(true);
        processLabel.setStyle("-fx-font-size: 14px;");

        var agreeLabel = new Label(message("agreement"));
        agreeLabel.setWrapText(true);
        agreeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");

        step1DuplicateWarningLabel = new Label();
        step1DuplicateWarningLabel.setWrapText(true);
        step1DuplicateWarningLabel.setVisible(false);
        step1DuplicateWarningLabel.setManaged(false);
        step1DuplicateWarningLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-color: orange; -fx-border-width: 1px; -fx-background-color: #fff3cd;");

        content.getChildren().addAll(headerLabel, warningLabel, requirementsFlow, processLabel, agreeLabel, step1DuplicateWarningLabel);
        return content;
    }

    private TextFlow createRequirementsTextFlow() {
        var textFlow = new TextFlow();
        var requirementsText = message("requirements.requirements");

        var urlPattern = Pattern.compile("(https?://[^\\s]+)");
        var matcher = urlPattern.matcher(requirementsText);

        var lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                var beforeText = requirementsText.substring(lastEnd, matcher.start());
                var textNode = new Text(beforeText);
                textNode.setStyle("-fx-font-size: 14px;");
                textFlow.getChildren().add(textNode);
            }

            var url = matcher.group();
            var hyperlink = new Hyperlink(url);
            hyperlink.setOnAction(e -> {
                try {
                    mainController.getHostServices().showDocument(url);
                } catch (Exception ex) {
                    LOGGER.error("Failed to open URL: " + url, ex);
                }
            });
            hyperlink.setStyle("-fx-font-size: 14px;");
            textFlow.getChildren().add(hyperlink);

            lastEnd = matcher.end();
        }

        if (lastEnd < requirementsText.length()) {
            var afterText = requirementsText.substring(lastEnd);
            var textNode = new Text(afterText);
            textNode.setStyle("-fx-font-size: 14px;");
            textFlow.getChildren().add(textNode);
        }

        if (textFlow.getChildren().isEmpty()) {
            var textNode = new Text(requirementsText);
            textNode.setStyle("-fx-font-size: 14px;");
            textFlow.getChildren().add(textNode);
        }

        return textFlow;
    }

    private boolean isWavelengthValid() {
        return wavelengthField.getValue() != null;
    }

    private void showWavelengthError() {
        var alert = AlertFactory.error(message("wavelength.error.message"));
        alert.setTitle(message("wavelength.error.title"));
        alert.setHeaderText(message("wavelength.error.header"));
        alert.showAndWait();
    }

    private VBox createStep2Content() {
        var content = new VBox(6);

        var headerLabel = new Label(message("orientation.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var instructionLabel = new Label(message("orientation.instruction"));
        instructionLabel.setWrapText(true);
        instructionLabel.setStyle("-fx-font-size: 14px;");

        var validationWarningLabel = new Label();
        validationWarningLabel.setWrapText(true);
        validationWarningLabel.setMaxWidth(Double.MAX_VALUE);
        validationWarningLabel.setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-padding: 15; -fx-border-color: #c62828; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;");
        validationWarningLabel.setId("validationWarning");
        validationWarningLabel.setVisible(false);
        validationWarningLabel.setManaged(false);

        var imageComparisonBox = new HBox(10);
        imageComparisonBox.setMaxHeight(IMAGE_VIEW_SIZE);

        imageComparisonBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
        this.originalImageComparisonBox = imageComparisonBox;

        var userImageContainer = new VBox(5);
        userImageContainer.setStyle("-fx-alignment: center;");
        userImageContainer.setPrefWidth(IMAGE_VIEW_SIZE);
        userImageContainer.setMaxWidth(IMAGE_VIEW_SIZE);
        userImageContainer.setMinWidth(IMAGE_VIEW_SIZE);
        userImageContainer.setMaxHeight(IMAGE_VIEW_SIZE);
        var userImageLabel = new Label(message("orientation.your.image"));
        userImageLabel.setStyle("-fx-font-weight: bold;");
        var userImageView = new ZoomableImageView();
        userImageView.setPrefWidth(IMAGE_VIEW_SIZE);
        userImageView.setPrefHeight(IMAGE_VIEW_SIZE);
        userImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        var userImageWrapper = new HBox();
        userImageWrapper.setPrefWidth(IMAGE_VIEW_SIZE);
        userImageWrapper.setPrefHeight(IMAGE_VIEW_SIZE);
        userImageWrapper.setMaxWidth(IMAGE_VIEW_SIZE);
        userImageWrapper.setMaxHeight(IMAGE_VIEW_SIZE);
        userImageWrapper.setMinWidth(IMAGE_VIEW_SIZE);
        userImageWrapper.setMinHeight(IMAGE_VIEW_SIZE);
        userImageWrapper.setStyle("-fx-alignment: center;");
        userImageWrapper.getChildren().add(userImageView);

        userImageContainer.getChildren().addAll(userImageLabel, userImageWrapper);

        gongImageContainer = new VBox(5);
        gongImageContainer.setStyle("-fx-alignment: center;");
        gongImageContainer.setPrefWidth(IMAGE_VIEW_SIZE);
        gongImageContainer.setMaxWidth(IMAGE_VIEW_SIZE);
        gongImageContainer.setMinWidth(IMAGE_VIEW_SIZE);
        gongImageContainer.setMaxHeight(IMAGE_VIEW_SIZE);
        var gongImageLabel = new Label(message("orientation.gong.reference"));
        gongImageLabel.setStyle("-fx-font-weight: bold;");
        var gongImageView = new ZoomableImageView();
        gongImageView.setPrefWidth(IMAGE_VIEW_SIZE);
        gongImageView.setPrefHeight(IMAGE_VIEW_SIZE);
        gongImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        var gongImageWrapper = new HBox();
        gongImageWrapper.setPrefWidth(IMAGE_VIEW_SIZE);
        gongImageWrapper.setPrefHeight(IMAGE_VIEW_SIZE);
        gongImageWrapper.setMaxWidth(IMAGE_VIEW_SIZE);
        gongImageWrapper.setMaxHeight(IMAGE_VIEW_SIZE);
        gongImageWrapper.setMinWidth(IMAGE_VIEW_SIZE);
        gongImageWrapper.setMinHeight(IMAGE_VIEW_SIZE);
        gongImageWrapper.setStyle("-fx-alignment: center;");
        gongImageWrapper.getChildren().add(gongImageView);

        gongImageContainer.getChildren().addAll(gongImageLabel, gongImageWrapper);

        imageComparisonBox.getChildren().addAll(userImageContainer, gongImageContainer);

        mainAngleSlider = new Slider(-2.0, 2.0, 0.0);
        mainAngleSlider.setShowTickLabels(true);
        mainAngleSlider.setShowTickMarks(true);
        mainAngleSlider.setMajorTickUnit(1.0);
        mainAngleSlider.setMinorTickCount(4);
        mainAngleSlider.setPrefWidth(300);

        var angleSliderListener = (ChangeListener<Number>) (observable, oldValue, newValue) -> {
            angleAdjustment = newValue.doubleValue();
            applyCurrentTransformations();
        };
        mainAngleSlider.valueProperty().addListener(angleSliderListener);

        comparisonModeSlider = new Slider(0.0, 100.0, 50.0);
        comparisonModeSlider.setShowTickLabels(true);
        comparisonModeSlider.setShowTickMarks(true);
        comparisonModeSlider.setMajorTickUnit(25.0);
        comparisonModeSlider.setMinorTickCount(4);
        comparisonModeSlider.setPrefWidth(300);

        var comparisonSliderListener = (ChangeListener<Number>) (observable, oldValue, newValue) -> {
            if (currentComparisonMode == ComparisonMode.BLINK) {
                double blinkMs = 500.0 + (newValue.doubleValue() / 100.0) * 4500.0;
                blinkDurationMs = blinkMs;
                if (blinkTimeline != null) {
                    restartBlinkAnimation();
                }
            } else if (currentComparisonMode == ComparisonMode.BLEND) {
                opacityValue = newValue.doubleValue();
                if (opacityUserImageView != null) {
                    opacityUserImageView.setOpacity(opacityValue / 100.0);
                }
            }
        };
        comparisonModeSlider.valueProperty().addListener(comparisonSliderListener);

        angleAdjustmentBox = new HBox(8);
        angleAdjustmentBox.setStyle("-fx-alignment: center; -fx-padding: 1;");
        angleAdjustmentBox.setVisible(false);
        var angleLabel = new Label(message("fine.tilt.adjust"));
        angleLabel.setStyle("-fx-font-weight: bold;");
        comparisonModeLabel = new Label(message("blink.duration"));
        comparisonModeLabel.setStyle("-fx-font-weight: bold;");
        var fullscreenButton = new Button(message("button.fullscreen"));
        fullscreenButton.getStyleClass().add("custom-button");
        fullscreenButton.setOnAction(e -> openFullscreenComparison());

        angleAdjustmentBox.getChildren().addAll(angleLabel, mainAngleSlider, comparisonModeLabel, comparisonModeSlider);

        var controlsBox = new HBox(8);
        controlsBox.setStyle("-fx-alignment: center; -fx-padding: 1;");

        var controlsLabel = new Label(message("orientation.controls.label"));
        controlsLabel.setStyle("-fx-font-weight: bold;");

        var horizontalFlipButton = new Button("⇄ " + message("orientation.button.horizontal.flip"));
        horizontalFlipButton.getStyleClass().add("default-button");
        horizontalFlipButton.setOnAction(e -> applyHorizontalFlip());

        var verticalFlipButton = new Button("⇅ " + message("orientation.button.vertical.flip"));
        verticalFlipButton.getStyleClass().add("default-button");
        verticalFlipButton.setOnAction(e -> applyVerticalFlip());

        var rotateLeftButton = new Button("↶ " + message("orientation.button.rotate.left"));
        rotateLeftButton.getStyleClass().add("default-button");
        rotateLeftButton.setOnAction(e -> applyRotation(-90));

        var rotateRightButton = new Button("↷ " + message("orientation.button.rotate.right"));
        rotateRightButton.getStyleClass().add("default-button");
        rotateRightButton.setOnAction(e -> applyRotation(90));

        var resetButton = new Button(message("orientation.button.reset"));
        resetButton.getStyleClass().add("default-button");

        comparisonModeButton = new Button(getComparisonModeButtonText());
        comparisonModeButton.getStyleClass().add("custom-button");
        comparisonModeButton.setOnAction(e -> toggleComparisonMode());

        resetButton.setOnAction(e -> {
            mainAngleSlider.valueProperty().removeListener(angleSliderListener);
            mainAngleSlider.setValue(0.0);
            resetTransformations();
            mainAngleSlider.valueProperty().addListener(angleSliderListener);
        });

        controlsBox.getChildren().addAll(controlsLabel, horizontalFlipButton, verticalFlipButton, rotateLeftButton, rotateRightButton, resetButton, comparisonModeButton, fullscreenButton);

        this.userImageView = userImageView;
        this.gongImageView = gongImageView;

        if (generatedBass2000Image != null) {
            updateUserImageDisplay();
        }
        loadGongReferenceImage();

        var processParams = findProcessParams();
        if (processParams != null && processParams.spectrumParams().ray() != SpectralRay.H_ALPHA) {
            var tipLabel = new Label(message("orientation.tip.non.halpha"));
            tipLabel.setStyle("-fx-background-color: #e8f4fd; -fx-text-fill: #0066cc; -fx-padding: 4; -fx-border-color: #0066cc; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
            tipLabel.setWrapText(true);
            tipLabel.setPrefHeight(Region.USE_COMPUTED_SIZE);
            tipLabel.setMinHeight(Region.USE_PREF_SIZE);
            content.getChildren().add(tipLabel);
        }

        content.getChildren().addAll(headerLabel, instructionLabel, validationWarningLabel, imageComparisonBox, angleAdjustmentBox, controlsBox);

        return content;
    }

    private VBox createStep3Content() {
        var content = new VBox(10);

        var headerLabel = new Label(message("metadata.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var instructionLabel = new Label(message("metadata.instruction"));
        instructionLabel.setStyle("-fx-font-size: 14px;");

        var formGrid = createForm();

        var scrollPane = new ScrollPane();
        var formContent = new VBox(12);
        formContent.getChildren().addAll(headerLabel, instructionLabel, formGrid);
        formContent.setStyle("-fx-padding: 8;");

        scrollPane.setContent(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        content.getChildren().add(scrollPane);
        return content;
    }

    private GridPane createForm() {
        var formGrid = new GridPane();
        formGrid.getStyleClass().add("form-grid");
        formGrid.setHgap(15);
        formGrid.setVgap(8);
        formGrid.setStyle("-fx-padding: 5;");

        var row = 0;

        addSectionHeader(formGrid, message("observer.section.title"), row++);

        wavelengthField = new ChoiceBox<>();
        wavelengthField.getItems().addAll(ACCEPTED_SPECTRAL_RAYS.keySet());
        addFormField(formGrid, message("instrument.wavelength.label"), wavelengthField, 0, row++, true);

        var nameEmailContainer = new HBox(15);
        
        var nameContainer = new VBox(3);
        var nameLabel = new Label(message("observer.name.label"));
        nameLabel.getStyleClass().add("field-label");
        nameLabel.setStyle("-fx-font-weight: bold;");
        observerNameField = new TextField();
        observerNameField.getStyleClass().add("text-field");
        observerNameField.setPromptText(message("observer.name.prompt"));
        observerNameField.setPrefWidth(300);
        requiredFields.add(observerNameField);
        observerNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            var isValid = isFieldValid(observerNameField);
            updateFieldValidationStyle(observerNameField, isValid);
            validateForm();
        });
        nameContainer.getChildren().addAll(nameLabel, observerNameField);
        
        var emailContainer = new VBox(3);
        var emailLabel = new Label(message("observer.email.label"));
        emailLabel.getStyleClass().add("field-label");
        emailLabel.setStyle("-fx-font-weight: bold;");
        observerEmailField = new TextField();
        observerEmailField.getStyleClass().add("text-field");
        observerEmailField.setPromptText(message("observer.email.prompt"));
        observerEmailField.setPrefWidth(300);
        requiredFields.add(observerEmailField);
        observerEmailField.textProperty().addListener((observable, oldValue, newValue) -> {
            var isValid = isFieldValid(observerEmailField);
            updateFieldValidationStyle(observerEmailField, isValid);
            validateForm();
        });
        emailContainer.getChildren().addAll(emailLabel, observerEmailField);
        
        nameEmailContainer.getChildren().addAll(nameContainer, emailContainer);
        HBox.setHgrow(nameContainer, Priority.ALWAYS);
        HBox.setHgrow(emailContainer, Priority.ALWAYS);
        
        formGrid.add(nameEmailContainer, 0, row++, 3, 1);

        siteLatitudeField = new TextField();
        siteLatitudeField.setPromptText(message("site.latitude.prompt"));
        addFormField(formGrid, message("site.latitude.label"), siteLatitudeField, 0, row, true);

        siteLongitudeField = new TextField();
        siteLongitudeField.setPromptText(message("site.longitude.prompt"));
        addFormField(formGrid, message("site.longitude.label"), siteLongitudeField, 1, row, true);

        var decimalHint = new Label(message("site.coordinates.decimal.hint"));
        decimalHint.setWrapText(true);
        
        formGrid.add(decimalHint, 2, row);
        row++;

        addSectionHeader(formGrid, message("instrument.section.title"), row++);

        focalReducerCheckbox = new CheckBox(message("instrument.focal.reducer.checkbox"));
        addFormField(formGrid, message("instrument.focal.reducer.label"), focalReducerCheckbox, 0, row++, false);

        mountNameField = new TextField();
        mountNameField.setPromptText(message("mount.name.prompt"));
        addFormField(formGrid, message("mount.name.label"), mountNameField, 0, row, true);

        telescopeNameField = new TextField();
        telescopeNameField.setPromptText(message("instrument.name.prompt"));
        addFormField(formGrid, message("instrument.name.label"), telescopeNameField, 1, row, true);

        telescopeFocalLengthField = new TextField();
        telescopeFocalLengthField.setPromptText("e.g., 1000");
        addFormField(formGrid, "Telescope Focal Length (mm)", telescopeFocalLengthField, 2, row++, true);

        apertureField = new TextField();
        apertureField.setPromptText(message("instrument.aperture.prompt"));
        addFormField(formGrid, message("instrument.aperture.label"), apertureField, 0, row, true);

        stopField = new TextField();
        stopField.setPromptText(message("instrument.stop.prompt"));
        addFormField(formGrid, message("instrument.stop.label"), stopField, 1, row, false);

        erfField = new TextField();
        erfField.setPromptText(message("instrument.erf.prompt"));
        addFormField(formGrid, message("instrument.erf.label"), erfField, 2, row++, false);

        cameraNameField = new TextField();
        cameraNameField.setPromptText(message("instrument.camera.name.prompt"));
        addFormField(formGrid, message("instrument.camera.name.label"), cameraNameField, 0, row, true);

        pixelSizeField = new TextField();
        pixelSizeField.setPromptText(message("instrument.pixel.size.only.prompt"));
        addFormField(formGrid, message("instrument.pixel.size.only.label"), pixelSizeField, 1, row, true);
        pixelSizeField.setDisable(true);
        pixelSizeField.setEditable(false);

        binningField = new ComboBox<>();
        binningField.getItems().addAll("1", "2", "3", "4");
        binningField.setPromptText(message("instrument.binning.prompt"));
        addFormField(formGrid, message("instrument.binning.label"), binningField, 2, row++, true);
        binningField.setDisable(true);
        binningField.setEditable(false);
        formGrid.add(new Label(message("instrument.pixels.binning.note")), 0, row++, 3, 1);

        addSectionHeader(formGrid, message("spectrograph.section.title"), row++);

        spectrographNameField = new TextField();
        spectrographNameField.setPromptText(message("spectrograph.name.prompt"));
        addFormField(formGrid, message("spectrograph.name.label"), spectrographNameField, 0, row, true);

        slitWidthField = new TextField();
        slitWidthField.setPromptText(message("spectrograph.slit.width.prompt"));
        addFormField(formGrid, message("spectrograph.slit.width.label"), slitWidthField, 1, row, true);

        slitHeightField = new TextField();
        slitHeightField.setPromptText(message("spectrograph.slit.height.prompt"));
        addFormField(formGrid, message("spectrograph.slit.height.label"), slitHeightField, 2, row++, true);

        gratingDensityField = new TextField();
        gratingDensityField.setPromptText(message("spectrograph.grating.density.prompt"));
        addFormField(formGrid, message("spectrograph.grating.density.label"), gratingDensityField, 0, row, true);

        collimatorFocalLengthField = new TextField();
        collimatorFocalLengthField.setPromptText(message("spectrograph.collimator.focal.length.prompt"));
        addFormField(formGrid, message("spectrograph.collimator.focal.length.label"), collimatorFocalLengthField, 1, row, true);

        cameraLensFocalLengthField = new TextField();
        cameraLensFocalLengthField.setPromptText(message("spectrograph.camera.lens.focal.length.prompt"));
        addFormField(formGrid, message("spectrograph.camera.lens.focal.length.label"), cameraLensFocalLengthField, 2, row++, true);

        orderField = new TextField();
        orderField.setPromptText(message("spectrograph.order.prompt"));
        addFormField(formGrid, message("spectrograph.order.label"), orderField, 0, row, true);

        totalAngleField = new TextField();
        totalAngleField.setPromptText(message("spectrograph.total.angle.prompt"));
        addFormField(formGrid, message("spectrograph.total.angle.label"), totalAngleField, 1, row++, true);

        populateFormFromProcessParams();

        return formGrid;
    }

    private void addSectionHeader(GridPane grid, String title, int row) {
        var headerLabel = new Label(title);
        headerLabel.getStyleClass().add("panel-section-title");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333; -fx-padding: 8 0 3 0;");
        grid.add(headerLabel, 0, row, 3, 1);
    }

    private void addFormField(GridPane grid, String labelText, Node field, int column, int row, boolean required) {
        var fieldContainer = new VBox(3);

        var label = new Label(labelText);
        label.getStyleClass().add("field-label");
        if (required) {
            label.setStyle("-fx-font-weight: bold;");
        }

        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        if (field instanceof TextField textField) {
            textField.getStyleClass().add("text-field");
            textField.setPrefWidth(180);

            if (required) {
                requiredFields.add(textField);
                textField.textProperty().addListener((observable, oldValue, newValue) -> {
                    var isValid = isFieldValid(textField);
                    updateFieldValidationStyle(textField, isValid);
                    validateForm();
                });
            }
        }
        if (field instanceof CheckBox checkBox) {
            checkBox.getStyleClass().add("check-box");
            if (required) {
                requiredCheckboxes.add(checkBox);
                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    var isValid = checkBox.isSelected();
                    updateFieldValidationStyle(checkBox, isValid);
                    validateForm();
                });
            }
        }
        if (field instanceof ComboBox<?> comboBox) {
            comboBox.getStyleClass().add("choice-box");
            if (required) {
                @SuppressWarnings("unchecked")
                var stringComboBox = (ComboBox<String>) comboBox;
                requiredComboBoxes.add(stringComboBox);
                stringComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                    var isValid = stringComboBox.getValue() != null;
                    updateFieldValidationStyle(stringComboBox, isValid);
                    validateForm();
                });
            }
        }
        if (field instanceof ChoiceBox<?> choiceBox) {
            choiceBox.getStyleClass().add("choice-box");
        }

        fieldContainer.getChildren().addAll(label, field);
        grid.add(fieldContainer, column, row);
    }


    private ProcessParams findProcessParams() {
        return generatedBass2000Image.findMetadata(ProcessParams.class).orElse(null);
    }

    private void populateFormFromProcessParams() {
        var processParams = findProcessParams();
        var spectralRay = processParams.spectrumParams().ray();
        if (spectralRay != null) {
            var wavelength = spectralRay.wavelength().angstroms();
            var closestRay = ACCEPTED_SPECTRAL_RAYS.keySet().stream()
                    .filter(ray -> Math.abs(ray.wavelength().angstroms() - wavelength) <= TOLERANCE_ANGSTROMS)
                    .min(Comparator.comparingDouble(a -> Math.abs(a.wavelength().angstroms() - wavelength)));
            closestRay.ifPresentOrElse(wavelengthField::setValue, () -> wavelengthField.setValue(BASS2000_HA));
        }

        var spectrograph = processParams.observationDetails().instrument();
        if (spectrograph != null) {
            spectrographNameField.setText(spectrograph.label());
            collimatorFocalLengthField.setText(String.format(Locale.US, "%.1f", spectrograph.collimatorFocalLength()));
            cameraLensFocalLengthField.setText(String.format(Locale.US, "%.1f", spectrograph.focalLength()));
            gratingDensityField.setText(String.format(Locale.US, "%.0f", (double) spectrograph.density()));
            orderField.setText(String.valueOf(spectrograph.order()));
            totalAngleField.setText(String.format(Locale.US, "%.1f", spectrograph.totalAngleDegrees()));
            slitWidthField.setText(String.format(Locale.US, "%.3f", spectrograph.slitWidthMicrons()));
            slitHeightField.setText(String.format(Locale.US, "%.1f", spectrograph.slitHeightMillimeters()));
        }

        var observationDetails = processParams.observationDetails();
        if (observationDetails.aperture() != null) {
            apertureField.setText(String.valueOf(observationDetails.aperture()));
        }
        if (observationDetails.stop() != null) {
            stopField.setText(String.valueOf(observationDetails.stop()));
        }
        if (observationDetails.focalLength() != null) {
            telescopeFocalLengthField.setText(String.valueOf(observationDetails.focalLength()));
        }
        if (observationDetails.telescope() != null) {
            telescopeNameField.setText(observationDetails.telescope());
        }
        if (observationDetails.mount() != null) {
            mountNameField.setText(observationDetails.mount());
        }
        if (observationDetails.camera() != null) {
            cameraNameField.setText(observationDetails.camera());
        }
        if (observationDetails.pixelSize() != null) {
            pixelSizeField.setText(String.format(Locale.US, "%.2f", observationDetails.pixelSize()));
        }
        if (observationDetails.binning() != null) {
            binningField.setValue(String.valueOf(observationDetails.binning()));
        }
        if (observationDetails.observer() != null && !observationDetails.observer().isBlank()) {
            observerNameField.setText(observationDetails.observer());
        }
        if (observationDetails.email() != null && !observationDetails.email().isBlank()) {
            observerEmailField.setText(observationDetails.email());
        }
        if (observationDetails.coordinates() != null) {
            var coords = observationDetails.coordinates();
            siteLatitudeField.setText(String.format(Locale.US, "%.4f", coords.a()));
            siteLongitudeField.setText(String.format(Locale.US, "%.4f", coords.b()));
        }
        if (observationDetails.energyRejectionFilter() != null) {
            erfField.setText(observationDetails.energyRejectionFilter());
        }
    }

    private void validateForm() {
        var allValid = true;

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

        if (currentStep == 3) {
            nextButton.setDisable(!allValid);
        }
    }

    private void validateAllFieldsVisually() {
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

        validateForm();
    }

    private boolean isFieldValid(TextField field) {
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
                return Arrays.stream(parts).allMatch(s -> s.length() >= 3);
            }
            return false;
        } else if (field == spectrographNameField) {
            return !"UNKNOWN".equals(SpectroHeliograph.bass2000Id(spectrographNameField.getText()));
        }

        return true;
    }

    private void updateFieldValidationStyle(Node field, boolean isValid) {
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
            // Remove validation tooltip when field becomes valid
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
    }

    private VBox createStep4Content() {
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

        lineCenterFilenameField = new TextField();
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

        offBandFilenameField = new TextField();
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

    private VBox createStep5Content() {
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
        return content;
    }

    private VBox createFileSavedSection() {
        var section = new VBox(6);

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
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .findFirst()
                .orElse(null);
    }

    private VBox createUploadSection() {
        var section = new VBox(6);
        section.setStyle("-fx-border-color: #2196F3; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 10;");

        var sectionHeader = new Label(message("image.preview.upload"));
        sectionHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2196F3;");

        var imagesContainer = new HBox(20);
        imagesContainer.setStyle("-fx-alignment: center;");
        VBox.setVgrow(imagesContainer, Priority.ALWAYS);

        var lineCenterContainer = createImagePreviewContainer(message("upload.line.center"), generatedBass2000Image);
        lineCenterUploadCheckbox = getCheckboxFromContainer(lineCenterContainer);
        imagesContainer.getChildren().add(lineCenterContainer);

        if (generatedOffBandImage != null) {
            var wingContainer = createImagePreviewContainer(message("upload.wing.image"), generatedOffBandImage);
            wingImageUploadCheckbox = getCheckboxFromContainer(wingContainer);
            imagesContainer.getChildren().add(wingContainer);
        }

        var uploadInfoLabel = new Label(message("upload.upload.info"));
        uploadInfoLabel.setWrapText(true);

        var uploadButton = new Button(message("upload.button.upload"));
        uploadButton.getStyleClass().add("primary-button");
        uploadButton.setPrefWidth(200);
        uploadButton.setOnAction(e -> uploadToBass2000());

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

        var selectedWavelength = wavelengthField.getValue();
        if (selectedWavelength != null) {
            if (!maybeConfirmDuplicateUpload(selectedWavelength.wavelength().angstroms())) {
                return;
            }
        }

        if (uploadLineCenter && savedFilePath == null) {
            Platform.runLater(() -> {
                var alert = AlertFactory.error(message("upload.error.no.file.message"));
                alert.setTitle(message("upload.error.no.file.title"));
                alert.setHeaderText(message("upload.error.no.file.header"));
                alert.showAndWait();
            });
            return;
        }

        if (uploadWingImage && savedOffBandFilePath == null) {
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
                Platform.runLater(() -> {
                    var uploadLabel = (Label) contentPane.lookup("#uploadStatusLabel");
                    var uploadProgressBar = (ProgressBar) contentPane.lookup("#uploadProgressBar");
                    if (uploadLabel != null) {
                        uploadLabel.setText(message("upload.uploading"));
                        uploadLabel.setVisible(true);
                        uploadLabel.setManaged(true);
                    }
                    if (uploadProgressBar != null) {
                        uploadProgressBar.setVisible(true);
                        uploadProgressBar.setManaged(true);
                        uploadProgressBar.setProgress(0.0);
                        uploadProgressBar.setStyle("-fx-accent: #4CAF50;"); // Green progress bar
                    }
                });

                var lineCenterSize = uploadLineCenter && savedFilePath != null ? savedFilePath.length() : 0;
                var wingSize = uploadWingImage && savedOffBandFilePath != null ? savedOffBandFilePath.length() : 0;
                final var totalBytes = lineCenterSize + wingSize;

                var bytesUploaded = 0L;

                if (uploadLineCenter && savedFilePath != null) {
                    Platform.runLater(() -> {
                        var uploadLabel = (Label) contentPane.lookup("#uploadStatusLabel");
                        if (uploadLabel != null) {
                            uploadLabel.setText("Uploading line center file...");
                        }
                    });

                    uploadFileToFTP(savedFilePath, totalBytes, bytesUploaded);
                    bytesUploaded += savedFilePath.length();
                }

                if (uploadWingImage && savedOffBandFilePath != null) {
                    Platform.runLater(() -> {
                        var uploadLabel = (Label) contentPane.lookup("#uploadStatusLabel");
                        if (uploadLabel != null) {
                            uploadLabel.setText("Uploading wing image file...");
                        }
                    });

                    uploadFileToFTP(savedOffBandFilePath, totalBytes, bytesUploaded);
                }

                Platform.runLater(() -> {
                    var uploadLabel = (Label) contentPane.lookup("#uploadStatusLabel");
                    var uploadProgressBar = (ProgressBar) contentPane.lookup("#uploadProgressBar");
                    if (uploadLabel != null) {
                        uploadLabel.setText(message("upload.success"));
                    }
                    if (uploadProgressBar != null) {
                        uploadProgressBar.setVisible(false);
                        uploadProgressBar.setManaged(false);
                    }

                    recordSuccessfulUploads();

                    var alert = AlertFactory.info();
                    alert.setTitle(message("upload.success.title"));
                    alert.setHeaderText(message("upload.success.header"));
                    alert.setContentText(message("upload.success.message"));
                    alert.showAndWait();
                    stage.close();
                });
            } catch (Exception e) {
                LOGGER.error("Failed to upload file to BASS2000", e);
                Platform.runLater(() -> {
                    var uploadLabel = (Label) contentPane.lookup("#uploadStatusLabel");
                    var uploadProgressBar = (ProgressBar) contentPane.lookup("#uploadProgressBar");
                    if (uploadLabel != null) {
                        uploadLabel.setText(message("upload.failed"));
                    }
                    if (uploadProgressBar != null) {
                        uploadProgressBar.setVisible(false);
                        uploadProgressBar.setManaged(false);
                    }

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

    private void uploadFileToFTP(File file, long totalBytes, long bytesAlreadyTransferred) throws IOException {
        var config = Configuration.getInstance();
        var ftpUrl = config.getBass2000FtpUrl();
        if (DUMMY_FTP.equals(ftpUrl)) {
            return;
        }
        try {
            var uri = new URI(ftpUrl);
            var ftpHost = uri.getHost();
            var ftpPort = uri.getPort() == -1 ? 21 : uri.getPort();
            var ftpPath = uri.getPath();

            var ftpClient = new FTPClient();
            try {
                ftpClient.connect(ftpHost, ftpPort);

                if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                    throw new IOException("FTP server refused connection");
                }

                if (!ftpClient.login("anonymous", "")) {
                    throw new IOException("FTP login failed");
                }

                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                ftpClient.enterLocalPassiveMode();

                if (!ftpClient.changeWorkingDirectory(ftpPath)) {
                    if (!ftpClient.makeDirectory(ftpPath) || !ftpClient.changeWorkingDirectory(ftpPath)) {
                        throw new IOException("Failed to create or change to directory: " + ftpPath);
                    }
                }

                try (var inputStream = new BufferedInputStream(new ProgressTrackingInputStream(new FileInputStream(file), totalBytes, bytesAlreadyTransferred))) {
                    if (!ftpClient.storeFile(file.getName(), inputStream)) {
                        throw new IOException("Failed to store file: " + file.getName());
                    }
                }

                ftpClient.logout();
            } finally {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid FTP URL: " + ftpUrl, e);
        }
    }

    private Optional<String> generateFileName() {
        try {
            var processParams = generatedBass2000Image.findMetadata(ProcessParams.class).orElseThrow();
            var instrumeId = generateInstrumentId(processParams);
            var spectralRay = processParams.spectrumParams().ray();
            var date = processParams.observationDetails().date();

            var lineName = wavelengthAbbreviationFor(spectralRay.wavelength().angstroms());
            var dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            var prefixDateStr = date.format(DateTimeFormatter.ofPattern("_HH_mm_ss"));

            return Optional.of(String.format("%sZ_%s_%s_%s", prefixDateStr, instrumeId, lineName, dateStr));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> generateOffBandFileName() {
        try {
            var processParams = generatedOffBandImage.findMetadata(ProcessParams.class).orElseThrow();
            var instrumeId = generateInstrumentId(processParams);
            var spectralRay = processParams.spectrumParams().ray();
            var date = processParams.observationDetails().date();

            var offBandName = offBandAbbreviationFor(spectralRay.wavelength().angstroms());
            var dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            var prefixDateStr = date.format(DateTimeFormatter.ofPattern("_HH_mm_ss"));
            var pixelShift = generatedOffBandImage.findMetadata(PixelShift.class)
                    .map(PixelShift::pixelShift)
                    .orElse(0.0);
            var dp = Math.round(pixelShift);
            return Optional.of(String.format("%sZ_dp%d_%s_%s_%s", prefixDateStr, dp, instrumeId, offBandName, dateStr));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String wavelengthAbbreviationFor(double wavelengthAngstroms) {
        return ACCEPTED_SPECTRAL_RAYS.entrySet()
                .stream()
                .filter(e -> Math.abs(e.getKey().wavelength().angstroms() - wavelengthAngstroms) <= TOLERANCE_ANGSTROMS)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private static String offBandAbbreviationFor(double wavelengthAngstroms) {
        return RAY_TO_NAME.keySet()
                .stream()
                .filter(s -> Math.abs(s.wavelength().angstroms() - wavelengthAngstroms) <= TOLERANCE_ANGSTROMS)
                .map(RAY_TO_NAME::get)
                .findFirst()
                .orElseThrow();
    }

    private String generateInstrumentId(ProcessParams params) {
        var observationDetails = params.observationDetails();
        var instrument = observationDetails.instrument();

        var spectrographName = instrument.bass2000Id();
        if ("UNKNOWN".equals(spectrographName)) {
            throw new ProcessingException(instrument.label() + " is not supported. Use either a Sol'Ex or the SHG 700");
        }
        var aperture = observationDetails.aperture();
        var telescopeFocalLength = observationDetails.focalLength();
        var focalReducer = focalReducerCheckbox.isSelected() ? "O" : "N";
        var telescope = observationDetails.telescope();
        var camera = observationDetails.camera();

        var telescopeParts = toBrandAndModel(telescope);
        var cameraParts = toBrandAndModel(camera);
        var mountParts = toBrandAndModel(observationDetails.mount());

        return String.format("%s_%s_%s_%s_%s_%s_%s",
                spectrographName,
                aperture,
                telescopeFocalLength,
                focalReducer,
                telescopeParts,
                cameraParts,
                mountParts
        );
    }

    private static String toBrandAndModel(String mount) {
        var uc = mount.toUpperCase(Locale.US);
        if (uc.length() < 3) {
            throw new ProcessingException("You need to set both the brand and model, separated with a space, with 3 characters each minimally");
        }
        var parts = uc.split("\\s+");
        var brand = uc.substring(0, 3);
        var model = parts[1].replaceAll("[^A-Z0-9]", "");
        return brand + "-" + model;
    }

    private void loadUserImage() {
        Platform.runLater(() -> {
            nextButton.setDisable(true);
            previousButton.setDisable(true);
        });

        BackgroundOperations.async(() -> {
            try {
                var lineCenterImage = getGeometryCorrectedImage(null, 0.0);
                if (lineCenterImage != null) {
                    this.originalImage = lineCenterImage;
                    this.generatedBass2000Image = lineCenterImage.copy();

                    Platform.runLater(() -> {
                        applyCurrentTransformations();
                        if (currentStep == 3) {
                            validateForm();
                        } else {
                            nextButton.setDisable(false);
                        }
                        updateButtons();

                        checkForDuplicateOnStep1();
                    });
                } else {
                    Platform.runLater(() -> {
                        var alert = AlertFactory.error(message("error.image.generation.title"));
                        alert.setTitle(message("error.image.generation.title"));
                        alert.setHeaderText(message("error.image.generation.header"));
                        alert.setContentText(message("error.image.generation.message"));
                        alert.showAndWait();
                        if (currentStep == 3) {
                            validateForm();
                        } else {
                            nextButton.setDisable(false);
                        }
                        updateButtons();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.error(message("error.image.generation.title"));
                    alert.setTitle(message("error.image.generation.title"));
                    alert.setHeaderText(message("error.image.generation.header"));
                    alert.setContentText(MessageFormat.format(message("error.image.generation.with.exception"), e.getMessage()));
                    alert.showAndWait();
                    if (currentStep == 3) {
                        validateForm();
                    } else {
                        nextButton.setDisable(false);
                    }
                    updateButtons();
                });
            }
        });
    }

    private void loadGongReferenceImage() {
        if (gongReferenceImage != null) {
            if (gongImageView != null) {
                gongImageView.setImage(gongReferenceImage);
                gongImageView.resetZoom();
                gongDisplayImage = gongReferenceImage;
            }
            return;
        }

        BackgroundOperations.async(() -> {
            try {
                var observationDate = findProcessParams().observationDetails().date();

                Platform.runLater(() -> {
                    if (gongImageView != null) {
                        showLoadingIndicator();
                    }
                });

                var gongImageUrl = GONG.fetchGongImage(observationDate);

                if (gongImageUrl.isPresent()) {
                    try (var inputStream = gongImageUrl.get().openStream()) {
                        gongReferenceImage = new Image(inputStream);
                        Platform.runLater(() -> {
                            if (gongImageView != null && !gongReferenceImage.isError()) {
                                restoreGongImageView();
                                gongImageView.setImage(gongReferenceImage);
                                gongImageView.resetZoom();
                                gongDisplayImage = gongReferenceImage;
                            } else {
                                loadFallbackGongImage();
                            }
                        });
                    }
                } else {
                    Platform.runLater(this::loadFallbackGongImage);
                }
            } catch (Exception e) {
                Platform.runLater(this::loadFallbackGongImage);
            }
        });
    }

    private void showLoadingIndicator() {
        if (gongImageView != null && gongImageContainer != null) {
            var loadingContainer = new VBox(10);
            loadingContainer.setStyle("-fx-alignment: center;");

            var pendulum = new Label("●");
            pendulum.setStyle("-fx-font-size: 24px; -fx-text-fill: #2196F3;");

            // Create pendulum swing animation with a much larger arc
            var pendulumSwing = new RotateTransition(Duration.seconds(1.2), pendulum);
            pendulumSwing.setFromAngle(-45);
            pendulumSwing.setToAngle(45);
            pendulumSwing.setCycleCount(Animation.INDEFINITE);
            pendulumSwing.setAutoReverse(true);
            pendulumSwing.play();

            var loadingTextLabel = new Label(message("gong.loading"));
            loadingTextLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");

            loadingContainer.getChildren().addAll(pendulum, loadingTextLabel);

            var loadingWrapper = new HBox();
            loadingWrapper.setPrefWidth(IMAGE_VIEW_SIZE);
            loadingWrapper.setPrefHeight(IMAGE_VIEW_SIZE);
            loadingWrapper.setMaxWidth(IMAGE_VIEW_SIZE);
            loadingWrapper.setMaxHeight(IMAGE_VIEW_SIZE);
            loadingWrapper.setMinWidth(IMAGE_VIEW_SIZE);
            loadingWrapper.setMinHeight(IMAGE_VIEW_SIZE);
            loadingWrapper.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-alignment: center;");
            loadingWrapper.getChildren().add(loadingContainer);

            if (gongImageContainer.getChildren().size() > 1) {
                gongImageContainer.getChildren().set(1, loadingWrapper);
            }
        }
    }

    private void restoreGongImageView() {
        if (gongImageView != null && gongImageContainer != null) {
            if (gongImageContainer.getChildren().size() > 1) {
                var gongImageWrapper = new HBox();
                gongImageWrapper.setPrefWidth(IMAGE_VIEW_SIZE);
                gongImageWrapper.setPrefHeight(IMAGE_VIEW_SIZE);
                gongImageWrapper.setMaxWidth(IMAGE_VIEW_SIZE);
                gongImageWrapper.setMaxHeight(IMAGE_VIEW_SIZE);
                gongImageWrapper.setMinWidth(IMAGE_VIEW_SIZE);
                gongImageWrapper.setMinHeight(IMAGE_VIEW_SIZE);
                gongImageWrapper.setStyle("-fx-alignment: center;");
                gongImageWrapper.getChildren().add(gongImageView);
                gongImageContainer.getChildren().set(1, gongImageWrapper);
            }
        }
    }

    private void loadFallbackGongImage() {
        try {
            gongReferenceImage = new Image("");
            if (gongImageView != null) {
                restoreGongImageView();
                gongImageView.setImage(gongReferenceImage);
                gongImageView.resetZoom();
                gongDisplayImage = gongReferenceImage;
            }
        } catch (Exception e) {
        }
    }

    private void updateUserImageDisplay() {
        if (userImageView != null && generatedBass2000Image != null) {
            BackgroundOperations.async(() -> {
                try {
                    var displayImage = generatedBass2000Image.copy();

                    displayImage = scaling.rescaleToRadius(displayImage, 225, 512, 512);

                    LinearStrechingStrategy.DEFAULT.stretch(displayImage);
                    if (displayImage instanceof ImageWrapper32 mono) {
                        displayImage = RGBImage.toRGB(mono);
                    }
                    var writableImage = WritableImageSupport.asWritable(displayImage);
                    Platform.runLater(() -> {
                        userImageView.setImage(writableImage);
                        userDisplayImage = writableImage;
                        userImageView.resetZoom();

                        if (currentComparisonMode == ComparisonMode.BLINK && blinkImageView != null && showingUserImage) {
                            blinkImageView.setImage(writableImage);
                            blinkImageView.resetZoom();
                        }

                        if (currentComparisonMode == ComparisonMode.BLEND && opacityUserImageView != null) {
                            opacityUserImageView.setImage(writableImage);
                            opacityUserImageView.resetZoom();
                        }

                        if (fullscreenImageView != null && fullscreenShowingUserImage) {
                            fullscreenImageView.setImage(writableImage);
                        }

                        if (fullscreenUserImageView != null) {
                            fullscreenUserImageView.setImage(writableImage);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        var alert = AlertFactory.error(message("error.image.display.title"));
                        alert.setTitle(message("error.image.display.title"));
                        alert.setHeaderText(message("error.image.display.header"));
                        alert.setContentText(MessageFormat.format(message("error.image.display.message"), e.getMessage()));
                        alert.showAndWait();
                    });
                }
            });
        }
    }

    private void applyHorizontalFlip() {
        horizontalFlip = !horizontalFlip;
        applyCurrentTransformations();
    }

    private void applyVerticalFlip() {
        verticalFlip = !verticalFlip;
        applyCurrentTransformations();
    }

    private void applyRotation(int degrees) {
        rotation = (rotation + degrees) % 360;
        if (rotation < 0) {
            rotation += 360;
        }
        applyCurrentTransformations();
    }

    private void resetTransformations() {
        rotation = 0;
        horizontalFlip = false;
        verticalFlip = false;
        angleAdjustment = 0.0;
        generatedBass2000Image = originalImage.copy();

        if (originalOffBandImage != null) {
            generatedOffBandImage = originalOffBandImage.copy();
        }

        applyCurrentTransformations();
    }

    private void toggleComparisonMode() {
        switch (currentComparisonMode) {
            case NORMAL -> enterBlinkMode();
            case BLINK -> {
                exitComparisonMode();
                enterBlendMode();
            }
            case BLEND -> exitComparisonMode();
        }
        updateComparisonUI();
    }

    private String getComparisonModeButtonText() {
        return switch (currentComparisonMode) {
            case NORMAL -> message("mode.button.blink");
            case BLINK -> message("mode.button.blend");
            case BLEND -> message("mode.button.normal");
        };
    }

    private String getFullscreenComparisonModeButtonText() {
        return switch (currentComparisonMode) {
            case NORMAL, BLINK -> message("mode.button.blend");
            case BLEND -> message("mode.button.blink");
        };
    }

    private void updateComparisonUI() {
        if (comparisonModeButton != null) {
            comparisonModeButton.setText(getComparisonModeButtonText());
        }

        if (comparisonModeLabel != null) {
            switch (currentComparisonMode) {
                case NORMAL -> comparisonModeLabel.setText(message("blink.duration"));
                case BLINK -> comparisonModeLabel.setText(message("blink.duration"));
                case BLEND -> comparisonModeLabel.setText(message("blend.percent"));
            }
        }

        if (comparisonModeSlider != null) {
            switch (currentComparisonMode) {
                case NORMAL, BLINK -> {
                    comparisonModeSlider.setMin(0.0);
                    comparisonModeSlider.setMax(100.0);
                    comparisonModeSlider.setMajorTickUnit(25.0);
                    comparisonModeSlider.setValue((blinkDurationMs - 500.0) / 4500.0 * 100.0);
                }
                case BLEND -> {
                    comparisonModeSlider.setMin(0.0);
                    comparisonModeSlider.setMax(100.0);
                    comparisonModeSlider.setMajorTickUnit(25.0);
                    comparisonModeSlider.setValue(opacityValue);
                }
            }
        }
    }

    private void enterBlinkMode() {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        currentComparisonMode = ComparisonMode.BLINK;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setVisible(true);
        }

        blinkContainer = new VBox(5);
        blinkContainer.setStyle("-fx-alignment: center; -fx-background-color: #f0f0f0; -fx-padding: 6;");

        var blinkLabel = new Label(message("blink.mode"));
        blinkLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        blinkImageView = new ZoomableImageView();
        blinkImageView.setPrefWidth(IMAGE_VIEW_SIZE);
        blinkImageView.setPrefHeight(IMAGE_VIEW_SIZE);
        blinkImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        blinkImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        blinkImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        var imageWrapper = new HBox();
        imageWrapper.setStyle("-fx-alignment: center;");
        imageWrapper.getChildren().add(blinkImageView);

        blinkContainer.getChildren().addAll(blinkLabel, imageWrapper);

        var contentVBox = (VBox) originalImageComparisonBox.getParent();
        var index = contentVBox.getChildren().indexOf(originalImageComparisonBox);
        contentVBox.getChildren().set(index, blinkContainer);

        startBlinkAnimation();
    }

    private void exitComparisonMode() {
        currentComparisonMode = ComparisonMode.NORMAL;
        showingUserImage = true;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setVisible(false);
        }

        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }

        if (blinkContainer != null && originalImageComparisonBox != null) {
            var contentVBox = (VBox) blinkContainer.getParent();
            var index = contentVBox.getChildren().indexOf(blinkContainer);
            contentVBox.getChildren().set(index, originalImageComparisonBox);
        }

        if (blendContainer != null && originalImageComparisonBox != null) {
            var contentVBox = (VBox) blendContainer.getParent();
            var index = contentVBox.getChildren().indexOf(blendContainer);
            contentVBox.getChildren().set(index, originalImageComparisonBox);
        }

        blinkContainer = null;
        blinkImageView = null;
        blendContainer = null;
        opacityStackPane = null;
        opacityUserImageView = null;
        opacityGongImageView = null;
    }

    private void startBlinkAnimation() {
        if (blinkImageView == null) {
            return;
        }

        showingUserImage = true;
        blinkImageView.setImage(userDisplayImage);
        blinkImageView.resetZoom();

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.millis(blinkDurationMs), e -> {
                    if (showingUserImage) {
                        blinkImageView.setImage(gongDisplayImage);
                        showingUserImage = false;
                    } else {
                        blinkImageView.setImage(userDisplayImage);
                        showingUserImage = true;
                    }
                    blinkImageView.resetZoom();
                })
        );
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
    }

    private void restartBlinkAnimation() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
        }
        startBlinkAnimation();
    }

    private void enterBlendMode() {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        currentComparisonMode = ComparisonMode.BLEND;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setVisible(true);
        }

        blendContainer = new VBox(5);
        blendContainer.setStyle("-fx-alignment: center; -fx-background-color: #f0f0f0; -fx-padding: 6;");

        var opacityLabel = new Label(message("blend.mode"));
        opacityLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        opacityStackPane = new StackPane();
        opacityStackPane.setPrefWidth(IMAGE_VIEW_SIZE);
        opacityStackPane.setPrefHeight(IMAGE_VIEW_SIZE);
        opacityStackPane.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        opacityGongImageView = new ZoomableImageView();
        opacityGongImageView.setPrefWidth(IMAGE_VIEW_SIZE);
        opacityGongImageView.setPrefHeight(IMAGE_VIEW_SIZE);
        opacityGongImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        opacityGongImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        opacityGongImageView.setImage(gongDisplayImage);

        opacityUserImageView = new ZoomableImageView();
        opacityUserImageView.setPrefWidth(IMAGE_VIEW_SIZE);
        opacityUserImageView.setPrefHeight(IMAGE_VIEW_SIZE);
        opacityUserImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        opacityUserImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        opacityUserImageView.setImage(userDisplayImage);
        opacityUserImageView.setOpacity(opacityValue / 100.0);

        opacityStackPane.getChildren().addAll(opacityGongImageView, opacityUserImageView);

        var imageWrapper = new HBox();
        imageWrapper.setStyle("-fx-alignment: center;");
        imageWrapper.getChildren().add(opacityStackPane);

        blendContainer.getChildren().addAll(opacityLabel, imageWrapper);

        var contentVBox = (VBox) originalImageComparisonBox.getParent();
        var index = contentVBox.getChildren().indexOf(originalImageComparisonBox);
        contentVBox.getChildren().set(index, blendContainer);

        syncZoomBetweenOpacityViews();
    }

    private void syncZoomBetweenOpacityViews() {
        if (opacityUserImageView != null && opacityGongImageView != null) {
            var userScrollPane = opacityUserImageView.getScrollPane();
            var gongScrollPane = opacityGongImageView.getScrollPane();

            userScrollPane.hvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (!gongScrollPane.hvalueProperty().getValue().equals(newVal)) {
                    gongScrollPane.setHvalue(newVal.doubleValue());
                }
            });
            userScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (!gongScrollPane.vvalueProperty().getValue().equals(newVal)) {
                    gongScrollPane.setVvalue(newVal.doubleValue());
                }
            });

            gongScrollPane.hvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (!userScrollPane.hvalueProperty().getValue().equals(newVal)) {
                    userScrollPane.setHvalue(newVal.doubleValue());
                }
            });
            gongScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                if (!userScrollPane.vvalueProperty().getValue().equals(newVal)) {
                    userScrollPane.setVvalue(newVal.doubleValue());
                }
            });
        }
    }

    private void openFullscreenComparison() {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        if (fullscreenStage != null) {
            fullscreenStage.toFront();
            return;
        }

        if (currentComparisonMode == ComparisonMode.NORMAL) {
            currentComparisonMode = ComparisonMode.BLINK;
            updateComparisonUI();
        }

        fullscreenStage = new Stage();
        fullscreenStage.initModality(APPLICATION_MODAL);
        fullscreenStage.setMaximized(true);

        var root = new VBox(10);
        root.setStyle("-fx-padding: 10; -fx-background-color: black;");

        var centeringPane = new StackPane();
        centeringPane.setStyle("-fx-alignment: center; -fx-background-color: black;");

        if (currentComparisonMode == ComparisonMode.BLEND) {
            fullscreenBlendPane = new StackPane();
            fullscreenBlendPane.setStyle("-fx-alignment: center;");

            fullscreenGongImageView = new ImageView();
            fullscreenGongImageView.setPreserveRatio(true);
            fullscreenGongImageView.setSmooth(true);
            fullscreenGongImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            fullscreenGongImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));
            fullscreenGongImageView.setImage(gongDisplayImage);

            fullscreenUserImageView = new ImageView();
            fullscreenUserImageView.setPreserveRatio(true);
            fullscreenUserImageView.setSmooth(true);
            fullscreenUserImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            fullscreenUserImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));
            fullscreenUserImageView.setImage(userDisplayImage);
            fullscreenUserImageView.setOpacity(opacityValue / 100.0);

            fullscreenBlendPane.getChildren().addAll(fullscreenGongImageView, fullscreenUserImageView);
            centeringPane.getChildren().add(fullscreenBlendPane);
        } else {
            var plainImageView = new ImageView();
            plainImageView.setPreserveRatio(true);
            plainImageView.setSmooth(true);
            plainImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            plainImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));

            centeringPane.getChildren().add(plainImageView);
            fullscreenImageView = plainImageView;
        }
        VBox.setVgrow(centeringPane, Priority.ALWAYS);

        var controlsPanel = createFullscreenControlsPanel();

        root.getChildren().addAll(centeringPane, controlsPanel);

        var scene = newScene(root);
        fullscreenStage.setScene(scene);

        fullscreenStage.setOnCloseRequest(e -> {
            e.consume();
            closeFullscreenComparison();
        });

        scene.setOnKeyReleased(event -> {
            if (event.getCode() == ESCAPE) {
                event.consume();
                closeFullscreenComparison();
            }
        });

        fullscreenStage.setOnCloseRequest(e -> closeFullscreenComparison());
        fullscreenStage.show();
        Platform.runLater(() -> {
            root.requestFocus();
            fullscreenStage.toFront();
        });

        if (currentComparisonMode == ComparisonMode.BLINK) {
            startFullscreenBlinkAnimation();
        }
    }

    private HBox createFullscreenControlsPanel() {
        var controlsPanel = new HBox(15);
        controlsPanel.setStyle("-fx-alignment: center; -fx-padding: 10; -fx-background-color: #333;");

        var closeButton = new Button(message("button.close.fullscreen"));
        closeButton.getStyleClass().add("dark-close-button");
        closeButton.setOnAction(e -> closeFullscreenComparison());

        fullscreenComparisonModeButton = new Button(getComparisonModeButtonText());
        fullscreenComparisonModeButton.getStyleClass().add("dark-button");
        fullscreenComparisonModeButton.setOnAction(e -> toggleFullscreenComparisonMode());

        var horizontalFlipButton = new Button("⇄ " + message("orientation.button.horizontal.flip"));
        horizontalFlipButton.getStyleClass().add("dark-button");
        horizontalFlipButton.setOnAction(e -> applyHorizontalFlip());

        var verticalFlipButton = new Button("⇅ " + message("orientation.button.vertical.flip"));
        verticalFlipButton.getStyleClass().add("dark-button");
        verticalFlipButton.setOnAction(e -> applyVerticalFlip());

        var rotateLeftButton = new Button("↶ " + message("orientation.button.rotate.left"));
        rotateLeftButton.getStyleClass().add("dark-button");
        rotateLeftButton.setOnAction(e -> applyRotation(-90));

        var rotateRightButton = new Button("↷ " + message("orientation.button.rotate.right"));
        rotateRightButton.getStyleClass().add("dark-button");
        rotateRightButton.setOnAction(e -> applyRotation(90));

        var resetButton = new Button(message("orientation.button.reset"));
        resetButton.getStyleClass().add("dark-button");
        resetButton.setOnAction(e -> resetTransformations());

        var angleLabel = new Label(message("fine.tilt.adjust") + ":");
        angleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        var angleSlider = new Slider(-2.0, 2.0, angleAdjustment);
        angleSlider.getStyleClass().add("dark-slider");
        angleSlider.setShowTickLabels(true);
        angleSlider.setShowTickMarks(true);
        angleSlider.setMajorTickUnit(1.0);
        angleSlider.setMinorTickCount(4);
        angleSlider.setPrefWidth(200);
        angleSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            angleAdjustment = newValue.doubleValue();
            applyCurrentTransformations();
        });

        var comparisonSliderLabel = new Label();
        comparisonSliderLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        var comparisonSlider = new Slider();
        comparisonSlider.getStyleClass().add("dark-slider");
        comparisonSlider.setShowTickLabels(true);
        comparisonSlider.setShowTickMarks(true);
        comparisonSlider.setPrefWidth(200);

        if (currentComparisonMode == ComparisonMode.BLEND) {
            comparisonSliderLabel.setText(message("blend.percent") + ":");
            comparisonSlider.setMin(0.0);
            comparisonSlider.setMax(100.0);
            comparisonSlider.setMajorTickUnit(25.0);
            comparisonSlider.setMinorTickCount(4);
            comparisonSlider.setValue(opacityValue);
            comparisonSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                opacityValue = newValue.doubleValue();
                if (fullscreenUserImageView != null) {
                    fullscreenUserImageView.setOpacity(opacityValue / 100.0);
                }
            });
        } else {
            comparisonSliderLabel.setText(message("blink.duration") + ":");
            comparisonSlider.setMin(500.0);
            comparisonSlider.setMax(5000.0);
            comparisonSlider.setMajorTickUnit(1000.0);
            comparisonSlider.setMinorTickCount(4);
            comparisonSlider.setValue(blinkDurationMs);
            comparisonSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                blinkDurationMs = newValue.doubleValue();
                if (fullscreenBlinkTimeline != null) {
                    restartFullscreenBlinkAnimation();
                }
            });
        }

        var leftControls = new HBox(10);
        leftControls.setStyle("-fx-alignment: center-left;");
        leftControls.getChildren().addAll(horizontalFlipButton, verticalFlipButton, rotateLeftButton, rotateRightButton, resetButton);

        var centerControls = new HBox(10);
        centerControls.setStyle("-fx-alignment: center;");
        centerControls.getChildren().addAll(angleLabel, angleSlider, comparisonSliderLabel, comparisonSlider);

        var rightControls = new HBox(10);
        rightControls.setStyle("-fx-alignment: center-right;");
        rightControls.getChildren().addAll(fullscreenComparisonModeButton, closeButton);

        HBox.setHgrow(leftControls, Priority.NEVER);
        HBox.setHgrow(centerControls, Priority.ALWAYS);
        HBox.setHgrow(rightControls, Priority.NEVER);

        controlsPanel.getChildren().addAll(leftControls, centerControls, rightControls);

        return controlsPanel;
    }

    private void toggleFullscreenComparisonMode() {
        switch (currentComparisonMode) {
            case NORMAL, BLINK -> {
                currentComparisonMode = ComparisonMode.BLEND;
                rebuildFullscreenUI();
            }
            case BLEND -> {
                currentComparisonMode = ComparisonMode.BLINK;
                rebuildFullscreenUI();
            }
        }
        updateComparisonUI();
        if (fullscreenComparisonModeButton != null) {
            fullscreenComparisonModeButton.setText(getFullscreenComparisonModeButtonText());
        }
    }

    private void rebuildFullscreenUI() {
        if (fullscreenStage == null) return;

        var scene = fullscreenStage.getScene();
        var root = (VBox) scene.getRoot();
        var centeringPane = (StackPane) root.getChildren().get(0);

        centeringPane.getChildren().clear();

        if (fullscreenBlinkTimeline != null) {
            fullscreenBlinkTimeline.stop();
            fullscreenBlinkTimeline = null;
        }

        if (currentComparisonMode == ComparisonMode.BLEND) {
            fullscreenBlendPane = new StackPane();
            fullscreenBlendPane.setStyle("-fx-alignment: center;");

            fullscreenGongImageView = new ImageView();
            fullscreenGongImageView.setPreserveRatio(true);
            fullscreenGongImageView.setSmooth(true);
            fullscreenGongImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            fullscreenGongImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));
            fullscreenGongImageView.setImage(gongDisplayImage);

            fullscreenUserImageView = new ImageView();
            fullscreenUserImageView.setPreserveRatio(true);
            fullscreenUserImageView.setSmooth(true);
            fullscreenUserImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            fullscreenUserImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));
            fullscreenUserImageView.setImage(userDisplayImage);
            fullscreenUserImageView.setOpacity(opacityValue / 100.0);

            fullscreenBlendPane.getChildren().addAll(fullscreenGongImageView, fullscreenUserImageView);
            centeringPane.getChildren().add(fullscreenBlendPane);

            fullscreenImageView = null;
        } else {
            var plainImageView = new ImageView();
            plainImageView.setPreserveRatio(true);
            plainImageView.setSmooth(true);
            plainImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
            plainImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));

            centeringPane.getChildren().add(plainImageView);
            fullscreenImageView = plainImageView;

            fullscreenBlendPane = null;
            fullscreenUserImageView = null;
            fullscreenGongImageView = null;

            if (currentComparisonMode == ComparisonMode.BLINK) {
                startFullscreenBlinkAnimation();
            } else {
                fullscreenImageView.setImage(userDisplayImage);
            }
        }

        rebuildFullscreenControls();
    }

    private void rebuildFullscreenControls() {
        if (fullscreenStage == null) return;

        var scene = fullscreenStage.getScene();
        var root = (VBox) scene.getRoot();
        var oldControlsPanel = (HBox) root.getChildren().get(1);

        var newControlsPanel = createFullscreenControlsPanel();
        root.getChildren().set(1, newControlsPanel);
    }

    private void startFullscreenBlinkAnimation() {
        if (fullscreenImageView == null) {
            return;
        }

        fullscreenShowingUserImage = true;
        fullscreenImageView.setImage(userDisplayImage);

        fullscreenBlinkTimeline = new Timeline(new KeyFrame(Duration.millis(blinkDurationMs), e -> {
            if (fullscreenShowingUserImage) {
                fullscreenImageView.setImage(gongDisplayImage);
                fullscreenShowingUserImage = false;
            } else {
                fullscreenImageView.setImage(userDisplayImage);
                fullscreenShowingUserImage = true;
            }
        }));
        fullscreenBlinkTimeline.setCycleCount(Animation.INDEFINITE);
        fullscreenBlinkTimeline.play();
    }

    private void restartFullscreenBlinkAnimation() {
        if (fullscreenBlinkTimeline != null) {
            fullscreenBlinkTimeline.stop();
        }
        startFullscreenBlinkAnimation();
    }

    private void closeFullscreenComparison() {
        if (fullscreenBlinkTimeline != null) {
            fullscreenBlinkTimeline.stop();
            fullscreenBlinkTimeline = null;
        }

        if (fullscreenStage != null) {
            fullscreenStage.close();
            fullscreenStage = null;
        }

        fullscreenImageView = null;
        fullscreenBlendPane = null;
        fullscreenUserImageView = null;
        fullscreenGongImageView = null;

        currentComparisonMode = ComparisonMode.NORMAL;
        exitComparisonMode();
        updateComparisonUI();
        
        mainAngleSlider.setValue(angleAdjustment);
    }

    private void applyCurrentTransformations() {
        if (originalImage == null) {
            return;
        }

        BackgroundOperations.async(() -> {
            try {
                var processParams = findProcessParams();
                var observationDate = processParams.observationDetails().date();
                var solarParams = SolarParametersUtils.computeSolarParams(observationDate.toLocalDateTime());
                var pAngle = solarParams.p();

                generatedBass2000Image = applyTransformationsToImage(originalImage, pAngle);

                if (originalOffBandImage != null) {
                    generatedOffBandImage = applyTransformationsToImage(originalOffBandImage, pAngle);
                }

                Platform.runLater(() -> {
                    updateUserImageDisplay();
                    if (currentStep == 2) {
                        validateBass2000Image();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    var alert = AlertFactory.error(message("error.transformation.title"));
                    alert.setTitle(message("error.transformation.title"));
                    alert.setHeaderText(message("error.transformation.header"));
                    alert.setContentText(MessageFormat.format(message("error.transformation.message"), e.getMessage()));
                    alert.showAndWait();
                });
            }
        });
    }

    private ImageWrapper applyTransformationsToImage(ImageWrapper originalImage, double pAngle) {
        var transformedImage = originalImage.copy();

        if (horizontalFlip) {
            transformedImage = Corrector.rotate(transformedImage, Math.PI, false);
            transformedImage = Corrector.verticalFlip(transformedImage);
        }

        if (verticalFlip) {
            transformedImage = Corrector.verticalFlip(transformedImage);
        }

        if (rotation != 0) {
            var rotationRadians = Math.toRadians(rotation);
            transformedImage = Corrector.rotate(transformedImage, rotationRadians, false);
        }

        if (angleAdjustment != 0) {
            transformedImage = Corrector.rotate(transformedImage, Math.toRadians(angleAdjustment), false);
        }

        return Corrector.rotate(transformedImage, pAngle, false);
    }

    private void finishWizard() {
        if (currentComparisonMode != ComparisonMode.NORMAL) {
            exitComparisonMode();
        }
        if (fullscreenStage != null) {
            closeFullscreenComparison();
        }
        stage.close();
    }

    private ImageWrapper getGeometryCorrectedImage(Double reference, double shift) {
        var executor = mainController.getScriptExecutor();
        var ref = reference != null ? String.format(Locale.US, ", ref:%.3f", reference) : "";
        executor.execute(String.format(Locale.US, """
            [internal]
            result=autocrop2(img(-a2px(a: %.3f%s));1.2)
            [outputs]
            """, shift, ref), ImageMathScriptExecutor.SectionKind.SINGLE);
        var result = executor.getVariable("result");

        if (result.isPresent() && result.get() instanceof ImageWrapper image) {
            return image.unwrapToMemory();
        }
        return null;
    }

    private void validateBass2000Image() {
        BackgroundOperations.async(() -> {
            try {
                var lineCenterImage = generatedBass2000Image != null ? generatedBass2000Image : originalImage;
                var bass2000Images = new Bass2000Images(lineCenterImage, generatedOffBandImage);
                var validationResult = bass2000Images.validate();

                Platform.runLater(() -> {
                    updateValidationUI(validationResult);

                    if (currentStep == 2) {
                        nextButton.setDisable(!validationResult.isValid());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    var warningLabel = contentPane.lookup("#validationWarning");
                    if (warningLabel != null) {
                        warningLabel.setVisible(true);
                        warningLabel.setManaged(true);
                    }

                    if (currentStep == 2) {
                        nextButton.setDisable(true);
                    }
                });
            }
        });
    }

    private void updateValidationUI(ValidationResult validationResult) {
        var warningLabel = contentPane.lookup("#validationWarning");
        if (warningLabel instanceof Label label) {
            if (validationResult.isValid()) {
                label.setVisible(false);
                label.setManaged(false);
            } else {
                var messageKey = switch (validationResult.error()) {
                    case PARTIAL_DISK -> "bass2000.validation.partial.disk.message";
                    case JAGGED_EDGES_CORRECTION -> "bass2000.validation.jagged.edges.message";
                };
                label.setText(message(messageKey));
                label.setVisible(true);
                label.setManaged(true);
            }
        }
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }

    private void createUpdatedProcessParams() {
        var processParams = findProcessParams();
        var currentObservationDetails = processParams.observationDetails();

        var observerName = observerNameField.getText().trim();
        var observerEmail = observerEmailField.getText().trim();
        var coordinates = new DoublePair(
                Double.parseDouble(siteLatitudeField.getText().trim()),
                Double.parseDouble(siteLongitudeField.getText().trim())
        );

        var aperture = Integer.parseInt(apertureField.getText().trim());
        var stop = stopField.getText().trim().isEmpty() ? null : Integer.parseInt(stopField.getText().trim());
        var binning = Integer.parseInt(binningField.getValue().trim());
        var pixelSize = Double.parseDouble(pixelSizeField.getText().trim());

        var telescope = telescopeNameField.getText().trim();
        var mount = mountNameField.getText().trim();
        var telescopeFocalLength = Integer.parseInt(telescopeFocalLengthField.getText().trim());
        var camera = cameraNameField.getText().trim();
        var energyRejectionFilter = erfField.getText().trim().isEmpty() ? null : erfField.getText().trim();

        var spectrographName = spectrographNameField.getText().trim();
        var collimatorFocalLength = Double.parseDouble(collimatorFocalLengthField.getText().trim());
        var cameraLensFocalLength = Double.parseDouble(cameraLensFocalLengthField.getText().trim());
        var gratingDensity = Integer.parseInt(gratingDensityField.getText().trim());
        var order = Integer.parseInt(orderField.getText().trim());
        var totalAngle = Double.parseDouble(totalAngleField.getText().trim());
        var slitWidth = Double.parseDouble(slitWidthField.getText().trim());
        var slitHeight = Double.parseDouble(slitHeightField.getText().trim());

        var newSpectroHeliograph = new SpectroHeliograph(
                spectrographName,
                totalAngle,
                cameraLensFocalLength,
                collimatorFocalLength,
                gratingDensity,
                order,
                slitWidth,
                slitHeight,
                currentObservationDetails.instrument().spectrumVFlip()
        );

        var updatedObservationDetails = new ObservationDetails(
                observerName,
                observerEmail,
                newSpectroHeliograph,
                telescope,
                mount,
                telescopeFocalLength,
                aperture,
                stop,
                energyRejectionFilter,
                coordinates,
                currentObservationDetails.date(),
                camera,
                binning,
                pixelSize,
                currentObservationDetails.forceCamera(),
                currentObservationDetails.showCoordinatesInDetails(),
                currentObservationDetails.altAzMode()
        );

        var selectedSpectralRay = wavelengthField.getValue();
        var updatedSpectrumParams = processParams
                .withObservationDetails(updatedObservationDetails)
                .withSpectrumParams(processParams.spectrumParams().withRay(selectedSpectralRay));
        var instrumentId = generateInstrumentId(updatedSpectrumParams);
        updatedSpectrumParams = updatedSpectrumParams.withObservationDetails(
                updatedObservationDetails.withInstrument(newSpectroHeliograph.withLabel(instrumentId))
        );
        generatedBass2000Image.metadata().put(ProcessParams.class, updatedSpectrumParams);
        if (generatedOffBandImage != null) {
            var offbandRay = RAY_TO_WING.get(updatedSpectrumParams.spectrumParams().ray());
            if (offbandRay != null) {
                var updatedOffBandParams = updatedSpectrumParams.withSpectrumParams(
                        updatedSpectrumParams.spectrumParams().withRay(offbandRay)
                );
                generatedOffBandImage.metadata().put(ProcessParams.class, updatedOffBandParams);
                generatedOffBandImage.metadata().put(Bass2000Compatibility.class, new Bass2000Compatibility(offbandRay.wavelength().angstroms()));
            }
        }
    }

    private enum ValidationError {
        PARTIAL_DISK,
        JAGGED_EDGES_CORRECTION
    }

    private record ValidationResult(boolean isValid, ValidationError error) {
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(ValidationError error) {
            return new ValidationResult(false, error);
        }
    }

    private record Bass2000Images(ImageWrapper lineCenter, ImageWrapper offBand) {
        ValidationResult validate() {
            var jaggingValidation = validateJaggedEdgesCorrection();
            if (!jaggingValidation.isValid()) {
                return jaggingValidation;
            }

            return validatePartialDisk();
        }

        private ValidationResult validateJaggedEdgesCorrection() {
            var processParams = lineCenter.findMetadata(ProcessParams.class).orElse(null);
            if (processParams != null) {
                var jaggingEnabled = processParams.enhancementParams().jaggingCorrectionParams().enabled();
                if (jaggingEnabled) {
                    return ValidationResult.invalid(ValidationError.JAGGED_EDGES_CORRECTION);
                }
            }
            return ValidationResult.valid();
        }

        private ValidationResult validatePartialDisk() {
            return lineCenter.findMetadata(Ellipse.class)
                    .map(e -> {
                        var bb = e.boundingBox();
                        var refCoords = lineCenter.findMetadata(ReferenceCoords.class).orElse(null);
                        if (refCoords == null) {
                            return ValidationResult.valid();
                        }
                        var sourceInfo = lineCenter.findMetadata(SourceInfo.class).orElse(null);
                        if (sourceInfo == null) {
                            return ValidationResult.valid();
                        }
                        var x0 = bb.a();
                        var x1 = bb.b();
                        var y0 = bb.c();
                        var y1 = bb.d();
                        var p1 = refCoords.determineOriginalCoordinates(new Point2D(x0, y0), ReferenceCoords.NO_LIMIT);
                        var p2 = refCoords.determineOriginalCoordinates(new Point2D(x1, y1), ReferenceCoords.NO_LIMIT);
                        if (p1.x() > p2.x() || p1.y() > p2.y()) {
                            var temp = p1;
                            p1 = p2;
                            p2 = temp;
                        }
                        var width = sourceInfo.width();
                        var height = sourceInfo.height();
                        var isFullDisk = p1.x() >= 0 && p1.y() >= 0 && p1.x() < width && p2.x() < width &&
                                p1.y() < height && p2.y() < height;

//                        return isFullDisk ? ValidationResult.valid() : ValidationResult.invalid(ValidationError.PARTIAL_DISK);
                        // there seem to be some cases where the image is detected as partial disk, but it is actually full disk
                        // so disable the validation for now
                        return ValidationResult.valid();
                    }).orElse(ValidationResult.invalid(ValidationError.PARTIAL_DISK));
        }
    }

    private class ProgressTrackingInputStream extends FilterInputStream {
        private final long totalBytes;
        private final long bytesAlreadyTransferred;
        private final ProgressBar progressBar;
        private long bytesRead = 0;

        public ProgressTrackingInputStream(InputStream in, long totalBytes, long bytesAlreadyTransferred) {
            super(in);
            this.totalBytes = totalBytes;
            this.bytesAlreadyTransferred = bytesAlreadyTransferred;
            this.progressBar = (ProgressBar) contentPane.lookup("#uploadProgressBar");
        }

        @Override
        public int read() throws IOException {
            var result = super.read();
            if (result != -1) {
                bytesRead++;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            var result = super.read(b);
            if (result != -1) {
                bytesRead += result;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            var result = super.read(b, off, len);
            if (result != -1) {
                bytesRead += result;
                updateProgress();
            }
            return result;
        }

        private void updateProgress() {
            if (totalBytes > 0) {
                var progress = (double) (bytesAlreadyTransferred + bytesRead) / totalBytes;
                Platform.runLater(() -> progressBar.setProgress(Math.min(progress, 1.0)));
            }
        }
    }


    private void checkForDuplicateOnStep1() {
        BackgroundOperations.async(() -> {
            var processParams = originalImage.findMetadata(ProcessParams.class);
            if (processParams.isPresent()) {
                var pp = processParams.get();
                var spectralRay = pp.spectrumParams().ray();
                if (spectralRay != null) {
                    var wavelengthAngstroms = spectralRay.wavelength().angstroms();
                    var observationDate = pp.observationDetails().date().toLocalDate();
                    var duplicate = Bass2000UploadHistoryService.getInstance().checkForDuplicateUpload(observationDate, wavelengthAngstroms);
                    Platform.runLater(() -> updateStep1DuplicateWarning(duplicate.orElse(null)));
                }
            }
        });
    }

    private void checkForDuplicateOnStep5() {
        BackgroundOperations.async(() -> {
            var selectedWavelength = wavelengthField.getValue();
            if (selectedWavelength != null) {
                var observationDate = findProcessParams().observationDetails().date().toLocalDate();
                var duplicate = Bass2000UploadHistoryService.getInstance().checkForDuplicateUpload(observationDate, selectedWavelength.wavelength().angstroms());
                Platform.runLater(() -> updateStep5DuplicateWarning(duplicate.orElse(null)));
            }
        });
    }

    private void updateStep1DuplicateWarning(Bass2000UploadHistoryService.UploadRecord duplicate) {
        if (step1DuplicateWarningLabel == null) {
            return;
        }

        if (duplicate != null) {
            step1DuplicateWarningLabel.setText(MessageFormat.format(message("duplicate.warning"), duplicate.sourceFilename()));
            step1DuplicateWarningLabel.setVisible(true);
            step1DuplicateWarningLabel.setManaged(true);
        } else {
            step1DuplicateWarningLabel.setVisible(false);
            step1DuplicateWarningLabel.setManaged(false);
        }
    }

    private void updateStep5DuplicateWarning(Bass2000UploadHistoryService.UploadRecord duplicate) {
        if (step5DuplicateWarningLabel == null) {
            return;
        }

        if (duplicate != null) {
            step5DuplicateWarningLabel.setText(MessageFormat.format(message("duplicate.warning"), duplicate.sourceFilename()));
            step5DuplicateWarningLabel.setVisible(true);
            step5DuplicateWarningLabel.setManaged(true);
        } else {
            step5DuplicateWarningLabel.setVisible(false);
            step5DuplicateWarningLabel.setManaged(false);
        }
    }

    private boolean maybeConfirmDuplicateUpload(double wavelengthAngstroms) {
        var observationDate = findProcessParams().observationDetails().date().toLocalDate();
        var duplicate = Bass2000UploadHistoryService.getInstance().checkForDuplicateUpload(observationDate, wavelengthAngstroms);
        if (duplicate.isPresent()) {
            var record = duplicate.get();
            var alert = AlertFactory.confirmation(MessageFormat.format(message("duplicate.confirmation.message"), record.sourceFilename()));
            alert.setTitle(message("duplicate.confirmation.title"));
            alert.setHeaderText(message("duplicate.confirmation.header"));
            var result = alert.showAndWait();
            return result.isPresent() && result.get().getButtonData().isDefaultButton();
        }
        return true;
    }

    private void recordSuccessfulUploads() {
        BackgroundOperations.async(() -> {
            var selectedWavelength = wavelengthField.getValue();
            if (selectedWavelength == null) {
                return;
            }

            var historyService = Bass2000UploadHistoryService.getInstance();
            var observationDate = findProcessParams().observationDetails().date().toLocalDate();
            var wavelengthAngstroms = selectedWavelength.wavelength().angstroms();
            var sourceFileBasename = generatedBass2000Image.findMetadata(SourceInfo.class).map(SourceInfo::serFileName).orElse(generatedFilename);

            var uploadLineCenter = lineCenterUploadCheckbox != null && lineCenterUploadCheckbox.isSelected();
            var uploadWingImage = wingImageUploadCheckbox != null && wingImageUploadCheckbox.isSelected();

            if (uploadLineCenter && savedFilePath != null) {
                var lineCenterFilename = generateFileName().orElse("unknown_line_center") + ".fits";
                historyService.recordUpload(observationDate, wavelengthAngstroms, sourceFileBasename, lineCenterFilename);
            }

            if (uploadWingImage && savedOffBandFilePath != null) {
                var wingFilename = generateFileName().orElse("unknown_wing") + "_wing.fits";
                historyService.recordUpload(observationDate, wavelengthAngstroms, sourceFileBasename, wingFilename);
            }
        });
    }

}
