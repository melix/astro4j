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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class JSolEx extends Application {
    private final Configuration config = new Configuration();

    @FXML
    private TextArea console;

    @FXML
    private Menu recentFilesMenu;

    @Override
    public void start(Stage stage) throws Exception {
        var fxmlLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        fxmlLoader.setController(this);

        try {
            var root = (Parent) fxmlLoader.load();
            var scene = new Scene(root, 1024, 768);
            stage.setTitle("JSol'Ex");
            stage.setScene(scene);
            stage.show();
            refreshRecentItemsMenu();
            LogbackConfigurer.configureLogger(console);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
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
    private void config() {

    }

    @FXML
    private void open() {
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SER files", "*.ser"));
        var selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            doOpen(selectedFile);
        }
    }

    private void doOpen(File selectedFile) {
        config.loaded(selectedFile.toPath());
        Platform.runLater(this::refreshRecentItemsMenu);
        startProcess(selectedFile);

    }

    private void startProcess(File selectedFile) {
        console.textProperty().set("");
        var processor = new SolexVideoProcessor(selectedFile, new File("/tmp/out"));
        var task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                processor.process();
                return null;
            }
        };
        new Thread(task).start();
    }


    @FXML
    private void exit() {
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}
