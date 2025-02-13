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

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.server.JSolexServer;

public class EmbeddedServerController {

    @FXML
    private VBox serverUrls;

    @FXML
    private TextField serverPort;

    @FXML
    private Button startStopButton;

    @FXML
    private CheckBox startOnLaunch;

    private Stage stage;
    private ApplicationContext context;
    private HostServices hostServices;
    private Configuration configuration;

    public void setup(Stage stage, ApplicationContext context, HostServices hostServices, Configuration configuration) {
        this.stage = stage;
        this.context = context;
        this.hostServices = hostServices;
        this.configuration = configuration;
        serverPort.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value == null || value < 1024 || value > 65535) {
                    return JSolEx.EMBEDDED_SERVER_DEFAULT_PORT;
                }
                return value;
            }
        }));
        if (context != null) {
            serverPort.setText(String.valueOf(context.getBean(EmbeddedServer.class).getPort()));
        } else {
            serverPort.setText(String.valueOf(configuration.getAutoStartServerPort()));
        }
        startOnLaunch.setSelected(configuration.isAutoStartServer());
        updateStartStopLabel();
    }

    private void updateStartStopLabel() {
        if (isStopped()) {
            serverPort.setDisable(false);
            startStopButton.setText(I18N.string(JSolEx.class, "embedded-server", "start"));
            serverUrls.getChildren().setAll(
                new Label(I18N.string(JSolEx.class, "embedded-server", "server.not.started"))
            );
        } else {
            serverPort.setDisable(true);
            startStopButton.setText(I18N.string(JSolEx.class, "embedded-server", "stop"));
            var contextURI = context.getBean(EmbeddedServer.class).getContextURI();
            var server = context.getBean(EmbeddedServer.class);
            var urls = JSolexServer.getServerUrls(server);
            serverUrls.getChildren().clear();
            for (var url : urls) {
                addServerUrl(url);
            }
            if (serverUrls.getChildren().isEmpty()) {
                addServerUrl(contextURI.toString());
            }
        }
    }

    private void addServerUrl(String url) {
        var label = new Hyperlink(url);
        label.setOnAction(e -> hostServices.showDocument(url));
        serverUrls.getChildren().add(label);
    }

    private boolean isStopped() {
        return context == null || !context.isRunning();
    }

    @FXML
    public void startOrStop() {
        if (isStopped()) {
            context = JSolexServer.start(Integer.parseInt(serverPort.getText()), ProcessParamsIO.loadDefaults());
        } else {
            context.stop();
            context = null;
        }
        updateStartStopLabel();
    }

    @FXML
    public void close() {
        configuration.setAutoStartServer(startOnLaunch.isSelected());
        configuration.setAutoStartServerPort(Integer.parseInt(serverPort.getText()));
        stage.close();
    }

    public ApplicationContext getApplicationContext() {
        return context;
    }
}
