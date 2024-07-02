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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.ReferenceIntensities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SpectrumBrowser extends BorderPane {
    private static final int SPECTRUM_OFFSET = 60;
    private static final double ZOOM_FACTOR = 1.05; // Adjust zoom factor for better control
    private static final double H_ALPHA_WAVELENGTH = 6563.0;
    private static final IdentifiedLine[] IDENTIFIED_LINES = loadDefaultLines();
    private static final Comparator<IdentifiedLine> IDENTIFIED_LINE_COMPARATOR =
        Comparator.comparingInt(IdentifiedLine::difficulty).thenComparing(IdentifiedLine::wavelength);

    private final BooleanProperty colorizeSpectrum = new SimpleBooleanProperty();

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
                        Double.parseDouble(parts[0]),
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

    private double currentMinWavelength;
    private double currentMaxWavelength;
    private double visibleRange = 100.0;

    private final Canvas canvas;
    private final Set<IdentifiedLine> userDefinedLines = new HashSet<>();

    public SpectrumBrowser(double height) {
        centerWavelength(H_ALPHA_WAVELENGTH);

        canvas = new Canvas(1000, height);
        var scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        heightProperty().addListener((observableValue, number, h) -> {
            canvas.setHeight(h.doubleValue());
            drawSpectrum();
        });
        widthProperty().addListener((observableValue, number, w) -> {
            canvas.setWidth(w.doubleValue());
            drawSpectrum();
        });
        setCenter(scrollPane);
        drawSpectrum();

        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double deltaY = event.getDeltaY();
                if (deltaY != 0) {
                    double factor = deltaY > 0 ? 1 / ZOOM_FACTOR : ZOOM_FACTOR;
                    zoom(factor);
                }
            } else {
                double delta = event.getDeltaY();
                double shift = delta * (visibleRange / canvas.getHeight());
                currentMinWavelength = Math.max(currentMinWavelength-shift, ReferenceIntensities.INSTANCE.getMinWavelength());
                currentMaxWavelength = Math.min(currentMinWavelength + visibleRange, ReferenceIntensities.INSTANCE.getMaxWavelength() - visibleRange);
                drawSpectrum();
            }
            event.consume();
        });

        canvas.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                double y = event.getY();
                double wavelength = currentMinWavelength + y * (visibleRange / canvas.getHeight());
                userDefinedLines.add(new IdentifiedLine(wavelength, null, -1));
                drawSpectrum();
            }
        });
        setTop(createTopBar());
        setBottom(createBottomBar());
    }

    private Node createTopBar() {
        var hbox = new HBox();
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setOpaqueInsets(new Insets(8, 8, 8, 8));
        hbox.setSpacing(8);
        hbox.setPadding(new Insets(8, 8, 8, 8));
        var gtLabel = new Label(I18N.string(JSolEx.class, "spectrum-browser", "goto"));
        var textField = new TextField();
        textField.setPrefWidth(64);
        var unit = new Label("Å");
        var searchButton = new Button(I18N.string(JSolEx.class, "spectrum-browser", "go"));
        var choiceBox = new ChoiceBox<IdentifiedLine>();
        choiceBox.getItems().addAll(Arrays.stream(IDENTIFIED_LINES).sorted(IDENTIFIED_LINE_COMPARATOR).toList());
        choiceBox.getSelectionModel().selectedItemProperty().addListener((observableValue, identifiedLine, t1) -> searchByWavelength(t1.wavelength()));
        textField.setOnAction(unused -> searchByWavelength(textField.getText()));
        searchButton.setOnAction(unused -> searchByWavelength(textField.getText()));
        var colorize = new CheckBox(I18N.string(JSolEx.class, "spectrum-browser", "color"));
        colorizeSpectrum.bind(colorize.selectedProperty());
        colorize.selectedProperty().addListener((observableValue, aBoolean, t1) -> drawSpectrum());
        var zoomIn = new Button("+");
        zoomIn.setOnAction(evt -> zoom(1/ZOOM_FACTOR));
        var zoomOut = new Button("-");
        zoomOut.setOnAction(evt -> zoom(ZOOM_FACTOR));
        var help = new Button("?");
        help.setOnAction(evt -> {
            var helpMessage = AlertFactory.info(I18N.string(JSolEx.class, "spectrum-browser", "help.message"));
            helpMessage.showAndWait();
        });
        hbox.getChildren().addAll(gtLabel, textField, unit, searchButton, choiceBox, colorize, zoomOut, zoomIn, help);
        return hbox;
    }

    private HBox createBottomBar() {
        var hbox = new HBox();
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setOpaqueInsets(new Insets(8, 8, 8, 8));
        hbox.setSpacing(8);
        hbox.setPadding(new Insets(8, 8, 8, 8));
        var label = new Label();
        canvas.setOnMouseMoved(evt -> {
            var y = evt.getY();
            // determine wavelength from y position
            var wavelength = currentMinWavelength + y * (visibleRange / canvas.getHeight());
            label.textProperty().set(String.format(I18N.string(JSolEx.class, "spectrum-browser", "wavelength"), wavelength));
        });
        hbox.getChildren().add(label);
        return hbox;
    }

    private void centerWavelength(double centerWavelength) {
        var halfRange = visibleRange / 2.0;
        currentMinWavelength = centerWavelength - halfRange;
        currentMaxWavelength = centerWavelength + halfRange;
    }

    private void drawSpectrum() {
        var gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        var spectrumWidth = canvas.getWidth() * 0.7;
        var range = currentMaxWavelength - currentMinWavelength;
        var step = range / canvas.getHeight();
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeLine(SPECTRUM_OFFSET, 0, SPECTRUM_OFFSET, canvas.getHeight());
        gc.strokeLine(SPECTRUM_OFFSET + spectrumWidth, 0, SPECTRUM_OFFSET + spectrumWidth, canvas.getHeight());

        for (int i = 0; i < canvas.getHeight(); i++) {
            var wavelength = currentMinWavelength + i * step;
            var intensity = ReferenceIntensities.intensityAt(wavelength);

            var grayscale = 0.9 * (intensity / 10000.0);
            var color = colorizeSpectrum.get() ? createColor(grayscale, wavelength) : Color.gray(grayscale);
            gc.setStroke(color);
            gc.setLineWidth(1);
            gc.strokeLine(SPECTRUM_OFFSET, i, SPECTRUM_OFFSET + spectrumWidth, i);

        }

        drawLegend(step, gc);

        drawIdentifiedLines(gc, spectrumWidth, step, IDENTIFIED_LINES, Color.RED);
        drawIdentifiedLines(gc, spectrumWidth, step, userDefinedLines.toArray(new IdentifiedLine[0]), Color.BLUE);
    }

    private Color createColor(double grayscale, double wavelength) {
        var ray = new SpectralRay("", null, wavelength/10, false);
        var rgb = ray.toSimpleRGB();
        if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0) {
            return Color.gray(grayscale);
        }
        return Color.rgb((int) (grayscale * rgb[0]), (int) (grayscale * rgb[1]), (int) (grayscale * rgb[2]));
    }

    private void drawLegend(double step, GraphicsContext gc) {
        for (double wl = Math.floor(currentMinWavelength / 50) * 50; wl <= currentMaxWavelength; wl += 50) {
            var position = (wl - currentMinWavelength) / step;
            gc.setFill(Color.BLACK);
            gc.fillText(String.format(Locale.US, "%.0f", wl), 10, position + 5);
        }
    }

    private void drawIdentifiedLines(GraphicsContext gc, double spectrumWidth, double step, IdentifiedLine[] lines, Color color) {
        double previousY = -1;
        var sortedByWavelen = Arrays.stream(lines).sorted(Comparator.comparingDouble(IdentifiedLine::wavelength)).toList();
        for (var identifiedLine : sortedByWavelen) {
            var identifiedWavelength = identifiedLine.wavelength();
            if (identifiedWavelength >= currentMinWavelength && identifiedWavelength <= currentMaxWavelength) {
                var position1 = (identifiedWavelength - currentMinWavelength) / step;
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
        visibleRange *= factor;
        double centerWavelength = (currentMinWavelength + currentMaxWavelength) / 2.0;
        centerWavelength(centerWavelength);
        drawSpectrum();
    }

    private void searchByWavelength(String text) {
        try {
            var wavelength = Double.parseDouble(text);
            wavelength = searchByWavelength(wavelength);
            userDefinedLines.add(new IdentifiedLine(wavelength, null, -1));
        } catch (NumberFormatException e) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "spectrum-browser", "invalid.wavelength"));
            alert.showAndWait();
        }
    }

    private double searchByWavelength(double wavelength) {
        var currentWaveLength = currentMinWavelength + (currentMaxWavelength - currentMinWavelength) / 2.0;
        wavelength = Math.clamp(wavelength, ReferenceIntensities.INSTANCE.getMinWavelength(), ReferenceIntensities.INSTANCE.getMaxWavelength());
        var wavelengthProperty = new SimpleDoubleProperty(wavelength);
        wavelengthProperty.addListener((observable, oldValue, newValue) -> {
            centerWavelength(newValue.doubleValue());
            drawSpectrum();
        });
        var timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(wavelengthProperty, currentWaveLength)),
            new KeyFrame(Duration.millis(1500), new KeyValue(wavelengthProperty, wavelength, Interpolator.SPLINE(0.25, 0.0, 0.25, 1.0))
            )
        );
        timeline.playFromStart();
        return wavelength;
    }

    private record IdentifiedLine(double wavelength, String name, int difficulty) {
        @Override
        public String toString() {
            if (name == null) {
                return String.format(Locale.US, "%.2f", wavelength);
            } else {
                return String.format(Locale.US, "%s (%.2f)", name, wavelength);
            }
        }
    }
}
