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

import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.ExtraParams;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.GeometryParams;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.params.VideoParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.Header;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProcessParamsController {
    private boolean batchMode;
    private HostServices hostServices;
    @FXML
    private Accordion accordion;
    @FXML
    private TextField aperture;
    @FXML
    private CheckBox assumeMonoVideo;
    @FXML
    private CheckBox autocorrectAngleP;
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
    private CheckBox generatePng;
    @FXML
    private CheckBox generateJpg;
    @FXML
    private CheckBox generateTif;
    @FXML
    private CheckBox horizontalMirror;
    @FXML
    private TextField instrument;
    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private ChoiceBox<NamedPattern> namingPattern;
    @FXML
    private TextField observationDate;
    @FXML
    private TextField observerName;
    @FXML
    private TextField pixelShifting;
    @FXML
    private CheckBox sharpen;
    @FXML
    private CheckBox disallowDownsampling;

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
    private Header serFileHeader;
    private ProcessParams initialProcessParams;
    private ProcessParams processParams;
    private ForkJoinContext forkJoinContext;

    public void setup(Stage stage, ForkJoinContext forkJoinContext, Header serFileHeader, boolean batchMode, HostServices hostServices) {
        this.stage = stage;
        this.serFileHeader = serFileHeader;
        this.hostServices = hostServices;
        this.forkJoinContext = forkJoinContext;
        this.initialProcessParams = ProcessParams.loadDefaults();
        this.batchMode = batchMode;
        accordion.setExpandedPane(accordion.getPanes().get(0));

        wavelength.getItems().addAll(FXCollections.observableList(SpectralRayIO.loadDefaults()));
        var ray = initialProcessParams.spectrumParams().ray();
        if (wavelength.getItems().contains(ray)) {
            wavelength.getSelectionModel().select(ray);
        } else {
            wavelength.getSelectionModel().select(SpectralRay.H_ALPHA);
        }
        observerName.textProperty().setValue(initialProcessParams.observationDetails().observer());
        email.textProperty().setValue(initialProcessParams.observationDetails().email());
        instrument.textProperty().setValue(initialProcessParams.observationDetails().instrument());
        telescope.textProperty().setValue(initialProcessParams.observationDetails().telescope());
        camera.textProperty().setValue(initialProcessParams.observationDetails().camera());
        generateDebugImages.setSelected(initialProcessParams.extraParams().generateDebugImages());
        generateFits.setSelected(initialProcessParams.extraParams().imageFormats().contains(ImageFormat.FITS));
        generateJpg.setSelected(initialProcessParams.extraParams().imageFormats().contains(ImageFormat.JPG));
        generatePng.setSelected(initialProcessParams.extraParams().imageFormats().contains(ImageFormat.PNG));
        generateTif.setSelected(initialProcessParams.extraParams().imageFormats().contains(ImageFormat.TIF));
        autoSave.setSelected(initialProcessParams.extraParams().autosave());
        focalLength.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        aperture.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        var length = initialProcessParams.observationDetails().focalLength();
        if (length != null) {
            focalLength.textProperty().setValue(String.valueOf(length));
        }
        var ap = initialProcessParams.observationDetails().aperture();
        if (ap != null) {
            aperture.textProperty().setValue(String.valueOf(ap));
        }
        observationDate.setTextFormatter(new TextFormatter<>(new ZonedDateTimeStringConverter()));
        observationDate.textProperty().set(serFileHeader.metadata().utcDateTime().toString());
        latitude.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        longitude.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        var coordinates = initialProcessParams.observationDetails().coordinates();
        if (coordinates != null) {
            latitude.setText(Double.toString(coordinates.a()));
            longitude.setText(Double.toString(coordinates.b()));
        }
        pixelShifting.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        pixelShifting.textProperty().set(String.valueOf(initialProcessParams.spectrumParams().pixelShift()));
        dopplerShifting.valueProperty().set(initialProcessParams.spectrumParams().dopplerShift());
        switchRedBlueChannels.setSelected(initialProcessParams.spectrumParams().switchRedBlueChannels());
        assumeMonoVideo.setSelected(initialProcessParams.videoParams().colorMode() == ColorMode.MONO);
        forceTilt.setSelected(false);
        tiltValue.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        tiltValue.setText(Double.toString(initialProcessParams.geometryParams().tilt().orElse(0d)));
        tiltValue.setDisable(true);
        forceTilt.selectedProperty().addListener((observable, oldValue, newValue) -> tiltValue.setDisable(!newValue));
        forceXYRatio.setSelected(false);
        xyRatioValue.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        xyRatioValue.setText(Double.toString(initialProcessParams.geometryParams().xyRatio().orElse(1.0d)));
        xyRatioValue.setDisable(true);
        forceXYRatio.selectedProperty().addListener((observable, oldValue, newValue) -> xyRatioValue.setDisable(!newValue));
        bandingCorrectionWidth.setValue(initialProcessParams.bandingCorrectionParams().width());
        bandingCorrectionPasses.setValue(initialProcessParams.bandingCorrectionParams().passes());
        horizontalMirror.setSelected(initialProcessParams.geometryParams().isHorizontalMirror());
        verticalMirror.setSelected(initialProcessParams.geometryParams().isVerticalMirror());
        sharpen.setSelected(initialProcessParams.geometryParams().isSharpen());
        var patterns = FXCollections.observableList(FileNamingPatternsIO.loadDefaults());
        namingPattern.getItems().addAll(patterns);
        autocorrectAngleP.setSelected(initialProcessParams.geometryParams().isAutocorrectAngleP());
        if (!patterns.isEmpty()) {
            namingPattern.getSelectionModel().selectFirst();
            var pattern = initialProcessParams.extraParams().fileNamePattern();
            if (pattern != null) {
                patterns.stream()
                        .filter(p -> p.pattern().equals(pattern))
                        .findFirst()
                        .ifPresent(e -> namingPattern.getSelectionModel().select(e));
            }

        }
        if (batchMode) {
            autoSave.setSelected(true);
            autoSave.setDisable(true);
            observationDate.setDisable(true);
        }
    }

    @FXML
    public void cancel() {
        stage.close();
    }

    @FXML
    public void process() {
        double dopplerShift = dopplerShifting.getValue();
        doProcess(new RequestedImages(
                generateDebugImages.isSelected() ? RequestedImages.FULL_MODE_WITH_DEBUG : RequestedImages.FULL_MODE,
                List.of(getPixelShiftAsDouble(), dopplerShift, -dopplerShift),
                Set.of(-dopplerShift, dopplerShift),
                ImageMathParams.NONE
        ));
    }

    @FXML
    public void customProcess() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "image-selection");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (ImageSelector) fxmlLoader.getController();
            controller.setup(
                    stage,
                    forkJoinContext, 
                    initialProcessParams.requestedImages().images(),
                    generateDebugImages.isSelected(),
                    List.of(getPixelShiftAsDouble()),
                    (double) dopplerShifting.getValue(),
                    initialProcessParams.requestedImages().mathImages(),
                    hostServices,
                    batchMode
            );
            Scene scene = new Scene(node);
            var currentScene = stage.getScene();
            var onCloseRequest = stage.getOnCloseRequest();
            var title = stage.getTitle();
            stage.setTitle(I18N.string(JSolEx.class, "image-selection", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                    e.consume();
                }
                controller.getRequestedImages().ifPresent(requested -> {
                    generateDebugImages.setSelected(controller.hasDebug());
                    var shifts = requested.pixelShifts();
                    if (!shifts.contains(getPixelShiftAsDouble())) {
                        pixelShifting.setText(String.valueOf(shifts.get(0)));
                    }
                    doProcess(requested);
                    stage.close();
                });
                stage.setOnCloseRequest(onCloseRequest);
                stage.setTitle(title);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void doProcess(RequestedImages requestedImages) {
        var focalLength = this.focalLength.getText();
        var aperture = this.aperture.getText();
        var latitude = this.latitude.getText();
        var longitude = this.longitude.getText();
        var geo = toDoublePair(latitude, longitude);
        var debugImagesRequested = requestedImages.isEnabled(GeneratedImageKind.DEBUG);
        var imageFormats = EnumSet.noneOf(ImageFormat.class);
        if (generatePng.isSelected()) {
            imageFormats.add(ImageFormat.PNG);
        }
        if (generateJpg.isSelected()) {
            imageFormats.add(ImageFormat.JPG);
        }
        if (generateTif.isSelected()) {
            imageFormats.add(ImageFormat.TIF);
        }
        if (generateFits.isSelected()) {
            imageFormats.add(ImageFormat.FITS);
        }
        if (imageFormats.isEmpty()) {
            // minimally add PNG
            imageFormats.add(ImageFormat.PNG);
        }
        var namingStrategy = namingPattern.getSelectionModel().getSelectedItem();
        processParams = new ProcessParams(
                new SpectrumParams(wavelength.getValue(), getPixelShiftAsDouble(), (int) Math.round(dopplerShifting.getValue()), switchRedBlueChannels.isSelected()),
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
                new ExtraParams(generateDebugImages.isSelected() || debugImagesRequested, autoSave.isSelected(), imageFormats, namingStrategy.pattern(), namingStrategy.datetimeFormat(), namingStrategy.dateFormat()),
                new VideoParams(assumeMonoVideo.isSelected() ? ColorMode.MONO : null),
                new GeometryParams(
                        forceTilt.isSelected() ? Double.parseDouble(tiltValue.getText()) : null,
                        forceXYRatio.isSelected() ? Double.parseDouble(xyRatioValue.getText()) : null,
                        horizontalMirror.isSelected(),
                        verticalMirror.isSelected(),
                        sharpen.isSelected(),
                        disallowDownsampling.isSelected(),
                        autocorrectAngleP.isSelected()),
                new BandingCorrectionParams(
                        (int) Math.round(bandingCorrectionWidth.getValue()),
                        (int) Math.round(bandingCorrectionPasses.getValue())
                ),
                requestedImages
        );
        ProcessParams.saveDefaults(processParams);
        stage.close();
    }

    private Double getPixelShiftAsDouble() {
        var text = pixelShifting.getText();
        if (text.isEmpty()) {
            return 0d;
        }
        return Double.parseDouble(text);
    }

    private DoublePair toDoublePair(String latitude, String longitude) {
        if (latitude != null && !latitude.isEmpty() && longitude != null && !longitude.isEmpty()) {
            return new DoublePair(Double.parseDouble(latitude), Double.parseDouble(longitude));
        }
        return null;
    }

    @FXML
    public void quickProcess() {
        doProcess(new RequestedImages(
                generateDebugImages.isSelected() ? RequestedImages.QUICK_MODE_WITH_DEBUG : RequestedImages.QUICK_MODE,
                List.of(getPixelShiftAsDouble()),
                Set.of(),
                ImageMathParams.NONE
        ));
    }

    public Optional<ProcessParams> getProcessParams() {
        return Optional.ofNullable(processParams);
    }

    @FXML
    public void resetRayParams() {
        pixelShifting.setText("0");
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

    @FXML
    public void openWavelengthEditor() {
        SpectralRayEditor.openEditor(stage, controller -> controller.getSelectedItem().ifPresent(ray -> {
            wavelength.getItems().clear();
            wavelength.getItems().addAll(SpectralRayIO.loadDefaults());
            wavelength.getSelectionModel().select(ray);
        }));
    }

    @FXML
    public void openNamingPatternEditor() {
        NamingPatternEditor.openEditor(stage, serFileHeader, controller -> controller.getSelectedPattern().ifPresent(pattern -> {
            namingPattern.getItems().clear();
            namingPattern.getItems().addAll(FileNamingPatternsIO.loadDefaults());
            namingPattern.getSelectionModel().select(pattern);
        }));
    }
}
