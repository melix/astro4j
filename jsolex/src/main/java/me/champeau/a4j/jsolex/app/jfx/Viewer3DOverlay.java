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

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Overlay for 3D viewers showing rotation hints and play/pause controls.
 * This overlay should not be included in exports.
 */
public class Viewer3DOverlay extends StackPane {

    private static final String I18N_BUNDLE = "viewer-3d-overlay";
    private static final String PLAY_ICON = "\u25B6";
    private static final String PAUSE_ICON = "\u23F8";
    private static final double BUTTON_SIZE = 44;
    private static final double PATTERN_PREVIEW_SIZE = 48;
    private static final int LISSAJOUS_SAMPLES = 200;
    private static final double POSITION_DOT_RADIUS = 4;

    private final Button playPauseButton;
    private final Viewer3DExportHelper.CameraAnimator cameraAnimator;
    private final StackPane patternPreview;
    private final Circle positionDot;
    private final AnimationTimer dotAnimator;
    private long animationStartTime;
    private boolean isPlaying = true;

    public Viewer3DOverlay(Viewer3DExportHelper.CameraAnimator cameraAnimator) {
        this.cameraAnimator = cameraAnimator;

        setPickOnBounds(false);
        setMouseTransparent(false);

        positionDot = new Circle(POSITION_DOT_RADIUS);
        positionDot.setFill(Color.YELLOW);
        positionDot.setMouseTransparent(true);

        patternPreview = createPatternPreview();
        var controlsBox = createControlsBox();
        StackPane.setAlignment(controlsBox, Pos.BOTTOM_LEFT);
        getChildren().add(controlsBox);

        playPauseButton = (Button) ((HBox) controlsBox.getChildren().get(1)).getChildren().getFirst();

        cameraAnimator.setOnPatternChanged(this::updatePatternPreview);

        dotAnimator = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateDotPosition();
            }
        };
        animationStartTime = System.nanoTime();
        dotAnimator.start();
    }

    private VBox createControlsBox() {
        var hintLabel = new Label(I18N.string(Viewer3DOverlay.class, I18N_BUNDLE, "hint"));
        hintLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        hintLabel.setTextFill(Color.WHITE);
        hintLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 8 12 8 12; -fx-background-radius: 6;");

        var playPauseBtn = new Button(PAUSE_ICON);
        playPauseBtn.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        playPauseBtn.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        playPauseBtn.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        playPauseBtn.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 20px; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
        );
        playPauseBtn.setOnAction(e -> togglePlayPause());

        playPauseBtn.setOnMouseEntered(e ->
                playPauseBtn.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.8); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 20px; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;"
                )
        );
        playPauseBtn.setOnMouseExited(e ->
                playPauseBtn.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 20px; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;"
                )
        );

        var rotationHint = createRotationHint();

        var buttonBox = new HBox(12, playPauseBtn, patternPreview, rotationHint);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        var controlsBox = new VBox(10, hintLabel, buttonBox);
        controlsBox.setAlignment(Pos.BOTTOM_LEFT);
        controlsBox.setStyle("-fx-padding: 15;");
        controlsBox.setPickOnBounds(false);

        return controlsBox;
    }

    private StackPane createRotationHint() {
        var circle = new Circle(24);
        circle.setFill(Color.rgb(0, 0, 0, 0.6));
        circle.setStroke(Color.rgb(255, 255, 255, 0.6));
        circle.setStrokeWidth(1.5);

        var arrowUp = createArrow(0);
        var arrowRight = createArrow(90);
        var arrowDown = createArrow(180);
        var arrowLeft = createArrow(270);

        arrowUp.setTranslateY(-10);
        arrowDown.setTranslateY(10);
        arrowLeft.setTranslateX(-10);
        arrowRight.setTranslateX(10);

        var hint = new StackPane(circle, arrowUp, arrowRight, arrowDown, arrowLeft);
        hint.setPickOnBounds(false);
        return hint;
    }

    private Polygon createArrow(double rotation) {
        var arrow = new Polygon(0, -7, 5, 0, -5, 0);
        arrow.setFill(Color.rgb(255, 255, 255, 0.8));
        arrow.setRotate(rotation);
        return arrow;
    }

    private StackPane createPatternPreview() {
        var background = new Circle(PATTERN_PREVIEW_SIZE / 2);
        background.setFill(Color.rgb(0, 0, 0, 0.6));
        background.setStroke(Color.rgb(255, 255, 255, 0.6));
        background.setStrokeWidth(1.5);

        var curve = createLissajousCurve(cameraAnimator.getAnimationParameters());

        var preview = new StackPane(background, curve, positionDot);
        preview.setCursor(Cursor.HAND);
        preview.setOnMouseClicked(e -> {
            cameraAnimator.nextPattern();
            updatePlayPauseState(true);
        });

        var tooltip = new Tooltip(I18N.string(Viewer3DOverlay.class, I18N_BUNDLE, "pattern.tooltip"));
        Tooltip.install(preview, tooltip);

        preview.setOnMouseEntered(e ->
                background.setFill(Color.rgb(0, 0, 0, 0.8))
        );
        preview.setOnMouseExited(e ->
                background.setFill(Color.rgb(0, 0, 0, 0.6))
        );

        return preview;
    }

    private void updatePatternPreview() {
        var curve = createLissajousCurve(cameraAnimator.getAnimationParameters());
        if (patternPreview.getChildren().size() > 1) {
            patternPreview.getChildren().set(1, curve);
        }
    }

    private Polyline createLissajousCurve(Viewer3DExportHelper.AnimationParameters params) {
        var points = new double[LISSAJOUS_SAMPLES * 2 + 2];
        double scale = (PATTERN_PREVIEW_SIZE / 2) - 6;

        for (int i = 0; i <= LISSAJOUS_SAMPLES; i++) {
            double t = (2 * Math.PI * i) / LISSAJOUS_SAMPLES;
            // X-axis (horizontal) shows left-right rotation (rotationY uses freqMultiplierY)
            // Y-axis (vertical) shows up-down rotation (rotationX uses freqMultiplierX)
            double x = Math.sin(t * params.freqMultiplierY() + params.phaseY()) * scale;
            double y = Math.sin(t * params.freqMultiplierX() + params.phaseX()) * scale;
            points[i * 2] = x;
            points[i * 2 + 1] = y;
        }

        var polyline = new Polyline(points);
        polyline.setStroke(Color.rgb(255, 80, 80, 0.9));
        polyline.setStrokeWidth(1.5);
        polyline.setFill(null);
        return polyline;
    }

    private void updateDotPosition() {
        if (!isPlaying) {
            return;
        }
        var params = cameraAnimator.getAnimationParameters();
        double scale = (PATTERN_PREVIEW_SIZE / 2) - 6;
        double elapsedSeconds = (System.nanoTime() - animationStartTime) / 1_000_000_000.0;
        double t = elapsedSeconds * Viewer3DExportHelper.BASE_ANIMATION_FREQ;

        // X-axis (horizontal) shows left-right rotation (rotationY uses freqMultiplierY)
        // Y-axis (vertical) shows up-down rotation (rotationX uses freqMultiplierX)
        double x = Math.sin(t * params.freqMultiplierY() + params.phaseY()) * scale;
        double y = Math.sin(t * params.freqMultiplierX() + params.phaseX()) * scale;

        positionDot.setTranslateX(x);
        positionDot.setTranslateY(y);
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        if (isPlaying) {
            playPauseButton.setText(PAUSE_ICON);
            animationStartTime = System.nanoTime();
            cameraAnimator.resetAndRestart();
        } else {
            playPauseButton.setText(PLAY_ICON);
            cameraAnimator.stop();
        }
    }

    /**
     * Updates the play/pause button state to reflect the current animation state.
     * Call this when the animation is stopped externally (e.g., by user dragging).
     */
    public void updatePlayPauseState(boolean playing) {
        isPlaying = playing;
        playPauseButton.setText(playing ? PAUSE_ICON : PLAY_ICON);
        if (playing) {
            animationStartTime = System.nanoTime();
        }
    }

    /**
     * Returns whether the animation is currently playing.
     */
    public boolean isPlaying() {
        return isPlaying;
    }
}
