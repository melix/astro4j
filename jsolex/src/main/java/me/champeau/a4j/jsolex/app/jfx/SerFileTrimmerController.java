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
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.spectrum.SerFileTrimmer;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.findMetadataFile;

/**
 * Controller for the SER file trimmer dialog, which allows users to trim SER files
 * by selecting frame ranges and spatial boundaries to reduce file size.
 */
public class SerFileTrimmerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerFileTrimmerController.class);

    @FXML
    private TextField firstFrame;

    @FXML
    private TextField lastFrame;

    @FXML
    private TextField minX;

    @FXML
    private TextField maxX;

    @FXML
    private TextField pixelsUp;

    @FXML
    private TextField pixelsDown;

    @FXML
    private Label pixelsUpAngstroms;

    @FXML
    private Label pixelsDownAngstroms;

    @FXML
    private Label totalFrames;

    @FXML
    private Label maxWidth;

    private Stage stage;
    private BiConsumer<Double, String> progressConsumer;
    private Runnable onStart;
    private Consumer<? super File> onFinish;
    private TrimmingParameters trimmingParameters;

    /**
     * Creates and displays a SER file trimmer dialog.
     *
     * @param stage the stage to display the dialog in
     * @param payload the trimming parameters containing file and boundary information
     * @param onStart callback to execute when trimming starts
     * @param progressConsumer callback to receive progress updates during trimming
     * @param onFinish callback to execute when trimming completes, receives the output file or null on failure
     */
    public static void create(Stage stage,
                              TrimmingParameters payload,
                              Runnable onStart,
                              BiConsumer<Double, String> progressConsumer,
                              Consumer<? super File> onFinish) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "ser-trimmer");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (SerFileTrimmerController) fxmlLoader.getController();
            controller.setup(stage, payload, progressConsumer, onStart, onFinish);
            Scene scene = new Scene(node);
            Platform.runLater(() -> {
                stage.setTitle(I18N.string(JSolEx.class, "ser-trimmer", "frame.title"));
                stage.setScene(scene);
                stage.show();
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies the metadata file associated with the given SER file to a trimmed version,
     * if such metadata file exists.
     *
     * @param serFile the original SER file whose metadata should be copied
     */
    public static void maybeCopyMetadata(File serFile) {
        findMetadataFile(serFile.toPath()).ifPresent(metadataFile -> {
            var outputMetadataFile = toTrimmedFile(metadataFile);
            try {
                Files.copy(metadataFile.toPath(), outputMetadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error(JSolEx.message("error.cannot.copy.metadata"), e);
            }
        });
    }

    private void setup(Stage stage, TrimmingParameters payload, BiConsumer<Double, String> progressConsumer, Runnable onStart, Consumer<? super File> onFinish) {
        this.stage = stage;
        this.trimmingParameters = payload;
        this.progressConsumer = progressConsumer;
        this.onStart = onStart;
        this.onFinish = onFinish;
        firstFrame.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 0) {
                    return 0;
                }
                var lastFrameText = lastFrame.getText();
                if (lastFrameText != null && !lastFrameText.isEmpty()) {
                    var last = Integer.parseInt(lastFrameText);
                    if (value >= last) {
                        return last - 1;
                    }
                }
                if (value >= payload.totalFrames()) {
                    return payload.totalFrames() - 1;
                }
                return value;
            }
        }));
        lastFrame.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 0) {
                    return 0;
                }
                var firstFrameText = firstFrame.getText();
                if (firstFrameText != null && !firstFrameText.isEmpty()) {
                    var first = Integer.parseInt(firstFrameText);
                    if (value <= first) {
                        return first + 1;
                    }
                }
                if (value >= payload.totalFrames()) {
                    return payload.totalFrames() - 1;
                }
                return value;
            }
        }));
        minX.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 0) {
                    return 0;
                }
                var maxXText = maxX.getText();
                if (maxXText != null && !maxXText.isEmpty()) {
                    var maxXValue = Integer.parseInt(maxXText);
                    if (value >= maxXValue) {
                        return maxXValue - 1;
                    }
                }
                if (value >= payload.maxWidth()) {
                    return payload.maxX() - 1;
                }
                return value;
            }
        }));
        maxX.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 0) {
                    return 0;
                }
                var minXText = minX.getText();
                if (minXText != null && !minXText.isEmpty()) {
                    var minXValue = Integer.parseInt(minXText);
                    if (value <= minXValue) {
                        return minXValue + 1;
                    }
                }
                if (value >= payload.maxWidth()) {
                    return payload.maxWidth() - 1;
                }
                return value;
            }
        }));
        pixelsUp.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 5) {
                    return 5;
                }
                if (value > payload.pixelsUp()) {
                    return payload.pixelsUp();
                }
                return value;
            }
        }));
        pixelsDown.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value < 5) {
                    return 5;
                }
                if (value > payload.pixelsDown()) {
                    return payload.pixelsDown();
                }
                return value;
            }
        }));
        firstFrame.setText(String.valueOf(payload.firstFrame()));
        lastFrame.setText(String.valueOf(payload.lastFrame()));
        minX.setText(String.valueOf(payload.minX()));
        maxX.setText(String.valueOf(payload.maxX()));
        pixelsUp.setText(String.valueOf(payload.pixelsUp()));
        pixelsDown.setText(String.valueOf(payload.pixelsDown()));
        pixelsUpAngstroms.textProperty().bind(pixelsUp.textProperty().map(s -> toAngstroms(s, payload.dispersion())));
        pixelsDownAngstroms.textProperty().bind(pixelsDown.textProperty().map(s -> toAngstroms(s, payload.dispersion())));
        totalFrames.setText(String.format(I18N.string(JSolEx.class, "ser-trimmer", "total.frames"), payload.totalFrames()));
        maxWidth.setText(String.format(I18N.string(JSolEx.class, "ser-trimmer", "max.width"), payload.maxWidth()));
    }

    private static String toAngstroms(String pixels, Dispersion dispersion) {
        try {
            var dispersionAngstroms = Double.parseDouble(pixels) * dispersion.angstromsPerPixel();
            return String.format(Locale.US, "%.2fÅ", dispersionAngstroms);
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    /**
     * Cancels the trimming operation and closes the dialog.
     */
    @FXML
    public void cancel() {
        stage.close();
    }

    /**
     * Initiates the SER file trimming process based on user-selected parameters.
     * This method closes the dialog, starts trimming in a background thread,
     * and invokes callbacks for progress updates and completion.
     */
    @FXML
    public void trim() {
        var outputFile = toTrimmedFile(trimmingParameters.serFile());
        stage.close();
        onStart.run();
        Thread.startVirtualThread(() -> {
            try {
                SerFileTrimmer.trimFile(
                    trimmingParameters.serFile(),
                    outputFile,
                    Integer.parseInt(firstFrame.getText()),
                    Integer.parseInt(lastFrame.getText()),
                    Integer.parseInt(pixelsUp.getText()),
                    Integer.parseInt(pixelsDown.getText()),
                    Integer.parseInt(minX.getText()),
                    Integer.parseInt(maxX.getText()),
                    trimmingParameters.polynomial(),
                    trimmingParameters.verticalFlip(),
                    progress -> Platform.runLater(() -> progressConsumer.accept(
                        progress,
                        I18N.string(JSolEx.class, "ser-trimmer", "trimming")
                    ))
                );
                maybeCopyMetadata(trimmingParameters.serFile());
            } finally {
                Platform.runLater(() -> {
                        if (outputFile.exists()) {
                            onFinish.accept(outputFile);
                        } else {
                            onFinish.accept(null);
                        }
                    }
                );
            }
        });

    }

    /**
     * Generates a file path for the trimmed version of a SER file by appending "-trimmed"
     * to the base name.
     *
     * @param serFile the original SER file
     * @return a file reference for the trimmed output file
     */
    public static File toTrimmedFile(File serFile) {
        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf('.'));
        var ext = serFile.getName().substring(serFile.getName().lastIndexOf('.'));
        var parent = serFile.getParentFile();
        var trimmedName = baseName + "-trimmed" + ext;
        return new File(parent, trimmedName);
    }
}
