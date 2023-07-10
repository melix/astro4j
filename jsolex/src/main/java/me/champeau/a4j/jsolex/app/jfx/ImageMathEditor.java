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
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImageMathEditor {
    private static final ButtonType PROCEED = new ButtonType(I18N.string(JSolEx.class, "imagemath-editor", "proceed.anyway"));
    private static final ButtonType BACK = new ButtonType(I18N.string(JSolEx.class, "imagemath-editor", "back"));
    public static final String MATH_EXTENSION = ".math";
    public static final FileChooser.ExtensionFilter MATH_SCRIPT_EXTENSION_FILTER = new FileChooser.ExtensionFilter("ImageMath Script (*.math)", "*" + MATH_EXTENSION);

    @FXML
    public ListView<ImageMathEntry> elements;

    private ImageMathParams params;
    @FXML
    private ImageMathTextArea scriptTextArea;

    @FXML
    private Button saveButton;

    private final AtomicBoolean hasPendingUpdates = new AtomicBoolean();
    private final AtomicBoolean updatingText = new AtomicBoolean();
    private Stage stage;
    private HostServices hostServices;

    public void setup(Stage stage, ImageMathParams imageMathParams, HostServices hostServices) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.params = null;
        var items = elements.getItems();
        if (imageMathParams != null) {
            populateFromParams(imageMathParams);
        }
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (doesNotHaveStaleChanges()) {
                if (updating.compareAndSet(false, true)) {
                    var index = newValue.intValue();
                    if (index >= 0) {
                        var item = items.get(index);
                        loadScriptFile(item.scriptFile());
                    }
                }
                updating.set(false);
            } else {
                if (oldValue != null) {
                    selectionModel.selectIndices(oldValue.intValue());
                }
            }
        });
        scriptTextArea.textProperty().addListener((obj, oldValue, newValue) -> {
            if (updatingText.get()) {
                updatingText.set(false);
                return;
            }
            saveButton.setDisable(false);
            hasPendingUpdates.set(true);
        });
        saveButton.setDisable(true);
        if (!items.isEmpty()) {
            elements.getSelectionModel().selectFirst();
        }
    }

    private void loadScriptFile(File file) {
        try {
            updatingText.set(true);
            scriptTextArea.setText(FilesUtils.readString(file.toPath()));
            saveButton.setDisable(true);
            hasPendingUpdates.set(false);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private void populateFromParams(ImageMathParams imageMathParams) {
        var items = elements.getItems();
        items.clear();
        var scriptFiles = imageMathParams.scriptFiles();
        for (File file : scriptFiles) {
            items.add(new ImageMathEntry(file));
        }
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    private void cancel() {
        requestClose();
    }

    @FXML
    private void ok() {
        if (doesNotHaveStaleChanges()) {
            params = new ImageMathParams(
                    elements.getItems().stream().map(ImageMathEntry::scriptFile).toList()
            );
            requestClose();
        }
    }


    @FXML
    public void removeSelectedItem() {
        if (doesNotHaveStaleChanges()) {
            var selectionModel = elements.getSelectionModel();
            var idx = selectionModel.getSelectedIndex();
            if (idx >= 0) {
                var items = elements.getItems();
                items.remove(idx);
                selectionModel.clearSelection();
                if (items.isEmpty()) {
                    clearTextArea();
                } else {
                    selectionModel.selectFirst();
                }
            }
        }
    }

    private void clearTextArea() {
        scriptTextArea.setText("");
        saveButton.setDisable(true);
        hasPendingUpdates.set(false);
        updatingText.set(true);
    }

    @FXML
    public void loadScript() {
        if (doesNotHaveStaleChanges()) {
            var fileChooser = new FileChooser();
            fileChooser.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "load.script"));
            fileChooser.getExtensionFilters().add(MATH_SCRIPT_EXTENSION_FILTER);
            var file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                hasPendingUpdates.set(false);
                var items = elements.getItems();
                for (ImageMathEntry item : items) {
                    if (item.scriptFile().equals(file)) {
                        elements.getSelectionModel().select(item);
                        return;
                    }
                }
                var item = new ImageMathEntry(file);
                items.add(item);
                elements.getSelectionModel().select(item);
                loadScriptFile(file);
            }
        }
    }

    public Optional<ImageMathParams> getConfiguration() {
        return Optional.ofNullable(params);
    }

    @FXML
    private void newScript() {
        if (doesNotHaveStaleChanges()) {
            clearTextArea();
            elements.getSelectionModel().select(null);
        }
    }

    public boolean doesNotHaveStaleChanges() {
        if (hasPendingUpdates.get()) {
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes"));
            alert.setHeaderText(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes.description"));
            alert.getButtonTypes().clear();
            alert.getButtonTypes().addAll(
                    BACK,
                    PROCEED
            );
            var buttonType = alert.showAndWait();
            return buttonType.map(type -> type.equals(PROCEED)).orElse(false);
        }
        return true;
    }

    @FXML
    private void saveScript() throws IOException {
        File targetFile;
        saveButton.setDisable(true);
        ImageMathEntry targetEntry = null;
        if (elements.getSelectionModel().getSelectedItem() == null) {
            var saveDialog = new FileChooser();
            saveDialog.getExtensionFilters().add(MATH_SCRIPT_EXTENSION_FILTER);
            targetFile = saveDialog.showSaveDialog(stage);
            if (!targetFile.getName().endsWith(MATH_EXTENSION)) {
                targetFile = new File(targetFile.getParentFile(), targetFile.getName() + MATH_EXTENSION);
            }
            var candidateFile = targetFile;
            var candidateEntry = elements.getItems().stream().filter(e -> e.scriptFile().equals(candidateFile)).findAny();
            if (candidateEntry.isPresent()) {
                targetEntry = candidateEntry.get();
            } else {
                var entry = new ImageMathEntry(candidateFile);
                elements.getItems().add(entry);
                targetEntry = entry;
            }
        } else {
            targetEntry = elements.getSelectionModel().getSelectedItem();
            targetFile = targetEntry.scriptFile();
        }
        Files.writeString(targetFile.toPath(), scriptTextArea.getText(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        elements.getSelectionModel().select(targetEntry);
        hasPendingUpdates.set(false);
    }

    @FXML
    public void openDocs() {
        DocsHelper.openHelp(hostServices, "imagemath");
    }

    private record ImageMathEntry(File scriptFile) {
        @Override
        public String toString() {
            return scriptFile.getName();
        }
    }

    public record ImageMathConfiguration(
            List<File> scriptFiles
    ) {

    }

}
