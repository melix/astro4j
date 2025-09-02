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
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.AverageImageCreator;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaListener;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;
import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.createImageConverter;
import static me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator.SPACING;

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
    private ScrollPane canvasScrollPane;

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
    @FXML
    private Button resetPolynomial;

    private Image image;
    private final List<Point2D> samplePoints = new ArrayList<>();
    private final List<Double> sampleDistances = new ArrayList<>();
    private double zoom = 1.0;
    private DoubleUnaryOperator polynomial;
    private DoubleUnaryOperator lockedPolynomial;
    private SerFileReader reader;
    private SpectrumAnalyzer.QueryDetails spectralRayDetectionResult;
    private ProcessParams processParams;
    private Consumer<? super String> onPolynomialComputed;
    private float[][] averageImage;
    private ProgressOperation operation;
    private DoubleQuadruplet originalPolynomial;

    public static Stage open(File file, ProgressOperation operation, Consumer<? super String> onPolynomialComputed) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "frame-debugger");
        Object configWindow;
        try {
            configWindow = fxmlLoader.load();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        var controller = (SpectralLineDebugger) fxmlLoader.getController();
        controller.operation = operation;
        controller.onPolynomialComputed = onPolynomialComputed;
        var stage = newStage();
        Scene scene = newScene((Parent) configWindow);
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

    private void updateCanvasSize(Scene scene) {
        if (image == null || scene == null) return;

        // Calculate available space for ScrollPane viewport
        double sceneWidth = scene.getWidth();
        double sceneHeight = scene.getHeight();

        // Account for padding and bottom panel (controls + status bar)
        double horizontalPadding = 40; // Border padding
        double bottomPanelHeight = status.prefHeight(-1) + 32 + 40; // Controls + status bar + padding

        double availableWidth = Math.max(200, sceneWidth - horizontalPadding);
        double availableHeight = Math.max(150, sceneHeight - bottomPanelHeight);

        // Set ScrollPane size to fit in available space
        canvasScrollPane.setPrefWidth(availableWidth);
        canvasScrollPane.setMaxWidth(availableWidth);
        canvasScrollPane.setPrefHeight(availableHeight);
        canvasScrollPane.setMaxHeight(availableHeight);

        // Calculate base scale to fit image in viewport when zoom = 1.0
        double scaleX = availableWidth / image.getWidth();
        double scaleY = availableHeight / image.getHeight();
        double baseScale = Math.min(scaleX, scaleY);

        // Apply zoom factor - allow canvas to grow beyond viewport
        double finalScale = Math.max(0.1, Math.min(10.0, baseScale * zoom));

        // Set canvas dimensions - can be larger than ScrollPane viewport
        double newWidth = image.getWidth() * finalScale;
        double newHeight = image.getHeight() * finalScale;

        canvas.setWidth(newWidth);
        canvas.setHeight(newHeight);
        canvas.setScaleX(1.0);
        canvas.setScaleY(1.0);
    }

    private void fireZoomChanged(Scene scene) {
        updateCanvasSize(scene);
        redraw();
    }

    private void redraw() {
        if (image == null) return;

        var graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Scale factor for drawing scaled image
        double scaleX = canvas.getWidth() / image.getWidth();
        double scaleY = canvas.getHeight() / image.getHeight();

        // Draw scaled image
        graphicsContext.drawImage(image, 0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw overlays scaled appropriately
        graphicsContext.setStroke(Color.RED);
        graphicsContext.setLineWidth(2.0);

        // Draw crosses for all sample points
        for (Point2D samplePoint : samplePoints) {
            drawCross(graphicsContext, samplePoint.x() * scaleX, samplePoint.y() * scaleY);
        }
    }

    private void prepareView(File file, ColorMode colorMode, Scene scene, Stage stage, ToggleGroup toggleGroup) {
        var converter = createImageConverter(colorMode);
        Platform.runLater(() -> progressBox.setVisible(true));
        var detector = new AverageImageCreator(converter,
                operation,
                event -> {
                    if (event instanceof ProgressEvent progress) {
                        BatchOperations.submitOneOfAKind("progress", () -> progressBar.setProgress(progress.getPayload().progress()));
                    }
                });
        try {
            reader = SerFileReader.of(file);
            detector.computeAverageImage(reader);
            averageImage = detector.getAverageImage();
            var tmpPath = TemporaryFolder.newTempFile("debug_", ".png");
            File imageFile = tmpPath.toFile();
            resetPolynomial.setDisable(true);
            Platform.runLater(() -> {
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
                        fireZoomChanged(scene);
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
                    resetPolynomial.setDisable(false);
                    redraw();
                });
                resetPolynomial.setOnAction(e -> {
                    lockedPolynomial = null;
                    polynomial = originalPolynomial.asPolynomial();
                    lockPolynomialCheckbox.setSelected(false);
                    processFrame(converter, reader, geometry, Integer.parseInt(frameId.getText()), imageFile, toggleGroup.getSelectedToggle() == average ? averageImage : null, scene);
                    polynomialTextField.setText(originalPolynomial.asPolynomialString());
                    resetPolynomial.setDisable(true);
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
                            if (id >= header.frameCount()) {
                                return header.frameCount() - 1;
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
                frameSlider.setMax(header.frameCount() - 1);
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


    private static void drawCross(GraphicsContext graphicsContext, double x, double y) {
        graphicsContext.strokeLine(x - 5, y, x + 5, y);
        graphicsContext.strokeLine(x, y - 5, x, y + 5);
    }


    public void close() throws Exception {
        reader.close();
    }

    private void processFrame(ImageConverter<float[][]> converter,
                              SerFileReader reader,
                              ImageGeometry geometry,
                              int frameId,
                              File imageFile,
                              float[][] average,
                              Scene scene) {
        float[][] source = average != null ? average : converter.createBuffer(geometry);
        if (average == null) {
            reader.seekFrame(frameId);
            converter.convert(frameId, reader.currentFrame().data(), geometry, source);
        }
        float[][] buffer = ImageWrapper.copyData(source);
        int width = geometry.width();
        int height = geometry.height();
        Double sunThreshold = sunDetectionThreshold.textProperty().getValue().isEmpty() ? null : Double.parseDouble(sunDetectionThreshold.textProperty().getValue());
        var analyzer = new SpectrumFrameAnalyzer(
                width,
                height,
                reader.header().isJSolexTrimmedSer(),
                sunThreshold
        );
        analyzer.analyze(buffer);
        processParams = ProcessParamsIO.loadDefaults();
        var instrument = processParams.observationDetails().instrument();
        var analysis = analyzer.result();
        analysis.distortionPolynomial().ifPresent(p -> {
            this.polynomial = p;
            this.originalPolynomial = analysis.distortionQuadruplet().orElse(null);
            if (this.originalPolynomial != null) {
                this.polynomialTextField.setText(originalPolynomial.asPolynomialString());
            }
            if (spectralRayDetectionResult == null) {
                var pixelSize = processParams.observationDetails().pixelSize();
                var candidates = new ArrayList<SpectrumAnalyzer.QueryDetails>();
                for (var line : SpectralRay.predefined()) {
                    if (line.wavelength().angstroms() > 0 && !line.emission()) {
                        candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 1, instrument));
                        candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 2, instrument));
                    }
                }
                int leftBorder = analysis.leftBorder().orElse(0);
                int rightBorder = analysis.rightBorder().orElse(width);
                var map = candidates
                        .stream()
                        .collect(Collectors.toMap(d -> d, details -> SpectrumAnalyzer.computeDataPoints(details, polynomial, leftBorder, rightBorder, width, height, buffer), (e1, e2) -> e1, LinkedHashMap::new));
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
            for (int i = 0; i < boostValue; i++) {
                new ArcsinhStretchingStrategy(0f, 0.25f, 0.25f).stretch(new ImageWrapper32(width, height, buffer, MutableMap.of()));
            }
        }
        var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
        var rgb = creator.generateDebugImage(lockedPolynomial);
        if (spectralRayDetectionResult != null) {
            var line = spectralRayDetectionResult.line();
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, line.wavelength(), spectralRayDetectionResult.pixelSize() * spectralRayDetectionResult.binning());
            var detector = new PhenomenaDetector(dispersion, line.wavelength(), 0);
            detector.setDetectEllermanBombsOrFlares(false);
            detector.setDetectRedshifts(spectralRayDetectionResult.line().equals(SpectralRay.H_ALPHA));
            detector.setDetectionListener(new PhenomenaListener() {
                @Override
                public void onRedshift(DoublePair rs) {
                    var x = rs.a();
                    var y = ((int) Math.round(polynomial.applyAsDouble(x))) + rs.b();
                    rgb.r()[(int) y][(int) x] = 0;
                    rgb.g()[(int) y][(int) x] = Constants.MAX_PIXEL_VALUE;
                    rgb.b()[(int) y][(int) x] = Constants.MAX_PIXEL_VALUE;
                }

                @Override
                public void onActiveRegion(int x) {
                    for (int y = 0; y < height; y++) {
                        rgb.r()[y + height + SPACING][x] = Constants.MAX_PIXEL_VALUE;
                        rgb.b()[y + height + SPACING][x] = Constants.MAX_PIXEL_VALUE;
                    }
                }
            });
            detector.performDetection(frameId, width, height, buffer, polynomial, reader.header());
        }
        ImageUtils.writeRgbImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), imageFile, EnumSet.of(ImageFormat.PNG));
        image = new Image(imageFile.toURI().toString());
        updateCanvasSize(scene);
        redraw();
        canvas.setOnMouseClicked(evt -> {
            // Convert canvas coordinates to original image coordinates
            double scaleX = canvas.getWidth() / image.getWidth();
            double scaleY = canvas.getHeight() / image.getHeight();
            var x = evt.getX() / scaleX;
            var y = evt.getY() / scaleY;
            double dist = computeDistanceToSpectralLine(detectedPolynomial, x, y);

            if (evt.getClickCount() == 2) {
                // Double-click: reset all points
                sampleDistances.clear();
                samplePoints.clear();
                computePolynomial.setDisable(true);
                polynomialTextField.setDisable(true);
                info.setText("");
                redraw();
                return;
            }

            // Single click: add a point
            sampleDistances.add(dist);
            samplePoints.add(new Point2D(x, y));
            double avgDist = computeAverageDistanceToSpectralLineFromSamples();
            info.setText(localized("average.distance.spectral.line") + " " + Math.round(avgDist));
            redraw();

            // Enable polynomial computation if we have enough points
            if (samplePoints.size() > 2) {
                computePolynomial.setDisable(false);
                polynomialTextField.setDisable(false);
            } else {
                computePolynomial.setDisable(true);
                polynomialTextField.setDisable(true);
            }
        });

        canvas.setOnMouseMoved(evt -> {
            var canvasX = evt.getX();
            var canvasY = evt.getY();

            // Convert canvas coordinates to image coordinates
            double scale = canvas.getWidth() / image.getWidth();
            var x = canvasX / scale;
            var y = canvasY / scale;

            // Show cursor position and distance information
            double dist = computeDistanceToSpectralLine(detectedPolynomial, x, y);
            double avgDist = computeAverageDistanceToSpectralLineFromSamples();
            var sb = new StringBuilder("(x=").append(format(x)).append(",y=")
                    .append(format(y)).append(", ")
                    .append(localized("distance.spectral.line.short")).append("=")
                    .append(format(dist))
                    .append(", ")
                    .append(localized("distance.spectral.line.avg.short")).append("=")
                    .append(format(avgDist)).append("). ");
            if (!samplePoints.isEmpty()) {
                    sb.append(localized("reset.points.tooltip"));
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
