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
package me.champeau.a4j.jsolex.server.ui;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Local-only HTTP server exposing a read-only view of the user interface.
 * It runs in a dedicated application context, isolated from the embedded web server.
 */
public final class UiApiServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiApiServer.class);

    /**
     * Name of the Micronaut environment of the UI API.
     */
    public static final String ENVIRONMENT = "ui-api";

    /**
     * Path of the generated OpenAPI document.
     */
    public static final String OPENAPI_PATH = "/openapi/jsolex-ui-api.yml";

    private static final String HOST = "127.0.0.1";

    private final ApplicationContext context;
    private final EmbeddedServer server;

    private UiApiServer(ApplicationContext context, EmbeddedServer server) {
        this.context = context;
        this.server = server;
    }

    /**
     * Starts the UI API server.
     *
     * @param port the port to listen on, or 0 for a random port
     * @param inspector the inspector giving access to the user interface
     * @return the started server
     */
    public static UiApiServer start(int port, UiInspector inspector) {
        var context = ApplicationContext.builder()
            .environments(ENVIRONMENT)
            .deduceEnvironment(false)
            .properties(Map.of(
                "micronaut.server.host", HOST,
                "micronaut.server.port", port
            ))
            .singletons(inspector)
            .banner(false)
            .start();
        try {
            var server = context.getBean(EmbeddedServer.class);
            server.start();
            var url = "http://" + HOST + ":" + server.getPort();
            LOGGER.info("UI API listening on {}/ui (OpenAPI: {}{})", url, url, OPENAPI_PATH);
            return new UiApiServer(context, server);
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    /**
     * Returns the port the server listens on.
     *
     * @return the port
     */
    public int port() {
        return server.getPort();
    }

    /**
     * Stops the server.
     */
    public void stop() {
        server.stop();
        context.close();
    }
}
