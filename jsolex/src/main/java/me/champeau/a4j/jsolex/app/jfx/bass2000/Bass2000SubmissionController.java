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
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Bass2000Compatibility;
import me.champeau.a4j.jsolex.processing.util.Bass2000UploadHistoryService;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the BASS2000 submission wizard.
 * Guides users through the process of preparing and submitting solar images to the BASS2000 database.
 */
public class Bass2000SubmissionController {

    /**
     * Creates a new instance. Required by FXML.
     */
    public Bass2000SubmissionController() {
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(Bass2000SubmissionController.class);

    static final SpectralRay BASS2000_HA = SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6562.762));
    static final SpectralRay BASS2000_CALCIUM_K = SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3933.663));
    static final SpectralRay BASS2000_CALCIUM_H = SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3968.469));

    static final SpectralRay BASS2000_HA_WING = SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6561.262));
    static final SpectralRay BASS2000_CALCIUM_K_WING = SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3932.163));
    static final SpectralRay BASS2000_CALCIUM_H_WING = SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3966.968));

    static final Map<SpectralRay, String> ACCEPTED_SPECTRAL_RAYS = new LinkedHashMap<>() {{
            // Use official wavelengths from the BASS2000 documentation
            put(BASS2000_HA, "Ha");
            put(BASS2000_CALCIUM_K, "Cak");
            put(BASS2000_CALCIUM_H, "Cah");
    }};

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

    static final double TOLERANCE_ANGSTROMS = 0.1;

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
    private ComparisonModeManager comparisonModeManager;
    private Step2ImageOrientationHandler step2Handler;

    // Step 4 - Filename generation
    private String generatedFilename;

    private final FileNameGenerator fileNameGenerator = new FileNameGenerator();
    private Step1AgreementHandler step1Handler;
    private Step3FormDataHandler step3Handler;
    private Step4FileNamingHandler step4Handler;
    private Step5UploadHandler step5Handler;

    private Path outputDirectory;

    /**
     * Sets up the submission controller.
     * @param stage the dialog stage
     * @param mainController the main controller
     * @param outputDirectory the output directory
     */
    public void setup(Stage stage, JSolExInterface mainController, Path outputDirectory) {
        this.stage = stage;
        this.mainController = mainController;
        var scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));
        this.outputDirectory = outputDirectory;

        this.step1Handler = new Step1AgreementHandler();
        this.step3Handler = new Step3FormDataHandler(Bass2000SubmissionController.this::findProcessParams, fileNameGenerator);
        this.step3Handler.setValidationChangeListener(this::updateNextButtonState);

        this.step4Handler = new Step4FileNamingHandler(
            fileNameGenerator,
            new Step4FileNamingHandler.ImageDataSupplier() {
                @Override
                public ImageWrapper getGeneratedBass2000Image() {
                    return generatedBass2000Image;
                }

                @Override
                public ImageWrapper getGeneratedOffBandImage() {
                    return generatedOffBandImage;
                }

                @Override
                public String getGeneratedFilename() {
                    return generatedFilename;
                }
            },
            disabled -> nextButton.setDisable(disabled)
        );

        this.step5Handler = new Step5UploadHandler(
            step4Handler,
            new Step5UploadHandler.ImageDataSupplier() {
                @Override
                public ImageWrapper getGeneratedBass2000Image() {
                    return generatedBass2000Image;
                }

                @Override
                public ImageWrapper getGeneratedOffBandImage() {
                    return generatedOffBandImage;
                }
            },
            new Step5UploadHandler.UploadDataProvider() {
                @Override
                public SpectralRay getSelectedWavelength() {
                    return step3Handler.getSelectedWavelength();
                }

                @Override
                public boolean maybeConfirmDuplicateUpload(double wavelengthAngstroms) {
                    return Bass2000SubmissionController.this.maybeConfirmDuplicateUpload(wavelengthAngstroms);
                }

                @Override
                public void recordSuccessfulUploads() {
                    Bass2000SubmissionController.this.recordSuccessfulUploads();
                }

                @Override
                public LocalDate getObservationDate() {
                    return findProcessParams().observationDetails().date().toLocalDate();
                }

                @Override
                public void exportFilesForUpload() {
                    Bass2000SubmissionController.this.exportBass2000ImageForUpload();
                }

                @Override
                public void closeWizard() {
                    Bass2000SubmissionController.this.finishWizard();
                }
            },
                scaling
        );

        this.comparisonModeManager = new ComparisonModeManager(new ComparisonModeManager.ImageTransformationListener() {
            @Override
            public void onHorizontalFlip() {
                if (step2Handler != null) {
                    step2Handler.performHorizontalFlip();
                }
            }

            @Override
            public void onVerticalFlip() {
                if (step2Handler != null) {
                    step2Handler.performVerticalFlip();
                }
            }

            @Override
            public void onRotation(int degrees) {
                if (step2Handler != null) {
                    step2Handler.performRotation(degrees);
                }
            }

            @Override
            public void onAngleAdjustment(double angle) {
                if (step2Handler != null) {
                    step2Handler.performAngleAdjustment();
                }
            }

            @Override
            public void onReset() {
                if (step2Handler != null) {
                    step2Handler.performReset();
                }
            }
        });

        this.step2Handler = new Step2ImageOrientationHandler(comparisonModeManager, new Step2ImageOrientationHandler.ImageTransformationListener() {
            @Override
            public void onTransformationApplied() {
            }

            @Override
            public ProcessParams findProcessParams() {
                return Bass2000SubmissionController.this.findProcessParams();
            }

            @Override
            public ImageWrapper getOriginalImage() {
                return originalImage;
            }

            @Override
            public ImageWrapper getOriginalOffBandImage() {
                return originalOffBandImage;
            }

            @Override
            public void setGeneratedBass2000Image(ImageWrapper image) {
                generatedBass2000Image = image;
            }

            @Override
            public void setGeneratedOffBandImage(ImageWrapper image) {
                generatedOffBandImage = image;
            }

            @Override
            public ImageWrapper getGeneratedBass2000Image() {
                return generatedBass2000Image;
            }

            @Override
            public void validateBass2000Image() {
                Bass2000SubmissionController.this.validateBass2000Image();
            }

            @Override
            public int getCurrentStep() {
                return currentStep;
            }
        });

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
                step4Handler.setSavedFilePath(null);
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
            // Validate current step before proceeding
            var currentHandler = getCurrentStepHandler();
            if (currentHandler != null && !currentHandler.validate()) {
                return;
            }

            if (currentStep == 3) {
                if (!isWavelengthValid()) {
                    showWavelengthError();
                    return;
                }

                createUpdatedProcessParams();

                // Generate wing image based on confirmed wavelength selection
                var centerRay = step3Handler.getSelectedWavelength();
                var wingRay = RAY_TO_WING.get(centerRay);

                if (wingRay != null) {
                    // Show loading indicator and disable buttons
                    nextButton.setDisable(true);
                    previousButton.setDisable(true);
                    wizardProgress.setProgress(-1.0); // Indeterminate progress
                    nextButton.setText(message("processing.wing.image"));

                    BackgroundOperations.async(() -> {
                        try {
                            var centerWavelength = centerRay.wavelength().angstroms();
                            var wingWavelength = wingRay.wavelength().angstroms();
                            var shift = centerWavelength - wingWavelength;

                            var originalOffBandImage = getGeometryCorrectedImage(centerRay.wavelength().angstroms(), shift);
                            if (originalOffBandImage != null) {
                                this.originalOffBandImage = originalOffBandImage;

                                this.generatedOffBandImage = originalOffBandImage.copy();

                                if (step2Handler != null) {
                                    step2Handler.applyTransformationsToOffBandImage();
                                }

                                var processParams = findProcessParams();

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

                                var maybeFileName = fileNameGenerator.generateFileName(generatedBass2000Image);
                                if (maybeFileName.isEmpty()) {
                                    loadStep3();
                                    AlertFactory.error(message("filename.error")).showAndWait();
                                    return;
                                }
                                generatedFilename = maybeFileName.get();
                                step4Handler.setSavedFilePath(null);

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
                    return;
                }

                var maybeFileName = fileNameGenerator.generateFileName(generatedBass2000Image);
                if (maybeFileName.isEmpty()) {
                    loadStep3();
                    AlertFactory.error(message("filename.error")).showAndWait();
                    return;
                }
                generatedFilename = maybeFileName.get();
                step4Handler.setSavedFilePath(null);
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
        if (comparisonModeManager != null) {
            comparisonModeManager.cleanup();
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
        if (comparisonModeManager != null && comparisonModeManager.getCurrentMode() != ComparisonModeManager.ComparisonMode.NORMAL && currentStep != 2) {
            step2Handler.cleanup();
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
        var step1Content = step1Handler.createContent();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step1Content);
        step1Handler.load();
    }

    private void loadStep2() {
        var step2Content = createStep2Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step2Content);

        step2Handler.load();
        validateBass2000Image();
    }

    private void loadStep3() {
        var step3Content = createStep3Content();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step3Content);

        step3Handler.load();
        Platform.runLater(this::updateNextButtonState);
    }

    private void loadStep4() {
        var step4Content = step4Handler.createContent();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step4Content);
        step4Handler.load();
    }

    private void loadStep5() {
        var step5Content = step5Handler.createContent();
        contentPane.getChildren().clear();
        contentPane.getChildren().add(step5Content);

        step5Handler.load();
    }

    private void exportBass2000ImageForUpload() {
        BackgroundOperations.async(() -> {
            try {
                var baseFileName = (!step4Handler.getLineCenterFilename().isEmpty())
                        ? step4Handler.getLineCenterFilename()
                        : fileNameGenerator.generateFileName(generatedBass2000Image).orElseThrow();

                if (baseFileName.toLowerCase().endsWith(".fits")) {
                    baseFileName = baseFileName.substring(0, baseFileName.length() - 5);
                }

                var lineCenterFileName = baseFileName + ".fits";
                Files.createDirectories(outputDirectory);
                var lineCenterFile = outputDirectory.resolve(lineCenterFileName).toFile();
                FitsUtils.writeFitsFile(generatedBass2000Image, lineCenterFile, withGeneratedInstrumentId(findProcessParams()), false);
                step4Handler.setSavedFilePath(lineCenterFile);

                if (generatedOffBandImage != null) {
                    var offBandBaseFileName = (!step4Handler.getOffBandFilename().isEmpty())
                            ? step4Handler.getOffBandFilename()
                            : fileNameGenerator.generateOffBandFileName(generatedOffBandImage).orElseThrow();

                    if (offBandBaseFileName.toLowerCase().endsWith(".fits")) {
                        offBandBaseFileName = offBandBaseFileName.substring(0, offBandBaseFileName.length() - 5);
                    }

                    var offBandFileName = offBandBaseFileName + ".fits";
                    var offBandFile = outputDirectory.resolve(offBandFileName).toFile();
                    FitsUtils.writeFitsFile(generatedOffBandImage, offBandFile, withGeneratedInstrumentId(generatedOffBandImage.findMetadata(ProcessParams.class).orElseThrow()), false);
                    step4Handler.setSavedOffBandFilePath(offBandFile);
                }

                Platform.runLater(() -> {
                    if (currentStep == 5) {
                        loadStep5();
                    }
                    step5Handler.setUploadButtonEnabled(true);
                });
            } catch (Exception e) {
                LOGGER.error(JSolEx.message("error.failed.save.bass2000"), e);
                Platform.runLater(() -> {
                    var alert = AlertFactory.error("Could not save BASS2000 file: " + e.getMessage());
                    alert.setTitle("Save Error");
                    alert.setHeaderText("File Save Failed");
                    alert.showAndWait();
                });
            }
        });
    }


    private boolean isWavelengthValid() {
        return step3Handler.getSelectedWavelength() != null;
    }

    private void showWavelengthError() {
        var alert = AlertFactory.error(message("wavelength.error.message"));
        alert.setTitle(message("wavelength.error.title"));
        alert.setHeaderText(message("wavelength.error.header"));
        alert.showAndWait();
    }

    private VBox createStep2Content() {
        return step2Handler.createContent();
    }


    private VBox createStep3Content() {
        return step3Handler.createContent();
    }

    private ProcessParams findProcessParams() {
        return generatedBass2000Image.findMetadata(ProcessParams.class).orElse(null);
    }

    private ProcessParams withGeneratedInstrumentId(ProcessParams params) {
        var instrumentId = fileNameGenerator.generateInstrumentId(params);
        var observationDetails = params.observationDetails();
        var updatedInstrument = observationDetails.instrument().withLabel(instrumentId);
        return params.withObservationDetails(observationDetails.withInstrument(updatedInstrument));
    }

    private void updateNextButtonState() {
        var currentHandler = getCurrentStepHandler();
        if (currentHandler != null) {
            if (currentHandler instanceof Step1AgreementHandler) {
                nextButton.setDisable(false);
            } else {
                nextButton.setDisable(!currentHandler.validate());
            }
        }
    }

    private StepHandler getCurrentStepHandler() {
        return switch (currentStep) {
            case 1 -> step1Handler;
            case 2 -> step2Handler;
            case 3 -> step3Handler;
            case 4 -> step4Handler;
            case 5 -> step5Handler;
            default -> null;
        };
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
                        updateNextButtonState();
                        updateButtons();

                        injectImageIntoStep1Handler();
                    });
                } else {
                    Platform.runLater(() -> {
                        var alert = AlertFactory.error(message("error.image.generation.title"));
                        alert.setTitle(message("error.image.generation.title"));
                        alert.setHeaderText(message("error.image.generation.header"));
                        alert.setContentText(message("error.image.generation.message"));
                        alert.showAndWait();
                        updateNextButtonState();
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
                    updateNextButtonState();
                    updateButtons();
                });
            }
        });
    }


    private void finishWizard() {
        if (comparisonModeManager != null) {
            comparisonModeManager.cleanup();
        }
        stage.close();
    }

    private ImageWrapper getGeometryCorrectedImage(Double reference, double shift) {
        var executor = mainController.getScriptExecutor();
        var ref = reference != null ? String.format(Locale.US, ", ref:%.3f", reference) : "";
        var output = executor.execute(String.format(Locale.US, """
            [outputs]
            __result=autocrop2(img(-a2px(a: %.3f%s));1.2)
            """, shift, ref), ImageMathScriptExecutor.SectionKind.SINGLE);
        var result = output.imagesByLabel().get("__result");

        if (result instanceof ImageWrapper image) {
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
        var updatedObservationDetails = step3Handler.getObservationDetails();
        var selectedSpectralRay = step3Handler.getSelectedWavelength();
        var updatedSpectrumParams = processParams
                .withObservationDetails(updatedObservationDetails)
                .withSpectrumParams(processParams.spectrumParams().withRay(selectedSpectralRay));
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


    private void injectImageIntoStep1Handler() {
        if (originalImage != null) {
            var processParams = originalImage.findMetadata(ProcessParams.class);
            processParams.ifPresent(step1Handler::setImageAndParams);
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
            var selectedWavelength = step3Handler.getSelectedWavelength();
            if (selectedWavelength == null) {
                return;
            }

            var historyService = Bass2000UploadHistoryService.getInstance();
            var observationDate = findProcessParams().observationDetails().date().toLocalDate();
            var wavelengthAngstroms = selectedWavelength.wavelength().angstroms();
            var sourceFileBasename = generatedBass2000Image.findMetadata(SourceInfo.class).map(SourceInfo::serFileName).orElse(generatedFilename);

            var uploadLineCenter = step5Handler.isLineCenterUploadSelected();
            var uploadWingImage = step5Handler.isWingImageUploadSelected();

            if (uploadLineCenter && step4Handler.getSavedFilePath() != null) {
                var lineCenterFilename = fileNameGenerator.generateFileName(generatedBass2000Image).orElse("unknown_line_center") + ".fits";
                historyService.recordUpload(observationDate, wavelengthAngstroms, sourceFileBasename, lineCenterFilename);
            }

            if (uploadWingImage && step4Handler.getSavedOffBandFilePath() != null) {
                var wingFilename = fileNameGenerator.generateOffBandFileName(generatedOffBandImage).orElse("unknown_wing") + ".fits";
                historyService.recordUpload(observationDate, wavelengthAngstroms, sourceFileBasename, wingFilename);
            }
        });
    }

}
