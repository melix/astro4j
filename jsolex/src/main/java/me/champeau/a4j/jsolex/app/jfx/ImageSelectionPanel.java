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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.repository.RemoteScript;
import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepositoryManager;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ScriptExecutionContext;
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ScriptParameterExtractor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.UserPreset;
import me.champeau.a4j.jsolex.processing.params.UserPresetIO;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Panel for selecting which images to generate during processing.
 */
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
    private Set<CheckBox> fullModeOnlyCheckBoxes;

    private CheckBox debug;
    private CheckBox applyAutomaticScripts;

    private Label scriptLabel;
    private Button openImageMathButton;
    private Button clearScriptsButton;
    private Button savePresetButton;
    private FlowPane userPresetsContainer;
    private VBox userPresetsSection;
    private VBox scriptParametersSection;
    private VBox scriptParametersContainer;
    private final Map<String, Boolean> scriptParameterValidationStates = new HashMap<>();
    private final BooleanProperty allScriptParametersValid = new SimpleBooleanProperty(true);
    private VBox repositoryScriptsSection;
    private FlowPane repositoryScriptsContainer;
    private final Set<RemoteScript> selectedRepositoryScripts = new HashSet<>();
    private ScriptRepositoryManager repositoryManager;

    private ProcessParamsController controller;
    private HostServices hostServices;
    private boolean batchMode;
    private Stage stage;
    private ImageMathParams imageMathParams = ImageMathParams.NONE;
    private Set<Double> requestedWaveLengths;
    private boolean autoContinuum;
    private SelectionMode currentMode = SelectionMode.CUSTOM;

    /**
     * Creates a new instance. Required by FXML.
     */
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
        fullModeOnlyCheckBoxes = new HashSet<>(fullModeCheckBoxes);
        fullModeOnlyCheckBoxes.removeAll(quickModeCheckBoxes);

        debug = createCheckbox("debug.images");
        applyAutomaticScripts = createCheckbox("apply.automatic.scripts");
        applyAutomaticScripts.setSelected(true);

        scriptLabel = new Label("No script loaded");
        scriptLabel.getStyleClass().add("script-label");
        scriptLabel.setWrapText(true);

        openImageMathButton = new Button(I18N.string(JSolEx.class, "image-selection", "open.imagemath"));
        openImageMathButton.getStyleClass().add("default-button");
        openImageMathButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);

        clearScriptsButton = new Button(I18N.string(JSolEx.class, "process-params", "clear"));
        clearScriptsButton.getStyleClass().add("default-button");
        clearScriptsButton.setOnAction(e -> clearScripts());
        clearScriptsButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);

        savePresetButton = new Button(I18N.string(JSolEx.class, "process-params", "save.preset"));
        savePresetButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "save.preset.tooltip")));
        savePresetButton.getStyleClass().add("default-button");
        savePresetButton.setOnAction(e -> saveCurrentSelectionAsPreset());
        savePresetButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);

        userPresetsContainer = new FlowPane();
        userPresetsContainer.setHgap(8);
        userPresetsContainer.setVgap(4);

        scriptParametersContainer = new VBox();
        scriptParametersContainer.setSpacing(8);

        repositoryManager = new ScriptRepositoryManager();

        repositoryScriptsContainer = new FlowPane();
        repositoryScriptsContainer.setHgap(8);
        repositoryScriptsContainer.setVgap(4);

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
        debugGrid.add(applyAutomaticScripts, 1, 0);

        debugSection.getChildren().add(debugGrid);

        var pixelShiftSection = createSection("custom.images");
        var pixelShiftGrid = createGrid();

        var scriptBox = createHBox();
        HBox.setHgrow(scriptLabel, Priority.ALWAYS);
        scriptBox.getChildren().addAll(scriptLabel, clearScriptsButton, openImageMathButton);

        var savePresetBox = createHBox();
        savePresetBox.getChildren().add(savePresetButton);

        addGridRow(pixelShiftGrid, 0, I18N.string(JSolEx.class, "process-params", "scripts") + ":", scriptBox);
        addGridRow(pixelShiftGrid, 1, I18N.string(JSolEx.class, "process-params", "save.preset") + ":", savePresetBox);

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

        userPresetsSection = new VBox();
        var userPresetsGrid = createGrid();
        addGridRow(userPresetsGrid, 0, I18N.string(JSolEx.class, "process-params", "user.presets") + ":", userPresetsContainer);
        userPresetsSection.getChildren().add(userPresetsGrid);

        repositoryScriptsSection = createSection("repository.scripts");
        repositoryScriptsSection.getChildren().add(repositoryScriptsContainer);
        repositoryScriptsSection.setVisible(false);
        repositoryScriptsSection.setManaged(false);

        scriptParametersSection = createSection("script.parameters");
        scriptParametersSection.getChildren().add(scriptParametersContainer);
        scriptParametersSection.setVisible(false);
        scriptParametersSection.setManaged(false);

        loadUserPresets();
        updateUserPresetsVisibility();
        loadRepositoryScripts();

        getChildren().addAll(actionsSection, userPresetsSection, basicSection, advancedSection, pixelShiftSection, repositoryScriptsSection, scriptParametersSection, debugSection);
    }

    private void setupEventHandlers() {
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
        addCustomModeListener(applyAutomaticScripts);
    }

    private void addCustomModeListener(CheckBox checkBox) {
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            determineCurrentMode();
            updateCurrentModeDisplay();
        });
    }

    private void updateCurrentModeDisplay() {
        controller.updateButtonsVisibility();
    }

    SelectionMode getCurrentMode() {
        return currentMode;
    }

    private List<Double> calculatePixelShiftsForSelectedImages() {
        var result = new TreeSet<Double>();
        result.addAll(controller.getProcessingPanel().getPixelShiftValues());

        if (continuum.isSelected() || activeRegions.isSelected() || redshift.isSelected() || ellermanBombs.isSelected()) {
            result.add(Double.parseDouble(controller.getProcessingPanel().getContinuumShiftValue()));
        }

        return result.stream().toList();
    }

    private List<Double> calculateInternalPixelShiftsForSelectedImages() {
        var result = new TreeSet<Double>();
        if (doppler.isSelected() || dopplerEclipse.isSelected()) {
            var ds = Double.parseDouble(controller.getProcessingPanel().getDopplerShiftValue());
            result.add(ds);
            result.add(-ds);
        }
        if (ellermanBombs.isSelected()) {
            result.add(Double.parseDouble(controller.getProcessingPanel().getContinuumShiftValue()));
        }

        return result.stream().toList();
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
        applyAutomaticScripts.setSelected(selected);

        if (selected) {
            determineCurrentMode();
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



    private DefaultImageScriptExecutor createScriptExecutor() {
        return new JSolExScriptExecutor(
                ShiftCollectingImageExpressionEvaluator.zeroImages(),
                ScriptExecutionContext.empty(),
                stage
        );
    }

    private PixelShifts findPixelShifts(ImageMathParams params) {
        var executor = createScriptExecutor();
        var normalShifts = new TreeSet<Double>();
        var internalShifts = new TreeSet<Double>();
        requestedWaveLengths = new TreeSet<>();
        autoContinuum = false;
        for (var file : params.scriptFiles()) {
            if (Files.exists(file.toPath())) {
                try {
                    // Inject parameters specific to this file
                    var fileParams = params.parameterValues().get(file);
                    if (fileParams != null) {
                        for (var entry : fileParams.entrySet()) {
                            executor.putVariable(entry.getKey(), entry.getValue());
                        }
                    }

                    var result = executor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    internalShifts.addAll(result.internalShifts());
                    normalShifts.addAll(result.outputShifts());
                    requestedWaveLengths.addAll(result.requestedWavelenghts());
                    autoContinuum |= result.autoContinuum();
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
            }
        }

        return new PixelShifts(normalShifts.stream().toList(), internalShifts.stream().toList());
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
                        this.imageMathParams = params;
                        syncRepositoryScriptSelection();
                        updateScriptParametersUI();

                        determineCurrentMode();
                        updateCurrentModeDisplay();
                    }));
        }
    }

    private void updateScriptParametersUI() {
        scriptParametersContainer.getChildren().clear();
        scriptParameterValidationStates.clear();
        updateOverallValidationState();

        var hasUserScripts = imageMathParams != null && !imageMathParams.scriptFiles().isEmpty();
        var hasRepositoryScripts = !selectedRepositoryScripts.isEmpty();

        if (!hasUserScripts && !hasRepositoryScripts) {
            scriptParametersSection.setVisible(false);
            scriptParametersSection.setManaged(false);
            return;
        }

        var currentLanguage = LocaleUtils.getConfiguredLocale().getLanguage();
        var hasAnyParameters = false;

        var allRepositoryScriptPaths = selectedRepositoryScripts.stream()
                .map(RemoteScript::localPath)
                .map(Path::toAbsolutePath)
                .collect(Collectors.toSet());

        if (hasUserScripts) {
            for (var scriptFile : imageMathParams.scriptFiles()) {
                if (!allRepositoryScriptPaths.contains(scriptFile.toPath().toAbsolutePath())) {
                    hasAnyParameters |= processScriptParameters(scriptFile.toPath(), scriptFile, currentLanguage);
                }
            }
        }

        if (hasRepositoryScripts) {
            for (var script : selectedRepositoryScripts) {
                var scriptFile = script.localPath().toFile();
                hasAnyParameters |= processScriptParameters(script.localPath(), scriptFile, currentLanguage);
            }
        }

        if (hasAnyParameters) {
            scriptParametersSection.setVisible(true);
            scriptParametersSection.setManaged(true);
        } else {
            scriptParametersSection.setVisible(false);
            scriptParametersSection.setManaged(false);
        }
    }

    private boolean processScriptParameters(Path scriptPath, File scriptFile, String currentLanguage) {
        if (!Files.exists(scriptPath)) {
            return false;
        }

        try {
            var result = ScriptParameterExtractor.extractParameters(scriptPath);
            if (result.getParameters().isEmpty()) {
                return false;
            }

            var scriptTitle = result.getDisplayTitle(currentLanguage);
            var headerText = I18N.string(JSolEx.class, "image-selection", "script.parameters.header");
            if (headerText == null || headerText.trim().isEmpty()) {
                headerText = "Parameters for %s";
            }
            var formattedHeader = String.format(headerText, scriptTitle);

            var headerLabel = new Label(formattedHeader);
            headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 0 5 30;");
            scriptParametersContainer.getChildren().add(headerLabel);

            if (!result.isVersionSupported()) {
                var warningText = I18N.string(JSolEx.class, "image-selection", "script.version.warning");
                var formattedWarning = String.format(warningText, result.getRequiredVersion(), VersionUtil.getVersion());

                var warningLabel = new Label(formattedWarning);
                warningLabel.setStyle("-fx-text-fill: #d73027; -fx-font-weight: bold; -fx-padding: 5 0 5 0; -fx-wrap-text: true;");
                scriptParametersContainer.getChildren().add(warningLabel);
            }

            var grid = createGrid();

            Map<String, Object> savedFileParams = null;
            if (scriptFile != null && imageMathParams != null) {
                savedFileParams = imageMathParams.parameterValues().get(scriptFile);
            }

            var initialValues = new HashMap<String, Object>();
            for (var param : result.getParameters()) {
                var savedValue = savedFileParams != null ? savedFileParams.get(param.getName()) : null;
                var valueToUse = savedValue != null ? savedValue : param.getDefaultValue();
                if (valueToUse != null) {
                    initialValues.put(param.getName(), valueToUse);
                }
            }

            ScriptParameterUIBuilder.buildParameterGrid(
                grid,
                result.getParameters(),
                currentLanguage,
                initialValues,
                (paramName, isValid) -> {
                    var paramKey = (scriptFile != null ? scriptFile.getName() : "unknown") + ":" + paramName;
                    scriptParameterValidationStates.put(paramKey, isValid);
                    updateOverallValidationState();
                },
                (paramName, value) -> updateParameterInImageMathParams(scriptFile, paramName, value)
            );

            if (!result.getParameters().isEmpty()) {
                scriptParametersContainer.getChildren().add(grid);
            }

            return true;
        } catch (Exception e) {
            LOGGER.warn(message("script.parameters.extract.failed"), scriptPath.getFileName(), e);
            return false;
        }
    }

    private void updateParameterInImageMathParams(File scriptFile, String paramName, Object value) {
        if (imageMathParams == null || imageMathParams.equals(ImageMathParams.NONE)) {
            return;
        }

        var currentFiles = new ArrayList<>(imageMathParams.scriptFiles());
        var currentParams = new HashMap<>(imageMathParams.parameterValues());

        var fileParams = currentParams.computeIfAbsent(scriptFile, k -> new HashMap<>());
        if (fileParams instanceof HashMap) {
            ((HashMap<String, Object>) fileParams).put(paramName, value);
        } else {
            var mutableParams = new HashMap<>(fileParams);
            mutableParams.put(paramName, value);
            currentParams.put(scriptFile, mutableParams);
        }

        imageMathParams = new ImageMathParams(currentFiles, currentParams);
    }

    /**
     * Sets the controller for this panel.
     * @param controller the controller
     */
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }

    /**
     * Sets the stage for this panel.
     * @param stage the stage
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Sets the host services for this panel.
     * @param hostServices the host services
     */
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /**
     * Sets whether batch mode is enabled.
     * @param batchMode true if batch mode is enabled
     */
    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }

    /**
     * Returns the property indicating whether all script parameters are valid.
     * @return the validation property
     */
    public BooleanProperty allScriptParametersValidProperty() {
        return allScriptParametersValid;
    }

    private void updateOverallValidationState() {
        boolean allValid = scriptParameterValidationStates.isEmpty() ||
                          scriptParameterValidationStates.values().stream().allMatch(Boolean::booleanValue);
        allScriptParametersValid.set(allValid);
    }

    /**
     * Loads the quick mode preset selection.
     */
    public void loadQuickModeSelection() {
        var debugWasSelected = debug.isSelected();
        selectAllExceptDebug(false);
        raw.setSelected(true);
        reconstruction.setSelected(true);
        geometryCorrected.setSelected(true);
        geometryCorrectedStretched.setSelected(true);
        debug.setSelected(debugWasSelected);
        applyAutomaticScripts.setSelected(false);
        imageMathParams = ImageMathParams.NONE;
        updateScriptLabel();
        determineCurrentMode();
        updateCurrentModeDisplay();
    }

    /**
     * Loads the full mode preset selection.
     */
    public void loadFullModeSelection() {
        var debugWasSelected = debug.isSelected();
        var scriptsWereSelected = applyAutomaticScripts.isSelected();
        var scriptsWereLoaded = imageMathParams;
        selectAllExceptDebug(true);
        debug.setSelected(debugWasSelected);
        applyAutomaticScripts.setSelected(scriptsWereSelected);
        imageMathParams = scriptsWereLoaded;
        determineCurrentMode();
        updateCurrentModeDisplay();
    }

    /**
     * Loads image selection data from process parameters.
     * @param processParams the process parameters to load from
     */
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
        applyAutomaticScripts.setSelected(requestedImages.applyAutomaticScripts());

        autoContinuum = requestedImages.autoContinuum();

        var imageMathParams = requestedImages.mathImages();
        if (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
            this.imageMathParams = imageMathParams;
            syncRepositoryScriptSelection();
            updateScriptLabel();
            updateScriptParametersUI();
        } else {
            this.imageMathParams = null;
            updateScriptLabel();
            updateScriptParametersUI();
        }

        determineCurrentMode();
        updateCurrentModeDisplay();

    }

    /**
     * Returns the requested images based on current selection.
     * @return the requested images
     */
    public RequestedImages getRequestedImages() {
        Set<GeneratedImageKind> images = EnumSet.noneOf(GeneratedImageKind.class);

        if (raw.isSelected()) {
            images.add(GeneratedImageKind.RAW);
        }
        if (technicalCard.isSelected()) {
            images.add(GeneratedImageKind.TECHNICAL_CARD);
        }
        if (geometryCorrected.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED);
        }
        if (geometryCorrectedStretched.isSelected()) {
            images.add(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED);
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
        if (reconstruction.isSelected()) {
            images.add(GeneratedImageKind.RECONSTRUCTION);
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

        var normalShifts = new ArrayList<>(calculatePixelShiftsForSelectedImages());
        var internalShiftsSet = new HashSet<>(calculateInternalPixelShiftsForSelectedImages());

        var finalImageMathParams = ImageMathParams.NONE;
        if (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
            finalImageMathParams = imageMathParams;

            var scriptPixelShifts = findPixelShifts(finalImageMathParams);
            normalShifts.addAll(scriptPixelShifts.normalShifts());
            internalShiftsSet.addAll(scriptPixelShifts.internalShifts());
        }

        normalShifts.forEach(internalShiftsSet::remove);
        
        return new RequestedImages(
            images,
            normalShifts.stream().distinct().toList(),
            internalShiftsSet,
            requestedWaveLengths == null ? Set.of() : Collections.unmodifiableSet(requestedWaveLengths),
            finalImageMathParams,
            autoContinuum,
            applyAutomaticScripts.isSelected()
        );
    }

    /**
     * Returns whether custom mode is active.
     * @return true if custom mode is active
     */
    public boolean isCustomMode() {
        return currentMode == SelectionMode.CUSTOM || 
               (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) ||
               applyAutomaticScripts.isSelected();
    }

    /**
     * Returns whether custom image selection is active.
     * @return true if custom image selection is active
     */
    public boolean hasCustomImageSelection() {
        return currentMode == SelectionMode.CUSTOM;
    }

    /**
     * Returns whether custom scripts are loaded.
     * @return true if custom scripts are loaded
     */
    public boolean hasCustomScripts() {
        return imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE);
    }

    /**
     * Returns whether automatic scripts are enabled.
     * @return true if automatic scripts are enabled
     */
    public boolean hasAutomaticScripts() {
        return applyAutomaticScripts.isSelected();
    }

    private void updateScriptLabel() {
        if (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
            var files = imageMathParams.scriptFiles();
            if (!files.isEmpty()) {
                var allRepositoryScriptPaths = new HashSet<Path>();
                for (var node : repositoryScriptsContainer.getChildren()) {
                    var checkBox = getCheckBoxFromNode(node);
                    if (checkBox != null) {
                        var script = (RemoteScript) checkBox.getUserData();
                        if (script != null) {
                            allRepositoryScriptPaths.add(script.localPath().toAbsolutePath());
                        }
                    }
                }

                var scriptNames = files.stream()
                        .filter(file -> !allRepositoryScriptPaths.contains(file.toPath().toAbsolutePath()))
                        .map(File::getName)
                        .toList();

                if (!scriptNames.isEmpty()) {
                    scriptLabel.setText(String.join(", ", scriptNames));
                    return;
                }
            }
        }

        scriptLabel.setText(I18N.string(JSolEx.class, "process-params", "no.script.loaded"));
    }

    private CheckBox getCheckBoxFromNode(Node node) {
        if (node instanceof CheckBox checkBox) {
            return checkBox;
        } else if (node instanceof HBox hbox) {
            for (var child : hbox.getChildren()) {
                if (child instanceof CheckBox checkBox) {
                    return checkBox;
                }
            }
        }
        return null;
    }

    private void clearScripts() {
        imageMathParams = null;

        for (var node : repositoryScriptsContainer.getChildren()) {
            var checkBox = getCheckBoxFromNode(node);
            if (checkBox != null) {
                checkBox.setSelected(false);
            }
        }
        selectedRepositoryScripts.clear();

        updateScriptLabel();
        updateScriptParametersUI();
        determineCurrentMode();
        updateCurrentModeDisplay();
    }

    private enum SelectionMode {
        QUICK, FULL, CUSTOM
    }

    private record PixelShifts(List<Double> normalShifts, List<Double> internalShifts) {}

    private void determineCurrentMode() {
        if (fullModeCheckBoxes.stream().allMatch(CheckBox::isSelected)) {
            currentMode = SelectionMode.FULL;
        } else if (quickModeCheckBoxes.stream().allMatch(CheckBox::isSelected) && fullModeOnlyCheckBoxes.stream().noneMatch(CheckBox::isSelected)) {
            currentMode = SelectionMode.QUICK;
        } else {
            currentMode = SelectionMode.CUSTOM;
        }
    }

    private void saveCurrentSelectionAsPreset() {
        var dialog = new TextInputDialog();
        dialog.setTitle(I18N.string(JSolEx.class, "process-params", "preset.name.dialog.title"));
        dialog.setHeaderText(I18N.string(JSolEx.class, "process-params", "preset.name.dialog.header"));
        dialog.setContentText(I18N.string(JSolEx.class, "process-params", "preset.name.dialog.content"));
        dialog.initOwner(getScene().getWindow());

        var result = dialog.showAndWait();
        result.ifPresent(name -> {
            var trimmedName = name.trim();
            if (trimmedName.isEmpty()) {
                showError(I18N.string(JSolEx.class, "process-params", "preset.name.empty.error"));
                return;
            }

            if (UserPresetIO.presetExists(trimmedName)) {
                var confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle(I18N.string(JSolEx.class, "process-params", "preset.name.dialog.title"));
                confirmDialog.setHeaderText(I18N.string(JSolEx.class, "process-params", "preset.name.exists.error"));
                confirmDialog.initOwner(getScene().getWindow());

                var confirmResult = confirmDialog.showAndWait();
                if (confirmResult.orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }

            try {
                var currentImages = getCurrentSelectedImages();
                var currentMathImages = imageMathParams != null ? imageMathParams : ImageMathParams.NONE;
                var currentAutomaticScripts = applyAutomaticScripts.isSelected();

                var preset = new UserPreset(trimmedName, currentImages, currentMathImages, currentAutomaticScripts);
                UserPresetIO.savePreset(preset);

                loadUserPresets();
                showInfo(I18N.string(JSolEx.class, "process-params", "preset.saved.success"));
            } catch (Exception ex) {
                LOGGER.error(message("user.preset.save.failed"), ex);
                showError("Failed to save preset: " + ex.getMessage());
            }
        });
    }

    private Set<GeneratedImageKind> getCurrentSelectedImages() {
        var images = EnumSet.noneOf(GeneratedImageKind.class);

        if (raw.isSelected()) images.add(GeneratedImageKind.RAW);
        if (geometryCorrected.isSelected()) images.add(GeneratedImageKind.GEOMETRY_CORRECTED);
        if (geometryCorrectedStretched.isSelected()) images.add(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED);
        if (reconstruction.isSelected()) images.add(GeneratedImageKind.RECONSTRUCTION);
        if (technicalCard.isSelected()) images.add(GeneratedImageKind.TECHNICAL_CARD);
        if (colorized.isSelected()) images.add(GeneratedImageKind.COLORIZED);
        if (virtualEclipse.isSelected()) images.add(GeneratedImageKind.VIRTUAL_ECLIPSE);
        if (negative.isSelected()) images.add(GeneratedImageKind.NEGATIVE);
        if (mixed.isSelected()) images.add(GeneratedImageKind.MIXED);
        if (doppler.isSelected()) images.add(GeneratedImageKind.DOPPLER);
        if (dopplerEclipse.isSelected()) images.add(GeneratedImageKind.DOPPLER_ECLIPSE);
        if (continuum.isSelected()) images.add(GeneratedImageKind.CONTINUUM);
        if (redshift.isSelected()) images.add(GeneratedImageKind.REDSHIFT);
        if (activeRegions.isSelected()) images.add(GeneratedImageKind.ACTIVE_REGIONS);
        if (ellermanBombs.isSelected()) {
            images.add(GeneratedImageKind.ELLERMAN_BOMBS);
            images.add(GeneratedImageKind.FLARES);
        }
        if (debug.isSelected()) images.add(GeneratedImageKind.DEBUG);

        return images;
    }

    private void loadUserPresets() {
        userPresetsContainer.getChildren().clear();

        try {
            var presets = UserPresetIO.loadPresets();
            for (var preset : presets) {
                var loadButton = new Button(preset.displayName());
                loadButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "load.preset.tooltip")));
                loadButton.getStyleClass().add("custom-button");
                loadButton.setOnAction(e -> loadUserPreset(preset));
                loadButton.setMinWidth(Button.USE_PREF_SIZE);
                loadButton.setMaxWidth(Double.MAX_VALUE);

                var deleteButton = new Button("✕");
                deleteButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "delete.preset.tooltip")));
                deleteButton.getStyleClass().addAll("default-button", "small-button");
                deleteButton.setOnAction(e -> deleteUserPreset(preset.displayName()));
                deleteButton.setMinWidth(25);
                deleteButton.setMaxWidth(25);

                userPresetsContainer.getChildren().addAll(loadButton, deleteButton);
            }
        } catch (Exception ex) {
            LOGGER.error(message("user.preset.load.failed"), ex);
        }
        updateUserPresetsVisibility();
    }

    private void updateUserPresetsVisibility() {
        try {
            var presets = UserPresetIO.loadPresets();
            var hasPresets = !presets.isEmpty();

            userPresetsSection.setVisible(hasPresets);
            userPresetsSection.setManaged(hasPresets);
        } catch (Exception ex) {
            LOGGER.error(message("user.preset.visibility.update.failed"), ex);
        }
    }

    private void loadRepositoryScripts() {
        repositoryScriptsContainer.getChildren().clear();

        try {
            var configuration = Configuration.getInstance();
            var repositories = configuration.getScriptRepositories();

            if (repositories.isEmpty()) {
                repositoryScriptsSection.setVisible(false);
                repositoryScriptsSection.setManaged(false);
                return;
            }

            Set<Path> scriptPathsInParams;
            if (imageMathParams != null && !imageMathParams.scriptFiles().isEmpty()) {
                scriptPathsInParams = imageMathParams.scriptFiles().stream()
                        .map(File::toPath)
                        .map(Path::toAbsolutePath)
                        .collect(Collectors.toSet());
            } else {
                scriptPathsInParams = Collections.emptySet();
            }

            var currentLanguage = LocaleUtils.getConfiguredLocale().getLanguage();

            for (var repository : repositories) {
                if (!repository.enabled()) {
                    continue;
                }
                var scripts = repositoryManager.getLocalScripts(repository);
                for (var script : scripts) {
                    var checkBox = new CheckBox("[" + repository.name() + "] - " + script.title());
                    checkBox.setWrapText(true);

                    String description = null;
                    try {
                        if (Files.exists(script.localPath())) {
                            var extractionResult = ScriptParameterExtractor.extractParameters(script.localPath());
                            description = extractionResult.getDisplayDescription(currentLanguage);
                        }
                    } catch (Exception e) {
                        LOGGER.debug(JSolEx.message("debug.failed.extract.description"), script.title(), e);
                    }

                    checkBox.setUserData(script);

                    var scriptPath = script.localPath().toAbsolutePath();
                    var isSelected = scriptPathsInParams.contains(scriptPath);
                    if (isSelected) {
                        selectedRepositoryScripts.add(script);
                    }
                    checkBox.setSelected(isSelected);

                    checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
                        if (newValue) {
                            selectedRepositoryScripts.add(script);
                            addRepositoryScriptToImageMathParams(script);
                        } else {
                            selectedRepositoryScripts.remove(script);
                            removeRepositoryScriptFromImageMathParams(script);
                        }
                        updateScriptLabel();
                        updateScriptParametersUI();
                        determineCurrentMode();
                        updateCurrentModeDisplay();
                    });

                    if (description != null && !description.isEmpty()) {
                        var container = new HBox(5);
                        container.setAlignment(Pos.CENTER_LEFT);
                        container.getChildren().add(checkBox);

                        var helpIcon = new Label("?");
                        helpIcon.getStyleClass().addAll("help-icon");

                        var customTooltip = new CustomTooltip(description);
                        customTooltip.attachTo(helpIcon);

                        container.getChildren().add(helpIcon);
                        repositoryScriptsContainer.getChildren().add(container);
                    } else {
                        var fallbackTooltip = script.author() + " - v" + script.version();
                        checkBox.setTooltip(new Tooltip(fallbackTooltip));
                        repositoryScriptsContainer.getChildren().add(checkBox);
                    }
                }
            }

            var hasScripts = !repositoryScriptsContainer.getChildren().isEmpty();
            repositoryScriptsSection.setVisible(hasScripts);
            repositoryScriptsSection.setManaged(hasScripts);
        } catch (Exception ex) {
            LOGGER.error(message("repository.scripts.load.failed"), ex);
        }
    }

    private void addRepositoryScriptToImageMathParams(RemoteScript script) {
        var scriptFile = script.localPath().toFile();
        var currentFiles = new ArrayList<File>();
        var currentParams = new HashMap<File, Map<String, Object>>();

        if (imageMathParams != null && !imageMathParams.equals(ImageMathParams.NONE)) {
            currentFiles.addAll(imageMathParams.scriptFiles());
            currentParams.putAll(imageMathParams.parameterValues());
        }

        if (!currentFiles.contains(scriptFile)) {
            currentFiles.add(scriptFile);
            currentParams.put(scriptFile, Map.of());
        }

        imageMathParams = new ImageMathParams(currentFiles, currentParams);
    }

    private void removeRepositoryScriptFromImageMathParams(RemoteScript script) {
        if (imageMathParams == null || imageMathParams.equals(ImageMathParams.NONE)) {
            return;
        }

        var scriptFile = script.localPath().toFile();
        var currentFiles = new ArrayList<>(imageMathParams.scriptFiles());
        var currentParams = new HashMap<>(imageMathParams.parameterValues());

        currentFiles.remove(scriptFile);
        currentParams.remove(scriptFile);

        if (currentFiles.isEmpty()) {
            imageMathParams = null;
        } else {
            imageMathParams = new ImageMathParams(currentFiles, currentParams);
        }
    }

    private void syncRepositoryScriptSelection() {
        if (imageMathParams == null || imageMathParams.scriptFiles().isEmpty()) {
            updateScriptLabel();
            return;
        }

        var scriptPaths = imageMathParams.scriptFiles().stream()
                .map(File::toPath)
                .map(Path::toAbsolutePath)
                .collect(Collectors.toSet());

        for (var node : repositoryScriptsContainer.getChildren()) {
            var checkBox = getCheckBoxFromNode(node);
            if (checkBox != null) {
                var script = (RemoteScript) checkBox.getUserData();
                if (script != null) {
                    var scriptPath = script.localPath().toAbsolutePath();
                    if (scriptPaths.contains(scriptPath)) {
                        selectedRepositoryScripts.add(script);
                        checkBox.setSelected(true);
                    }
                }
            }
        }

        updateScriptLabel();
    }

    /**
     * Loads a user preset.
     * @param preset the preset to load
     */
    public void loadUserPreset(UserPreset preset) {
        try {
            // Clear all selections first
            selectAllExceptDebug(false);

            // Apply image selections from preset
            for (var imageKind : preset.selectedImages()) {
                switch (imageKind) {
                    case RAW -> raw.setSelected(true);
                    case GEOMETRY_CORRECTED -> geometryCorrected.setSelected(true);
                    case GEOMETRY_CORRECTED_PROCESSED -> geometryCorrectedStretched.setSelected(true);
                    case RECONSTRUCTION -> reconstruction.setSelected(true);
                    case TECHNICAL_CARD -> technicalCard.setSelected(true);
                    case COLORIZED -> colorized.setSelected(true);
                    case VIRTUAL_ECLIPSE -> virtualEclipse.setSelected(true);
                    case NEGATIVE -> negative.setSelected(true);
                    case MIXED -> mixed.setSelected(true);
                    case DOPPLER -> doppler.setSelected(true);
                    case DOPPLER_ECLIPSE -> dopplerEclipse.setSelected(true);
                    case CONTINUUM -> continuum.setSelected(true);
                    case REDSHIFT -> redshift.setSelected(true);
                    case ACTIVE_REGIONS -> activeRegions.setSelected(true);
                    case ELLERMAN_BOMBS -> ellermanBombs.setSelected(true);
                    case DEBUG -> debug.setSelected(true);
                }
            }

            imageMathParams = preset.mathImages();
            syncRepositoryScriptSelection();
            updateScriptLabel();
            updateScriptParametersUI();

            applyAutomaticScripts.setSelected(preset.applyAutomaticScripts());

            determineCurrentMode();
            updateCurrentModeDisplay();
        } catch (Exception ex) {
            LOGGER.error(message("user.preset.load.specific.failed"), preset.displayName(), ex);
            showError("Failed to load preset: " + ex.getMessage());
        }
    }

    private void deleteUserPreset(String presetName) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18N.string(JSolEx.class, "process-params", "preset.delete.confirm.title"));
        alert.setHeaderText(I18N.string(JSolEx.class, "process-params", "preset.delete.confirm.header").replace("{0}", presetName));
        alert.setContentText(I18N.string(JSolEx.class, "process-params", "preset.delete.confirm.content"));
        alert.initOwner(getScene().getWindow());

        var result = alert.showAndWait();
        if (result.orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                UserPresetIO.deletePreset(presetName);
                loadUserPresets();
            } catch (Exception ex) {
                LOGGER.error(message("user.preset.delete.failed"), presetName, ex);
                showError("Failed to delete preset: " + ex.getMessage());
            }
        }
    }

    private void showError(String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }

    private void showInfo(String message) {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }
}