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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.AutoStretchParams;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement;
import me.champeau.a4j.jsolex.processing.params.DeconvolutionMode;
import me.champeau.a4j.jsolex.processing.params.EnhancementParams;
import me.champeau.a4j.jsolex.processing.params.ExtraParams;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.GeometryParams;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.RichardsonLucyDeconvolutionParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.params.VideoParams;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.math.image.Deconvolution;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.Header;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.jfx.SetupEditor.nullable;
import static me.champeau.a4j.jsolex.processing.sun.BandingReduction.DEFAULT_BAND_SIZE;
import static me.champeau.a4j.jsolex.processing.sun.BandingReduction.DEFAULT_PASS_COUNT;

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
    private GridPane autostretchParamsPane;
    @FXML
    private Slider bandingCorrectionPasses;
    @FXML
    private Slider bandingCorrectionWidth;
    @FXML
    private TextField camera;
    @FXML
    private GridPane claheParamsPane;
    @FXML
    private ChoiceBox<Integer> claheTileSize;
    @FXML
    private ChoiceBox<Integer> claheBins;
    @FXML
    private TextField claheClipping;
    @FXML
    private ChoiceBox<ContrastEnhancement> contrastEnhancementTechnique;
    @FXML
    private TextField autostretchGamma;
    @FXML
    private TextField dopplerShifting;
    @FXML
    private TextField continuumShifting;
    @FXML
    private TextField email;
    @FXML
    private GridPane enhancementsPane;
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
    private ChoiceBox<SpectroHeliograph> instrument;
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
    @FXML
    private ChoiceBox<RotationKind> rotation;
    @FXML
    private ChoiceBox<AutocropMode> autocrop;
    @FXML
    private ChoiceBox<Integer> binning;
    @FXML
    private TextField pixelSize;
    @FXML
    private ChoiceBox<DeconvolutionMode> deconvolutionMode;
    @FXML
    private Pane rlParams;
    @FXML
    private TextField rlRadius;
    @FXML
    private TextField rlSigma;
    @FXML
    private TextField rlIterations;
    @FXML
    private CheckBox forcePolynomial;
    @FXML
    private TextField forcedPolynomial;
    @FXML
    private Button forcePolynomialOpen;
    @FXML
    private CheckBox artificialFlatCorrection;
    @FXML
    private Label flatLoPercentileLabel;
    @FXML
    private TextField flatLoPercentile;
    @FXML
    private Label flatHiPercentileLabel;
    @FXML
    private TextField flatHiPercentile;
    @FXML
    private Label flatOrderLabel;
    @FXML
    private TextField flatOrder;
    @FXML
    private CheckBox spectrumVFlip;
    @FXML
    private CheckBox altAzMode;
    @FXML
    private CheckBox autoTrimSerFile;

    private final List<Stage> popups = new CopyOnWriteArrayList<>();
    private Stage stage;
    private File serFile;
    private Header serFileHeader;
    private ProcessParams initialProcessParams;
    private ProcessParams processParams;
    private boolean forceCamera;
    private boolean showCoordinatesInDetails;
    private boolean autoTrimSerFileSelected;

    public void setup(Stage stage, File serFile, Header serFileHeader, CaptureSoftwareMetadataHelper.CaptureMetadata md, boolean batchMode, HostServices hostServices) {
        this.stage = stage;
        this.serFile = serFile;
        this.serFileHeader = serFileHeader;
        this.hostServices = hostServices;
        this.initialProcessParams = ProcessParams.loadDefaults();
        this.batchMode = batchMode;
        accordion.setExpandedPane(accordion.getPanes().get(0));

        wavelength.getItems().addAll(FXCollections.observableList(SpectralRayIO.loadDefaults()));
        binning.getItems().addAll(1, 2, 3, 4);
        var ray = initialProcessParams.spectrumParams().ray();
        if (wavelength.getItems().contains(ray)) {
            wavelength.getSelectionModel().select(ray);
        } else {
            wavelength.getSelectionModel().select(SpectralRay.H_ALPHA);
        }
        observerName.textProperty().setValue(initialProcessParams.observationDetails().observer());
        email.textProperty().setValue(initialProcessParams.observationDetails().email());
        var instruments = SpectroHeliographsIO.loadDefaults();
        instrument.getItems().addAll(instruments);
        instruments.stream().filter(i -> initialProcessParams.observationDetails().instrument().label().equals(i.label())).findFirst()
            .ifPresentOrElse(instrument.getSelectionModel()::select, () -> instrument.getSelectionModel().selectFirst());
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
        continuumShifting.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        dopplerShifting.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        pixelShifting.textProperty().set(String.valueOf(initialProcessParams.spectrumParams().pixelShift()));
        dopplerShifting.textProperty().set(String.valueOf(initialProcessParams.spectrumParams().dopplerShift()));
        var continuumShift = initialProcessParams.spectrumParams().continuumShift();
        continuumShifting.textProperty().set(String.valueOf(continuumShift == 0 ? Constants.DEFAULT_CONTINUUM_SHIFT : continuumShift));
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
        disallowDownsampling.setOnAction(e -> {
            if (disallowDownsampling.isSelected()) {
                var alert = AlertFactory.confirmation(message("disallow.downsampling.header"));
                alert.setTitle(message("disallow.downsampling.confirm.title"));
                alert.setContentText(message("disallow.downsampling.content"));
                alert.showAndWait().ifPresent(buttonType -> {
                    if (buttonType == ButtonType.CANCEL) {
                        disallowDownsampling.setSelected(false);
                    }
                });
            }
        });
        rotation.getItems().addAll(RotationKind.NONE, RotationKind.LEFT, RotationKind.RIGHT);
        rotation.getSelectionModel().select(initialProcessParams.geometryParams().rotation());
        rotation.setConverter(new StringConverter<>() {
            @Override
            public String toString(RotationKind dir) {
                return message("rotation." + dir);
            }

            @Override
            public RotationKind fromString(String string) {
                return RotationKind.valueOf(string);
            }
        });
        autocrop.getItems().addAll(AutocropMode.values());
        autocrop.getSelectionModel().select(initialProcessParams.geometryParams().autocropMode());
        autocrop.setConverter(new StringConverter<>() {
            @Override
            public String toString(AutocropMode mode) {
                return message("autocrop." + mode);
            }

            @Override
            public AutocropMode fromString(String string) {
                return AutocropMode.valueOf(string);
            }
        });
        claheTileSize.getItems().addAll(
            8, 16, 32, 64, 128, 256, 512, 1024
        );
        claheTileSize.valueProperty().addListener((obs, oldValue, newValue) -> {
            var bins = claheBins.getValue();
            if (bins != null && bins > newValue * newValue) {
                claheBins.setValue(newValue * newValue);
            }
        });
        claheTileSize.getSelectionModel().select(Integer.valueOf(initialProcessParams.claheParams().tileSize()));
        claheBins.getItems().addAll(
            32, 64, 128, 256, 512, 1024
        );
        claheBins.valueProperty().addListener((obs, oldValue, newValue) -> {
            var tileSize = claheTileSize.getValue();
            if (newValue > tileSize * tileSize) {
                claheBins.setValue(oldValue);
            }
        });
        claheBins.getSelectionModel().select(Integer.valueOf(initialProcessParams.claheParams().bins()));
        claheClipping.setTextFormatter(new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String value) {
                var v = super.fromString(value);
                if (v != null && v < 0) {
                    return 0d;
                }
                return v;
            }
        }));
        claheClipping.setText("" + initialProcessParams.claheParams().clipping());
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
        contrastEnhancementTechnique.getItems().addAll(ContrastEnhancement.values());
        contrastEnhancementTechnique.getSelectionModel().select(initialProcessParams.contrastEnhancement());
        contrastEnhancementTechnique.setConverter(new StringConverter<>() {
            @Override
            public String toString(ContrastEnhancement technique) {
                return message("contrast.enhancement." + technique.name());
            }

            @Override
            public ContrastEnhancement fromString(String string) {
                return ContrastEnhancement.valueOf(string);
            }
        });
        autostretchGamma.setTextFormatter(new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String value) {
                var v = super.fromString(value);
                if (v != null && v < 1.1) {
                    return 1.1d;
                }
                return v;
            }

        }));
        autostretchGamma.setText(String.valueOf(initialProcessParams.autoStretchParams().gamma()));
        claheParamsPane.disableProperty().bind(contrastEnhancementTechnique.getSelectionModel().selectedItemProperty().isNotEqualTo(ContrastEnhancement.CLAHE));
        autostretchParamsPane.disableProperty().bind(contrastEnhancementTechnique.getSelectionModel().selectedItemProperty().isNotEqualTo(ContrastEnhancement.AUTOSTRETCH));
        if (batchMode) {
            autoSave.setSelected(true);
            autoSave.setDisable(true);
            observationDate.setDisable(true);
        }
        forceCamera = initialProcessParams.observationDetails().forceCamera();
        showCoordinatesInDetails = initialProcessParams.observationDetails().showCoordinatesInDetails();
        var bin = initialProcessParams.observationDetails().binning();
        binning.setValue(Objects.requireNonNullElse(bin, 1));
        var pSize = initialProcessParams.observationDetails().pixelSize();
        if (pSize != null) {
            pixelSize.setText(String.valueOf(pSize));
        }
        deconvolutionMode.getItems().addAll(DeconvolutionMode.values());
        deconvolutionMode.setConverter(new StringConverter<>() {
            @Override
            public String toString(DeconvolutionMode mode) {
                return message("deconvolution." + mode);
            }

            @Override
            public DeconvolutionMode fromString(String string) {
                return DeconvolutionMode.valueOf(string);
            }
        });
        deconvolutionMode.getSelectionModel().select(initialProcessParams.geometryParams().deconvolutionMode());
        rlRadius.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        rlSigma.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        rlIterations.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
        rlParams.disableProperty().bind(deconvolutionMode.getSelectionModel().selectedItemProperty().isNotEqualTo(DeconvolutionMode.RICHARDSON_LUCY));
        var richardsonLucyDeconvolutionParams = initialProcessParams.geometryParams().richardsonLucyDeconvolutionParams();
        if (richardsonLucyDeconvolutionParams.isPresent()) {
            rlRadius.setText(String.valueOf(richardsonLucyDeconvolutionParams.get().radius()));
            rlSigma.setText(String.valueOf(richardsonLucyDeconvolutionParams.get().sigma()));
            rlIterations.setText(String.valueOf(richardsonLucyDeconvolutionParams.get().iterations()));
        } else {
            configureRichardsonLucyDefaults();
        }
        sharpen.setSelected(initialProcessParams.geometryParams().isSharpen());
        if (md != null) {
            camera.setText(md.camera());
            binning.setValue(md.binning());
        }
        instrument.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpectroHeliograph instrument) {
                return instrument == null ? null : instrument.label();
            }

            @Override
            public SpectroHeliograph fromString(String string) {
                return instruments.stream().filter(i -> i.label().equals(string)).findFirst().orElse(SpectroHeliograph.SOLEX);
            }
        });
        instrument.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                spectrumVFlip.setSelected(newValue.spectrumVFlip());
            }
        });
        forcedPolynomial.disableProperty().bind(forcePolynomial.selectedProperty().not());
        forcePolynomial.setSelected(initialProcessParams.geometryParams().isForcePolynomial());
        forcePolynomialOpen.disableProperty().bind(forcePolynomial.selectedProperty().not());
        forcedPolynomial.setText(initialProcessParams.geometryParams().forcedPolynomial().orElse(null));
        artificialFlatCorrection.setSelected(initialProcessParams.enhancementParams().artificialFlatCorrection());
        flatLoPercentileLabel.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatLoPercentile.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatHiPercentileLabel.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatHiPercentile.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatOrderLabel.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatOrder.disableProperty().bind(artificialFlatCorrection.selectedProperty().not());
        flatLoPercentile.setTextFormatter(createPercentileFormatter());
        flatHiPercentile.setTextFormatter(createPercentileFormatter());
        flatOrder.setTextFormatter(createOrderFormatter());
        flatLoPercentile.setText(String.valueOf(initialProcessParams.enhancementParams().artificialFlatCorrectionLoPercentile()));
        flatHiPercentile.setText(String.valueOf(initialProcessParams.enhancementParams().artificialFlatCorrectionHiPercentile()));
        flatOrder.setText(String.valueOf(initialProcessParams.enhancementParams().artificialFlatCorrectionOrder()));
        spectrumVFlip.setSelected(initialProcessParams.geometryParams().isSpectrumVFlip());
        altAzMode.setSelected(initialProcessParams.observationDetails().altAzMode());
        autoTrimSerFile.setVisible(batchMode);
    }

    private static TextFormatter<Integer> createOrderFormatter() {
        return new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String value) {
                var v = super.fromString(value);
                if (v != null) {
                    if (v < 1) {
                        return 1;
                    }
                    if (v > 16) {
                        return 16;
                    }
                }
                return v;
            }
        });
    }

    private static TextFormatter<Double> createPercentileFormatter() {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String value) {
                var v = super.fromString(value);
                if (v != null) {
                    if (v < 0) {
                        return 0d;
                    }
                    if (v > 1) {
                        return 1d;
                    }
                }
                return v;
            }
        });
    }

    private void configureRichardsonLucyDefaults() {
        rlRadius.setText(String.valueOf(Deconvolution.DEFAULT_RADIUS));
        rlSigma.setText(String.valueOf(Deconvolution.DEFAULT_SIGMA));
        rlIterations.setText(String.valueOf(Deconvolution.DEFAULT_ITERATIONS));
    }

    @FXML
    public void openInstrumentEditor() {
        SpectroHeliographEditor.openEditor(stage, editor ->
            Platform.runLater(() ->
                editor.getSelected().ifPresent(s -> {
                    instrument.getItems().setAll(editor.getItems());
                    instrument.getSelectionModel().select(s);
                })
            )
        );
    }

    @FXML
    public void openVideoDebugger() {
        var debugger = SpectralLineDebugger.open(serFile, polynomial -> forcedPolynomial.setText(polynomial));
        popups.add(debugger);
        debugger.setOnCloseRequest(e -> popups.remove(debugger));
    }

    @FXML
    public void openSetupEditor() {
        SetupEditor.openEditor(stage, editor ->
            Platform.runLater(() ->
                editor.getSelected().ifPresent(s -> {
                    telescope.setText(nullable(s.telescope(), String::valueOf));
                    focalLength.setText(nullable(s.focalLength(), String::valueOf));
                    aperture.setText(nullable(s.aperture(), String::valueOf));
                    latitude.setText(nullable(s.latitude(), String::valueOf));
                    longitude.setText(nullable(s.longitude(), String::valueOf));
                    camera.setText(nullable(s.camera(), String::valueOf));
                    pixelSize.setText(nullable(s.pixelSize(), String::valueOf));
                    forceCamera = s.forceCamera();
                    showCoordinatesInDetails = s.showCoordinatesInDetails();
                    altAzMode.setSelected(s.altAzMode());
                })
            )
        );
    }

    @FXML
    public void cancel() {
        stage.close();
    }

    @FXML
    public void process() {
        double dopplerShift = Double.parseDouble(dopplerShifting.getText());
        double continuumShift = Double.parseDouble(continuumShifting.getText());
        doProcess(new RequestedImages(
            generateDebugImages.isSelected() ? RequestedImages.FULL_MODE_WITH_DEBUG : RequestedImages.FULL_MODE,
            List.of(getPixelShiftAsDouble(), dopplerShift, -dopplerShift, continuumShift),
            Set.of(-dopplerShift, dopplerShift),
            Set.of(),
            ImageMathParams.NONE,
            false
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
                initialProcessParams.requestedImages().images(),
                generateDebugImages.isSelected(),
                List.of(getPixelShiftAsDouble()),
                Double.parseDouble(dopplerShifting.getText()),
                Double.parseDouble(continuumShifting.getText()),
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
                        if (shifts.isEmpty()) {
                            pixelShifting.setText("0");
                        } else {
                            pixelShifting.setText(String.valueOf(shifts.get(0)));
                        }
                    }
                    doProcess(requested);
                    stage.close();
                });
                stage.setOnCloseRequest(onCloseRequest);
                stage.setTitle(title);
                closePopups();
                popups.clear();
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void closePopups() {
        popups.forEach(p -> {
            if (p.isShowing()) {
                p.close();
            }
        });
    }

    private void doProcess(RequestedImages requestedImages) {
        if (altAzMode.isSelected()) {
            // check that longitude and latitude are set and warn otherwise
            if (longitude.getText() == null || longitude.getText().isEmpty() || latitude.getText() == null || latitude.getText().isEmpty()) {
                var alert = AlertFactory.confirmation(I18N.string(JSolEx.class, "process-params", "altazmode.warning.header"));
                alert.setContentText(I18N.string(JSolEx.class, "process-params", "altazmode.warning.content"));
                var result = alert.showAndWait();
                if (ButtonType.CANCEL.equals(result.orElse(ButtonType.CANCEL))) {
                    return;
                }
            }
        }
        closePopups();
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
            new SpectrumParams(wavelength.getValue(), getPixelShiftAsDouble(), Double.parseDouble(dopplerShifting.getText()), Double.parseDouble(continuumShifting.getText()), switchRedBlueChannels.isSelected()),
            new ObservationDetails(
                observerName.getText(),
                email.getText(),
                instrument.getSelectionModel().getSelectedItem(),
                telescope.getText(),
                focalLength.isEmpty() ? null : Integer.parseInt(focalLength),
                aperture.isEmpty() ? null : Integer.parseInt(aperture),
                geo,
                ZonedDateTime.parse(observationDate.getText()),
                camera.getText(),
                binning.getValue(),
                getPixelSizeAsDouble(),
                forceCamera,
                showCoordinatesInDetails,
                altAzMode.isSelected()
            ),
            new ExtraParams(generateDebugImages.isSelected() || debugImagesRequested, autoSave.isSelected(), imageFormats, namingStrategy.pattern(), namingStrategy.datetimeFormat(), namingStrategy.dateFormat()),
            new VideoParams(assumeMonoVideo.isSelected() ? ColorMode.MONO : null),
            new GeometryParams(
                forceTilt.isSelected() ? Double.parseDouble(tiltValue.getText()) : null,
                forceXYRatio.isSelected() ? Double.parseDouble(xyRatioValue.getText()) : null,
                horizontalMirror.isSelected(),
                verticalMirror.isSelected(),
                sharpen.isSelected(),
                disallowDownsampling.isSelected(),
                autocorrectAngleP.isSelected(),
                rotation.getValue(),
                autocrop.getValue(),
                deconvolutionMode.getValue(),
                new RichardsonLucyDeconvolutionParams(
                    Double.parseDouble(rlRadius.getText()),
                    Double.parseDouble(rlSigma.getText()),
                    Integer.parseInt(rlIterations.getText())
                ),
                forcePolynomial.isSelected(),
                forcedPolynomial.getText(),
                spectrumVFlip.isSelected()
            ),
            new BandingCorrectionParams(
                (int) Math.round(bandingCorrectionWidth.getValue()),
                (int) Math.round(bandingCorrectionPasses.getValue())
            ),
            requestedImages,
            new ClaheParams(
                claheTileSize.getValue(),
                claheBins.getValue(),
                Double.parseDouble(claheClipping.getText())
            ),
            new AutoStretchParams(
                Double.parseDouble(autostretchGamma.getText())
            ),
            contrastEnhancementTechnique.getValue(),
            new EnhancementParams(artificialFlatCorrection.isSelected(), Double.parseDouble(flatLoPercentile.getText()), Double.parseDouble(flatHiPercentile.getText()), Integer.parseInt(flatOrder.getText()))
        );
        ProcessParams.saveDefaults(processParams);
        autoTrimSerFileSelected = autoTrimSerFile.isSelected();
        stage.close();
    }

    private Double getPixelSizeAsDouble() {
        return textFieldToDouble(pixelSize);
    }

    private static double textFieldToDouble(TextField pixelSize) {
        var text = pixelSize.getText();
        if (text.isEmpty()) {
            return 0d;
        }
        return Double.parseDouble(text);
    }

    private Double getPixelShiftAsDouble() {
        return textFieldToDouble(pixelShifting);
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
            Set.of(),
            ImageMathParams.NONE,
            false
        ));
    }

    public Optional<ProcessParams> getProcessParams() {
        return Optional.ofNullable(processParams);
    }

    public boolean isAutoTrimSerFileSelected() {
        return autoTrimSerFileSelected;
    }

    @FXML
    public void resetRayParams() {
        pixelShifting.setText("0");
        dopplerShifting.setText("3.0");
        continuumShifting.setText(String.valueOf(Constants.DEFAULT_CONTINUUM_SHIFT));
        verticalMirror.setSelected(false);
        horizontalMirror.setSelected(false);
        rotation.getSelectionModel().select(RotationKind.NONE);
        autocorrectAngleP.setSelected(false);
        forceTilt.setSelected(false);
        forceXYRatio.setSelected(false);
        disallowDownsampling.setSelected(false);
        forcePolynomial.setSelected(false);
        forcedPolynomial.setText(null);
    }

    @FXML
    public void resetMiscParams() {
        assumeMonoVideo.setSelected(true);
        generateDebugImages.setSelected(false);
        autoSave.setSelected(true);
        generateFits.setSelected(true);
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

    @FXML
    public void resetImageEnhancementsParams() {
        deconvolutionMode.getSelectionModel().select(DeconvolutionMode.NONE);
        sharpen.setSelected(false);
        configureArtificialFlatDefaults();
        autostretchGamma.setText(String.valueOf(AutohistogramStrategy.DEFAULT_GAMMA));
        configureRichardsonLucyDefaults();
        configureClaheDefaults();
        configureBandingCorrectionDefaults();
    }

    private void configureArtificialFlatDefaults() {
        artificialFlatCorrection.setSelected(false);
        flatLoPercentile.setText(String.valueOf(FlatCorrection.DEFAULT_LO_PERCENTILE));
        flatHiPercentile.setText(String.valueOf(FlatCorrection.DEFAULT_HI_PERCENTILE));
        flatOrder.setText(String.valueOf(FlatCorrection.DEFAULT_ORDER));
    }

    private void configureBandingCorrectionDefaults() {
        bandingCorrectionWidth.setValue(DEFAULT_BAND_SIZE);
        bandingCorrectionPasses.setValue(DEFAULT_PASS_COUNT);
    }

    private void configureClaheDefaults() {
        claheTileSize.setValue(ClaheStrategy.DEFAULT_TILE_SIZE);
        claheBins.setValue(ClaheStrategy.DEFAULT_BINS);
        claheClipping.setText("" + ClaheStrategy.DEFAULT_CLIP);
    }
}
