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
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyData;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.VideoEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 3D viewer for spherical chromospheric tomography using OpenGL.
 * Displays the Sun as concentric semi-transparent spheres, where each sphere
 * represents a different wavelength offset (atmospheric height).
 */
public class SphericalTomography3DViewer extends BorderPane {

    private static final int RENDER_WIDTH = 800;
    private static final int RENDER_HEIGHT = 600;

    // Animation parameters
    private static final int VIDEO_FPS = 30;
    private static final int LIVE_CYCLE_DURATION_SECONDS = 20;
    private static final int VIDEO_CYCLE_DURATION_SECONDS = 10;
    // Export resolution: 1.5x the sun's diameter (square)
    private static final double EXPORT_SIZE_FACTOR = 1.5;
    // Different frequencies for X and Y create a Lissajous pattern
    // Y completes 2 cycles per period, X completes 1 cycle
    private static final double ANIMATION_FREQ_Y = 2 * Math.PI * 2 / LIVE_CYCLE_DURATION_SECONDS;
    private static final double ANIMATION_FREQ_X = 2 * Math.PI / LIVE_CYCLE_DURATION_SECONDS;
    private static final double ANIMATION_AMPLITUDE_Y = 15.0;
    private static final double ANIMATION_AMPLITUDE_X = 12.0;

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

    private AnimationTimer cameraAnimation;
    private volatile boolean animationActive = true;
    private long animationStartTime;

    private ComboBox<RenderMode> renderModeCombo;

    private enum RenderMode {
        SHELLS,
        VOLUME;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spherical-tomography", "render.mode." + name().toLowerCase());
        }
    }

    private record ExportOptions(Integer resolution, boolean annotate) {}

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
                    // If shaders not supported, fall back to shells renderer
                    if (!shadersSupported) {
                        Platform.runLater(() -> renderModeCombo.setValue(RenderMode.SHELLS));
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
        startCameraAnimation();
    }

    private void startCameraAnimation() {
        animationActive = true;
        animationStartTime = System.nanoTime();

        cameraAnimation = new AnimationTimer() {
            private long lastUpdate = 0;
            private static final long FRAME_INTERVAL_NS = 33_333_333L; // ~30fps for smooth animation

            @Override
            public void handle(long now) {
                if (!animationActive) {
                    return;
                }

                // Limit update rate to reduce jerkiness
                if (now - lastUpdate < FRAME_INTERVAL_NS) {
                    return;
                }
                lastUpdate = now;

                var elapsedSeconds = (now - animationStartTime) / 1_000_000_000.0;
                applyAnimationRotation(elapsedSeconds);
                glImageView.requestRender();
            }
        };
        cameraAnimation.start();
    }

    private void applyAnimationRotation(double time) {
        var rotationY = (float) (Math.sin(time * ANIMATION_FREQ_Y) * ANIMATION_AMPLITUDE_Y);
        var rotationX = (float) (Math.sin(time * ANIMATION_FREQ_X) * ANIMATION_AMPLITUDE_X);
        renderer.setRotation(rotationX, rotationY);
    }

    private int calculateExportResolution() {
        // Get the sun's diameter from the first shell image
        var firstShell = data.shells().getFirst();
        var image = firstShell.image();
        var diameter = Math.max(image.width(), image.height());
        // Export at 1.5x the diameter, as a square
        return (int) (diameter * EXPORT_SIZE_FACTOR);
    }

    private void stopCameraAnimation() {
        animationActive = false;
    }

    private void resetAndRestartAnimation() {
        renderer.setRotation(0, 0);
        renderer.setCameraDistance(3.0f);
        animationActive = true;
        animationStartTime = System.nanoTime();
        glImageView.requestRender();
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
        resetAndRestartAnimation();
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
            stopCameraAnimation();
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorRotationX = renderer.getRotationX();
            anchorRotationY = renderer.getRotationY();
        });

        glImageView.setOnMouseDragged(event -> {
            var dx = event.getSceneX() - anchorX;
            var dy = event.getSceneY() - anchorY;

            // Drag up = rotate up (positive X rotation), drag right = rotate right
            renderer.setRotation(
                    anchorRotationX + (float) dy * 0.5f,
                    anchorRotationY + (float) dx * 0.5f
            );

            glImageView.requestRender();
        });

        glImageView.setOnScroll(event -> {
            stopCameraAnimation();
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
        var renderModeLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "render.mode"));
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
        var resetButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "reset.view"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> resetAndRestartAnimation());

        var exportButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "export.png"));
        exportButton.getStyleClass().add("default-button");
        exportButton.setOnAction(e -> exportToPng());

        var exportVideoButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "export.video"));
        exportVideoButton.getStyleClass().add("default-button");
        exportVideoButton.setOnAction(e -> exportToVideo());

        var buttonBox = new HBox(10, resetButton, exportButton, exportVideoButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(8));
        buttonBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        return buttonBox;
    }

    private void exportToPng() {
        // First, show export options dialog
        var annotate = showPngExportDialog();
        if (annotate == null) {
            return;
        }

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
        fileChooser.setInitialFileName("spherical_tomography.png");

        var stage = (Stage) getScene().getWindow();
        var file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            var snapshot = graphPane.snapshot(null, null);
            var bufferedImage = new BufferedImage(
                    (int) snapshot.getWidth(),
                    (int) snapshot.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            for (var y = 0; y < snapshot.getHeight(); y++) {
                for (var x = 0; x < snapshot.getWidth(); x++) {
                    bufferedImage.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                }
            }
            if (annotate) {
                bufferedImage = annotateImage(bufferedImage);
            }
            try {
                ImageIO.write(bufferedImage, "png", file);
            } catch (IOException e) {
                // Silently ignore
            }
        }
    }

    private ExportOptions showVideoExportDialog(int nativeSize) {
        var stage = (Stage) getScene().getWindow();

        var resolutions = new int[] { 512, 800, 1024, 2048, nativeSize };

        var resolutionLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "export.video.resolution"));
        var resolutionCombo = new ComboBox<Integer>();
        for (var res : resolutions) {
            if (!resolutionCombo.getItems().contains(res)) {
                resolutionCombo.getItems().add(res);
            }
        }
        resolutionCombo.setValue(512);
        resolutionCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                if (value == null) {
                    return "";
                }
                if (value == nativeSize) {
                    return String.format(I18N.string(JSolEx.class, "spherical-tomography", "export.video.resolution.native"), value, value);
                }
                return value + "x" + value;
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });

        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, "spherical-tomography", "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "export.video.cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, resolutionLabel, resolutionCombo, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.video.title"));
        dialogStage.setScene(new Scene(content));
        dialogStage.setResizable(false);

        var result = new AtomicReference<ExportOptions>();
        okButton.setOnAction(e -> {
            result.set(new ExportOptions(resolutionCombo.getValue(), annotateCheckbox.isSelected()));
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> {
            result.set(null);
            dialogStage.close();
        });

        dialogStage.showAndWait();
        return result.get();
    }

    private Boolean showPngExportDialog() {
        var stage = (Stage) getScene().getWindow();

        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, "spherical-tomography", "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "export.video.cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.title"));
        dialogStage.setScene(new Scene(content));
        dialogStage.setResizable(false);

        var result = new AtomicReference<Boolean>();
        okButton.setOnAction(e -> {
            result.set(annotateCheckbox.isSelected());
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> {
            result.set(null);
            dialogStage.close();
        });

        dialogStage.showAndWait();
        return result.get();
    }

    private void saveImage(WritableImage image, File file) {
        var bufferedImage = new BufferedImage(
                (int) image.getWidth(),
                (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                bufferedImage.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            // Silently ignore
        }
    }

    private void exportToVideo() {
        var stage = (Stage) getScene().getWindow();

        // First, choose where to save
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.video.title"));
        fileChooser.setInitialFileName("spherical_tomography");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));

        var file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        // Then ask for resolution and annotation options
        var nativeSize = calculateExportResolution();
        var exportOptions = showVideoExportDialog(nativeSize);
        if (exportOptions == null || exportOptions.resolution() == null) {
            return;
        }
        var selectedSize = exportOptions.resolution();
        var shouldAnnotate = exportOptions.annotate();

        var animationFormats = Configuration.getInstance().getAnimationFormats();
        if (animationFormats.isEmpty()) {
            animationFormats = EnumSet.of(AnimationFormat.MP4);
        }

        var basePath = file.getAbsolutePath();
        var lastDot = basePath.lastIndexOf('.');
        var lastSep = Math.max(basePath.lastIndexOf('/'), basePath.lastIndexOf(File.separatorChar));
        if (lastDot > lastSep && lastDot > 0) {
            basePath = basePath.substring(0, lastDot);
        }

        var existingFiles = new ArrayList<File>();
        for (var format : animationFormats) {
            var outputFile = new File(basePath + "." + format.name().toLowerCase());
            if (outputFile.exists()) {
                existingFiles.add(outputFile);
            }
        }

        if (!existingFiles.isEmpty()) {
            var fileNames = existingFiles.stream()
                    .map(File::getName)
                    .collect(Collectors.joining(", "));
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.video.overwrite.title"));
            alert.setHeaderText(I18N.string(JSolEx.class, "spherical-tomography", "export.video.overwrite.header"));
            alert.setContentText(fileNames);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        // Stop any current animation
        stopCameraAnimation();

        var frameCount = VIDEO_FPS * VIDEO_CYCLE_DURATION_SECONDS;
        var exportSize = selectedSize;

        var cancelled = new AtomicBoolean(false);

        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        var progressLabel = new Label(I18N.string(JSolEx.class, "spherical-tomography", "export.video.progress"));
        var cancelButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "export.video.cancel"));

        var progressBox = new VBox(10, progressLabel, progressBar, cancelButton);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));

        var progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(I18N.string(JSolEx.class, "spherical-tomography", "export.video.title"));
        progressStage.setScene(new Scene(progressBox));
        progressStage.setResizable(false);
        progressStage.setOnCloseRequest(e -> cancelled.set(true));
        cancelButton.setOnAction(e -> cancelled.set(true));
        progressStage.show();

        var formats = animationFormats;
        var finalBasePath = basePath;
        var currentlyUsingVolume = renderer instanceof VolumeOpenGLSphereRenderer;
        var annotate = shouldAnnotate;

        new Thread(() -> {
            // Create a dedicated export renderer and GL view
            var exportRendererRef = new AtomicReference<SphereRenderer>();
            var exportGlViewRef = new AtomicReference<OpenGLImageView>();
            var initLatch = new CountDownLatch(1);

            Platform.runLater(() -> {
                SphereRenderer exportRenderer;
                if (currentlyUsingVolume) {
                    exportRenderer = new VolumeOpenGLSphereRenderer(data);
                } else {
                    exportRenderer = new OpenGLSphereRenderer(data);
                }

                // Copy settings from main renderer
                exportRenderer.setRadialExaggeration(renderer.getRadialExaggeration());
                exportRenderer.setColorMap(renderer.getColorMap());
                exportRenderer.setShowProminences(renderer.isShowProminences());
                exportRenderer.setContrastEnhanced(renderer.isContrastEnhanced());
                // Copy shell visibility
                for (var shell : data.shells()) {
                    exportRenderer.setShellVisible(shell.pixelShift(), renderer.isShellVisible(shell.pixelShift()));
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

                exportRendererRef.set(exportRenderer);
                exportGlViewRef.set(exportGlView);
                initLatch.countDown();
            });

            try {
                initLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Wait for GL context to fully initialize (textures loaded, etc.)
            var exportGlView = exportGlViewRef.get();
            if (exportGlView == null || !exportGlView.waitForInitialization(10000)) {
                Platform.runLater(() -> {
                    progressStage.close();
                    var alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Export Error");
                    alert.setContentText("Failed to initialize OpenGL context for export");
                    alert.showAndWait();
                    resetAndRestartAnimation();
                });
                return;
            }

            List<File> outputFiles = null;
            try {
                Consumer<Double> progressCallback = progress -> Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    progressLabel.setText((int) (progress * 100) + "%");
                });

                outputFiles = VideoEncoder.encodeToMultipleFormats(
                        finalBasePath,
                        formats,
                        frameCount,
                        VIDEO_FPS,
                        1000 / VIDEO_FPS,
                        idx -> {
                            if (cancelled.get()) {
                                return null;
                            }
                            var frame = captureExportFrame(idx, exportRendererRef.get(), exportGlViewRef.get());
                            return annotate ? annotateImage(frame) : frame;
                        },
                        progressCallback
                );

                if (cancelled.get() && outputFiles != null) {
                    for (var outputFile : outputFiles) {
                        if (outputFile.exists()) {
                            outputFile.delete();
                        }
                    }
                }
            } catch (IOException e) {
                if (!cancelled.get()) {
                    Platform.runLater(() -> {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Export Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            } finally {
                // Dispose export GL view
                var glViewToDispose = exportGlViewRef.get();
                if (glViewToDispose != null) {
                    glViewToDispose.dispose();
                }
                Platform.runLater(() -> {
                    progressStage.close();
                    // Restart animation after export
                    resetAndRestartAnimation();
                });
            }
        }).start();
    }

    private BufferedImage captureExportFrame(int frameIndex, SphereRenderer exportRenderer, OpenGLImageView exportGlView) {
        // Map frame index to one complete cycle duration for live animation frequency
        var elapsedSeconds = (double) frameIndex / VIDEO_FPS * LIVE_CYCLE_DURATION_SECONDS / VIDEO_CYCLE_DURATION_SECONDS;

        var rotationY = (float) (Math.sin(elapsedSeconds * ANIMATION_FREQ_Y) * ANIMATION_AMPLITUDE_Y);
        var rotationX = (float) (Math.sin(elapsedSeconds * ANIMATION_FREQ_X) * ANIMATION_AMPLITUDE_X);
        exportRenderer.setRotation(rotationX, rotationY);

        // Use synchronous render and capture to ensure consistent results
        return exportGlView.renderAndCapture();
    }

    private BufferedImage annotateImage(BufferedImage image) {
        if (processParams == null) {
            return image;
        }
        // Convert BufferedImage to RGBImage with ProcessParams metadata
        var width = image.getWidth();
        var height = image.getHeight();
        var r = new float[height][width];
        var g = new float[height][width];
        var b = new float[height][width];
        var rgbArray = image.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = rgbArray[y * width + x];
                r[y][x] = ((rgb >> 16) & 0xFF) << 8;
                g[y][x] = ((rgb >> 8) & 0xFF) << 8;
                b[y][x] = (rgb & 0xFF) << 8;
            }
        }
        var metadata = new HashMap<Class<?>, Object>();
        metadata.put(ProcessParams.class, processParams);
        var wrapper = new RGBImage(width, height, r, g, b, metadata);

        // Create ImageDraw and apply annotation
        var context = Map.<Class<?>, Object>of(ProcessParams.class, processParams);
        var imageDraw = new ImageDraw(context, Broadcaster.NO_OP);
        var fontSize = Math.min(width, height) / 64;
        var annotated = (ImageWrapper) imageDraw.drawObservationDetails(Map.of(
                "img", wrapper,
                "fs", fontSize
        ));

        // Convert back to BufferedImage
        return toBufferedImage(annotated);
    }

    private static BufferedImage toBufferedImage(ImageWrapper wrapper) {
        if (wrapper instanceof RGBImage rgb) {
            var width = rgb.width();
            var height = rgb.height();
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var r = rgb.r();
            var g = rgb.g();
            var b = rgb.b();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rv = Math.round(r[y][x]);
                    int gv = Math.round(g[y][x]);
                    int bv = Math.round(b[y][x]);
                    rv = (rv >> 8) & 0xFF;
                    gv = (gv >> 8) & 0xFF;
                    bv = (bv >> 8) & 0xFF;
                    image.setRGB(x, y, (rv << 16) | (gv << 8) | bv);
                }
            }
            return image;
        }
        throw new IllegalArgumentException("Unsupported image type: " + wrapper.getClass());
    }

    public void dispose() {
        // Stop the animation timer
        if (cameraAnimation != null) {
            cameraAnimation.stop();
            cameraAnimation = null;
        }
        animationActive = false;

        // Dispose the glImageView - it will call renderer.dispose() on the OpenGL thread
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
