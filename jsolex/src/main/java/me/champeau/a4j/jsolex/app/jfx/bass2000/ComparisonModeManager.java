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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;

import java.util.List;
import java.util.function.Function;

import static javafx.scene.input.KeyCode.ESCAPE;
import static javafx.stage.Modality.APPLICATION_MODAL;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

public class ComparisonModeManager {
    private static final int IMAGE_VIEW_SIZE = 480;

    public enum ComparisonMode {
        NORMAL, BLINK, BLEND
    }

    private ComparisonMode currentMode = ComparisonMode.NORMAL;
    private Timeline blinkTimeline;
    private Timeline fullscreenBlinkTimeline;
    private boolean showingUserImage = true;
    private boolean fullscreenShowingUserImage = true;
    private double blinkDurationMs = 500.0;
    private double opacityValue = 50.0;

    private HBox originalImageComparisonBox;
    private VBox blinkContainer;
    private VBox blendContainer;
    private ZoomableImageView blinkImageView;
    private StackPane opacityStackPane;
    private ZoomableImageView opacityUserImageView;
    private ZoomableImageView opacityGongImageView;

    private Stage fullscreenStage;
    private ImageView fullscreenImageView;
    private StackPane fullscreenBlendPane;
    private ImageView fullscreenUserImageView;
    private ImageView fullscreenGongImageView;
    private Button fullscreenComparisonModeButton;

    private Image userDisplayImage;
    private Image gongDisplayImage;

    private static final List<ComparisonMode> DEFAULT_MODE_ORDER = List.of(
            ComparisonMode.NORMAL, ComparisonMode.BLINK, ComparisonMode.BLEND);

    private double angleAdjustment = 0.0;
    private final ImageTransformationListener transformationListener;
    private final Function<String, String> messageFunction;
    private final List<ComparisonMode> modeOrder;

    public interface ImageTransformationListener {
        void onHorizontalFlip();
        void onVerticalFlip();
        void onRotation(int degrees);
        void onAngleAdjustment(double angle);
        void onReset();
        default void onAutoAlign() {}
        default boolean isAutoAlignAvailable() { return false; }
    }

    public ComparisonModeManager(Function<String, String> messageFunction, ImageTransformationListener transformationListener) {
        this(messageFunction, transformationListener, DEFAULT_MODE_ORDER);
    }

    public ComparisonModeManager(Function<String, String> messageFunction, ImageTransformationListener transformationListener, List<ComparisonMode> modeOrder) {
        this.messageFunction = messageFunction;
        this.transformationListener = transformationListener;
        this.modeOrder = modeOrder;
    }

    public ComparisonMode getCurrentMode() {
        return currentMode;
    }

    public void setImages(Image userImage, Image gongImage) {
        this.userDisplayImage = userImage;
        this.gongDisplayImage = gongImage;
    }

    public void setOriginalImageComparisonBox(HBox box) {
        this.originalImageComparisonBox = box;
    }

    public double getAngleAdjustment() {
        return angleAdjustment;
    }

    public void setAngleAdjustment(double angle) {
        this.angleAdjustment = angle;
    }

    private ComparisonMode nextMode() {
        int idx = modeOrder.indexOf(currentMode);
        return modeOrder.get((idx + 1) % modeOrder.size());
    }

    private String labelForMode(ComparisonMode mode) {
        return switch (mode) {
            case NORMAL -> message("mode.button.normal");
            case BLINK -> message("mode.button.blink");
            case BLEND -> message("mode.button.blend");
        };
    }

    public String getComparisonModeButtonText() {
        return labelForMode(nextMode());
    }

    public String getFullscreenComparisonModeButtonText() {
        return labelForMode(nextFullscreenMode());
    }

    public void toggleComparisonMode(HBox angleAdjustmentBox, Button comparisonModeButton,
                              Label comparisonModeLabel, Slider comparisonModeSlider) {
        transitionTo(nextMode(), angleAdjustmentBox);
        updateComparisonUI(comparisonModeButton, comparisonModeLabel, comparisonModeSlider);
    }

    private void transitionTo(ComparisonMode target, HBox angleAdjustmentBox) {
        if (currentMode != ComparisonMode.NORMAL) {
            exitComparisonMode(angleAdjustmentBox);
        }
        switch (target) {
            case BLINK -> enterBlinkMode(angleAdjustmentBox);
            case BLEND -> enterBlendMode(angleAdjustmentBox);
            case NORMAL -> {}
        }
    }

    public void updateComparisonUI(Button comparisonModeButton, Label comparisonModeLabel, Slider comparisonModeSlider) {
        if (comparisonModeButton != null) {
            comparisonModeButton.setText(getComparisonModeButtonText());
        }

        if (comparisonModeLabel != null) {
            switch (currentMode) {
                case NORMAL, BLINK -> comparisonModeLabel.setText(message("blink.duration"));
                case BLEND -> comparisonModeLabel.setText(message("blend.percent"));
            }
        }

        if (comparisonModeSlider != null) {
            switch (currentMode) {
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

    public void handleComparisonSliderChange(Number newValue) {
        if (currentMode == ComparisonMode.BLINK) {
            double blinkMs = 500.0 + (newValue.doubleValue() / 100.0) * 4500.0;
            blinkDurationMs = blinkMs;
            if (blinkTimeline != null) {
                restartBlinkAnimation();
            }
        } else if (currentMode == ComparisonMode.BLEND) {
            opacityValue = newValue.doubleValue();
            if (opacityUserImageView != null) {
                opacityUserImageView.setOpacity(opacityValue / 100.0);
            }
        }
    }

    public void enterBlinkMode(HBox angleAdjustmentBox) {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        currentMode = ComparisonMode.BLINK;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setManaged(true);
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

    public void enterBlendMode(HBox angleAdjustmentBox) {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        currentMode = ComparisonMode.BLEND;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setVisible(true);
            angleAdjustmentBox.setManaged(true);
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

    public void exitComparisonMode(HBox angleAdjustmentBox) {
        currentMode = ComparisonMode.NORMAL;
        showingUserImage = true;

        if (angleAdjustmentBox != null) {
            angleAdjustmentBox.setVisible(false);
            angleAdjustmentBox.setManaged(false);
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

    public void updateUserImage(WritableImage newImage) {
        userDisplayImage = newImage;

        if (currentMode == ComparisonMode.BLINK && blinkImageView != null && showingUserImage) {
            blinkImageView.setImage(newImage);
            blinkImageView.resetZoom();
        }

        if (currentMode == ComparisonMode.BLEND && opacityUserImageView != null) {
            opacityUserImageView.setImage(newImage);
            opacityUserImageView.resetZoom();
        }

        if (fullscreenImageView != null && fullscreenShowingUserImage) {
            fullscreenImageView.setImage(newImage);
        }

        if (fullscreenUserImageView != null) {
            fullscreenUserImageView.setImage(newImage);
        }
    }

    public void cleanup() {
        if (currentMode != ComparisonMode.NORMAL) {
            exitComparisonMode(null);
        }
        if (fullscreenStage != null) {
            closeFullscreenComparison();
        }
    }

    public void openFullscreenComparison(Slider mainAngleSlider) {
        if (userDisplayImage == null || gongDisplayImage == null) {
            return;
        }

        if (fullscreenStage != null) {
            fullscreenStage.toFront();
            return;
        }

        if (currentMode == ComparisonMode.NORMAL) {
            currentMode = nextFullscreenMode();
        }

        fullscreenStage = new Stage();
        fullscreenStage.initModality(APPLICATION_MODAL);
        fullscreenStage.setMaximized(true);

        var root = new VBox(10);
        root.setStyle("-fx-padding: 10; -fx-background-color: black;");

        var centeringPane = new StackPane();
        centeringPane.setStyle("-fx-alignment: center; -fx-background-color: black;");

        if (currentMode == ComparisonMode.BLEND) {
            setupFullscreenBlendMode(centeringPane);
        } else {
            setupFullscreenBlinkMode(centeringPane);
        }
        VBox.setVgrow(centeringPane, Priority.ALWAYS);

        var controlsPanel = createFullscreenControlsPanel();

        root.getChildren().addAll(centeringPane, controlsPanel);

        var scene = newScene(root);
        fullscreenStage.setScene(scene);

        fullscreenStage.setOnCloseRequest(e -> {
            e.consume();
            closeFullscreenComparison(mainAngleSlider);
        });

        scene.setOnKeyReleased(event -> {
            if (event.getCode() == ESCAPE) {
                event.consume();
                closeFullscreenComparison(mainAngleSlider);
            }
        });

        fullscreenStage.show();
        Platform.runLater(() -> {
            root.requestFocus();
            fullscreenStage.toFront();
        });

        if (currentMode == ComparisonMode.BLINK) {
            startFullscreenBlinkAnimation();
        }
    }

    private void setupFullscreenBlendMode(StackPane centeringPane) {
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
    }

    private void setupFullscreenBlinkMode(StackPane centeringPane) {
        var plainImageView = new ImageView();
        plainImageView.setPreserveRatio(true);
        plainImageView.setSmooth(true);
        plainImageView.fitWidthProperty().bind(centeringPane.widthProperty().multiply(0.9));
        plainImageView.fitHeightProperty().bind(centeringPane.heightProperty().multiply(0.9));

        centeringPane.getChildren().add(plainImageView);
        fullscreenImageView = plainImageView;
    }

    private HBox createFullscreenControlsPanel() {
        var controlsPanel = new HBox(15);
        controlsPanel.setStyle("-fx-alignment: center; -fx-padding: 10; -fx-background-color: #333;");

        var closeButton = new Button(message("button.close.fullscreen"));
        closeButton.getStyleClass().add("dark-close-button");
        closeButton.setOnAction(e -> closeFullscreenComparison());

        fullscreenComparisonModeButton = new Button(getFullscreenComparisonModeButtonText());
        fullscreenComparisonModeButton.getStyleClass().add("dark-button");
        fullscreenComparisonModeButton.setOnAction(e -> toggleFullscreenComparisonMode());

        var comparisonSliderLabel = new Label();
        comparisonSliderLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        var comparisonSlider = new Slider();
        comparisonSlider.getStyleClass().add("dark-slider");
        comparisonSlider.setShowTickLabels(true);
        comparisonSlider.setShowTickMarks(true);
        comparisonSlider.setPrefWidth(200);

        setupFullscreenComparisonSlider(comparisonSliderLabel, comparisonSlider);

        var leftControls = new HBox(10);
        leftControls.setStyle("-fx-alignment: center-left;");

        var centerControls = new HBox(10);
        centerControls.setStyle("-fx-alignment: center;");

        if (transformationListener != null) {
            var horizontalFlipButton = new Button("\u21C4 " + message("orientation.button.horizontal.flip"));
            horizontalFlipButton.getStyleClass().add("dark-button");
            horizontalFlipButton.setOnAction(e -> transformationListener.onHorizontalFlip());

            var verticalFlipButton = new Button("\u21C5 " + message("orientation.button.vertical.flip"));
            verticalFlipButton.getStyleClass().add("dark-button");
            verticalFlipButton.setOnAction(e -> transformationListener.onVerticalFlip());

            var rotateLeftButton = new Button("\u21B6 " + message("orientation.button.rotate.left"));
            rotateLeftButton.getStyleClass().add("dark-button");
            rotateLeftButton.setOnAction(e -> transformationListener.onRotation(-90));

            var rotateRightButton = new Button("\u21B7 " + message("orientation.button.rotate.right"));
            rotateRightButton.getStyleClass().add("dark-button");
            rotateRightButton.setOnAction(e -> transformationListener.onRotation(90));

            var resetButton = new Button(message("orientation.button.reset"));
            resetButton.getStyleClass().add("dark-button");
            resetButton.setOnAction(e -> transformationListener.onReset());

            leftControls.getChildren().addAll(horizontalFlipButton, verticalFlipButton, rotateLeftButton, rotateRightButton, resetButton);

            if (transformationListener.isAutoAlignAvailable()) {
                var autoAlignButton = new Button(message("orientation.button.auto.align"));
                autoAlignButton.getStyleClass().add("dark-button");
                autoAlignButton.setOnAction(e -> transformationListener.onAutoAlign());
                leftControls.getChildren().add(autoAlignButton);
            }

            var angleLabel = new Label(message("fine.tilt.adjust") + ":");
            angleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

            var limit = Math.max(2.0, Math.ceil(Math.abs(angleAdjustment)) + 2);
            var angleSlider = new Slider(-limit, limit, angleAdjustment);
            angleSlider.getStyleClass().add("dark-slider");
            angleSlider.setShowTickLabels(true);
            angleSlider.setShowTickMarks(true);
            angleSlider.setMajorTickUnit(1.0);
            angleSlider.setMinorTickCount(4);
            angleSlider.setPrefWidth(200);
            angleSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                angleAdjustment = newValue.doubleValue();
                transformationListener.onAngleAdjustment(angleAdjustment);
            });

            centerControls.getChildren().addAll(angleLabel, angleSlider, comparisonSliderLabel, comparisonSlider);
        } else {
            centerControls.getChildren().addAll(comparisonSliderLabel, comparisonSlider);
        }

        var rightControls = new HBox(10);
        rightControls.setStyle("-fx-alignment: center-right;");
        rightControls.getChildren().addAll(fullscreenComparisonModeButton, closeButton);

        HBox.setHgrow(leftControls, Priority.NEVER);
        HBox.setHgrow(centerControls, Priority.ALWAYS);
        HBox.setHgrow(rightControls, Priority.NEVER);

        controlsPanel.getChildren().addAll(leftControls, centerControls, rightControls);

        return controlsPanel;
    }

    private void setupFullscreenComparisonSlider(Label label, Slider slider) {
        if (currentMode == ComparisonMode.BLEND) {
            label.setText(message("blend.percent") + ":");
            slider.setMin(0.0);
            slider.setMax(100.0);
            slider.setMajorTickUnit(25.0);
            slider.setMinorTickCount(4);
            slider.setValue(opacityValue);
            slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                opacityValue = newValue.doubleValue();
                if (fullscreenUserImageView != null) {
                    fullscreenUserImageView.setOpacity(opacityValue / 100.0);
                }
            });
        } else {
            label.setText(message("blink.duration") + ":");
            slider.setMin(500.0);
            slider.setMax(5000.0);
            slider.setMajorTickUnit(1000.0);
            slider.setMinorTickCount(4);
            slider.setValue(blinkDurationMs);
            slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                blinkDurationMs = newValue.doubleValue();
                if (fullscreenBlinkTimeline != null) {
                    restartFullscreenBlinkAnimation();
                }
            });
        }
    }

    private ComparisonMode nextFullscreenMode() {
        var next = nextMode();
        if (next == ComparisonMode.NORMAL) {
            next = modeOrder.stream()
                    .filter(m -> m != ComparisonMode.NORMAL)
                    .findFirst()
                    .orElse(ComparisonMode.BLINK);
        }
        return next;
    }

    private void toggleFullscreenComparisonMode() {
        currentMode = nextFullscreenMode();
        rebuildFullscreenUI();
        if (fullscreenComparisonModeButton != null) {
            fullscreenComparisonModeButton.setText(labelForMode(nextFullscreenMode()));
        }
    }

    private void rebuildFullscreenUI() {
        if (fullscreenStage == null) {
            return;
        }

        var scene = fullscreenStage.getScene();
        var root = (VBox) scene.getRoot();
        var centeringPane = (StackPane) root.getChildren().getFirst();

        centeringPane.getChildren().clear();

        if (fullscreenBlinkTimeline != null) {
            fullscreenBlinkTimeline.stop();
            fullscreenBlinkTimeline = null;
        }

        if (currentMode == ComparisonMode.BLEND) {
            setupFullscreenBlendMode(centeringPane);
            fullscreenImageView = null;
        } else {
            setupFullscreenBlinkMode(centeringPane);
            fullscreenBlendPane = null;
            fullscreenUserImageView = null;
            fullscreenGongImageView = null;

            if (currentMode == ComparisonMode.BLINK) {
                startFullscreenBlinkAnimation();
            } else {
                fullscreenImageView.setImage(userDisplayImage);
            }
        }

        rebuildFullscreenControls();
    }

    private void rebuildFullscreenControls() {
        if (fullscreenStage == null) {
            return;
        }

        var scene = fullscreenStage.getScene();
        var root = (VBox) scene.getRoot();

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
        closeFullscreenComparison(null);
    }

    private void closeFullscreenComparison(Slider mainAngleSlider) {
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

        currentMode = ComparisonMode.NORMAL;
        exitComparisonMode(null);

        if (mainAngleSlider != null) {
            mainAngleSlider.setValue(angleAdjustment);
        }
    }

    private String message(String messageKey) {
        return messageFunction.apply(messageKey);
    }
}