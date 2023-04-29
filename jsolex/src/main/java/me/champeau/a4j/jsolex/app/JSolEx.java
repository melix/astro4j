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
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.jfx.ConfigurationController;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.JFXProcessingEventListener;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineDebugger;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class JSolEx extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolEx.class);

    private final Configuration config = new Configuration();

    @FXML
    private TextArea console;

    @FXML
    private Menu recentFilesMenu;

    @FXML
    private TabPane mainPane;

    private boolean reconstructionStarted = false;

    @Override
    public void start(Stage stage) throws Exception {
        var fxmlLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        fxmlLoader.setController(this);

        try {
            var root = (Parent) fxmlLoader.load();
            var preferredDimensions = config.getPreferredDimensions();
            var scene = new Scene(root, preferredDimensions.a(), preferredDimensions.b());
            var pause = new PauseTransition(Duration.seconds(1));
            scene.widthProperty().addListener((observable, oldValue, newValue) -> {
                pause.setOnFinished(e -> config.setPreferredWidth(newValue.intValue()));
                pause.playFromStart();
            });
            scene.heightProperty().addListener((observable, oldValue, newValue) -> {
                pause.setOnFinished(e -> config.setPreferredHeigth(newValue.intValue()));
                pause.playFromStart();
            });
            stage.setTitle("JSol'Ex");
            stage.setScene(scene);
            addIcons(stage);
            stage.show();
            refreshRecentItemsMenu();
            LogbackConfigurer.configureLogger(console);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
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

    @FXML
    private void config() throws IOException {
        var fxmlLoader = new FXMLLoader(getClass().getResource("configuration.fxml"));
        var configWindow = fxmlLoader.load();
        var controller = (ConfigurationController) fxmlLoader.getController();
        var stage = new Stage();
        controller.configure(config, stage);
        stage.setTitle("Configuration");
        stage.setScene(new Scene((Parent) configWindow, 500, 200));
        stage.showAndWait();
    }

    @FXML
    private void open() {
        selectSerFileAndThen(this::doOpen);
    }

    private void selectSerFileAndThen(Consumer<? super File> consumer) {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SER files", "*.ser"));
        var selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            consumer.accept(selectedFile);
        }
    }

    @FXML
    private void showFrameDebugger() {
        selectSerFileAndThen(file -> {
            var fxmlLoader = new FXMLLoader(getClass().getResource("frame-debugger.fxml"));
            Object configWindow;
            try {
                configWindow = fxmlLoader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            var controller = (SpectralLineDebugger) fxmlLoader.getController();
            var stage = new Stage();
            Scene scene = new Scene((Parent) configWindow, 1024, 400);
            controller.open(file, config, scene);
            stage.setTitle("Frame debugger");
            stage.setScene(scene);
            stage.showAndWait();
        });

    }

    @FXML
    private void about() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(480, 320);
        alert.setTitle("About JSol'Ex");
        alert.setHeaderText("Solar images processor");
        alert.setContentText("JSol'Ex is free software aimed at processing solar images produced with Christian Buil's Sol'Ex instrument. " +
                             "It is provided as is without any warrante and licensed under Apache License version 2. It was written as an experiment to understand " +
                             "how Sol'Ex works and is heavily inspired by Valérie Desnoux's INTI (http://valerie.desnoux.free.fr/inti/). " +
                             "This is still under heavy development, make sure to upgrade regularly.");
        alert.showAndWait();
    }

    private void doOpen(File selectedFile) {
        config.loaded(selectedFile.toPath());
        Platform.runLater(this::refreshRecentItemsMenu);
        startProcess(selectedFile);

    }

    private void startProcess(File selectedFile) {
        mainPane.getTabs().clear();
        console.textProperty().set("");
        reconstructionStarted = false;
        var outputDirName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf("."));
        var outputDirectory = new File(selectedFile.getParentFile(), outputDirName);
        try {
            Files.createDirectories(outputDirectory.toPath());
            LOGGER.info("Output directory set to {}", outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var processor = new SolexVideoProcessor(selectedFile,
                outputDirectory,
                config.isDebugImagesGenerationEnabled(),
                config.getSpectrumDetectionThreshold()
        );
        var listener = JFXProcessingEventListener.of(new ProcessingEventListener() {
            private final ImageView imageView = new ZoomableImageView();

            @Override
            public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
                LOGGER.info("Dimensions of {} image determined : {}x{}", event.getLabel(), event.getWidth(), event.getHeight());
                if (reconstructionStarted) {
                    return;
                }
                reconstructionStarted = true;
                imageView.setPreserveRatio(true);
                int width = event.getWidth();
                int height = event.getHeight();
                imageView.fitWidthProperty().bind(mainPane.widthProperty());
                imageView.setImage(new WritableImage(width, height));
                var colorAdjust = new ColorAdjust();
                colorAdjust.brightnessProperty().setValue(0.2);
                imageView.setEffect(colorAdjust);
                var scrollPane = new ScrollPane();
                scrollPane.setContent(imageView);
                mainPane.getTabs().add(new Tab("Reconstruction", scrollPane));
            }

            @Override
            public void onPartialReconstruction(PartialReconstructionEvent event) {
                WritableImage image = (WritableImage) imageView.getImage();
                var payload = event.getPayload();
                int y = payload.line();
                double[] line = payload.data();
                byte[] rgb = new byte[3 * line.length];
                for (int x = 0; x < line.length; x++) {
                    int v = (int) Math.round(line[x]);
                    byte c = (byte) (v >> 8);
                    rgb[3 * x] = c;
                    rgb[3 * x + 1] = c;
                    rgb[3 * x + 2] = c;
                }
                var pixelformat = PixelFormat.getByteRgbInstance();
                image.getPixelWriter().setPixels(0, y, line.length, 1, pixelformat, rgb, 0, 3 * line.length);
            }

            @Override
            public void onImageGenerated(ImageGeneratedEvent event) {
                var tab = new Tab(event.getPayload().title());
                var viewer = newImageViewer();
                viewer.fitWidthProperty().bind(mainPane.widthProperty());
                viewer.setImage(event.getPayload().image(), event.getPayload().stretchingStrategy(), event.getPayload().path().toFile());
                var scrollPane = new ScrollPane();
                scrollPane.setContent(viewer.getRoot());
                tab.setContent(scrollPane);
                mainPane.getTabs().add(tab);
                mainPane.getSelectionModel().select(tab);
            }

            @Override
            public void onNotification(NotificationEvent e) {
                var alert = new Alert(e.type());
                alert.setResizable(true);
                alert.getDialogPane().setPrefSize(480, 320);
                alert.setTitle(e.title());
                alert.setHeaderText(e.header());
                alert.setContentText(e.message());
                alert.showAndWait();
            }
        });

        processor.addEventListener(listener);
        var task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    processor.process();
                } finally {
                    processor.removeEventListener(listener);
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private ImageViewer newImageViewer() {
        var fxmlLoader = new FXMLLoader(getClass().getResource("imageview.fxml"));
        try {
            var node = (Node) fxmlLoader.load();
            var controller = (ImageViewer) fxmlLoader.getController();
            controller.init(node);
            return controller;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void exit() {
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}
