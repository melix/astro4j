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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.AutoStretchParams;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement;
import me.champeau.a4j.jsolex.processing.params.DeconvolutionMode;
import me.champeau.a4j.jsolex.processing.params.EnhancementParams;
import me.champeau.a4j.jsolex.processing.params.JaggingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RichardsonLucyDeconvolutionParams;
import me.champeau.a4j.jsolex.processing.params.SharpeningMethod;
import me.champeau.a4j.jsolex.processing.params.SharpeningParams;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;
import me.champeau.a4j.jsolex.processing.sun.workflow.JaggingCorrection;
import me.champeau.a4j.math.image.Deconvolution;

import java.nio.file.Path;
import java.util.Locale;

public class ImageEnhancementPanel extends BaseParameterPanel {
    
    private ChoiceBox<ContrastEnhancement> contrastEnhancementChoice;
    private TextField autostretchGamma;
    private TextField bgThreshold;
    private CheckBox stretchProtus;
    private TextField protusStretchValue;
    private ChoiceBox<DeconvolutionMode> deconvolutionMode;
    private TextField rlRadius;
    private TextField rlSigma;
    private TextField rlIterations;
    private ChoiceBox<SharpeningMethod> sharpeningMethod;
    private TextField sharpeningRadius;
    private TextField sharpeningAmount;


    private VBox autostretchSection;
    private VBox claheSection;
    private VBox rlSection;
    private VBox sharpeningSection;
    
    private ChoiceBox<Integer> claheTileSize;
    private ChoiceBox<Integer> claheBins;
    private TextField claheClipping;
    
    private TextField flatFilePath;
    private Path selectedFlatFilePath;

    private TextField bandingCorrectionPasses;
    private TextField bandingCorrectionWidth;
    
    private CheckBox jaggingCorrection;
    private TextField jaggingCorrectionSigma;
    
    private ChoiceBox<FlatMode> flatMode;
    private TextField flatLoPercentile;
    private TextField flatHiPercentile;
    private TextField flatOrder;
    private VBox artificialFlatSection;
    private VBox realFlatSection;
    private Label flatModeHelp;
    
    private ProcessParamsController controller;
    
    public ImageEnhancementPanel() {
        getStyleClass().add("parameter-panel");
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        contrastEnhancementChoice = createChoiceBox();
        contrastEnhancementChoice.setItems(FXCollections.observableArrayList(ContrastEnhancement.values()));
        contrastEnhancementChoice.setValue(ContrastEnhancement.AUTOSTRETCH);
        contrastEnhancementChoice.setOnAction(e -> updateParameterVisibility());
        contrastEnhancementChoice.setConverter(new StringConverter<ContrastEnhancement>() {
            @Override
            public String toString(ContrastEnhancement technique) {
                return technique == null ? null : I18N.string(JSolEx.class, "messages", "contrast.enhancement." + technique.name());
            }
            @Override
            public ContrastEnhancement fromString(String string) {
                return ContrastEnhancement.valueOf(string);
            }
        });
        
        autostretchGamma = new TextField("2.2");
        bgThreshold = new TextField("0.1");
        stretchProtus = new CheckBox(I18N.string(JSolEx.class, "process-params", "protus.stretch.value"));
        protusStretchValue = new TextField("1.5");
        
        deconvolutionMode = createChoiceBox();
        deconvolutionMode.setItems(FXCollections.observableArrayList(DeconvolutionMode.values()));
        deconvolutionMode.setValue(DeconvolutionMode.NONE);
        deconvolutionMode.setOnAction(e -> updateParameterVisibility());
        deconvolutionMode.setConverter(new StringConverter<DeconvolutionMode>() {
            @Override
            public String toString(DeconvolutionMode mode) {
                return mode == null ? null : I18N.string(JSolEx.class, "messages", "deconvolution." + mode.name());
            }
            @Override
            public DeconvolutionMode fromString(String string) {
                return DeconvolutionMode.valueOf(string);
            }
        });
        
        rlRadius = new TextField("2.5");
        rlSigma = new TextField("2.5");
        rlIterations = new TextField("5");
        
        sharpeningMethod = createChoiceBox();
        sharpeningMethod.setItems(FXCollections.observableArrayList(SharpeningMethod.values()));
        sharpeningMethod.setValue(SharpeningMethod.NONE);
        sharpeningMethod.setOnAction(e -> updateParameterVisibility());
        sharpeningMethod.setConverter(new StringConverter<SharpeningMethod>() {
            @Override
            public String toString(SharpeningMethod method) {
                return method == null ? null : I18N.string(JSolEx.class, "process-params", "sharpeningmethod." + method.name().toLowerCase(Locale.US));
            }
            @Override
            public SharpeningMethod fromString(String string) {
                return SharpeningMethod.valueOf(string);
            }
        });
        
        sharpeningRadius = new TextField("1.0");
        sharpeningAmount = new TextField("1.0");
        
        
        claheTileSize = createChoiceBox();
        claheTileSize.setItems(FXCollections.observableArrayList(8, 16, 32, 64, 128, 256, 512, 1024));
        claheTileSize.setValue(ClaheStrategy.DEFAULT_TILE_SIZE);
        claheTileSize.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "tile.size")));
        
        claheBins = createChoiceBox();
        claheBins.setItems(FXCollections.observableArrayList(32, 64, 128, 256, 512, 1024));
        claheBins.setValue(ClaheStrategy.DEFAULT_BINS);
        claheBins.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "bins")));
        
        claheClipping = new TextField(String.valueOf(ClaheStrategy.DEFAULT_CLIP));
        claheClipping.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "clipping")));
        
        claheTileSize.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && claheBins.getValue() != null && claheBins.getValue() > newValue * newValue) {
                claheBins.setValue(newValue * newValue);
            }
        });
        
        claheBins.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && claheTileSize.getValue() != null && newValue > claheTileSize.getValue() * claheTileSize.getValue()) {
                claheBins.setValue(oldValue);
            }
        });
        
        flatFilePath = new TextField();
        flatFilePath.setPromptText(I18N.string(JSolEx.class, "process-params", "choose.flat.file"));
        flatFilePath.setEditable(false);
        
        bandingCorrectionPasses = new TextField("1");
        bandingCorrectionWidth = new TextField("32");
        
        jaggingCorrection = new CheckBox(I18N.string(JSolEx.class, "process-params", "jagging.correction"));
        jaggingCorrection.setOnAction(e -> updateParameterVisibility());
        jaggingCorrectionSigma = new TextField("2.5");
        jaggingCorrectionSigma.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "jagging.correction.sigma.tooltip")));
        
        flatMode = createChoiceBox();
        flatMode.setItems(FXCollections.observableArrayList(FlatMode.values()));
        flatMode.setValue(FlatMode.NONE);
        flatMode.setOnAction(e -> updateParameterVisibility());
        flatMode.setConverter(new StringConverter<FlatMode>() {
            @Override
            public String toString(FlatMode mode) {
                return mode == null ? null : I18N.string(JSolEx.class, "process-params", "flatmode." + mode.name().toLowerCase(Locale.US));
            }
            @Override
            public FlatMode fromString(String string) {
                return FlatMode.valueOf(string);
            }
        });
        
        flatLoPercentile = new TextField("0.1");
        flatHiPercentile = new TextField("0.95");
        flatOrder = new TextField("2");
        
        flatModeHelp = new Label(I18N.string(JSolEx.class, "process-params", "flat.mode.help"));
        flatModeHelp.getStyleClass().add("field-description");
        flatModeHelp.setWrapText(true);
    }
    
    private void setupLayout() {
        setPadding(new Insets(24));
        setSpacing(24);
        
        var contrastSection = createSection("contrast.enhancement.technique");
        var contrastGrid = createGrid();
        
        addGridRow(contrastGrid, 0, I18N.string(JSolEx.class, "process-params", "contrast.enhancement.technique") + ":", contrastEnhancementChoice);
        
        autostretchSection = new VBox(8);
        autostretchSection.getStyleClass().add("subsection");

        var autostretchGrid = createGrid();
        addGridRow(autostretchGrid, 0, I18N.string(JSolEx.class, "process-params", "gamma") + ":", autostretchGamma);
        addGridRow(autostretchGrid, 1, I18N.string(JSolEx.class, "process-params", "background.threshold") + ":", bgThreshold, "background.threshold.tooltip");
        autostretchGrid.add(stretchProtus, 0, 2, 2, 1);
        addGridRow(autostretchGrid, 3, I18N.string(JSolEx.class, "process-params", "protus.stretch.value") + ":", protusStretchValue, "protus.stretch.value.tooltip");
        autostretchSection.getChildren().add(autostretchGrid);
        
        claheSection = new VBox(8);
        claheSection.getStyleClass().add("subsection");

        var claheGrid = createGrid();
        addGridRow(claheGrid, 0, I18N.string(JSolEx.class, "process-params", "tile.size") + ":", claheTileSize);
        addGridRow(claheGrid, 1, I18N.string(JSolEx.class, "process-params", "bins") + ":", claheBins);
        addGridRow(claheGrid, 2, I18N.string(JSolEx.class, "process-params", "clipping") + ":", claheClipping);
        claheSection.getChildren().add(claheGrid);
        claheSection.setVisible(false);
        claheSection.setManaged(false);
        
        contrastGrid.add(autostretchSection, 0, 1, 2, 1);
        contrastGrid.add(claheSection, 0, 2, 2, 1);
        
        contrastSection.getChildren().add(contrastGrid);
        
        var bandingSection = createSection("banding.correction");
        var bandingGrid = createGrid();
        addGridRow(bandingGrid, 0, I18N.string(JSolEx.class, "process-params", "banding.correction.passes") + ":", bandingCorrectionPasses, "banding.correction.passes.tooltip");
        addGridRow(bandingGrid, 1, I18N.string(JSolEx.class, "process-params", "banding.correction.width") + ":", bandingCorrectionWidth, "banding.correction.width.tooltip");
        bandingSection.getChildren().add(bandingGrid);
        
        var deconvolutionSharpeningSection = createSection(I18N.string(JSolEx.class, "process-params", "sharpening.method") + " & " + I18N.string(JSolEx.class, "process-params", "deconvolution.mode"));
        var deconvolutionGrid = createGrid();
        
        addGridRow(deconvolutionGrid, 0, I18N.string(JSolEx.class, "process-params", "deconvolution.mode") + ":", deconvolutionMode, "deconvolution.mode.tooltip");
        
        rlSection = new VBox(8);
        rlSection.getStyleClass().add("subsection");

        var rlGrid = createGrid();
        addGridRow(rlGrid, 0, I18N.string(JSolEx.class, "process-params", "rl.psf.radius") + ":", rlRadius);
        addGridRow(rlGrid, 1, I18N.string(JSolEx.class, "process-params", "rl.psf.sigma") + ":", rlSigma);
        addGridRow(rlGrid, 2, I18N.string(JSolEx.class, "process-params", "rl.iterations") + ":", rlIterations);
        rlSection.getChildren().add(rlGrid);
        rlSection.setVisible(false);
        rlSection.setManaged(false);
        
        deconvolutionGrid.add(rlSection, 0, 1, 2, 1);
        
        addGridRow(deconvolutionGrid, 2, I18N.string(JSolEx.class, "process-params", "sharpening.method") + ":", sharpeningMethod, "sharpening.method.tooltip");
        
        sharpeningSection = new VBox(8);
        sharpeningSection.getStyleClass().add("subsection");

        var sharpeningGrid = createGrid();
        addGridRow(sharpeningGrid, 0, I18N.string(JSolEx.class, "process-params", "sharpening.kernel.size") + ":", sharpeningRadius);
        addGridRow(sharpeningGrid, 1, I18N.string(JSolEx.class, "process-params", "sharpening.strength") + ":", sharpeningAmount);
        sharpeningSection.getChildren().add(sharpeningGrid);
        sharpeningSection.setVisible(false);
        sharpeningSection.setManaged(false);
        
        deconvolutionGrid.add(sharpeningSection, 0, 3, 2, 1);
        
        deconvolutionSharpeningSection.getChildren().add(deconvolutionGrid);
        
        
        var flatSection = createSection("flat");
        var flatGrid = createGrid();
        
        addGridRow(flatGrid, 0, I18N.string(JSolEx.class, "process-params", "artificial.flat") + ":", flatMode);
        
        flatGrid.add(flatModeHelp, 0, 1, 2, 1);
        
        artificialFlatSection = new VBox(8);
        artificialFlatSection.getStyleClass().add("subsection");

        var artificialFlatGrid = createGrid();
        addGridRow(artificialFlatGrid, 0, I18N.string(JSolEx.class, "process-params", "flat.percentile.lo") + ":", flatLoPercentile, "flat.percentile.lo.tooltip");
        addGridRow(artificialFlatGrid, 1, I18N.string(JSolEx.class, "process-params", "flat.percentile.hi") + ":", flatHiPercentile, "flat.percentile.hi.tooltip");
        addGridRow(artificialFlatGrid, 2, I18N.string(JSolEx.class, "process-params", "flat.percentile.order") + ":", flatOrder, "flat.percentile.order.tooltip");
        artificialFlatSection.getChildren().add(artificialFlatGrid);
        artificialFlatSection.setVisible(false);
        artificialFlatSection.setManaged(false);
        
        realFlatSection = new VBox(8);
        realFlatSection.getStyleClass().add("subsection");
        var realFlatGrid = createGrid();
        addGridRow(realFlatGrid, 0, "Flat file:", createFlatFileBox());
        realFlatSection.getChildren().add(realFlatGrid);
        realFlatSection.setVisible(false);
        realFlatSection.setManaged(false);
        
        flatGrid.add(artificialFlatSection, 0, 2, 2, 1);
        flatGrid.add(realFlatSection, 0, 3, 2, 1);
        
        flatSection.getChildren().add(flatGrid);
        
        var jaggingSection = createSection("jagging.correction");
        var jaggingGrid = createGrid();
        jaggingGrid.add(jaggingCorrection, 0, 0, 3, 1);
        addGridRow(jaggingGrid, 1, I18N.string(JSolEx.class, "process-params", "jagging.correction.sigma") + ":", jaggingCorrectionSigma);
        jaggingSection.getChildren().add(jaggingGrid);
        
        getChildren().addAll(contrastSection, bandingSection, deconvolutionSharpeningSection, flatSection, jaggingSection);
        
        updateParameterVisibility();
    }
    
    
    private HBox createFlatFileBox() {
        var box = createHBox();

        var selectButton = new Button(I18N.string(JSolEx.class, "process-params", "choose.flat.file"));
        selectButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "flat.mode.help")));
        selectButton.getStyleClass().add("cancel-button");
        selectButton.setOnAction(e -> selectFlatFile());
        
        box.getChildren().addAll(flatFilePath, selectButton);
        return box;
    }
    
    private void selectFlatFile() {
        if (controller != null) {
            controller.selectFlatFile();
        }
    }
    
    public void loadData(ProcessParams params) {
        contrastEnhancementChoice.setValue(params.contrastEnhancement());
        autostretchGamma.setText(String.valueOf(params.autoStretchParams().gamma()));
        bgThreshold.setText(String.valueOf(params.autoStretchParams().bgThreshold()));
        stretchProtus.setSelected(params.autoStretchParams().protusStretch() > 1.0);
        protusStretchValue.setText(String.valueOf(params.autoStretchParams().protusStretch()));
        
        var claheParams = params.claheParams();
        claheTileSize.setValue(claheParams.tileSize());
        claheBins.setValue(claheParams.bins());
        claheClipping.setText(String.valueOf(claheParams.clipping()));
        
        
        var bandingParams = params.bandingCorrectionParams();
        bandingCorrectionPasses.setText(String.valueOf(bandingParams.passes()));
        bandingCorrectionWidth.setText(String.valueOf(bandingParams.width()));
        
        var jaggingParams = params.enhancementParams().jaggingCorrectionParams();
        jaggingCorrection.setSelected(jaggingParams.enabled());
        jaggingCorrectionSigma.setText(String.valueOf(jaggingParams.sigma()));
        
        var enhancementParams = params.enhancementParams();
        if (enhancementParams.artificialFlatCorrection()) {
            flatMode.setValue(FlatMode.ARTIFICIAL);
        } else if (enhancementParams.masterFlatFile() != null) {
            flatMode.setValue(FlatMode.REAL);
            updateFlatFile(enhancementParams.masterFlatFile());
        } else {
            flatMode.setValue(FlatMode.NONE);
        }
        
        flatLoPercentile.setText(String.valueOf(enhancementParams.artificialFlatCorrectionLoPercentile()));
        flatHiPercentile.setText(String.valueOf(enhancementParams.artificialFlatCorrectionHiPercentile()));
        flatOrder.setText(String.valueOf(enhancementParams.artificialFlatCorrectionOrder()));
        
        deconvolutionMode.setValue(params.geometryParams().deconvolutionMode());
        
        var rlParamsOpt = params.geometryParams().richardsonLucyDeconvolutionParams();
        if (rlParamsOpt.isPresent()) {
            var rlParams = rlParamsOpt.get();
            rlRadius.setText(String.valueOf(rlParams.radius()));
            rlSigma.setText(String.valueOf(rlParams.sigma()));
            rlIterations.setText(String.valueOf(rlParams.iterations()));
        } else {
            rlRadius.setText("2.5");
            rlSigma.setText("2.5");
            rlIterations.setText("5");
        }
        
        var sharpeningParams = params.enhancementParams().sharpeningParams();
        sharpeningMethod.setValue(sharpeningParams.method());
        
        switch (sharpeningParams) {
            case SharpeningParams.Sharpen sharpen -> {
                sharpeningRadius.setText(String.valueOf(sharpen.kernelSize()));
                sharpeningAmount.setText("1.0");
            }
            case SharpeningParams.UnsharpMask unsharp -> {
                sharpeningRadius.setText(String.valueOf(unsharp.kernelSize()));
                sharpeningAmount.setText(String.valueOf(unsharp.strength()));
            }
            case SharpeningParams.None none -> {
                sharpeningRadius.setText("1.0");
                sharpeningAmount.setText("1.0");
            }
        }
    }
    
    public ContrastEnhancement getContrastEnhancement() {
        return contrastEnhancementChoice.getValue();
    }
    
    public AutoStretchParams getAutoStretchParams() {
        var gamma = parseDouble(autostretchGamma.getText(), AutohistogramStrategy.DEFAULT_GAMMA);
        var bgThresholdValue = parseDouble(bgThreshold.getText(), AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD);
        var protusStretch = stretchProtus.isSelected() ? parseDouble(protusStretchValue.getText(), AutohistogramStrategy.DEFAULT_PROM_STRETCH) : 0;
        
        return new AutoStretchParams(gamma, bgThresholdValue, protusStretch);
    }
    
    public ClaheParams getClaheParams() {
        var tileSize = claheTileSize.getValue() != null ? claheTileSize.getValue() : ClaheStrategy.DEFAULT_TILE_SIZE;
        var bins = claheBins.getValue() != null ? claheBins.getValue() : ClaheStrategy.DEFAULT_BINS;
        var clipping = parseDouble(claheClipping.getText(), ClaheStrategy.DEFAULT_CLIP);
        
        return new ClaheParams(tileSize, bins, clipping);
    }
    
    
    private void updateParameterVisibility() {
        var selectedEnhancement = contrastEnhancementChoice.getValue();
        
        if (selectedEnhancement != null) {
            switch (selectedEnhancement) {
                case AUTOSTRETCH:
                    autostretchSection.setVisible(true);
                    autostretchSection.setManaged(true);
                    claheSection.setVisible(false);
                    claheSection.setManaged(false);
                    break;
                case CLAHE:
                    autostretchSection.setVisible(false);
                    autostretchSection.setManaged(false);
                    claheSection.setVisible(true);
                    claheSection.setManaged(true);
                    break;
                default:
                    autostretchSection.setVisible(false);
                    autostretchSection.setManaged(false);
                    claheSection.setVisible(false);
                    claheSection.setManaged(false);
                    break;
            }
        }
        
        var selectedDeconvolution = deconvolutionMode.getValue();
        if (selectedDeconvolution != null && rlSection != null) {
            var showRL = selectedDeconvolution == DeconvolutionMode.RICHARDSON_LUCY;
            rlSection.setVisible(showRL);
            rlSection.setManaged(showRL);
        }
        
        var selectedSharpening = sharpeningMethod.getValue();
        if (selectedSharpening != null && sharpeningSection != null) {
            var showSharpening = selectedSharpening == SharpeningMethod.UNSHARP_MASK;
            sharpeningSection.setVisible(showSharpening);
            sharpeningSection.setManaged(showSharpening);
        }
        
        if (jaggingCorrectionSigma != null) {
            jaggingCorrectionSigma.setDisable(!jaggingCorrection.isSelected());
        }
        
        var selectedFlatMode = flatMode.getValue();
        if (selectedFlatMode != null && artificialFlatSection != null && realFlatSection != null && flatModeHelp != null) {
            switch (selectedFlatMode) {
                case NONE:
                    artificialFlatSection.setVisible(false);
                    artificialFlatSection.setManaged(false);
                    realFlatSection.setVisible(false);
                    realFlatSection.setManaged(false);
                    flatModeHelp.setVisible(true);
                    flatModeHelp.setManaged(true);
                    break;
                case ARTIFICIAL:
                    artificialFlatSection.setVisible(true);
                    artificialFlatSection.setManaged(true);
                    realFlatSection.setVisible(false);
                    realFlatSection.setManaged(false);
                    flatModeHelp.setVisible(false);
                    flatModeHelp.setManaged(false);
                    break;
                case REAL:
                    artificialFlatSection.setVisible(false);
                    artificialFlatSection.setManaged(false);
                    realFlatSection.setVisible(true);
                    realFlatSection.setManaged(true);
                    flatModeHelp.setVisible(false);
                    flatModeHelp.setManaged(false);
                    break;
            }
        }
    }
    
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    public void updateFlatFile(Path flatPath) {
        selectedFlatFilePath = flatPath;
        if (flatPath != null) {
            flatFilePath.setText(flatPath.getFileName().toString());
        } else {
            flatFilePath.setText("");
        }
    }
    
    public BandingCorrectionParams getBandingCorrectionParams() {
        var passes = parseInt(bandingCorrectionPasses.getText(), BandingReduction.DEFAULT_PASS_COUNT);
        var width = parseInt(bandingCorrectionWidth.getText(), BandingReduction.DEFAULT_BAND_SIZE);
        return new BandingCorrectionParams(passes, width);
    }
    
    public DeconvolutionMode getDeconvolutionMode() {
        return deconvolutionMode.getValue();
    }
    
    public RichardsonLucyDeconvolutionParams getRichardsonLucyParams() {
        return new RichardsonLucyDeconvolutionParams(
            parseDouble(rlRadius.getText(), Deconvolution.DEFAULT_RADIUS),
            parseDouble(rlSigma.getText(), Deconvolution.DEFAULT_SIGMA),
            parseInt(rlIterations.getText(), Deconvolution.DEFAULT_ITERATIONS)
        );
    }
    
    public EnhancementParams getEnhancementParams() {
        var flatMode = this.flatMode.getValue();
        boolean artificialFlat = flatMode == FlatMode.ARTIFICIAL;
        Path masterFlatFile = flatMode == FlatMode.REAL ? selectedFlatFilePath : null;
        
        var jaggingParams = new JaggingCorrectionParams(
            jaggingCorrection.isSelected(),
            parseDouble(jaggingCorrectionSigma.getText(), JaggingCorrection.DEFAULT_SIGMA)
        );
        
        return new EnhancementParams(
            artificialFlat,
            parseDouble(flatLoPercentile.getText(), FlatCorrection.DEFAULT_LO_PERCENTILE),
            parseDouble(flatHiPercentile.getText(), FlatCorrection.DEFAULT_HI_PERCENTILE),
            parseInt(flatOrder.getText(), FlatCorrection.DEFAULT_ORDER),
            masterFlatFile,
            jaggingParams,
            getSharpeningParams()
        );
    }
    
    public SharpeningParams getSharpeningParams() {
        var method = sharpeningMethod.getValue();
        if (method == null || method == SharpeningMethod.NONE) {
            return SharpeningParams.none();
        }

        var kernelSize = parseInt(sharpeningRadius.getText(), 3);
        
        return switch (method) {
            case SHARPEN -> SharpeningParams.sharpen(kernelSize);
            case UNSHARP_MASK -> {
                var strength = parseDouble(sharpeningAmount.getText(), 1.0);
                yield SharpeningParams.unsharpMask(kernelSize, strength);
            }
            case NONE -> SharpeningParams.none();
        };
    }

    
    private enum FlatMode {
        NONE,
        ARTIFICIAL,
        REAL;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "process-params", "flatmode." + name().toLowerCase(Locale.US));
        }
    }
}