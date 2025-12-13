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

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.listeners.RedshiftImagesProcessor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Controller for the custom animation creation dialog.
 * Allows users to create custom Doppler shift animations.
 */
public class CustomAnimationCreator {

    /**
     * Creates a new instance. Required by FXML.
     */
    public CustomAnimationCreator() {
    }

    /**
     * Default delay in milliseconds between animation frames.
     */
    public static final String DEFAULT_DELAY = "25";

    /**
     * Width input field.
     */
    @FXML
    public TextField width;

    /**
     * Height input field.
     */
    @FXML
    public TextField height;

    /**
     * Minimum pixel shift input field.
     */
    @FXML
    public TextField minShift;

    /**
     * Maximum pixel shift input field.
     */
    @FXML
    public TextField maxShift;

    /**
     * Animation title input field.
     */
    @FXML
    public TextField title;

    /**
     * Checkbox to enable animation generation.
     */
    @FXML
    public CheckBox generateAnim;

    /**
     * Checkbox to enable panel generation.
     */
    @FXML
    public CheckBox generatePanel;

    /**
     * Checkbox to enable animation annotation.
     */
    @FXML
    public CheckBox annotateAnim;

    /**
     * Label showing hint for minimum shift value.
     */
    @FXML
    public Label minShiftHint;

    /**
     * Label showing hint for maximum shift value.
     */
    @FXML
    public Label maxShiftHint;

    /**
     * Animation delay input field.
     */
    @FXML
    public TextField delay;

    /**
     * Label showing estimated disk space requirement.
     */
    @FXML
    public Label estimatedDiskSpace;

    /**
     * Color picker for annotation color.
     */
    @FXML
    public ColorPicker annotationColor;

    private int x;
    private int y;
    private RedshiftImagesProcessor redshiftProcessor;

    private Stage stage;

    /**
     * Sets up the custom animation creator dialog.
     * @param stage the dialog stage
     * @param processParams the processing parameters
     * @param range the pixel shift range
     * @param imageWidth the image width
     * @param imageHeight the image height
     * @param x the x coordinate
     * @param y the y coordinate
     * @param w the width
     * @param h the height
     * @param redshiftProcessor the redshift images processor
     * @param id the doppler shift ID
     */
    public void setup(Stage stage,
                      ProcessParams processParams,
                      PixelShiftRange range,
                      int imageWidth,
                      int imageHeight,
                      int x,
                      int y,
                      int w,
                      int h,
                      RedshiftImagesProcessor redshiftProcessor,
                      int id) {
        this.stage = stage;
        this.x = x;
        this.y = y;
        this.redshiftProcessor = redshiftProcessor;
        width.setTextFormatter(createDimensionFormatter());
        height.setTextFormatter(createDimensionFormatter());
        width.setText(Integer.toString(w));
        height.setText(Integer.toString(h));
        var minPixelShift = range.minPixelShift();
        var maxPixelShift = range.maxPixelShift();
        var minAbsShift = Math.min(Math.abs(minPixelShift), Math.abs(maxPixelShift));
        if (minAbsShift == 0) {
            minAbsShift = 1;
        }
        if (processParams.spectrumParams().ray().wavelength().nanos() > 0) {
            var maxDefaultShiftInPixels = redshiftProcessor.toPixels(2.0);
            minAbsShift = Math.min(minAbsShift, maxDefaultShiftInPixels);
        } else {
            minAbsShift = Math.round(minAbsShift * 100.0) / 100.0;
        }
        minPixelShift = -minAbsShift;
        maxPixelShift = minAbsShift;
        minShift.setTextFormatter(createShiftFormatter(range.minPixelShift(), range.maxPixelShift()));
        maxShift.setTextFormatter(createShiftFormatter(range.minPixelShift(), range.maxPixelShift()));
        minShift.setText(String.format(Locale.US, "%.2f", minPixelShift));
        maxShift.setText(String.format(Locale.US, "%.2f", maxPixelShift));
        annotateAnim.disableProperty().bind(generateAnim.selectedProperty().not());
        delay.disableProperty().bind(generateAnim.selectedProperty().not());
        if (processParams.spectrumParams().ray().wavelength().nanos() > 0) {
            minShiftHint.textProperty().bind(minShift.textProperty().map(s -> redshiftProcessor.toAngstroms(safeParseDouble(s))));
            maxShiftHint.textProperty().bind(maxShift.textProperty().map(s -> redshiftProcessor.toAngstroms(safeParseDouble(s))));
        } else {
            minShiftHint.setText(message("shift.hint"));
            maxShiftHint.setText(message("shift.hint"));
        }
        estimatedDiskSpace.textProperty().bind(Bindings.subtract(
            Bindings.createDoubleBinding(() -> Double.parseDouble(maxShift.getText()), maxShift.textProperty()),
            Bindings.createDoubleBinding(() -> Double.parseDouble(minShift.getText()), minShift.textProperty())
        ).map(n -> redshiftProcessor.estimateRequiredDiskSpace(n.doubleValue())));
        title.setText(String.format(message("custom.animation"), id));
        delay.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < 1) {
                    return 25;
                }
                return i;
            }
        }));
        delay.setText(DEFAULT_DELAY);
        annotationColor.setValue(Color.YELLOW);
        annotationColor.disableProperty().bind(annotateAnim.selectedProperty().not());
    }

    private static double safeParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TextFormatter<Integer> createDimensionFormatter() {
        return new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < 64) {
                    return 64;
                }
                // round to a multiple of 8
                return (i + 7) & ~7;
            }
        });
    }

    private static TextFormatter<Double> createShiftFormatter(double minPixelShift, double maxPixelShift) {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String s) {
                var i = super.fromString(s);
                if (i == null || i < minPixelShift) {
                    return minPixelShift;
                }
                if (i > maxPixelShift) {
                    return maxPixelShift;
                }
                return Math.round(i * 100.0) / 100.0;
            }

            @Override
            public String toString(Double aDouble) {
                if (aDouble != null) {
                    if (aDouble < minPixelShift) {
                        return String.format(Locale.US, "%.2f", minPixelShift);
                    }
                    if (aDouble > maxPixelShift) {
                        return String.format(Locale.US, "%.2f", maxPixelShift);
                    }
                    return String.format(Locale.US, "%.2f", aDouble);
                }
                return super.toString(aDouble);
            }
        });
    }

    private int width() {
        return Integer.parseInt(width.getText());
    }

    private int height() {
        return Integer.parseInt(height.getText());
    }

    private double minPixelShift() {
        return safeParseDouble(minShift.getText());
    }

    private double maxPixelShift() {
        return safeParseDouble(maxShift.getText());
    }

    @FXML
    private void close() {
        stage.close();
    }

    @FXML
    private void generate() {
        double imageCount = Double.parseDouble(maxShift.getText())-Double.parseDouble(minShift.getText());
        try {
            if (Files.getFileStore(TemporaryFolder.tempDir()).getUsableSpace() < redshiftProcessor.estimateRequiredBytesForProcessing(imageCount)) {
                var alert = AlertFactory.error(message("disk.space.error"));
                alert.showAndWait();
                return;
            }
        } catch (IOException e) {
            // ignore
        }
        stage.close();
        if (generateAnim.isSelected()) {
            BackgroundOperations.async(() -> redshiftProcessor.generateStandaloneAnimation(x, y, width(), height(), minPixelShift(), maxPixelShift(), title.getText(), "custom-anim", annotateAnim.isSelected(), Integer.parseInt(delay.getText()), asRGB(annotationColor.getValue())));
        }
        if (generatePanel.isSelected()) {
            BackgroundOperations.async(() -> redshiftProcessor.generateStandalonePanel(x, y, width(), height(), minPixelShift(), maxPixelShift(), title.getText(), "custom-panel", asRGB(annotationColor.getValue())));
        }
    }

    private static int[] asRGB(Color color) {
        return new int[] {
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255)
        };
    }
}