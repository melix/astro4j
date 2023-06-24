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
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.MagnitudeBasedSunEdgeDetector;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.io.File;
import java.nio.file.Files;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.createImageConverter;

public class SpectralLineDebugger {
    @FXML
    private Slider frameSlider;

    @FXML
    private Label frameId;

    @FXML
    private TextField sunDetectionThreshold;

    @FXML
    private Label info;

    @FXML
    private RadioButton average;
    @FXML
    private RadioButton frames;
    @FXML
    private Canvas canvas;

    @FXML
    private VBox status;

    private Image image;
    private Point2D ref;

    private DoubleUnaryOperator polynomial;
    private SerFileReader reader;

    public void open(File file, ColorMode colorMode, Scene scene) {
        var toggleGroup = new ToggleGroup();
        average.setToggleGroup(toggleGroup);
        frames.setToggleGroup(toggleGroup);
        toggleGroup.selectedToggleProperty().addListener((obj, oldValue, newValue) -> {
            if (newValue == average) {
                frameSlider.setDisable(true);
            } else {
                frameSlider.setDisable(false);
            }
        });
        var converter = createImageConverter(colorMode);
        var detector = new MagnitudeBasedSunEdgeDetector(converter, event -> {
        });
        try {
            reader = SerFileReader.of(file);
            detector.detectEdges(reader);
            var averageImage = detector.getAverageImage();
            var tmpPath = Files.createTempFile("debug_", ".png");
            File imageFile = tmpPath.toFile();
            imageFile.deleteOnExit();
            var header = reader.header();
            var geometry = header.geometry();
            int current = header.frameCount() / 2;
            status.maxWidthProperty().bind(scene.widthProperty());
            scene.widthProperty().addListener((o, oldValue, newValue) -> {
                if (image != null) {
                    canvas.setScaleX(newValue.doubleValue() / image.getWidth());
                    redraw();
                }
            });
            scene.heightProperty().addListener((o, oldValue, newValue) -> {
                if (image != null) {
                    redraw();
                }
            });
            sunDetectionThreshold.textProperty().set("5000d");
            frameSlider.setMin(0);
            frameSlider.setMax(header.frameCount());
            frameSlider.setValue(current);
            frameId.textProperty().bind(frameSlider.valueProperty().asString("Frame %.0f"));
            var pause = new PauseTransition(Duration.millis(50));
            frameSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                int frameId = newValue.intValue();
                pause.setOnFinished(e ->
                        processFrame(converter, reader, geometry, frameId, imageFile, null, scene)
                );
                pause.playFromStart();
            });
            sunDetectionThreshold.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.trim().isEmpty()) {
                    processFrame(converter, reader, geometry, frameSlider.valueProperty().intValue(), imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene);
                }
            });
            toggleGroup.selectedToggleProperty().addListener((obj, oldValue, newValue) -> {
                processFrame(converter, reader, geometry, current, imageFile, newValue == average ? averageImage : null, scene);
            });
            toggleGroup.selectToggle(average);
        } catch (
                Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void redraw() {
        var graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.drawImage(image, 0, 0);
        graphicsContext.setStroke(Color.RED);
        graphicsContext.setLineWidth(2.0);
        if (ref != null) {
            var x = ref.x();
            var y = ref.y();
            graphicsContext.strokeLine(x - 5, y, x + 5, y);
            graphicsContext.strokeLine(x, y - 5, x, y + 5);
        }
    }

    public void close() throws Exception {
        reader.close();
    }

    private void processFrame(ImageConverter<float[]> converter,
                              SerFileReader reader,
                              ImageGeometry geometry,
                              int frameId,
                              File imageFile,
                              float[] average,
                              Scene scene) {
        float[] buffer = average != null ? average : converter.createBuffer(geometry);
        if (average == null) {
            reader.seekFrame(frameId);
            converter.convert(frameId, reader.currentFrame().data(), geometry, buffer);
        }
        int width = geometry.width();
        int height = geometry.height();
        var sunThreshold = Double.parseDouble(sunDetectionThreshold.textProperty().getValue());
        var analyzer = new SpectrumFrameAnalyzer(
                width,
                height,
                sunThreshold
        );
        analyzer.analyze(buffer);
        analyzer.findDistortionPolynomial().ifPresent(triplet -> this.polynomial = triplet.asPolynomial());
        var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
        var rgb = creator.generateDebugImage();
        ImageUtils.writeRgbImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), imageFile);
        image = new Image(imageFile.toURI().toString());
        canvas.setWidth(image.getWidth());
        canvas.setHeight(image.getHeight());
        canvas.setScaleX(scene.getWidth() / image.getWidth());
        var graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.drawImage(image, 0, 0);
        canvas.setOnMouseClicked(evt -> {
            var x = evt.getX();
            var y = evt.getY();
            ref = new Point2D(x, y);
            redraw();
        });
        canvas.setOnMouseMoved(evt -> {
            var sb = new StringBuilder();
            var x = evt.getX();
            var y = evt.getY();
            sb.append("(").append(x).append(",").append(y).append(")");
            if (ref != null) {
                redraw();
                var cur = new Point2D(x, y);
                var color = Color.RED;
                if (Math.abs(x - ref.x()) <= 0.1d) {
                    color = Color.GREEN;
                }
                graphicsContext.setStroke(color);
                graphicsContext.strokeLine(ref.x(), ref.y(), x, y);
                sb.append(" - dist ").append(cur.distanceTo(ref)).append(" pixels");
            }
            info.setText(sb.toString());
        });
    }
}
