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
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 3D viewer for a single solar image using OpenGL.
 * Displays the Sun as a textured hemisphere that can be rotated and zoomed.
 * Preserves the original image colors (RGB or mono).
 */
public class SingleImage3DViewer extends BorderPane {

    private static final int RENDER_WIDTH = 800;
    private static final int RENDER_HEIGHT = 600;

    private static final int VIDEO_FPS = 30;
    private static final int LIVE_CYCLE_DURATION_SECONDS = 20;
    private static final int VIDEO_CYCLE_DURATION_SECONDS = 10;
    private static final double EXPORT_SIZE_FACTOR = 1.5;
    private static final double ANIMATION_FREQ_Y = 2 * Math.PI * 2 / LIVE_CYCLE_DURATION_SECONDS;
    private static final double ANIMATION_FREQ_X = 2 * Math.PI / LIVE_CYCLE_DURATION_SECONDS;
    private static final double ANIMATION_AMPLITUDE_Y = 15.0;
    private static final double ANIMATION_AMPLITUDE_X = 12.0;

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

    private AnimationTimer cameraAnimation;
    private volatile boolean animationActive = true;
    private long animationStartTime;

    private record ExportOptions(Integer resolution, boolean annotate) {}

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
        startCameraAnimation();
    }

    private void startCameraAnimation() {
        animationActive = true;
        animationStartTime = System.nanoTime();

        cameraAnimation = new AnimationTimer() {
            private long lastUpdate = 0;
            private static final long FRAME_INTERVAL_NS = 33_333_333L;

            @Override
            public void handle(long now) {
                if (!animationActive || renderer == null || glImageView == null) {
                    return;
                }

                if (now - lastUpdate < FRAME_INTERVAL_NS) {
                    return;
                }
                lastUpdate = now;

                var elapsedSeconds = (now - animationStartTime) / 1_000_000_000.0;
                try {
                    applyAnimationRotation(elapsedSeconds);
                    glImageView.requestRender();
                } catch (Exception e) {
                    // Ignore exceptions during shutdown
                }
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
        var diameter = Math.max(originalImage.width(), originalImage.height());
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

    private void showOpenGLError(Throwable error) {
        graphPane.getChildren().clear();

        var errorLabel = new Label(I18N.string(JSolEx.class, "single-image-3d", "opengl.error"));
        errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #cc0000; -fx-font-weight: bold;");
        errorLabel.setWrapText(true);

        var detailsLabel = new Label(I18N.string(JSolEx.class, "single-image-3d", "opengl.error.details"));
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

    private HBox createButtonPanel() {
        var prominencesCheckbox = new CheckBox(I18N.string(JSolEx.class, "single-image-3d", "show.prominences"));
        prominencesCheckbox.setSelected(true);
        renderer.setShowProminences(true);
        prominencesCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            renderer.setShowProminences(newVal);
            glImageView.requestRender();
        });
        var resetButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "reset.view"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> resetAndRestartAnimation());

        var exportButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "export.png"));
        exportButton.getStyleClass().add("default-button");
        exportButton.setOnAction(e -> exportToPng());

        var exportVideoButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "export.video"));
        exportVideoButton.getStyleClass().add("default-button");
        exportVideoButton.setOnAction(e -> exportToVideo());

        var buttonBox = new HBox(10, prominencesCheckbox, resetButton, exportButton, exportVideoButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(8));
        buttonBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        return buttonBox;
    }

    private void exportToPng() {
        var annotate = showPngExportDialog();
        if (annotate == null) {
            return;
        }

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
        fileChooser.setInitialFileName("sun_3d.png");
        if (sourceDirectory != null && sourceDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(sourceDirectory);
        }

        var stage = (Stage) getScene().getWindow();
        stage.toFront();
        stage.requestFocus();
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

    private Boolean showPngExportDialog() {
        var stage = (Stage) getScene().getWindow();

        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, "single-image-3d", "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.title"));
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

    private ExportOptions showVideoExportDialog(int nativeSize) {
        var stage = (Stage) getScene().getWindow();

        var resolutions = new int[] { 512, 800, 1024, 2048, nativeSize };

        var resolutionLabel = new Label(I18N.string(JSolEx.class, "single-image-3d", "export.video.resolution"));
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
                    return String.format(I18N.string(JSolEx.class, "single-image-3d", "export.video.resolution.native"), value, value);
                }
                return value + "x" + value;
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });

        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, "single-image-3d", "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, resolutionLabel, resolutionCombo, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.video.title"));
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

    private void exportToVideo() {
        var stage = (Stage) getScene().getWindow();
        stage.toFront();
        stage.requestFocus();

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.video.title"));
        fileChooser.setInitialFileName("sun_3d");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        if (sourceDirectory != null && sourceDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(sourceDirectory);
        }

        var file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

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
            alert.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.video.overwrite.title"));
            alert.setHeaderText(I18N.string(JSolEx.class, "single-image-3d", "export.video.overwrite.header"));
            alert.setContentText(fileNames);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        stopCameraAnimation();

        var frameCount = VIDEO_FPS * VIDEO_CYCLE_DURATION_SECONDS;
        var exportSize = selectedSize;

        var cancelled = new AtomicBoolean(false);

        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        var progressLabel = new Label(I18N.string(JSolEx.class, "single-image-3d", "export.video.progress"));
        var cancelButton = new Button(I18N.string(JSolEx.class, "single-image-3d", "cancel"));

        var progressBox = new VBox(10, progressLabel, progressBar, cancelButton);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));

        var progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(I18N.string(JSolEx.class, "single-image-3d", "export.video.title"));
        progressStage.setScene(new Scene(progressBox));
        progressStage.setResizable(false);
        progressStage.setOnCloseRequest(e -> cancelled.set(true));
        cancelButton.setOnAction(e -> cancelled.set(true));
        progressStage.show();

        var formats = animationFormats;
        var finalBasePath = basePath;
        var annotate = shouldAnnotate;

        new Thread(() -> {
            var exportRendererRef = new AtomicReference<SingleImageSphereRenderer>();
            var exportGlViewRef = new AtomicReference<OpenGLImageView>();
            var initLatch = new CountDownLatch(1);

            Platform.runLater(() -> {
                var exportRenderer = new SingleImageSphereRenderer(originalImage);
                exportRenderer.setShowProminences(renderer.isShowProminences());

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
                var glViewToDispose = exportGlViewRef.get();
                if (glViewToDispose != null) {
                    glViewToDispose.dispose();
                }
                Platform.runLater(() -> {
                    progressStage.close();
                    resetAndRestartAnimation();
                });
            }
        }).start();
    }

    private BufferedImage captureExportFrame(int frameIndex, SingleImageSphereRenderer exportRenderer, OpenGLImageView exportGlView) {
        var elapsedSeconds = (double) frameIndex / VIDEO_FPS * LIVE_CYCLE_DURATION_SECONDS / VIDEO_CYCLE_DURATION_SECONDS;

        var rotationY = (float) (Math.sin(elapsedSeconds * ANIMATION_FREQ_Y) * ANIMATION_AMPLITUDE_Y);
        var rotationX = (float) (Math.sin(elapsedSeconds * ANIMATION_FREQ_X) * ANIMATION_AMPLITUDE_X);
        exportRenderer.setRotation(rotationX, rotationY);

        return exportGlView.renderAndCapture();
    }

    private BufferedImage annotateImage(BufferedImage image) {
        if (processParams == null) {
            return image;
        }
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

        var context = Map.<Class<?>, Object>of(ProcessParams.class, processParams);
        var imageDraw = new ImageDraw(context, Broadcaster.NO_OP);
        var fontSize = Math.min(width, height) / 64;
        var annotated = (ImageWrapper) imageDraw.drawObservationDetails(Map.of(
                "img", wrapper,
                "fs", fontSize
        ));

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
        // First, stop the animation to prevent further render requests
        animationActive = false;
        if (cameraAnimation != null) {
            cameraAnimation.stop();
            cameraAnimation = null;
        }

        // Null out renderer to signal to any pending callbacks that we're disposing
        renderer = null;

        // Give time for any pending animation frame to complete
        // This avoids race conditions with the render thread
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
