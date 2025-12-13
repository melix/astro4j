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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Pair;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.function.Consumer;

/**
 * Controller for the add/edit repository dialog.
 */
public class AddRepositoryController {
    /** Creates a new controller. */
    public AddRepositoryController() {
    }

    @FXML
    private TextField nameField;

    @FXML
    private TextField urlField;

    @FXML
    private Button addButton;

    private Stage stage;
    private Consumer<Pair<String, String>> onAdd;
    private boolean editMode;

    /**
     * Sets up the controller for adding a new repository.
     * @param stage the dialog stage
     * @param onAdd callback when repository is added
     */
    public void setup(Stage stage, Consumer<Pair<String, String>> onAdd) {
        this.stage = stage;
        this.onAdd = onAdd;
        this.editMode = false;
    }

    /**
     * Sets up the controller for editing an existing repository.
     * @param stage the dialog stage
     * @param existingName the current repository name
     * @param existingUrl the current repository URL
     * @param onSave callback when repository is saved
     */
    public void setupForEdit(Stage stage, String existingName, String existingUrl, Consumer<Pair<String, String>> onSave) {
        this.stage = stage;
        this.onAdd = onSave;
        this.editMode = true;
        nameField.setText(existingName);
        nameField.setDisable(true);
        urlField.setText(existingUrl);
        addButton.setText(I18N.string(JSolEx.class, "add-repository", "repository.save"));
    }

    @FXML
    private void add() {
        var name = nameField.getText().trim();
        var url = urlField.getText().trim();

        if (name.isEmpty()) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.name.empty"));
            alert.initOwner(stage);
            alert.showAndWait();
            return;
        }

        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.invalid.url"));
            alert.initOwner(stage);
            alert.showAndWait();
            return;
        }

        if (onAdd != null) {
            onAdd.accept(new Pair<>(name, url));
        }
        stage.close();
    }

    @FXML
    private void cancel() {
        stage.close();
    }
}
