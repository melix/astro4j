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

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.listeners.RedshiftImagesProcessor;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.BiConsumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class SphericalTomographyCreator {

    private static final int LAYER_COUNT = 5;

    @FXML
    public TextField depthAngstroms;
    @FXML
    public RadioButton redWingRadio;
    @FXML
    public RadioButton blueWingRadio;

    private Stage stage;
    private RedshiftImagesProcessor redshiftProcessor;
    private BiConsumer<Double, Double> onGenerate;
    private PixelShiftRange range;

    public void setup(Stage stage,
                      PixelShiftRange range,
                      RedshiftImagesProcessor redshiftProcessor,
                      BiConsumer<Double, Double> onGenerate) {
        this.stage = stage;
        this.redshiftProcessor = redshiftProcessor;
        this.onGenerate = onGenerate;
        this.range = range;

        depthAngstroms.setTextFormatter(createDepthFormatter());
        depthAngstroms.setText("1.5");
    }

    private static double safeParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TextFormatter<Double> createDepthFormatter() {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String s) {
                var d = super.fromString(s);
                if (d == null || d < 0.1) {
                    return 0.1;
                }
                if (d > 5.0) {
                    return 5.0;
                }
                return Math.round(d * 100.0) / 100.0;
            }

            @Override
            public String toString(Double aDouble) {
                if (aDouble != null) {
                    if (aDouble < 0.1) {
                        return String.format(Locale.US, "%.2f", 0.1);
                    }
                    if (aDouble > 5.0) {
                        return String.format(Locale.US, "%.2f", 5.0);
                    }
                    return String.format(Locale.US, "%.2f", aDouble);
                }
                return super.toString(aDouble);
            }
        });
    }

    private double getDepthAngstroms() {
        return safeParseDouble(depthAngstroms.getText());
    }

    private boolean isRedDirection() {
        return redWingRadio.isSelected();
    }

    @FXML
    private void close() {
        stage.close();
    }

    @FXML
    private void generate() {
        double depthAng = getDepthAngstroms();
        boolean towardsRed = isRedDirection();

        // Convert depth in angstroms to pixel shift
        double depthPixels = redshiftProcessor.toPixels(depthAng);

        // Compute min and max pixel shifts for 5 layers from 0 to depth
        double minShift, maxShift;
        if (towardsRed) {
            minShift = 0;
            maxShift = depthPixels;
        } else {
            minShift = -depthPixels;
            maxShift = 0;
        }

        // Clamp to available range
        minShift = Math.max(minShift, range.minPixelShift());
        maxShift = Math.min(maxShift, range.maxPixelShift());

        // Compute step size for exactly 5 layers
        double step = (maxShift - minShift) / (LAYER_COUNT - 1);
        if (step <= 0) {
            step = 0.25;
        }

        try {
            if (Files.getFileStore(TemporaryFolder.tempDir()).getUsableSpace() < redshiftProcessor.estimateRequiredBytesForProcessing(LAYER_COUNT)) {
                var alert = AlertFactory.error(message("disk.space.error"));
                alert.showAndWait();
                return;
            }
        } catch (IOException e) {
            // ignore
        }

        stage.close();
        double finalMinShift = minShift;
        double finalStep = step;
        BackgroundOperations.async(() -> onGenerate.accept(finalStep, finalMinShift));
    }

    public double getMinShift() {
        double depthAng = getDepthAngstroms();
        boolean towardsRed = isRedDirection();
        double depthPixels = redshiftProcessor.toPixels(depthAng);
        if (towardsRed) {
            return Math.max(0, range.minPixelShift());
        } else {
            return Math.max(-depthPixels, range.minPixelShift());
        }
    }

    public double getMaxShift() {
        double depthAng = getDepthAngstroms();
        boolean towardsRed = isRedDirection();
        double depthPixels = redshiftProcessor.toPixels(depthAng);
        if (towardsRed) {
            return Math.min(depthPixels, range.maxPixelShift());
        } else {
            return Math.min(0, range.maxPixelShift());
        }
    }

    public double getStepSize() {
        double min = getMinShift();
        double max = getMaxShift();
        double step = (max - min) / (LAYER_COUNT - 1);
        return step > 0 ? step : 0.25;
    }
}
