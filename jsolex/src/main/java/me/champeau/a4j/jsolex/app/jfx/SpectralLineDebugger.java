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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.AverageImageCreator;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;
import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.createImageConverter;

public class SpectralLineDebugger {
    private static final int FAST_MOVE = 10;
    private static final int FINE_MOVE = 1;
    private static final double EPSILON = 0.5d;
    private static final DoubleUnaryOperator UNDEFINED_POLY = e -> 0;

    @FXML
    private Slider frameSlider;

    @FXML
    private TextField frameId;

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
    private Button computePolynomial;

    @FXML
    private TextField polynomialTextField;

    @FXML
    private Slider contrastBoost;

    @FXML
    private HBox frameMoveGroup;

    @FXML
    private HBox progressBox;
    @FXML
    private ProgressBar progressBar;

    private Image image;
    private Point2D p1;
    private Point2D p2;
    private final List<Point2D> samplePoints = new ArrayList<>();
    private final List<Double> sampleDistances = new ArrayList<>();
    private double zoom = 1.0;
    private DoubleUnaryOperator polynomial;
    private DoubleUnaryOperator lockedPolynomial;
    private SerFileReader reader;
    private SpectrumAnalyzer.QueryDetails spectralRayDetectionResult;
    private ProcessParams processParams;
    private Consumer<? super String> onPolynomialComputed;
    private float[] averageImage;

    public static Stage open(File file, Consumer<? super String> onPolynomialComputed) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "frame-debugger");
        Object configWindow;
        try {
            configWindow = fxmlLoader.load();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        var controller = (SpectralLineDebugger) fxmlLoader.getController();
        controller.onPolynomialComputed = onPolynomialComputed;
        var stage = newStage();
        Scene scene = new Scene((Parent) configWindow);
        controller.open(file, null, scene, stage);
        stage.setTitle(I18N.string(JSolEx.class, "frame-debugger", "frame.debugger") + " (" + file.getName() + ")");
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    public void open(File file, ColorMode colorMode, Scene scene, Stage stage) {
        var toggleGroup = new ToggleGroup();
        average.setToggleGroup(toggleGroup);
        frames.setToggleGroup(toggleGroup);
        canvas.setOnScroll(event -> {
            if (event.isControlDown()) {
                double deltaY = event.getDeltaY();
                if (deltaY != 0) {
                    double zoomFactor = 1.05;
                    if (deltaY < 0) {
                        zoom /= zoomFactor;
                    } else {
                        zoom *= zoomFactor;
                    }
                    zoom = Math.max(0.1, Math.min(zoom, 5));
                    fireZoomChanged(scene);
                }
                event.consume();
            }
        });
        toggleGroup.selectedToggleProperty().addListener((obj, oldValue, newValue) -> {
            if (newValue == average) {
                frameMoveGroup.setDisable(true);
            } else {
                frameMoveGroup.setDisable(false);
            }
        });
        status.setDisable(true);
        BackgroundOperations.asyncIo(() -> prepareView(file, colorMode, scene, stage, toggleGroup));
        computePolynomial.setDisable(true);
        polynomialTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                if (onPolynomialComputed != null) {
                    onPolynomialComputed.accept(newValue);
                }
            }
        });
    }

    private void fireZoomChanged(Scene scene) {
        double scale = zoom;
        if (image != null) {
            scale = zoom * scene.getWidth() / image.getWidth();
        }
        canvas.setScaleX(scale);
        canvas.setScaleY(scale);
        redraw();
    }

    private void prepareView(File file, ColorMode colorMode, Scene scene, Stage stage, ToggleGroup toggleGroup) {
        var converter = createImageConverter(colorMode);
        BatchOperations.submit(() -> progressBox.setVisible(true));
        var detector = new AverageImageCreator(converter, event -> {
            if (event instanceof ProgressEvent progress) {
                BatchOperations.submitOneOfAKind("progress", () -> progressBar.setProgress(progress.getPayload().progress()));
            }
        });
        try {
            reader = SerFileReader.of(file);
            detector.computeAverageImage(reader);
            averageImage = detector.getAverageImage();
            var tmpPath = Files.createTempFile("debug_", ".png");
            File imageFile = tmpPath.toFile();
            imageFile.deleteOnExit();
            BatchOperations.submit(() -> {
                status.setDisable(false);
                progressBox.setVisible(false);
                var header = reader.header();
                var geometry = header.geometry();
                int current = header.frameCount() / 2;
                var screenWidth = Screen.getPrimary().getBounds().getWidth();
                stage.setWidth(Math.max(screenWidth, Math.min(1024, header.geometry().width())));
                stage.setHeight(stage.getHeight() + 2 * geometry.height() + 20);
                status.maxWidthProperty().bind(scene.widthProperty());
                scene.widthProperty().addListener((o, oldValue, newValue) -> {
                    if (image != null) {
                        fireZoomChanged(scene);
                    }
                });
                scene.heightProperty().addListener((o, oldValue, newValue) -> {
                    if (image != null) {
                        redraw();
                    }
                });
                var pause = new PauseTransition(Duration.millis(50));
                ChangeListener listener = (observable, oldValue, newValue) -> {
                    int newId = newValue instanceof Number ? ((Number) newValue).intValue() : frameSlider.valueProperty().intValue();
                    frameId.setText(newId + "");
                    pause.setOnFinished(e ->
                        processFrame(converter, reader, geometry, newId, imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene)
                    );
                    pause.playFromStart();
                };
                computePolynomial.setOnAction(e -> {
                    var polynomial = LinearRegression.thirdOrderRegression(samplePoints.toArray(new Point2D[0]));
                    lockedPolynomial = polynomial.asPolynomial();
                    processFrame(converter, reader, geometry, Integer.parseInt(frameId.getText()), imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene);
                    polynomialTextField.setText(polynomial.asPolynomialString());
                    redraw();
                });
                frameSlider.valueProperty().addListener(listener);
                frameId.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (!newValue.trim().isEmpty() && !Objects.equals(oldValue, newValue)) {
                        frameSlider.setValue(Integer.parseInt(newValue));
                    }
                });
                frameId.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
                    @Override
                    public Integer fromString(String value) {
                        var id = super.fromString(value);
                        if (id != null) {
                            if (id < 0) {
                                return 0;
                            }
                            if (id > header.frameCount()) {
                                return header.frameCount();
                            }
                        }
                        return id;
                    }
                }));
                contrastBoost.valueProperty().addListener((observable, oldValue, newValue) -> {
                    int frameId = frameSlider.valueProperty().intValue();
                    pause.setOnFinished(e -> processFrame(converter, reader, geometry, frameId, imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene));
                    pause.playFromStart();
                });
                sunDetectionThreshold.textProperty().set("");
                frameSlider.setMin(0);
                frameSlider.setMax(header.frameCount());
                frameSlider.setValue(current);
                sunDetectionThreshold.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (!newValue.trim().isEmpty()) {
                        processFrame(converter, reader, geometry, frameSlider.valueProperty().intValue(), imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene);
                    }
                });
                toggleGroup.selectedToggleProperty().addListener((obj, oldValue, newValue) -> {
                    processFrame(converter, reader, geometry, current, imageFile, newValue == average ? averageImage : null, scene);
                });
                toggleGroup.selectToggle(average);
                lockPolynomialCheckbox.setSelected(true);
            });
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
        Double sunThreshold = sunDetectionThreshold.textProperty().getValue().isEmpty() ? null : Double.parseDouble(sunDetectionThreshold.textProperty().getValue());
        var analyzer = new SpectrumFrameAnalyzer(
            width,
            height,
            sunThreshold
        );
        analyzer.analyze(buffer);
        processParams = ProcessParamsIO.loadDefaults();
        var instrument = processParams.observationDetails().instrument();
        var analysis = analyzer.result();
        analysis.distortionPolynomial().ifPresent(p -> {
            this.polynomial = p;
            this.polynomialTextField.setText(analysis.distortionQuadruplet().get().asPolynomialString());
            if (spectralRayDetectionResult == null) {
                var pixelSize = processParams.observationDetails().pixelSize();
                var candidates = new ArrayList<SpectrumAnalyzer.QueryDetails>();
                for (var line : SpectralRay.predefined()) {
                    if (line.wavelength() > 0 && !line.emission()) {
                        candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 1, instrument));
                        candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 2, instrument));
                    }
                }
                int leftBorder = analysis.leftBorder().orElse(0);
                int rightBorder = analysis.rightBorder().orElse(width);
                var map = candidates
                    .stream()
                    .collect(Collectors.toMap(d -> d, details -> SpectrumAnalyzer.computeDataPoints(details, polynomial, leftBorder, rightBorder, width, height, buffer)));
                spectralRayDetectionResult = SpectrumAnalyzer.findBestMatch(map);
            }
        });
        var detectedPolynomial = analysis.distortionPolynomial().orElse(null);
        lockPolynomialCheckbox.selectedProperty().addListener((obj, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue) && detectedPolynomial != null) {
                lockedPolynomial = detectedPolynomial;
            } else {
                lockedPolynomial = null;
            }
        });
        var boostValue = contrastBoost.getValue();
        if (boostValue > 0) {
            new ArcsinhStretchingStrategy(0f, (float) boostValue, boostValue).stretch(new ImageWrapper32(width, height, buffer, MutableMap.of()));
        }
        var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
        var rgb = creator.generateDebugImage(lockedPolynomial);
        if (spectralRayDetectionResult != null && spectralRayDetectionResult.line().equals(SpectralRay.H_ALPHA)) {
            var line = spectralRayDetectionResult.line();
            var dispersion = SpectrumAnalyzer.computeSpectralDispersionNanosPerPixel(instrument, line.wavelength(), spectralRayDetectionResult.pixelSize() * spectralRayDetectionResult.binning());
            var detector = new PhenomenaDetector(dispersion, line.wavelength(), 0);
            detector.setDetectionListener(rs -> {
                var x = rs.a();
                var y = ((int) Math.round(polynomial.applyAsDouble(x))) + rs.b();
                var pos = (int) (y * width + x);
                rgb.r()[pos] = 0;
                rgb.g()[pos] = Constants.MAX_PIXEL_VALUE;
                rgb.b()[pos] = Constants.MAX_PIXEL_VALUE;
            });
            detector.performDetection(frameId, width, height, buffer, polynomial);
        }
        ImageUtils.writeRgbImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), imageFile, EnumSet.of(ImageFormat.PNG));
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
                samplePoints.add(new Point2D(x, y));
                double avgDist = computeAverageDistanceToSpectralLineFromSamples();
                info.setText(localized("average.distance.spectral.line") + " " + Math.round(avgDist));
                redraw();
                if (samplePoints.size()>2) {
                    computePolynomial.setDisable(false);
                    polynomialTextField.setDisable(false);
                } else {
                    computePolynomial.setDisable(true);
                    polynomialTextField.setDisable(true);
                }
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

    private double computeDistanceToSpectralLine(DoubleUnaryOperator detectedPolynomial, double x, double y) {
        var poly = Optional.ofNullable(lockedPolynomial)
            .or(() -> Optional.ofNullable(detectedPolynomial))
            .orElse(UNDEFINED_POLY);
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

    private void moveFrameBy(int amount) {
        var max = frameSlider.getMax();
        var newPosition = Math.max(0, Math.min(max, frameSlider.getValue() + amount));
        frameSlider.setValue(newPosition);
    }

    @FXML
    private void fastRewind() {
        moveFrameBy(-FAST_MOVE);
    }

    @FXML
    private void rewind() {
        moveFrameBy(-FINE_MOVE);
    }

    @FXML
    private void fastForward() {
        moveFrameBy(FAST_MOVE);
    }

    @FXML
    private void forward() {
        moveFrameBy(FINE_MOVE);
    }
}
