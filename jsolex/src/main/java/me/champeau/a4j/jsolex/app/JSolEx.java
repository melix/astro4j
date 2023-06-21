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

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import me.champeau.a4j.jsolex.app.jfx.ProcessParamsController;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineDebugger;
import me.champeau.a4j.jsolex.app.listeners.BatchModeEventListener;
import me.champeau.a4j.jsolex.app.listeners.BatchProcessingContext;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.app.listeners.SingleModeProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ForkJoinParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.LoggingSupport;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.VectorApiSupport;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

public class JSolEx extends Application implements JSolExInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolEx.class);

    private final ForkJoinParallelExecutor cpuExecutor = ForkJoinParallelExecutor.newExecutor();
    private final ForkJoinParallelExecutor ioExecutor = ForkJoinParallelExecutor.newExecutor(1);

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
        this.rootStage = stage;
        var fxmlLoader = I18N.fxmlLoader(getClass(), "app");
        fxmlLoader.setController(this);

        try {
            var root = (Parent) fxmlLoader.load();
            var preferredDimensions = config.getPreferredDimensions();
            Scene rootScene = new Scene(root, preferredDimensions.a(), preferredDimensions.b());
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
            stage.setTitle("JSol'Ex");
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
            LOGGER.info("Java runtime version {}", System.getProperty("java.version"));
            LOGGER.info("Vector API support is {} and {}", VectorApiSupport.isPresent() ? "available" : "missing", VectorApiSupport.isEnabled() ? "enabled" : "disabled (enable by setting " + VectorApiSupport.VECTOR_API_ENV_VAR + " environment variable to true)");
        } catch (IOException exception) {
            throw new ProcessingException(exception);
        }
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

    @FXML
    private void open() {
        selectSerFileAndThen(this::doOpen);
    }

    @FXML
    private void openBatch() {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SER files", "*.ser"));
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selectedFiles = fileChooser.showOpenMultipleDialog(rootStage);
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            LoggingSupport.LOGGER.info("Selected files {}", selectedFiles.stream().map(File::getName).toList());
            doOpenMany(selectedFiles);
        } else {
            LoggingSupport.LOGGER.info("No selected file, processing cancelled.");
        }
    }

    private void selectSerFileAndThen(Consumer<? super File> consumer) {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SER files", "*.ser"));
        config.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        var selectedFile = fileChooser.showOpenDialog(rootStage);
        if (selectedFile != null) {
            LoggingSupport.LOGGER.info("Selected file {}", selectedFile);
            consumer.accept(selectedFile);
        } else {
            LoggingSupport.LOGGER.info("No selected file, processing cancelled.");
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
            Scene scene = new Scene((Parent) configWindow, 1024, 400);
            controller.open(file, null, scene);
            stage.setTitle(I18N.string(getClass(), "frame-debugger", "frame.debugger"));
            stage.setScene(scene);
            stage.showAndWait();
        });

    }

    @FXML
    private void about() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(700, 400);
        alert.setTitle(I18N.string(getClass(), "about", "about.title"));
        alert.setHeaderText(I18N.string(getClass(), "about", "about.header"));
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

    private void doOpen(File selectedFile) {
        config.loaded(selectedFile.toPath());
        configureThreadExceptionHandler();
        BatchOperations.submit(this::refreshRecentItemsMenu);
        Optional<ProcessParams> processParams;
        try (var reader = SerFileReader.of(selectedFile)) {
            var controller = createProcessParams(reader, false);
            processParams = controller.getProcessParams();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        processParams.ifPresent(params -> {
            mainPane.getTabs().clear();
            console.textProperty().set("");
            new Thread(() -> cpuExecutor.blocking(context ->
                    processSingleFile(context, params, selectedFile, false, 0, null, () -> {
                    })
            )).start();
        });
    }

    private static void configureThreadExceptionHandler() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> logError(e));
    }

    private void doOpenMany(List<File> selectedFiles) {
        configureThreadExceptionHandler();
        File initial = selectedFiles.get(0);
        config.updateLastOpenDirectory(initial.toPath().getParent());
        Optional<ProcessParams> processParams;
        try (var reader = SerFileReader.of(initial)) {
            var controller = createProcessParams(reader, true);
            processParams = controller.getProcessParams();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        processParams.ifPresent(params -> startProcess(params, selectedFiles));

    }

    private void startProcess(ProcessParams params, List<File> selectedFiles) {
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
            cpuExecutor.blocking(context -> {
                var semaphore = new Semaphore(Runtime.getRuntime().availableProcessors() / 2);
                // We're using a separate task submission thread in order to not
                // block the processing ones
                var taskSubmissionThread = new Thread(() -> {
                    int idx = 0;
                    while (idx < selectedFiles.size()) {
                        semaphore.acquireUninterruptibly();
                        var selectedFile = selectedFiles.get(idx);
                        processSingleFile(context, params, selectedFile, true, idx, batchContext, semaphore::release);
                        idx++;
                    }
                });
                taskSubmissionThread.start();
                try {
                    taskSubmissionThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }).start();
    }

    private void processSingleFile(ForkJoinContext cpu,
                                   ProcessParams params,
                                   File selectedFile,
                                   boolean batchMode,
                                   int sequenceNumber,
                                   Object context,
                                   Runnable onComplete) {
        cpu.async(() -> {
            var baseName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf("."));
            var outputDirectory = selectedFile.getParentFile();
            LoggingSupport.LOGGER.info(message("output.dir.set"), outputDirectory);
            var processor = new SolexVideoProcessor(selectedFile,
                    outputDirectory.toPath(),
                    sequenceNumber,
                    params,
                    cpu,
                    ioExecutor,
                    batchMode
            );
            var listener = createListener(baseName, params, batchMode, sequenceNumber, context);
            processor.addEventListener(listener);
            try {
                processor.process();
            } catch (Exception ex) {
                LoggingSupport.logError(ex);
            } finally {
                onComplete.run();
                processor.removeEventListener(listener);
            }
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
