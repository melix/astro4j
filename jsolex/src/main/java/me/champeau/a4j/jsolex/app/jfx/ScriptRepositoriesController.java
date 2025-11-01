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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepository;
import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepositoryManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class ScriptRepositoriesController {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    @FXML
    private TableView<ScriptRepository> repositoriesTable;

    @FXML
    private TableColumn<ScriptRepository, String> nameColumn;

    @FXML
    private TableColumn<ScriptRepository, String> urlColumn;

    @FXML
    private TableColumn<ScriptRepository, String> scriptsColumn;

    @FXML
    private TableColumn<ScriptRepository, String> lastCheckColumn;

    @FXML
    private Button editButton;

    @FXML
    private Button removeButton;

    @FXML
    private Button refreshButton;

    private Stage stage;
    private Configuration configuration;
    private ScriptRepositoryManager repositoryManager;
    private ObservableList<ScriptRepository> repositories;

    public void setup(Stage stage, Configuration configuration) {
        this.stage = stage;
        this.configuration = configuration;
        this.repositoryManager = new ScriptRepositoryManager();

        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        urlColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().url()));
        scriptsColumn.setCellValueFactory(cellData -> {
            var scripts = repositoryManager.getLocalScripts(cellData.getValue());
            return new SimpleStringProperty(String.valueOf(scripts.size()));
        });
        lastCheckColumn.setCellValueFactory(cellData -> {
            var lastCheck = cellData.getValue().lastCheck();
            return new SimpleStringProperty(lastCheck != null ? DATE_FORMATTER.format(lastCheck) : "-");
        });

        repositories = FXCollections.observableArrayList(configuration.getScriptRepositories());
        repositoriesTable.setItems(repositories);

        repositoriesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            var hasSelection = newSelection != null;
            editButton.setDisable(!hasSelection);
            removeButton.setDisable(!hasSelection);
        });
    }

    @FXML
    private void addRepository() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "add-repository");
        try {
            var dialogStage = FXUtils.newStage();
            dialogStage.initOwner(stage);
            var node = (Parent) fxmlLoader.load();
            var controller = (AddRepositoryController) fxmlLoader.getController();
            controller.setup(dialogStage, pair -> {
                var repository = new ScriptRepository(pair.getKey(), pair.getValue(), null);
                repositories.add(repository);
                saveRepositories();
                refreshRepositoryInBackground(repository, repositories.indexOf(repository));
            });
            var scene = JSolEx.newScene(node);
            dialogStage.setTitle(I18N.string(JSolEx.class, "script-repositories", "repository.add.title"));
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        } catch (Exception e) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.add.error") + ": " + e.getMessage());
            alert.initOwner(stage);
            alert.showAndWait();
        }
    }

    @FXML
    private void editRepository() {
        var selected = repositoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "add-repository");
        try {
            var dialogStage = FXUtils.newStage();
            dialogStage.initOwner(stage);
            var node = (Parent) fxmlLoader.load();
            var controller = (AddRepositoryController) fxmlLoader.getController();
            controller.setupForEdit(dialogStage, selected.name(), selected.url(), pair -> {
                var updated = new ScriptRepository(pair.getKey(), pair.getValue(), selected.lastCheck());
                var index = repositories.indexOf(selected);
                repositories.set(index, updated);
                saveRepositories();
            });
            var scene = JSolEx.newScene(node);
            dialogStage.setTitle(I18N.string(JSolEx.class, "script-repositories", "repository.edit.title"));
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        } catch (Exception e) {
            var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.edit.error") + ": " + e.getMessage());
            alert.initOwner(stage);
            alert.showAndWait();
        }
    }

    @FXML
    private void removeRepository() {
        var selected = repositoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        var alert = AlertFactory.confirmation(I18N.string(JSolEx.class, "script-repositories", "repository.remove.confirm"));
        var result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            repositories.remove(selected);
            repositoryManager.cleanRepository(selected);
            saveRepositories();
        }
    }

    @FXML
    private void refreshRepository() {
        var selected = repositoriesTable.getSelectionModel().getSelectedItem();

        refreshButton.setDisable(true);
        refreshButton.setText(I18N.string(JSolEx.class, "script-repositories", "repository.refreshing"));

        if (selected == null) {
            refreshAllRepositories();
        } else {
            var index = repositories.indexOf(selected);
            refreshRepositoryInBackground(selected, index);
        }
    }

    private void refreshAllRepositories() {
        new Thread(() -> {
            for (int i = 0; i < repositories.size(); i++) {
                try {
                    var repository = repositories.get(i);
                    repositoryManager.refreshRepository(repository);
                    var updated = repository.withLastCheck(Instant.now());
                    final int index = i;
                    Platform.runLater(() -> {
                        repositories.set(index, updated);
                        saveRepositories();
                        repositoriesTable.refresh();
                    });
                } catch (Exception e) {
                    final int index = i;
                    Platform.runLater(() -> {
                        var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.refresh.error") + " (" + repositories.get(index).name() + "): " + e.getMessage());
                        alert.showAndWait();
                    });
                }
            }
            Platform.runLater(() -> {
                refreshButton.setDisable(false);
                refreshButton.setText(I18N.string(JSolEx.class, "script-repositories", "repository.refresh"));
            });
        }).start();
    }

    private void refreshRepositoryInBackground(ScriptRepository repository, int index) {
        new Thread(() -> {
            try {
                repositoryManager.refreshRepository(repository);
                var updated = repository.withLastCheck(Instant.now());
                Platform.runLater(() -> {
                    repositories.set(index, updated);
                    saveRepositories();
                    repositoriesTable.refresh();
                    if (refreshButton != null) {
                        refreshButton.setDisable(false);
                        refreshButton.setText(I18N.string(JSolEx.class, "script-repositories", "repository.refresh"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (refreshButton != null) {
                        refreshButton.setDisable(false);
                        refreshButton.setText(I18N.string(JSolEx.class, "script-repositories", "repository.refresh"));
                    }

                    var alert = AlertFactory.error(I18N.string(JSolEx.class, "script-repositories", "repository.refresh.error") + ": " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    private void close() {
        stage.close();
    }

    private void saveRepositories() {
        configuration.setScriptRepositories(new ArrayList<>(repositories));
    }
}
