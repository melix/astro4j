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
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.jfx.Corrector;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.app.jfx.WritableImageSupport;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.app.jfx.bass2000.ComparisonModeManager;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.GONG;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.jfx.bass2000.ComparisonModeManager.ComparisonMode.*;

class Step3OrientationHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step3OrientationHandler.class);
    private static final int IMAGE_VIEW_SIZE = 400;

    private final ProcessParams processParams;
    private final Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier;
    private final ComparisonModeManager comparisonModeManager;
    private final Scaling scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));
    private ZoomableImageView userImageView;
    private ZoomableImageView gongImageView;
    private VBox gongImageContainer;
    private HBox comparisonControlsBox;
    private Button comparisonModeButton;
    private Label comparisonModeLabel;
    private Slider comparisonModeSlider;
    private Image gongReferenceImage;

    Step3OrientationHandler(ProcessParams processParams, Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier) {
        this.processParams = processParams;
        this.imagesSupplier = imagesSupplier;
        this.comparisonModeManager = new ComparisonModeManager(
                Step3OrientationHandler::message, null, List.of(NORMAL, BLEND, BLINK));
    }

    private static String message(String key) {
        return SpectroSolHubSubmissionController.message(key);
    }

    @Override
    public VBox createContent() {
        var content = new VBox(10);
        content.setPadding(new Insets(10));

        var title = new Label(message("orientation.title"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var instruction = new Label(message("orientation.instruction"));
        instruction.setWrapText(true);

        var warning = new Label(message("orientation.warning"));
        warning.setWrapText(true);
        warning.setMaxWidth(Double.MAX_VALUE);
        warning.setMinHeight(Label.USE_PREF_SIZE);
        warning.setStyle("-fx-background-color: #fff3e0; -fx-text-fill: #e65100; -fx-padding: 10; -fx-border-color: #e65100; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");

        var imageComparisonBox = new HBox(10);
        imageComparisonBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");

        var userImageContainer = new VBox(5);
        userImageContainer.setAlignment(Pos.CENTER);
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
        userImageContainer.getChildren().addAll(userImageLabel, userImageView);

        gongImageContainer = new VBox(5);
        gongImageContainer.setAlignment(Pos.CENTER);
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
        gongImageContainer.getChildren().addAll(gongImageLabel, gongImageView);

        imageComparisonBox.getChildren().addAll(userImageContainer, gongImageContainer);
        VBox.setVgrow(imageComparisonBox, Priority.ALWAYS);

        comparisonModeManager.setOriginalImageComparisonBox(imageComparisonBox);

        comparisonControlsBox = new HBox(8);
        comparisonControlsBox.setStyle("-fx-alignment: center; -fx-padding: 1;");
        comparisonControlsBox.setVisible(false);
        comparisonControlsBox.setManaged(false);

        comparisonModeLabel = new Label(message("blink.duration"));
        comparisonModeLabel.setStyle("-fx-font-weight: bold;");

        comparisonModeSlider = new Slider(0.0, 100.0, 50.0);
        comparisonModeSlider.setShowTickLabels(true);
        comparisonModeSlider.setShowTickMarks(true);
        comparisonModeSlider.setMajorTickUnit(25.0);
        comparisonModeSlider.setMinorTickCount(4);
        comparisonModeSlider.setPrefWidth(300);
        comparisonModeSlider.valueProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) ->
                comparisonModeManager.handleComparisonSliderChange(newValue));

        comparisonControlsBox.getChildren().addAll(comparisonModeLabel, comparisonModeSlider);

        var controlsBox = new HBox(8);
        controlsBox.setStyle("-fx-alignment: center; -fx-padding: 1;");

        comparisonModeButton = new Button(comparisonModeManager.getComparisonModeButtonText());
        comparisonModeButton.getStyleClass().add("custom-button");
        comparisonModeButton.setOnAction(e -> comparisonModeManager.toggleComparisonMode(
                comparisonControlsBox, comparisonModeButton, comparisonModeLabel, comparisonModeSlider));

        var fullscreenButton = new Button(message("button.fullscreen"));
        fullscreenButton.getStyleClass().add("custom-button");
        fullscreenButton.setOnAction(e -> comparisonModeManager.openFullscreenComparison(null));

        controlsBox.getChildren().addAll(comparisonModeButton, fullscreenButton);

        content.getChildren().addAll(title, instruction, warning, imageComparisonBox, comparisonControlsBox, controlsBox);
        return content;
    }

    @Override
    public void load() {
        loadUserImage();
        loadGongReferenceImage();
    }

    @Override
    public void cleanup() {
        comparisonModeManager.cleanup();
    }

    @Override
    public boolean validate() {
        return true;
    }

    private void loadUserImage() {
        var allImages = imagesSupplier.get();
        var representative = findRepresentativeImage(allImages);
        if (representative == null) {
            return;
        }
        BackgroundOperations.async(() -> {
            try {
                var wrapper = representative.image();
                if (wrapper instanceof FileBackedImage fbi) {
                    wrapper = fbi.unwrapToMemory();
                }
                var image = wrapper.copy();
                image = applyPAngleCorrectionIfNeeded(image, representative.kind());
                image = scaling.rescaleToRadius(image, 225, 512, 512);
                LinearStrechingStrategy.DEFAULT.stretch(image);
                if (image instanceof ImageWrapper32 mono) {
                    image = RGBImage.toRGB(mono);
                }
                var writableImage = WritableImageSupport.asWritable(image);
                Platform.runLater(() -> {
                    userImageView.setImage(writableImage);
                    userImageView.resetZoom();
                    if (gongReferenceImage != null) {
                        comparisonModeManager.setImages(writableImage, gongReferenceImage);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Failed to load user image for orientation check", e);
            }
        });
    }

    private void loadGongReferenceImage() {
        if (gongReferenceImage != null) {
            gongImageView.setImage(gongReferenceImage);
            gongImageView.resetZoom();
            return;
        }
        if (processParams == null || processParams.observationDetails().date() == null) {
            return;
        }
        var loadingLabel = new Label(message("orientation.gong.loading"));
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        if (gongImageContainer.getChildren().size() > 1) {
            gongImageContainer.getChildren().set(1, loadingLabel);
        }
        BackgroundOperations.async(() -> {
            try {
                var gongImageUrl = GONG.fetchGongImage(processParams.observationDetails().date());
                if (gongImageUrl.isPresent()) {
                    try (var inputStream = gongImageUrl.get().openStream()) {
                        gongReferenceImage = new Image(inputStream);
                        Platform.runLater(() -> {
                            restoreGongImageView();
                            gongImageView.setImage(gongReferenceImage);
                            gongImageView.resetZoom();
                            if (userImageView.getImage() != null) {
                                comparisonModeManager.setImages(userImageView.getImage(), gongReferenceImage);
                            }
                        });
                    }
                } else {
                    Platform.runLater(() -> {
                        loadingLabel.setText(message("orientation.gong.unavailable"));
                        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange;");
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load GONG reference image", e);
                Platform.runLater(() -> {
                    loadingLabel.setText(message("orientation.gong.unavailable"));
                    loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange;");
                });
            }
        });
    }

    private void restoreGongImageView() {
        if (gongImageContainer.getChildren().size() > 1) {
            gongImageContainer.getChildren().set(1, gongImageView);
        }
    }

    private ImageWrapper applyPAngleCorrectionIfNeeded(ImageWrapper image, GeneratedImageKind kind) {
        if (processParams == null || processParams.observationDetails().date() == null) {
            return image;
        }
        boolean pAlreadyCorrected = kind == GeneratedImageKind.TECHNICAL_CARD
                || (processParams.geometryParams().isAutocorrectAngleP() && !kind.cannotPerformManualRotation());
        if (!pAlreadyCorrected) {
            var p = SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()).p();
            if (p != 0) {
                image = Corrector.rotate(image, p, processParams.geometryParams().autocropMode() == AutocropMode.OFF);
            }
        }
        return image;
    }

    private MultipleImagesViewer.ImageInfo findRepresentativeImage(List<MultipleImagesViewer.ImageInfo> images) {
        var mainShift = processParams != null ? new PixelShift(processParams.spectrumParams().pixelShift()) : new PixelShift(0);
        var zeroShift = new PixelShift(0);
        MultipleImagesViewer.ImageInfo processedAtMainShift = null;
        MultipleImagesViewer.ImageInfo rawAtMainShift = null;
        MultipleImagesViewer.ImageInfo imageMathAtMainShift = null;
        MultipleImagesViewer.ImageInfo processedAtZeroShift = null;
        MultipleImagesViewer.ImageInfo rawAtZeroShift = null;
        MultipleImagesViewer.ImageInfo imageMathAtZeroShift = null;
        MultipleImagesViewer.ImageInfo continuum = null;
        for (var image : images) {
            var ps = image.image().findMetadata(PixelShift.class).orElse(null);
            if (ps != null && ps.equals(mainShift)) {
                if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                    processedAtMainShift = image;
                } else if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                    rawAtMainShift = image;
                } else if (image.kind() == GeneratedImageKind.IMAGE_MATH && imageMathAtMainShift == null) {
                    imageMathAtMainShift = image;
                }
            }
            if (ps != null && ps.equals(zeroShift)) {
                if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                    processedAtZeroShift = image;
                } else if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                    rawAtZeroShift = image;
                } else if (image.kind() == GeneratedImageKind.IMAGE_MATH && imageMathAtZeroShift == null) {
                    imageMathAtZeroShift = image;
                }
            }
            if (image.kind() == GeneratedImageKind.CONTINUUM && continuum == null) {
                continuum = image;
            }
        }
        if (processedAtMainShift != null) {
            return processedAtMainShift;
        }
        if (rawAtMainShift != null) {
            return rawAtMainShift;
        }
        if (imageMathAtMainShift != null) {
            return imageMathAtMainShift;
        }
        if (processedAtZeroShift != null) {
            return processedAtZeroShift;
        }
        if (rawAtZeroShift != null) {
            return rawAtZeroShift;
        }
        if (imageMathAtZeroShift != null) {
            return imageMathAtZeroShift;
        }
        if (continuum != null) {
            return continuum;
        }
        for (var image : images) {
            if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                return image;
            }
        }
        for (var image : images) {
            if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                return image;
            }
        }
        return images.isEmpty() ? null : images.getFirst();
    }
}
