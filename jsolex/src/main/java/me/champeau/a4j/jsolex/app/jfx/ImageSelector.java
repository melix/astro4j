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
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ImageSelector {
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
    private TextField pixelShifts;
    @FXML
    private Button openImageMathButton;
    @FXML
    private ChoiceBox<PixelShiftMode> mode;

    private Stage stage;

    private double dopplerShift;
    private RequestedImages requestedImages;
    private ImageMathParams imageMathParams;
    private Set<Double> internalPixelShifts;
    private ForkJoinContext forkJoinContext;

    public void setup(Stage stage,
                      ForkJoinContext forkJoinContext,
                      Set<GeneratedImageKind> images,
                      boolean debug,
                      List<Double> selectedPixelShifts,
                      double dopplerShift,
                      ImageMathParams imageMathParams,
                      HostServices hostServices,
                      boolean batchMode) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.dopplerShift = dopplerShift;
        this.imageMathParams = imageMathParams;
        this.forkJoinContext = forkJoinContext;
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
            if (image==null) {
                // can happen because of backwards compatibility
                continue;
            }
            switch (image) {
                case RAW -> raw.setSelected(true);
                case GEOMETRY_CORRECTED -> geometryCorrected.setSelected(true);
                case GEOMETRY_CORRECTED_PROCESSED -> geometryCorrectedStretched.setSelected(true);
                case COLORIZED -> colorized.setSelected(true);
                case VIRTUAL_ECLIPSE -> virtualEclipse.setSelected(true);
                case NEGATIVE -> negative.setSelected(true);
                case MIXED -> mixed.setSelected(true);
                case DOPPLER -> doppler.setSelected(true);
                case CONTINUUM -> continuum.setSelected(true);
                case RECONSTRUCTION -> reconstruction.setSelected(true);
                case TECHNICAL_CARD -> technicalCard.setSelected(true);
            }
        }
        this.debug.setSelected(debug);
        updatePixelShiftsWithSelectedImages(newPixelShifts);
        doppler.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, -dopplerShift, dopplerShift));
        continuum.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, Constants.CONTINUUM_SHIFT));
    }

    private void updatePixelShiftsWithSelectedImages(List<Double> newPixelShifts) {
        var result = new TreeSet<>(newPixelShifts);
        if (result.isEmpty()) {
            result.add(0d);
        }
        if (continuum.isSelected()) {
            result.add(Constants.CONTINUUM_SHIFT);
        }
        if (doppler.isSelected()) {
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
        if (continuum.isSelected()) {
            images.add(GeneratedImageKind.CONTINUUM);
            if (internalPixelShifts != null) {
                internalPixelShifts.remove(Constants.CONTINUUM_SHIFT);
            }
        }
        if (reconstruction.isSelected()) {
            images.add(GeneratedImageKind.RECONSTRUCTION);
        }
        if (debug.isSelected()) {
            images.add(GeneratedImageKind.DEBUG);
        }
        var pixelShifts = readPixelShifts();
        requestedImages = new RequestedImages(
                images,
                pixelShifts,
                mode.getSelectionModel().getSelectedItem() == PixelShiftMode.SIMPLE || internalPixelShifts == null ? Set.of() : internalPixelShifts,
                mode.getSelectionModel().getSelectedItem() == PixelShiftMode.IMAGEMATH ? imageMathParams : ImageMathParams.NONE
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
        continuum.setSelected(selected);
        reconstruction.setSelected(selected);
        technicalCard.setSelected(selected);
        debug.setSelected(selected);
    }

    @FXML
    public void openImageMath() {
        ImageMathEditor.create(stage,
            imageMathParams,
            hostServices,
            batchMode,
            true,
            controller -> {},
            controller -> controller.getConfiguration().ifPresent(params -> {
                updatePixelShiftsWithSelectedImages(findPixelShifts(params));
                this.imageMathParams = params;
            }));
    }

    private DefaultImageScriptExecutor createScriptExecutor() {
        var images = new HashMap<Double, ImageWrapper32>();
        return new JSolExScriptExecutor(
                forkJoinContext,
                i -> images.computeIfAbsent(i, unused -> ImageWrapper32.createEmpty()),
                MutableMap.of(),
                stage
        );
    }

    private List<Double> findPixelShifts(ImageMathParams params) {
        var executor = createScriptExecutor();
        var allShifts = new TreeSet<Double>();
        internalPixelShifts = new TreeSet<>();
        for (File file : params.scriptFiles()) {
            try {
                var result = executor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                allShifts.addAll(result.internalShifts());
                internalPixelShifts.addAll(result.internalShifts());
                allShifts.addAll(result.outputShifts());
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        }
        return allShifts.stream().toList();
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
