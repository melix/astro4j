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
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ImageSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageSelector.class);

    private boolean batchMode;
    private HostServices hostServices;
    @FXML
    private CheckBox raw;
    @FXML
    private CheckBox geometryCorrected;
    @FXML
    private CheckBox geometryCorrectedStretched;
    @FXML
    private CheckBox colorized;
    @FXML
    private CheckBox virtualEclipse;
    @FXML
    private CheckBox dopplerEclipse;
    @FXML
    private CheckBox negative;
    @FXML
    private CheckBox mixed;
    @FXML
    private CheckBox doppler;
    @FXML
    private CheckBox continuum;
    @FXML
    private CheckBox debug;
    @FXML
    private CheckBox reconstruction;
    @FXML
    private CheckBox technicalCard;
    @FXML
    private CheckBox redshift;
    @FXML
    private TextField pixelShifts;
    @FXML
    private CheckBox activeRegions;
    @FXML
    private CheckBox ellermanBombs;
    @FXML
    private Button openImageMathButton;
    @FXML
    private ChoiceBox<PixelShiftMode> mode;

    private Stage stage;

    private double dopplerShift;
    private double continuumShift;
    private RequestedImages requestedImages;
    private ImageMathParams imageMathParams;
    private Set<Double> internalPixelShifts;
    private Set<Double> requestesWaveLengths;
    private boolean autoContinuum;

    // cache
    private ImageMathParams cachedImageMathParams;
    private List<Double> cachedShifts;

    public void setup(Stage stage,
                      Set<GeneratedImageKind> images,
                      boolean debug,
                      List<Double> selectedPixelShifts,
                      double dopplerShift,
                      double continuumShift,
                      ImageMathParams imageMathParams,
                      HostServices hostServices,
                      boolean batchMode) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.dopplerShift = dopplerShift;
        this.continuumShift = continuumShift;
        this.imageMathParams = imageMathParams;
        this.batchMode = batchMode;
        this.mode.getItems().add(PixelShiftMode.SIMPLE);
        this.mode.getItems().add(PixelShiftMode.IMAGEMATH);
        this.mode.getSelectionModel().selectedItemProperty().addListener((obj, oldValue, newValue) -> {
            if (newValue != null) {
                if (newValue == PixelShiftMode.SIMPLE) {
                    openImageMathButton.setDisable(true);
                    pixelShifts.setDisable(false);
                    var newPixelShifts = new ArrayList<>(selectedPixelShifts);
                    updatePixelShiftsWithSelectedImages(newPixelShifts);
                } else {
                    openImageMathButton.setDisable(false);
                    pixelShifts.setDisable(true);
                    updatePixelShiftsWithSelectedImages(findPixelShifts(imageMathParams));
                }
            }
        });
        var newPixelShifts = new ArrayList<Double>();
        if (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
            this.mode.getSelectionModel().select(PixelShiftMode.IMAGEMATH);
            newPixelShifts.addAll(findPixelShifts(imageMathParams));
        } else {
            this.mode.getSelectionModel().select(PixelShiftMode.SIMPLE);
            newPixelShifts.addAll(selectedPixelShifts);
        }
        for (GeneratedImageKind image : images) {
            if (image == null) {
                // can happen because of backwards compatibility
                continue;
            }
            switch (image) {
                case RAW -> raw.setSelected(true);
                case GEOMETRY_CORRECTED -> geometryCorrected.setSelected(true);
                case GEOMETRY_CORRECTED_PROCESSED -> geometryCorrectedStretched.setSelected(true);
                case COLORIZED -> colorized.setSelected(true);
                case VIRTUAL_ECLIPSE -> virtualEclipse.setSelected(true);
                case DOPPLER_ECLIPSE -> dopplerEclipse.setSelected(true);
                case NEGATIVE -> negative.setSelected(true);
                case MIXED -> mixed.setSelected(true);
                case DOPPLER -> doppler.setSelected(true);
                case CONTINUUM -> continuum.setSelected(true);
                case RECONSTRUCTION -> reconstruction.setSelected(true);
                case TECHNICAL_CARD -> technicalCard.setSelected(true);
                case REDSHIFT -> redshift.setSelected(true);
                case ACTIVE_REGIONS -> activeRegions.setSelected(true);
                case ELLERMAN_BOMBS -> ellermanBombs.setSelected(true);
            }
        }
        this.debug.setSelected(debug);
        updatePixelShiftsWithSelectedImages(newPixelShifts);
        doppler.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, -dopplerShift, dopplerShift));
        continuum.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, continuumShift));
        activeRegions.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                continuum.setSelected(true);
            }
        });
    }

    private void updatePixelShiftsWithSelectedImages(List<Double> newPixelShifts) {
        var result = new TreeSet<>(newPixelShifts);
        if (result.isEmpty()) {
            result.add(0d);
        }
        if (continuum.isSelected()) {
            result.add(continuumShift);
        }
        if (doppler.isSelected() || dopplerEclipse.isSelected()) {
            result.add(-dopplerShift);
            result.add(dopplerShift);
        }
        setPixelShiftText(result.stream().toList());
    }

    private void adjustPixelShifts(boolean newValue, double... shifts) {
        var newPixelShifts = new ArrayList<>(readPixelShifts());
        for (double shift : shifts) {
            if (newValue) {
                newPixelShifts.add(shift);
            } else {
                newPixelShifts.remove(shift);
            }
            setPixelShiftText(newPixelShifts);
        }
    }

    private void setPixelShiftText(List<Double> pixelShifts) {
        this.pixelShifts.setText(pixelShifts.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(";")));
    }

    private List<Double> readPixelShifts() {
        return Arrays.stream(pixelShifts.getText().split("\s*;\s*"))
            .filter(s -> !s.isEmpty())
            .map(Double::parseDouble)
            .toList();
    }


    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public boolean hasDebug() {
        return debug.isSelected();
    }

    public Optional<RequestedImages> getRequestedImages() {
        return Optional.ofNullable(requestedImages);
    }

    @FXML
    private void cancel() {
        requestClose();
    }

    @FXML
    private void process() {
        Set<GeneratedImageKind> images = EnumSet.noneOf(GeneratedImageKind.class);
        if (raw.isSelected()) {
            images.add(GeneratedImageKind.RAW);
            makeDefaultShiftNonInternal();
        }
        if (technicalCard.isSelected()) {
            images.add(GeneratedImageKind.TECHNICAL_CARD);
            makeDefaultShiftNonInternal();
        }
        if (geometryCorrected.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED);
            makeDefaultShiftNonInternal();
        }
        if (geometryCorrectedStretched.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED);
            makeDefaultShiftNonInternal();
        }
        if (colorized.isSelected()) {
            images.add(GeneratedImageKind.COLORIZED);
            makeDefaultShiftNonInternal();
        }
        if (virtualEclipse.isSelected()) {
            images.add(GeneratedImageKind.VIRTUAL_ECLIPSE);
            makeDefaultShiftNonInternal();
        }
        if (negative.isSelected()) {
            images.add(GeneratedImageKind.NEGATIVE);
            makeDefaultShiftNonInternal();
        }
        if (mixed.isSelected()) {
            images.add(GeneratedImageKind.MIXED);
            makeDefaultShiftNonInternal();
        }
        if (doppler.isSelected()) {
            images.add(GeneratedImageKind.DOPPLER);
            if (internalPixelShifts != null) {
                internalPixelShifts.remove(-dopplerShift);
                internalPixelShifts.remove(dopplerShift);
            }
        }
        if (dopplerEclipse.isSelected()) {
            images.add(GeneratedImageKind.DOPPLER_ECLIPSE);
            if (internalPixelShifts != null) {
                internalPixelShifts.remove(-dopplerShift);
                internalPixelShifts.remove(dopplerShift);
            }
        }
        if (continuum.isSelected()) {
            images.add(GeneratedImageKind.CONTINUUM);
            if (internalPixelShifts != null) {
                internalPixelShifts.remove(continuumShift);
            }
        }
        if (reconstruction.isSelected()) {
            images.add(GeneratedImageKind.RECONSTRUCTION);
        }
        if (debug.isSelected()) {
            images.add(GeneratedImageKind.DEBUG);
        }
        if (redshift.isSelected()) {
            images.add(GeneratedImageKind.REDSHIFT);
            makeDefaultShiftNonInternal();
        }
        if (activeRegions.isSelected()) {
            images.add(GeneratedImageKind.ACTIVE_REGIONS);
            makeDefaultShiftNonInternal();
        }
        if (ellermanBombs.isSelected()) {
            images.add(GeneratedImageKind.ELLERMAN_BOMBS);
            images.add(GeneratedImageKind.FLARES);
            if (internalPixelShifts != null) {
                internalPixelShifts.add(continuumShift);
            }
            if (!continuum.isSelected()) {
                if (internalPixelShifts == null) {
                    internalPixelShifts = new TreeSet<>();
                }
                internalPixelShifts.add(continuumShift);
            }
        }
        var pixelShifts = readPixelShifts();
        requestedImages = new RequestedImages(
            images,
            pixelShifts,
            mode.getSelectionModel().getSelectedItem() == PixelShiftMode.SIMPLE || internalPixelShifts == null ? Set.of() : internalPixelShifts,
            requestesWaveLengths == null ? Set.of() : Collections.unmodifiableSet(requestesWaveLengths),
            mode.getSelectionModel().getSelectedItem() == PixelShiftMode.IMAGEMATH ? imageMathParams : ImageMathParams.NONE,
            autoContinuum,
            false
        );
        requestClose();
    }

    private void makeDefaultShiftNonInternal() {
        if (internalPixelShifts != null) {
            internalPixelShifts.remove(0d);
        }
    }

    @FXML
    private void selectAll() {
        selectAll(true);
    }

    @FXML
    private void unselectAll() {
        selectAll(false);
    }

    private void selectAll(boolean selected) {
        raw.setSelected(selected);
        geometryCorrected.setSelected(selected);
        geometryCorrectedStretched.setSelected(selected);
        colorized.setSelected(selected);
        virtualEclipse.setSelected(selected);
        negative.setSelected(selected);
        mixed.setSelected(selected);
        doppler.setSelected(selected);
        dopplerEclipse.setSelected(selected);
        continuum.setSelected(selected);
        reconstruction.setSelected(selected);
        technicalCard.setSelected(selected);
        debug.setSelected(selected);
        redshift.setSelected(selected);
        activeRegions.setSelected(selected);
        ellermanBombs.setSelected(selected);
    }

    @FXML
    public void openImageMath() {
        ImageMathEditor.create(stage,
            imageMathParams,
            hostServices,
            batchMode,
            true,
            controller -> {
            },
            controller -> controller.getConfiguration().ifPresent(params -> {
                try {
                    updatePixelShiftsWithSelectedImages(findPixelShifts(params));
                } catch (ProcessingException e) {
                    LOGGER.warn(message("warning.imagemath.script.error"), e.getMessage());
                    updatePixelShiftsWithSelectedImages(List.of(0d));
                }
                this.imageMathParams = params;
            }));
    }

    private DefaultImageScriptExecutor createScriptExecutor() {
        var images = new HashMap<PixelShift, ImageWrapper32>();
        return new JSolExScriptExecutor(
            i -> images.computeIfAbsent(i, unused -> ImageWrapper32.createEmpty()),
            MutableMap.of(),
            stage
        );
    }

    private List<Double> findPixelShifts(ImageMathParams params) {
        if (cachedImageMathParams == params) {
            return cachedShifts;
        }
        var executor = createScriptExecutor();
        var allShifts = new TreeSet<Double>();
        internalPixelShifts = new TreeSet<>();
        requestesWaveLengths = new TreeSet<>();
        autoContinuum = false;
        for (File file : params.scriptFiles()) {
            if (Files.exists(file.toPath())) {
                try {
                    var result = executor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    allShifts.addAll(result.internalShifts());
                    internalPixelShifts.addAll(result.internalShifts());
                    allShifts.addAll(result.outputShifts());
                    requestesWaveLengths.addAll(result.requestedWavelenghts());
                    autoContinuum |= result.autoContinuum();
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
            }
        }
        var list = allShifts.stream().toList();
        cachedShifts = list;
        cachedImageMathParams = imageMathParams;
        return list;
    }

    private enum PixelShiftMode {
        SIMPLE("mode.simple"),
        IMAGEMATH("mode.imagemath");

        private final String label;

        PixelShiftMode(String label) {
            this.label = label;
        }


        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "image-selection", label);
        }
    }

}
