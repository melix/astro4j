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

import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;

/**
 * JavaFX utility methods.
 */
public class FXUtils {
    private FXUtils() {

    }

    /**
     * Creates a new stage with ESCAPE key handling.
     *
     * @return a new stage which closes when ESCAPE is pressed
     */
    public static Stage newStage() {
        var stage = new Stage();
        stage.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (KeyCode.ESCAPE == event.getCode()) {
                stage.close();
            }
        });
        return stage;
    }

    /**
     * Creates a new modal dialog stage with proper configuration for macOS compatibility.
     * The stage is configured with:
     * - Owner window set
     * - Application modal modality
     * - Non-resizable (suitable for dialogs)
     * - Scene with JSolEx stylesheet applied
     * - Size adjusted to content
     * - ESCAPE key handling to close
     *
     * @param owner the owner stage
     * @param content the root node for the scene
     * @return a new modal stage
     */
    public static Stage newModalStage(Stage owner, Parent content) {
        var stage = newStage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(JSolEx.newScene(content));
        stage.sizeToScene();
        return stage;
    }

}
