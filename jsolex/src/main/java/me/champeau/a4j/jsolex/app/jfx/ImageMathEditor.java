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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.ime.HighlightingMode;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * FXML controller for the ImageMath script editor window.
 */
public class ImageMathEditor {
    /** Creates a new instance. Required by FXML. */
    public ImageMathEditor() {
    }

    private static final ButtonType PROCEED = new ButtonType(I18N.string(JSolEx.class, "imagemath-editor", "proceed.anyway"));
    private static final ButtonType BACK = new ButtonType(I18N.string(JSolEx.class, "imagemath-editor", "back"));
    /** File extension for ImageMath scripts. */
    public static final String MATH_EXTENSION = ".math";
    /** File extension for Python scripts. */
    public static final String PY_EXTENSION = ".py";
    /** File chooser filter for ImageMath script files. */
    public static final FileChooser.ExtensionFilter MATH_SCRIPT_EXTENSION_FILTER = new FileChooser.ExtensionFilter("ImageMath Script (*.math)", "*" + MATH_EXTENSION);
    /** File chooser filter for Python script files. */
    public static final FileChooser.ExtensionFilter PY_SCRIPT_EXTENSION_FILTER = new FileChooser.ExtensionFilter("Python Script (*.py)", "*" + PY_EXTENSION);
    /** File chooser filter for all script files. */
    public static final FileChooser.ExtensionFilter ALL_SCRIPTS_EXTENSION_FILTER = new FileChooser.ExtensionFilter("All Scripts (*.math, *.py)", "*" + MATH_EXTENSION, "*" + PY_EXTENSION);

    private final Configuration configuration = Configuration.getInstance();

    /** Container for the file list panel. */
    @FXML
    public VBox fileList;

    @FXML
    private ListView<ImageMathEntry> scriptsToApply;

    private ImageMathParams params;
    @FXML
    private ImageMathTextArea scriptTextArea;

    @FXML
    private Button saveButton;

    @FXML
    private ChoiceBox<PredefinedScript> predefinedScripts;

    @FXML
    private ChoiceBox<ScriptLanguage> scriptLanguage;

    private final AtomicBoolean hasPendingUpdates = new AtomicBoolean();
    private final AtomicBoolean updatingText = new AtomicBoolean();
    private Stage stage;
    private HostServices hostServices;
    private boolean batchMode;

    /**
     * Creates and displays an ImageMath editor window.
     * @param stage the parent stage
     * @param imageMathParams the initial parameters
     * @param hostServices the host services for opening URLs
     * @param batchMode whether the editor is in batch mode
     * @param multiFile whether multiple files are supported
     * @param onCreate callback when the editor is created
     * @param onClose callback when the editor is closed
     */
    public static void create(Stage stage,
                              ImageMathParams imageMathParams,
                              HostServices hostServices,
                              boolean batchMode,
                              boolean multiFile,
                              Consumer<? super ImageMathEditor> onCreate,
                              Consumer<? super ImageMathEditor> onClose) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "imagemath-editor");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (ImageMathEditor) fxmlLoader.getController();
            controller.setup(stage, imageMathParams, hostServices, batchMode);
            if (!multiFile) {
                controller.fileList.setManaged(false);
                controller.fileList.setVisible(false);
            }
            onCreate.accept(controller);
            Scene scene = newScene(node);
            scene.getStylesheets().add(JSolEx.class.getResource("syntax.css").toExternalForm());
            var currentScene = stage.getScene();
            var onCloseRequest = stage.getOnCloseRequest();
            var title = stage.getTitle();
            stage.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                    e.consume();
                }
                onClose.accept(controller);
                stage.setOnCloseRequest(onCloseRequest);
                stage.setTitle(title);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a known variable to the script editor for autocompletion.
     * @param variable the variable name
     */
    public void addKnownVariable(String variable) {
        scriptTextArea.addKnownVariable(variable);
    }

    /**
     * Sets up the editor with the given parameters.
     * @param stage the parent stage
     * @param imageMathParams the initial parameters
     * @param hostServices the host services for opening URLs
     * @param batchMode whether the editor is in batch mode
     */
    public void setup(Stage stage, ImageMathParams imageMathParams, HostServices hostServices, boolean batchMode) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.batchMode = batchMode;
        this.params = null;
        initializeLanguageSelector();
        loadPredefinedScripts();
        var items = scriptsToApply.getItems();
        if (imageMathParams != null) {
            populateFromParams(imageMathParams);
        }
        var selectionModel = scriptsToApply.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                try {
                    if (items.isEmpty()) {
                        return;
                    }
                    if (doesNotHaveStaleChanges()) {
                        var index = newValue.intValue();
                        if (index >= 0) {
                            var item = items.get(index);
                            loadScriptFile(item.scriptFile());
                        }
                    } else {
                        if (oldValue != null) {
                            selectionModel.selectIndices(oldValue.intValue());
                        }
                    }
                    scriptTextArea.setIncludesDir(items.isEmpty() || newValue.intValue() < 0 ? null : items.get(newValue.intValue()).scriptFile().getParentFile().toPath());
                } finally {
                    updating.set(false);
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
            scriptsToApply.getSelectionModel().selectFirst();
        }
        scriptTextArea.minHeightProperty().bind(stage.heightProperty().subtract(300));
        scriptTextArea.setMetadataEditorHandler(this::openMetadataEditor);
    }

    private void initializeLanguageSelector() {
        scriptLanguage.getItems().addAll(ScriptLanguage.values());
        scriptLanguage.setValue(ScriptLanguage.IMAGEMATH);
        scriptLanguage.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                scriptTextArea.setHighlightingMode(newVal.getHighlightingMode());
            }
        });
    }

    private void openMetadataEditor() {
        var scriptText = scriptTextArea.getText();
        var isPython = scriptLanguage.getValue() == ScriptLanguage.PYTHON;
        var editorStage = new Stage();
        editorStage.initOwner(stage);
        MetadataEditor.openEditor(editorStage, scriptText, isPython, (oldScript, newScript) -> {
            if (!oldScript.equals(newScript)) {
                updatingText.set(true);
                scriptTextArea.setText(newScript);
                saveButton.setDisable(false);
                hasPendingUpdates.set(true);
            }
        });
    }

    private void loadPredefinedScripts() {
        predefinedScripts.getItems().add(new PredefinedScript("", "", ScriptLanguage.IMAGEMATH));
        loadPredefinedScript("helium-processing", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("continuum-animation", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("doppler", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("virtual-eclipse", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("globe", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("enhanced-invert", ScriptLanguage.IMAGEMATH);
        loadPredefinedScript("python-demo", ScriptLanguage.PYTHON);
        if (batchMode) {
            loadPredefinedScript("stacking", ScriptLanguage.IMAGEMATH);
            loadPredefinedScript("aggressive-stacking-ha", ScriptLanguage.IMAGEMATH);
            loadPredefinedScript("aggressive-stacking-ca", ScriptLanguage.IMAGEMATH);
            loadPredefinedScript("corona", ScriptLanguage.IMAGEMATH);
        }
        predefinedScripts.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> {
            if (doesNotHaveStaleChanges()) {
                saveButton.setDisable(false);
                scriptsToApply.getSelectionModel().clearSelection();
                scriptTextArea.setText(newValue.script());
                scriptLanguage.setValue(newValue.language());
                scriptLanguage.setDisable(false);
                scriptTextArea.setHighlightingMode(newValue.language().getHighlightingMode());
            }
        });
    }

    private void loadPredefinedScript(String id, ScriptLanguage language) {
        predefinedScripts.getItems().add(loadScriptFromClasspath(id, language));
    }

    private PredefinedScript loadScriptFromClasspath(String name, ScriptLanguage language) {
        var label = JSolEx.message("script." + name.replace('-', '.'));
        var extension = language.getExtension();
        try {
            return new PredefinedScript(label, new String(ImageMathEditor.class.getResourceAsStream("/me/champeau/a4j/jsolex/templates/" + name + extension).readAllBytes(), "utf-8"), language);
        } catch (IOException e) {
            return new PredefinedScript(label, "", language);
        }
    }

    private void loadScriptFile(File file) {
        try {
            updatingText.set(true);
            // Set language selector based on file extension and disable it (file determines language)
            var language = ScriptLanguage.fromExtension(file.getName());
            scriptLanguage.setValue(language);
            scriptLanguage.setDisable(true);
            scriptTextArea.setHighlightingMode(language.getHighlightingMode());
            scriptTextArea.setText(FilesUtils.readString(file.toPath()));
            saveButton.setDisable(true);
            hasPendingUpdates.set(false);
            configuration.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private void populateFromParams(ImageMathParams imageMathParams) {
        var items = scriptsToApply.getItems();
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
                    scriptsToApply.getItems().stream().map(ImageMathEntry::scriptFile).toList(),
                    Map.of()
            );
            requestClose();
        }
    }


    /** Removes the currently selected script from the list. */
    @FXML
    public void removeSelectedItem() {
        if (doesNotHaveStaleChanges()) {
            var selectionModel = scriptsToApply.getSelectionModel();
            var idx = selectionModel.getSelectedIndex();
            if (idx >= 0) {
                var items = scriptsToApply.getItems();
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

    /** Opens a file chooser to load a script file. */
    @FXML
    public void loadScript() {
        if (doesNotHaveStaleChanges()) {
            var fileChooser = new FileChooser();
            configuration.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
            fileChooser.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "load.script"));
            fileChooser.getExtensionFilters().addAll(
                ALL_SCRIPTS_EXTENSION_FILTER,
                MATH_SCRIPT_EXTENSION_FILTER,
                PY_SCRIPT_EXTENSION_FILTER
            );
            var file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                hasPendingUpdates.set(false);
                var items = scriptsToApply.getItems();
                for (ImageMathEntry item : items) {
                    if (item.scriptFile().equals(file)) {
                        scriptsToApply.getSelectionModel().select(item);
                        return;
                    }
                }
                var item = new ImageMathEntry(file);
                items.add(item);
                scriptsToApply.getSelectionModel().select(item);
                loadScriptFile(file);
            }
        }
    }

    /**
     * Returns the current configuration if the user confirmed the dialog.
     * @return the configuration, or empty if cancelled
     */
    public Optional<ImageMathParams> getConfiguration() {
        return Optional.ofNullable(params);
    }

    @FXML
    private void newScript() {
        if (doesNotHaveStaleChanges()) {
            clearTextArea();
            scriptLanguage.setValue(ScriptLanguage.IMAGEMATH);
            scriptLanguage.setDisable(false);  // Enable selector for new scripts
            scriptTextArea.setHighlightingMode(HighlightingMode.IMAGEMATH);
            scriptsToApply.getSelectionModel().select(null);
        }
    }

    /**
     * Checks if there are unsaved changes and prompts the user if so.
     * @return true if there are no stale changes or user chose to proceed
     */
    public boolean doesNotHaveStaleChanges() {
        if (hasPendingUpdates.get()) {
            var alert = AlertFactory.confirmation(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes.description"));
            alert.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes"));
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
        ImageMathEntry targetEntry;
        if (scriptsToApply.getSelectionModel().getSelectedItem() == null) {
            var saveDialog = new FileChooser();
            var selectedLanguage = scriptLanguage.getValue();
            // Set default filter based on selected language
            if (selectedLanguage == ScriptLanguage.PYTHON) {
                saveDialog.getExtensionFilters().addAll(
                    PY_SCRIPT_EXTENSION_FILTER,
                    MATH_SCRIPT_EXTENSION_FILTER
                );
            } else {
                saveDialog.getExtensionFilters().addAll(
                    MATH_SCRIPT_EXTENSION_FILTER,
                    PY_SCRIPT_EXTENSION_FILTER
                );
            }
            targetFile = saveDialog.showSaveDialog(stage);
            if (targetFile == null) {
                return;
            }
            saveButton.setDisable(true);
            // Only add extension if the file doesn't already have a supported one
            if (!targetFile.getName().endsWith(MATH_EXTENSION) && !targetFile.getName().endsWith(PY_EXTENSION)) {
                // Use extension based on selected language
                targetFile = new File(targetFile.getParentFile(), targetFile.getName() + selectedLanguage.getExtension());
            }
            var candidateFile = targetFile;
            var candidateEntry = scriptsToApply.getItems().stream().filter(e -> e.scriptFile().equals(candidateFile)).findAny();
            if (candidateEntry.isPresent()) {
                targetEntry = candidateEntry.get();
            } else {
                var entry = new ImageMathEntry(candidateFile);
                scriptsToApply.getItems().add(entry);
                targetEntry = entry;
            }
            configuration.rememberDirectoryFor(targetFile.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
        } else {
            targetEntry = scriptsToApply.getSelectionModel().getSelectedItem();
            targetFile = targetEntry.scriptFile();
        }
        Files.writeString(targetFile.toPath(), scriptTextArea.getText(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        hasPendingUpdates.set(false);
        scriptLanguage.setDisable(true);  // Disable selector after save (file determines language)
        scriptsToApply.getSelectionModel().select(targetEntry);
    }

    /** Opens the ImageMath documentation in the default browser. */
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

    /**
     * Configuration for ImageMath scripts.
     * @param scriptFiles the list of script files
     */
    public record ImageMathConfiguration(
            List<File> scriptFiles
    ) {

    }

    private record PredefinedScript(String label, String script, ScriptLanguage language) {
        @Override
        public String toString() {
            return JSolEx.message(label);
        }
    }

    /**
     * Represents the available script languages.
     */
    public enum ScriptLanguage {
        IMAGEMATH("ImageMath", MATH_EXTENSION),
        PYTHON("Python", PY_EXTENSION);

        private final String displayName;
        private final String extension;

        ScriptLanguage(String displayName, String extension) {
            this.displayName = displayName;
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        /**
         * Returns the highlighting mode for this script language.
         * @return the highlighting mode
         */
        public HighlightingMode getHighlightingMode() {
            return switch (this) {
                case IMAGEMATH -> HighlightingMode.IMAGEMATH;
                case PYTHON -> HighlightingMode.PYTHON;
            };
        }

        @Override
        public String toString() {
            return displayName;
        }

        public static ScriptLanguage fromExtension(String filename) {
            if (filename != null && filename.endsWith(PY_EXTENSION)) {
                return PYTHON;
            }
            return IMAGEMATH;
        }
    }
}
