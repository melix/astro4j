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
package me.champeau.a4j.jsolex.app;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;

/**
 * Factory for creating styled alert dialogs.
 */
public class AlertFactory {
    private AlertFactory() {
    }

    private static Alert newAlert(Alert.AlertType type, String message) {
        var alert = new Alert(type);
        alert.setWidth(600);
        alert.setResizable(true);
        if (message != null) {
            alert.setContentText(message);
        }

        var dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());

        dialogPane.getStyleClass().add("params-dialog");

        if (dialogPane.getHeader() != null) {
            dialogPane.getHeader().getStyleClass().add("params-header");
        }

        if (dialogPane.getContent() != null) {
            dialogPane.getContent().getStyleClass().add("parameter-panel");
        }

        alert.setOnShown(e -> {
            for (var buttonType : dialogPane.getButtonTypes()) {
                var button = (Button) dialogPane.lookupButton(buttonType);
                if (button != null) {
                    button.getStyleClass().removeAll("button");
                    if (buttonType == ButtonType.CANCEL || buttonType == ButtonType.NO) {
                        button.getStyleClass().add("default-button");
                    } else {
                        button.getStyleClass().add("primary-button");
                    }
                    button.setStyle(button.getStyle() + "; -fx-alignment: center; -fx-text-alignment: center;");
                }
            }
        });

        return alert;
    }

    /**
     * Creates an information alert with no message.
     * @return the alert
     */
    public static Alert info() {
        return newAlert(Alert.AlertType.INFORMATION, null);
    }

    /**
     * Creates an information alert with a message.
     * @param message the message to display
     * @return the alert
     */
    public static Alert info(String message) {
        return newAlert(Alert.AlertType.INFORMATION, message);
    }

    /**
     * Creates an error alert with a message.
     * @param message the error message
     * @return the alert
     */
    public static Alert error(String message) {
        return newAlert(Alert.AlertType.ERROR, message);
    }

    /**
     * Creates a warning alert with a message.
     * @param message the warning message
     * @return the alert
     */
    public static Alert warning(String message) {
        return newAlert(Alert.AlertType.WARNING, message);
    }

    /**
     * Creates a confirmation alert with a message.
     * @param message the confirmation message
     * @return the alert
     */
    public static Alert confirmation(String message) {
        return newAlert(Alert.AlertType.CONFIRMATION, message);
    }
}
