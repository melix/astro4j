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
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.SampleScripts.SampleScript;
import me.champeau.a4j.jsolex.app.jfx.ime.HighlightingMode;
import me.champeau.a4j.jsolex.app.jfx.ime.ImageMathTextArea;
import me.champeau.a4j.jsolex.app.util.FxUtils;
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
    public static final FileChooser.ExtensionFilter MATH_SCRIPT_EXTENSION_FILTER = new FileChooser.ExtensionFilter(
            I18N.string(JSolEx.class, "common", "file.filter.imagemath") + " (*.math)", "*" + MATH_EXTENSION);
    /** File chooser filter for Python script files. */
    public static final FileChooser.ExtensionFilter PY_SCRIPT_EXTENSION_FILTER = new FileChooser.ExtensionFilter(
            I18N.string(JSolEx.class, "common", "file.filter.python") + " (*.py)", "*" + PY_EXTENSION);
    /** File chooser filter for all script files. */
    public static final FileChooser.ExtensionFilter ALL_SCRIPTS_EXTENSION_FILTER = new FileChooser.ExtensionFilter(
            I18N.string(JSolEx.class, "common", "file.filter.scripts") + " (*.math, *.py)",
            "*" + MATH_EXTENSION, "*" + PY_EXTENSION);

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
    private MenuButton addSampleButton;

    @FXML
    private Label sampleNotice;

    @FXML
    private ChoiceBox<ScriptLanguage> scriptLanguage;

    private Stage stage;
    private HostServices hostServices;
    private boolean batchMode;
    private boolean multiFile;
    private ImageMathEntry currentEntry;
    private String savedText = "";
    private boolean selectingProgrammatically;

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
            controller.setup(stage, imageMathParams, hostServices, batchMode, multiFile);
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
                if (!controller.doesNotHaveStaleChanges()) {
                    e.consume();
                    return;
                }
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
     * @param multiFile whether multiple files are supported
     */
    public void setup(Stage stage, ImageMathParams imageMathParams, HostServices hostServices, boolean batchMode, boolean multiFile) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.batchMode = batchMode;
        this.multiFile = multiFile;
        this.params = null;
        initializeLanguageSelector();
        initializeSamplesMenu();
        scriptsToApply.setCellFactory(list -> new ScriptCell());
        if (imageMathParams != null) {
            populateFromParams(imageMathParams);
        }
        scriptsToApply.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if (selectingProgrammatically || newItem == null || newItem.equals(currentEntry)) {
                return;
            }
            if (doesNotHaveStaleChanges()) {
                showEntry(newItem);
            } else {
                FxUtils.runLater(() -> selectInList(currentEntry));
            }
        });
        scriptTextArea.textProperty().addListener((obj, oldValue, newValue) -> updateEditorState());
        var items = scriptsToApply.getItems();
        if (items.isEmpty()) {
            showNewScript();
        } else {
            select(items.getFirst());
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

    private void initializeSamplesMenu() {
        for (var sample : SampleScripts.available(batchMode)) {
            var item = new MenuItem(sample.label());
            item.setOnAction(e -> addSample(sample));
            addSampleButton.getItems().add(item);
        }
    }

    private void openMetadataEditor() {
        var scriptText = scriptTextArea.getText();
        var isPython = scriptLanguage.getValue() == ScriptLanguage.PYTHON;
        var editorStage = new Stage();
        editorStage.initOwner(stage);
        MetadataEditor.openEditor(editorStage, scriptText, isPython, (oldScript, newScript) -> {
            if (!oldScript.equals(newScript)) {
                scriptTextArea.setText(newScript);
            }
        });
    }

    private void addSample(SampleScript sample) {
        if (!doesNotHaveStaleChanges()) {
            return;
        }
        File file;
        try {
            file = sample.install();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        var entry = new ImageMathEntry(file);
        updateListSilently(() -> addEntry(entry));
        select(entry);
    }

    private void addEntry(ImageMathEntry entry) {
        var items = scriptsToApply.getItems();
        if (!multiFile) {
            items.setAll(entry);
        } else if (!items.contains(entry)) {
            items.add(entry);
        }
    }

    private void select(ImageMathEntry entry) {
        selectInList(entry);
        showEntry(entry);
    }

    private void selectInList(ImageMathEntry entry) {
        updateListSilently(() -> {
            if (entry == null) {
                scriptsToApply.getSelectionModel().clearSelection();
            } else {
                scriptsToApply.getSelectionModel().select(entry);
            }
        });
    }

    private void updateListSilently(Runnable action) {
        selectingProgrammatically = true;
        try {
            action.run();
        } finally {
            selectingProgrammatically = false;
        }
    }

    private void showEntry(ImageMathEntry entry) {
        var file = entry.scriptFile();
        String text;
        try {
            text = FilesUtils.readString(file.toPath());
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        currentEntry = entry;
        showLanguage(ScriptLanguage.fromExtension(file.getName()), true);
        scriptTextArea.setIncludesDir(file.getParentFile().toPath());
        if (entry.sample().isEmpty()) {
            configuration.rememberDirectoryFor(file.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
        }
        showText(text);
    }

    private void showNewScript() {
        currentEntry = null;
        showLanguage(ScriptLanguage.IMAGEMATH, false);
        scriptTextArea.setIncludesDir(null);
        showText("");
    }

    private void showLanguage(ScriptLanguage language, boolean locked) {
        scriptLanguage.setValue(language);
        scriptLanguage.setDisable(locked);
        scriptTextArea.setHighlightingMode(language.getHighlightingMode());
    }

    private void showText(String text) {
        scriptTextArea.setText(text);
        FxUtils.runLater(() -> {
            savedText = scriptTextArea.getText();
            updateEditorState();
        });
        updateEditorState();
    }

    private boolean hasUnsavedChanges() {
        return !scriptTextArea.getText().equals(savedText);
    }

    private boolean isEditingSample() {
        return currentEntry != null && currentEntry.sample().isPresent();
    }

    private void updateEditorState() {
        var sample = isEditingSample();
        sampleNotice.setVisible(sample);
        sampleNotice.setManaged(sample);
        saveButton.setText(I18N.string(JSolEx.class, "imagemath-editor", currentEntry == null || sample ? "save.as" : "save"));
        saveButton.setDisable(!hasUnsavedChanges());
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
        var entry = scriptsToApply.getSelectionModel().getSelectedItem();
        if (entry == null || !doesNotHaveStaleChanges()) {
            return;
        }
        var items = scriptsToApply.getItems();
        updateListSilently(() -> items.remove(entry));
        if (items.isEmpty()) {
            selectInList(null);
            showNewScript();
        } else {
            select(items.getFirst());
        }
    }

    /** Opens a file chooser to load a script file. */
    @FXML
    public void loadScript() {
        var fileChooser = new FileChooser();
        configuration.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
        fileChooser.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "load.script"));
        fileChooser.getExtensionFilters().addAll(
            ALL_SCRIPTS_EXTENSION_FILTER,
            MATH_SCRIPT_EXTENSION_FILTER,
            PY_SCRIPT_EXTENSION_FILTER
        );
        var file = fileChooser.showOpenDialog(stage);
        if (file == null || !doesNotHaveStaleChanges()) {
            return;
        }
        var entry = new ImageMathEntry(file);
        updateListSilently(() -> addEntry(entry));
        select(entry);
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
            selectInList(null);
            showNewScript();
        }
    }

    /**
     * Checks if there are unsaved changes and prompts the user if so.
     * If the user chooses to proceed, the changes are discarded.
     * @return true if there are no stale changes or user chose to proceed
     */
    public boolean doesNotHaveStaleChanges() {
        if (!hasUnsavedChanges()) {
            return true;
        }
        var alert = AlertFactory.confirmation(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes.description"));
        alert.setTitle(I18N.string(JSolEx.class, "imagemath-editor", "unsaved.changes"));
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(
                BACK,
                PROCEED
        );
        var proceed = alert.showAndWait().map(type -> type.equals(PROCEED)).orElse(false);
        if (proceed) {
            savedText = scriptTextArea.getText();
            updateEditorState();
        }
        return proceed;
    }

    @FXML
    private void saveScript() throws IOException {
        if (currentEntry != null && currentEntry.sample().isEmpty()) {
            writeScript(currentEntry.scriptFile());
            return;
        }
        var targetFile = chooseSaveTarget();
        if (targetFile == null) {
            return;
        }
        writeScript(targetFile);
        var previous = currentEntry;
        var entry = new ImageMathEntry(targetFile);
        updateListSilently(() -> replaceEntry(previous, entry));
        configuration.rememberDirectoryFor(targetFile.toPath(), Configuration.DirectoryKind.IMAGE_MATH);
        select(entry);
    }

    private void writeScript(File file) throws IOException {
        var text = scriptTextArea.getText();
        Files.writeString(file.toPath(), text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        savedText = text;
        updateEditorState();
    }

    private void replaceEntry(ImageMathEntry previous, ImageMathEntry entry) {
        var items = scriptsToApply.getItems();
        var index = previous == null ? -1 : items.indexOf(previous);
        if (index >= 0 && !items.contains(entry)) {
            items.set(index, entry);
        } else {
            items.remove(previous);
            addEntry(entry);
        }
    }

    private File chooseSaveTarget() {
        var saveDialog = new FileChooser();
        configuration.findLastOpenDirectory(Configuration.DirectoryKind.IMAGE_MATH).ifPresent(dir -> saveDialog.setInitialDirectory(dir.toFile()));
        if (currentEntry != null) {
            saveDialog.setInitialFileName(currentEntry.scriptFile().getName());
        }
        var selectedLanguage = scriptLanguage.getValue();
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
        var targetFile = saveDialog.showSaveDialog(stage);
        if (targetFile == null) {
            return null;
        }
        if (!targetFile.getName().endsWith(MATH_EXTENSION) && !targetFile.getName().endsWith(PY_EXTENSION)) {
            targetFile = new File(targetFile.getParentFile(), targetFile.getName() + selectedLanguage.getExtension());
        }
        if (SampleScripts.isInSamplesDir(targetFile.toPath())) {
            AlertFactory.error(I18N.string(JSolEx.class, "imagemath-editor", "samples.dir.forbidden")).showAndWait();
            return null;
        }
        return targetFile;
    }

    /** Opens the ImageMath documentation in the default browser. */
    @FXML
    public void openDocs() {
        DocsHelper.openHelp(hostServices, "imagemath");
    }

    private record ImageMathEntry(File scriptFile) {
        Optional<SampleScript> sample() {
            return SampleScripts.find(scriptFile);
        }

        @Override
        public String toString() {
            return sample().map(SampleScript::label).orElseGet(scriptFile::getName);
        }
    }

    private static class ScriptCell extends ListCell<ImageMathEntry> {
        @Override
        protected void updateItem(ImageMathEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.toString());
            if (item.sample().isPresent()) {
                var badge = new Label(I18N.string(JSolEx.class, "imagemath-editor", "sample.badge"));
                badge.getStyleClass().add("sample-badge");
                setGraphic(badge);
                setContentDisplay(ContentDisplay.RIGHT);
            } else {
                setGraphic(null);
            }
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
