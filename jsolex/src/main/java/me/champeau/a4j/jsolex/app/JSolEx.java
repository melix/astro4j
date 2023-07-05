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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.jfx.BatchItem;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.ExplorerSupport;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageMathEditor;
import me.champeau.a4j.jsolex.app.jfx.NamingPatternEditor;
import me.champeau.a4j.jsolex.app.jfx.ProcessParamsController;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineDebugger;
import me.champeau.a4j.jsolex.app.jfx.SpectralRayEditor;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.app.listeners.BatchModeEventListener;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.app.listeners.SingleModeProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ForkJoinParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.LoggingSupport;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.VectorApiSupport;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageMetadata;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

public class JSolEx extends Application implements JSolExInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolEx.class);
    private static final String LOG_EXTENSION = ".log";
    private static final FileChooser.ExtensionFilter LOG_FILE_EXTENSION_FILTER = new FileChooser.ExtensionFilter("Log files (*" + LOG_EXTENSION + ")", "*" + LOG_EXTENSION);
    private static final FileChooser.ExtensionFilter SER_FILES_EXTENSION_FILTER = new FileChooser.ExtensionFilter("SER files", "*.ser");

    private ForkJoinParallelExecutor cpuExecutor;
    private ForkJoinParallelExecutor ioExecutor;

    private final Configuration config = new Configuration();

    Stage rootStage;

    @FXML
    private TextArea console;

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
    private Button imageMathRun;
    @FXML
    private Button imageMathLoad;
    @FXML
    private Button imageMathSave;
    @FXML
    private ProgressBar memory;
    @FXML
    private Label memoryLabel;

    @Override
    public ForkJoinContext getCpuExecutor() {
        return cpuExecutor;
    }

    @Override
    public TabPane getMainPane() {
        return mainPane;
    }

    @Override
    public void start(Stage stage) throws Exception {
        prepareExecutors();
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
            stage.setTitle("JSol'Ex " + getVersion());
            stage.setScene(rootScene);
            addIcons(stage);
            stage.show();
            refreshRecentItemsMenu();
            LogbackConfigurer.configureLogger(console);
            cpuExecutor.setUncaughtExceptionHandler((t, e) -> logError(e));
            ioExecutor.setUncaughtExceptionHandler((t, e) -> logError(e));
            stage.setOnCloseRequest(e -> {
                try {
                    cpuExecutor.close();
                } catch (Exception ex) {
                    // ignore
                }
                try {
                    ioExecutor.close();
                } catch (Exception ex) {
                    // ignore
                }
                System.exit(0);
            });
            BatchOperations.submit(() -> UpdateChecker.findLatestRelease().ifPresent(this::maybeWarnAboutNewRelease));
            LOGGER.info("Java runtime version {}", System.getProperty("java.version"));
            LOGGER.info("Vector API support is {} and {}", VectorApiSupport.isPresent() ? "available" : "missing", VectorApiSupport.isEnabled() ? "enabled" : "disabled (enable by setting " + VectorApiSupport.VECTOR_API_ENV_VAR + " environment variable to true)");
        } catch (IOException exception) {
            throw new ProcessingException(exception);
        }
    }

    private void maybeWarnAboutNewRelease(UpdateChecker.ReleaseInfo release) {
        var currentVersion = toVersionLong(getVersion());
        var latestRelease = toVersionLong(release.version());
        if (latestRelease > currentVersion) {
            var alert = new Alert(Alert.AlertType.INFORMATION);
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
            var totalMemory = Runtime.getRuntime().totalMemory() >> 20;
            var maxMemory = Runtime.getRuntime().maxMemory() >> 20;
            memory.setProgress(totalMemory / (double) maxMemory);
            memoryLabel.setText(String.format("%d M / %d M", totalMemory, maxMemory));
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void closeExecutors() {
        if (cpuExecutor != null) {
            try {
                cpuExecutor.close();
            } catch (Exception e) {
                // ignore
            }
        }
        if (ioExecutor != null) {
            try {
                ioExecutor.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void prepareExecutors() {
        cpuExecutor = ForkJoinParallelExecutor.newExecutor();
        ioExecutor = ForkJoinParallelExecutor.newExecutor(1);
    }

    private void addIcons(Stage stage) {
        for (int i = 16; i <= 512; i *= 2) {
            stage.getIcons().add(
                    new Image(getClass().getResourceAsStream("icons/jsolex-" + i + "x" + i + ".png"))
            );
        }
    }

    private void refreshRecentItemsMenu() {
        recentFilesMenu.getItems().clear();
        for (Path recentFile : config.getRecentFiles()) {
            var recent = new MenuItem(recentFile.toAbsolutePath().toString());
            recent.setOnAction(e -> doOpen(recentFile.toFile()));
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
            cpuExecutor.async(() -> executor.execute(text));
        });
        imageMathSave.setDisable(true);
        imageMathLoad.setOnAction(evt -> {
            var fileChooser = new FileChooser();
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
            var script = String.join(System.lineSeparator(), FilesUtils.readAllLines(file.toPath()));
            BatchOperations.submit(() -> {
                imageMathScript.setText(script);
                imageMathSave.setDisable(true);
            });
        }
    }

    @FXML
    private void open() {
        selectSerFileAndThen(this::doOpen);
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
            var fxmlLoader = I18N.fxmlLoader(getClass(), "frame-debugger");
            Object configWindow;
            try {
                configWindow = fxmlLoader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            var controller = (SpectralLineDebugger) fxmlLoader.getController();
            var stage = new Stage();
            Scene scene = new Scene((Parent) configWindow);
            controller.open(file, null, scene, stage);
            stage.setTitle(I18N.string(getClass(), "frame-debugger", "frame.debugger"));
            stage.setScene(scene);
            stage.showAndWait();
        });

    }

    @FXML
    private void showFileNamePatternEditor() {
        var now = LocalDateTime.now();
        var stage = new Stage();
        NamingPatternEditor.openEditor(stage, createFakeHeader(now), e -> stage.close());
    }

    @FXML
    private void showSpectralRayEditor() {
        var stage = new Stage();
        SpectralRayEditor.openEditor(stage, e -> stage.close());
    }

    private static Header createFakeHeader(LocalDateTime now) {
        return new Header(null, null, 0, new ImageMetadata(
                null,
                null,
                null,
                true,
                now,
                now.atZone(ZoneId.of("UTC"))
        ));
    }

    @FXML
    private void about() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(700, 400);
        String version = getVersion();
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

    private static String getVersion() {
        String version = "";
        try {
            version = new String(JSolEx.class.getResourceAsStream("/version.txt").readAllBytes(), "utf-8").trim();
            if (version.contains("-SNAPSHOT")) {
                version = version.substring(0, version.indexOf("-SNAPSHOT"));
            }
        } catch (IOException e) {
            version = "unknown";
        }
        return version;
    }

    private void doOpen(File selectedFile) {
        imageMathPane.setDisable(true);
        config.loaded(selectedFile.toPath());
        configureThreadExceptionHandler();
        BatchOperations.submit(this::refreshRecentItemsMenu);
        Optional<ProcessParams> processParams;
        Header header = null;
        try (var reader = SerFileReader.of(selectedFile)) {
            var controller = createProcessParams(reader, false);
            processParams = controller.getProcessParams();
            header = reader.header();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        var firstHeader = header;
        processParams.ifPresent(params -> {
            mainPane.getTabs().clear();
            console.textProperty().set("");
            var interruptButton = addInterruptButton();
            var processingThread = new Thread(() -> cpuExecutor.blocking(() ->
                    processSingleFile(cpuExecutor, params, selectedFile, false, 0, null, firstHeader, () -> {
                        BatchOperations.submit(() -> workButtons.getChildren().remove(interruptButton));
                    })
            ));
            interruptButton.setOnAction(e -> {
                closeExecutors();
                prepareExecutors();
                BatchOperations.submit(() -> updateProgress(0, message("interrupted")));
                workButtons.getChildren().remove(interruptButton);
                processingThread.interrupt();
            });
            processingThread.start();
        });
    }

    private static void configureThreadExceptionHandler() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> logError(e));
    }

    private void doOpenMany(List<File> selectedFiles) {
        imageMathPane.setDisable(true);
        configureThreadExceptionHandler();
        File initial = selectedFiles.get(0);
        config.updateLastOpenDirectory(initial.toPath().getParent());
        Optional<ProcessParams> processParams;
        Header header = null;
        try (var reader = SerFileReader.of(initial)) {
            var controller = createProcessParams(reader, true);
            processParams = controller.getProcessParams();
            header = reader.header();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        var firstHeader = header;
        processParams.ifPresent(params -> startProcess(firstHeader, params, selectedFiles));

    }

    private void startProcess(Header header, ProcessParams params, List<File> selectedFiles) {
        mainPane.getTabs().clear();
        LOGGER.info(message("batch.mode.info"));
        var tab = new Tab(message("batch.process"));
        var table = new TableView<BatchItem>();
        var batchItems = new ArrayList<BatchItem>(selectedFiles.size());
        for (int i = 0; i < selectedFiles.size(); i++) {
            var selectedFile = selectedFiles.get(i);
            batchItems.add(new BatchItem(
                    i,
                    selectedFile,
                    new SimpleDoubleProperty(0),
                    FXCollections.synchronizedObservableList(FXCollections.observableArrayList()),
                    new SimpleStringProperty(message("batch.pending")),
                    new StringBuilder()
            ));
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
        mainPane.getTabs().add(tab);
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
            var batchContext = new BatchProcessingContext(batchItems, new AtomicInteger(), selectedFiles.get(0).getParentFile(), LocalDateTime.now());
            cpuExecutor.blocking(() -> {
                var semaphore = new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
                // We're using a separate task submission thread in order to not
                // block the processing ones
                var taskSubmissionThread = new Thread(() -> {
                    int idx = 0;
                    while (idx < selectedFiles.size() && !Thread.currentThread().isInterrupted()) {
                        semaphore.acquireUninterruptibly();
                        var selectedFile = selectedFiles.get(idx);
                        processSingleFile(cpuExecutor, params, selectedFile, true, idx, batchContext, header, semaphore::release);
                        idx++;
                    }
                });
                interruptButton.setOnAction(e -> {
                    interruptButton.setDisable(true);
                    taskSubmissionThread.interrupt();
                    closeExecutors();
                    prepareExecutors();
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
            });
        }).start();
    }

    private Button addInterruptButton() {
        var interruptButton = new Button(message("interrupt"));
        workButtons.getChildren().add(interruptButton);
        return interruptButton;
    }

    private void processSingleFile(ForkJoinContext cpu,
                                   ProcessParams params,
                                   File selectedFile,
                                   boolean batchMode,
                                   int sequenceNumber,
                                   Object context,
                                   Header header,
                                   Runnable onComplete) {
        cpu.isolate(cpuIsolate -> {
            cpuIsolate.setOnTaskStart(t -> LogbackConfigurer.recordThreadOwner(t.getName(), sequenceNumber));
            ioExecutor.isolate(ioIsolate -> cpuIsolate.async(() -> {
                var processingDate = context instanceof BatchProcessingContext batch ? batch.processingDate() : LocalDateTime.now();
                ioIsolate.setOnTaskStart(t -> LogbackConfigurer.recordThreadOwner(t.getName(), sequenceNumber));
                var namingStrategy = new FileNamingStrategy(
                        params.extraParams().fileNamePattern(),
                        params.extraParams().datetimeFormat(),
                        params.extraParams().dateFormat(),
                        processingDate,
                        header
                );
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
                var processor = new SolexVideoProcessor(selectedFile,
                        outputDirectory.toPath(),
                        sequenceNumber,
                        params,
                        cpuIsolate,
                        ioIsolate,
                        processingDate,
                        batchMode
                );
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
            }));
        });
    }

    private ProcessingEventListener createListener(String baseName, ProcessParams params, boolean batchMode, int sequenceNumber, Object context) {
        if (batchMode) {
            return new BatchModeEventListener(this, sequenceNumber, (BatchProcessingContext) context, params);
        }
        return new SingleModeProcessingEventListener(this, baseName, params);
    }

    private ProcessParamsController createProcessParams(SerFileReader serFileReader, boolean batchMode) {
        var loader = I18N.fxmlLoader(getClass(), "process-params");
        try {
            var dialog = new Stage();
            dialog.setTitle(I18N.string(getClass(), "process-params", "process.parameters"));
            var content = (Parent) loader.load();
            var controller = (ProcessParamsController) loader.getController();
            var scene = new Scene(content);
            controller.setup(dialog, serFileReader.header(), batchMode);
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
