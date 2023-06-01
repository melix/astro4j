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

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.DebugParams;
import me.champeau.a4j.jsolex.processing.params.GeometryParams;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.params.VideoParams;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.ser.ColorMode;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;

public class ProcessParamsController {
    @FXML
    private Accordion accordion;
    @FXML
    private TextField aperture;
    @FXML
    private CheckBox assumeMonoVideo;
    @FXML
    private CheckBox autoSave;
    @FXML
    private Slider bandingCorrectionPasses;
    @FXML
    private Slider bandingCorrectionWidth;
    @FXML
    private TextField camera;
    @FXML
    private Slider dopplerShifting;
    @FXML
    private TextField email;
    @FXML
    private TextField focalLength;
    @FXML
    private CheckBox forceTilt;
    @FXML
    private CheckBox forceXYRatio;
    @FXML
    private CheckBox generateDebugImages;
    @FXML
    private CheckBox generateFits;
    @FXML
    private CheckBox horizontalMirror;
    @FXML
    private TextField instrument;
    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private TextField observationDate;
    @FXML
    private TextField observerName;
    @FXML
    private Slider pixelShifting;
    @FXML
    private CheckBox sharpen;
    @FXML
    private Slider spectralLineDetectionThreshold;
    @FXML
    private CheckBox switchRedBlueChannels;
    @FXML
    private TextField telescope;
    @FXML
    private TextField tiltValue;
    @FXML
    private CheckBox verticalMirror;
    @FXML
    private ChoiceBox<SpectralRay> wavelength;
    @FXML
    private TextField xyRatioValue;

    private Stage stage;
    private ProcessParams processParams;
    private boolean quickMode;

    public void setup(Stage stage, ZonedDateTime dateFromSerFile) {
        this.stage = stage;
        var initial = ProcessParams.loadDefaults();
        accordion.setExpandedPane(accordion.getPanes().get(0));

        wavelength.getItems().addAll(FXCollections.observableList(Arrays.stream(SpectralRay.values()).toList()));
        wavelength.getSelectionModel().select(SpectralRay.H_ALPHA);
        wavelength.valueProperty().addListener((observable, oldValue, newValue) -> spectralLineDetectionThreshold.setValue(newValue.getDetectionThreshold()));
        spectralLineDetectionThreshold.valueProperty().set(wavelength.getValue().getDetectionThreshold());
        observerName.textProperty().setValue(initial.observationDetails().observer());
        email.textProperty().setValue(initial.observationDetails().email());
        instrument.textProperty().setValue(initial.observationDetails().instrument());
        telescope.textProperty().setValue(initial.observationDetails().telescope());
        camera.textProperty().setValue(initial.observationDetails().camera());
        generateDebugImages.setSelected(initial.debugParams().generateDebugImages());
        generateFits.setSelected(initial.debugParams().generateFits());
        autoSave.setSelected(initial.debugParams().autosave());
        focalLength.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        aperture.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        var length = initial.observationDetails().focalLength();
        if (length != null) {
            focalLength.textProperty().setValue(String.valueOf(length));
        }
        var ap = initial.observationDetails().aperture();
        if (ap != null) {
            aperture.textProperty().setValue(String.valueOf(ap));
        }
        observationDate.setTextFormatter(new TextFormatter<>(new ZonedDateTimeStringConverter()));
        observationDate.textProperty().set(dateFromSerFile.toString());
        latitude.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        longitude.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        var coordinates = initial.observationDetails().coordinates();
        if (coordinates != null) {
            latitude.setText(Double.toString(coordinates.a()));
            longitude.setText(Double.toString(coordinates.b()));
        }
        pixelShifting.valueProperty().set(initial.spectrumParams().pixelShift());
        dopplerShifting.valueProperty().set(initial.spectrumParams().dopplerShift());
        switchRedBlueChannels.setSelected(initial.spectrumParams().switchRedBlueChannels());
        assumeMonoVideo.setSelected(initial.videoParams().colorMode() == ColorMode.MONO);
        forceTilt.setSelected(false);
        tiltValue.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        tiltValue.setText(Double.toString(initial.geometryParams().tilt().orElse(0d)));
        tiltValue.setDisable(true);
        forceTilt.selectedProperty().addListener((observable, oldValue, newValue) -> tiltValue.setDisable(!newValue));
        forceXYRatio.setSelected(false);
        xyRatioValue.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        xyRatioValue.setText(Double.toString(initial.geometryParams().xyRatio().orElse(1.0d)));
        xyRatioValue.setDisable(true);
        forceXYRatio.selectedProperty().addListener((observable, oldValue, newValue) -> xyRatioValue.setDisable(!newValue));
        bandingCorrectionWidth.setValue(initial.bandingCorrectionParams().width());
        bandingCorrectionPasses.setValue(initial.bandingCorrectionParams().passes());
        horizontalMirror.setSelected(initial.geometryParams().isHorizontalMirror());
        verticalMirror.setSelected(initial.geometryParams().isVerticalMirror());
        sharpen.setSelected(initial.geometryParams().isSharpen());
    }

    @FXML
    public void cancel() {
        stage.close();
    }

    @FXML
    public void process() {
        doProcess(false);
    }

    private void doProcess(boolean quick) {
        this.quickMode = quick;
        var focalLength = this.focalLength.getText();
        var aperture = this.aperture.getText();
        var latitude = this.latitude.getText();
        var longitude = this.longitude.getText();
        var geo = toDoublePair(latitude, longitude);
        processParams = new ProcessParams(
                new SpectrumParams(wavelength.getValue(), spectralLineDetectionThreshold.getValue(), (int) Math.round(pixelShifting.getValue()), (int) Math.round(dopplerShifting.getValue()), switchRedBlueChannels.isSelected()),
                new ObservationDetails(
                        observerName.getText(),
                        email.getText(),
                        instrument.getText(),
                        telescope.getText(),
                        focalLength.isEmpty() ? null : Integer.parseInt(focalLength),
                        aperture.isEmpty() ? null : Integer.parseInt(aperture),
                        geo,
                        ZonedDateTime.parse(observationDate.getText()),
                        camera.getText()),
                new DebugParams(generateDebugImages.isSelected(), autoSave.isSelected(), generateFits.isSelected()),
                new VideoParams(assumeMonoVideo.isSelected() ? ColorMode.MONO : null),
                new GeometryParams(
                        forceTilt.isSelected() ? Double.parseDouble(tiltValue.getText()) : null,
                        forceXYRatio.isSelected() ? Double.parseDouble(xyRatioValue.getText()) : null,
                        horizontalMirror.isSelected(),
                        verticalMirror.isSelected(),
                        sharpen.isSelected()),
                new BandingCorrectionParams(
                        (int) Math.round(bandingCorrectionWidth.getValue()),
                        (int) Math.round(bandingCorrectionPasses.getValue())
                )
        );
        ProcessParams.saveDefaults(processParams);
        stage.close();
    }

    private DoublePair toDoublePair(String latitude, String longitude) {
        if (latitude != null && !latitude.isEmpty() && longitude!=null && !longitude.isEmpty()) {
            return new DoublePair(Double.parseDouble(latitude), Double.parseDouble(longitude));
        }
        return null;
    }

    @FXML
    public void quickProcess() {
        doProcess(true);
    }

    public boolean isQuickMode() {
        return quickMode;
    }

    public Optional<ProcessParams> getProcessParams() {
        return Optional.ofNullable(processParams);
    }

    @FXML
    public void resetRayParams() {
        spectralLineDetectionThreshold.setValue(wavelength.getValue().getDetectionThreshold());
        pixelShifting.setValue(0);
        dopplerShifting.setValue(3);
        verticalMirror.setSelected(false);
        horizontalMirror.setSelected(false);
    }

    @FXML
    public void resetMiscParams() {
        assumeMonoVideo.setSelected(true);
        generateDebugImages.setSelected(false);
        autoSave.setSelected(true);
        generateFits.setSelected(true);
        bandingCorrectionWidth.setValue(24);
        bandingCorrectionPasses.setValue(3);
    }
}
