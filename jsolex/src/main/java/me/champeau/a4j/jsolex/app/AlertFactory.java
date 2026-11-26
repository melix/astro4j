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
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;

/**
 * Factory for creating styled dialogs. Every dialog of the application must be created
 * through this factory, so that its buttons use the application style: the default button
 * is the primary one, the other buttons are secondary.
 */
public class AlertFactory {
    private AlertFactory() {
    }

    private static Alert newAlert(Alert.AlertType type, String message) {
        var alert = style(new Alert(type));
        alert.setWidth(600);
        alert.setResizable(true);
        if (message != null) {
            alert.setContentText(message);
        }
        return alert;
    }

    /**
     * Applies the application style to a dialog. The buttons are styled when the dialog
     * is shown, whatever the dialog does with its own show handlers.
     *
     * @param dialog the dialog to style
     * @param <D> the type of the dialog
     * @return the same dialog
     */
    public static <D extends Dialog<?>> D style(D dialog) {
        var dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(JSolEx.class.getResource("components.css").toExternalForm());
        dialogPane.getStyleClass().add("params-dialog");
        dialogPane.setMinHeight(Region.USE_PREF_SIZE);
        if (dialogPane.getHeader() != null) {
            dialogPane.getHeader().getStyleClass().add("params-header");
        }
        if (dialogPane.getContent() != null) {
            dialogPane.getContent().getStyleClass().add("parameter-panel");
        }
        dialog.showingProperty().addListener((obs, wasShowing, showing) -> {
            if (showing) {
                styleButtons(dialogPane);
            }
        });
        return dialog;
    }

    private static void styleButtons(DialogPane dialogPane) {
        var buttons = dialogPane.getButtonTypes().stream()
                .map(dialogPane::lookupButton)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
        var primary = buttons.stream()
                .filter(Button::isDefaultButton)
                .findFirst()
                .or(() -> buttons.stream().filter(button -> !button.isCancelButton()).findFirst());
        for (var button : buttons) {
            button.getStyleClass().removeAll("button", "primary-button", "default-button");
            button.getStyleClass().add(primary.filter(button::equals).isPresent() ? "primary-button" : "default-button");
            button.setStyle(button.getStyle() + "; -fx-alignment: center; -fx-text-alignment: center;");
        }
    }

    /**
     * Creates an alert of the given type with no message.
     * @param type the alert type
     * @return the alert
     */
    public static Alert of(Alert.AlertType type) {
        return newAlert(type, null);
    }

    /**
     * Creates an alert of the given type with a message.
     * @param type the alert type
     * @param message the message to display
     * @return the alert
     */
    public static Alert of(Alert.AlertType type, String message) {
        return newAlert(type, message);
    }

    /**
     * Creates a dialog with no content.
     * @param <T> the result type
     * @return the dialog
     */
    public static <T> Dialog<T> dialog() {
        return style(new Dialog<>());
    }

    /**
     * Creates a text input dialog.
     * @return the dialog
     */
    public static TextInputDialog textInput() {
        return style(new TextInputDialog());
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
