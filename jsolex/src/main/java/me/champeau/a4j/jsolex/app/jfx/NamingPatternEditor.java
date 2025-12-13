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

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.file.Token;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.ser.Header;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * Editor for file naming patterns.
 */
public class NamingPatternEditor {
    /**
     * The list of naming patterns.
     */
    @FXML
    public ListView<NamedPattern> elements;
    @FXML
    private TextField label;
    @FXML
    private TextField pattern;
    @FXML
    private TextField example;
    @FXML
    private TextField datetimeFormat;
    @FXML
    private TextField dateFormat;

    @FXML
    private VBox tokens;

    private Stage stage;
    private NamedPattern selectedPattern;

    /**
     * Creates a new instance.
     */
    public NamingPatternEditor() {
    }

    /**
     * Opens the naming pattern editor in the given stage.
     * @param stage the stage to display the editor in
     * @param header the SER file header
     * @param onCloseRequest callback when the editor is closed
     */
    public static void openEditor(Stage stage, Header header, Consumer<? super NamingPatternEditor> onCloseRequest) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "naming-patterns");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (NamingPatternEditor) fxmlLoader.getController();
            controller.setup(stage, header);
            Scene scene = newScene(node);
            var currentScene = stage.getScene();
            stage.setTitle(I18N.string(JSolEx.class, "naming-patterns", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                    e.consume();
                }
                onCloseRequest.accept(controller);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the editor with the given stage and header.
     * @param stage the stage to use
     * @param header the SER file header
     */
    public void setup(Stage stage, Header header) {
        var patterns = FileNamingPatternsIO.loadDefaults();
        this.stage = stage;
        prepareTokens();
        var items = elements.getItems();
        items.addAll(patterns);
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (updating.compareAndSet(false, true)) {
                var index = newValue.intValue();
                var item = items.get(index);
                label.setText(item.label());
                pattern.setText(item.pattern());
                datetimeFormat.setText(item.datetimeFormat());
                dateFormat.setText(item.dateFormat());
                updateExampleText(header);
                updating.set(false);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newPattern = pattern.getText();
                var newDatetimeFormat = datetimeFormat.getText();
                var newDateFormat = dateFormat.getText();
                updateExampleText(header);
                var selectedIndex = selectionModel.getSelectedIndex();
                if (selectedIndex != -1) {
                    var e = new NamedPattern(newLabel, newPattern, newDatetimeFormat, newDateFormat);
                    items.set(selectedIndex, e);
                    elements.getSelectionModel().select(e);
                }
                updating.set(false);
            }
        };
        label.textProperty().addListener(updateValueListener);
        pattern.textProperty().addListener(updateValueListener);
        datetimeFormat.textProperty().addListener(updateValueListener);
        dateFormat.textProperty().addListener(updateValueListener);
        datetimeFormat.setTextFormatter(newDateTimeFormatter());
        dateFormat.setTextFormatter(newDateTimeFormatter());
        if (!patterns.isEmpty()) {
            selectionModel.select(0);
        }
    }

    private void updateExampleText(Header header) {
        var namingStrategy = new FileNamingStrategy(pattern.getText(), datetimeFormat.getText(), dateFormat.getText(), LocalDateTime.now(), header);
        var dummyImage = new ImageWrapper32(0,0,new float[0][], Map.of(PixelShift.class, new PixelShift(5)));
        example.setText(namingStrategy.render(24, null, Constants.TYPE_PROCESSED, "disk-1", "video_sun", dummyImage));
    }

    private void prepareTokens() {
        var tokenList = tokens.getChildren();
        tokenList.add(new Label(I18N.string(JSolEx.class, "naming-patterns", "available.tokens")));
        for (Token e : Token.values()) {
            var tokenLabel = new Label(e.token() + " - " + I18N.string(JSolEx.class, "naming-patterns", "token_" + e.name()));
            tokenLabel.setWrapText(true);
            tokenList.add(tokenLabel);
        }
    }

    /**
     * Closes the editor and saves the patterns.
     */
    @FXML
    public void close() {
        FileNamingPatternsIO.saveDefaults(elements.getItems());
        this.selectedPattern = elements.getSelectionModel().getSelectedItem();
        requestClose();
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    /**
     * Cancels the editor without saving.
     */
    @FXML
    public void cancel() {
        this.selectedPattern = null;
        requestClose();
    }

    /**
     * Resets the patterns to their default values.
     */
    @FXML
    public void reset() {
        elements.getItems().clear();
        elements.getItems().addAll(FileNamingPatternsIO.predefined());
        elements.getSelectionModel().select(0);
    }

    /**
     * Returns the selected pattern.
     * @return the selected pattern, or empty if none is selected
     */
    public Optional<NamedPattern> getSelectedPattern() {
        return Optional.ofNullable(selectedPattern);
    }

    /**
     * Removes the selected pattern from the list.
     */
    @FXML
    public void removeSelectedItem() {
        var idx = elements.getSelectionModel().getSelectedIndex();
        elements.getItems().remove(idx);
    }

    /**
     * Adds a new custom pattern to the list.
     */
    @FXML
    public void addNewItem() {
        var newElement = new NamedPattern("Custom " + elements.getItems().size(), FileNamingStrategy.DEFAULT_TEMPLATE, FileNamingStrategy.DEFAULT_DATETIME_FORMAT, FileNamingStrategy.DEFAULT_DATE_FORMAT);
        elements.getItems().add(newElement);
        elements.getSelectionModel().select(newElement);
    }

    private static TextFormatter<Object> newDateTimeFormatter() {
        return new TextFormatter<>(change -> {
            var text = change.getText();
            try {
                DateTimeFormatter.ofPattern(text).format(LocalDateTime.now().atZone(ZoneId.of("UTC")));
            } catch (Exception ex) {
                return null;
            }
            return change;
        });
    }
}
