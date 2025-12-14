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
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepository;
import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepositoryManager;
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.*;

/**
 * Controller for managing script repositories in the JSol'Ex application.
 * Provides UI for adding, editing, removing, and refreshing script repositories,
 * as well as viewing available scripts from each repository.
 */
public class ScriptRepositoriesController {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    @FXML
    private TableView<ScriptRepository> repositoriesTable;

    @FXML
    private TableColumn<ScriptRepository, Boolean> enabledColumn;

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

    /**
     * Creates a new script repositories controller.
     */
    public ScriptRepositoriesController() {
    }

    /**
     * Initializes the controller with the required dependencies and sets up the UI components.
     * Configures table columns, cell factories, and event handlers for managing repositories.
     *
     * @param stage the parent stage for this controller
     * @param configuration the application configuration containing repository settings
     */
    public void setup(Stage stage, Configuration configuration) {
        this.stage = stage;
        this.configuration = configuration;
        this.repositoryManager = new ScriptRepositoryManager();

        enabledColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private boolean updating = false;

            {
                checkBox.setOnAction(event -> {
                    if (updating) {
                        return;
                    }
                    var repository = getTableRow().getItem();
                    if (repository != null) {
                        var updated = repository.withEnabled(checkBox.isSelected());
                        var index = repositories.indexOf(repository);
                        repositories.set(index, updated);
                        saveRepositories();
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    updating = true;
                    checkBox.setSelected(getTableRow().getItem().enabled());
                    updating = false;
                    setGraphic(checkBox);
                }
            }
        });

        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        urlColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().url()));
        scriptsColumn.setCellValueFactory(cellData -> {
            var scripts = repositoryManager.getLocalScripts(cellData.getValue());
            return new SimpleStringProperty(String.valueOf(scripts.size()));
        });
        scriptsColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    setOnMouseClicked(null);
                } else {
                    var repository = getTableRow().getItem();
                    var scripts = repositoryManager.getLocalScripts(repository);
                    setText(String.valueOf(scripts.size()));
                    if (!scripts.isEmpty()) {
                        setStyle("-fx-text-fill: blue; -fx-underline: true; -fx-cursor: hand;");
                        setOnMouseClicked(event -> showScriptsSummary(repository));
                    } else {
                        setStyle("");
                        setOnMouseClicked(null);
                    }
                }
            }
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
            var dialogStage = newStage();
            dialogStage.initOwner(stage);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            var node = (Parent) fxmlLoader.load();
            var controller = (AddRepositoryController) fxmlLoader.getController();
            controller.setup(dialogStage, pair -> {
                var repository = new ScriptRepository(pair.getKey(), pair.getValue(), null);
                repositories.add(repository);
                saveRepositories();
                refreshRepositoryInBackground(repository, repositories.indexOf(repository));
            });
            var scene = newScene(node);
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
            var dialogStage = newStage();
            dialogStage.initOwner(stage);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            var node = (Parent) fxmlLoader.load();
            var controller = (AddRepositoryController) fxmlLoader.getController();
            controller.setupForEdit(dialogStage, selected.name(), selected.url(), pair -> {
                var updated = new ScriptRepository(pair.getKey(), pair.getValue(), selected.lastCheck());
                var index = repositories.indexOf(selected);
                repositories.set(index, updated);
                saveRepositories();
            });
            var scene = newScene(node);
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
                var repository = repositories.get(i);
                if (!repository.enabled()) {
                    continue;
                }
                try {
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

    private void showScriptsSummary(ScriptRepository repository) {
        var scripts = repositoryManager.getLocalScripts(repository);
        if (scripts.isEmpty()) {
            return;
        }

        var extractor = new ImageMathParameterExtractor();
        var currentLanguage = LocaleUtils.getConfiguredLocale().getLanguage();

        var dialogStage = newStage();
        dialogStage.initOwner(stage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(I18N.string(JSolEx.class, "script-repositories", "scripts.summary.title") + " - " + repository.name());

        var content = new VBox(10);
        content.setPadding(new Insets(15));

        for (var script : scripts) {
            var scriptBox = new VBox(5);
            scriptBox.setPadding(new Insets(10));
            scriptBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");

            var titleLabel = new Label(script.title());
            titleLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
            scriptBox.getChildren().add(titleLabel);

            var authorVersion = new Label(script.author() + " - v" + script.version());
            authorVersion.setStyle("-fx-text-fill: #666666;");
            scriptBox.getChildren().add(authorVersion);

            try {
                if (Files.exists(script.localPath())) {
                    var extractionResult = extractor.extractParameters(script.localPath());
                    var description = extractionResult.getDisplayDescription(currentLanguage);
                    if (description != null && !description.isEmpty()) {
                        var descLabel = new Label(description);
                        descLabel.setWrapText(true);
                        descLabel.setMaxWidth(500);
                        descLabel.setStyle("-fx-padding: 5 0 0 0;");
                        scriptBox.getChildren().add(descLabel);
                    }
                }
            } catch (Exception e) {
            }

            content.getChildren().add(scriptBox);
        }

        var scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(550, 400);

        var closeButton = new Button(I18N.string(JSolEx.class, "script-repositories", "close"));
        closeButton.getStyleClass().add("primary-button");

        closeButton.setOnAction(e -> dialogStage.close());

        var buttonBox = new VBox(10);
        buttonBox.setPadding(new Insets(10));
        buttonBox.getChildren().add(closeButton);
        buttonBox.setStyle("-fx-alignment: center;");

        var root = new VBox(scrollPane, buttonBox);
        var scene = newScene(root);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }
}
