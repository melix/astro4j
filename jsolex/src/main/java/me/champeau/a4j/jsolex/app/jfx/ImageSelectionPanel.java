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
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ImageSelectionPanel extends BaseParameterPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageSelectionPanel.class);

    private CheckBox raw;
    private CheckBox geometryCorrected;
    private CheckBox geometryCorrectedStretched;
    private CheckBox reconstruction;
    private CheckBox technicalCard;

    private CheckBox colorized;
    private CheckBox virtualEclipse;
    private CheckBox negative;
    private CheckBox mixed;
    private CheckBox doppler;
    private CheckBox dopplerEclipse;
    private CheckBox continuum;
    private CheckBox redshift;
    private CheckBox activeRegions;
    private CheckBox ellermanBombs;

    private Set<CheckBox> quickModeCheckBoxes;
    private Set<CheckBox> fullModeCheckBoxes;

    private CheckBox debug;

    private ChoiceBox<PixelShiftMode> pixelShiftMode;
    private TextField pixelShiftsField;
    private Button openImageMathButton;

    private ProcessParamsController controller;
    private HostServices hostServices;
    private boolean batchMode;
    private Stage stage;
    private double dopplerShift = 3.0;
    private double continuumShift = 0.0;
    private ImageMathParams imageMathParams = ImageMathParams.NONE;
    private Set<Double> internalPixelShifts;
    private Set<Double> requestedWaveLengths;
    private boolean autoContinuum;
    private SelectionMode currentMode = SelectionMode.CUSTOM; // Will be dynamically updated

    private ImageMathParams cachedImageMathParams;
    private List<Double> cachedShifts;

    public ImageSelectionPanel() {
        getStyleClass().add("parameter-panel");
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initializeComponents() {
        raw = createCheckbox("raw");
        geometryCorrected = createCheckbox("geometry.corrected");
        geometryCorrectedStretched = createCheckbox("geometry.corrected.stretched");
        reconstruction = createCheckbox("reconstruction");
        technicalCard = createCheckbox("technical.card");

        colorized = createCheckbox("colorized");
        virtualEclipse = createCheckbox("eclipse");
        negative = createCheckbox("negative.image");
        mixed = createCheckbox("mixed.image");
        doppler = createCheckbox("doppler.image");
        dopplerEclipse = createCheckbox("doppler.eclipse");
        continuum = createCheckbox("continuum");
        redshift = createCheckbox("redshift");
        activeRegions = createCheckbox("activeregions");
        ellermanBombs = createCheckbox("ellerman.bombs");

        quickModeCheckBoxes = Set.of(
            raw, reconstruction, geometryCorrected, geometryCorrectedStretched
        );
        fullModeCheckBoxes = Set.of(
                reconstruction,
                raw,
                virtualEclipse,
                colorized,
                mixed,
                negative,
                dopplerEclipse,
                doppler,
                geometryCorrected,
                geometryCorrectedStretched,
                continuum,
                technicalCard,
                redshift,
                activeRegions,
                ellermanBombs
        );

        debug = createCheckbox("debug.images");

        pixelShiftMode = createChoiceBox();
        pixelShiftMode.setItems(FXCollections.observableArrayList(PixelShiftMode.values()));
        pixelShiftMode.setValue(PixelShiftMode.SIMPLE);
        pixelShiftMode.setConverter(new StringConverter<PixelShiftMode>() {
            @Override
            public String toString(PixelShiftMode mode) {
                return mode == null ? "" : (mode == PixelShiftMode.SIMPLE ? "Simple" : "Image Math");
            }

            @Override
            public PixelShiftMode fromString(String string) {
                return null;
            }
        });

        pixelShiftsField = new TextField("0");

        openImageMathButton = new Button(I18N.string(JSolEx.class, "image-selection", "open.imagemath"));
        openImageMathButton.getStyleClass().add("default-button");
        openImageMathButton.setDisable(true);
    }

    private static CheckBox createCheckbox(String item) {
        var checkBox = new CheckBox(I18N.string(JSolEx.class, "image-selection", item));
        checkBox.setWrapText(true);
        return checkBox;
    }

    private void setupLayout() {
        setPadding(new Insets(24));
        setSpacing(24);

        var basicSection = createSection("basic.images");
        var basicGrid = createCompactGrid();

        basicGrid.add(raw, 0, 0);
        basicGrid.add(reconstruction, 0, 1);
        basicGrid.add(continuum, 0, 2);

        basicGrid.add(geometryCorrected, 1, 0);
        basicGrid.add(geometryCorrectedStretched, 1, 1);

        basicSection.getChildren().add(basicGrid);

        var advancedSection = createSection("advanced.images");
        var advancedGrid = createCompactGrid();

        advancedGrid.add(colorized, 0, 0);
        advancedGrid.add(virtualEclipse, 0, 1);
        advancedGrid.add(negative, 0, 2);
        advancedGrid.add(mixed, 0, 3);
        advancedGrid.add(doppler, 0, 4);

        advancedGrid.add(dopplerEclipse, 1, 0);
        advancedGrid.add(technicalCard, 1, 1);
        advancedGrid.add(redshift, 1, 2);
        advancedGrid.add(activeRegions, 1, 3);
        advancedGrid.add(ellermanBombs, 1, 4);

        advancedSection.getChildren().add(advancedGrid);

        var debugSection = createSection("debug.options");
        var debugGrid = createCompactGrid();

        debugGrid.add(debug, 0, 0);

        debugSection.getChildren().add(debugGrid);

        var pixelShiftSection = createSection("custom.images");
        var pixelShiftGrid = createGrid();

        var modeBox = createHBox();
        HBox.setHgrow(pixelShiftMode, Priority.ALWAYS);
        modeBox.getChildren().addAll(pixelShiftMode, openImageMathButton);

        addGridRow(pixelShiftGrid, 0, "Mode:", modeBox);
        addGridRow(pixelShiftGrid, 1, I18N.string(JSolEx.class, "process-params", "pixel.shifts") + ":", pixelShiftsField);

        pixelShiftSection.getChildren().add(pixelShiftGrid);

        var actionsSection = createSection("presets");
        var actionsGrid = createGrid();

        var quickModeButton = new Button(I18N.string(JSolEx.class, "process-params", "quick.mode"));
        quickModeButton.getStyleClass().add("quick-button");
        quickModeButton.setOnAction(e -> loadQuickModeSelection());

        var fullModeButton = new Button(I18N.string(JSolEx.class, "process-params", "full.process"));
        fullModeButton.getStyleClass().add("custom-button");
        fullModeButton.setOnAction(e -> loadFullModeSelection());

        var selectAllButton = new Button(I18N.string(JSolEx.class, "image-selection", "select.all"));
        selectAllButton.getStyleClass().add("default-button");
        selectAllButton.setOnAction(e -> selectAll(true));

        var unselectAllButton = new Button(I18N.string(JSolEx.class, "image-selection", "unselect.all"));
        unselectAllButton.getStyleClass().add("default-button");
        unselectAllButton.setOnAction(e -> selectAll(false));

        var presetBox = createHBox();
        presetBox.getChildren().addAll(quickModeButton, fullModeButton);

        var selectionBox = createHBox();
        selectionBox.getChildren().addAll(selectAllButton, unselectAllButton);

        addGridRow(actionsGrid, 0, I18N.string(JSolEx.class, "process-params", "presets") + ":", presetBox);
        addGridRow(actionsGrid, 1, I18N.string(JSolEx.class, "process-params", "selection") + ":", selectionBox);

        actionsSection.getChildren().add(actionsGrid);

        getChildren().addAll(actionsSection, basicSection, advancedSection, pixelShiftSection, debugSection);
    }

    private void setupEventHandlers() {
        pixelShiftMode.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                var isImageMath = newValue == PixelShiftMode.IMAGEMATH;
                openImageMathButton.setDisable(!isImageMath);
                pixelShiftsField.setDisable(isImageMath);

                if (isImageMath && imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
                    updatePixelShiftsWithSelectedImages(findPixelShifts(imageMathParams));
                } else if (!isImageMath) {
                    updatePixelShiftsWithSelectedImages(List.of(0.0));
                }
            }
        });

        doppler.selectedProperty().addListener((obs, oldValue, newValue) -> adjustPixelShifts(newValue, -dopplerShift, dopplerShift));
        continuum.selectedProperty().addListener((obs, oldValue, newValue) -> adjustPixelShifts(newValue, continuumShift));
        activeRegions.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                continuum.setSelected(true);
            }
        });

        openImageMathButton.setOnAction(e -> openImageMath());

        addCustomModeListener(raw);
        addCustomModeListener(geometryCorrected);
        addCustomModeListener(geometryCorrectedStretched);
        addCustomModeListener(reconstruction);
        addCustomModeListener(technicalCard);
        addCustomModeListener(colorized);
        addCustomModeListener(virtualEclipse);
        addCustomModeListener(negative);
        addCustomModeListener(mixed);
        addCustomModeListener(doppler);
        addCustomModeListener(dopplerEclipse);
        addCustomModeListener(continuum);
        addCustomModeListener(redshift);
        addCustomModeListener(activeRegions);
        addCustomModeListener(ellermanBombs);
        addCustomModeListener(debug);
    }

    private void addCustomModeListener(CheckBox checkBox) {
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            currentMode = determineCurrentMode();
            updateCurrentModeDisplay();
        });
    }

    private void updateCurrentModeDisplay() {
        if (controller != null) {
            controller.updateFullButtonLabel();
        }
    }



    private GridPane createCompactGrid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        var leftCol = new ColumnConstraints();
        leftCol.setMinWidth(150);
        leftCol.setPrefWidth(180);
        leftCol.setHgrow(Priority.SOMETIMES);

        var rightCol = new ColumnConstraints();
        rightCol.setMinWidth(150);
        rightCol.setPrefWidth(180);
        rightCol.setHgrow(Priority.SOMETIMES);

        grid.getColumnConstraints().addAll(leftCol, rightCol);
        grid.getStyleClass().add("form-grid");
        return grid;
    }


    private void selectAll(boolean selected) {
        selectAllExceptDebug(selected);

        debug.setSelected(selected);

        if (selected) {
            currentMode = determineCurrentMode();
            updateCurrentModeDisplay();
        }
    }

    private void selectAllExceptDebug(boolean selected) {
        raw.setSelected(selected);
        geometryCorrected.setSelected(selected);
        geometryCorrectedStretched.setSelected(selected);
        reconstruction.setSelected(selected);
        technicalCard.setSelected(selected);

        colorized.setSelected(selected);
        virtualEclipse.setSelected(selected);
        negative.setSelected(selected);
        mixed.setSelected(selected);
        doppler.setSelected(selected);
        dopplerEclipse.setSelected(selected);
        continuum.setSelected(selected);
        redshift.setSelected(selected);
        activeRegions.setSelected(selected);
        ellermanBombs.setSelected(selected);
    }

    private void updatePixelShiftsWithSelectedImages(List<Double> newPixelShifts) {
        var result = new TreeSet<>(newPixelShifts);
        if (result.isEmpty()) {
            result.add(0.0);
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
        for (var shift : shifts) {
            if (newValue) {
                newPixelShifts.add(shift);
            } else {
                newPixelShifts.remove(shift);
            }
        }
        setPixelShiftText(newPixelShifts);
    }

    private void setPixelShiftText(List<Double> pixelShifts) {
        pixelShiftsField.setText(pixelShifts.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(";")));
    }

    private List<Double> readPixelShifts() {
        return Arrays.stream(pixelShiftsField.getText().split("\\s*;\\s*"))
                .filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .collect(Collectors.toList());
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
        requestedWaveLengths = new TreeSet<>();
        autoContinuum = false;
        for (var file : params.scriptFiles()) {
            if (Files.exists(file.toPath())) {
                try {
                    var result = executor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    allShifts.addAll(result.internalShifts());
                    internalPixelShifts.addAll(result.internalShifts());
                    allShifts.addAll(result.outputShifts());
                    requestedWaveLengths.addAll(result.requestedWavelenghts());
                    autoContinuum |= result.autoContinuum();
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
            }
        }
        var list = allShifts.stream().toList();
        cachedShifts = list;
        cachedImageMathParams = params;
        
        updatePixelShiftFromInternalShifts();
        
        return list;
    }

    private void openImageMath() {
        if (stage != null && hostServices != null) {
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
                            LOGGER.warn("Error while executing image math script {}", e.getMessage());
                            updatePixelShiftsWithSelectedImages(List.of(0.0));
                        }
                        this.imageMathParams = params;


                        currentMode = determineCurrentMode();
                        updateCurrentModeDisplay();
                    }));
        }
    }

    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    public void updatePixelShiftFromInternalShifts() {
        if (controller != null && !internalPixelShifts.isEmpty()) {
            double currentPixelShift = Double.parseDouble(controller.getProcessingPanel().getPixelShiftValue());
            
            if (!internalPixelShifts.contains(currentPixelShift)) {
                double newPixelShift = internalPixelShifts.iterator().next(); // Use first internal shift
                controller.getProcessingPanel().setPixelShiftValue(String.valueOf(newPixelShift));
            }
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }

    public void setShiftParameters(double dopplerShift, double continuumShift) {
        this.dopplerShift = dopplerShift;
        this.continuumShift = continuumShift;
    }

    public void loadQuickModeSelection() {
        selectAllExceptDebug(false);
        raw.setSelected(true);
        reconstruction.setSelected(true);
        geometryCorrected.setSelected(true);
        geometryCorrectedStretched.setSelected(true);
        currentMode = determineCurrentMode();
        updateCurrentModeDisplay();
    }

    public void loadFullModeSelection() {
        selectAllExceptDebug(true);
        currentMode = determineCurrentMode();
        updateCurrentModeDisplay();
    }

    public void loadData(ProcessParams processParams) {
        var requestedImages = processParams.requestedImages();
        var images = requestedImages.images();

        raw.setSelected(images.contains(GeneratedImageKind.RAW));
        geometryCorrected.setSelected(images.contains(GeneratedImageKind.GEOMETRY_CORRECTED));
        geometryCorrectedStretched.setSelected(images.contains(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED));
        reconstruction.setSelected(images.contains(GeneratedImageKind.RECONSTRUCTION));
        technicalCard.setSelected(images.contains(GeneratedImageKind.TECHNICAL_CARD));

        colorized.setSelected(images.contains(GeneratedImageKind.COLORIZED));
        virtualEclipse.setSelected(images.contains(GeneratedImageKind.VIRTUAL_ECLIPSE));
        negative.setSelected(images.contains(GeneratedImageKind.NEGATIVE));
        mixed.setSelected(images.contains(GeneratedImageKind.MIXED));
        doppler.setSelected(images.contains(GeneratedImageKind.DOPPLER));
        dopplerEclipse.setSelected(images.contains(GeneratedImageKind.DOPPLER_ECLIPSE));
        continuum.setSelected(images.contains(GeneratedImageKind.CONTINUUM));
        redshift.setSelected(images.contains(GeneratedImageKind.REDSHIFT));
        activeRegions.setSelected(images.contains(GeneratedImageKind.ACTIVE_REGIONS));
        ellermanBombs.setSelected(images.contains(GeneratedImageKind.ELLERMAN_BOMBS));

        debug.setSelected(images.contains(GeneratedImageKind.DEBUG));

        autoContinuum = requestedImages.autoContinuum();

        currentMode = determineCurrentMode();
        updateCurrentModeDisplay();

    }

    public RequestedImages getRequestedImages() {
        Set<GeneratedImageKind> images = EnumSet.noneOf(GeneratedImageKind.class);

        if (raw.isSelected()) {
            images.add(GeneratedImageKind.RAW);
        }
        if (geometryCorrected.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED);
        }
        if (geometryCorrectedStretched.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED);
        }
        if (reconstruction.isSelected()) {
            images.add(GeneratedImageKind.RECONSTRUCTION);
        }
        if (technicalCard.isSelected()) {
            images.add(GeneratedImageKind.TECHNICAL_CARD);
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
        if (dopplerEclipse.isSelected()) {
            images.add(GeneratedImageKind.DOPPLER_ECLIPSE);
        }
        if (continuum.isSelected()) {
            images.add(GeneratedImageKind.CONTINUUM);
        }
        if (redshift.isSelected()) {
            images.add(GeneratedImageKind.REDSHIFT);
        }
        if (activeRegions.isSelected()) {
            images.add(GeneratedImageKind.ACTIVE_REGIONS);
        }
        if (ellermanBombs.isSelected()) {
            images.add(GeneratedImageKind.ELLERMAN_BOMBS);
            images.add(GeneratedImageKind.FLARES);
        }

        if (debug.isSelected()) {
            images.add(GeneratedImageKind.DEBUG);
        }

        var pixelShifts = readPixelShifts();
        
        Set<Double> computedInternalShifts = new HashSet<>();
        if (images.contains(GeneratedImageKind.DOPPLER) || 
            images.contains(GeneratedImageKind.DOPPLER_ECLIPSE) ||
            images.contains(GeneratedImageKind.REDSHIFT)) {
            computedInternalShifts.add(dopplerShift);
            computedInternalShifts.add(-dopplerShift);
        }

        if (continuum.isSelected()) {
            pixelShifts.add(continuumShift);
        }

        if (internalPixelShifts != null) {
            computedInternalShifts.addAll(internalPixelShifts);
        }
        
        return new RequestedImages(
                images,
                pixelShifts.stream().distinct().toList(),
                computedInternalShifts,
                requestedWaveLengths == null ? Set.of() : Collections.unmodifiableSet(requestedWaveLengths),
                pixelShiftMode.getValue() == PixelShiftMode.IMAGEMATH ? imageMathParams : ImageMathParams.NONE,
                autoContinuum
        );
    }

    public boolean isCustomMode() {
        return currentMode == SelectionMode.CUSTOM || pixelShiftMode.getValue() == PixelShiftMode.IMAGEMATH;
    }

    private enum PixelShiftMode {
        SIMPLE("mode.simple"),
        IMAGEMATH("mode.imagemath");

        private final String label;

        PixelShiftMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private enum SelectionMode {
        QUICK, FULL, CUSTOM
    }

    /**
     * Dynamically determines the selection mode based on the current image selections
     * by comparing with RequestedImages constants.
     */
    private SelectionMode determineCurrentMode() {
        if (fullModeCheckBoxes.stream().allMatch(CheckBox::isSelected)) {
            return SelectionMode.FULL;
        } else if (quickModeCheckBoxes.stream().allMatch(CheckBox::isSelected)) {
            return SelectionMode.QUICK;
        } else {
            return SelectionMode.CUSTOM;
        }
    }
}