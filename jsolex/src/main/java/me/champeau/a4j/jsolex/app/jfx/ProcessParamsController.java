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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.VideoParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;


public class ProcessParamsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessParamsController.class);

    private Stage stage;
    private NavigationSidebar navigationSidebar;
    private BreadcrumbNavigation breadcrumbNav;
    private StackPane contentArea;
    private BorderPane root;
    private ProcessParams processParams;
    private boolean completed = false;
    private HostServices hostServices;
    private File serFile;
    private Header header;
    private boolean batchMode;
    private ProgressOperation progressOperation;
    private Broadcaster broadcaster;
    private final List<Stage> popups = new CopyOnWriteArrayList<>();

    private ProcessingParametersPanel processingPanel;
    private ImageEnhancementPanel enhancementPanel;
    private ObservationDetailsPanel observationPanel;
    private AdvancedParametersPanel advancedPanel;
    private OutputConfigurationPanel outputPanel;
    private ImageSelectionPanel imageSelectionPanel;
    private Button processSelectedButton;

    public void setup(Stage stage,
                      ProgressOperation progressOperation,
                      Broadcaster broadcaster,
                      File serFile,
                      Header header,
                      CaptureSoftwareMetadataHelper.CaptureMetadata metadata,
                      boolean batchMode,
                      HostServices hostServices) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.serFile = serFile;
        this.header = header;
        this.batchMode = batchMode;
        this.progressOperation = progressOperation;
        this.broadcaster = broadcaster;
        this.processParams = ProcessParams.loadDefaults();

        initializeComponents();
        setupNavigation();
        setupLayout();
        loadInitialData(serFile, header, metadata, batchMode);
        updateButtonsVisibility();

        if (!navigationSidebar.getNavigationItems().isEmpty()) {
            var firstItem = navigationSidebar.getNavigationItems().getFirst();
            onNavigationItemSelected(firstItem);
            navigationSidebar.selectItem(firstItem);
        }

        stage.setOnHidden(e -> {
            if (!completed) {
                processParams = null;
            }
            closePopups();
            popups.clear();
        });
    }

    private void initializeComponents() {
        navigationSidebar = new NavigationSidebar();
        breadcrumbNav = new BreadcrumbNavigation();
        contentArea = new StackPane();
        root = new BorderPane();

        processingPanel = new ProcessingParametersPanel();
        enhancementPanel = new ImageEnhancementPanel();
        observationPanel = new ObservationDetailsPanel(header);
        advancedPanel = new AdvancedParametersPanel();
        outputPanel = new OutputConfigurationPanel();
        imageSelectionPanel = new ImageSelectionPanel();

        processingPanel.setController(this);
        if (header != null) {
            processingPanel.setSourceWidth(header.geometry().width());
        }
        enhancementPanel.setController(this);
        observationPanel.setController(this);
        advancedPanel.setController(this);
        outputPanel.setController(this);
        imageSelectionPanel.setController(this);
        imageSelectionPanel.setHostServices(hostServices);
        imageSelectionPanel.setBatchMode(batchMode);
        imageSelectionPanel.setStage(stage);
        
    }
    

    private void setupNavigation() {
        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "processing",
                I18N.string(JSolEx.class, "process-params", "process.parameters"),
                "⚙",
                createScrollableContent(processingPanel),
                I18N.string(JSolEx.class, "process-params", "process.parameters.details")
        ));

        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "enhancement",
                I18N.string(JSolEx.class, "process-params", "image.enhancement"),
                "★",
                createScrollableContent(enhancementPanel),
                I18N.string(JSolEx.class, "process-params","image.enhancement.details")
        ));

        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "observation",
                I18N.string(JSolEx.class, "process-params", "observation.details"),
                "●",
                createScrollableContent(observationPanel),
                I18N.string(JSolEx.class, "process-params", "observation.details.description")
        ));

        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "imageselection",
                I18N.string(JSolEx.class, "process-params", "image.selection"),
                "□",
                createScrollableContent(imageSelectionPanel),
                I18N.string(JSolEx.class, "process-params", "image.selection.details")
        ));

        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "advanced",
                I18N.string(JSolEx.class, "process-params", "advanced.process.params"),
                "▲",
                createScrollableContent(advancedPanel),
                I18N.string(JSolEx.class, "process-params", "advanced.process.params.details")
        ));

        navigationSidebar.addNavigationItem(new NavigationSidebar.NavigationItem(
                "output",
                I18N.string(JSolEx.class, "process-params", "outputs"),
                "▦",
                createScrollableContent(outputPanel),
                I18N.string(JSolEx.class, "process-params", "outputs.details")
        ));

        navigationSidebar.setOnItemSelected(this::onNavigationItemSelected);
    }

    private ScrollPane createScrollableContent(Node content) {
        var scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("content-scroll-pane");
        return scrollPane;
    }

    private void setupLayout() {
        var header = new VBox();
        header.getStyleClass().add("params-header");

        header.getChildren().addAll(breadcrumbNav);

        var mainContent = new HBox();
        mainContent.getChildren().addAll(navigationSidebar, contentArea);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        var buttonBar = createButtonBar();

        root.setTop(header);
        root.setCenter(mainContent);
        root.setBottom(buttonBar);

        root.getStyleClass().add("params-dialog");
    }

    private HBox createButtonBar() {
        var buttonContainer = new HBox();
        buttonContainer.setPadding(new Insets(12));

        var cancelButton = new Button(I18N.string(JSolEx.class, "process-params", "cancel"));
        cancelButton.getStyleClass().add("default-button");
        cancelButton.setMinWidth(80);
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> {
            processParams = null;
            stage.close();
        });

        Button quickButton = new Button(I18N.string(JSolEx.class, "process-params", "quick.mode"));
        quickButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "quick.mode.tooltip")));
        quickButton.getStyleClass().add("quick-button");
        quickButton.setMinWidth(120);
        quickButton.setPrefWidth(140);
        quickButton.setOnAction(e -> {
            imageSelectionPanel.loadQuickModeSelection();
            process();
        });

        Button fullButton = new Button(I18N.string(JSolEx.class, "process-params", "full.process"));
        fullButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "full.process.tooltip")));
        fullButton.getStyleClass().addAll("quick-button");
        fullButton.setMinWidth(160);
        fullButton.setPrefWidth(180);
        fullButton.setOnAction(e -> {
            imageSelectionPanel.loadFullModeSelection();
            process();
        });

        processSelectedButton = new Button(I18N.string(JSolEx.class, "process-params", "process.selected"));
        processSelectedButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "process.selected.tooltip")));
        processSelectedButton.getStyleClass().addAll("primary-button", "process-selected-button");
        processSelectedButton.setMinWidth(160);
        processSelectedButton.setPrefWidth(180);
        processSelectedButton.setOnAction(e -> process());
        processSelectedButton.setVisible(false);
        processSelectedButton.setManaged(false);

        // Create right container for action buttons  
        var rightContainer = new HBox(6);
        rightContainer.getChildren().addAll(quickButton, fullButton, processSelectedButton);
        
        // Create spacer that fills all available space
        var spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Add all elements to main container
        buttonContainer.getChildren().addAll(cancelButton, spacer, rightContainer);
        
        return buttonContainer;
    }

    private void onNavigationItemSelected(NavigationSidebar.NavigationItem item) {
        breadcrumbNav.clear();
        breadcrumbNav.addItem("root", I18N.string(JSolEx.class, "process-params", "root.process.params"), null);
        breadcrumbNav.addItem(item.getId(), item.getTitle(), null);

        contentArea.getChildren().clear();
        contentArea.getChildren().add(item.getContent());

        NavigationSidebar.createContentTransition(item.getContent()).play();
    }

    private void process() {
        // Check altazimuth mode warning
        var obsDetails = observationPanel.getObservationDetails();
        if (obsDetails.altAzMode()) {
            var coordinates = obsDetails.coordinates();
            if (coordinates == null || coordinates.a() == 0.0 || coordinates.b() == 0.0) {
                var alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.initOwner(stage);
                alert.setTitle(I18N.string(JSolEx.class, "process-params", "altazmode.warning.header"));
                alert.setContentText(I18N.string(JSolEx.class, "process-params", "altazmode.warning.content"));
                var result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        }
        
        collectParameters();
        ProcessParams.saveDefaults(processParams);
        completed = true;
        stage.close();
    }

    private void collectParameters() {
        var newEnhancement = enhancementPanel.getEnhancementParams();

        var extraParams = outputPanel.getExtraParams()
                .withReviewImagesAfterBatch(processingPanel.isReviewImagesAfterBatch())
                .withGenerateDebugImages(imageSelectionPanel.getRequestedImages().isEnabled(GeneratedImageKind.DEBUG));
                
        var baseGeometry = processingPanel.getGeometryParams()
                .withDeconvolutionMode(enhancementPanel.getDeconvolutionMode());

        var fullGeometry = baseGeometry
                .withTilt(advancedPanel.isForceTiltSelected() ? advancedPanel.getTiltValue() : null)
                .withXYRatio(advancedPanel.isForceXYRatioSelected() ? advancedPanel.getXYRatioValue() : null)
                .withDisallowDownsampling(advancedPanel.isDisallowDownsamplingSelected())
                .withForcePolynomial(advancedPanel.isForcePolynomialSelected())
                .withForcedPolynomial(advancedPanel.getForcedPolynomial())
                .withSpectrumVFlip(advancedPanel.isSpectrumVFlipSelected())
                .withEllipseFittingMode(advancedPanel.getEllipseFittingMode())
                .withRichardsonLucyDeconvolutionParams(enhancementPanel.getRichardsonLucyParams());
        
        var videoParams = new VideoParams(advancedPanel.isAssumeMonoVideoSelected() ? ColorMode.MONO : null);
        
        processParams = processParams
                .withSpectrumParams(processingPanel.getSpectrumParams())
                .withGeometryParams(fullGeometry)
                .withRequestedImages(imageSelectionPanel.getRequestedImages())
                .withContrastEnhancement(enhancementPanel.getContrastEnhancement())
                .withAutoStretchParams(enhancementPanel.getAutoStretchParams())
                .withClaheParams(enhancementPanel.getClaheParams())
                .withBandingCorrectionParams(enhancementPanel.getBandingCorrectionParams())
                .withEnhancementParams(newEnhancement)
                .withObservationDetails(observationPanel.getObservationDetails())
                .withVideoParams(videoParams)
                .withExtraParams(extraParams);
    }

    private void loadInitialData(File serFile,
                                 Header header,
                                 CaptureSoftwareMetadataHelper.CaptureMetadata metadata,
                                 boolean batchMode) {
        processingPanel.loadData(processParams, batchMode);
        enhancementPanel.loadData(processParams);
        imageSelectionPanel.loadData(processParams);
        observationPanel.loadData(processParams, metadata);
        advancedPanel.loadData(processParams);
        outputPanel.loadData(processParams, batchMode);
    }

    public Optional<ProcessParams> getProcessParams() {
        return Optional.ofNullable(processParams);
    }

    public boolean isAutoTrimSerFileSelected() {
        return outputPanel != null && outputPanel.isAutoTrimSelected();
    }

    public BorderPane getRoot() {
        return root;
    }

    public void openInstrumentEditor() {
        var stage = new Stage();
        stage.initStyle(StageStyle.DECORATED);
        stage.initOwner(root.getScene().getWindow());
        SpectroHeliographEditor.openEditor(stage, editor -> {
            var selectedInstrument = editor.getSelected();
            selectedInstrument.ifPresent(spectroHeliograph -> Platform.runLater(() -> {
                if (observationPanel != null) {
                    observationPanel.updateInstrument(spectroHeliograph);
                }
                if (advancedPanel != null) {
                    advancedPanel.updateSpectrumVFlip(spectroHeliograph.spectrumVFlip());
                }
            }));
            stage.close();
        });
    }

    public void openSetupEditor() {
        var editorStage = new Stage();
        editorStage.initStyle(StageStyle.DECORATED);
        editorStage.initOwner(root.getScene().getWindow());
        SetupEditor.openEditor(editorStage, editor -> {
            var selectedSetup = editor.getSelected();
            if (selectedSetup.isPresent() && observationPanel != null) {
                Platform.runLater(() -> observationPanel.updateFromSetup(selectedSetup.get()));
            }
            editorStage.close();
        });
    }

    public void openWavelengthEditor() {
        var stage = new Stage();
        stage.initStyle(StageStyle.DECORATED);
        stage.initOwner(root.getScene().getWindow());
        SpectralRayEditor.openEditor(stage, editor -> {
            var selectedRay = editor.getSelectedItem();
            if (selectedRay.isPresent() && processingPanel != null) {
                Platform.runLater(() -> processingPanel.updateWavelength(selectedRay.get()));
            }
            stage.close();
        });
    }

    public void openNamingPatternEditor() {
        var stage = new Stage();
        stage.initStyle(StageStyle.DECORATED);
        stage.initOwner(root.getScene().getWindow());
        var header = getCurrentHeader();
        NamingPatternEditor.openEditor(stage, header, editor -> {
            var selectedPattern = editor.getSelectedPattern();
            if (selectedPattern.isPresent() && outputPanel != null) {
                Platform.runLater(() -> outputPanel.updateNamingPattern(selectedPattern.get()));
            }
            stage.close();
        });
    }
    
    private Header getCurrentHeader() {
        var now = LocalDateTime.now();
        return new Header(null, null, null, 0, 
            new ImageMetadata(null, null, null, true, now, now.atZone(ZoneId.of("UTC"))));
    }
    
    public void updateSpectrumVFlipForInstrument(me.champeau.a4j.jsolex.processing.params.SpectroHeliograph instrument) {
        if (advancedPanel != null) {
            advancedPanel.updateSpectrumVFlip(instrument.spectrumVFlip());
        }
    }

    public void selectFlatFile() {
        var stage = new Stage(StageStyle.UTILITY);
        FlatSelectionController.open(stage,
                broadcaster,
                progressOperation,
                Configuration.getInstance(),
                ProcessParams.loadDefaults(),
                path -> path.ifPresent(p -> {
                    if (enhancementPanel != null) {
                        enhancementPanel.updateFlatFile(p);
                    }
                }));
    }

    public void openVideoDebugger() {
        if (serFile == null) {
            return;
        }

        var progressOperation = ProgressOperation.root(
                "Spectrum Analysis",
                op -> {
                }
        );

        var debugger = SpectralLineDebugger.open(serFile, progressOperation, polynomial -> {
            if (advancedPanel != null) {
                advancedPanel.updateForcedPolynomial(polynomial);
            }
        });
        popups.add(debugger);
        debugger.setOnCloseRequest(e -> popups.remove(debugger));
    }

    private void closePopups() {
        popups.forEach(p -> {
            if (p.isShowing()) {
                p.close();
            }
        });
    }

    public void updateButtonsVisibility() {
        if (imageSelectionPanel != null && processSelectedButton != null) {
            boolean isCustom = imageSelectionPanel.isCustomMode();
            processSelectedButton.setVisible(isCustom);
            processSelectedButton.setManaged(isCustom);
        }
    }
    
    public ProcessingParametersPanel getProcessingPanel() {
        return processingPanel;
    }
    
    public ObservationDetails getObservationDetails() {
        if (observationPanel != null) {
            return observationPanel.getObservationDetails();
        }
        return null;
    }
    
    public void notifyObservationDetailsChanged() {
        if (processingPanel != null) {
            processingPanel.onObservationDetailsChanged();
        }
    }
}