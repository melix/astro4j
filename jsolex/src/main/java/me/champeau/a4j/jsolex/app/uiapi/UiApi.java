/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.uiapi;

import javafx.stage.Window;
import me.champeau.a4j.jsolex.app.jfx.ProgressHandler;
import me.champeau.a4j.jsolex.server.ui.UiApiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Starts the UI API when requested through the {@value #ENV_VAR} environment variable.
 */
public final class UiApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiApi.class);

    /**
     * Environment variable holding the port of the UI API.
     */
    public static final String ENV_VAR = "JSOLEX_UI_API";

    private UiApi() {

    }

    /**
     * Starts the UI API server if the {@value #ENV_VAR} environment variable holds a valid port.
     *
     * @param mainWindow the main window of the application
     * @param progress supplies the latest progress state
     * @return the started server, or empty if not requested or invalid
     */
    public static Optional<UiApiServer> startIfRequested(Window mainWindow, Supplier<ProgressHandler.ProgressSnapshot> progress) {
        var value = System.getenv(ENV_VAR);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var port = parsePort(value);
        if (port.isEmpty()) {
            LOGGER.warn("Ignoring invalid value for {}: {}", ENV_VAR, value);
            return Optional.empty();
        }
        try {
            return Optional.of(UiApiServer.start(port.getAsInt(), new JavaFxUiInspector(mainWindow, progress)));
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to start the UI API on port {}", port.getAsInt(), e);
            return Optional.empty();
        }
    }

    /**
     * Parses a TCP port.
     *
     * @param value the value to parse
     * @return the port, or empty if the value is not a valid port
     */
    static OptionalInt parsePort(String value) {
        try {
            var port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(port);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
