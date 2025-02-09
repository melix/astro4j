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

public class AlertFactory {
    private static Alert newAlert(Alert.AlertType type, String message) {
        var alert = new Alert(type);
        alert.setWidth(600);
        alert.setResizable(true);
        if (message != null) {
            alert.setContentText(message);
        }
        return alert;
    }

    public static Alert info() {
        return newAlert(Alert.AlertType.INFORMATION, null);
    }

    public static Alert info(String message) {
        return newAlert(Alert.AlertType.INFORMATION, message);
    }

    public static Alert error(String message) {
        return newAlert(Alert.AlertType.ERROR, message);
    }

    public static Alert warning(String message) {
        return newAlert(Alert.AlertType.WARNING, message);
    }

    public static Alert confirmation(String message) {
        return newAlert(Alert.AlertType.CONFIRMATION, message);
    }
}
