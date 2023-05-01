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
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.processing.params.DebugParams;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.params.VideoParams;
import me.champeau.a4j.ser.ColorMode;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;

public class ProcessParamsController {
    @FXML
    private Slider pixelShifting;
    @FXML
    private ChoiceBox<SpectralRay> wavelength;
    @FXML
    private TextField observerName;
    @FXML
    private TextField instrument;
    @FXML
    private TextField telescope;
    @FXML
    private TextField observationDate;
    @FXML
    private TextField focalLength;
    @FXML
    private TextField camera;
    @FXML
    private Slider spectralLineDetectionThreshold;
    @FXML
    private CheckBox generateDebugImages;
    @FXML
    private Accordion accordion;
    @FXML
    private CheckBox assumeMonoVideo;

    private Stage stage;
    private ProcessParams processParams;

    public void setup(Stage stage, ZonedDateTime dateFromSerFile) {
        this.stage = stage;
        var initial = ProcessParams.loadDefaults();
        accordion.setExpandedPane(accordion.getPanes().get(0));

        wavelength.getItems().addAll(FXCollections.observableList(Arrays.stream(SpectralRay.values()).toList()));
        wavelength.getSelectionModel().select(SpectralRay.H_ALPHA);
        wavelength.valueProperty().addListener((observable, oldValue, newValue) -> spectralLineDetectionThreshold.setValue(newValue.getDetectionThreshold()));
        spectralLineDetectionThreshold.valueProperty().set(wavelength.getValue().getDetectionThreshold());
        observerName.textProperty().setValue(initial.observationDetails().observer());
        instrument.textProperty().setValue(initial.observationDetails().instrument());
        telescope.textProperty().setValue(initial.observationDetails().telescope());
        camera.textProperty().setValue(initial.observationDetails().camera());
        generateDebugImages.setSelected(initial.debugParams().generateDebugImages());
        var length = initial.observationDetails().focalLength();
        if (length != null) {
            focalLength.textProperty().setValue(String.valueOf(length));
        }
        observationDate.textProperty().set(dateFromSerFile.toString());
        pixelShifting.valueProperty().set(initial.spectrumParams().pixelShift());
        assumeMonoVideo.setSelected(initial.videoParams().colorMode() == ColorMode.MONO);
    }

    @FXML
    public void cancel() {
        stage.close();
    }

    @FXML
    public void process() {
        var focalLength = this.focalLength.getText();
        processParams = new ProcessParams(
                new SpectrumParams(wavelength.getValue(), spectralLineDetectionThreshold.getValue(), (int) Math.round(pixelShifting.getValue())),
                new ObservationDetails(
                        observerName.getText(),
                        instrument.getText(),
                        telescope.getText(),
                        focalLength.isEmpty() ? null : Integer.parseInt(focalLength),
                        ZonedDateTime.parse(observationDate.getText()),
                        camera.getText()
                ),
                new DebugParams(generateDebugImages.isSelected()),
                new VideoParams(assumeMonoVideo.isSelected() ? ColorMode.MONO : null)
        );
        ProcessParams.saveDefaults(processParams);
        stage.close();
    }

    public Optional<ProcessParams> getProcessParams() {
        return Optional.ofNullable(processParams);
    }

    @FXML
    public void resetRayParams() {
        spectralLineDetectionThreshold.setValue(wavelength.getValue().getDetectionThreshold());
        pixelShifting.setValue(0);
    }
}
