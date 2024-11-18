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
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.processing.params.ScanDirection;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SetupsIO;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;
import static me.champeau.a4j.jsolex.processing.util.SolarParametersUtils.localDateTimeToJulianDate;

public class ExposureCalculator {
    private static final double J2000 = 2451545;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final double DEFAULT_PIXEL_SIZE = 2.4;
    public static final int DEFAULT_FOCAL_LENGTH = 400;

    @FXML
    private ChoiceBox<Setup> setup;
    @FXML
    private ChoiceBox<SpectroHeliograph> instrument;

    @FXML
    private TextField pixelSize;
    @FXML
    private TextField binning;
    @FXML
    private TextField focalLength;
    @FXML
    private TextField scanSpeed;
    @FXML
    private DatePicker date;
    @FXML
    private TextField exposure;
    @FXML
    private TextField fps;
    @FXML
    private RadioButton ra;
    @FXML
    private RadioButton dec;

    private Stage stage;

    public void setup(Stage stage) {
        pixelSize.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        binning.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        focalLength.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        scanSpeed.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));

        pixelSize.setText("2.4");
        binning.setText("1");
        focalLength.setText("400");
        scanSpeed.setText("8");

        pixelSize.textProperty().addListener(updateExposure());
        binning.textProperty().addListener(updateExposure());
        focalLength.textProperty().addListener(updateExposure());
        date.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate localDate) {
                return localDate.format(DateTimeFormatter.ofPattern(DATE_PATTERN));
            }

            @Override
            public LocalDate fromString(String s) {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(DATE_PATTERN));
            }
        });
        date.setValue(LocalDateTime.now(UTC).toLocalDate());
        date.valueProperty().addListener(updateExposure());
        scanSpeed.textProperty().addListener(updateExposure());
        var setups = SetupsIO.loadDefaults();
        if (setups.isEmpty()) {
            setups = List.of(createDefaultSetup());
        }
        setup.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            pixelSize.setText(String.valueOf(selected.pixelSize() == null ? DEFAULT_PIXEL_SIZE : selected.pixelSize()));
            focalLength.setText(String.valueOf(selected.focalLength() == null ? DEFAULT_FOCAL_LENGTH : selected.focalLength()));
        });
        setup.getItems().addAll(setups);
        setup.getSelectionModel().select(0);
        var shgs = SpectroHeliographsIO.loadDefaults();
        if (shgs.isEmpty()) {
            shgs = SpectroHeliographsIO.predefined();
        }
        instrument.getItems().addAll(shgs);
        instrument.getSelectionModel().select(0);
        instrument.getSelectionModel().selectedItemProperty().addListener(updateExposure());
        instrument.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpectroHeliograph spectroHeliograph) {
                return spectroHeliograph.label();
            }

            @Override
            public SpectroHeliograph fromString(String s) {
                return instrument.getItems().stream().filter(shg -> shg.label().equals(s)).findFirst().orElse(SpectroHeliograph.SOLEX);
            }
        });
        var toggleGroup = new ToggleGroup();
        ra.setToggleGroup(toggleGroup);
        dec.setToggleGroup(toggleGroup);
        ra.setSelected(true);
        toggleGroup.selectedToggleProperty().addListener(updateExposure());
        updateExposure().changed(null, null, null);
        this.stage = stage;

    }

    private static Setup createDefaultSetup() {
        return new Setup("Default", "Telescope", DEFAULT_FOCAL_LENGTH, 67, "Camera", DEFAULT_PIXEL_SIZE, null, null, false, false);
    }

    private <T> ChangeListener<T> updateExposure() {
        return (observable, oldValue, newValue) -> {
            double p = Double.parseDouble(pixelSize.getText());
            double bin = Double.parseDouble(binning.getText());
            double f = Double.parseDouble(focalLength.getText());
            double v = Double.parseDouble(scanSpeed.getText());
            var date = this.date.getValue().atStartOfDay(UTC).toLocalDateTime();
            var shg = instrument.getSelectionModel().getSelectedItem();
            if (shg != null) {
                double expo = recommendedExposureMillis(f, shg.focalLength(), shg.collimatorFocalLength(), p * bin, date, (int) v, ra.isSelected() ? ScanDirection.RA : ScanDirection.DEC);
                fps.setText(String.format("%.2f fps", 1 / (expo / 1000)));
                exposure.setText(String.format("%.2f ms", expo));
            }
        };
    }

    @FXML
    private void close() {
        stage.close();
    }

    @FXML
    private void chooseSetup() {
        SetupEditor.openEditor(stage, editor -> {
            var items = editor.getItems();
            if (items.isEmpty()) {
                items = List.of(createDefaultSetup());
            }
            setup.getItems().setAll(items);
            editor.getSelected().ifPresentOrElse(setup.getSelectionModel()::select, () -> setup.getSelectionModel().select(0));
        });
    }

    @FXML
    private void chooseInstrument() {
        SpectroHeliographEditor.openEditor(stage, editor -> {
            var items = editor.getItems();
            if (items.isEmpty()) {
                items = List.of(SpectroHeliograph.SOLEX);
            }
            instrument.getItems().setAll(items);
            editor.getSelected().ifPresentOrElse(instrument.getSelectionModel()::select, () -> instrument.getSelectionModel().select(0));
        });
    }

    private static double recommendedFrameRate(double focalLength, double cameraFocalLength, double collimatorFocalLength, double pixelSize, LocalDateTime date, int slewRate, ScanDirection direction) {
        var apparentSizeArcMins = solarDiskApparentSizeArcMin(date);
        var correction = direction == ScanDirection.RA ? Math.cos(toRadians(sunDeclinationDegrees(date))) : 1;
        return correction * solarDiskHeight(focalLength, cameraFocalLength, collimatorFocalLength, apparentSizeArcMins, pixelSize) / fullDiskScanTimeSeconds(apparentSizeArcMins, slewRate);
    }

    private static double recommendedExposureMillis(double focalLength, double cameraFocalLength, double collimatorFocalLength, double pixelSize, LocalDateTime date, int slewRate, ScanDirection scanDirection) {
        var fps = recommendedFrameRate(focalLength, cameraFocalLength, collimatorFocalLength, pixelSize, date, slewRate, scanDirection);
        return 1000 / fps;
    }

    private static double solarDiskHeight(double focalLength, double cameraFocalLength, double collimatorFocalLength, double apparentSizeArcMins, double pixelSize) {
        return 1000 * solarDiskOnSensorMillimeters(focalLength, cameraFocalLength, collimatorFocalLength, apparentSizeArcMins) / pixelSize;
    }

    private static double solarDiskOnSensorMillimeters(double focalLength, double cameraFocalLength, double collimatorFocalLength, double apparentSizeArcMins) {
        return solarDiskSizeAtSlitFocusMillimeters(apparentSizeArcMins, focalLength) * cameraFocalLength / collimatorFocalLength;
    }

    private static double solarDiskSizeAtSlitFocusMillimeters(double solarDiskArcMins, double focalLength) {
        return Math.tan(toRadians(solarDiskArcMins / 60)) * focalLength;
    }

    private static double fullDiskScanTimeSeconds(double apparentSizeArcMins, int slewRate) {
        return apparentSizeArcMins * 4 / slewRate;
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360.0;
        if (angle < 0) {
            angle += 360.0;
        }
        return angle;
    }

    private static double solarDiskApparentSizeArcMin(LocalDateTime date) {
        var julianDate = localDateTimeToJulianDate(date);
        var n = julianDate - J2000;
        var meanAnomaly = computeMeanAnomaly(n);
        var sunTrueAnomaly = computeTrueAnomaly(meanAnomaly);
        var trueAnomaly = meanAnomaly + sunTrueAnomaly;
        var distanceAU = computeDistanceAU(trueAnomaly);
        return 60 * 0.533128 / distanceAU;
    }

    private static double computeDistanceAU(double trueAnomaly) {
        return 1.000001018 * (1 - 0.01670862 * cos(toRadians(trueAnomaly)));
    }

    private static double computeTrueAnomaly(double meanAnomaly) {
        return 1.9148 * sin(toRadians(meanAnomaly)) + 0.02 * sin(toRadians(2 * meanAnomaly)) + 0.0003 * sin(toRadians(3 * meanAnomaly));
    }

    private static double computeMeanAnomaly(double n) {
        return normalizeAngle(357.5291 + 0.98560028 * n);
    }

    private static double sunDeclinationDegrees(LocalDateTime date) {
        var julianDate = localDateTimeToJulianDate(date);
        var n = julianDate - J2000;
        var meanAnomaly = computeMeanAnomaly(n);
        var sunTrueAnomaly = computeTrueAnomaly(meanAnomaly);
        var trueAnomaly = meanAnomaly + sunTrueAnomaly;
        var eclipticLongitude = normalizeAngle(trueAnomaly + 102.9372);
        var declination = Math.asin(sin(toRadians(eclipticLongitude)) * sin(toRadians(23.44)));
        return Math.toDegrees(declination);
    }

}
