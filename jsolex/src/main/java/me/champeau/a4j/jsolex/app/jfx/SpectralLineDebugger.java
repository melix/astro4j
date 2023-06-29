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
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.MagnitudeBasedSunEdgeDetector;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.createImageConverter;

public class SpectralLineDebugger {
    private static final double EPSILON = 0.5d;
    private static final DoubleTriplet UNDEFINED_POLY = new DoubleTriplet(0, 0, 0);

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

    @FXML
    private CheckBox lockPolynomialCheckbox;

    @FXML
    private Slider contrastBoost;

    private Image image;
    private Point2D p1;
    private Point2D p2;
    private final List<Point2D> samplePoints = new ArrayList<>();
    private final List<Double> sampleDistances = new ArrayList<>();

    private DoubleUnaryOperator polynomial;
    private DoubleTriplet lockedPolynomial;
    private SerFileReader reader;

    public void open(File file, ColorMode colorMode, Scene scene, Stage stage) {
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
            var screenWidth = Screen.getPrimary().getBounds().getWidth();
            stage.setWidth(Math.max(screenWidth, Math.min(1024, header.geometry().width())));

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
            ChangeListener listener = (observable, oldValue, newValue) -> {
                int frameId = frameSlider.valueProperty().intValue();
                pause.setOnFinished(e ->
                        processFrame(converter, reader, geometry, frameId, imageFile, null, scene)
                );
                pause.playFromStart();
            };
            frameSlider.valueProperty().addListener(listener);
            contrastBoost.valueProperty().addListener(listener);
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
        for (Point2D samplePoint : samplePoints) {
            drawCross(graphicsContext, samplePoint.x(), samplePoint.y());
        }
        if (p1 != null) {
            var x = p1.x();
            var y = p1.y();
            drawCross(graphicsContext, x, y);
        }
        if (p2 != null) {
            drawline(graphicsContext, p2);
        }
    }

    private static void drawCross(GraphicsContext graphicsContext, double x, double y) {
        graphicsContext.strokeLine(x - 5, y, x + 5, y);
        graphicsContext.strokeLine(x, y - 5, x, y + 5);
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
        var detectedPolynomial = analyzer.findDistortionPolynomial().orElse(null);
        lockPolynomialCheckbox.selectedProperty().addListener((obj, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue) && detectedPolynomial != null) {
                lockedPolynomial = detectedPolynomial;
            } else {
                lockedPolynomial = null;
            }
        });
        var boostValue = contrastBoost.getValue();
        if (boostValue > 0) {
            new ArcsinhStretchingStrategy(0f, (float) boostValue, boostValue).stretch(buffer);
        }
        var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
        var rgb = creator.generateDebugImage(lockedPolynomial);
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
            double dist = computeDistanceToSpectralLine(detectedPolynomial, x, y);
            if (evt.isControlDown()) {
                sampleDistances.add(dist);
                samplePoints.add(new Point2D(x,y));
                double avgDist = computeAverageDistanceToSpectralLineFromSamples();
                info.setText(localized("average.distance.spectral.line") + " " + Math.round(avgDist));
                redraw();
                return;
            }
            sampleDistances.clear();
            samplePoints.clear();
            if (p1 == null) {
                p1 = new Point2D(x, y);
                sampleDistances.add(dist);
                samplePoints.add(p1);
            } else if (p2 == null) {
                p2 = new Point2D(x, y);
            } else {
                p1 = null;
                p2 = null;
            }
            redraw();
        });
        canvas.setOnMouseMoved(evt -> {
            var x = evt.getX();
            var y = evt.getY();
            var sb = new StringBuilder();
            double dist = computeDistanceToSpectralLine(detectedPolynomial, x, y);
            info.setText(localized("distance.spectral.line") + " " + Math.round(dist));
            double avgDist = computeAverageDistanceToSpectralLineFromSamples();
            sb.append("(x=").append(format(x)).append(",y=")
                    .append(format(y)).append(",")
                    .append(localized("distance.spectral.line.short")).append("=")
                    .append(format(dist))
                    .append(",")
                    .append(localized("distance.spectral.line.avg.short")).append("=")
                    .append(format(avgDist)).append(")");
            if (p1 != null) {
                redraw();
                var cur = p2 != null ? p2 : new Point2D(x, y);
                drawline(graphicsContext, cur);
            }
            info.setText(sb.toString());
        });
    }

    private double computeAverageDistanceToSpectralLineFromSamples() {
        return sampleDistances.stream().mapToDouble(d -> d).average().orElse(0);
    }

    private double computeDistanceToSpectralLine(DoubleTriplet detectedPolynomial, double x, double y) {
        var poly = Optional.ofNullable(lockedPolynomial)
                .or(() -> Optional.ofNullable(detectedPolynomial))
                .orElse(UNDEFINED_POLY)
                .asPolynomial();
        var yy = poly.applyAsDouble(x);
        return y - yy;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private void drawline(GraphicsContext graphicsContext, Point2D cur) {
        var color = Color.RED;
        if (Math.abs(cur.x() - p1.x()) <= EPSILON) {
            color = Color.ORANGE;
            if (polynomial != null) {
                var py = polynomial.applyAsDouble(cur.x());
                if (Math.abs(py - cur.y()) <= EPSILON) {
                    color = Color.GREEN;
                }
            }
        }
        graphicsContext.setStroke(color);
        graphicsContext.setLineWidth(4);
        graphicsContext.strokeLine(p1.x(), p1.y(), cur.x(), cur.y());
    }

    private static String localized(String key) {
        return I18N.string(JSolEx.class, "frame-debugger", key);
    }
}
