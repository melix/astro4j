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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 3D viewer for spherical chromospheric tomography using OpenGL.
 * Displays the Sun as concentric semi-transparent spheres, where each sphere
 * represents a different wavelength offset (atmospheric height).
 */
public class SphericalTomography3DViewer extends BorderPane {

    private static final int RENDER_WIDTH = 800;
    private static final int RENDER_HEIGHT = 600;
    private static final String I18N_BUNDLE = "spherical-tomography";

    private final SphericalTomographyData data;
    private final ProcessParams processParams;
    private final StackPane graphPane;

    private OpenGLImageView glImageView;
    private SphereRenderer renderer;
    private boolean shadersSupported = false;

    private double anchorX;
    private double anchorY;
    private float anchorRotationX;
    private float anchorRotationY;

    private Viewer3DExportHelper.CameraAnimator cameraAnimator;
    private Viewer3DOverlay overlay;

    private Label renderModeLabel;
    private ComboBox<RenderMode> renderModeCombo;

    private enum RenderMode {
        SHELLS,
        VOLUME;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, I18N_BUNDLE, "render.mode." + name().toLowerCase());
        }
    }

    public SphericalTomography3DViewer(SphericalTomographyData data, ProcessParams processParams) {
        this.data = data;
        this.processParams = processParams;

        graphPane = new StackPane();
        graphPane.setStyle("-fx-background-color: #000000;");

        initOpenGL();

        setCenter(graphPane);
        setRight(createControlPanel());
        setTop(createDescriptionPanel());
        setBottom(createButtonPanel());
    }

    private void initOpenGL() {
        renderer = new VolumeOpenGLSphereRenderer(data);

        glImageView = new OpenGLImageView(RENDER_WIDTH, RENDER_HEIGHT,
                () -> {
                    renderer.loadTextures();
                    shadersSupported = glImageView.areShadersSupported();
                    // If shaders not supported, fall back to shells renderer and hide the selector
                    if (!shadersSupported) {
                        Platform.runLater(() -> {
                            renderModeCombo.setValue(RenderMode.SHELLS);
                            renderModeLabel.setVisible(false);
                            renderModeLabel.setManaged(false);
                            renderModeCombo.setVisible(false);
                            renderModeCombo.setManaged(false);
                        });
                    }
                },
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
            var right = getRight();
            var top = getTop();
            var bottom = getBottom();
            var rightWidth = right != null ? right.getLayoutBounds().getWidth() : 0;
            var topHeight = top != null ? top.getLayoutBounds().getHeight() : 0;
            var bottomHeight = bottom != null ? bottom.getLayoutBounds().getHeight() : 0;

            var w = (int) (newBounds.getWidth() - rightWidth);
            var h = (int) (newBounds.getHeight() - topHeight - bottomHeight);
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
    }

    private void switchRenderer(RenderMode mode) {
        if (!shadersSupported && mode == RenderMode.VOLUME) {
            return;
        }

        var currentRotationX = renderer.getRotationX();
        var currentRotationY = renderer.getRotationY();
        var currentCameraDistance = renderer.getCameraDistance();
        var currentRadialExaggeration = renderer.getRadialExaggeration();
        var currentColorMap = renderer.getColorMap();
        var currentShowProminences = renderer.isShowProminences();
        var currentContrastEnhanced = renderer.isContrastEnhanced();

        SphereRenderer newRenderer;
        if (mode == RenderMode.VOLUME) {
            newRenderer = new VolumeOpenGLSphereRenderer(data);
        } else {
            newRenderer = new OpenGLSphereRenderer(data);
        }

        newRenderer.setRotation(currentRotationX, currentRotationY);
        newRenderer.setCameraDistance(currentCameraDistance);
        newRenderer.setRadialExaggeration(currentRadialExaggeration);
        newRenderer.setColorMap(currentColorMap);
        newRenderer.setShowProminences(currentShowProminences);
        newRenderer.setContrastEnhanced(currentContrastEnhanced);

        for (var shell : data.shells()) {
            newRenderer.setShellVisible(shell.pixelShift(), renderer.isShellVisible(shell.pixelShift()));
        }

        glImageView.switchRenderer(
                newRenderer::loadTextures,
                view -> {
                    if (newRenderer.needsTextureReload()) {
                        newRenderer.reloadTextures();
                    }
                    newRenderer.render(view.getRenderWidth(), view.getRenderHeight());
                },
                newRenderer::dispose
        );

        renderer = newRenderer;

        // Restart animation after switching
        cameraAnimator.resetAndRestart();
    }

    private void showOpenGLError(Throwable error) {
        graphPane.getChildren().clear();

        var errorLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "opengl.error"));
        errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #cc0000; -fx-font-weight: bold;");
        errorLabel.setWrapText(true);

        var detailsLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "opengl.error.details"));
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

    private HBox createDescriptionPanel() {
        var titleLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "title"));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        var wavelengthLabel = new Label(String.format(Locale.US, "%s: %.2f Å",
                I18N.string(JSolEx.class, "spherical-tomography", "center.wavelength"),
                data.centerWavelength().angstroms()));

        var info = new HBox(20, titleLabel, new Separator(Orientation.VERTICAL), wavelengthLabel);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setPadding(new Insets(8));
        info.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        return info;
    }

    private VBox createControlPanel() {
        renderModeLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "render.mode"));
        renderModeCombo = new ComboBox<>();
        renderModeCombo.getItems().addAll(RenderMode.values());
        renderModeCombo.setValue(RenderMode.VOLUME);
        renderModeCombo.setMaxWidth(Double.MAX_VALUE);
        renderModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal != null && newVal != null && newVal != oldVal) {
                if (newVal == RenderMode.VOLUME && !shadersSupported) {
                    renderModeCombo.setValue(RenderMode.SHELLS);
                } else {
                    switchRenderer(newVal);
                }
            }
        });

        var exaggerationLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "radial.exaggeration"));
        var exaggerationSlider = new Slider(0.01, 1.0, 0.2);
        exaggerationSlider.setShowTickLabels(true);
        exaggerationSlider.setShowTickMarks(true);
        exaggerationSlider.setMajorTickUnit(0.2);
        exaggerationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            renderer.setRadialExaggeration(newVal.floatValue());
            glImageView.requestRender();
        });

        var colormapLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "colormap"));
        var colormapCombo = new ComboBox<SphereRenderer.ColorMap>();
        colormapCombo.getItems().addAll(SphereRenderer.ColorMap.values());
        colormapCombo.setValue(SphereRenderer.ColorMap.MONO);
        colormapCombo.setMaxWidth(Double.MAX_VALUE);
        colormapCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                renderer.setColorMap(newVal);
                glImageView.requestRender();
            }
        });

        var prominencesCheckbox = new CheckBox(I18N.string(JSolEx.class, "spherical-tomography", "show.prominences"));
        prominencesCheckbox.setSelected(false);
        prominencesCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            renderer.setShowProminences(newVal);
            glImageView.requestRender();
        });

        var contrastCheckbox = new CheckBox(I18N.string(JSolEx.class, "spherical-tomography", "contrast.enhanced"));
        contrastCheckbox.setSelected(false);
        contrastCheckbox.setDisable(!data.hasEnhancedShells());
        contrastCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            renderer.setContrastEnhanced(newVal);
            glImageView.requestRender();
        });

        var layersLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "layers"));
        layersLabel.setStyle("-fx-font-weight: bold;");

        var layersBox = new VBox(5);
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (var shell : data.shells()) {
            var pixelShift = shell.pixelShift();
            var angstroms = shell.wavelengthOffset();
            var label = String.format(Locale.US, "%+.2f Å", angstroms);
            var checkBox = new CheckBox(label);
            checkBox.setSelected(true);
            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                var selectedCount = checkBoxes.stream().filter(CheckBox::isSelected).count();
                if (!newVal && selectedCount == 0) {
                    // Prevent unselecting the last layer
                    checkBox.setSelected(true);
                    return;
                }
                renderer.setShellVisible(pixelShift, newVal);
                if (selectedCount == 1) {
                    // Only one layer left, set exaggeration to minimum
                    exaggerationSlider.setValue(exaggerationSlider.getMin());
                } else if (newVal && selectedCount == 2 && exaggerationSlider.getValue() == exaggerationSlider.getMin()) {
                    // Layer was added and exaggeration is at minimum, reset to default
                    exaggerationSlider.setValue(0.2);
                }
                glImageView.requestRender();
            });
            checkBoxes.add(checkBox);
            layersBox.getChildren().add(checkBox);
        }

        var minOffset = data.shells().stream()
                .mapToDouble(SphericalTomographyData.ShellData::wavelengthOffset)
                .min()
                .orElse(0);
        var maxOffset = data.shells().stream()
                .mapToDouble(SphericalTomographyData.ShellData::wavelengthOffset)
                .max()
                .orElse(0);

        var showLowestBtn = new Button(String.format(Locale.US, "%+.2f Å", minOffset));
        showLowestBtn.getStyleClass().add("default-button");
        showLowestBtn.setMaxWidth(Double.MAX_VALUE);
        showLowestBtn.setOnAction(e -> {
            // First select the target to ensure at least one is selected
            for (var i = 0; i < checkBoxes.size(); i++) {
                var shell = data.shells().get(i);
                if (Math.abs(shell.wavelengthOffset() - minOffset) < 0.001) {
                    checkBoxes.get(i).setSelected(true);
                }
            }
            // Then deselect all others
            for (var i = 0; i < checkBoxes.size(); i++) {
                var shell = data.shells().get(i);
                if (Math.abs(shell.wavelengthOffset() - minOffset) >= 0.001) {
                    checkBoxes.get(i).setSelected(false);
                }
            }
        });

        var showHighestBtn = new Button(String.format(Locale.US, "%+.2f Å", maxOffset));
        showHighestBtn.getStyleClass().add("default-button");
        showHighestBtn.setMaxWidth(Double.MAX_VALUE);
        showHighestBtn.setOnAction(e -> {
            // First select the target to ensure at least one is selected
            for (var i = 0; i < checkBoxes.size(); i++) {
                var shell = data.shells().get(i);
                if (Math.abs(shell.wavelengthOffset() - maxOffset) < 0.001) {
                    checkBoxes.get(i).setSelected(true);
                }
            }
            // Then deselect all others
            for (var i = 0; i < checkBoxes.size(); i++) {
                var shell = data.shells().get(i);
                if (Math.abs(shell.wavelengthOffset() - maxOffset) >= 0.001) {
                    checkBoxes.get(i).setSelected(false);
                }
            }
        });

        var showAllBtn = new Button(I18N.string(JSolEx.class, "spherical-tomography", "show.all"));
        showAllBtn.getStyleClass().add("default-button");
        showAllBtn.setMaxWidth(Double.MAX_VALUE);
        showAllBtn.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(true)));

        var quickButtons = new VBox(5, showLowestBtn, showHighestBtn, showAllBtn);

        var layersScroll = new ScrollPane(layersBox);
        layersScroll.setFitToWidth(true);
        layersScroll.setMaxHeight(200);
        layersScroll.setStyle("-fx-background-color: transparent;");

        var interpretationText = I18N.string(JSolEx.class, "spherical-tomography", "interpretation");
        var interpretLabel = new Label(interpretationText);
        interpretLabel.setWrapText(true);
        interpretLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        interpretLabel.setMaxWidth(170);
        interpretLabel.setTooltip(new Tooltip(interpretationText));

        var panel = new VBox(10,
                renderModeLabel, renderModeCombo,
                new Separator(),
                exaggerationLabel, exaggerationSlider,
                colormapLabel, colormapCombo,
                prominencesCheckbox,
                contrastCheckbox,
                new Separator(),
                layersLabel, quickButtons, layersScroll,
                new Separator(),
                interpretLabel
        );
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 0 1;");
        panel.setMinWidth(180);
        panel.setMaxWidth(180);

        return panel;
    }

    private HBox createButtonPanel() {
        var resetButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "reset.view"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> cameraAnimator.resetAndRestart());

        var exportButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "export.png"));
        exportButton.getStyleClass().add("default-button");
        exportButton.setOnAction(e -> exportToPng());

        var exportVideoButton = new Button(I18N.string(JSolEx.class, I18N_BUNDLE, "export.video"));
        exportVideoButton.getStyleClass().add("default-button");
        exportVideoButton.setOnAction(e -> exportToVideo());

        var buttonBox = new HBox(10, resetButton, exportButton, exportVideoButton);
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
        Viewer3DExportHelper.exportToPng(stage, graphPane, I18N_BUNDLE, "spherical_tomography.png", null, processParams);
        if (overlay != null) {
            overlay.setVisible(true);
        }
    }

    private void exportToVideo() {
        var stage = (Stage) getScene().getWindow();
        var firstShell = data.shells().getFirst();
        var image = firstShell.image();
        var nativeSize = Viewer3DExportHelper.calculateExportResolution(Math.max(image.width(), image.height()));
        var currentlyUsingVolume = renderer instanceof VolumeOpenGLSphereRenderer;
        var radialExaggeration = renderer.getRadialExaggeration();
        var colorMap = renderer.getColorMap();
        var showProminences = renderer.isShowProminences();
        var contrastEnhanced = renderer.isContrastEnhanced();
        var animParams = cameraAnimator.getAnimationParameters();
        var shellVisibility = new java.util.HashMap<Double, Boolean>();
        for (var shell : data.shells()) {
            shellVisibility.put(shell.pixelShift(), renderer.isShellVisible(shell.pixelShift()));
        }

        Viewer3DExportHelper.exportToVideo(
                stage,
                I18N_BUNDLE,
                "spherical_tomography",
                null,
                nativeSize,
                processParams,
                exportSize -> {
                    SphereRenderer exportRenderer;
                    if (currentlyUsingVolume) {
                        exportRenderer = new VolumeOpenGLSphereRenderer(data);
                    } else {
                        exportRenderer = new OpenGLSphereRenderer(data);
                    }

                    exportRenderer.setRadialExaggeration(radialExaggeration);
                    exportRenderer.setColorMap(colorMap);
                    exportRenderer.setShowProminences(showProminences);
                    exportRenderer.setContrastEnhanced(contrastEnhanced);
                    for (var entry : shellVisibility.entrySet()) {
                        exportRenderer.setShellVisible(entry.getKey(), entry.getValue());
                    }

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
                    var exportRenderer = (SphereRenderer) context.renderer();
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

        if (glImageView != null) {
            glImageView.dispose();
        }
    }

    /**
     * Shows the spherical tomography viewer in a new window.
     *
     * @param data          the tomography data to display
     * @param title         the window title
     * @param processParams the process parameters for annotation (may be null)
     */
    public static void show(SphericalTomographyData data, String title, ProcessParams processParams) {
        Platform.runLater(() -> {
            var viewer = new SphericalTomography3DViewer(data, processParams);
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
