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

import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.util.FxUtils;

/**
 * A small modal dialog showing a progress bar and the item currently being
 * processed during a session export or import. Being modal, it also prevents the
 * user from launching the same operation a second time while it is running.
 */
public class SessionProgressDialog {
    private final Stage stage;
    private final ProgressBar progressBar;
    private final Label statusLabel;

    public SessionProgressDialog(Stage owner, String title) {
        this.progressBar = new ProgressBar(0);
        this.progressBar.setPrefWidth(360);
        this.statusLabel = new Label(title);
        this.statusLabel.setWrapText(true);
        this.statusLabel.setMaxWidth(360);
        var content = new VBox(12, statusLabel, progressBar);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(20));
        this.stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setScene(JSolEx.newScene(content));
        stage.setResizable(false);
        stage.setOnCloseRequest(Event::consume);
    }

    public void show() {
        FxUtils.runLater(stage::show);
    }

    public void update(double fraction, String message) {
        FxUtils.runLater(() -> {
            if (fraction > progressBar.getProgress()) {
                progressBar.setProgress(fraction);
            }
            if (message != null) {
                statusLabel.setText(message);
            }
        });
    }

    public void close() {
        FxUtils.runLater(() -> {
            stage.setOnCloseRequest(null);
            stage.close();
        });
    }
}
