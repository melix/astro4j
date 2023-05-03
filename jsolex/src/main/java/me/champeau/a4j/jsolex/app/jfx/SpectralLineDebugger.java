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

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.io.File;
import java.nio.file.Files;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.createImageConverter;

public class SpectralLineDebugger {
    @FXML
    private ImageView imageView;

    @FXML
    private Slider frameSlider;

    @FXML
    private Label frameId;

    @FXML
    private TextField spectrumDetectionThreshold;

    @FXML
    private TextField sunDetectionThreshold;

    private SerFileReader reader;

    public void open(File file, ColorMode colorMode, Scene scene) {
        var converter = createImageConverter(colorMode);
        try {
            reader = SerFileReader.of(file);
            var tmpPath = Files.createTempFile("debug_", ".png");
            File imageFile = tmpPath.toFile();
            imageFile.deleteOnExit();
            var header = reader.header();
            var geometry = header.geometry();
            int current = header.frameCount() / 2;
            imageView.fitWidthProperty().bind(scene.widthProperty());
            spectrumDetectionThreshold.textProperty().set("0.2d");
            sunDetectionThreshold.textProperty().set("5000d");
            frameSlider.setMin(0);
            frameSlider.setMax(header.frameCount());
            frameSlider.setValue(current);
            frameId.textProperty().bind(frameSlider.valueProperty().asString("Frame %.0f"));
            var pause = new PauseTransition(Duration.millis(50));
            frameSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                int frameId = newValue.intValue();
                pause.setOnFinished(e ->
                        processFrame(converter, reader, geometry, frameId, imageFile)
                );
                pause.playFromStart();
            });
            spectrumDetectionThreshold.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.trim().isEmpty()) {
                    pause.setOnFinished(e -> processFrame(converter, reader, geometry, frameSlider.valueProperty().intValue(), imageFile));
                    pause.playFromStart();
                }
            });
            sunDetectionThreshold.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.trim().isEmpty()) {
                    processFrame(converter, reader, geometry, frameSlider.valueProperty().intValue(), imageFile);
                }
            });
            processFrame(converter, reader, geometry, current, imageFile);
        } catch (
                Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws Exception {
        reader.close();
    }

    private void processFrame(ImageConverter<float[]> converter,
                              SerFileReader reader,
                              ImageGeometry geometry,
                              int frameId,
                              File imageFile) {
        reader.seekFrame(frameId);
        float[] buffer = converter.createBuffer(geometry);
        converter.convert(frameId, reader.currentFrame().data(), geometry, buffer);
        int width = geometry.width();
        int height = geometry.height();
        var spectrumThreshold = Double.parseDouble(spectrumDetectionThreshold.textProperty().getValue());
        var sunThreshold = Double.parseDouble(sunDetectionThreshold.textProperty().getValue());
        var analyzer = new SpectrumFrameAnalyzer(
                width,
                height,
                spectrumThreshold,
                sunThreshold
        );
        analyzer.analyze(buffer);
        var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
        creator.generateDebugImage(imageFile);
        imageView.setImage(new Image(imageFile.toURI().toString()));
    }
}
