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
package me.champeau.a4j.serplayer.controls;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import me.champeau.a4j.serplayer.PlayerController;

import java.io.IOException;

/** Control panel for video player operations. */
public class PlayerControl extends HBox {
    private final PlayerController controller;

    /**
     * Creates a new player control panel.
     * @param controller the player controller to use
     */
    public PlayerControl(PlayerController controller) {
        this.controller = controller;
        var fxmlLoader = new FXMLLoader(getClass().getResource(
                "player.fxml")
        );
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /** Handles the play/pause action. */
    @FXML
    public void play() {
        controller.play();
    }

    /** Handles the rewind action. */
    @FXML
    public void rewind() {
        controller.rewind();
    }

    /** Handles the forward action. */
    @FXML
    public void forward() {
        controller.forward();
    }
}
