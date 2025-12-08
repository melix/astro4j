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

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;

import java.util.List;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.spectrum.ReferenceIntensities;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.sun.DistortionCorrection;
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.image.ImageMath;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.DoubleStream;

import static me.champeau.a4j.jsolex.app.JSolEx.IMAGE_FILES_EXTENSIONS;

public class SpectrumBrowser extends BorderPane {
    private static final int SPECTRUM_OFFSET = 60;
    private static final double ZOOM_FACTOR = 1.05; // Adjust zoom factor for better control
    private static final Wavelen H_ALPHA_WAVELENGTH = Wavelen.ofAngstroms(6563.0);
    private static final IdentifiedLine[] IDENTIFIED_LINES = loadDefaultLines();
    private static final Comparator<IdentifiedLine> IDENTIFIED_LINE_COMPARATOR =
        Comparator.comparingInt(IdentifiedLine::difficulty).thenComparing(i -> i.wavelength().angstroms());
    private static final double WIDTH_FACTOR = 0.7;
    public static final int CROP_HEIGHT = 64;

    private final DoubleProperty pixelSize = new SimpleDoubleProperty();
    private final BooleanProperty colorizeSpectrum = new SimpleBooleanProperty();
    private final CheckBox adjustDispersion = new CheckBox(I18N.string(JSolEx.class, "spectrum-browser", "adjust.dispersion"));
    private final AtomicBoolean animating = new AtomicBoolean(false);
    private final CheckBox flipSpectrumCheckBox = new CheckBox(I18N.string(JSolEx.class, "spectrum-browser", "flip.spectrum"));

    private SpectroHeliograph selectedShg;

    private double currentMinWavelength;
    private double currentMaxWavelength;
    private final DoubleProperty visibleRangeAngstroms = new SimpleDoubleProperty(100.0);

    private final StackPane stackPane;
    private final Canvas canvas;
    private final ImageView imageView;
    private final DoubleProperty imageRangeAngstroms = new SimpleDoubleProperty(visibleRangeAngstroms.get());

    private final Set<IdentifiedLine> userDefinedLines = new HashSet<>();
    private final Configuration configuration;

    public SpectrumBrowser(double height) {
        getStyleClass().add("params-dialog");
        centerWavelength(H_ALPHA_WAVELENGTH);
        configuration = Configuration.getInstance();
        canvas = new Canvas(1000, height);
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        var imageViewPane = new AnchorPane(imageView);
        AnchorPane.setLeftAnchor(imageView, (double) SPECTRUM_OFFSET);
        imageView.viewportProperty().bind(imageView.imageProperty().flatMap(i -> canvas.widthProperty().multiply(0.25).map(w -> {
            if (imageView.getImage() != null) {
                return new Rectangle2D(0, 0, w.intValue(), imageView.getImage().getHeight());
            }
            return new Rectangle2D(0, 0, 0, 0);
        })));
        stackPane = new StackPane(canvas, imageViewPane);
        stackPane.setAlignment(Pos.BASELINE_LEFT);
        widthProperty().addListener((observableValue, oldW, w) -> {
            canvas.setWidth(w.doubleValue());
            drawSpectrum();
        });
        heightProperty().addListener((observableValue, oldH, h) -> {
            if (oldH != null && oldH.doubleValue() != h.doubleValue()) {
                canvas.setHeight(h.doubleValue());
                drawSpectrum();
            }
        });
        setCenter(stackPane);
        var startY = new AtomicReference<Double>();
        stackPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, evt -> {
            if (evt.isControlDown()) {
                return;
            }
            if (startY.get() == null) {
                startY.set(evt.getY());
                return;
            }
            var deltaY = evt.getY() - startY.get();
            startY.set(evt.getY());
            var shift = deltaY * (visibleRangeAngstroms.get() / canvas.getHeight());
            currentMinWavelength = Math.max(currentMinWavelength - shift, ReferenceIntensities.INSTANCE.getMinWavelength());
            currentMaxWavelength = Math.min(currentMinWavelength + visibleRangeAngstroms.get(), ReferenceIntensities.INSTANCE.getMaxWavelength() - visibleRangeAngstroms.get());
            drawSpectrum();
        });
        stackPane.addEventFilter(MouseEvent.MOUSE_RELEASED, evt -> startY.set(null));
        stackPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double deltaY = event.getDeltaY();
                if (deltaY != 0) {
                    double factor = deltaY > 0 ? 1 / ZOOM_FACTOR : ZOOM_FACTOR;
                    double mouseY = event.getY();
                    zoomAroundPosition(factor, mouseY);
                }
            } else {
                double delta = event.getDeltaY();
                double shift = delta * (visibleRangeAngstroms.get() / canvas.getHeight());
                currentMinWavelength = Math.max(currentMinWavelength - shift, ReferenceIntensities.INSTANCE.getMinWavelength());
                currentMaxWavelength = Math.min(currentMinWavelength + visibleRangeAngstroms.get(), ReferenceIntensities.INSTANCE.getMaxWavelength() - visibleRangeAngstroms.get());
                drawSpectrum();
            }
            event.consume();
        });

        stackPane.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                double y = event.getY();
                var idx = flipSpectrumCheckBox.isSelected() ? canvas.getHeight() - y : y;
                double wavelength = currentMinWavelength + idx * (visibleRangeAngstroms.get() / canvas.getHeight());
                userDefinedLines.add(new IdentifiedLine(Wavelen.ofAngstroms(wavelength), null, -1));
                drawSpectrum();
            }
        });
        flipSpectrumCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            drawSpectrum();
        });
        setTop(createTopBar());
        setBottom(createBottomBar());
        Platform.runLater(this::adjustZoomToOpticsAndDraw);
    }

    private Node createTopBar() {
        var vbox = new VBox();
        vbox.setSpacing(2);
        
        // Apply styling to labels and controls
        var gtLabel = new Label(I18N.string(JSolEx.class, "spectrum-browser", "goto"));
        gtLabel.getStyleClass().add("field-label");
        var textField = new TextField();
        textField.getStyleClass().add("text-field");
        textField.setPrefWidth(CROP_HEIGHT);
        var unit = new Label("Å");
        unit.getStyleClass().add("field-unit");
        adjustDispersion.setSelected(true);
        adjustDispersion.selectedProperty().addListener((observableValue, aBoolean, value) -> drawSpectrum());
        adjustDispersion.getStyleClass().add("check-box");
        var searchButton = new Button(I18N.string(JSolEx.class, "spectrum-browser", "go"));
        searchButton.getStyleClass().add("default-button");
        var choiceBox = new ChoiceBox<IdentifiedLine>();
        choiceBox.getStyleClass().add("choice-box");
        choiceBox.getItems().addAll(Arrays.stream(IDENTIFIED_LINES).sorted(IDENTIFIED_LINE_COMPARATOR).toList());
        choiceBox.getSelectionModel().selectedItemProperty().addListener((observableValue, identifiedLine, t1) -> searchByWavelength(t1.wavelength()));
        textField.setOnAction(unused -> searchByWavelength(textField.getText()));
        searchButton.setOnAction(unused -> searchByWavelength(textField.getText()));
        var colorize = new CheckBox(I18N.string(JSolEx.class, "spectrum-browser", "color"));
        colorize.getStyleClass().add("check-box");
        colorizeSpectrum.bind(colorize.selectedProperty());
        colorize.selectedProperty().addListener((observableValue, aBoolean, t1) -> drawSpectrum());
        var zoomIn = new Button("+");
        zoomIn.getStyleClass().add("default-button");
        zoomIn.setOnAction(evt -> zoom(1 / ZOOM_FACTOR));
        var zoomOut = new Button("-");
        zoomOut.getStyleClass().add("default-button");
        zoomOut.setOnAction(evt -> zoom(ZOOM_FACTOR));
        var shg = new ChoiceBox<SpectroHeliograph>();
        shg.getStyleClass().add("choice-box");
        shg.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpectroHeliograph spectroHeliograph) {
                return spectroHeliograph.label();
            }

            @Override
            public SpectroHeliograph fromString(String s) {
                return shg.getItems().stream().filter(spectroHeliograph -> spectroHeliograph.label().equals(s)).findFirst().orElse(null);
            }
        });
        shg.getItems().addAll(SpectroHeliographsIO.loadDefaults());
        if (shg.getItems().isEmpty()) {
            shg.getItems().add(SpectroHeliograph.SOLEX);
        }
        shg.getSelectionModel().selectedItemProperty().addListener((observableValue, spectroHeliograph, selected) -> {
            if (selected != null) {
                selectedShg = selected;
                adjustZoomToOpticsAndDraw();
            }
        });
        var processParams = ProcessParamsIO.loadDefaults();
        var instrument = processParams.observationDetails().instrument();
        if (shg.getItems().contains(instrument)) {
            shg.getSelectionModel().select(instrument);
        } else {
            shg.getSelectionModel().selectFirst();
        }
        var initialPixelSize = computeInitialPixelSize(processParams);
        var pixelSizeLabel = new Label(I18N.string(JSolEx.class, "spectrum-browser", "pixel.size"));
        pixelSizeLabel.getStyleClass().add("field-label");
        var pixelSizeValue = new TextField();
        pixelSizeValue.getStyleClass().add("text-field");
        pixelSizeValue.setPrefWidth(48);
        pixelSizeValue.setTextFormatter(createPixelSizeFormatter());
        pixelSizeValue.textProperty().addListener((observableValue, s, value) -> {
            double ps;
            try {
                ps = Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return;
            }
            pixelSize.set(ps);
            adjustZoomToOpticsAndDraw();
        });
        pixelSizeValue.setText("" + initialPixelSize);
        var instrumentLabel = new Label(I18N.string(JSolEx.class, "spectrum-browser", "instrument"));
        instrumentLabel.getStyleClass().add("field-label");
        var help = new Button("?");
        help.getStyleClass().add("default-button");
        help.setOnAction(evt -> {
            var helpMessage = AlertFactory.info(I18N.string(JSolEx.class, "spectrum-browser", "help.message"));
            helpMessage.setTitle(I18N.string(JSolEx.class, "spectrum-browser", "help"));
            helpMessage.setHeaderText(I18N.string(JSolEx.class, "spectrum-browser", "help"));
            helpMessage.showAndWait();
        });
        var loadImage = new Button(I18N.string(JSolEx.class, "spectrum-browser", "identify"));
        loadImage.getStyleClass().add("primary-button");
        loadImage.setOnAction(evt -> {
            var fileChooser = new FileChooser();
            configuration.findLastOpenDirectory(Configuration.DirectoryKind.SPECTRUM_IDENTIFICATION).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            fileChooser.getExtensionFilters().add(IMAGE_FILES_EXTENSIONS);
            var file = fileChooser.showOpenDialog(null);
            if (file != null) {
                configuration.updateLastOpenDirectory(file.getParentFile().toPath(), Configuration.DirectoryKind.SPECTRUM_IDENTIFICATION);
                BackgroundOperations.async(() -> performWavelengthIdentification(file));
            }

        });
        var hide = new Button(I18N.string(JSolEx.class, "spectrum-browser", "hide"));
        hide.getStyleClass().add("default-button");
        hide.setOnAction(evt -> imageView.setImage(null));
        hide.disableProperty().bind(imageView.imageProperty().isNull());
        flipSpectrumCheckBox.getStyleClass().add("check-box");
        
        var line1 = new HBox();
        line1.setAlignment(Pos.CENTER);
        line1.setSpacing(8);
        line1.setPadding(new Insets(4, 4, 4, 4));
        var line2 = new HBox();
        line2.setAlignment(Pos.CENTER);
        line2.setSpacing(8);
        line2.setPadding(new Insets(4, 4, 4, 4));
        line1.getChildren().addAll(gtLabel, textField, unit, searchButton, choiceBox, colorize, flipSpectrumCheckBox);
        line2.getChildren().addAll(instrumentLabel, shg, pixelSizeLabel, pixelSizeValue, adjustDispersion, zoomIn, zoomOut, help, loadImage, hide);
        vbox.getChildren().addAll(line1, line2);
        return vbox;
    }

    private double calculatePosition(double originalPosition, double spectrumHeight) {
        return flipSpectrumCheckBox.isSelected()
            ? spectrumHeight - originalPosition
            : originalPosition;
    }


    private void showIdentificationFailure() {
        Platform.runLater(() -> {
            imageView.setImage(null);
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "spectrum-browser", "identification.failed"));
            alert.showAndWait();
        });
    }

    private void performWavelengthIdentification(File file) {
        var wrapper = Loader.loadImage(file);
        if (wrapper instanceof RGBImage rgb) {
            wrapper = rgb.toMono();
        }
        if (wrapper instanceof ImageWrapper32 image) {
            CaptureSoftwareMetadataHelper.readSharpcapMetadata(file)
                .or(() -> CaptureSoftwareMetadataHelper.readFireCaptureMetadata(file))
                .ifPresent(md -> {
                    var observationDetails = ProcessParamsIO.loadDefaults().observationDetails();
                    Platform.runLater(() -> pixelSize.set(observationDetails.pixelSize() * md.binning()));
                });
            // in order to improve distorsion correction, and because identification is likely to use an image which is full height
            // we are going to perform a polynomial detection based on a limited area
            var polynomialImage = cropForPolynomialDetection(image);
            var width = image.width();
            var analyzer = new SpectrumFrameAnalyzer(width, polynomialImage.height(), false, null);
            analyzer.analyze(polynomialImage.data());
            var result = analyzer.result();
            result.distortionPolynomial().ifPresentOrElse(polynomial -> {
                var distorsionCorrection = new DistortionCorrection(image.data(), width, image.height());
                var correctedImage = distorsionCorrection.polynomicalCorrectionHeightRestricted(polynomial);
                var corrected = correctedImage.data();
                for (float[] line : corrected) {
                    for (float v : line) {
                        if (Float.isNaN(v)) {
                            showIdentificationFailure();
                            return;
                        }
                    }
                }
                var height = correctedImage.height();
                int minX = result.leftBorder().orElse(0);
                if (minX > 0) {
                    minX += 5 * width / 100;
                }
                int maxX = result.rightBorder().orElse(width);
                if (maxX < width) {
                    maxX -= 5 * width / 100;
                }
                var range = maxX - minX;
                int finalMinX = minX;
                int finalMaxX = maxX;
                double[] lineAverages = new double[height];
                var flipped = flipSpectrumCheckBox.isSelected();
                for (int y = 0; y < height; y++) {
                    double sum = 0;
                    for (int x = minX; x < maxX; x++) {
                        var v = corrected[y][x] / Constants.MAX_PIXEL_VALUE;
                        sum += v;
                    }
                    var yy = flipped ? height - y - 1 : y;
                    lineAverages[yy] = sum / range;
                }
                var localMinima = identifyLocalMinima(lineAverages);

                var step = ReferenceIntensities.INSTANCE.getStep();
                DoubleStream.iterate(ReferenceIntensities.INSTANCE.getMinWavelength(), wl -> wl < ReferenceIntensities.INSTANCE.getMaxWavelength(), wl -> wl + step)
                    .parallel()
                    .mapToObj(wl -> {
                        var baseDispersion = computeDispersion(Wavelen.ofAngstroms(wl));
                        if (Double.isNaN(baseDispersion.angstromsPerPixel())) {
                            return new Score(Wavelen.ofAngstroms(wl), Double.MAX_VALUE);
                        }
                        double[] ref = new double[height];
                        double maxAvg = 0;
                        for (int y = 0; y < height; y++) {
                            var currentWl = Wavelen.ofAngstroms(wl + y * baseDispersion.angstromsPerPixel());
                            ref[y] = ReferenceIntensities.intensityAt(currentWl) / 10000;
                            maxAvg = Math.max(maxAvg, lineAverages[y]);
                        }
                        var localRefMinima = identifyLocalMinima(ref);
                        var total = 0d;
                        for (int y = 0; y < height; y++) {
                            var diff = ref[y] - lineAverages[y];
                            var diff2 = ref[y] - lineAverages[y] / maxAvg;
                            total += Math.min(Math.abs(diff), Math.abs(diff2));
                        }
                        var common = (BitSet) localMinima.clone();
                        common.and(localRefMinima);
                        var weight = Math.pow(common.cardinality() / (double) Math.max(localRefMinima.cardinality(), localMinima.cardinality()), 2);
                        total = total / weight;
                        return new Score(Wavelen.ofAngstroms(wl), total);
                    })
                    .min(Comparator.comparingDouble(Score::score))
                    .ifPresent(score -> {
                        var disp = computeDispersion(score.wavelength);
                        if (Double.isNaN(disp.angstromsPerPixel())) {
                            return;
                        }
                        visibleRangeAngstroms.set(disp.angstromsPerPixel() * height);
                        imageRangeAngstroms.set(visibleRangeAngstroms.get());
                        Platform.runLater(() -> {
                            var writableImage = new WritableImage(range, height);
                            for (int y = 0; y < height; y++) {
                                for (int x = finalMinX; x < finalMaxX; x++) {
                                    var v = corrected[y][x] / Constants.MAX_PIXEL_VALUE;
                                    writableImage.getPixelWriter().setColor(x - finalMinX, y, Color.gray(v, .8));
                                }
                                imageView.fitHeightProperty().set(canvas.heightProperty().flatMap(w ->
                                    visibleRangeAngstroms.flatMap(visible ->
                                        imageRangeAngstroms.map(r -> w.doubleValue() * r.doubleValue() / visible.doubleValue()))
                                ).getValue());
                                imageView.setImage(writableImage);
                            }
                            adjustDispersion.setSelected(false);
                            var targetWavelen = score.wavelength.plusAngstroms(disp.angstromsPerPixel() * height / 2);
                            searchByWavelength(targetWavelen);
                        });
                    });
            }, this::showIdentificationFailure);
        } else {
            AlertFactory.error(I18N.string(JSolEx.class, "spectrum-browser", "unsupported.format")).showAndWait();
        }
    }

    private static ImageWrapper32 cropForPolynomialDetection(ImageWrapper32 image) {
        var lineAverages = ImageMath.newInstance().lineAverages(image.asImage());
        int minLine = 0;
        double minValue = Double.MAX_VALUE;
        for (int i = 0; i < image.height(); i++) {
            if (lineAverages[i] < minValue) {
                minValue = lineAverages[i];
                minLine = i;
            }
        }
        int yMin = Math.max(0, minLine - CROP_HEIGHT);
        int yMax = Math.min(image.height(), minLine + CROP_HEIGHT);
        int restrictedHeight = yMax - yMin;
        var cropped = new float[restrictedHeight][image.width()];
        var width = image.width();
        for (int y = yMin; y < yMax; y++) {
            System.arraycopy(image.data()[y], 0, cropped[y - yMin], 0, width);
        }
        return new ImageWrapper32(image.width(), restrictedHeight, cropped, Map.of());
    }

    private static double[] performSmoothing(double[] values) {
        double[] result = new double[values.length];
        double max = 0;
        result[0] = (2 * values[0] + values[1]) / 3d;
        for (int i = 1; i < values.length - 1; i++) {
            result[i] = (values[i - 1] + 2 * values[i] + values[i + 1]) / 4d;
            max = Math.max(max, result[i]);
        }
        result[values.length - 1] = (2 * values[values.length - 1] + values[values.length - 2]) / 3d;
        return result;
    }

    private BitSet identifyLocalMinima(double[] lineAverages) {
        int length = lineAverages.length;
        BitSet minima = new BitSet(length);

        if (length < 3) {
            // No local minima possible with less than 3 points
            return minima;
        }

        double[] smoothed = performSmoothing(lineAverages);

        for (int i = 1; i < length - 1; i++) {
            if (smoothed[i] < smoothed[i - 1] && smoothed[i] < smoothed[i + 1]) {
                minima.set(i);
            }
        }

        return minima;
    }

    private static double computeInitialPixelSize(ProcessParams processParams) {
        var initialPixelSize = processParams.observationDetails().pixelSize() != null ? processParams.observationDetails().pixelSize() : 2.4;
        if (processParams.observationDetails().binning() != null) {
            initialPixelSize *= processParams.observationDetails().binning();
        }
        return initialPixelSize;
    }

    private static TextFormatter<Double> createPixelSizeFormatter() {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String s) {
                try {
                    var value = super.fromString(s);
                    if (value != null) {
                        if (value < 0) {
                            return 0d;
                        }
                    }
                    return value;
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        });
    }

    private void adjustZoomToOpticsAndDraw() {
        adjustZoomToOptics();
        drawSpectrum();
    }

    private void adjustZoomToOptics() {
        var centerWavelength = Wavelen.ofAngstroms(currentMinWavelength + visibleRangeAngstroms.get() / 2.0);
        var dispersion = computeDispersion(centerWavelength);
        if (!Double.isNaN(dispersion.angstromsPerPixel())) {
            // adjust zoom level so that it matches the expected dispersion
            visibleRangeAngstroms.set(canvas.getHeight() * dispersion.angstromsPerPixel());
        }
        centerWavelength(centerWavelength);
    }

    private Dispersion computeDispersion(Wavelen centerWavelength) {
        return SpectrumAnalyzer.computeSpectralDispersion(selectedShg, centerWavelength, pixelSize.get());
    }

    private HBox createBottomBar() {
        var hbox = new HBox();
        hbox.getStyleClass().add("status-bar");
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setSpacing(8);
        var label = new Label();
        label.getStyleClass().add("status-text");
        stackPane.setOnMouseMoved(evt -> {
            var y = evt.getY();
            // determine wavelength from y position
            var wavelength = currentMinWavelength + y * (visibleRangeAngstroms.get() / canvas.getHeight());
            label.textProperty().set(String.format(I18N.string(JSolEx.class, "spectrum-browser", "wavelength"), wavelength));
        });
        hbox.getChildren().add(label);
        return hbox;
    }

    private void centerWavelength(Wavelen centerWavelength) {
        var halfRange = visibleRangeAngstroms.get() / 2.0;
        currentMinWavelength = centerWavelength.angstroms() - halfRange;
        currentMaxWavelength = centerWavelength.angstroms() + halfRange;
    }

    private void drawSpectrum() {
        if (adjustDispersion.isSelected() && !animating.get()) {
            // For performance reasons, we don't recompute dispersion during animations
            adjustZoomToOptics();
        }

        var gc = canvas.getGraphicsContext2D();
        var height = canvas.getHeight();
        gc.clearRect(0, 0, canvas.getWidth(), height);

        var spectrumWidth = canvas.getWidth() * WIDTH_FACTOR;
        var range = currentMaxWavelength - currentMinWavelength;
        var step = range / height;

        // Draw spectrum bounds
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeLine(SPECTRUM_OFFSET, 0, SPECTRUM_OFFSET, height);
        gc.strokeLine(SPECTRUM_OFFSET + spectrumWidth, 0, SPECTRUM_OFFSET + spectrumWidth, height);

        for (int i = 0; i < height; i++) {
            // Calculate wavelength and intensity
            var wavelength = Wavelen.ofAngstroms(currentMinWavelength + i * step);
            var intensity = ReferenceIntensities.intensityAt(wavelength);
            var grayscale = 0.9 * (intensity / 10000.0);

            // Determine drawing position, flipped if necessary
            var position = calculatePosition(i, height);

            // Choose color
            var color = colorizeSpectrum.get() ? createColor(grayscale, wavelength) : Color.gray(grayscale);

            // Draw spectrum line
            gc.setStroke(color);
            gc.setLineWidth(1);
            gc.strokeLine(SPECTRUM_OFFSET, position, SPECTRUM_OFFSET + spectrumWidth, position);
        }

        // Draw legend and identified lines
        drawLegend(step, gc);
        drawIdentifiedLines(gc, spectrumWidth, step, IDENTIFIED_LINES, Color.RED);
        drawIdentifiedLines(gc, spectrumWidth, step, userDefinedLines.toArray(new IdentifiedLine[0]), Color.BLUE);
    }

    private Color createColor(double grayscale, Wavelen wavelength) {
        var ray = new SpectralRay("", null, wavelength, false, List.of());
        var rgb = ray.toSimpleRGB();
        if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0) {
            return Color.gray(grayscale);
        }
        return Color.rgb((int) (grayscale * rgb[0]), (int) (grayscale * rgb[1]), (int) (grayscale * rgb[2]));
    }

    private void drawLegend(double step, GraphicsContext gc) {
        var spectrumHeight = gc.getCanvas().getHeight();
        for (double wl = Math.floor(currentMinWavelength / 50) * 50; wl <= currentMaxWavelength; wl += 50) {
            var originalPosition = (wl - currentMinWavelength) / step;
            var position = calculatePosition(originalPosition, spectrumHeight);
            gc.setFill(Color.BLACK);
            gc.fillText(String.format(Locale.US, "%.0f", wl), 10, position + 5);
        }
    }

    private void drawIdentifiedLines(GraphicsContext gc, double spectrumWidth, double step, IdentifiedLine[] lines, Color color) {
        double previousY = -1;
        var height = canvas.getHeight();
        var sortedByWavelen = Arrays.stream(lines).sorted(Comparator.comparingDouble(i ->
                flipSpectrumCheckBox.isSelected() ? -i.wavelength().angstroms() : i.wavelength().angstroms()))
                .toList();
        for (var identifiedLine : sortedByWavelen) {
            var identifiedWavelength = identifiedLine.wavelength().angstroms();
            if (identifiedWavelength >= currentMinWavelength && identifiedWavelength <= currentMaxWavelength) {
                var position1 = calculatePosition((identifiedWavelength - currentMinWavelength) / step, height);
                var position2 = position1;
                if (position1 - previousY < 18) {
                    position2 = previousY + 18;
                }
                previousY = position2;
                gc.setStroke(color);
                gc.setLineDashes(5);
                gc.setLineCap(StrokeLineCap.BUTT);
                gc.strokeLine(SPECTRUM_OFFSET + spectrumWidth + 5, position1, SPECTRUM_OFFSET + spectrumWidth + 50, position2);
                gc.setLineDashes(0);
                gc.setFill(color);
                gc.fillText(identifiedLine.toString(), SPECTRUM_OFFSET + spectrumWidth + 55, position2 + 5);
            }
        }
    }

    private void zoom(double factor) {
        double centerWavelength = (currentMinWavelength + currentMaxWavelength) / 2.0;
        adjustDispersion.setSelected(false);
        visibleRangeAngstroms.set(visibleRangeAngstroms.get() * factor);
        centerWavelength(Wavelen.ofAngstroms(centerWavelength));
        drawSpectrum();
    }

    private void zoomAroundPosition(double factor, double mouseY) {
        var height = canvas.getHeight();
        var idx = flipSpectrumCheckBox.isSelected() ? height - mouseY : mouseY;
        double wavelengthAtMouse = currentMinWavelength + idx * (visibleRangeAngstroms.get() / height);
        adjustDispersion.setSelected(false);
        double oldRange = visibleRangeAngstroms.get();
        double newRange = oldRange * factor;
        visibleRangeAngstroms.set(newRange);
        double relativePosition = idx / height;
        currentMinWavelength = wavelengthAtMouse - relativePosition * newRange;
        currentMinWavelength = Math.max(currentMinWavelength, ReferenceIntensities.INSTANCE.getMinWavelength());
        currentMaxWavelength = currentMinWavelength + newRange;
        if (currentMaxWavelength > ReferenceIntensities.INSTANCE.getMaxWavelength()) {
            currentMaxWavelength = ReferenceIntensities.INSTANCE.getMaxWavelength();
            currentMinWavelength = currentMaxWavelength - newRange;
        }
        drawSpectrum();
    }

    private void searchByWavelength(String text) {
        try {
            var wavelength = Wavelen.ofAngstroms(Double.parseDouble(text));
            wavelength = searchByWavelength(wavelength);
            userDefinedLines.add(new IdentifiedLine(wavelength, null, -1));
        } catch (NumberFormatException e) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "spectrum-browser", "invalid.wavelength"));
            alert.showAndWait();
        }
    }

    private Wavelen searchByWavelength(Wavelen wl) {
        var currentWaveLength = currentMinWavelength + (currentMaxWavelength - currentMinWavelength) / 2.0;
        var wavelength = Math.clamp(wl.angstroms(), ReferenceIntensities.INSTANCE.getMinWavelength(), ReferenceIntensities.INSTANCE.getMaxWavelength());
        var wavelengthProperty = new SimpleDoubleProperty(wavelength);
        wavelengthProperty.addListener((observable, oldValue, newValue) -> {
            centerWavelength(Wavelen.ofAngstroms(newValue.doubleValue()));
            drawSpectrum();
        });
        var timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(wavelengthProperty, currentWaveLength)),
            new KeyFrame(Duration.millis(1500), new KeyValue(wavelengthProperty, wavelength, Interpolator.SPLINE(0.25, 0.0, 0.25, 1.0))
            )
        );
        timeline.setOnFinished(evt -> {
            animating.set(false);
            drawSpectrum();
        });
        animating.set(true);
        timeline.playFromStart();
        return Wavelen.ofAngstroms(wavelength);
    }

    private record IdentifiedLine(Wavelen wavelength, String name, int difficulty) {
        @Override
        public String toString() {
            if (name == null) {
                return String.format(Locale.US, "%.2f", wavelength.angstroms());
            } else {
                return String.format(Locale.US, "%s (%.2f)", name, wavelength.angstroms());
            }
        }
    }

    private record Score(
        Wavelen wavelength,
        double score
    ) {
    }

    private static IdentifiedLine[] loadDefaultLines() {
        try (var reader = new BufferedReader(new InputStreamReader(SpectrumBrowser.class.getResourceAsStream("interesting-lines.txt"), StandardCharsets.UTF_8))) {
            List<IdentifiedLine> lines = new ArrayList<>();
            String cur;
            while ((cur = reader.readLine()) != null) {
                if (cur.startsWith("#")) {
                    continue;
                }
                var parts = cur.split(";");
                if (parts.length == 4) {
                    var line = new IdentifiedLine(
                        Wavelen.ofAngstroms(Double.parseDouble(parts[0])),
                        parts[1] + " (" + parts[2] + ")",
                        Integer.parseInt(parts[3])
                    );
                    lines.add(line);
                }
            }
            return lines.toArray(new IdentifiedLine[0]);
        } catch (IOException e) {
            return new IdentifiedLine[0];
        }
    }

}
