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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.FlatCreator;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class FlatSelectionController {
    private static final FileChooser.ExtensionFilter FLAT_EXTENSIONS = new FileChooser.ExtensionFilter("Flats", "*.fits", "*.ser");

    @FXML
    private TextArea helpMessage;

    @FXML
    private Button selectFlatButton;

    @FXML
    private Button cancelButton;

    private Configuration configuration;
    private Stage stage;
    private Broadcaster broadcaster;
    private ProgressOperation operation;
    private ProcessParams params;
    private Consumer<? super Optional<Path>> onClose;

    public static void open(Stage stage,
                            Broadcaster broadcaster,
                            ProgressOperation operation,
                            Configuration configuration,
                            ProcessParams params,
                            Consumer<? super Optional<Path>> onClose) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "flat-selector");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (FlatSelectionController) fxmlLoader.getController();
            controller.setup(configuration, stage, broadcaster, operation, params, onClose);
            var scene = new Scene(node);
            stage.setTitle(I18N.string(JSolEx.class, "flat-selector", "frame.title"));
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setup(Configuration configuration,
                       Stage stage,
                       Broadcaster broadcaster,
                       ProgressOperation operation,
                       ProcessParams params,
                       Consumer<? super Optional<Path>> onClose) {
        this.configuration = configuration;
        this.stage = stage;
        this.broadcaster = broadcaster;
        this.operation = operation;
        this.params = params;
        this.onClose = onClose;
    }

    @FXML
    public void selectFlat() {
        var fileChooser = new FileChooser();
        configuration.findLastOpenDirectory().ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        fileChooser.getExtensionFilters().add(FLAT_EXTENSIONS);
        Optional.ofNullable(fileChooser.showOpenDialog(stage)).ifPresent(selectedFlat -> {
            configuration.updateLastOpenDirectory(selectedFlat.getParentFile().toPath());
            var extension = selectedFlat.getName().substring(selectedFlat.getName().lastIndexOf('.') + 1).toLowerCase(Locale.US);
            if ("fits".equals(extension)) {
                stage.close();
                onClose.accept(Optional.of(selectedFlat.toPath()));
            } else if ("ser".equals(extension)) {
                createMasterFlat(selectedFlat.toPath());
            } else {
                onClose.accept(Optional.empty());
            }
        });
    }

    @FXML
    public void cancel() {
        stage.close();
        onClose.accept(Optional.empty());
    }

    private void createMasterFlat(Path path) {
        Platform.runLater(() -> {
            helpMessage.setDisable(true);
            selectFlatButton.setDisable(true);
            cancelButton.setDisable(true);
        });
        BackgroundOperations.async(() -> {
            var converter = ImageUtils.createImageConverter(params.videoParams().colorMode(), params.geometryParams().isSpectrumVFlip());
            var creator = new FlatCreator(converter, operation, broadcaster);
            var flat = creator.createMasterFlat(path);
            var sourceFileName = path.toFile().getName();
            var masterFlatFile = path.resolveSibling(sourceFileName.substring(0, sourceFileName.lastIndexOf('.')) + "_MasterFlat.fits");
            FitsUtils.writeFitsFile(flat, masterFlatFile.toFile(), params);
            Platform.runLater(() -> stage.close());
            onClose.accept(Optional.of(masterFlatFile));
        });
    }
}
