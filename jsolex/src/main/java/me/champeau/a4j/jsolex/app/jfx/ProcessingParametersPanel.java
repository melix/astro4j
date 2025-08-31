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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.GeometryParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.util.Dispersion;

public class ProcessingParametersPanel extends BaseParameterPanel {
    
    private ChoiceBox<SpectralRay> wavelengthChoice;
    private TextField pixelShiftingField;
    private TextField dopplerShiftingField;
    private TextField continuumShiftingField;
    private Label pixelShiftAngstromLabel;
    private Label dopplerShiftAngstromLabel;
    private Label continuumShiftAngstromLabel;
    private ChoiceBox<RotationKind> rotationChoice;
    private ChoiceBox<AutocropMode> autocropChoice;
    private TextField fixedWidthField;
    private Label fixedWidthLabel;
    private InlineGuidance fixedWidthWarning;
    private Integer sourceWidth;
    private CheckBox horizontalMirrorCheck;
    private CheckBox verticalMirrorCheck;
    private CheckBox autocorrectAnglePCheck;
    private CheckBox switchRedBlueChannelsCheck;
    private CheckBox reviewImagesAfterBatch;
    private ProcessParamsController controller;
    private boolean batchMode;
    
    public ProcessingParametersPanel() {
        initializeComponents();
        setupLayout();
        getStyleClass().add("parameter-panel");
    }
    
    private void initializeComponents() {
        wavelengthChoice = createChoiceBox();
        wavelengthChoice.setItems(FXCollections.observableArrayList(SpectralRayIO.loadDefaults()));
        wavelengthChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpectralRay ray) {
                return ray == null ? "" : ray.label();
            }

            @Override
            public SpectralRay fromString(String string) {
                return wavelengthChoice.getItems().stream()
                        .filter(ray -> ray.label().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        
        pixelShiftingField = createTextField("0", I18N.string(JSolEx.class, "process-params", "pixel.shifting.tooltip"));
        dopplerShiftingField = createTextField("3.0", I18N.string(JSolEx.class, "process-params", "doppler.tooltip"));
        continuumShiftingField = createTextField("0", null);
        
        pixelShiftAngstromLabel = new Label("");
        pixelShiftAngstromLabel.getStyleClass().add("field-description");
        pixelShiftAngstromLabel.setVisible(false);
        
        dopplerShiftAngstromLabel = new Label("");
        dopplerShiftAngstromLabel.getStyleClass().add("field-description");
        dopplerShiftAngstromLabel.setVisible(false);
        
        continuumShiftAngstromLabel = new Label("");
        continuumShiftAngstromLabel.getStyleClass().add("field-description");
        continuumShiftAngstromLabel.setVisible(false);
        
        pixelShiftingField.textProperty().addListener((obs, oldVal, newVal) -> updateAngstromLabels());
        dopplerShiftingField.textProperty().addListener((obs, oldVal, newVal) -> updateAngstromLabels());
        continuumShiftingField.textProperty().addListener((obs, oldVal, newVal) -> updateAngstromLabels());
        wavelengthChoice.valueProperty().addListener((obs, oldVal, newVal) -> updateAngstromLabels());
        
        rotationChoice = createChoiceBox();
        rotationChoice.setItems(FXCollections.observableArrayList(RotationKind.values()));
        rotationChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(RotationKind rotation) {
                return rotation == null ? "" : I18N.string(JSolEx.class, "messages", "rotation." + rotation.name());
            }

            @Override
            public RotationKind fromString(String string) {
                return RotationKind.valueOf(string);
            }
        });
        
        horizontalMirrorCheck = new CheckBox();
        verticalMirrorCheck = new CheckBox();
        autocorrectAnglePCheck = new CheckBox();
        switchRedBlueChannelsCheck = new CheckBox();
        
        reviewImagesAfterBatch = new CheckBox();
        
        autocropChoice = createChoiceBox();
        autocropChoice.setItems(FXCollections.observableArrayList(AutocropMode.values()));
        autocropChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(AutocropMode mode) {
                return mode == null ? "" : I18N.string(JSolEx.class, "messages", "autocrop." + mode.name());
            }

            @Override
            public AutocropMode fromString(String string) {
                return AutocropMode.valueOf(string);
            }
        });
        
        fixedWidthField = createTextField("1024", I18N.string(JSolEx.class, "process-params", "fixed.width.tooltip"));
        fixedWidthField.setVisible(false);
        
        fixedWidthWarning = InlineGuidance.warning(
            I18N.string(JSolEx.class, "process-params", "fixed.width.warning.title"),
            I18N.string(JSolEx.class, "process-params", "fixed.width.warning.message")
        );
        fixedWidthWarning.setVisible(false);
        fixedWidthWarning.setManaged(false);
        
        autocropChoice.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean showFixedWidth = newVal == AutocropMode.FIXED_WIDTH;
            fixedWidthField.setVisible(showFixedWidth);
            fixedWidthField.setManaged(showFixedWidth);
            fixedWidthLabel.setVisible(showFixedWidth);
            fixedWidthLabel.setManaged(showFixedWidth);
            updateFixedWidthWarning();
        });
        
        fixedWidthField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFixedWidthWarning();
        });
    }
    
    private void updateFixedWidthWarning() {
        if (autocropChoice.getValue() != AutocropMode.FIXED_WIDTH || sourceWidth == null) {
            fixedWidthWarning.setVisible(false);
            fixedWidthWarning.setManaged(false);
            return;
        }
        
        try {
            int fixedWidth = Integer.parseInt(fixedWidthField.getText());
            boolean showWarning = fixedWidth > sourceWidth * 1.25;
            fixedWidthWarning.setVisible(showWarning);
            fixedWidthWarning.setManaged(showWarning);
        } catch (NumberFormatException e) {
            fixedWidthWarning.setVisible(false);
            fixedWidthWarning.setManaged(false);
        }
    }
    
    public void setSourceWidth(Integer width) {
        this.sourceWidth = width;
        updateFixedWidthWarning();
    }
    
    private void setupLayout() {
        getStyleClass().add("parameter-panel");
        setSpacing(24);
        
        var spectrumSection = createSection("spectrum.configuration");
        var spectrumGrid = createGrid();
        
        addGridRow(spectrumGrid, 0, I18N.string(JSolEx.class, "process-params", "wavelength"), createWavelengthBox(), "wavelength.tooltip");
        addGridRow(spectrumGrid, 1, I18N.string(JSolEx.class, "process-params", "pixel.shifting"), createFieldWithAngstromLabel(pixelShiftingField, pixelShiftAngstromLabel), "pixel.shifting.tooltip");
        addGridRow(spectrumGrid, 2, I18N.string(JSolEx.class, "process-params", "doppler.shifting"), createFieldWithAngstromLabel(dopplerShiftingField, dopplerShiftAngstromLabel), "doppler.tooltip");
        addGridRow(spectrumGrid, 3, I18N.string(JSolEx.class, "process-params", "continuum.shift"), createFieldWithAngstromLabel(continuumShiftingField, continuumShiftAngstromLabel), "continuum.shift.desc");
        addGridRow(spectrumGrid, 4, I18N.string(JSolEx.class, "process-params", "doppler.switch.red.blue.channels") + ":", switchRedBlueChannelsCheck, "doppler.switch.red.blue.channels.tooltip");
        
        spectrumSection.getChildren().add(spectrumGrid);
        
        var geometrySection = createSection("geometry.orientation");
        var geometryGrid = createGrid();
        
        addGridRow(geometryGrid, 0, I18N.string(JSolEx.class, "process-params", "rotation"), rotationChoice, "rotation.tooltip");
        addGridRow(geometryGrid, 1, I18N.string(JSolEx.class, "process-params", "autocrop"), autocropChoice, "autocrop.tooltip");
        
        fixedWidthLabel = new Label(I18N.string(JSolEx.class, "process-params", "fixed.width"));
        fixedWidthLabel.getStyleClass().add("field-label");
        fixedWidthLabel.setVisible(false);
        fixedWidthLabel.setManaged(false);
        geometryGrid.add(fixedWidthLabel, 0, 2);
        geometryGrid.add(fixedWidthField, 1, 2);
        
        addGridRow(geometryGrid, 3, I18N.string(JSolEx.class, "process-params", "autocorrect.p.angle") + ":", autocorrectAnglePCheck, "autocorrect.p.angle.tooltip");
        addGridRow(geometryGrid, 4, I18N.string(JSolEx.class, "process-params", "horizontal.flip") + ":", horizontalMirrorCheck, "horizontal.mirror.tooltip");
        addGridRow(geometryGrid, 5, I18N.string(JSolEx.class, "process-params", "vertical.flip") + ":", verticalMirrorCheck, "vertical.mirror.tooltip");
        
        geometrySection.getChildren().addAll(geometryGrid, fixedWidthWarning);
        
        getChildren().addAll(spectrumSection, geometrySection);
        
        if (batchMode) {
            var batchSection = createSection("batch.options");
            var batchGrid = createGrid();
            addGridRow(batchGrid, 0, I18N.string(JSolEx.class, "process-params", "review.images.after.batch") + ":", reviewImagesAfterBatch, "review.images.after.batch.tooltip");
            batchSection.getChildren().add(batchGrid);
            getChildren().add(batchSection);
        }
        
        var resetButton = new Button(I18N.string(JSolEx.class, "process-params", "reset.to.defaults"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(ignored -> resetToDefaults());
        
        var buttonContainer = new HBox();
        buttonContainer.getChildren().add(resetButton);
        getChildren().add(buttonContainer);
    }
    
    private HBox createWavelengthBox() {
        var editButton = new Button("...");
        editButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "wavelength.tooltip")));
        editButton.getStyleClass().add("default-button");
        editButton.setOnAction(ignored -> openWavelengthEditor());
        
        return createChoiceBoxWithButton(wavelengthChoice, editButton);
    }



    private void openWavelengthEditor() {
        controller.openWavelengthEditor();
    }
    
    
    private void resetToDefaults() {
        pixelShiftingField.setText("0");
        dopplerShiftingField.setText("3.0");
        continuumShiftingField.setText("0");
        rotationChoice.setValue(RotationKind.NONE);
        autocropChoice.setValue(AutocropMode.RADIUS_1_2);
        fixedWidthField.setText("1024");
        horizontalMirrorCheck.setSelected(false);
        verticalMirrorCheck.setSelected(false);
        autocorrectAnglePCheck.setSelected(false);
        switchRedBlueChannelsCheck.setSelected(false);
        if (batchMode) {
            reviewImagesAfterBatch.setSelected(false);
        }
    }
    
    public void loadData(ProcessParams params, boolean batchMode) {
        if (this.batchMode != batchMode) {
            this.batchMode = batchMode;
            getChildren().clear();
            setupLayout();
        }
        
        var spectrum = params.spectrumParams();
        var geometry = params.geometryParams();
        var extraParams = params.extraParams();
        
        pixelShiftingField.setText(String.valueOf(spectrum.pixelShift()));
        dopplerShiftingField.setText(String.valueOf(spectrum.dopplerShift()));
        continuumShiftingField.setText(String.valueOf(spectrum.continuumShift()));
        switchRedBlueChannelsCheck.setSelected(spectrum.switchRedBlueChannels());
        
        rotationChoice.setValue(geometry.rotation());
        autocropChoice.setValue(geometry.autocropMode());
        fixedWidthField.setText(String.valueOf(geometry.fixedWidth().orElse(1024)));
        horizontalMirrorCheck.setSelected(geometry.isHorizontalMirror());
        verticalMirrorCheck.setSelected(geometry.isVerticalMirror());
        autocorrectAnglePCheck.setSelected(geometry.isAutocorrectAngleP());
        
        if (batchMode) {
            reviewImagesAfterBatch.setSelected(extraParams.reviewImagesAfterBatch());
        }
        
        var savedRay = spectrum.ray();
        if (savedRay != null && wavelengthChoice.getItems().contains(savedRay)) {
            wavelengthChoice.getSelectionModel().select(savedRay);
        } else if (!wavelengthChoice.getItems().isEmpty()) {
            wavelengthChoice.getSelectionModel().selectFirst();
        }
    }
    
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    public void updateWavelength(SpectralRay ray) {
        wavelengthChoice.getItems().clear();
        var candidates = SpectralRayIO.loadDefaults().stream().filter(spectralRay -> !spectralRay.emission()).toList();
        wavelengthChoice.getItems().addAll(candidates);
        if (candidates.contains(ray)) {
            wavelengthChoice.getSelectionModel().select(ray);
        } else {
            wavelengthChoice.getSelectionModel().selectFirst();
        }
    }
    
    
    public SpectrumParams getSpectrumParams() {
        var selectedRay = wavelengthChoice.getValue();
        return new SpectrumParams(
            selectedRay,
            Double.parseDouble(pixelShiftingField.getText()),
            Double.parseDouble(dopplerShiftingField.getText()),
            Double.parseDouble(continuumShiftingField.getText()),
            switchRedBlueChannelsCheck.isSelected()
        );
    }
    
    public GeometryParams getGeometryParams() {
        var defaults = ProcessParams.loadDefaults().geometryParams();
        Integer fixedWidth = null;
        if (autocropChoice.getValue() == AutocropMode.FIXED_WIDTH) {
            try {
                fixedWidth = Integer.parseInt(fixedWidthField.getText());
            } catch (NumberFormatException e) {
                fixedWidth = 1024;
            }
        }
        return defaults
            .withRotation(rotationChoice.getValue())
            .withAutocropMode(autocropChoice.getValue())
            .withFixedWidth(fixedWidth)
            .withHorizontalMirror(horizontalMirrorCheck.isSelected())
            .withVerticalMirror(verticalMirrorCheck.isSelected())
            .withAutocorrectAngleP(autocorrectAnglePCheck.isSelected());
    }
    
    public boolean isValid() {
        try {
            Double.parseDouble(pixelShiftingField.getText());
            Double.parseDouble(dopplerShiftingField.getText());
            Double.parseDouble(continuumShiftingField.getText());
            if (autocropChoice.getValue() == AutocropMode.FIXED_WIDTH) {
                int width = Integer.parseInt(fixedWidthField.getText());
                if (width <= 0) {
                    return false;
                }
            }
            return wavelengthChoice.getValue() != null;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public boolean isReviewImagesAfterBatch() {
        return batchMode && reviewImagesAfterBatch.isSelected();
    }
    
    public String getPixelShiftValue() {
        return pixelShiftingField.getText();
    }

    public String getContinuumShiftValue() {
        return continuumShiftingField.getText();
    }

    public String getDopplerShiftValue() {
        return dopplerShiftingField.getText();
    }
    
    public void setPixelShiftValue(String value) {
        pixelShiftingField.setText(value);
    }
    
    private HBox createFieldWithAngstromLabel(TextField field, Label angstromLabel) {
        var container = new HBox(8);
        var fieldContainer = new HBox();
        fieldContainer.getChildren().add(field);
        
        var labelContainer = new HBox();
        labelContainer.getChildren().add(angstromLabel);
        labelContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        container.getChildren().addAll(fieldContainer, labelContainer);
        return container;
    }
    
    private void updateAngstromLabels() {
        try {
            var dispersion = computeCurrentDispersion();
            if (dispersion != null && !Double.isNaN(dispersion.angstromsPerPixel())) {
                // Update pixel shift label
                try {
                    double pixelShift = Double.parseDouble(pixelShiftingField.getText());
                    double angstroms = pixelShift * dispersion.angstromsPerPixel();
                    pixelShiftAngstromLabel.setText(String.format("(%.2f Å)", angstroms));
                    pixelShiftAngstromLabel.setVisible(true);
                } catch (NumberFormatException ignored) {
                    pixelShiftAngstromLabel.setVisible(false);
                }
                
                // Update doppler shift label
                try {
                    double dopplerShift = Double.parseDouble(dopplerShiftingField.getText());
                    double angstroms = dopplerShift * dispersion.angstromsPerPixel();
                    dopplerShiftAngstromLabel.setText(String.format("(%.2f Å)", angstroms));
                    dopplerShiftAngstromLabel.setVisible(true);
                } catch (NumberFormatException ignored) {
                    dopplerShiftAngstromLabel.setVisible(false);
                }
                
                // Update continuum shift label
                try {
                    double continuumShift = Double.parseDouble(continuumShiftingField.getText());
                    double angstroms = continuumShift * dispersion.angstromsPerPixel();
                    continuumShiftAngstromLabel.setText(String.format("(%.2f Å)", angstroms));
                    continuumShiftAngstromLabel.setVisible(true);
                } catch (NumberFormatException ignored) {
                    continuumShiftAngstromLabel.setVisible(false);
                }
            } else {
                pixelShiftAngstromLabel.setVisible(false);
                dopplerShiftAngstromLabel.setVisible(false);
                continuumShiftAngstromLabel.setVisible(false);
            }
        } catch (Exception ignored) {
            pixelShiftAngstromLabel.setVisible(false);
            dopplerShiftAngstromLabel.setVisible(false);
            continuumShiftAngstromLabel.setVisible(false);
        }
    }
    
    private Dispersion computeCurrentDispersion() {
        if (controller == null) {
            return null;
        }
        
        try {
            var obsDetails = controller.getObservationDetails();
            if (obsDetails == null || obsDetails.instrument() == null || obsDetails.pixelSize() == null) {
                return null;
            }
            
            var selectedRay = wavelengthChoice.getValue();
            if (selectedRay == null) {
                return null;
            }
            
            var lambda0 = selectedRay.wavelength();
            var pixelSize = obsDetails.pixelSize();
            var binning = obsDetails.binning() != null ? obsDetails.binning() : 1;
            
            if (pixelSize == null) {
                return null;
            }
            
            return SpectrumAnalyzer.computeSpectralDispersion(obsDetails.instrument(), lambda0, pixelSize * binning);
        } catch (Exception e) {
            return null;
        }
    }
    
    public void onObservationDetailsChanged() {
        updateAngstromLabels();
    }
}