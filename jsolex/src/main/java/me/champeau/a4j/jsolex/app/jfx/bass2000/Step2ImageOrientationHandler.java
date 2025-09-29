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

import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.app.jfx.Corrector;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.app.jfx.WritableImageSupport;
import me.champeau.a4j.jsolex.processing.util.GONG;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;

class Step2ImageOrientationHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step2ImageOrientationHandler.class);
    private static final int IMAGE_VIEW_SIZE = 480;

    interface ImageTransformationListener {
        void onTransformationApplied();
        ProcessParams findProcessParams();
        ImageWrapper getOriginalImage();
        ImageWrapper getOriginalOffBandImage();
        void setGeneratedBass2000Image(ImageWrapper image);
        void setGeneratedOffBandImage(ImageWrapper image);
        ImageWrapper getGeneratedBass2000Image();
        void validateBass2000Image();
        int getCurrentStep();
    }

    private ZoomableImageView userImageView;
    private ZoomableImageView gongImageView;
    private VBox gongImageContainer;
    private HBox angleAdjustmentBox;
    private Slider mainAngleSlider;
    private Button comparisonModeButton;
    private Label comparisonModeLabel;
    private Slider comparisonModeSlider;

    private Image gongReferenceImage;
    private WritableImage userDisplayImage;
    private Image gongDisplayImage;

    private int rotation = 0;
    private boolean horizontalFlip = false;
    private boolean verticalFlip = false;

    private final ComparisonModeManager comparisonModeManager;
    private final ImageTransformationListener transformationListener;
    private final Scaling scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));

    Step2ImageOrientationHandler(ComparisonModeManager comparisonModeManager, ImageTransformationListener transformationListener) {
        this.comparisonModeManager = comparisonModeManager;
        this.transformationListener = transformationListener;
    }

    @Override
    public VBox createContent() {
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
        imageComparisonBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");

        var userImageContainer = new VBox(5);
        userImageContainer.setStyle("-fx-alignment: center;");
        HBox.setHgrow(userImageContainer, Priority.ALWAYS);
        var userImageLabel = new Label(message("orientation.your.image"));
        userImageLabel.setStyle("-fx-font-weight: bold;");
        userImageView = new ZoomableImageView();
        userImageView.setPrefSize(IMAGE_VIEW_SIZE, IMAGE_VIEW_SIZE);
        userImageView.setMinSize(200, 200);
        VBox.setVgrow(userImageView, Priority.ALWAYS);
        HBox.setHgrow(userImageView, Priority.ALWAYS);
        userImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        var userImageWrapper = new HBox();
        userImageWrapper.setStyle("-fx-alignment: center;");
        userImageWrapper.getChildren().add(userImageView);
        VBox.setVgrow(userImageWrapper, Priority.ALWAYS);
        HBox.setHgrow(userImageView, Priority.ALWAYS);

        userImageContainer.getChildren().addAll(userImageLabel, userImageWrapper);

        gongImageContainer = new VBox(5);
        gongImageContainer.setStyle("-fx-alignment: center;");
        HBox.setHgrow(gongImageContainer, Priority.ALWAYS);
        var gongImageLabel = new Label(message("orientation.gong.reference"));
        gongImageLabel.setStyle("-fx-font-weight: bold;");
        gongImageView = new ZoomableImageView();
        gongImageView.setPrefSize(IMAGE_VIEW_SIZE, IMAGE_VIEW_SIZE);
        gongImageView.setMinSize(200, 200);
        VBox.setVgrow(gongImageView, Priority.ALWAYS);
        HBox.setHgrow(gongImageView, Priority.ALWAYS);
        gongImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");

        var gongImageWrapper = new HBox();
        gongImageWrapper.setStyle("-fx-alignment: center;");
        gongImageWrapper.getChildren().add(gongImageView);
        VBox.setVgrow(gongImageWrapper, Priority.ALWAYS);
        HBox.setHgrow(gongImageView, Priority.ALWAYS);

        gongImageContainer.getChildren().addAll(gongImageLabel, gongImageWrapper);

        imageComparisonBox.getChildren().addAll(userImageContainer, gongImageContainer);

        mainAngleSlider = new Slider(-2.0, 2.0, 0.0);
        mainAngleSlider.setShowTickLabels(true);
        mainAngleSlider.setShowTickMarks(true);
        mainAngleSlider.setMajorTickUnit(1.0);
        mainAngleSlider.setMinorTickCount(4);
        mainAngleSlider.setPrefWidth(300);

        var angleSliderListener = (ChangeListener<Number>) (observable, oldValue, newValue) -> {
            comparisonModeManager.setAngleAdjustment(newValue.doubleValue());
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
            comparisonModeManager.handleComparisonSliderChange(newValue);
        };
        comparisonModeSlider.valueProperty().addListener(comparisonSliderListener);

        angleAdjustmentBox = new HBox(8);
        angleAdjustmentBox.setStyle("-fx-alignment: center; -fx-padding: 1;");
        angleAdjustmentBox.setVisible(false);
        angleAdjustmentBox.setManaged(false);
        var angleLabel = new Label(message("fine.tilt.adjust"));
        angleLabel.setStyle("-fx-font-weight: bold;");
        comparisonModeLabel = new Label(message("blink.duration"));
        comparisonModeLabel.setStyle("-fx-font-weight: bold;");
        var fullscreenButton = new Button(message("button.fullscreen"));
        fullscreenButton.getStyleClass().add("custom-button");
        fullscreenButton.setOnAction(e -> comparisonModeManager.openFullscreenComparison(mainAngleSlider));

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

        comparisonModeButton = new Button(comparisonModeManager.getComparisonModeButtonText());
        comparisonModeButton.getStyleClass().add("custom-button");
        comparisonModeButton.setOnAction(e -> comparisonModeManager.toggleComparisonMode(angleAdjustmentBox, comparisonModeButton, comparisonModeLabel, comparisonModeSlider));

        resetButton.setOnAction(e -> {
            mainAngleSlider.valueProperty().removeListener(angleSliderListener);
            mainAngleSlider.setValue(0.0);
            resetTransformations();
            mainAngleSlider.valueProperty().addListener(angleSliderListener);
        });

        controlsBox.getChildren().addAll(controlsLabel, horizontalFlipButton, verticalFlipButton, rotateLeftButton, rotateRightButton, resetButton, comparisonModeButton, fullscreenButton);

        comparisonModeManager.setOriginalImageComparisonBox(imageComparisonBox);

        var processParams = transformationListener.findProcessParams();
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

    @Override
    public void load() {
        applyCurrentTransformations();
        loadGongReferenceImage();
    }

    @Override
    public void cleanup() {
        comparisonModeManager.exitComparisonMode(angleAdjustmentBox);
    }

    @Override
    public boolean validate() {
        return true;
    }

    ZoomableImageView getUserImageView() {
        return userImageView;
    }

    ZoomableImageView getGongImageView() {
        return gongImageView;
    }


    void performHorizontalFlip() {
        applyHorizontalFlip();
    }

    void performVerticalFlip() {
        applyVerticalFlip();
    }

    void performRotation(int degrees) {
        applyRotation(degrees);
    }

    void performReset() {
        resetTransformations();
    }

    void performAngleAdjustment() {
        applyCurrentTransformations();
    }

    void applyTransformationsToOffBandImage() {
        var originalOffBandImage = transformationListener.getOriginalOffBandImage();
        if (originalOffBandImage != null) {
            try {
                var processParams = transformationListener.findProcessParams();
                var observationDate = processParams.observationDetails().date();
                var solarParams = SolarParametersUtils.computeSolarParams(observationDate.toLocalDateTime());
                var pAngle = solarParams.p();
                var generatedOffBandImage = applyTransformationsToImage(originalOffBandImage, pAngle);
                transformationListener.setGeneratedOffBandImage(generatedOffBandImage);
            } catch (Exception e) {
                LOGGER.error("Failed to apply transformations to offband image", e);
            }
        }
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
                var observationDate = transformationListener.findProcessParams().observationDetails().date();

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
                                if (comparisonModeManager != null && userDisplayImage != null) {
                                    comparisonModeManager.setImages(userDisplayImage, gongDisplayImage);
                                }
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
            loadingWrapper.setPrefSize(IMAGE_VIEW_SIZE, IMAGE_VIEW_SIZE);
            loadingWrapper.setMinSize(200, 200);
            loadingWrapper.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-alignment: center;");
            loadingWrapper.getChildren().add(loadingContainer);
            VBox.setVgrow(loadingWrapper, Priority.ALWAYS);
            HBox.setHgrow(loadingWrapper, Priority.ALWAYS);

            if (gongImageContainer.getChildren().size() > 1) {
                gongImageContainer.getChildren().set(1, loadingWrapper);
            }
        }
    }

    private void restoreGongImageView() {
        if (gongImageView != null && gongImageContainer != null) {
            if (gongImageContainer.getChildren().size() > 1) {
                var gongImageWrapper = new HBox();
                gongImageWrapper.setStyle("-fx-alignment: center;");
                gongImageWrapper.getChildren().add(gongImageView);
                VBox.setVgrow(gongImageWrapper, Priority.ALWAYS);
                HBox.setHgrow(gongImageView, Priority.ALWAYS);
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
                if (comparisonModeManager != null && userDisplayImage != null) {
                    comparisonModeManager.setImages(userDisplayImage, gongDisplayImage);
                }
            }
        } catch (Exception e) {
        }
    }

    private void updateUserImageDisplay() {
        var generatedBass2000Image = transformationListener.getGeneratedBass2000Image();
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

                        if (comparisonModeManager != null) {
                            comparisonModeManager.updateUserImage(writableImage);
                            if (gongDisplayImage != null) {
                                comparisonModeManager.setImages(writableImage, gongDisplayImage);
                            }
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
        if (comparisonModeManager != null) {
            comparisonModeManager.setAngleAdjustment(0.0);
        }

        applyCurrentTransformations();
    }

    private void applyCurrentTransformations() {
        var originalImage = transformationListener.getOriginalImage();
        if (originalImage == null) {
            return;
        }

        try {
            var processParams = transformationListener.findProcessParams();
            var observationDate = processParams.observationDetails().date();
            var solarParams = SolarParametersUtils.computeSolarParams(observationDate.toLocalDateTime());
            var pAngle = solarParams.p();

            var generatedBass2000Image = applyTransformationsToImage(originalImage, pAngle);
            transformationListener.setGeneratedBass2000Image(generatedBass2000Image);

            var originalOffBandImage = transformationListener.getOriginalOffBandImage();
            if (originalOffBandImage != null) {
                var generatedOffBandImage = applyTransformationsToImage(originalOffBandImage, pAngle);
                transformationListener.setGeneratedOffBandImage(generatedOffBandImage);
            }

            updateUserImageDisplay();
            if (transformationListener.getCurrentStep() == 2) {
                transformationListener.validateBass2000Image();
            }

            transformationListener.onTransformationApplied();

        } catch (Exception e) {
            throw new ProcessingException("Error applying transformations", e);
        }
    }

    private ImageWrapper applyTransformationsToImage(ImageWrapper originalImage, double pAngle) {
        var image = originalImage.copy();

        if (horizontalFlip) {
            image = Corrector.rotate(image, Math.PI, false);
            image = Corrector.verticalFlip(image);
        }

        if (verticalFlip) {
            image = Corrector.verticalFlip(image);
        }

        if (rotation != 0) {
            image = Corrector.rotate(image, Math.toRadians(rotation), false);
        }

        var angleAdjustment = comparisonModeManager.getAngleAdjustment();
        if (angleAdjustment != 0) {
            image = Corrector.rotate(image, Math.toRadians(angleAdjustment), false);
        }

        return Corrector.rotate(image, pAngle, false);
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }
}