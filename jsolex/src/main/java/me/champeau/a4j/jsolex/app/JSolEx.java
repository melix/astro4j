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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingFXUtils;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.DocsHelper;
import me.champeau.a4j.jsolex.app.jfx.ExplorerSupport;
import me.champeau.a4j.jsolex.app.jfx.ExposureCalculator;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageMathEditor;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.app.jfx.NamingPatternEditor;
import me.champeau.a4j.jsolex.app.jfx.ProcessParamsController;
import me.champeau.a4j.jsolex.app.jfx.SetupEditor;
import me.champeau.a4j.jsolex.app.jfx.SimpleMarkdownViewer;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineDebugger;
import me.champeau.a4j.jsolex.app.jfx.SpectralRayEditor;
import me.champeau.a4j.jsolex.app.jfx.SpectroHeliographEditor;
import me.champeau.a4j.jsolex.app.jfx.SpectrumBrowser;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.app.jfx.stacking.StackingAndMosaicController;
import me.champeau.a4j.jsolex.app.listeners.BatchModeEventListener;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.app.listeners.RedshiftImagesProcessor;
import me.champeau.a4j.jsolex.app.listeners.SingleModeProcessingEventListener;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.expr.InvalidExpression;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.LoggingSupport;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.math.VectorApiSupport;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageMetadata;
import me.champeau.a4j.ser.SerFileReader;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

public class JSolEx extends Application implements JSolExInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolEx.class);
    private static final String LOG_EXTENSION = ".log";
    private static final FileChooser.ExtensionFilter LOG_FILE_EXTENSION_FILTER = new FileChooser.ExtensionFilter("Log files (*" + LOG_EXTENSION + ")", "*" + LOG_EXTENSION);
    private static final FileChooser.ExtensionFilter SER_FILES_EXTENSION_FILTER = new FileChooser.ExtensionFilter("SER files", "*.ser", "*.SER");
    private static final int FILE_WATCH_TIMEOUT = 2_500;

    public static final Set<String> IMAGE_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "tif", "tiff", "fits", "fit");
    public static final FileChooser.ExtensionFilter IMAGE_FILES_EXTENSIONS = new FileChooser.ExtensionFilter("Image Files", IMAGE_FILE_EXTENSIONS.stream().map(ext -> "*." + ext).toList());

    private final Configuration config = new Configuration();

    Stage rootStage;

    @FXML
    private StyleClassedTextArea console;

    @FXML
    private Menu recentFilesMenu;

    @FXML
    private TabPane mainPane;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    @FXML
    private HBox workButtons;

    @FXML
    private Node imageMathPane;
    @FXML
    private ImageMathTextArea imageMathScript;
    @FXML
    private CheckBox clearImagesCheckbox;
    @FXML
    private Button imageMathRun;
    @FXML
    private Button imageMathLoad;
    @FXML
    private Button imageMathSave;
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
    private Tab metadataTab;

    @FXML
    private Tab redshiftTab;

    @FXML
    private Tab referenceImageTab;

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

    private final Map<String, ImageViewer> popupViewers = new ConcurrentHashMap<>();

    private final MultipleImagesViewer multipleImagesViewer = new MultipleImagesViewer();
    private ProcessParams reusedProcessParams;
    private ProcessParams lastExecutionProcessParams;
    private Path watchedDirectory;
    private WatchService watchService;
    private Button interruptWatchButton;
    private Button interruptClearParamsButton;
    private final BooleanBinding reusedProcessParamsBinding = Bindings.createBooleanBinding(() -> reusedProcessParams == null);

    @Override
    public MultipleImagesViewer getImagesViewer() {
        return multipleImagesViewer;
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
    public Tab getMetadataTab() {
        return metadataTab;
    }

    @Override
    public Tab getRedshiftTab() {
        return redshiftTab;
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.rootStage = stage;
        var fxmlLoader = I18N.fxmlLoader(getClass(), "app");
        fxmlLoader.setController(this);
        configureMemoryStatus();
        try {
            var root = (Parent) fxmlLoader.load();
            imageMathPane.setDisable(true);
            imageMathScript.setPrefHeight(10000);
            var preferredDimensions = config.getPreferredDimensions();
            Scene rootScene = new Scene(root, preferredDimensions.a(), preferredDimensions.b());
            rootScene.getStylesheets().add(JSolEx.class.getResource("syntax.css").toExternalForm());
            rootScene.getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
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
            stage.show();
            refreshRecentItemsMenu();
            LogbackConfigurer.configureLogger(console);
            stage.setOnCloseRequest(e -> System.exit(0));
            startWatcherThread();
            Thread.startVirtualThread(() -> UpdateChecker.findLatestRelease().ifPresent(this::maybeWarnAboutNewRelease));
            LOGGER.info("Java runtime version {}", System.getProperty("java.version"));
            LOGGER.info("Vector API support is {} and {}", VectorApiSupport.isPresent() ? "available" : "missing",
                VectorApiSupport.isEnabled() ? "enabled (disable by setting " + VectorApiSupport.VECTOR_API_ENV_VAR + " environment variable to false)" : "disabled");
            maybeShowWelcomeMessage(rootScene);
        } catch (IOException exception) {
            throw new ProcessingException(exception);
        }
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
        BatchOperations.submit(() -> redshiftCreatorKind.getSelectionModel().select(RedshiftImagesProcessor.RedshiftCreatorKind.ANIMATION));
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
        var webview = new SimpleMarkdownViewer(message("welcome"));
        var country = Locale.getDefault().getCountry();
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
                            BatchOperations.submit(() -> {
                                LOGGER.info(message("no.change.on.file"), child.getFileName());
                                doOpen(child.toFile(), true);
                            });
                        } else {
                            newFiles.put(child, currentSize);
                        }
                    } catch (IOException e) {
                        newFiles.remove(child);
                        LOGGER.error("Unable to determine size of {}", child);
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
                                    LOGGER.error("Unable to determine size of {}", child);
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
                    Thread.sleep(FILE_WATCH_TIMEOUT);
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
            BatchOperations.submit(() -> {
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
                        getHostServices().showDocument("https://github.com/melix/astro4j");
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
            var recent = new MenuItem(recentFile.toAbsolutePath().toString());
            recent.setOnAction(e -> doOpen(recentFile.toFile(), false));
            recentFilesMenu.getItems().add(recent);
        }
    }

    public void hideProgress() {
        progressBar.setProgress(0);
        progressLabel.setText("");
        progressBar.setVisible(false);
    }

    public void showProgress() {
        progressBar.setVisible(true);
    }

    public void updateProgress(double progress, String message) {
        progressBar.setProgress(progress);
        progressLabel.setText(message);
    }

    @Override
    public void prepareForScriptExecution(ImageMathScriptExecutor executor, ProcessParams params) {
        imageMathPane.setDisable(false);
        imageMathRun.setOnAction(evt -> {
            var text = imageMathScript.getText();
            if (clearImagesCheckbox.isSelected()) {
                BatchOperations.submit(this::newSession);
            }
            BackgroundOperations.async(() -> executor.execute(text, ImageMathScriptExecutor.SectionKind.SINGLE));
        });
        imageMathSave.setDisable(true);
        imageMathLoad.setOnAction(evt -> {
            var fileChooser = new FileChooser();
            config.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            fileChooser.getExtensionFilters().add(ImageMathEditor.MATH_SCRIPT_EXTENSION_FILTER);
            var file = fileChooser.showOpenDialog(rootStage);
            loadImageMathScriptFrom(file);
        });
        imageMathScript.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                imageMathSave.setDisable(false);
            }
        });
        imageMathSave.setOnAction(evt -> {
            var fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(ImageMathEditor.MATH_SCRIPT_EXTENSION_FILTER);
            var file = fileChooser.showSaveDialog(rootStage);
            if (file != null) {
                if (!file.getName().endsWith(ImageMathEditor.MATH_EXTENSION)) {
                    file = new File(file.getParentFile(), file.getName() + ImageMathEditor.MATH_EXTENSION);
                }
                try {
                    FilesUtils.writeString(imageMathScript.getText(), file.toPath());
                    imageMathSave.setDisable(true);
                    config.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
                } catch (IOException e) {
                    // ignore
                }
            }
        });
        var scriptFiles = params.requestedImages().mathImages().scriptFiles();
        if (!scriptFiles.isEmpty()) {
            loadImageMathScriptFrom(scriptFiles.get(0));
        }
    }

    private void loadImageMathScriptFrom(File file) {
        if (file != null) {
            config.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
            var script = String.join(System.lineSeparator(), FilesUtils.readAllLines(file.toPath()));
            BatchOperations.submit(() -> {
                imageMathScript.setText(script);
                imageMathSave.setDisable(true);
            });
        }
    }

    @FXML
    private void open() {
        selectSerFileAndThen(f -> doOpen(f, false));
    }

    @FXML
    private void openBatch() {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(SER_FILES_EXTENSION_FILTER);
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selectedFiles = fileChooser.showOpenMultipleDialog(rootStage);
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            if (LOGGER.isInfoEnabled()) {
                LoggingSupport.LOGGER.info(message("selected.files"), System.lineSeparator() + selectedFiles.stream().map(File::getName).collect(Collectors.joining(System.lineSeparator())));
            }
            doOpenMany(selectedFiles);
        } else {
            LoggingSupport.LOGGER.info(message("no.selected.file"));
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
                        BatchOperations.submit(() -> workButtons.getChildren().remove(interruptWatchButton));
                        interruptWatchButton = null;
                    }
                }
                watchService = watcher;
                watchedDirectory = directory.toPath();
                var key = watchedDirectory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
                interruptWatchButton = new Button(message("stop.watching"));
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
                    BatchOperations.submit(() -> {
                        workButtons.getChildren().remove(interruptWatchButton);
                        workButtons.getChildren().remove(interruptClearParamsButton);
                    });
                    LOGGER.info(message("stopped.watching"), watchedDirectory);
                });
                interruptClearParamsButton = addInterruptClearParamsButton();

                BatchOperations.submit(() -> workButtons.getChildren().add(interruptWatchButton));
            } catch (IOException e) {
                LOGGER.error("Cannot create watch service", e);
            }
        }
    }

    private Button addInterruptClearParamsButton() {
        var interruptClearParamsButton = new Button(message("interrupt.new.params"));
        interruptClearParamsButton.setOnAction(e -> {
            reusedProcessParams = null;
            reusedProcessParamsBinding.invalidate();
        });
        interruptClearParamsButton.disableProperty().bind(reusedProcessParamsBinding);
        BatchOperations.submit(() -> workButtons.getChildren().add(interruptClearParamsButton));
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
            SpectralLineDebugger.open(file, unused -> {
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
        ImageMathEditor.create(stage, params.requestedImages().mathImages(), getHostServices(), true, true, e -> {
        }, e -> {
            stage.close();
            BatchOperations.submit(this::newSession);
            e.getConfiguration().ifPresent(scripts -> BackgroundOperations.async(() -> executeStandaloneScripts(params.withRequestedImages(params.requestedImages().withMathImages(scripts)))));
        });
    }

    @FXML
    private void showExposureCalculator() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "exposure-calculator");
        try {
            var stage = newStage();
            var node = (Parent) fxmlLoader.load();
            var controller = (ExposureCalculator) fxmlLoader.getController();
            controller.setup(stage);
            Scene scene = new Scene(node);
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
            controller.setup(stage, this, ProcessParams.loadDefaults(), popupViewers);
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
        stage.setScene(new Scene(browser));
        stage.setTitle(I18N.string(JSolEx.class, "spectrum-browser", "frame.title"));
        stage.show();
    }

    private void executeStandaloneScripts(ProcessParams params) {
        var scriptFiles = params.requestedImages().mathImages().scriptFiles();
        var scriptFile = scriptFiles.stream().findFirst();
        scriptFile.ifPresent(script -> {
            var outputDirectory = script.getParentFile();
            var processingDate = LocalDateTime.now();
            var listener = new SingleModeProcessingEventListener(this, "", null, outputDirectory.toPath(), params, processingDate, popupViewers);
            var namingStrategy = new FileNamingStrategy(params.extraParams().fileNamePattern(), params.extraParams().datetimeFormat(), params.extraParams().dateFormat(), processingDate, createFakeHeader(processingDate));
            var imageScriptExecutor = new JSolExScriptExecutor(img -> {
                throw new ProcessingException("img() is not available in standalone image math scripts. Use load or load_many to load images");
            }, MutableMap.of(), listener, null) {
                @Override
                public ImageMathScriptResult execute(String script, SectionKind kind) {
                    var result = super.execute(script, kind);
                    processResult(result);
                    return result;
                }

                private void processResult(ImageMathScriptResult result) {
                    result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
                        var name = namingStrategy.render(0, Constants.TYPE_PROCESSED, entry.getKey(), "standalone");
                        var outputFile = new File(outputDirectory, name);
                        listener.onImageGenerated(new ImageGeneratedEvent(new GeneratedImage(GeneratedImageKind.IMAGE_MATH, entry.getKey(), outputFile.toPath(), entry.getValue())));

                    });
                    result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
                        var name = namingStrategy.render(0, Constants.TYPE_PROCESSED, entry.getKey(), "standalone");
                        try {
                            var fileName = entry.getValue().toFile().getName();
                            var ext = fileName.substring(fileName.lastIndexOf("."));
                            var targetPath = new File(outputDirectory, name + ext).toPath();
                            Files.createDirectories(targetPath.getParent());
                            Files.move(entry.getValue(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                            listener.onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, entry.getKey(), targetPath));
                        } catch (IOException e) {
                            throw new ProcessingException(e);
                        }
                    });
                    for (InvalidExpression expression : result.invalidExpressions()) {
                        LOGGER.error("Found invalid expression {} ({}): {}", expression.label(), expression.expression(), expression.error().getMessage());
                    }
                }
            };
            for (File file : scriptFiles) {
                try {
                    imageScriptExecutor.execute(file.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
                prepareForScriptExecution(imageScriptExecutor, params);
            }
        });
    }

    public static Header createFakeHeader(LocalDateTime now) {
        return new Header(null, null, 0, new ImageMetadata(null, null, null, true, now, now.atZone(ZoneId.of("UTC"))));
    }

    @FXML
    private void about() {
        var alert = AlertFactory.info();
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(700, 400);
        String version = VersionUtil.getVersion();
        alert.setTitle(I18N.string(getClass(), "about", "about.title"));
        alert.setHeaderText(I18N.string(getClass(), "about", "about.header") + ". Version " + version);
        alert.setContentText(I18N.string(getClass(), "about", "about.message"));
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

    private void doOpen(File selectedFile, boolean rememberProcessParams) {
        imageMathPane.setDisable(true);
        config.loadedSerFile(selectedFile.toPath());
        configureThreadExceptionHandler();
        BatchOperations.submit(this::refreshRecentItemsMenu);
        Optional<ProcessParams> processParams;
        Header header;
        try (var reader = SerFileReader.of(selectedFile)) {
            header = reader.header();
            if (reusedProcessParams != null) {
                processParams = Optional.of(reusedProcessParams.withObservationDetails(reusedProcessParams.observationDetails().withDate(header.metadata().utcDateTime())));
            } else {
                var controller = createProcessParams(selectedFile, reader, false);
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
            processFileWithParams(selectedFile, firstHeader, params);
        });
    }

    private void processFileWithParams(File selectedFile, Header firstHeader, ProcessParams params) {
        newSession();
        console.clear();
        var interruptButton = addInterruptButton();
        var processingThread =
            new Thread(() -> processSingleFile(params, selectedFile, false, 0, selectedFile, firstHeader, () -> BatchOperations.submit(() -> workButtons.getChildren().remove(interruptButton))));
        interruptButton.setOnAction(e -> {
            BatchOperations.submit(() -> {
                updateProgress(0, message("interrupted"));
                workButtons.getChildren().remove(interruptButton);
            });
            processingThread.interrupt();
        });
        processingThread.start();
    }

    public void newSession() {
        mainPane.getTabs().clear();
        mainPane.getTabs().add(new Tab(message("images"), multipleImagesViewer));
        multipleImagesViewer.clear();
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
        BatchOperations.submit(() -> {
            redshiftTab.setDisable(redshifts.isEmpty());
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
                    if (Files.getFileStore(Path.of(System.getProperty("java.io.tmpdir"))).getUsableSpace() < processor.estimateRequiredBytesForProcessingWithMargin(margin)) {
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
                        BatchOperations.submit(() -> rightTabs.getSelectionModel().select(logsTab));
                        processor.withRedshifts(selectedShifts.stream().toList()).produceImages(kind, size, margin, useFullRangePanels, annotate);
                    });
                }
            });
            redshiftBoxSize.getSelectionModel().select((Integer) boxSize);
        });
    }

    @Override
    public void prepareForGongImageDownload(ProcessParams processParams) {
        var vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.setOpaqueInsets(new Insets(4));
        vbox.setSpacing(4);
        var button = new Button(message("download.gong.image"));
        var imageView = new ImageView();
        var date = new Label();
        button.setOnAction(e -> {
            button.setText(message("downloading.gong.image"));
            button.setDisable(true);
            BackgroundOperations.async(() -> {
                var optionalURL = GONG.fetchGongImage(processParams.observationDetails().date());
                BatchOperations.submit(() -> {
                    date.setText(message("file.date") + " " + processParams.observationDetails().date().format(GONG.FORMATTER));
                    optionalURL.ifPresentOrElse(
                        url -> {
                            imageView.setImage(new Image(url.toExternalForm()));
                            // add context menu to image
                            var contextMenu = new ContextMenu();
                            var save = new MenuItem(message("save.gong.image"));
                            save.setOnAction(ev -> {
                                var fileChooser = new FileChooser();
                                fileChooser.getExtensionFilters().add(IMAGE_FILES_EXTENSIONS);
                                var file = fileChooser.showSaveDialog(rootStage);
                                if (file != null) {
                                    BackgroundOperations.async(() -> {
                                        var image = imageView.getImage();
                                        try {
                                            var outputFile = file;
                                            var extIndex = outputFile.getName().lastIndexOf(".");
                                            if (extIndex == -1) {
                                                outputFile = new File(file.getParent(), file.getName() + ".png");
                                            }
                                            ImageIO.write(SwingFXUtils.fromFXImage(image, null), outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1), outputFile);
                                        } catch (IOException ex) {
                                            LOGGER.error("Cannot save image", e);
                                        }
                                    });
                                }
                            });
                            contextMenu.getItems().add(save);
                            imageView.setOnContextMenuRequested(ev -> contextMenu.show(imageView, ev.getScreenX(), ev.getScreenY()));
                            vbox.getChildren().remove(button);
                        },
                        () -> {
                            date.setText(message("no.image.available"));
                            button.setDisable(false);
                        });
                });
            });
        });
        BatchOperations.submit(() -> {
            vbox.getChildren().addAll(button, date, imageView);
            var scrollPane = new ScrollPane(vbox);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            referenceImageTab.setContent(scrollPane);
        });
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
        imageMathPane.setDisable(true);
        configureThreadExceptionHandler();
        Optional<ProcessParams> processParams = Optional.empty();
        Header header = null;
        for (var selectedFile : selectedFiles) {
            try (var reader = SerFileReader.of(selectedFile)) {
                var controller = createProcessParams(selectedFile, reader, true);
                processParams = controller.getProcessParams();
                header = reader.header();
                config.updateLastOpenDirectory(selectedFile.toPath().getParent());
                break;
            } catch (Exception e) {
                throw ProcessingException.wrap(e);
            }
        }
        var firstHeader = header;
        processParams.ifPresent(params -> startBatchProcess(firstHeader, params, selectedFiles));

    }

    private void startBatchProcess(Header header, ProcessParams params, List<File> selectedFiles) {
        newSession();
        LOGGER.info(message("batch.mode.info"));
        var tab = new Tab(message("batch.process"));
        var table = new TableView<BatchItem>();
        var batchItems = new ArrayList<BatchItem>(selectedFiles.size());
        for (int i = 0; i < selectedFiles.size(); i++) {
            var selectedFile = selectedFiles.get(i);
            batchItems.add(new BatchItem(i, selectedFile, new SimpleDoubleProperty(0), FXCollections.synchronizedObservableList(FXCollections.observableArrayList()), new SimpleStringProperty(message("batch.pending")), new StringBuilder()));
        }
        table.getItems().addAll(batchItems);
        var idColumn = new TableColumn<BatchItem, String>();
        idColumn.setText("#");
        idColumn.setCellValueFactory(param -> new SimpleStringProperty(String.format("%04d", param.getValue().id())));
        var fnColumn = new TableColumn<BatchItem, String>();
        fnColumn.setText(message("filename"));
        fnColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().file().getName()));
        var progressColumn = new TableColumn<BatchItem, Number>();
        progressColumn.setText(message("reconstruction"));
        progressColumn.setCellValueFactory(param -> param.getValue().reconstructionProgress());
        progressColumn.setCellFactory(new ProgressCellFactory());
        var images = new TableColumn<BatchItem, List<File>>();
        images.setText(message("images"));
        images.setCellValueFactory(param -> Bindings.createObjectBinding(() -> new ArrayList<>(param.getValue().generatedFiles()), param.getValue().generatedFiles()));
        images.setCellFactory(new ImageLinksFactory());
        var statusColumn = new TableColumn<BatchItem, String>();
        statusColumn.setText(message("status"));
        statusColumn.setCellValueFactory(param -> param.getValue().status());
        var firstColumnsWidth = idColumn.widthProperty().add(fnColumn.widthProperty().add(progressColumn.widthProperty())).add(statusColumn.widthProperty()).add(20);
        images.prefWidthProperty().bind(table.widthProperty().subtract(firstColumnsWidth));
        var columns = table.getColumns();
        columns.setAll(idColumn, fnColumn, progressColumn, images, statusColumn);
        tab.setContent(table);
        mainPane.getTabs().addFirst(tab);
        mainPane.getSelectionModel().select(0);
        var interruptButton = addInterruptButton();
        new Thread(() -> {
            configureThreadExceptionHandler();
            var groups = new ArrayList<List<File>>();
            var current = new ArrayList<File>();
            var batchSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
            for (File selectedFile : selectedFiles) {
                current.add(selectedFile);
                if (current.size() == batchSize) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            }
            if (!current.isEmpty()) {
                groups.add(current);
            }
            var batchContext =
                new BatchProcessingContext(batchItems, Collections.synchronizedSet(new HashSet<>()), Collections.synchronizedSet(new HashSet<>()), new AtomicBoolean(), selectedFiles.get(0).getParentFile(), LocalDateTime.now(), new HashMap<>(),
                    header);
            var semaphore = new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
            // We're using a separate task submission thread in order to not
            // block the processing ones
            var taskSubmissionThread = new Thread(() -> {
                int idx = 0;
                while (idx < selectedFiles.size() && !Thread.currentThread().isInterrupted()) {
                    semaphore.acquireUninterruptibly();
                    var selectedFile = selectedFiles.get(idx);
                    var fileIdx = idx;
                    BackgroundOperations.async(() -> processSingleFile(params, selectedFile, true, fileIdx, batchContext, header, semaphore::release));
                    idx++;
                }
            });
            interruptButton.setOnAction(e -> {
                interruptButton.setDisable(true);
                taskSubmissionThread.interrupt();
                BackgroundOperations.interrupt();
                updateProgress(0, message("batch.interrupted"));
            });
            taskSubmissionThread.start();
            try {
                taskSubmissionThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                BatchOperations.submit(() -> workButtons.getChildren().remove(interruptButton));
            }
        }).start();
    }

    private Button addInterruptButton() {
        var interruptButton = new Button(message("interrupt"));
        BatchOperations.submit(() -> workButtons.getChildren().add(interruptButton));
        return interruptButton;
    }

    private void processSingleFile(ProcessParams params, File selectedFile, boolean batchMode, int sequenceNumber, Object context, Header header, Runnable onComplete) {
        lastExecutionProcessParams = params;
        LogbackConfigurer.recordThreadOwner(Thread.currentThread().getName(), sequenceNumber);
        var processingDate = context instanceof BatchProcessingContext batch ? batch.processingDate() : LocalDateTime.now();
        var namingStrategy = new FileNamingStrategy(params.extraParams().fileNamePattern(), params.extraParams().datetimeFormat(), params.extraParams().dateFormat(), processingDate, header);
        var outputDirectory = selectedFile.getParentFile();
        var baseName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf("."));
        var logFileName = namingStrategy.render(sequenceNumber, "log", "log", baseName) + LOG_EXTENSION;
        var logFile = new File(outputDirectory, logFileName);
        // For the log file we cannot _fully_ use the pattern since some data is not yet available (the file header)
        logFile = new File(logFile.getParentFile(), String.format("%04d_%s" + LOG_EXTENSION, sequenceNumber, baseName));
        if (context instanceof BatchProcessingContext ctx) {
            ctx.items().get(sequenceNumber).generatedFiles().add(logFile);
        }
        var appender = LogbackConfigurer.createContextualFileAppender(sequenceNumber, logFile);
        LoggingSupport.LOGGER.info(message("output.dir.set"), outputDirectory);
        var processor = new SolexVideoProcessor(selectedFile, outputDirectory.toPath(), sequenceNumber, params, processingDate, batchMode);
        var listener = createListener(baseName, params, batchMode, sequenceNumber, context);
        processor.addEventListener(listener);
        try {
            LOGGER.info("File {}", selectedFile.getName());
            processor.process();
        } catch (Exception ex) {
            LoggingSupport.logError(ex);
        } finally {
            onComplete.run();
            appender.stop();
            LogbackConfigurer.clearOwners();
        }
    }

    private ProcessingEventListener createListener(String baseName, ProcessParams params, boolean batchMode, int sequenceNumber, Object context) {
        if (batchMode) {
            var batchProcessingContext = (BatchProcessingContext) context;
            var outputDirectory = batchProcessingContext.outputDirectory();
            var delegate = new SingleModeProcessingEventListener(this, baseName, null, outputDirectory.toPath(), params, ((BatchProcessingContext) context).processingDate(), popupViewers);
            return new BatchModeEventListener(this, delegate, sequenceNumber, batchProcessingContext, params);
        }
        var serFile = (File) context;
        var outputDirectory = serFile.getParentFile().toPath();
        return new SingleModeProcessingEventListener(this, baseName, serFile, outputDirectory, params, LocalDateTime.now(), popupViewers);
    }

    private ProcessParamsController createProcessParams(File serFile, SerFileReader serFileReader, boolean batchMode) {
        var loader = I18N.fxmlLoader(getClass(), "process-params");
        try {
            var dialog = newStage();
            dialog.setTitle(I18N.string(getClass(), "process-params", "process.parameters"));
            var content = (Parent) loader.load();
            var controller = (ProcessParamsController) loader.getController();
            var scene = new Scene(content);
            var md = CaptureSoftwareMetadataHelper.readSharpcapMetadata(serFile)
                .or(() -> CaptureSoftwareMetadataHelper.readFireCaptureMetadata(serFile))
                .orElse(null);
            controller.setup(dialog, serFile, serFileReader.header(), md, batchMode, getHostServices());
            dialog.setScene(scene);
            dialog.initOwner(rootStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
            return controller;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static String message(String label) {
        var message = I18N.string(JSolEx.class, "messages", label);
        if (message.isEmpty()) {
            return Constants.message(label);
        }
        return message;
    }

    public static void main(String[] args) {
        launch();
    }

    private static class ProgressCellFactory implements Callback<TableColumn<BatchItem, Number>, TableCell<BatchItem, Number>> {
        @Override
        public TableCell<BatchItem, Number> call(TableColumn<BatchItem, Number> param) {
            var cell = new TableCell<BatchItem, Number>();
            cell.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    var node = cell.graphicProperty().get();
                    if (node instanceof ProgressBar progress) {
                        progress.setProgress(newValue.doubleValue());
                    } else {
                        var progress = new ProgressBar(newValue.doubleValue());
                        cell.graphicProperty().set(progress);
                    }
                }
            });
            return cell;
        }
    }

    private static class ImageLinksFactory implements Callback<TableColumn<BatchItem, List<File>>, TableCell<BatchItem, List<File>>> {

        @Override
        public TableCell<BatchItem, List<File>> call(TableColumn<BatchItem, List<File>> param) {
            var cell = new TableCell<BatchItem, List<File>>();
            cell.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && !newValue.isEmpty()) {
                    var vbox = (VBox) cell.graphicProperty().get();
                    if (vbox == null) {
                        vbox = new VBox();
                        cell.graphicProperty().set(vbox);
                    } else {
                        vbox.getChildren().clear();
                    }
                    for (File file : newValue) {
                        var link = new Hyperlink(file.getName());
                        link.setOnAction(e -> ExplorerSupport.openInExplorer(file.toPath()));
                        vbox.getChildren().add(link);
                    }
                }
            });
            return cell;
        }
    }

}
