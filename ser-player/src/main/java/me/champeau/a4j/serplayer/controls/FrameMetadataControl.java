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
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;

/** Control panel displaying metadata information for video frames. */
public class FrameMetadataControl extends VBox {
    @FXML private Label fileNameTitle;
    @FXML private Label fileNameValue;

    @FXML private Label colorModeTitle;
    @FXML private Label colorModeValue;

    @FXML private Label frameTitle;
    @FXML private Label frameValue;

    @FXML private Label fpsTitle;
    @FXML private Label fpsValue;

    @FXML private Label timestampValue;

    /** Creates a new frame metadata control panel. */
    public FrameMetadataControl() {
        var fxmlLoader = new FXMLLoader(getClass().getResource(
                "file-metadata.fxml")
        );
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Sets the filename display.
     * @param name the filename to display
     */
    public void setFilename(String name) {
        fileNameValue.textProperty().set(name);
    }

    /**
     * Sets the color mode display.
     * @param colorMode the color mode to display
     */
    public void setColorMode(String colorMode) {
        colorModeValue.textProperty().set(colorMode);
    }

    /**
     * Sets the frame number display.
     * @param frame the frame number to display
     */
    public void setFrame(String frame) {
        frameValue.textProperty().set(frame);
    }

    /**
     * Sets the frames per second display.
     * @param text the FPS text to display
     */
    public void setFps(String text) {
        fpsValue.textProperty().set(text);
    }

    /**
     * Sets the timestamp display.
     * @param timestamp the timestamp to display
     */
    public void setTimestamp(String timestamp) {
        timestampValue.textProperty().set(timestamp);
    }
}
