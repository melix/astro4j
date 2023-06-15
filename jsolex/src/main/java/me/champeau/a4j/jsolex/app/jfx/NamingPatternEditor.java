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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.file.Token;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.ser.Header;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class NamingPatternEditor {
    @FXML
    public ListView<NamedPattern> elements;
    @FXML
    private TextField label;
    @FXML
    private TextField pattern;
    @FXML
    private TextField example;

    @FXML
    private VBox tokens;

    private Stage stage;
    private NamedPattern selectedPattern;

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
                updateExampleText(header);
                updating.set(false);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newPattern = pattern.getText();
                updateExampleText(header);
                var selectedIndex = selectionModel.getSelectedIndex();
                if (selectedIndex != -1) {
                    var e = new NamedPattern(newLabel, newPattern);
                    items.set(selectedIndex, e);
                    elements.getSelectionModel().select(e);
                }
                updating.set(false);
            }
        };
        label.textProperty().addListener(updateValueListener);
        pattern.textProperty().addListener(updateValueListener);
        if (!patterns.isEmpty()) {
            selectionModel.select(0);
        }
    }

    private void updateExampleText(Header header) {
        var namingStrategy = new FileNamingStrategy(pattern.getText(), LocalDateTime.now(), header);
        example.setText(namingStrategy.render(24, Constants.TYPE_PROCESSED, "disk", "11_22_33_Sun"));
    }

    private void prepareTokens() {
        var tokenList = tokens.getChildren();
        tokenList.add(new Label(I18N.string(JSolEx.class, "naming-patterns", "available.tokens")));
        for (Token e : Token.values()) {
            tokenList.add(new Label(e.token() + " - " + I18N.string(JSolEx.class, "naming-patterns", "token_" + e.name())));
        }
    }

    @FXML
    public void close() {
        FileNamingPatternsIO.saveDefaults(elements.getItems());
        this.selectedPattern = elements.getSelectionModel().getSelectedItem();
        requestClose();
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    public void cancel() {
        this.selectedPattern = null;
        requestClose();
    }

    @FXML
    public void reset() {
        elements.getItems().clear();
        elements.getItems().addAll(FileNamingPatternsIO.predefined());
        elements.getSelectionModel().select(0);
    }

    public Optional<NamedPattern> getSelectedPattern() {
        return Optional.ofNullable(selectedPattern);
    }

    @FXML
    public void removeSelectedItem() {
        var idx = elements.getSelectionModel().getSelectedIndex();
        elements.getItems().remove(idx);
    }

    @FXML
    public void addNewItem() {
        var newElement = new NamedPattern("Custom " + elements.getItems().size(), FileNamingStrategy.DEFAULT_TEMPLATE);
        elements.getItems().add(newElement);
        elements.getSelectionModel().select(newElement);
    }

}
