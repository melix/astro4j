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
package me.champeau.a4j.jsolex.app;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.jfx.AdvancedParamsController;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.AssistedEllipseFittingController;
import me.champeau.a4j.jsolex.app.jfx.BatchProcessingHelper;
import me.champeau.a4j.jsolex.app.jfx.CustomTooltip;
import me.champeau.a4j.jsolex.app.jfx.DocsHelper;
import me.champeau.a4j.jsolex.app.jfx.EmbeddedServerController;
import me.champeau.a4j.jsolex.app.jfx.ExposureCalculator;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageMathEditor;
import me.champeau.a4j.jsolex.app.jfx.ime.HighlightingMode;
import me.champeau.a4j.jsolex.app.jfx.ScriptErrorDialog;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.MetadataEditor;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.app.jfx.NamingPatternEditor;
import me.champeau.a4j.jsolex.app.jfx.OpenGLAvailability;
import me.champeau.a4j.jsolex.app.jfx.ProcessParamsController;
import me.champeau.a4j.jsolex.app.jfx.ProgressHandler;
import me.champeau.a4j.jsolex.app.jfx.ProgressTreeBuilder;
import me.champeau.a4j.jsolex.app.jfx.ReferenceImageHelper;
import me.champeau.a4j.jsolex.app.jfx.ScriptParametersDialog;
import me.champeau.a4j.jsolex.app.jfx.ScriptRepositoriesController;
import me.champeau.a4j.jsolex.app.jfx.SerFileTrimmerController;
import me.champeau.a4j.jsolex.app.jfx.SetupEditor;
import me.champeau.a4j.jsolex.app.jfx.SimpleMarkdownViewer;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineDebugger;
import me.champeau.a4j.jsolex.app.jfx.SpectralRayEditor;
import me.champeau.a4j.jsolex.app.jfx.SpectroHeliographEditor;
import me.champeau.a4j.jsolex.app.jfx.SpectrumBrowser;
import me.champeau.a4j.jsolex.app.jfx.StandaloneImagesLoader;
import me.champeau.a4j.jsolex.app.jfx.bass2000.Bass2000SubmissionController;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.app.jfx.stacking.StackingAndMosaicController;
import me.champeau.a4j.jsolex.app.listeners.BatchModeEventListener;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.app.listeners.DelegatingProcessingEventListener;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.app.listeners.RedshiftImagesProcessor;
import me.champeau.a4j.jsolex.app.listeners.SingleModeProcessingEventListener;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.expr.InvalidExpression;
import me.champeau.a4j.jsolex.processing.expr.ScriptExecutionContext;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ScriptParameterExtractor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.NumberParameter;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.params.ScriptParameter;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Bass2000ConfigService;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.DurationFormatter;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import me.champeau.a4j.jsolex.processing.util.LoggingSupport;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.NativeLibrariesUtils;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.math.VectorApiSupport;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageMetadata;
import me.champeau.a4j.ser.SerFileReader;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.findMetadataFile;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

/**
 * Main JavaFX application for processing Sol'Ex spectroheliographic video files.
 */
public class JSolEx implements JSolExInterface, BatchProcessingHelper.BatchContext {

    private static final long MINIMAL_MEMORY = 6 * 1024 * 1024 * 1024L;

    static {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.exceptionHandler", LoggingSupport.class.getName());
        if (Runtime.getRuntime().maxMemory() < MINIMAL_MEMORY) {
            System.err.println("Detected low memory environment, limiting parallelism to 2 threads");
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2");
        }
        NativeLibrariesUtils.ensureNativesLoaded();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JSolEx.class);
    private static final String LOG_EXTENSION = ".log";
    private static final FileChooser.ExtensionFilter LOG_FILE_EXTENSION_FILTER = new FileChooser.ExtensionFilter("Log files (*" + LOG_EXTENSION + ")", "*" + LOG_EXTENSION);
    private static final FileChooser.ExtensionFilter SER_FILES_EXTENSION_FILTER = new FileChooser.ExtensionFilter("SER files", "*.ser", "*.SER");
    private static final String DISCORD_INVITE = "https://discord.gg/y9NCGaWzve";

    /**
     * Default port for the embedded web server.
     */
    public static final int EMBEDDED_SERVER_DEFAULT_PORT = 9122;
    /**
     * Supported image file extensions.
     */
    public static final Set<String> IMAGE_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "tif", "tiff", "fits", "fit");
    /**
     * File chooser extension filter for image files.
     */
    public static final FileChooser.ExtensionFilter IMAGE_FILES_EXTENSIONS = new FileChooser.ExtensionFilter("Image Files", IMAGE_FILE_EXTENSIONS.stream().map(ext -> "*." + ext).toList());

    private final Configuration config = Configuration.getInstance();

    Stage rootStage;

    @FXML
    private StyleClassedTextArea console;

    @FXML
    private Menu recentFilesMenu;

    @FXML
    private StackPane rootStackPane;

    @FXML
    private TabPane mainPane;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    @FXML
    private Label progressBarLabel;

    @FXML
    private HBox workButtons;

    @FXML
    private Node imageMathPane;
    @FXML
    private ImageMathTextArea imageMathScript;
    @FXML
    private CheckBox clearImagesCheckbox;
    @FXML
    private SplitMenuButton imageMathRun;
    @FXML
    private Button imageMathLoad;
    @FXML
    private Button imageMathSave;
    @FXML
    private ChoiceBox<ImageMathEditor.ScriptLanguage> scriptLanguageChoice;
    @FXML
    private ProgressBar memory;
    @FXML
    private Label memoryLabel;

    @FXML
    private TabPane rightTabs;

    @FXML
    private Tab logsTab;

    @FXML
    private Tab statsTab;

    @FXML
    private Tab profileTab;

    @FXML
    private Tab analysisTab;

    @FXML
    private Tab metadataTab;

    @FXML
    private Tab redshiftTab;

    @FXML
    private Tab referenceImageTab;

    @FXML
    private Button bass2000Button;

    @FXML
    private Label spectralLinePrefix;

    @FXML
    private Label spectralLineIndicator;

    @FXML
    private Label tiltIndicator;

    @FXML
    private Label xyRatioIndicator;

    @FXML
    private ChoiceBox<Integer> redshiftBoxSize;

    @FXML
    private ChoiceBox<RedshiftImagesProcessor.RedshiftCreatorKind> redshiftCreatorKind;

    @FXML
    private Button generateRedshiftImages;

    @FXML
    private TextField pixelShiftMargin;

    @FXML
    private Label annotateAnimationsLabel;
    @FXML
    private CheckBox annotateAnimations;

    @FXML
    private CheckBox fullRangePanels;
    @FXML
    private Label fullRangePanelsLabel;

    @FXML
    private GridPane redshiftSelectionBox;

    @FXML
    private Label estimatedDiskSpace;

    @FXML
    private Button closeAllButton;

    @FXML
    private Button deleteSerFileButton;

    @FXML
    private Button trimSerFileButton;

    @FXML
    private Button serverStatus;

    private final Map<String, ImageViewer> popupViewers = new ConcurrentHashMap<>();

    private final MultipleImagesViewer multipleImagesViewer = new MultipleImagesViewer();
    private final JSolExServerHolder server = new JSolExServerHolder();
    private ProcessParams reusedProcessParams;
    private ProcessParams lastExecutionProcessParams;
    private Path watchedDirectory;
    private WatchService watchService;
    private Button interruptWatchButton;
    private Button interruptClearParamsButton;
    private final BooleanBinding reusedProcessParamsBinding = Bindings.createBooleanBinding(() -> reusedProcessParams == null);
    private TrimmingParameters trimmingParameters;
    private HostServices hostServices;
    private Tab imagesViewerTab;
    private ImageMathScriptExecutor scriptExecutor;
    private Path outputDirectory;
    private final RepositoryUpdateService repositoryUpdateService = new RepositoryUpdateService();
    private ProgressHandler progressHandler;
    private ReferenceImageHelper referenceImageHelper;
    private final BatchProcessingHelper batchProcessingHelper = new BatchProcessingHelper();

    /**
     * Creates a new instance. Required by JavaFX.
     */
    public JSolEx() {
    }

    @Override
    public MultipleImagesViewer getImagesViewer() {
        return multipleImagesViewer;
    }

    @Override
    public Tab getLogsTab() {
        return logsTab;
    }

    @Override
    public Tab getStatsTab() {
        return statsTab;
    }

    @Override
    public Tab getProfileTab() {
        return profileTab;
    }

    @Override
    public Tab getAnalysisTab() {
        return analysisTab;
    }

    @Override
    public StackPane getRootStackPane() {
        return rootStackPane;
    }

    @Override
    public Tab getMetadataTab() {
        return metadataTab;
    }

    @Override
    public Tab getRedshiftTab() {
        return redshiftTab;
    }

    @Override
    public Tab getImagesViewerTab() {
        return imagesViewerTab;
    }

    @Override
    public TabPane getTabs() {
        return mainPane;
    }

    @Override
    public Stage getMainStage() {
        return rootStage;
    }

    /**
     * Starts the JSolEx application.
     *
     * @param stage the primary stage
     * @throws Exception if an error occurs during startup
     */
    public void start(Stage stage) throws Exception {
        this.rootStage = stage;
        var fxmlLoader = I18N.fxmlLoader(getClass(), "app");
        fxmlLoader.setController(this);
        configureMemoryStatus();
        try {
            var root = (Parent) fxmlLoader.load();
            progressHandler = new ProgressHandler(snapshot -> {
                if (snapshot.taskCount() == 0 && snapshot.currentTaskLabel().isEmpty()) {
                    // Nothing to show - hide everything
                    progressBar.setProgress(0);
                    progressLabel.setText("");
                    progressBar.setVisible(false);
                    progressBarLabel.setText("");
                } else {
                    // Show progress bar and message
                    progressBar.setVisible(true);
                    progressBar.setProgress(snapshot.progress());
                    progressLabel.setText(snapshot.currentTaskLabel());

                    // Show task count in progress bar when multiple active tasks
                    if (snapshot.taskCount() > 1) {
                        progressBarLabel.setText(snapshot.taskCount() + " " + message("tasks"));
                    } else {
                        progressBarLabel.setText("");
                    }
                }
            });
            // Attach tooltip to progress bar to show operation tree on hover
            var progressTooltip = new CustomTooltip(new Label(message("no.active.operations")));
            progressTooltip.setOnBeforeShow(() -> {
                var treeView = ProgressTreeBuilder.buildTreeView(progressHandler.getActiveRoots());
                progressTooltip.setContent(treeView);
            });
            progressTooltip.enableAutoRefresh(500);
            progressTooltip.attachTo(progressBar);
            progressTooltip.addTriggerNode(progressLabel);
            configureIsolatedScriptExecution();
            imageMathScript.setPrefHeight(10000);
            imageMathScript.setAutoFoldMetaBlocks(true);
            initializeScriptLanguageChoice();
            var preferredDimensions = config.getPreferredDimensions();
            var rootScene = newScene(root, preferredDimensions.a(), preferredDimensions.b());
            var pause = new PauseTransition(Duration.seconds(1));
            rootScene.widthProperty().addListener((observable, oldValue, newValue) -> {
                pause.setOnFinished(e -> config.setPreferredWidth(newValue.intValue()));
                pause.playFromStart();
            });
            rootScene.heightProperty().addListener((observable, oldValue, newValue) -> {
                pause.setOnFinished(e -> config.setPreferredHeigth(newValue.intValue()));
                pause.playFromStart();
            });
            hideProgress();
            String version = VersionUtil.getFullVersion();
            if (version.endsWith("-SNAPSHOT")) {
                version = VersionUtil.getVersion() + " (dev)";
            }
            stage.setTitle("JSol'Ex " + version);
            stage.setScene(rootScene);
            addIcons(stage);
            hideTabHeaderWhenSingleTab(mainPane);
            configureRedshiftControls();
            referenceImageHelper = new ReferenceImageHelper(rootStage, referenceImageTab);
            referenceImageHelper.initialize();
            bass2000Button.setVisible(true);
            stage.show();
            refreshRecentItemsMenu();
            LogbackConfigurer.configureLogger(console);
            setupLogWindowContextMenu();
            setupImageMathEditorContextMenu();
            createFastModePane();
            stage.setOnCloseRequest(e -> {
                progressHandler.close();
                System.exit(0);
            });
            startWatcherThread();
            repositoryUpdateService.checkAtStartup();
            Thread.startVirtualThread(() -> UpdateChecker.findLatestRelease().ifPresent(this::maybeWarnAboutNewRelease));
            LOGGER.info(message("java.runtime.version"), System.getProperty("java.version"));
            LOGGER.info(message("vector.api.support"), VectorApiSupport.isPresent() ? message("vector.api.available") : message("vector.api.missing"),
                    VectorApiSupport.isEnabled() ? message("vector.api.enabled") + MessageFormat.format(message("vector.api.enabled.disable.hint"), VectorApiSupport.VECTOR_API_ENV_VAR) : message("vector.api.disabled"));
            if (config.isAutoStartServer()) {
                server.start(config.getAutoStartServerPort());
            }
            LOGGER.info(message("gpu.support"), OpenCLSupport.isEnabled() ? message("gpu.enabled") : message("gpu.disabled"));
            OpenGLAvailability.checkAsync().thenAccept(available -> {
                LOGGER.info(message("opengl.support"), available ? message("opengl.available") : message("opengl.unavailable"));
            });
            updateServerStatus(false);
            server.addStatusChangeListener(this::updateServerStatus);
            maybeShowWelcomeMessage(rootScene);
        } catch (IOException exception) {
            throw new ProcessingException(exception);
        }
    }

    private void configureIsolatedScriptExecution() {
        var standalone = createRootOperation("standalone");
        var defaultParams = ProcessParams.loadDefaults();
        prepareForScriptExecution(
                createNewStandaloneExecutor(defaultParams, standalone, config.findLastOpenDirectory().orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"))).toFile()),
                defaultParams,
                standalone,
                ImageMathScriptExecutor.SectionKind.SINGLE
        );
        var firstRun = new AtomicBoolean(true);
        var prevHandler = imageMathRun.getOnAction();
        imageMathRun.setOnAction(event -> {
            if (firstRun.compareAndSet(true, false)) {
                Platform.runLater(this::newSession);
            }
            prevHandler.handle(event);
        });
        hideArrowButton(true);
    }

    private void initializeScriptLanguageChoice() {
        scriptLanguageChoice.getItems().addAll(ImageMathEditor.ScriptLanguage.values());
        scriptLanguageChoice.setValue(ImageMathEditor.ScriptLanguage.IMAGEMATH);
        scriptLanguageChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                imageMathScript.setHighlightingMode(newVal.getHighlightingMode());
            }
        });
    }

    private void updateServerStatus(boolean started) {
        Platform.runLater(() -> {
            var serverText = new Text(message("server") + "");
            var statusCircle = new Circle(6, started ? Color.GREEN : Color.RED);
            var statusBox = new HBox(serverText, statusCircle);
            statusBox.setSpacing(4);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            serverStatus.setGraphic(statusBox);
        });
    }

    private void createFastModePane() {
        var loader = I18N.fxmlLoader(getClass(), "fast-mode-open");
        try {
            loader.setController(this);
            var pane = (BorderPane) loader.load();
            var stack = new StackPane();
            pane.prefWidthProperty().bind(stack.widthProperty());
            stack.setAlignment(Pos.CENTER);
            stack.getChildren().add(pane);
            mainPane.getTabs().add(new Tab("Fast mode", stack));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void showEmbeddedServerMenu() {
        var stage = newStage();
        stage.initOwner(rootStage);
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "embedded-server");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (EmbeddedServerController) fxmlLoader.getController();
            controller.setup(stage, server, getHostServices(), config);
            var scene = newScene(node);
            stage.setScene(scene);
            stage.setTitle(I18N.string(JSolEx.class, "embedded-server", "frame.title"));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    /**
     * Creates a new scene with the JSolEx stylesheet applied.
     *
     * @param root the root node
     * @param width the scene width
     * @param height the scene height
     * @return the configured scene
     */
    public static Scene newScene(Parent root, int width, int height) {
        var scene = new Scene(root, width, height);
        scene.getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
        scene.getStylesheets().add(JSolEx.class.getResource("syntax.css").toExternalForm());
        return scene;
    }

    /**
     * Creates a new scene with the JSolEx stylesheet applied.
     *
     * @param root the root node
     * @return the configured scene
     */
    public static Scene newScene(Parent root) {
        var scene = new Scene(root);
        scene.getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
        return scene;
    }

    private void configureRedshiftControls() {
        redshiftBoxSize.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer size) {
                if (size == null) {
                    return "";
                }
                return size + "x" + size;
            }

            @Override
            public Integer fromString(String string) {
                if (string == null || string.isBlank()) {
                    return 0;
                }
                return Integer.parseInt(string.substring(0, string.indexOf('x')));
            }
        });
        redshiftCreatorKind.setItems(FXCollections.observableArrayList(RedshiftImagesProcessor.RedshiftCreatorKind.values()));
        redshiftCreatorKind.setConverter(new StringConverter<>() {
            @Override
            public String toString(RedshiftImagesProcessor.RedshiftCreatorKind kind) {
                return kind == null ? "" : message("redshift.creator.kind." + kind);
            }

            @Override
            public RedshiftImagesProcessor.RedshiftCreatorKind fromString(String string) {
                return RedshiftImagesProcessor.RedshiftCreatorKind.valueOf(string);
            }
        });
        pixelShiftMargin.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String value) {
                var asInt = super.fromString(value);
                if (asInt < 0) {
                    return 0;
                }
                return asInt;
            }
        }));
        pixelShiftMargin.setText("2");
        Platform.runLater(() -> redshiftCreatorKind.getSelectionModel().select(RedshiftImagesProcessor.RedshiftCreatorKind.ANIMATION));
    }

    private void maybeShowWelcomeMessage(Scene current) throws IOException {
        var markerFile = VersionUtil.getJsolexDir().resolve("welcome.txt");
        if (!Files.exists(markerFile) || !Files.readString(markerFile).equals(VersionUtil.getVersion())) {
            showWelcomeMessage(current, () -> {
                try {
                    Files.writeString(markerFile, VersionUtil.getVersion());
                } catch (IOException e) {
                    // ignore
                }
            });
        }
    }


    private void showWelcomeMessage(Scene current, Runnable onDismiss) {
        var webview = new SimpleMarkdownViewer(message("welcome"), getHostServices());
        var country = LocaleUtils.getConfiguredLocale().getLanguage().toUpperCase(Locale.US);
        InputStream resource = getClass().getResourceAsStream("/whats-new_" + country + ".md");
        if (resource == null) {
            resource = getClass().getResourceAsStream("/whats-new.md");
        }
        if (resource != null) {
            var message = new Scanner(resource, "UTF-8").useDelimiter("\\A").next();
            message = message.replace("{{version}}", VersionUtil.getVersion());
            webview.render(current, message, onDismiss);
        }
    }

    private Thread startWatcherThread() {
        var waitTime = config.getWatchModeWaitTimeMilis();
        var newFiles = new ConcurrentHashMap<Path, Long>();
        var watcherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // look into the list of new files, if their size haven't changed, consider that we can start processing
                for (Map.Entry<Path, Long> entry : newFiles.entrySet()) {
                    var child = entry.getKey();
                    var oldSize = entry.getValue();
                    try {
                        var currentSize = Files.size(child);
                        if (currentSize == oldSize) {
                            newFiles.remove(child);
                            Platform.runLater(() -> {
                                LOGGER.info(message("no.change.on.file"), child.getFileName());
                                doOpen(child.toFile(), true, null);
                            });
                        } else {
                            newFiles.put(child, currentSize);
                        }
                    } catch (IOException e) {
                        newFiles.remove(child);
                        LOGGER.error(message("error.unable.determine.size"), child);
                    }
                }
                if (watchService != null) {
                    WatchKey watchKey;
                    while ((watchKey = watchService.poll()) != null) {
                        for (var event : watchKey.pollEvents()) {
                            var kind = event.kind();

                            if (kind == OVERFLOW) {
                                continue;
                            }

                            var ev = (WatchEvent<Path>) event;
                            Path filename = ev.context();
                            Path child = watchedDirectory.resolve(filename);
                            if (Files.isRegularFile(child) && filename.toString().toLowerCase(Locale.US).endsWith(".ser")) {
                                try {
                                    newFiles.put(child, Files.size(child));
                                    LOGGER.info(message("file.added.wait.list"), filename);
                                } catch (IOException e) {
                                    LOGGER.error(message("error.unable.determine.size"), child);
                                }
                            }
                        }

                        boolean valid = watchKey.reset();
                        if (!valid) {
                            watchKey.cancel();
                        }
                    }
                }
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
        return watcherThread;
    }

    private void maybeWarnAboutNewRelease(UpdateChecker.ReleaseInfo release) {
        var currentVersion = toVersionLong(VersionUtil.getVersion());
        var latestRelease = toVersionLong(release.version());
        if (latestRelease > currentVersion) {
            Platform.runLater(() -> {
                var alert = AlertFactory.info();
                alert.setTitle(message("new.release.available"));
                alert.setHeaderText("JSol'Ex " + release.version() + " " + message("has.been.released"));
                var textArea = new TextArea();
                textArea.setEditable(false);
                var scroll = new ScrollPane(textArea);
                textArea.setText(release.notes());
                scroll.fitToHeightProperty().set(true);
                scroll.fitToWidthProperty().set(true);
                alert.getDialogPane().setExpandableContent(scroll);
                alert.getButtonTypes().clear();
                var download = new ButtonType(message("download"));
                alert.getButtonTypes().add(download);
                alert.getButtonTypes().add(ButtonType.CLOSE);
                alert.showAndWait().ifPresent(button -> {
                    if (button == download) {
                        var lang = LocaleUtils.getConfiguredLanguageCode();
                        getHostServices().showDocument("https://melix.github.io/astro4j/latest/" + lang + "/jsolex.html");
                    }
                });
            });
        }
    }

    private long toVersionLong(String version) {
        var v = version;
        if (v.endsWith("-SNAPSHOT")) {
            v = version.substring(0, v.indexOf("-SNAPSHOT"));
        }
        var parts = Arrays.asList(v.split("[.]"));
        if (parts.size() < 3) {
            parts.add("0");
        }
        long id = 0;
        for (int i = 2; i >= 0; i--) {
            id += Math.pow(1000, 2 - i) * Long.parseLong(parts.get(i));
        }
        return id;
    }

    private void configureMemoryStatus() {
        var timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), event -> {
            var usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
            var maxMemory = Runtime.getRuntime().maxMemory() >> 20;
            memory.setProgress(usedMemory / (double) maxMemory);
            memoryLabel.setText(String.format("%d M / %d M", usedMemory, maxMemory));
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void addIcons(Stage stage) {
        for (int i = 16; i <= 512; i *= 2) {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("icons/jsolex-" + i + "x" + i + ".png")));
        }
    }

    private void refreshRecentItemsMenu() {
        recentFilesMenu.getItems().clear();
        for (Path recentFile : config.getRecentFiles()) {
            if (Files.exists(recentFile)) {
                var recent = new MenuItem(recentFile.toAbsolutePath().toString());
                recent.setOnAction(e -> doOpen(recentFile.toFile(), false, null));
                recentFilesMenu.getItems().add(recent);
            }
        }
    }

    public void hideProgress() {
        progressHandler.hide();
    }

    public void showProgress() {
        progressHandler.markDirty();
    }

    public void updateProgress(double progress, String message) {
        if (progress >= 1.0 && message != null && !message.isEmpty()) {
            progressHandler.setCompletionMessage(message);
        } else {
            progressHandler.markDirty();
        }
    }

    @Override
    public void updateProgress(ProgressOperation operation) {
        // If a root operation completes with a message, show it as the completion message
        if (operation.parent() == null && operation.progress() >= 1.0) {
            var task = operation.task();
            if (task != null && !task.isEmpty()) {
                progressHandler.setCompletionMessage(task);
                return;
            }
        }
        progressHandler.ensureTracked(operation);
    }

    @Override
    public void updateSpectralLineIndicator(SpectralRay ray, boolean autoDetected) {
        Platform.runLater(() -> {
            if (autoDetected) {
                spectralLinePrefix.setText(message("spectral.line.detected"));
                spectralLinePrefix.setVisible(true);
                spectralLinePrefix.setManaged(true);
            } else {
                spectralLinePrefix.setVisible(false);
                spectralLinePrefix.setManaged(false);
            }
            spectralLineIndicator.setText(ray.toString());
            var bgColor = wavelengthToColor(ray.wavelength().nanos());
            spectralLineIndicator.setStyle("-fx-background-color: " + toWebColor(bgColor) + "; -fx-text-fill: " + contrastingTextColor(bgColor) + ";");
            spectralLineIndicator.setVisible(true);
            spectralLineIndicator.setManaged(true);
        });
    }

    @Override
    public void hideSpectralLineIndicator() {
        Platform.runLater(() -> {
            spectralLinePrefix.setVisible(false);
            spectralLinePrefix.setManaged(false);
            spectralLineIndicator.setVisible(false);
            spectralLineIndicator.setManaged(false);
        });
    }

    @Override
    public void updateGeometryIndicators(double tiltDegrees, double xyRatio) {
        Platform.runLater(() -> {
            var absTilt = Math.abs(tiltDegrees);
            String tiltColor;
            if (absTilt <= 1.0) {
                tiltColor = "#28a745";
            } else if (absTilt <= 2.0) {
                tiltColor = "#fd7e14";
            } else {
                tiltColor = "#dc3545";
            }
            tiltIndicator.setText(String.format(message("tilt.indicator"), tiltDegrees));
            tiltIndicator.setStyle("-fx-background-color: " + tiltColor + ";");
            tiltIndicator.setVisible(true);
            tiltIndicator.setManaged(true);

            String xyColor;
            if (xyRatio >= 0.9 && xyRatio <= 1.1) {
                xyColor = "#28a745";
            } else if ((xyRatio >= 0.5 && xyRatio < 0.9) || (xyRatio > 1.1 && xyRatio <= 2.0)) {
                xyColor = "#fd7e14";
            } else {
                xyColor = "#dc3545";
            }
            xyRatioIndicator.setText(String.format(message("xyratio.indicator"), xyRatio));
            xyRatioIndicator.setStyle("-fx-background-color: " + xyColor + ";");
            xyRatioIndicator.setVisible(true);
            xyRatioIndicator.setManaged(true);
        });
    }

    @Override
    public void hideGeometryIndicators() {
        Platform.runLater(() -> {
            tiltIndicator.setVisible(false);
            tiltIndicator.setManaged(false);
            xyRatioIndicator.setVisible(false);
            xyRatioIndicator.setManaged(false);
        });
    }

    private static Color wavelengthToColor(double wavelengthNm) {
        if (wavelengthNm <= 0) {
            return Color.GRAY;
        }
        if (wavelengthNm < 380) {
            return Color.rgb(148, 0, 211);
        }
        if (wavelengthNm < 440) {
            return Color.rgb(75, 0, 130);
        }
        if (wavelengthNm < 490) {
            return Color.rgb(0, 0, 255);
        }
        if (wavelengthNm < 510) {
            return Color.rgb(0, 255, 255);
        }
        if (wavelengthNm < 580) {
            return Color.rgb(0, 128, 0);
        }
        if (wavelengthNm < 590) {
            return Color.rgb(255, 255, 0);
        }
        if (wavelengthNm < 620) {
            return Color.rgb(255, 165, 0);
        }
        return Color.rgb(255, 0, 0);
    }

    private static String toWebColor(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private static String contrastingTextColor(Color bgColor) {
        double luminance = 0.299 * bgColor.getRed() + 0.587 * bgColor.getGreen() + 0.114 * bgColor.getBlue();
        return luminance > 0.5 ? "black" : "white";
    }

    @Override
    public void prepareForScriptExecution(ImageMathScriptExecutor executor, ProcessParams params, ProgressOperation rootOperation, ImageMathScriptExecutor.SectionKind sectionKind) {
        this.scriptExecutor = executor;
        var shouldRunBothSections = executor instanceof SingleModeProcessingEventListener listener
                && listener.hasSerFile()
                && sectionKind == ImageMathScriptExecutor.SectionKind.BATCH;
        imageMathRun.setOnAction(evt -> {
            var text = imageMathScript.getText();
            if (clearImagesCheckbox.isSelected()) {
                Platform.runLater(this::newSession);
            }
            config.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(executor::setIncludesDir);
            BackgroundOperations.async(() -> {
                var startTime = System.nanoTime();
                // Create a fresh progress operation for this execution
                var scriptOperation = createRootOperation(message("script.execution"));
                executor.putInContext(ProgressOperation.class, scriptOperation);
                executor.putInContext(AnimationFormat.class, config.getAnimationFormats());
                var section = shouldRunBothSections ? ImageMathScriptExecutor.SectionKind.SINGLE : sectionKind;
                var parameters = extractScriptParameters(text);
                var parameterValues = getScriptParameterValues(parameters);
                for (var entry : parameterValues.entrySet()) {
                    executor.putVariable(entry.getKey(), entry.getValue());
                }
                var result = isPythonScript()
                    ? executor.executePythonScript(text, section)
                    : executor.execute(text, section);
                // Python scripts handle batch mode via explicit batch() function, not automatic "both sections"
                if (shouldRunBothSections && !isPythonScript()) {
                    var previousVariables = new HashMap<String, Object>();
                    var previousKeys = new HashSet<String>();
                    previousKeys.addAll(result.imagesByLabel().keySet());
                    previousKeys.addAll(result.valuesByLabel().keySet());
                    for (var key : previousKeys) {
                        var value = executor.getVariable(key);
                        value.ifPresent(v -> previousVariables.put(key, v));
                    }
                    try {
                        // simulate single element batch mode
                        result.imagesByLabel().forEach((key, image) -> executor.putVariable(key, List.of(image)));
                        result.valuesByLabel().forEach((key, value) -> executor.putVariable(key, List.of(value)));
                        if (isPythonScript()) {
                            executor.executePythonScript(text, ImageMathScriptExecutor.SectionKind.BATCH);
                        } else {
                            executor.execute(text, ImageMathScriptExecutor.SectionKind.BATCH);
                        }
                    } finally {
                        for (var key : previousKeys) {
                            if (previousVariables.containsKey(key)) {
                                executor.putVariable(key, previousVariables.get(key));
                            } else {
                                executor.removeVariable(key);
                            }
                        }
                    }
                }
                // Mark script operation complete after all sections have finished
                var formatted = DurationFormatter.formatNanos(System.nanoTime() - startTime);
                updateProgress(scriptOperation.complete(String.format(message("script.completed.in.format"), formatted)));
            });
        });

        imageMathSave.setDisable(true);
        imageMathLoad.setOnAction(evt -> {
            var fileChooser = new FileChooser();
            config.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            fileChooser.getExtensionFilters().addAll(
                ImageMathEditor.ALL_SCRIPTS_EXTENSION_FILTER,
                ImageMathEditor.MATH_SCRIPT_EXTENSION_FILTER,
                ImageMathEditor.PY_SCRIPT_EXTENSION_FILTER
            );
            var file = fileChooser.showOpenDialog(rootStage);
            loadImageMathScriptFrom(file);
        });
        imageMathScript.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                imageMathSave.setDisable(false);
                updateScriptParametersFromText(newValue);
            }
        });
        imageMathSave.setOnAction(evt -> {
            var fileChooser = new FileChooser();
            config.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            var selectedLanguage = scriptLanguageChoice.getValue();
            if (selectedLanguage == ImageMathEditor.ScriptLanguage.PYTHON) {
                fileChooser.getExtensionFilters().addAll(
                    ImageMathEditor.PY_SCRIPT_EXTENSION_FILTER,
                    ImageMathEditor.MATH_SCRIPT_EXTENSION_FILTER
                );
            } else {
                fileChooser.getExtensionFilters().addAll(
                    ImageMathEditor.MATH_SCRIPT_EXTENSION_FILTER,
                    ImageMathEditor.PY_SCRIPT_EXTENSION_FILTER
                );
            }
            var file = fileChooser.showSaveDialog(rootStage);
            if (file != null) {
                if (!file.getName().endsWith(ImageMathEditor.MATH_EXTENSION) && !file.getName().endsWith(ImageMathEditor.PY_EXTENSION)) {
                    file = new File(file.getParentFile(), file.getName() + selectedLanguage.getExtension());
                }
                try {
                    FilesUtils.writeString(imageMathScript.getText(), file.toPath());
                    imageMathScript.setIncludesDir(file.getParentFile().toPath());
                    imageMathSave.setDisable(true);
                    scriptLanguageChoice.setDisable(true);
                    config.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
                } catch (IOException e) {
                    // ignore
                }
            }
        });
        var scriptFiles = params.combinedImageMathParams().scriptFiles();
        if (!scriptFiles.isEmpty()) {
            loadImageMathScriptFrom(scriptFiles.getFirst());
        }
        updateScriptParametersFromText(imageMathScript.getText());
    }

    private void loadImageMathScriptFrom(File file) {
        if (file != null) {
            config.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
            var script = String.join(System.lineSeparator(), FilesUtils.readAllLines(file.toPath()));
            var language = ImageMathEditor.ScriptLanguage.fromExtension(file.getName());
            Platform.runLater(() -> {
                imageMathScript.setIncludesDir(file.getParentFile().toPath());
                scriptLanguageChoice.setValue(language);
                scriptLanguageChoice.setDisable(true);
                imageMathScript.setHighlightingMode(language.getHighlightingMode());
                imageMathScript.setText(script);
                imageMathSave.setDisable(true);
            });
        }
    }

    private Map<String, Object> getScriptParameterValues(List<ScriptParameter> parameters) {
        var values = new HashMap<String, Object>();
        for (var param : parameters) {
            if (lastExecutionProcessParams != null) {
                var allParams = lastExecutionProcessParams.combinedImageMathParams().parameterValues();
                var found = false;
                for (var entry : allParams.entrySet()) {
                    var existingParams = entry.getValue();
                    if (existingParams != null && existingParams.containsKey(param.getName())) {
                        var value = existingParams.get(param.getName());
                        if (param instanceof NumberParameter && value instanceof String str) {
                            try {
                                value = Double.parseDouble(str);
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Failed to parse parameter {} as number: {}", param.getName(), str);
                                continue;
                            }
                        }
                        values.put(param.getName(), value);
                        found = true;
                        break;
                    }
                }
                if (!found && param.getDefaultValue() != null) {
                    values.put(param.getName(), param.getDefaultValue());
                }
            } else if (param.getDefaultValue() != null) {
                values.put(param.getName(), param.getDefaultValue());
            }
        }
        return values;
    }

    private void updateScriptParametersFromText(String scriptText) {
        var parameters = extractScriptParameters(scriptText);
        updateParametersMenuItem(parameters);
    }

    private List<ScriptParameter> extractScriptParameters(String scriptText) {
        if (scriptText == null || scriptText.isEmpty()) {
            return List.of();
        }
        try {
            var isPython = scriptLanguageChoice.getValue() == ImageMathEditor.ScriptLanguage.PYTHON;
            var result = ScriptParameterExtractor.extractParameters(scriptText, isPython);
            return result.getParameters();
        } catch (Exception e) {
            LOGGER.debug("Error extracting parameters from script", e);
            return List.of();
        }
    }

    private boolean isPythonScript() {
        return scriptLanguageChoice.getValue() == ImageMathEditor.ScriptLanguage.PYTHON;
    }

    private void updateParametersMenuItem(List<ScriptParameter> parameters) {
        Platform.runLater(() -> {
            if (imageMathRun != null && imageMathRun.getItems() != null) {
                if (parameters.isEmpty()) {
                    imageMathRun.getItems().clear();
                    hideArrowButton(true);
                } else if (imageMathRun.getItems().isEmpty()) {
                    var paramsMenuItem = new MenuItem(I18N.string(JSolEx.class, "app", "imagemath.params"));
                    paramsMenuItem.setOnAction(evt -> showScriptParametersDialogAndRun());
                    imageMathRun.getItems().add(paramsMenuItem);
                    hideArrowButton(false);
                }
            }
        });
    }

    private void hideArrowButton(boolean hide) {
        imageMathRun.lookupAll(".arrow-button").forEach(node -> {
            node.setVisible(!hide);
            node.setManaged(!hide);
        });
        var label = imageMathRun.lookup(".label");
        if (label != null) {
            if (hide) {
                label.setStyle("-fx-background-radius: 6; -fx-border-width: 0;");
            } else {
                label.setStyle("");
            }
        }
    }

    private void showScriptParametersDialogAndRun() {
        var scriptText = imageMathScript.getText();
        var parameters = extractScriptParameters(scriptText);
        var currentValues = getScriptParameterValues(parameters);
        var dialog = new ScriptParametersDialog(parameters, currentValues);
        var result = dialog.showAndWait(rootStage);
        if (result != null && lastExecutionProcessParams != null) {
            var currentMathParams = lastExecutionProcessParams.requestedImages().mathImages();
            var updatedParamValues = new HashMap<>(currentMathParams.parameterValues());
            var scriptFile = currentMathParams.scriptFiles().isEmpty() ? new File("") : currentMathParams.scriptFiles().getFirst();
            updatedParamValues.put(scriptFile, result);
            var updatedMathParams = new ImageMathParams(currentMathParams.scriptFiles(), updatedParamValues);
            var updatedRequestedImages = lastExecutionProcessParams.requestedImages().withMathImages(updatedMathParams);
            lastExecutionProcessParams = new ProcessParams(
                    lastExecutionProcessParams.spectrumParams(),
                    lastExecutionProcessParams.observationDetails(),
                    lastExecutionProcessParams.extraParams(),
                    lastExecutionProcessParams.videoParams(),
                    lastExecutionProcessParams.geometryParams(),
                    lastExecutionProcessParams.bandingCorrectionParams(),
                    updatedRequestedImages,
                    lastExecutionProcessParams.claheParams(),
                    lastExecutionProcessParams.autoStretchParams(),
                    lastExecutionProcessParams.contrastEnhancement(),
                    lastExecutionProcessParams.enhancementParams()
            );
            imageMathRun.fire();
        }
    }


    @FXML
    private void open() {
        selectSerFileAndThen(f -> doOpen(f, false, null));
    }

    @FXML
    private void openFastMode() {
        selectSerFileAndThen(f -> doOpen(f, false, ProcessParams.loadDefaults()));
    }

    @FXML
    private void openBatch() {
        List<File> allSelectedFiles = new ArrayList<>();
        boolean addMoreFiles = true;

        while (addMoreFiles) {
            var fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(SER_FILES_EXTENSION_FILTER);
            config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            var selectedFiles = fileChooser.showOpenMultipleDialog(rootStage);

            if (selectedFiles != null && !selectedFiles.isEmpty()) {
                allSelectedFiles.addAll(selectedFiles);
                var alert = AlertFactory.confirmation(message("batch.add.more.files"));
                alert.setTitle(message("batch.file.selection.title"));
                alert.setHeaderText(message("batch.files.added.success"));
                alert.getButtonTypes().clear();
                alert.getButtonTypes().addAll(ButtonType.NO, ButtonType.YES);

                var noButton = (Button) alert.getDialogPane().lookupButton(ButtonType.NO);
                noButton.setDefaultButton(true);
                var yesButton = (Button) alert.getDialogPane().lookupButton(ButtonType.YES);
                yesButton.setDefaultButton(false);

                var result = alert.showAndWait();
                addMoreFiles = result.isPresent() && result.get() == ButtonType.YES;
            } else {
                addMoreFiles = false;
            }
        }

        if (!allSelectedFiles.isEmpty()) {
            doOpenMany(allSelectedFiles);
        }
    }

    @FXML
    private void watchMode() {
        var fileChooser = new DirectoryChooser();
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var directory = fileChooser.showDialog(rootStage);
        if (directory != null) {
            LOGGER.info(message("watching"), directory);
            try {
                var watcher = FileSystems.getDefault().newWatchService();
                if (watchService != null) {
                    reusedProcessParams = null;
                    reusedProcessParamsBinding.invalidate();
                    watchService.close();
                    if (interruptWatchButton != null) {
                        Platform.runLater(() -> workButtons.getChildren().remove(interruptWatchButton));
                        interruptWatchButton = null;
                    }
                }
                watchService = watcher;
                watchedDirectory = directory.toPath();
                var key = watchedDirectory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
                interruptWatchButton = new Button(message("stop.watching"));
                interruptWatchButton.getStyleClass().add("default-button");
                interruptWatchButton.setOnAction(e -> {
                    try {
                        reusedProcessParams = null;
                        reusedProcessParamsBinding.invalidate();
                        key.cancel();
                        watchService.close();
                        watchService = null;
                    } catch (IOException ex) {
                        // ignore
                    }
                    Platform.runLater(() -> {
                        workButtons.getChildren().remove(interruptWatchButton);
                        workButtons.getChildren().remove(interruptClearParamsButton);
                    });
                    LOGGER.info(message("stopped.watching"), watchedDirectory);
                });
                interruptClearParamsButton = addInterruptClearParamsButton();

                Platform.runLater(() -> workButtons.getChildren().add(interruptWatchButton));
            } catch (IOException e) {
                LOGGER.error(message("error.cannot.create.watch.service"), e);
            }
        }
    }

    @FXML
    private void loadImages() {
        if (imagesViewerTab == null || !mainPane.getTabs().contains(imagesViewerTab)) {
            mainPane.getTabs().clear();
            imagesViewerTab = new Tab(message("images"), multipleImagesViewer);
            mainPane.getTabs().add(imagesViewerTab);
            hideTabHeaderWhenSingleTab(mainPane);
        }
        new StandaloneImagesLoader(rootStage, config, multipleImagesViewer, IMAGE_FILES_EXTENSIONS, this::updateProgress, ProcessParams.loadDefaults()).loadImages();
    }

    private Button addInterruptClearParamsButton() {
        var interruptClearParamsButton = new Button(message("interrupt.new.params"));
        interruptClearParamsButton.getStyleClass().add("default-button");
        interruptClearParamsButton.setOnAction(e -> {
            reusedProcessParams = null;
            reusedProcessParamsBinding.invalidate();
        });
        interruptClearParamsButton.disableProperty().bind(reusedProcessParamsBinding);
        Platform.runLater(() -> workButtons.getChildren().add(interruptClearParamsButton));
        return interruptClearParamsButton;
    }

    private void selectSerFileAndThen(Consumer<? super File> consumer) {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(SER_FILES_EXTENSION_FILTER);
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selectedFile = fileChooser.showOpenDialog(rootStage);
        if (selectedFile != null) {
            LoggingSupport.LOGGER.info(message("selected.files"), selectedFile);
            consumer.accept(selectedFile);
        } else {
            LoggingSupport.LOGGER.info(message("no.selected.file"));
        }
    }

    @FXML
    private void showFrameDebugger() {
        selectSerFileAndThen(file -> {
            config.loadedSerFile(file.toPath());
            SpectralLineDebugger.open(file, createRootOperation(file.getName()), unused -> {
            });
        });
    }

    @FXML
    private void showFileNamePatternEditor() {
        var now = LocalDateTime.now();
        var stage = newStage();
        NamingPatternEditor.openEditor(stage, createFakeHeader(now), e -> stage.close());
    }

    @FXML
    private void showSpectralRayEditor() {
        var stage = newStage();
        SpectralRayEditor.openEditor(stage, e -> stage.close());
    }

    @FXML
    private void showSHGEditor() {
        var stage = newStage();
        SpectroHeliographEditor.openEditor(stage, e -> stage.close());
    }

    @FXML
    private void showSetupEditor() {
        var stage = newStage();
        SetupEditor.openEditor(stage, e -> stage.close());
    }

    @FXML
    private void showImageMathEditor() {
        var stage = newStage();
        var params = ProcessParams.loadDefaults();
        ImageMathEditor.create(stage, params.combinedImageMathParams(), getHostServices(), true, true, e -> {
        }, e -> {
            stage.close();
            Platform.runLater(this::newSession);
            e.getConfiguration().ifPresent(scripts -> BackgroundOperations.async(() -> executeStandaloneScripts(params.withRequestedImages(params.requestedImages().withMathImages(scripts)), createRootOperation(""))));
        });
    }

    private void openMetadataEditor() {
        var stage = newStage();
        var scriptText = imageMathScript.getText();
        MetadataEditor.openEditor(stage, scriptText, isPythonScript(), (originalScript, modifiedScript) -> {
            imageMathScript.setText(modifiedScript);
            imageMathSave.setDisable(false);
        });
    }

    @FXML
    private void showScriptRepositories() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "script-repositories");
        try {
            var stage = newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (ScriptRepositoriesController) fxmlLoader.getController();
            controller.setup(stage, Configuration.getInstance());
            Scene scene = newScene(node);
            stage.setTitle(I18N.string(JSolEx.class, "script-repositories", "frame.title"));
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            LOGGER.error(message("error.unable.open.script.repositories"), e);
        }
    }

    @FXML
    private void showAdvancedParams() {
        AdvancedParamsController.openDialog(rootStage);
    }

    @FXML
    private void showExposureCalculator() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "exposure-calculator");
        try {
            var stage = newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (ExposureCalculator) fxmlLoader.getController();
            controller.setup(stage);
            Scene scene = newScene(node);
            stage.setTitle(I18N.string(JSolEx.class, "exposure-calculator", "frame.title"));
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void showMosaicParams() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "mosaic-params");
        try {
            var stage = newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (StackingAndMosaicController) fxmlLoader.getController();
            controller.setup(stage, this, createRootOperation("moisaic"), ProcessParams.loadDefaults(), popupViewers);
            Scene scene = new Scene(node);
            stage.setTitle(I18N.string(JSolEx.class, "mosaic-params", "frame.title"));
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void showSpectrumBrowser() {
        var browser = new SpectrumBrowser(800);
        var stage = newStage();
        stage.initModality(Modality.NONE);
        stage.setScene(newScene(browser));
        stage.setTitle(I18N.string(JSolEx.class, "spectrum-browser", "frame.title"));
        stage.show();
    }

    @FXML
    private void showBass2000Submission() {
        if (lastExecutionProcessParams != null &&
                lastExecutionProcessParams.observationDetails().instrument().isSupportedByBass2000() &&
                !Bass2000ConfigService.getInstance().isBass2000Enabled()) {

            var configService = Bass2000ConfigService.getInstance();
            var reason = configService.getDisabledReason();
            String message = switch (reason) {
                case Bass2000ConfigService.DisabledReason.EXPLICITLY_DISABLED -> {
                    var config = configService.fetchConfiguration();
                    if (config.isPresent()) {
                        var currentLocale = LocaleUtils.getConfiguredLocale();
                        var language = currentLocale.getLanguage();
                        yield config.get().getMessage(language);
                    }
                    yield message("bass2000.disabled");
                }
                case Bass2000ConfigService.DisabledReason.VERSION_TOO_OLD -> {
                    var requiredVersion = configService.getRequiredVersion();
                    var baseMessage = message("bass2000.version.too.old");
                    if (requiredVersion != null) {
                        yield baseMessage.replace("{version}", requiredVersion);
                    } else {
                        yield baseMessage;
                    }
                }
                default -> message("bass2000.disabled");
            };

            if (!message.isEmpty()) {
                var alert = AlertFactory.warning(message);
                alert.setTitle("BASS2000");
                alert.showAndWait();
                return;
            }
        }

        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "bass2000-submission");
        try {
            var stage = newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (Bass2000SubmissionController) fxmlLoader.getController();
            controller.setup(stage, this, outputDirectory.resolve("bass2000"));
            Scene scene = new Scene(node);
            stage.setTitle(I18N.string(JSolEx.class, "bass2000-submission", "wizard.header"));
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeStandaloneScripts(ProcessParams params, ProgressOperation rootOperation) {
        var scriptFiles = params.combinedImageMathParams().scriptFiles();
        var scriptFile = scriptFiles.stream().findFirst();
        scriptFile.ifPresent(script -> {
            var outputDirectory = script.getParentFile();
            var imageScriptExecutor = createNewStandaloneExecutor(params, rootOperation, outputDirectory);
            for (File file : scriptFiles) {
                try {
                    imageScriptExecutor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
                prepareForScriptExecution(imageScriptExecutor, params, rootOperation, ImageMathScriptExecutor.SectionKind.SINGLE);
            }
        });
    }

    private JSolExScriptExecutor createNewStandaloneExecutor(ProcessParams params, ProgressOperation rootOperation, File outputDirectory) {
        var processingDate = LocalDateTime.now();
        var listener = delegatingListener(new SingleModeProcessingEventListener(this, rootOperation, "", null, outputDirectory.toPath(), params, processingDate, popupViewers));
        var namingStrategy = new FileNamingStrategy(params.extraParams().fileNamePattern(), params.extraParams().datetimeFormat(), params.extraParams().dateFormat(), processingDate, createFakeHeader(processingDate));
        // Create a child operation for script execution so the root doesn't get marked complete prematurely
        var scriptOperation = rootOperation.createChild("Script");
        var imageScriptExecutor = new JSolExScriptExecutor(img -> {
            throw new ProcessingException("img() is not available in standalone image math scripts. Use load or load_many to load images");
        }, ScriptExecutionContext.builder().progressOperation(scriptOperation).build(), (Broadcaster) listener, null) {
            @Override
            public ImageMathScriptResult execute(String script, SectionKind kind) {
                var result = super.execute(script, kind);
                processResult(result);
                // Mark root complete only after all post-processing is done
                updateProgress(rootOperation.complete(message("script.completed")));
                return result;
            }

            @Override
            public ImageMathScriptResult executePythonScript(String script, SectionKind kind) {
                var result = super.executePythonScript(script, kind);
                processResult(result);
                // Mark root complete only after all post-processing is done
                updateProgress(rootOperation.complete(message("script.completed")));
                return result;
            }

            private void processResult(ImageMathScriptResult result) {
                result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
                    var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "standalone", entry.getValue());
                    var outputFile = new File(outputDirectory, name);
                    listener.onImageGenerated(new ImageGeneratedEvent(new GeneratedImage(GeneratedImageKind.IMAGE_MATH, entry.getKey(), outputFile.toPath(), entry.getValue(), null)));

                });
                result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
                    var fileOutput = entry.getValue();
                    var baseName = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "standalone", null);
                    try {
                        var displayPath = FilesUtils.saveAllFilesAndGetDisplayPath(fileOutput, outputDirectory.toPath(), baseName);
                        // Only fire display event for the designated display file
                        if (displayPath != null) {
                            listener.onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, entry.getKey(), displayPath));
                        }
                    } catch (IOException e) {
                        throw new ProcessingException(e);
                    }
                });
                for (InvalidExpression expression : result.invalidExpressions()) {
                    LOGGER.error(message("error.invalid.expression"), expression.label(), expression.expression(), expression.error().getMessage());
                }
            }
        };
        return imageScriptExecutor;
    }

    private ProcessingEventListener delegatingListener(SingleModeProcessingEventListener singleModeProcessingEventListener) {
        if (server.isStarted()) {
            List<ProcessingEventListener> listeners = Stream.concat(
                    Stream.of(singleModeProcessingEventListener),
                    server.getListeners().stream()
            ).toList();
            return new DelegatingProcessingEventListener(listeners);
        }
        return singleModeProcessingEventListener;
    }

    /**
     * Creates a fake SER file header for testing or synthetic data.
     *
     * @param now the timestamp to use
     * @return the fake header
     */
    public static Header createFakeHeader(LocalDateTime now) {
        return new Header(null, null, null, 0, new ImageMetadata(null, null, null, true, now, now.atZone(ZoneId.of("UTC"))));
    }

    @FXML
    private void about() {
        var alert = AlertFactory.info();
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(700, 400);
        String version = VersionUtil.getVersion();
        alert.setTitle(I18N.string(getClass(), "about", "about.title"));
        alert.setHeaderText(I18N.string(getClass(), "about", "about.header") + ". Version " + version);

        var contentLabel = new Label(I18N.string(getClass(), "about", "about.message"));
        contentLabel.setWrapText(true);
        alert.getDialogPane().setContent(contentLabel);

        var licenses = new TextArea();
        try {
            licenses.setText(new String(JSolEx.class.getResourceAsStream("/licenses.txt").readAllBytes(), "utf-8"));
        } catch (Exception e) {
            licenses.setText("Cannot find licenses file");
        }
        var scroll = new ScrollPane(licenses);
        scroll.fitToHeightProperty().set(true);
        scroll.fitToWidthProperty().set(true);
        alert.getDialogPane().setExpandableContent(scroll);
        alert.showAndWait();
    }


    @FXML
    private void showHelp() {
        DocsHelper.openHelp(getHostServices(), null);
    }

    @FXML
    private void openDiscord() {
        getHostServices().showDocument(DISCORD_INVITE);
    }

    @FXML
    private void donate() {
        var lang = LocaleUtils.getConfiguredLanguageCode();
        getHostServices().showDocument("https://melix.github.io/astro4j/latest/" + lang + "/jsolex.html#donate");
    }

    @FXML
    private void exportPythonStubs() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(getClass(), "messages", "export.python.stubs.title"));
        fileChooser.setInitialFileName("jsolex.pyi");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Python Stub Files", "*.pyi"));
        var file = fileChooser.showSaveDialog(rootStage);
        if (file != null) {
            try (var input = JSolEx.class.getResourceAsStream("/python-stubs/jsolex.pyi");
                 var output = new java.io.FileOutputStream(file)) {
                if (input == null) {
                    throw new IllegalStateException("Python stubs resource not found");
                }
                input.transferTo(output);
                var alert = AlertFactory.info();
                alert.setTitle(I18N.string(getClass(), "messages", "export.python.stubs.success.title"));
                alert.setHeaderText(I18N.string(getClass(), "messages", "export.python.stubs.success.header"));
                alert.setContentText(MessageFormat.format(I18N.string(getClass(), "messages", "export.python.stubs.success.content"), file.getAbsolutePath()));
                alert.showAndWait();
            } catch (Exception e) {
                var alert = AlertFactory.error(e.getMessage());
                alert.setTitle(I18N.string(getClass(), "messages", "export.python.stubs.error.title"));
                alert.showAndWait();
            }
        }
    }

    private void doOpen(File selectedFile, boolean rememberProcessParams, ProcessParams forcedParams) {
        config.loadedSerFile(selectedFile.toPath());
        configureThreadExceptionHandler();
        Platform.runLater(this::refreshRecentItemsMenu);
        Optional<ProcessParams> processParams;
        Header header;
        try (var reader = SerFileReader.of(selectedFile)) {
            header = reader.header();
            var params = forcedParams != null ? forcedParams : reusedProcessParams;
            if (params != null) {
                processParams = Optional.of(params.withObservationDetails(params.observationDetails().withDate(header.metadata().utcDateTime())));
            } else {
                var controller = createProcessParams(selectedFile, createRootOperation(selectedFile.getName()), reader, false);
                processParams = controller.getProcessParams();
            }
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        var firstHeader = header;
        processParams.ifPresent(params -> {
            if (rememberProcessParams) {
                reusedProcessParams = params;
                reusedProcessParamsBinding.invalidate();
            }
            deleteSerFileButton.setDisable(true);
            trimSerFileButton.setDisable(true);
            processFileWithParams(selectedFile, firstHeader, params);
        });
    }

    private void processFileWithParams(File selectedFile, Header firstHeader, ProcessParams params) {
        newSession();
        console.clear();
        var interruptButton = addInterruptButton();
        var rootOperation = createRootOperation(selectedFile.getName());
        Platform.runLater(() -> imageMathRun.setDisable(true));
        var processingThread =
                new Thread(() -> processSingleFile(params, rootOperation, selectedFile, false, 0, selectedFile, firstHeader, () -> Platform.runLater(() -> {
                    workButtons.getChildren().remove(interruptButton);
                    imageMathRun.setDisable(false);
                })));
        interruptButton.setOnAction(e -> {
            Platform.runLater(() -> {
                hideProgress();
                workButtons.getChildren().remove(interruptButton);
                imageMathRun.setDisable(false);
            });
            processingThread.interrupt();
        });
        processingThread.start();
    }

    public void newSession() {
        mainPane.getTabs().clear();
        imagesViewerTab = new Tab(message("images"), multipleImagesViewer);
        mainPane.getTabs().add(imagesViewerTab);
        multipleImagesViewer.clear();
        multipleImagesViewer.setCollageContext(this, lastExecutionProcessParams, outputDirectory);
        hideTabHeaderWhenSingleTab(mainPane);
        bass2000Button.setDisable(true);
    }

    @Override
    public void prepareForRedshiftImages(RedshiftImagesProcessor processor) {
        var redshifts = processor.getRedshifts();
        var maxSize = redshifts.stream()
                .mapToDouble(RedshiftArea::size)
                .max()
                .orElse(0);
        var power = highestPowerOfTwoGreaterOrEqualTo(processor.getSunRadius().map(r -> r / 10d).orElse(maxSize));
        int boxSize = (int) Math.pow(2, power);
        Platform.runLater(() -> {
            redshiftTab.setDisable(redshifts.isEmpty());
            if (lastExecutionProcessParams != null) {
                bass2000Button.setDisable(!lastExecutionProcessParams.observationDetails().instrument().isSupportedByBass2000());
            }
            fullRangePanels.disableProperty().bind(redshiftCreatorKind.valueProperty().isEqualTo(RedshiftImagesProcessor.RedshiftCreatorKind.ANIMATION));
            fullRangePanelsLabel.disableProperty().bind(redshiftCreatorKind.valueProperty().isEqualTo(RedshiftImagesProcessor.RedshiftCreatorKind.ANIMATION));
            annotateAnimationsLabel.disableProperty().bind(redshiftCreatorKind.valueProperty().isEqualTo(RedshiftImagesProcessor.RedshiftCreatorKind.PANEL));
            annotateAnimations.disableProperty().bind(redshiftCreatorKind.valueProperty().isEqualTo(RedshiftImagesProcessor.RedshiftCreatorKind.PANEL));
            redshiftBoxSize.getItems().clear();
            redshiftSelectionBox.getChildren().clear();
            var selectedShifts = new HashSet<>(redshifts);
            for (int i = 0; i < redshifts.size(); i++) {
                var redshift = redshifts.get(i);
                var checkBox = new CheckBox(String.format("%s (%.2f km/s)", redshift.id(), redshift.kmPerSec()));
                checkBox.setSelected(true);
                checkBox.selectedProperty().addListener((observableValue, aBoolean, newValue) -> {
                    if (Boolean.TRUE.equals(newValue)) {
                        selectedShifts.add(redshift);
                    } else {
                        selectedShifts.remove(redshift);
                    }
                });
                var row = i / 3;
                var column = i % 3;
                GridPane.setRowIndex(checkBox, row);
                GridPane.setColumnIndex(checkBox, column);
                GridPane.setMargin(checkBox, new Insets(0, 8, 4, 8));
                redshiftSelectionBox.setPadding(new Insets(8, 8, 8, 8));
                redshiftSelectionBox.getChildren().add(checkBox);
            }
            int bSize = boxSize;
            for (int i = 0; i < 4; i++) {
                redshiftBoxSize.getItems().add(bSize);
                bSize += boxSize;
            }
            estimatedDiskSpace.textProperty().bind(pixelShiftMargin.textProperty().map(Double::parseDouble).map(m -> processor.estimateRequiredDiskSpaceWithMargin(m.intValue())));
            generateRedshiftImages.setOnAction(e -> {
                var kind = redshiftCreatorKind.getValue();
                var size = redshiftBoxSize.getValue();
                var margin = Integer.valueOf(pixelShiftMargin.getText());
                var useFullRangePanels = fullRangePanels.isSelected();
                var annotate = annotateAnimations.isSelected();
                try {
                    if (Files.getFileStore(TemporaryFolder.tempDir()).getUsableSpace() < processor.estimateRequiredBytesForProcessingWithMargin(margin)) {
                        var alert = AlertFactory.confirmation(message("disk.space.error.confirm"));
                        var result = alert.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.CANCEL) {
                            return;
                        }
                    }
                } catch (IOException ex) {
                    // ignore
                }
                if (kind != null && size != null) {
                    BackgroundOperations.async(() -> {
                        Platform.runLater(() -> rightTabs.getSelectionModel().select(logsTab));
                        processor.withRedshifts(selectedShifts.stream().toList()).produceImages(kind, size, margin, useFullRangePanels, annotate, new int[]{255, 255, 0});
                    });
                }
            });
            redshiftBoxSize.getSelectionModel().select((Integer) boxSize);
        });
    }

    @Override
    public void prepareForGongImageDownload(ProcessParams processParams) {
        referenceImageHelper.createReferenceImageInterface(processParams.observationDetails().date());
    }

    @Override
    public void applyUserRotation(ApplyUserRotation userParams) {
        if (lastExecutionProcessParams != null) {
            // Correct parameters according to the parameters that the user has explicitly applied
            var newGeoparams = lastExecutionProcessParams.geometryParams().withAutocorrectAngleP(userParams.correctAngleP());
            var hasHorizontalFlip = newGeoparams.isHorizontalMirror();
            var hasVerticalFlip = newGeoparams.isVerticalMirror();

            // Reverse user's vertical flip (apply another vertical flip if user applied one)
            if (userParams.verticalFlip()) {
                hasVerticalFlip = !hasVerticalFlip;
            }

            // Reverse user's rotation (convert to number of 90-degree left rotations)
            int reversedRotation = -userParams.rotation(); // Reverse direction of rotation
            int totalLeftRotations = 0;

            // Normalize rotation to be between 0 and 3 left rotations
            if (reversedRotation < 0) {
                totalLeftRotations = (4 - ((-reversedRotation) % 4)) % 4; // Convert right to left
            } else {
                totalLeftRotations = (reversedRotation % 4); // Already left rotations
            }

            // Adjust initial rotation kind
            RotationKind initialRotation = newGeoparams.rotation();
            switch (initialRotation) {
                case LEFT:
                    totalLeftRotations = (totalLeftRotations + 1) % 4;
                    break;
                case RIGHT:
                    totalLeftRotations = (totalLeftRotations + 3) % 4;
                    break;
                default:
                    break;
            }

            // Determine the final rotation kind
            RotationKind finalRotation;
            switch (totalLeftRotations) {
                case 1:
                    finalRotation = RotationKind.LEFT;
                    break;
                case 2:
                    finalRotation = RotationKind.NONE; // 180 degrees should be NONE and toggle both flips
                    hasHorizontalFlip = !hasHorizontalFlip;
                    hasVerticalFlip = !hasVerticalFlip;
                    break;
                case 3:
                    finalRotation = RotationKind.RIGHT;
                    break;
                default:
                    finalRotation = RotationKind.NONE;
                    break;
            }

            // Update newGeoparams with the computed transformations
            newGeoparams = newGeoparams.withHorizontalMirror(hasHorizontalFlip)
                    .withVerticalMirror(hasVerticalFlip)
                    .withRotation(finalRotation);
            ProcessParamsIO.saveDefaults(lastExecutionProcessParams.withGeometryParams(newGeoparams));
        }
    }

    private static int highestPowerOfTwoGreaterOrEqualTo(double n) {
        var power = 0;
        while (Math.pow(2, power) < n) {
            power++;
        }
        return power;
    }

    private static void hideTabHeaderWhenSingleTab(TabPane tabPane) {
        tabPane.getTabs().addListener((ListChangeListener<? super Tab>) tab -> {
            var styleClass = tabPane.getStyleClass();
            if (tabPane.getTabs().size() <= 1) {
                styleClass.add("no-tab-header");
            } else {
                styleClass.removeIf("no-tab-header"::equals);
            }
        });
    }

    private static void configureThreadExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> logError(e));
    }

    private void doOpenMany(List<File> selectedFiles) {
        configureThreadExceptionHandler();
        Optional<ProcessParams> processParams = Optional.empty();
        Optional<Boolean> autoTrim = Optional.empty();
        Header header = null;
        for (var selectedFile : selectedFiles) {
            try (var reader = SerFileReader.of(selectedFile)) {
                var controller = createProcessParams(selectedFile, createRootOperation(selectedFile.getName()), reader, true);
                processParams = controller.getProcessParams();
                autoTrim = Optional.of(controller.isAutoTrimSerFileSelected());
                header = reader.header();
                config.updateLastOpenDirectory(selectedFile.toPath().getParent());
                break;
            } catch (Exception e) {
                throw ProcessingException.wrap(e);
            }
        }
        var firstHeader = header;
        boolean autoTrimFinal = autoTrim.orElse(false);
        var progressOperation = createRootOperation("Batch");
        processParams.ifPresent(params -> startBatchProcess(firstHeader, progressOperation, params, selectedFiles, autoTrimFinal));

    }

    private ProgressOperation createRootOperation(String name) {
        var root = ProgressOperation.root(name, op -> {
            // This callback is called when a child of 'op' is removed
            if (op.hasNoChild() && op.parent() == null) {
                // Root operation has no more children, unregister it
                progressHandler.unregisterRoot(op);
            }
            progressHandler.markDirty();
        });
        progressHandler.registerRoot(root);
        return root;
    }

    private void startBatchProcess(Header header, ProgressOperation progressOperation, ProcessParams params, List<File> selectedFiles, boolean autoTrimSerFile) {
        batchProcessingHelper.startBatchProcess(this, header, progressOperation, params, selectedFiles, autoTrimSerFile);
    }

    @Override
    public Button addInterruptButton() {
        var interruptButton = new Button(message("interrupt"));
        interruptButton.getStyleClass().add("default-button");
        Platform.runLater(() -> workButtons.getChildren().add(interruptButton));
        return interruptButton;
    }

    @Override
    public void removeInterruptButton(Button button) {
        Platform.runLater(() -> workButtons.getChildren().remove(button));
    }

    @Override
    public TabPane getMainPane() {
        return mainPane;
    }

    @Override
    public void setImageMathRunDisabled(boolean disabled) {
        Platform.runLater(() -> imageMathRun.setDisable(disabled));
    }

    @Override
    public TrimmingParameters getTrimmingParameters() {
        return trimmingParameters;
    }

    @Override
    public File toTrimmedFile(File serFile) {
        return SerFileTrimmerController.toTrimmedFile(serFile);
    }

    @Override
    public void processSingleFile(ProcessParams params,
                                  ProgressOperation operation,
                                  File selectedFile,
                                  int sequenceNumber,
                                  BatchProcessingContext context,
                                  Header header,
                                  Runnable onComplete) {
        processSingleFile(params, operation, selectedFile, true, sequenceNumber, context, header, onComplete);
    }

    private void processSingleFile(ProcessParams params,
                                   ProgressOperation operation,
                                   File selectedFile,
                                   boolean batchMode,
                                   int sequenceNumber,
                                   Object context,
                                   Header header,
                                   Runnable onComplete) {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> LoggingSupport.logError(e));
        lastExecutionProcessParams = params;
        LogbackConfigurer.recordThreadOwner(Thread.currentThread().getName(), sequenceNumber);
        var processingDate = context instanceof BatchProcessingContext batch ? batch.processingDate() : LocalDateTime.now();
        var namingStrategy = new FileNamingStrategy(params.extraParams().fileNamePattern(), params.extraParams().datetimeFormat(), params.extraParams().dateFormat(), processingDate, header);
        var outputDirectory = selectedFile.getParentFile();
        this.outputDirectory = outputDirectory.toPath();
        multipleImagesViewer.setCollageContext(this, lastExecutionProcessParams, this.outputDirectory);
        var baseName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf("."));
        var logFileName = namingStrategy.render(sequenceNumber, null, "log", "log", baseName, null) + LOG_EXTENSION;
        var logFile = new File(outputDirectory, logFileName);
        // For the log file we cannot _fully_ use the pattern since some data is not yet available (the file header)
        logFile = new File(logFile.getParentFile(), String.format("%04d_%s" + LOG_EXTENSION, sequenceNumber, baseName));
        if (context instanceof BatchProcessingContext ctx) {
            ctx.items().get(sequenceNumber).generatedFiles().add(logFile);
        }
        var appender = LogbackConfigurer.createContextualFileAppender(sequenceNumber, logFile);
        LoggingSupport.LOGGER.info(message("output.dir.set"), outputDirectory);
        var processorContext = Map.<Class<?>, Object>of(AnimationFormat.class, config.getAnimationFormats());
        var processor = new SolexVideoProcessor(selectedFile, outputDirectory.toPath(), sequenceNumber, params, processingDate, batchMode, config.getMemoryRestrictionMultiplier(), operation, processorContext);
        var listener = createListener(operation, baseName, params, batchMode, sequenceNumber, context);
        processor.addEventListener(listener);
        try {
            LOGGER.info(message("info.file"), selectedFile.getName());
            processor.process();
        } catch (Throwable ex) {
            LoggingSupport.logError(ex);
            // In batch mode, notify listener about processing failure
            if (batchMode && listener instanceof BatchModeEventListener batchListener) {
                batchListener.onProcessingFailed();
            }
        } finally {
            onComplete.run();
            appender.stop();
            LogbackConfigurer.clearOwners();
        }
        closeAllButton.setDisable(false);
        if (!batchMode) {
            deleteSerFileButton.setDisable(false);
        }
    }

    @Override
    public void setTrimmingParameters(TrimmingParameters payload) {
        this.trimmingParameters = payload;
        trimSerFileButton.setDisable(false);
    }

    @Override
    public CompletableFuture<Ellipse> showEllipseFittingDialog(ImageWrapper32 image, Ellipse initialEllipse) {
        return AssistedEllipseFittingController.showDialog(rootStage, image, initialEllipse);
    }

    @Override
    public CompletableFuture<Ellipse> showEllipseFittingDialog(ImageWrapper32 image, Ellipse initialEllipse, String fileName, int currentFile, int totalFiles) {
        return AssistedEllipseFittingController.showDialog(rootStage, image, initialEllipse, fileName, currentFile, totalFiles);
    }

    @Override
    public ImageMathScriptExecutor getScriptExecutor() {
        return scriptExecutor;
    }


    /**
     * Trims a SER file by removing unwanted frames.
     */
    @FXML
    public void trimSerFile() {
        Platform.runLater(() -> {
            var stage = newStage();
            SerFileTrimmerController.create(stage,
                    trimmingParameters,
                    this::showProgress,
                    this::updateProgress,
                    trimmedFile -> {
                        hideProgress();
                        if (trimmedFile != null) {
                            long initialSize = trimmingParameters.serFile().length();
                            long finalSize = trimmedFile.length();
                            double initialSizeMb = initialSize / (1024.0 * 1024.0);
                            double finalSizeMb = finalSize / (1024.0 * 1024.0);
                            double reduction = 100.0 - (100.0 * finalSize / initialSize);
                            var alert = AlertFactory.confirmation(
                                    String.format(message("trimming.success"), trimmedFile.getName(), initialSizeMb, finalSizeMb, reduction)
                            );
                            alert.setHeaderText(message("trimming.success.title"));
                            alert.showAndWait().ifPresent(button -> {
                                if (button == ButtonType.OK) {
                                    doOpen(trimmedFile, false, lastExecutionProcessParams);
                                }
                            });
                        } else {
                            AlertFactory.error(message("trimming.failure"));
                        }
                    }
            );
        });
    }

    private ProcessingEventListener createListener(ProgressOperation rootOperation, String baseName, ProcessParams params, boolean batchMode, int sequenceNumber, Object context) {
        if (batchMode) {
            var batchProcessingContext = (BatchProcessingContext) context;
            var outputDirectory = batchProcessingContext.outputDirectory();
            var delegate = new SingleModeProcessingEventListener(this, rootOperation, baseName, null, outputDirectory.toPath(), params, ((BatchProcessingContext) context).processingDate(), popupViewers);
            return new BatchModeEventListener(this, rootOperation, delegate, sequenceNumber, batchProcessingContext, params);
        }
        var serFile = (File) context;
        var outputDirectory = serFile.getParentFile().toPath();
        return delegatingListener(new SingleModeProcessingEventListener(this, rootOperation, baseName, serFile, outputDirectory, params, LocalDateTime.now(), popupViewers));
    }

    private ProcessParamsController createProcessParams(File serFile, ProgressOperation progressOperation, SerFileReader serFileReader, boolean batchMode) {
        var dialog = newStage();
        dialog.setTitle(I18N.string(getClass(), "process-params", "process.parameters"));

        var controller = new ProcessParamsController();
        var md = CaptureSoftwareMetadataHelper.readSharpcapMetadata(serFile)
                .or(() -> CaptureSoftwareMetadataHelper.readFireCaptureMetadata(serFile))
                .orElse(null);

        controller.setup(dialog, progressOperation, evt -> {
                    if (evt instanceof ProgressEvent pg) {
                        updateProgress(pg.getPayload());
                    }
                },
                serFile, serFileReader.header(), md, batchMode, getHostServices());

        var scene = new Scene(controller.getRoot(), 1000, 700);
        scene.getStylesheets().add(
                getClass().getResource("/me/champeau/a4j/jsolex/app/components.css").toExternalForm()
        );

        dialog.setScene(scene);
        dialog.initOwner(rootStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.showAndWait();

        return controller;
    }

    @FXML
    private void exit() {
        System.exit(0);
    }

    @FXML
    private void saveLog() throws IOException {
        var saveWindow = new FileChooser();
        saveWindow.getExtensionFilters().add(LOG_FILE_EXTENSION_FILTER);
        var file = saveWindow.showSaveDialog(rootStage);
        if (file != null) {
            if (!file.getName().endsWith(LOG_EXTENSION)) {
                file = new File(file.getParentFile(), file.getName() + LOG_EXTENSION);
            }
            FilesUtils.writeString(console.getText(), file.toPath());
        }
    }

    @FXML
    void clearLog() {
        console.clear();
    }

    @FXML
    void resetUI() {
        closeAllButton.setDisable(true);
        deleteSerFileButton.setDisable(true);
        trimSerFileButton.setDisable(true);
        console.clear();
        mainPane.getTabs().clear();
        trimmingParameters = null;
        createFastModePane();
    }

    @FXML
    void deleteSerFile() {
        var confirmation = AlertFactory.confirmation(message("delete.ser.file.confirm"));
        confirmation.setTitle(message("delete.ser.file"));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            var recentFiles = config.getRecentFiles();
            if (!recentFiles.isEmpty()) {
                var toDelete = recentFiles.getFirst();
                findMetadataFile(toDelete).ifPresent(md -> {
                    try {
                        Files.deleteIfExists(md.toPath());
                    } catch (IOException e) {
                        LOGGER.error(message("error.cannot.delete.metadata"), e);
                    }
                });
                try {
                    Files.deleteIfExists(toDelete);
                } catch (IOException e) {
                    LOGGER.error(message("error.cannot.delete.ser"), e);
                }
            }
            resetUI();
            refreshRecentItemsMenu();
        }
    }

    /**
     * Retrieves a localized message by label.
     *
     * @param label the message label
     * @return the localized message
     */
    public static String message(String label) {
        var message = I18N.string(JSolEx.class, "messages", label);
        if (message.isEmpty()) {
            return Constants.message(label);
        }
        return message;
    }

    /**
     * Sets the JavaFX HostServices for opening URLs.
     *
     * @param hostServices the host services
     */
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @Override
    public HostServices getHostServices() {
        return hostServices;
    }

    /**
     * JavaFX Application launcher for JSolEx.
     */
    public static class Launcher extends Application {

        /**
         * Creates a new instance. Required by JavaFX.
         */
        public Launcher() {
        }

        @Override
        public void start(Stage stage) throws Exception {
            var jsolex = new JSolEx();
            jsolex.setHostServices(getHostServices());
            jsolex.start(stage);
        }
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Application.launch(Launcher.class, args);
    }

    private void setupLogWindowContextMenu() {
        var contextMenu = new ContextMenu();

        var copyItem = new MenuItem(I18N.string(JSolEx.class, "app", "copy"));
        copyItem.setOnAction(e -> {
            String selectedText = console.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                var clipboard = Clipboard.getSystemClipboard();
                var content = new ClipboardContent();
                content.putString(selectedText);
                clipboard.setContent(content);
            }
        });

        var selectAllItem = new MenuItem(I18N.string(JSolEx.class, "app", "select.all"));
        selectAllItem.setOnAction(e -> console.selectAll());

        contextMenu.getItems().addAll(copyItem, selectAllItem);
        console.setContextMenu(contextMenu);

        // Enable/disable menu items based on selection
        contextMenu.setOnShowing(e -> {
            boolean hasSelection = console.getSelectedText() != null && !console.getSelectedText().isEmpty();
            copyItem.setDisable(!hasSelection);
        });
    }

    private void setupImageMathEditorContextMenu() {
        var codeArea = imageMathScript.getCodeArea();
        var contextMenu = new ContextMenu();

        var copyItem = new MenuItem(I18N.string(JSolEx.class, "app", "copy"));
        copyItem.setOnAction(e -> {
            String selectedText = codeArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                var clipboard = Clipboard.getSystemClipboard();
                var content = new ClipboardContent();
                content.putString(selectedText);
                clipboard.setContent(content);
            }
        });

        var pasteItem = new MenuItem(I18N.string(JSolEx.class, "app", "paste"));
        pasteItem.setOnAction(e -> {
            var clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                String clipboardText = clipboard.getString();
                codeArea.replaceSelection(clipboardText);
            }
        });

        var selectAllItem = new MenuItem(I18N.string(JSolEx.class, "app", "select.all"));
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        var editMetadataItem = new MenuItem(I18N.string(JSolEx.class, "imagemath-editor", "edit.metadata"));
        editMetadataItem.setOnAction(e -> openMetadataEditor());

        contextMenu.getItems().addAll(copyItem, pasteItem, selectAllItem, new SeparatorMenuItem(), editMetadataItem);
        codeArea.setContextMenu(contextMenu);

        // Enable/disable menu items based on selection and clipboard content
        contextMenu.setOnShowing(e -> {
            boolean hasSelection = codeArea.getSelectedText() != null && !codeArea.getSelectedText().isEmpty();
            boolean hasClipboardText = Clipboard.getSystemClipboard().hasString();
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!hasClipboardText);
        });
    }

}
