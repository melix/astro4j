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
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * Helper class for video and image export functionality shared between 3D viewers.
 */
public final class Viewer3DExportHelper {

    public static final int VIDEO_FPS = 30;
    public static final int LIVE_CYCLE_DURATION_SECONDS = 20;
    public static final int VIDEO_CYCLE_DURATION_SECONDS = 10;
    public static final double EXPORT_SIZE_FACTOR = 1.5;
    public static final double BASE_ANIMATION_FREQ = 2 * Math.PI / LIVE_CYCLE_DURATION_SECONDS;
    public static final double ANIMATION_AMPLITUDE_Y = 15.0;
    public static final double ANIMATION_AMPLITUDE_X = 12.0;

    /**
     * Lissajous pattern frequency multipliers [X, Y].
     * All values are integers to ensure perfect looping.
     * The first pattern is horizontal-only for users who prefer simple left-right motion.
     */
    public static final double[][] LISSAJOUS_PATTERNS = {
            {0, 1},  // horizontal only - left-right sweep
            {1, 2},  // 2:1 - classic figure-8
            {2, 3},  // 3:2 - trefoil
            {1, 3},  // 3:1 - complex figure-8
            {3, 4},  // 4:3 - flower pattern
            {2, 5},  // 5:2 - complex
    };

    /**
     * Parameters for animation that provide variety while maintaining seamless looping.
     *
     * @param patternIndex    index into LISSAJOUS_PATTERNS array
     * @param freqMultiplierX frequency multiplier for X rotation (integer for perfect looping)
     * @param freqMultiplierY frequency multiplier for Y rotation (integer for perfect looping)
     * @param phaseX          phase offset for X rotation (0 to 2π)
     * @param phaseY          phase offset for Y rotation (0 to 2π)
     */
    public record AnimationParameters(
            int patternIndex,
            double freqMultiplierX,
            double freqMultiplierY,
            double phaseX,
            double phaseY
    ) {
        /**
         * Creates randomized animation parameters with a random Lissajous pattern and phase offsets.
         * For horizontal-only pattern, phaseX is fixed at 0 to keep the preview line centered.
         */
        public static AnimationParameters randomize() {
            var random = new Random();
            int index = random.nextInt(LISSAJOUS_PATTERNS.length);
            var pattern = LISSAJOUS_PATTERNS[index];
            // For horizontal-only (freqMultiplierX == 0), phaseX must be 0 to keep the preview line centered
            double phaseX = (pattern[0] == 0) ? 0 : random.nextDouble() * 2 * Math.PI;
            return new AnimationParameters(
                    index,
                    pattern[0],
                    pattern[1],
                    phaseX,
                    random.nextDouble() * 2 * Math.PI
            );
        }

        /**
         * Creates parameters for a specific pattern index with fixed phase offsets.
         * This ensures deterministic behavior when users cycle through patterns.
         */
        public static AnimationParameters forPattern(int patternIndex) {
            int index = patternIndex % LISSAJOUS_PATTERNS.length;
            var pattern = LISSAJOUS_PATTERNS[index];
            return new AnimationParameters(
                    index,
                    pattern[0],
                    pattern[1],
                    0,
                    0
            );
        }

        /**
         * Returns true if this is the horizontal-only pattern (no vertical movement).
         */
        public boolean isHorizontalOnly() {
            return freqMultiplierX == 0;
        }

        /**
         * Returns parameters for the next pattern in sequence.
         * If coming from a random state (non-zero phases), starts the cycle from pattern 0.
         * When completing a full cycle, randomizes phases for the next cycle.
         */
        public AnimationParameters nextPattern() {
            boolean isRandomState = phaseX != 0 || phaseY != 0;
            if (isRandomState) {
                // First click after random: go to pattern 0, unless already on 0
                int nextIndex = (patternIndex == 0) ? 1 : 0;
                return forPattern(nextIndex);
            }
            int nextIndex = (patternIndex + 1) % LISSAJOUS_PATTERNS.length;
            if (nextIndex == 0) {
                // Completed a cycle, randomize phases for the next round
                return forPatternWithRandomPhases(0);
            }
            return forPattern(nextIndex);
        }

        /**
         * Creates parameters for a specific pattern index with random phase offsets.
         * For horizontal-only pattern, phaseX is fixed at 0 to keep the line centered.
         */
        private static AnimationParameters forPatternWithRandomPhases(int patternIndex) {
            var random = new Random();
            int index = patternIndex % LISSAJOUS_PATTERNS.length;
            var pattern = LISSAJOUS_PATTERNS[index];
            // For horizontal-only (freqMultiplierX == 0), phaseX must be 0 to keep the preview line centered
            double phaseX = (pattern[0] == 0) ? 0 : random.nextDouble() * 2 * Math.PI;
            return new AnimationParameters(
                    index,
                    pattern[0],
                    pattern[1],
                    phaseX,
                    random.nextDouble() * 2 * Math.PI
            );
        }

        /**
         * Returns the total number of available patterns.
         */
        public static int patternCount() {
            return LISSAJOUS_PATTERNS.length;
        }
    }

    public record ExportOptions(Integer resolution, int durationSeconds, boolean annotate) {}

    private Viewer3DExportHelper() {
    }

    /**
     * Shows a PNG export dialog with an annotate checkbox.
     *
     * @param stage         the owner stage
     * @param i18nBundle    the i18n bundle name
     * @param processParams the process params (may be null, disables annotation option)
     * @return true if annotate selected, false if not, null if cancelled
     */
    public static Boolean showPngExportDialog(Stage stage, String i18nBundle, ProcessParams processParams) {
        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, i18nBundle, "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, i18nBundle, "cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.title"));
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

    /**
     * Shows a video export dialog with resolution and annotate options.
     *
     * @param stage         the owner stage
     * @param i18nBundle    the i18n bundle name
     * @param nativeSize    the native resolution size
     * @param processParams the process params (may be null, disables annotation option)
     * @return the export options, or null if cancelled
     */
    public static ExportOptions showVideoExportDialog(Stage stage, String i18nBundle, int nativeSize, ProcessParams processParams) {
        var resolutions = new int[]{512, 800, 1024, 2048, nativeSize};

        var resolutionLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "export.video.resolution"));
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
                    return String.format(I18N.string(JSolEx.class, i18nBundle, "export.video.resolution.native"), value, value);
                }
                return value + "x" + value;
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });

        var durationLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "export.video.duration"));
        var durationSpinner = new Spinner<Integer>();
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, VIDEO_CYCLE_DURATION_SECONDS));
        durationSpinner.setEditable(true);
        durationSpinner.setPrefWidth(80);
        var durationBox = new HBox(10, durationSpinner, new Label(I18N.string(JSolEx.class, i18nBundle, "export.video.duration.seconds")));
        durationBox.setAlignment(Pos.CENTER_LEFT);

        var annotateCheckbox = new CheckBox(I18N.string(JSolEx.class, i18nBundle, "export.annotate"));
        annotateCheckbox.setSelected(false);
        annotateCheckbox.setDisable(processParams == null);

        var okButton = new Button("OK");
        okButton.setDefaultButton(true);
        var cancelButton = new Button(I18N.string(JSolEx.class, i18nBundle, "cancel"));

        var buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        var content = new VBox(10, resolutionLabel, resolutionCombo, durationLabel, durationBox, annotateCheckbox, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        var dialogStage = new Stage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.video.title"));
        dialogStage.setScene(new Scene(content));
        dialogStage.setResizable(false);

        var result = new AtomicReference<ExportOptions>();
        okButton.setOnAction(e -> {
            result.set(new ExportOptions(resolutionCombo.getValue(), durationSpinner.getValue(), annotateCheckbox.isSelected()));
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> {
            result.set(null);
            dialogStage.close();
        });

        dialogStage.showAndWait();
        return result.get();
    }

    /**
     * Exports the current view to PNG.
     *
     * @param stage            the owner stage
     * @param graphPane        the pane to snapshot
     * @param i18nBundle       the i18n bundle name
     * @param initialFileName  the initial file name
     * @param initialDirectory the initial directory (may be null)
     * @param processParams    the process params for annotation (may be null)
     */
    public static void exportToPng(Stage stage, StackPane graphPane, String i18nBundle,
                                   String initialFileName, File initialDirectory, ProcessParams processParams) {
        var annotate = showPngExportDialog(stage, i18nBundle, processParams);
        if (annotate == null) {
            return;
        }

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
        fileChooser.setInitialFileName(initialFileName);
        if (initialDirectory != null && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        }

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
                bufferedImage = annotateImage(bufferedImage, processParams);
            }
            try {
                ImageIO.write(bufferedImage, "png", file);
                ExplorerSupport.openInExplorer(file.toPath());
            } catch (IOException e) {
                // Silently ignore
            }
        }
    }

    /**
     * Exports to video with the given frame generator.
     *
     * @param stage                   the owner stage
     * @param i18nBundle              the i18n bundle name
     * @param initialFileName         the initial file name (without extension)
     * @param initialDirectory        the initial directory (may be null)
     * @param nativeSize              the native resolution size
     * @param processParams           the process params for annotation (may be null)
     * @param exportGlViewFactory     factory to create the export GL view (receives export size)
     * @param frameGenerator          generates frames given the frame index
     * @param onExportStart           called when export starts (for stopping animations)
     * @param onExportEnd             called when export ends (for restarting animations)
     */
    public static void exportToVideo(Stage stage, String i18nBundle, String initialFileName,
                                     File initialDirectory, int nativeSize, ProcessParams processParams,
                                     IntFunction<ExportContext> exportGlViewFactory,
                                     FrameGenerator frameGenerator,
                                     Runnable onExportStart, Runnable onExportEnd) {
        stage.toFront();
        stage.requestFocus();

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.video.title"));
        fileChooser.setInitialFileName(initialFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        if (initialDirectory != null && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        }

        var file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        var exportOptions = showVideoExportDialog(stage, i18nBundle, nativeSize, processParams);
        if (exportOptions == null || exportOptions.resolution() == null) {
            return;
        }
        var selectedSize = exportOptions.resolution();
        var durationSeconds = exportOptions.durationSeconds();
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
            alert.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.video.overwrite.title"));
            alert.setHeaderText(I18N.string(JSolEx.class, i18nBundle, "export.video.overwrite.header"));
            alert.setContentText(fileNames);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        onExportStart.run();

        var frameCount = VIDEO_FPS * durationSeconds;

        var cancelled = new AtomicBoolean(false);

        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        var progressLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "export.video.progress"));
        var cancelButton = new Button(I18N.string(JSolEx.class, i18nBundle, "cancel"));

        var progressBox = new VBox(10, progressLabel, progressBar, cancelButton);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));

        var progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(I18N.string(JSolEx.class, i18nBundle, "export.video.title"));
        progressStage.setScene(new Scene(progressBox));
        progressStage.setResizable(false);
        progressStage.setOnCloseRequest(e -> cancelled.set(true));
        cancelButton.setOnAction(e -> cancelled.set(true));
        progressStage.show();

        var formats = animationFormats;
        var finalBasePath = basePath;
        var annotate = shouldAnnotate;

        new Thread(() -> {
            var exportContextRef = new AtomicReference<ExportContext>();
            var initLatch = new CountDownLatch(1);

            Platform.runLater(() -> {
                var context = exportGlViewFactory.apply(selectedSize);
                exportContextRef.set(context);
                initLatch.countDown();
            });

            try {
                initLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            var exportContext = exportContextRef.get();
            if (exportContext == null || !exportContext.glView().waitForInitialization(10000)) {
                Platform.runLater(() -> {
                    progressStage.close();
                    var alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Export Error");
                    alert.setContentText("Failed to initialize OpenGL context for export");
                    alert.showAndWait();
                    onExportEnd.run();
                });
                return;
            }

            List<File> outputFiles = null;
            try {
                Consumer<Double> progressCallback = progress -> Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    progressLabel.setText((int) (progress * 100) + "%");
                });

                var duration = durationSeconds;
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
                            var frame = frameGenerator.generateFrame(idx, duration, exportContext);
                            if (frame == null) {
                                return null;
                            }
                            return annotate ? annotateImage(frame, processParams) : frame;
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
                if (exportContext != null) {
                    exportContext.glView().dispose();
                }
                var finalOutputFiles = outputFiles;
                Platform.runLater(() -> {
                    progressStage.close();
                    if (!cancelled.get() && finalOutputFiles != null && !finalOutputFiles.isEmpty()) {
                        ExplorerSupport.openInExplorer(finalOutputFiles.getFirst().toPath());
                    }
                    onExportEnd.run();
                });
            }
        }).start();
    }

    /**
     * Calculates the rotation for live animation based on elapsed time and animation parameters.
     *
     * @param timeSeconds the elapsed time in seconds
     * @param params      the animation parameters
     * @return array with [rotationX, rotationY]
     */
    public static float[] calculateLiveAnimationRotation(double timeSeconds, AnimationParameters params) {
        var rotationY = (float) (Math.sin(timeSeconds * BASE_ANIMATION_FREQ * params.freqMultiplierY() + params.phaseY()) * ANIMATION_AMPLITUDE_Y);
        var rotationX = (float) (Math.sin(timeSeconds * BASE_ANIMATION_FREQ * params.freqMultiplierX() + params.phaseX()) * ANIMATION_AMPLITUDE_X);
        return new float[]{rotationX, rotationY};
    }

    /**
     * Calculates the rotation for a given frame during video export animation.
     *
     * @param frameIndex      the frame index
     * @param durationSeconds the video duration in seconds
     * @param params          the animation parameters
     * @return array with [rotationX, rotationY]
     */
    public static float[] calculateExportAnimationRotation(int frameIndex, int durationSeconds, AnimationParameters params) {
        var elapsedSeconds = (double) frameIndex / VIDEO_FPS * LIVE_CYCLE_DURATION_SECONDS / durationSeconds;
        return calculateLiveAnimationRotation(elapsedSeconds, params);
    }

    /**
     * Calculates the export resolution based on the image diameter.
     *
     * @param imageDiameter the diameter of the image (max of width and height)
     * @return the export resolution
     */
    public static int calculateExportResolution(int imageDiameter) {
        return (int) (imageDiameter * EXPORT_SIZE_FACTOR);
    }

    /**
     * Annotates an image with observation details.
     *
     * @param image         the image to annotate
     * @param processParams the process parameters
     * @return the annotated image
     */
    public static BufferedImage annotateImage(BufferedImage image, ProcessParams processParams) {
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

    /**
     * Converts an ImageWrapper to a BufferedImage.
     *
     * @param wrapper the image wrapper
     * @return the buffered image
     */
    public static BufferedImage toBufferedImage(ImageWrapper wrapper) {
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

    /**
     * Context for video export containing renderer and GL view.
     */
    public record ExportContext(Object renderer, OpenGLImageView glView) {}

    /**
     * Functional interface for generating video frames.
     */
    @FunctionalInterface
    public interface FrameGenerator {
        BufferedImage generateFrame(int frameIndex, int durationSeconds, ExportContext context);
    }

    /**
     * Manages camera animation for 3D viewers.
     * Handles the AnimationTimer lifecycle and rotation calculations.
     */
    public static final class CameraAnimator {
        private static final long FRAME_INTERVAL_NS = 33_333_333L; // ~30fps

        private final RotationSetter rotationSetter;
        private final Runnable renderRequester;
        private final Runnable resetCallback;
        private volatile AnimationParameters animationParameters;
        private Runnable onPatternChanged;

        private AnimationTimer animationTimer;
        private volatile boolean animationActive = false;
        private long animationStartTime;

        /**
         * Creates a new camera animator with randomized animation parameters.
         *
         * @param rotationSetter  callback to set rotation on the renderer (receives rotationX, rotationY)
         * @param renderRequester callback to request a render
         * @param resetCallback   callback to reset renderer state (rotation to 0,0 and camera distance)
         */
        public CameraAnimator(RotationSetter rotationSetter, Runnable renderRequester, Runnable resetCallback) {
            this(rotationSetter, renderRequester, resetCallback, AnimationParameters.randomize());
        }

        /**
         * Creates a new camera animator with specified animation parameters.
         *
         * @param rotationSetter      callback to set rotation on the renderer (receives rotationX, rotationY)
         * @param renderRequester     callback to request a render
         * @param resetCallback       callback to reset renderer state (rotation to 0,0 and camera distance)
         * @param animationParameters the animation parameters to use
         */
        public CameraAnimator(RotationSetter rotationSetter, Runnable renderRequester,
                              Runnable resetCallback, AnimationParameters animationParameters) {
            this.rotationSetter = rotationSetter;
            this.renderRequester = renderRequester;
            this.resetCallback = resetCallback;
            this.animationParameters = animationParameters;
        }

        /**
         * Returns the animation parameters used by this animator.
         */
        public AnimationParameters getAnimationParameters() {
            return animationParameters;
        }

        /**
         * Sets a callback to be invoked when the animation pattern changes.
         */
        public void setOnPatternChanged(Runnable callback) {
            this.onPatternChanged = callback;
        }

        /**
         * Switches to the next animation pattern and restarts the animation.
         */
        public void nextPattern() {
            animationParameters = animationParameters.nextPattern();
            resetAndRestart();
            if (onPatternChanged != null) {
                onPatternChanged.run();
            }
        }

        /**
         * Starts the camera animation.
         */
        public void start() {
            animationActive = true;
            animationStartTime = System.nanoTime();

            animationTimer = new AnimationTimer() {
                private long lastUpdate = 0;

                @Override
                public void handle(long now) {
                    if (!animationActive) {
                        return;
                    }

                    if (now - lastUpdate < FRAME_INTERVAL_NS) {
                        return;
                    }
                    lastUpdate = now;

                    var elapsedSeconds = (now - animationStartTime) / 1_000_000_000.0;
                    try {
                        var rotation = calculateLiveAnimationRotation(elapsedSeconds, animationParameters);
                        rotationSetter.setRotation(rotation[0], rotation[1]);
                        renderRequester.run();
                    } catch (Exception e) {
                        // Ignore exceptions during shutdown
                    }
                }
            };
            animationTimer.start();
        }

        /**
         * Stops the camera animation.
         */
        public void stop() {
            animationActive = false;
        }

        /**
         * Resets and restarts the animation.
         */
        public void resetAndRestart() {
            resetCallback.run();
            animationActive = true;
            animationStartTime = System.nanoTime();
            renderRequester.run();
        }

        /**
         * Disposes the animator and releases resources.
         */
        public void dispose() {
            animationActive = false;
            if (animationTimer != null) {
                animationTimer.stop();
                animationTimer = null;
            }
        }

        /**
         * Returns whether the animation is currently active.
         */
        public boolean isActive() {
            return animationActive;
        }
    }

    /**
     * Functional interface for setting rotation on a renderer.
     */
    @FunctionalInterface
    public interface RotationSetter {
        void setRotation(float rotationX, float rotationY);
    }
}
