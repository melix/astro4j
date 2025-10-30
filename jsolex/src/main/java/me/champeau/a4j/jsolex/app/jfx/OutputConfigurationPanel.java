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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ExtraParams;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;

public class OutputConfigurationPanel extends BaseParameterPanel {

    private CheckBox autoSave;
    private CheckBox autoTrimSerFile;
    private ChoiceBox<GlobeStyle> globeStyle;
    private ChoiceBox<NamedPattern> namingPattern;
    private ProcessParamsController controller;
    
    public OutputConfigurationPanel() {
        getStyleClass().add("parameter-panel");
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        autoSave = new CheckBox(I18N.string(JSolEx.class, "process-params", "automatic.save.images"));
        autoSave.setSelected(true);
        autoSave.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "automatic.save.images.tooltip")));
        
        autoTrimSerFile = new CheckBox(I18N.string(JSolEx.class, "process-params", "auto.trim.ser.file"));
        autoTrimSerFile.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "auto.trim.ser.file.tooltip")));
        
        // Globe style
        globeStyle = createChoiceBox();
        globeStyle.setItems(FXCollections.observableArrayList(GlobeStyle.values()));
        globeStyle.setValue(GlobeStyle.EQUATORIAL_COORDS);
        globeStyle.setConverter(new StringConverter<GlobeStyle>() {
            @Override
            public String toString(GlobeStyle style) {
                return style == null ? "" : style.toString();
            }
            
            @Override
            public GlobeStyle fromString(String string) {
                return null;
            }
        });
        
        // Naming pattern
        namingPattern = createChoiceBox();
        namingPattern.setItems(FXCollections.observableArrayList(FileNamingPatternsIO.loadDefaults()));
        namingPattern.setConverter(new StringConverter<NamedPattern>() {
            @Override
            public String toString(NamedPattern pattern) {
                return pattern == null ? "" : pattern.label();
            }
            
            @Override
            public NamedPattern fromString(String string) {
                return null;
            }
        });
        
        if (!namingPattern.getItems().isEmpty()) {
            namingPattern.getSelectionModel().selectFirst();
        }
    }
    
    private void setupLayout() {
        setPadding(new Insets(24));
        setSpacing(24);

        var optionsSection = createSection("output.options");
        var optionCheckboxes = new VBox(8);
        optionCheckboxes.getChildren().addAll(
            autoSave,
            autoTrimSerFile
        );

        var optionsGrid = createGrid();
        addGridRow(optionsGrid, 0, I18N.string(JSolEx.class, "process-params", "globestyle") + ":", globeStyle);
        
        optionsSection.getChildren().addAll(optionCheckboxes, optionsGrid);
        
        var namingSection = createSection("naming.pattern");
        var namingGrid = createGrid();
        addGridRow(namingGrid, 0, I18N.string(JSolEx.class, "process-params", "naming.pattern") + ":", createNamingPatternBox());
        namingSection.getChildren().add(namingGrid);
        
        var resetButton = new Button(I18N.string(JSolEx.class, "process-params", "reset.to.defaults"));
        resetButton.getStyleClass().add("default-button");
        resetButton.setOnAction(e -> resetToDefaults());
        
        getChildren().addAll(optionsSection, namingSection, resetButton);
    }
    
    
    private HBox createNamingPatternBox() {
        var box = createHBox();

        HBox.setHgrow(namingPattern, Priority.ALWAYS);

        var editButton = new Button("...");
        editButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "naming.pattern")));
        editButton.getStyleClass().add("default-button");
        editButton.setOnAction(e -> openNamingPatternEditor());
        
        box.getChildren().addAll(namingPattern, editButton);
        return box;
    }
    
    private void openNamingPatternEditor() {
        controller.openNamingPatternEditor();
    }
    
    private void resetToDefaults() {
        autoSave.setSelected(true);
        autoTrimSerFile.setSelected(false);
        
        globeStyle.setValue(GlobeStyle.EQUATORIAL_COORDS);
        
        namingPattern.getItems().clear();
        namingPattern.getItems().addAll(FileNamingPatternsIO.loadDefaults());
        if (!namingPattern.getItems().isEmpty()) {
            namingPattern.getSelectionModel().selectFirst();
        }
    }
    
    public void loadData(ProcessParams params, boolean batchMode) {
        var extraParams = params.extraParams();

        // Load other options
        autoSave.setSelected(extraParams.autosave());
        globeStyle.setValue(extraParams.globeStyle() != null ? extraParams.globeStyle() : GlobeStyle.EQUATORIAL_COORDS);
        // autoTrimSerFile is handled separately in the controller
        
        // Load naming pattern
        if (extraParams.fileNamePattern() != null) {
            // Find matching pattern by pattern string
            namingPattern.getItems().stream()
                .filter(p -> p.pattern().equals(extraParams.fileNamePattern()))
                .findFirst()
                .ifPresent(namingPattern::setValue);
        }
    }
    
    public ExtraParams getExtraParams() {
        var defaults = ProcessParams.loadDefaults().extraParams();

        return defaults
            .withAutosave(autoSave.isSelected())
            .withGlobeStyle(globeStyle.getValue() != null ? globeStyle.getValue() : GlobeStyle.EQUATORIAL_COORDS)
            .withFileNamePattern(namingPattern.getValue() != null ? namingPattern.getValue().pattern() : defaults.fileNamePattern());
    }
    
    public boolean isAutoTrimSelected() {
        return autoTrimSerFile.isSelected();
    }
    
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    public void updateNamingPattern(NamedPattern pattern) {
        namingPattern.getItems().clear();
        namingPattern.getItems().addAll(FileNamingPatternsIO.loadDefaults());
        namingPattern.getSelectionModel().select(pattern);
    }
}