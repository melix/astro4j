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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Dashboard strip displayed above the batch table, showing global progress
 * and per-state counts.
 */
public final class BatchDashboard {

    private BatchDashboard() {
    }

    /**
     * Builds the dashboard node bound to the given batch items.
     *
     * @param items the batch items
     * @param batchScriptsRunning flag set while the post-processing batch outputs scripts are running
     * @param onAddFiles action triggered to add more files to the finished batch
     * @return the dashboard node, ready to be inserted at the top of the batch view
     */
    public static Node create(List<BatchItem> items, AtomicBoolean batchScriptsRunning, Runnable onAddFiles) {
        var headerLabel = new Label(message("batch.dashboard.progress"));
        headerLabel.setStyle("-fx-font-weight: bold;");
        headerLabel.setMinWidth(Region.USE_PREF_SIZE);

        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(280);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        var progressOverlay = new Label();
        progressOverlay.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a1a;");
        progressOverlay.setMouseTransparent(true);
        progressOverlay.setVisible(false);
        progressOverlay.setManaged(false);

        var progressStack = new StackPane(progressBar, progressOverlay);
        progressStack.setMaxWidth(Double.MAX_VALUE);
        progressBar.prefWidthProperty().bind(progressStack.widthProperty());

        var summaryLabel = new Label();
        summaryLabel.setMinWidth(Region.USE_PREF_SIZE);

        var addFilesButton = new Button(message("batch.add.files"));
        addFilesButton.getStyleClass().add("default-button");
        addFilesButton.setMinWidth(Region.USE_PREF_SIZE);
        addFilesButton.setDisable(true);
        addFilesButton.setOnAction(e -> {
            addFilesButton.setDisable(true);
            onAddFiles.run();
        });

        var doneChip = createChip("✓", "-fx-background-color: #d6f5d6; -fx-text-fill: #2c662c;");
        var runningChip = createChip("▶", "-fx-background-color: #d6e4f5; -fx-text-fill: #2c4d80;");
        var queuedChip = createChip("⏳", "-fx-background-color: #ececec; -fx-text-fill: #555;");
        var errorChip = createChip("✗", "-fx-background-color: #f5d6d6; -fx-text-fill: #802c2c;");

        var topRow = new HBox(12, headerLabel, progressStack, summaryLabel, addFilesButton);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressStack, Priority.ALWAYS);

        var chipsRow = new HBox(10, doneChip.node(), runningChip.node(), queuedChip.node(), errorChip.node());
        chipsRow.setAlignment(Pos.CENTER_LEFT);

        var root = new VBox(8, topRow, chipsRow);
        root.setPadding(new Insets(10, 14, 10, 14));
        root.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border; -fx-border-width: 0 0 1 0;");

        Runnable refresh = () -> updateAll(items, batchScriptsRunning,
                progressBar, progressOverlay, summaryLabel, addFilesButton,
                doneChip.text(), runningChip.text(), queuedChip.text(), errorChip.text());

        ChangeListener<Object> listener = (obs, oldV, newV) -> FxUtils.runLater(refresh);
        for (var item : items) {
            item.status().addListener(listener);
        }

        var timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh.run()));
        timeline.setCycleCount(Animation.INDEFINITE);

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                timeline.play();
            } else {
                timeline.stop();
            }
        });

        refresh.run();
        return root;
    }

    private static Chip createChip(String icon, String style) {
        var iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-weight: bold;");
        var textLabel = new Label("");
        var box = new HBox(6, iconLabel, textLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(3, 10, 3, 10));
        box.setStyle(style + " -fx-background-radius: 12;");
        return new Chip(box, textLabel);
    }

    private static void updateAll(List<BatchItem> items,
                                  AtomicBoolean batchScriptsRunning,
                                  ProgressBar progressBar,
                                  Label progressOverlay,
                                  Label summaryLabel,
                                  Button addFilesButton,
                                  Label doneText,
                                  Label runningText,
                                  Label queuedText,
                                  Label errorText) {
        int done = 0;
        int running = 0;
        int queued = 0;
        int errors = 0;
        for (var item : items) {
            switch (BatchItemState.of(item.status().get())) {
                case DONE -> done++;
                case ERROR -> errors++;
                case QUEUED -> queued++;
                case RUNNING -> running++;
            }
        }
        int total = items.size();
        int finished = done + errors;
        boolean scriptsRunning = batchScriptsRunning.get();
        boolean allFilesDone = total > 0 && finished == total;
        addFilesButton.setDisable(!allFilesDone || scriptsRunning);
        double progress;
        if (allFilesDone && scriptsRunning) {
            progress = ProgressBar.INDETERMINATE_PROGRESS;
        } else {
            progress = total == 0 ? 0 : (double) finished / total;
        }

        progressBar.setProgress(progress);
        boolean showOverlay = allFilesDone && scriptsRunning;
        progressOverlay.setVisible(showOverlay);
        progressOverlay.setManaged(showOverlay);
        if (showOverlay) {
            progressOverlay.setText(message("batch.dashboard.running.scripts"));
        }
        int percent = total == 0 ? 0 : (int) Math.round((double) finished / total * 100);
        summaryLabel.setText(finished + " / " + total + "  (" + percent + "%)");
        doneText.setText(done + " " + message("batch.dashboard.done"));
        runningText.setText(running + " " + message("batch.dashboard.running"));
        queuedText.setText(queued + " " + message("batch.dashboard.queued"));
        errorText.setText(errors + " " + message("batch.dashboard.errors"));
    }

    private record Chip(HBox node, Label text) {
    }
}
