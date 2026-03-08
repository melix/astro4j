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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.SpectroSolHubApi.RepositoryDetail;
import me.champeau.a4j.jsolex.app.jfx.SpectroSolHubApi.ScriptInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class BrowseSpectroSolHubController {
    private static final String CARD_STYLE = "-fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: #ffffff;";
    private static final String CARD_HOVER_STYLE = "-fx-border-color: #007bff; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: #f8f9fa;";

    @FXML
    private StackPane contentPane;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox repositoriesPane;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Label statusLabel;

    private Stage stage;
    private SpectroSolHubApi api;
    private Set<String> addedUrls;
    private BiConsumer<String, String> onRepositorySelected;

    public BrowseSpectroSolHubController() {
    }

    public void setup(Stage stage, String baseUrl, Set<String> existingUrls, BiConsumer<String, String> onRepositorySelected) {
        this.stage = stage;
        this.api = new SpectroSolHubApi(baseUrl);
        this.addedUrls = new HashSet<>(existingUrls);
        this.onRepositorySelected = onRepositorySelected;
        fetchRepositories();
    }

    private void fetchRepositories() {
        progressIndicator.setVisible(true);
        statusLabel.setVisible(true);
        statusLabel.setText(I18N.string(JSolEx.class, "browse-spectrosolhub", "loading"));
        scrollPane.setVisible(false);

        new Thread(() -> {
            try {
                var details = api.fetchAllRepositoryDetails();
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    if (details.isEmpty()) {
                        statusLabel.setText(I18N.string(JSolEx.class, "browse-spectrosolhub", "no.repositories"));
                        return;
                    }
                    statusLabel.setVisible(false);
                    scrollPane.setVisible(true);
                    repositoriesPane.getChildren().clear();
                    for (var detail : details) {
                        repositoriesPane.getChildren().add(createRepositoryCard(detail));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText(I18N.string(JSolEx.class, "browse-spectrosolhub", "fetch.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }

    private VBox createRepositoryCard(RepositoryDetail repo) {
        var card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle(CARD_STYLE);

        var nameLabel = new Label(repo.name());
        nameLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        var ownerLabel = new Label(I18N.string(JSolEx.class, "browse-spectrosolhub", "by") + " " + repo.owner());
        ownerLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12;");

        var headerLeft = new VBox(2, nameLabel, ownerLabel);

        var repoUrl = api.buildScriptsUrl(repo.owner(), repo.slug());
        var alreadyAdded = addedUrls.contains(repoUrl);

        var addButton = new Button(alreadyAdded
            ? I18N.string(JSolEx.class, "browse-spectrosolhub", "added")
            : I18N.string(JSolEx.class, "browse-spectrosolhub", "add.repository"));
        addButton.getStyleClass().add(alreadyAdded ? "default-button" : "primary-button");
        addButton.setDisable(alreadyAdded);
        addButton.setOnAction(e -> {
            onRepositorySelected.accept(repo.name(), repoUrl);
            addedUrls.add(repoUrl);
            addButton.setDisable(true);
            addButton.setText(I18N.string(JSolEx.class, "browse-spectrosolhub", "added"));
            addButton.getStyleClass().remove("primary-button");
            addButton.getStyleClass().add("default-button");
        });

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var headerRow = new HBox(8, headerLeft, spacer, addButton);
        headerRow.setAlignment(Pos.TOP_CENTER);
        card.getChildren().add(headerRow);

        if (!repo.description().isEmpty()) {
            var descLabel = new Label(repo.description());
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-padding: 4 0 4 0;");
            card.getChildren().add(descLabel);
        }

        var statsBox = new HBox(16);
        statsBox.setPadding(new Insets(4, 0, 0, 0));

        var scriptCount = repo.scripts().size();
        var scriptKey = scriptCount <= 1 ? "script" : "scripts";
        var scriptsCountLabel = new Label(scriptCount + " " + I18N.string(JSolEx.class, "browse-spectrosolhub", scriptKey));
        scriptsCountLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 11;");

        var starsLabel = new Label("\u2605 " + repo.starCount());
        starsLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 11;");

        var totalDownloads = repo.totalDownloads();
        var downloadsLabel = new Label(totalDownloads + " \u2193");
        downloadsLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 11;");

        statsBox.getChildren().addAll(scriptsCountLabel, starsLabel, downloadsLabel);
        card.getChildren().add(statsBox);

        if (!repo.scripts().isEmpty()) {
            var scriptsBox = new VBox(6);
            for (var script : repo.scripts()) {
                scriptsBox.getChildren().add(createScriptEntry(script));
            }
            var titledPane = new TitledPane(
                I18N.string(JSolEx.class, "browse-spectrosolhub", "scripts.details"),
                scriptsBox
            );
            titledPane.setExpanded(false);
            titledPane.setAnimated(true);
            card.getChildren().add(titledPane);
        }

        card.setOnMouseEntered(e -> card.setStyle(CARD_HOVER_STYLE));
        card.setOnMouseExited(e -> card.setStyle(CARD_STYLE));

        return card;
    }

    private VBox createScriptEntry(ScriptInfo script) {
        var entry = new VBox(3);
        entry.setPadding(new Insets(6, 8, 6, 8));
        entry.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 4;");

        var titleLabel = new Label(script.title());
        titleLabel.setFont(Font.font(null, FontWeight.SEMI_BOLD, 12));
        entry.getChildren().add(titleLabel);

        var metaItems = new StringBuilder();
        if (!script.author().isEmpty()) {
            metaItems.append(script.author());
        }
        if (!script.version().isEmpty()) {
            metaItems.append(" \u2022 v").append(script.version());
        }
        if (script.fileCount() > 1) {
            metaItems.append(" \u2022 ").append(script.fileCount()).append(" ").append(I18N.string(JSolEx.class, "browse-spectrosolhub", "files"));
        }
        if (script.downloadCount() > 0) {
            metaItems.append(" \u2022 ").append(script.downloadCount()).append(" \u2193");
        }
        if (!script.requiresVersion().isEmpty()) {
            metaItems.append(" \u2022 \u2265 ").append(script.requiresVersion());
        }

        if (!metaItems.isEmpty()) {
            var metaLabel = new Label(metaItems.toString());
            metaLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11;");
            entry.getChildren().add(metaLabel);
        }

        if (!script.description().isEmpty()) {
            var descLabel = new Label(script.description());
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 11; -fx-padding: 2 0 0 0;");
            entry.getChildren().add(descLabel);
        }

        return entry;
    }

    @FXML
    private void close() {
        stage.close();
    }
}
