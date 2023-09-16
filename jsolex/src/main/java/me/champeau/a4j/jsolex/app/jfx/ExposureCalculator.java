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

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;

public class ExposureCalculator {
    @FXML
    private TextField pixelSize;
    @FXML
    private TextField binning;
    @FXML
    private TextField focalLength;
    @FXML
    private TextField scanSpeed;
    @FXML
    private TextField declination;
    @FXML
    private TextField exposure;

    private Stage stage;

    public void setup(Stage stage) {
        pixelSize.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        binning.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        focalLength.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        scanSpeed.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        declination.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));

        pixelSize.setText("2.4");
        binning.setText("1");
        focalLength.setText("400");
        scanSpeed.setText("8");

        pixelSize.textProperty().addListener(updateExposure());
        binning.textProperty().addListener(updateExposure());
        focalLength.textProperty().addListener(updateExposure());
        scanSpeed.textProperty().addListener(updateExposure());
        declination.textProperty().addListener(updateExposure());

        declination.setText("30");

        this.stage = stage;

    }

    private ChangeListener<String> updateExposure() {
        return (observable, oldValue, newValue) -> {
            double p = Double.parseDouble(pixelSize.getText());
            double bin = Double.parseDouble(binning.getText());
            double f = Double.parseDouble(focalLength.getText());
            double v = Double.parseDouble(scanSpeed.getText());
            double delta = Double.parseDouble(declination.getText());
            double expo = 1000 * ((8.79 * p * bin) / (f * v * Math.cos(Math.toRadians(delta))));
            exposure.setText(String.format("%.2f", expo));
        };
    }

    @FXML
    private void close() {
        stage.close();
    }

}
