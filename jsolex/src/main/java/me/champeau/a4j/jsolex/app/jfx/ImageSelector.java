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
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageSelector {
    @FXML
    private CheckBox raw;
    @FXML
    private CheckBox stretched;
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
    private TextField pixelShifts;

    private Stage stage;

    private RequestedImages requestedImages;

    public void setup(Stage stage,
                      Set<GeneratedImageKind> images,
                      boolean debug,
                      List<Integer> pixelShifts,
                      int dopplerShift) {
        this.stage = stage;
        pixelShifts = new ArrayList<>(pixelShifts);
        for (GeneratedImageKind image : images) {
            switch (image) {
                case RAW -> raw.setSelected(true);
                case RAW_STRETCHED -> stretched.setSelected(true);
                case GEOMETRY_CORRECTED -> geometryCorrected.setSelected(true);
                case GEOMETRY_CORRECTED_STRETCHED -> geometryCorrectedStretched.setSelected(true);
                case COLORIZED -> colorized.setSelected(true);
                case VIRTUAL_ECLIPSE -> virtualEclipse.setSelected(true);
                case NEGATIVE -> negative.setSelected(true);
                case MIXED -> mixed.setSelected(true);
                case DOPPLER -> doppler.setSelected(true);
                case CONTINUUM -> continuum.setSelected(true);
                case RECONSTRUCTION -> reconstruction.setSelected(true);
            }
        }
        this.debug.setSelected(debug);
        if (pixelShifts.isEmpty()) {
            pixelShifts.add(0);
        }
        if (continuum.isSelected()) {
            pixelShifts.add(Constants.CONTINUUM_SHIFT);
        }
        if (doppler.isSelected()) {
            pixelShifts.add(-dopplerShift);
            pixelShifts.add(dopplerShift);
        }
        doppler.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, -dopplerShift, dopplerShift));
        continuum.selectedProperty().addListener((observable, oldValue, newValue) -> adjustPixelShifts(newValue, Constants.CONTINUUM_SHIFT));
        setPixelShiftText(pixelShifts);
    }

    private void adjustPixelShifts(boolean newValue, int... shifts) {
        var newPixelShifts = new ArrayList<>(readPixelShifts());
        for (int shift : shifts) {
            if (newValue) {
                newPixelShifts.add(shift);
            } else {
                newPixelShifts.remove((Integer) shift);
            }
            setPixelShiftText(newPixelShifts);
        }
    }

    private void setPixelShiftText(List<Integer> pixelShifts) {
        this.pixelShifts.setText(pixelShifts.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(";")));
    }

    private List<Integer> readPixelShifts() {
        return Arrays.stream(pixelShifts.getText().split("\s*;\s*"))
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
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
        }
        if (stretched.isSelected()) {
            images.add(GeneratedImageKind.RAW_STRETCHED);
        }
        if (geometryCorrected.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED);
        }
        if (geometryCorrectedStretched.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED_STRETCHED);
        }
        if (colorized.isSelected()) {
            images.add(GeneratedImageKind.COLORIZED);
        }
        if (virtualEclipse.isSelected()) {
            images.add(GeneratedImageKind.VIRTUAL_ECLIPSE);
        }
        if (negative.isSelected()) {
            images.add(GeneratedImageKind.NEGATIVE);
        }
        if (mixed.isSelected()) {
            images.add(GeneratedImageKind.MIXED);
        }
        if (doppler.isSelected()) {
            images.add(GeneratedImageKind.DOPPLER);
        }
        if (continuum.isSelected()) {
            images.add(GeneratedImageKind.CONTINUUM);
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
                pixelShifts
        );
        requestClose();
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
        stretched.setSelected(selected);
        geometryCorrected.setSelected(selected);
        geometryCorrectedStretched.setSelected(selected);
        colorized.setSelected(selected);
        virtualEclipse.setSelected(selected);
        negative.setSelected(selected);
        mixed.setSelected(selected);
        doppler.setSelected(selected);
        continuum.setSelected(selected);
        reconstruction.setSelected(selected);
        debug.setSelected(selected);
    }

}
