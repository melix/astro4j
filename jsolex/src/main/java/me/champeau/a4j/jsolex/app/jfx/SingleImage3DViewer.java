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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.io.File;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 3D viewer for a single solar image using OpenGL.
 * Displays the Sun as a textured hemisphere that can be rotated and zoomed.
 * Preserves the original image colors (RGB or mono).
 */
public class SingleImage3DViewer extends BorderPane {

    private static final int RENDER_WIDTH = 800;
    private static final int RENDER_HEIGHT = 600;
    private static final String I18N_BUNDLE = "single-image-3d";

    private final ImageWrapper originalImage;
    private final ProcessParams processParams;
    private final File sourceDirectory;
    private final StackPane graphPane;

    private OpenGLImageView glImageView;
    private SingleImageSphereRenderer renderer;

    private double anchorX;
    private double anchorY;
    private float anchorRotationX;
    private float anchorRotationY;

    private Viewer3DExportHelper.CameraAnimator cameraAnimator;
    private Viewer3DOverlay overlay;
    private Viewer3DHelpOverlay helpOverlay;

    public SingleImage3DViewer(ImageWrapper image, ProcessParams processParams, File sourceDirectory) {
        this.originalImage = image;
        this.processParams = processParams;
        this.sourceDirectory = sourceDirectory;

        graphPane = new StackPane();
        graphPane.setStyle("-fx-background-color: #000000;");

        initOpenGL();

        setCenter(graphPane);
        setBottom(createButtonPanel());
    }

    private void initOpenGL() {
        renderer = new SingleImageSphereRenderer(originalImage);

        glImageView = new OpenGLImageView(RENDER_WIDTH, RENDER_HEIGHT,
                renderer::loadTextures,
                view -> {
                    if (renderer.needsTextureReload()) {
                        renderer.reloadTextures();
                    }
                    renderer.render(view.getRenderWidth(), view.getRenderHeight());
                },
                renderer::dispose
        );

        glImageView.setOnError(this::showOpenGLError);

        glImageView.setPreserveRatio(false);
        glImageView.setPickOnBounds(true);

        layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            var bottom = getBottom();
            var bottomHeight = bottom != null ? bottom.getLayoutBounds().getHeight() : 0;

            var w = (int) newBounds.getWidth();
            var h = (int) (newBounds.getHeight() - bottomHeight);
            if (w > 0 && h > 0) {
                glImageView.setFitWidth(w);
                glImageView.setFitHeight(h);
                glImageView.requestResize(w, h);
            }
        });

        graphPane.getChildren().add(glImageView);

        setupMouseHandlers();

        cameraAnimator = new Viewer3DExportHelper.CameraAnimator(
                renderer::setRotation,
                glImageView::requestRender,
                () -> {
                    renderer.setRotation(0, 0);
                    renderer.setCameraDistance(3.0f);
                }
        );
        cameraAnimator.start();

        overlay = new Viewer3DOverlay(cameraAnimator);
        graphPane.getChildren().add(overlay);

        helpOverlay = new Viewer3DHelpOverlay(I18N_BUNDLE, true);
        helpOverlay.setMouseTransparent(true);
        graphPane.getChildren().add(helpOverlay);
        var helpButton = helpOverlay.createStandaloneButton();
        graphPane.getChildren().add(helpButton);
    }

    private void showOpenGLError(Throwable error) {
        graphPane.getChildren().clear();

        var errorLabel = new Label(I18N.string(JSolEx.class, I18N_BUNDLE, "opengl.error"));
        errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #cc0000; -fx-font-weight: bold;");
        errorLabel.setWrapText(true);

        var detailsLabel = new Label(I18N.string(JSolEx.class, I18N_BUNDLE, "opengl.error.details"));
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");
        detailsLabel.setWrapText(true);

        var technicalLabel = new Label(error.getMessage());
        technicalLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999999; -fx-font-family: monospace;");
        technicalLabel.setWrapText(true);

        var errorBox = new VBox(15, errorLabel, detailsLabel, technicalLabel);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setPadding(new Insets(40));
        errorBox.setMaxWidth(500);

        graphPane.getChildren().add(errorBox);
        graphPane.setStyle("-fx-background-color: #1a1a24;");
    }

    private void setupMouseHandlers() {
        glImageView.setOnMousePressed(event -> {
            cameraAnimator.stop();
            if (overlay != null) {
                overlay.updatePlayPauseState(false);
            }
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorRotationX = renderer.getRotationX();
            anchorRotationY = renderer.getRotationY();
        });

        glImageView.setOnMouseDragged(event -> {
            var dx = event.getSceneX() - anchorX;
            var dy = event.getSceneY() - anchorY;

            renderer.setRotation(
                    anchorRotationX + (float) dy * 0.5f,
                    anchorRotationY + (float) dx * 0.5f
            );

            glImageView.requestRender();
        });

        glImageView.setOnScroll(event -> {
            cameraAnimator.stop();
            if (overlay != null) {
                overlay.updatePlayPauseState(false);
            }
            var delta = (float) event.getDeltaY() * 0.01f;
            renderer.setCameraDistance(renderer.getCameraDistance() - delta);
            glImageView.requestRender();
        });
    }

    private HBox createButtonPanel() {
        var prominencesCheckbox = new CheckBox(I18N.string(JSolEx.class, I18N_BUNDLE, "show.prominences"));
        prominencesCheckbox.setSelected(true);
        renderer.setShowProminences(true);
        prominencesCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            renderer.setShowProminences(newVal);
            glImageView.requestRender();
        });
        var resetButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "reset.view"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> cameraAnimator.resetAndRestart());

        var exportButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "export.png"));
        exportButton.getStyleClass().add("default-button");
        exportButton.setOnAction(e -> exportToPng());

        var exportVideoButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "export.video"));
        exportVideoButton.getStyleClass().add("default-button");
        exportVideoButton.setOnAction(e -> exportToVideo());

        var buttonBox = new HBox(10, prominencesCheckbox, resetButton, exportButton, exportVideoButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(8));
        buttonBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        return buttonBox;
    }

    private void exportToPng() {
        var stage = (Stage) getScene().getWindow();
        if (overlay != null) {
            overlay.setVisible(false);
        }
        if (helpOverlay != null) {
            helpOverlay.setVisible(false);
        }
        Viewer3DExportHelper.exportToPng(stage, graphPane, I18N_BUNDLE, "sun_3d.png", sourceDirectory, processParams);
        if (overlay != null) {
            overlay.setVisible(true);
        }
        if (helpOverlay != null) {
            helpOverlay.setVisible(true);
        }
    }

    private void exportToVideo() {
        var stage = (Stage) getScene().getWindow();
        var nativeSize = Viewer3DExportHelper.calculateExportResolution(Math.max(originalImage.width(), originalImage.height()));
        var showProminences = renderer.isShowProminences();
        var animParams = cameraAnimator.getAnimationParameters();

        Viewer3DExportHelper.exportToVideo(
                stage,
                I18N_BUNDLE,
                "sun_3d",
                sourceDirectory,
                nativeSize,
                processParams,
                exportSize -> {
                    var exportRenderer = new SingleImageSphereRenderer(originalImage);
                    exportRenderer.setShowProminences(showProminences);

                    var exportGlView = new OpenGLImageView(exportSize, exportSize,
                            exportRenderer::loadTextures,
                            view -> {
                                if (exportRenderer.needsTextureReload()) {
                                    exportRenderer.reloadTextures();
                                }
                                exportRenderer.render(view.getRenderWidth(), view.getRenderHeight());
                            },
                            exportRenderer::dispose
                    );

                    return new Viewer3DExportHelper.ExportContext(exportRenderer, exportGlView);
                },
                (frameIndex, duration, context) -> {
                    var exportRenderer = (SingleImageSphereRenderer) context.renderer();
                    var rotation = Viewer3DExportHelper.calculateExportAnimationRotation(frameIndex, duration, animParams);
                    exportRenderer.setRotation(rotation[0], rotation[1]);
                    return context.glView().renderAndCapture();
                },
                cameraAnimator::stop,
                cameraAnimator::resetAndRestart
        );
    }

    public void dispose() {
        if (cameraAnimator != null) {
            cameraAnimator.dispose();
            cameraAnimator = null;
        }

        renderer = null;

        // Give time for any pending animation frame to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (glImageView != null) {
            glImageView.dispose();
            glImageView = null;
        }
    }

    /**
     * Shows the single image 3D viewer in a new window.
     *
     * @param image           the image to display (mono or RGB)
     * @param title           the window title
     * @param processParams   the process parameters for annotation (may be null)
     * @param sourceDirectory the directory where the source image is located (may be null)
     */
    public static void show(ImageWrapper image, String title, ProcessParams processParams, File sourceDirectory) {
        Platform.runLater(() -> {
            var viewer = new SingleImage3DViewer(image, processParams, sourceDirectory);
            var stage = FXUtils.newStage();
            stage.setTitle(title);
            var scene = newScene(viewer, 1000, 700);
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> viewer.dispose());
            stage.setMaximized(true);
            stage.show();
        });
    }
}
