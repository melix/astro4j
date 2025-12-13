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
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.EllipseFittingMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.ser.ColorMode;

import java.util.Locale;

/**
 * Panel for advanced processing parameters like tilt, XY ratio, and polynomial settings.
 */
public class AdvancedParametersPanel extends BaseParameterPanel {

    private ProcessParamsController controller;
    
    private CheckBox forceTilt;
    private TextField tiltValue;
    
    private CheckBox forceXYRatio;
    private TextField xyRatioValue;
    
    private CheckBox forcePolynomial;
    private TextField forcedPolynomial;
    private Button forcePolynomialOpen;
    
    private ChoiceBox<EllipseFittingMode> ellipseFittingMode;
    
    private CheckBox disallowDownsampling;
    private CheckBox spectrumVFlip;
    private CheckBox assumeMonoVideo;

    /** Creates a new advanced parameters panel. */
    public AdvancedParametersPanel() {
        getStyleClass().add("parameter-panel");
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        forceTilt = new CheckBox(I18N.string(JSolEx.class, "process-params", "override.tilt"));
        forceTilt.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "override.tilt.tooltip")));
        tiltValue = new TextField();
        tiltValue.setPromptText(I18N.string(JSolEx.class, "process-params", "tilt.angle"));
        tiltValue.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "tilt.angle")));
        
        forceXYRatio = new CheckBox(I18N.string(JSolEx.class, "process-params", "override.xy"));
        forceXYRatio.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "override.xy.tooltip")));
        xyRatioValue = new TextField();
        xyRatioValue.setPromptText(I18N.string(JSolEx.class, "process-params", "xy.ratio.tooltip"));
        xyRatioValue.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "xy.ratio.tooltip")));
        
        forcePolynomial = new CheckBox(I18N.string(JSolEx.class, "process-params", "force.polynomial"));
        forcedPolynomial = new TextField();
        forcedPolynomial.setPromptText(I18N.string(JSolEx.class, "process-params", "force.polynomial"));
        forcePolynomialOpen = new Button("...");
        forcePolynomialOpen.setOnAction(e -> openPolynomialEditor());
        
        ellipseFittingMode = createChoiceBox();
        ellipseFittingMode.setItems(FXCollections.observableArrayList(EllipseFittingMode.values()));
        ellipseFittingMode.setValue(EllipseFittingMode.AUTOMATIC);
        ellipseFittingMode.setConverter(new StringConverter<EllipseFittingMode>() {
            @Override
            public String toString(EllipseFittingMode mode) {
                return mode == null ? null : I18N.string(JSolEx.class, "process-params", "ellipse.fitting.mode." + mode.name().toLowerCase(Locale.US));
            }
            @Override
            public EllipseFittingMode fromString(String string) {
                return EllipseFittingMode.valueOf(string);
            }
        });
        
        disallowDownsampling = new CheckBox(I18N.string(JSolEx.class, "process-params", "disallow.downsampling"));
        disallowDownsampling.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "disallow.downsampling")));
        
        spectrumVFlip = new CheckBox(I18N.string(JSolEx.class, "process-params", "spectrum.vflip"));
        spectrumVFlip.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "spectrum.vflip.tooltip")));
        
        assumeMonoVideo = new CheckBox(I18N.string(JSolEx.class, "process-params", "assume.mono.images"));
        assumeMonoVideo.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "assume.mono.images.tooltip")));
        assumeMonoVideo.setSelected(true);

        tiltValue.disableProperty().bind(forceTilt.selectedProperty().not());
        xyRatioValue.disableProperty().bind(forceXYRatio.selectedProperty().not());
        forcedPolynomial.disableProperty().bind(forcePolynomial.selectedProperty().not());
        forcePolynomialOpen.disableProperty().bind(forcePolynomial.selectedProperty().not());
    }
    
    private void setupLayout() {
        setPadding(new Insets(24));
        setSpacing(24);
        
        var advancedSection = createSection("advanced.process.params");
        var advancedGrid = createGrid();
        
        advancedGrid.add(forceTilt, 0, 0);
        advancedGrid.add(tiltValue, 1, 0);
        advancedGrid.add(forceXYRatio, 0, 1);
        advancedGrid.add(xyRatioValue, 1, 1);
        advancedGrid.add(forcePolynomial, 0, 2);
        advancedGrid.add(forcedPolynomial, 1, 2);
        advancedGrid.add(forcePolynomialOpen, 2, 2);
        
        addGridRow(advancedGrid, 3, I18N.string(JSolEx.class, "process-params", "ellipse.fitting.mode") + ":", ellipseFittingMode, "ellipse.fitting.mode.tooltip");
        addGridRow(advancedGrid, 4, disallowDownsampling);
        addGridRow(advancedGrid, 5, spectrumVFlip, "spectrum.vflip.tooltip");
        addGridRow(advancedGrid, 6, assumeMonoVideo, "assume.mono.images.tooltip");
        
        advancedSection.getChildren().add(advancedGrid);
        
        var resetButton = new Button(I18N.string(JSolEx.class, "process-params", "reset.to.defaults"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> resetToDefaults());
        
        getChildren().addAll(advancedSection, resetButton);
    }
    
    
    private void resetToDefaults() {
        forceTilt.setSelected(false);
        tiltValue.setText("");
        forceXYRatio.setSelected(false);
        xyRatioValue.setText("");
        forcePolynomial.setSelected(false);
        forcedPolynomial.setText("");
        ellipseFittingMode.setValue(EllipseFittingMode.AUTOMATIC);
        disallowDownsampling.setSelected(false);
        spectrumVFlip.setSelected(false);
    }

    /**
     * Loads data from the given process parameters.
     * @param params the parameters to load from
     */
    public void loadData(ProcessParams params) {
        var geometryParams = params.geometryParams();
        
        var tiltOpt = geometryParams.tilt();
        if (tiltOpt.isPresent()) {
            forceTilt.setSelected(true);
            tiltValue.setText(String.valueOf(tiltOpt.getAsDouble()));
        } else {
            forceTilt.setSelected(false);
            tiltValue.setText("");
        }
        
        var xyRatioOpt = geometryParams.xyRatio();
        if (xyRatioOpt.isPresent()) {
            forceXYRatio.setSelected(true);
            xyRatioValue.setText(String.valueOf(xyRatioOpt.getAsDouble()));
        } else {
            forceXYRatio.setSelected(false);
            xyRatioValue.setText("");
        }
        
        forcePolynomial.setSelected(geometryParams.isForcePolynomial());
        var forcedPoly = geometryParams.forcedPolynomial();
        if (forcedPoly.isPresent()) {
            forcedPolynomial.setText(forcedPoly.get());
        } else {
            forcedPolynomial.setText("");
        }
        
        ellipseFittingMode.setValue(geometryParams.ellipseFittingMode());
        disallowDownsampling.setSelected(geometryParams.isDisallowDownsampling());
        spectrumVFlip.setSelected(geometryParams.isSpectrumVFlip());
        
        var videoParams = params.videoParams();
        assumeMonoVideo.setSelected(videoParams.colorMode() != null && videoParams.colorMode() == ColorMode.MONO);
    }

    /**
     * Returns whether the force tilt checkbox is selected.
     * @return true if selected
     */
    public boolean isForceTiltSelected() {
        return forceTilt.isSelected();
    }

    /**
     * Returns the tilt value entered.
     * @return the tilt value
     */
    public double getTiltValue() {
        return parseDouble(tiltValue.getText(), 0.0);
    }

    /**
     * Returns whether the force XY ratio checkbox is selected.
     * @return true if selected
     */
    public boolean isForceXYRatioSelected() {
        return forceXYRatio.isSelected();
    }

    /**
     * Returns the XY ratio value entered.
     * @return the XY ratio value
     */
    public double getXYRatioValue() {
        return parseDouble(xyRatioValue.getText(), 1.0);
    }

    /**
     * Returns the selected ellipse fitting mode.
     * @return the ellipse fitting mode
     */
    public EllipseFittingMode getEllipseFittingMode() {
        return ellipseFittingMode.getValue();
    }

    /**
     * Returns whether the force polynomial checkbox is selected.
     * @return true if selected
     */
    public boolean isForcePolynomialSelected() {
        return forcePolynomial.isSelected();
    }

    /**
     * Returns the forced polynomial expression.
     * @return the polynomial expression
     */
    public String getForcedPolynomial() {
        return forcedPolynomial.getText();
    }

    /**
     * Returns whether downsampling is disallowed.
     * @return true if downsampling is disallowed
     */
    public boolean isDisallowDownsamplingSelected() {
        return disallowDownsampling.isSelected();
    }

    /**
     * Returns whether spectrum vertical flip is enabled.
     * @return true if enabled
     */
    public boolean isSpectrumVFlipSelected() {
        return spectrumVFlip.isSelected();
    }

    /**
     * Returns whether to assume mono video.
     * @return true if mono video is assumed
     */
    public boolean isAssumeMonoVideoSelected() {
        return assumeMonoVideo.isSelected();
    }

    /**
     * Sets the controller for this panel.
     * @param controller the controller
     */
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    private void openPolynomialEditor() {
        controller.openVideoDebugger();
    }

    /**
     * Updates the forced polynomial field.
     * @param polynomial the polynomial expression
     */
    public void updateForcedPolynomial(String polynomial) {
        if (polynomial != null && !polynomial.trim().isEmpty()) {
            forcedPolynomial.setText(polynomial);
            forcePolynomial.setSelected(true);
        }
    }

    /**
     * Updates the spectrum vertical flip setting.
     * @param spectrumVFlipEnabled whether vertical flip is enabled
     */
    public void updateSpectrumVFlip(boolean spectrumVFlipEnabled) {
        spectrumVFlip.setSelected(spectrumVFlipEnabled);
    }
}