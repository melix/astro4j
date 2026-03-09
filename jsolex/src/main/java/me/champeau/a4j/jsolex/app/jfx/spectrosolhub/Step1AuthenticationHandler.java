/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.spectrosolhub;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.SpectroSolHubClient;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.QuotaResponse;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.SpectroSolHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.jfx.spectrosolhub.SpectroSolHubSubmissionController.message;

class Step1AuthenticationHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step1AuthenticationHandler.class);

    private final Consumer<Boolean> onAuthenticationChanged;
    private boolean authenticated;
    private QuotaResponse quotaResponse;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField totpField;
    private Label statusLabel;
    private Label connectedLabel;
    private VBox loginForm;
    private VBox connectedInfo;

    Step1AuthenticationHandler(Consumer<Boolean> onAuthenticationChanged) {
        this.onAuthenticationChanged = onAuthenticationChanged;
    }

    @Override
    public VBox createContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setAlignment(Pos.CENTER);

        loginForm = createLoginForm();
        connectedInfo = createConnectedInfo();

        content.getChildren().addAll(loginForm, connectedInfo);
        VBox.setVgrow(loginForm, Priority.ALWAYS);
        VBox.setVgrow(connectedInfo, Priority.ALWAYS);
        return content;
    }

    private VBox createLoginForm() {
        var form = new VBox(15);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(450);

        var title = new Label(message("auth.title"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var instructions = new Label(message("auth.instructions"));
        instructions.setWrapText(true);
        instructions.setMaxWidth(450);

        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label(message("auth.username.label")), 0, 0);
        usernameField = new TextField();
        usernameField.setPromptText(message("auth.username.prompt"));
        usernameField.setPrefWidth(300);
        grid.add(usernameField, 1, 0);

        grid.add(new Label(message("auth.password.label")), 0, 1);
        passwordField = new PasswordField();
        passwordField.setPrefWidth(300);
        grid.add(passwordField, 1, 1);

        var totpLabel = new Label(message("auth.totp.label"));
        totpField = new TextField();
        totpField.setPromptText(message("auth.totp.prompt"));
        totpField.setPrefWidth(300);
        totpLabel.setVisible(false);
        totpLabel.setManaged(false);
        totpField.setVisible(false);
        totpField.setManaged(false);

        grid.add(totpLabel, 0, 2);
        grid.add(totpField, 1, 2);

        var loginButton = new Button(message("auth.login"));
        loginButton.getStyleClass().add("primary-button");
        loginButton.setOnAction(e -> performLogin());

        statusLabel = new Label();

        var registerLink = new Hyperlink(message("auth.register"));
        registerLink.setOnAction(e -> openRegistrationPage());

        form.getChildren().addAll(title, instructions, grid, loginButton, statusLabel, registerLink);
        return form;
    }

    private VBox createConnectedInfo() {
        var info = new VBox(10);
        info.setVisible(false);
        info.setManaged(false);
        info.setAlignment(Pos.CENTER);
        info.setMaxWidth(450);

        connectedLabel = new Label(message("auth.connected"));
        connectedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green;");

        var disconnect = new Hyperlink(message("auth.disconnect"));
        disconnect.setOnAction(e -> disconnect());

        info.getChildren().addAll(connectedLabel, disconnect);
        return info;
    }

    private void performLogin() {
        var url = Configuration.getInstance().getSpectroSolHubUrl();
        var username = usernameField.getText().trim();
        var password = passwordField.getText();
        var totp = totpField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText(message("auth.validation.empty"));
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        statusLabel.setText(message("auth.logging.in"));
        statusLabel.setStyle("-fx-text-fill: gray;");

        BackgroundOperations.async(() -> {
            try {
                var tokenName = "JSolEx " + System.getProperty("os.name", "Desktop");
                var totpCode = totp.isEmpty() ? null : totp;
                var token = SpectroSolHubClient.login(url, username, password, tokenName, totpCode);

                Configuration.getInstance().setSpectroSolHubToken(token);

                var client = new SpectroSolHubClient(url, token);
                try {
                    quotaResponse = client.fetchQuota();
                } catch (SpectroSolHubException ignored) {
                }

                Platform.runLater(this::showConnected);
            } catch (SpectroSolHubException ex) {
                Platform.runLater(() -> {
                    if (ex.isTotpRequired()) {
                        showTotpField();
                        statusLabel.setText(message("auth.totp.required"));
                        statusLabel.setStyle("-fx-text-fill: orange;");
                    } else if (ex.statusCode() == 401) {
                        statusLabel.setText(message("auth.validation.failed"));
                        statusLabel.setStyle("-fx-text-fill: red;");
                    } else {
                        statusLabel.setText(message("auth.validation.error") + ": " + ex.getMessage());
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });
            }
        });
    }

    private void showTotpField() {
        var grid = (GridPane) loginForm.getChildren().get(1);
        for (var node : grid.getChildren()) {
            if (node == totpField || (GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) == 2)) {
                node.setVisible(true);
                node.setManaged(true);
            }
        }
        totpField.requestFocus();
    }

    private void showConnectedView() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        connectedInfo.setVisible(true);
        connectedInfo.setManaged(true);
    }

    private void showConnected() {
        boolean wasAuthenticated = authenticated;
        authenticated = true;
        showConnectedView();
        if (!wasAuthenticated) {
            onAuthenticationChanged.accept(true);
        }
    }

    private void disconnect() {
        Configuration.getInstance().clearSpectroSolHubToken();
        authenticated = false;
        onAuthenticationChanged.accept(false);
        connectedInfo.setVisible(false);
        connectedInfo.setManaged(false);
        loginForm.setVisible(true);
        loginForm.setManaged(true);
    }

    private void openRegistrationPage() {
        var url = Configuration.getInstance().getSpectroSolHubUrl();
        BackgroundOperations.async(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url + "/register"));
            } catch (Exception ex) {
                LOGGER.error("Failed to open registration page", ex);
            }
        });
    }

    @Override
    public void load() {
        if (authenticated) {
            showConnectedView();
            return;
        }
        var config = Configuration.getInstance();
        var tokenOpt = config.getSpectroSolHubToken();
        if (tokenOpt.isPresent()) {
            connectedLabel.setText(message("auth.verifying"));
            connectedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
            loginForm.setVisible(false);
            loginForm.setManaged(false);
            connectedInfo.setVisible(true);
            connectedInfo.setManaged(true);

            BackgroundOperations.async(() -> {
                try {
                    var url = config.getSpectroSolHubUrl();
                    var client = new SpectroSolHubClient(url, tokenOpt.get());
                    quotaResponse = client.fetchQuota();
                    Platform.runLater(() -> {
                        connectedLabel.setText(message("auth.connected"));
                        connectedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green;");
                        showConnected();
                    });
                } catch (SpectroSolHubException ex) {
                    LOGGER.warn("Stored token is invalid", ex);
                    Platform.runLater(() -> {
                        config.clearSpectroSolHubToken();
                        connectedLabel.setText(message("auth.connected"));
                        connectedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green;");
                        connectedInfo.setVisible(false);
                        connectedInfo.setManaged(false);
                        loginForm.setVisible(true);
                        loginForm.setManaged(true);
                        statusLabel.setText(message("auth.token.expired"));
                        statusLabel.setStyle("-fx-text-fill: orange;");
                    });
                }
            });
        }
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean validate() {
        return authenticated;
    }

    boolean isAuthenticated() {
        return authenticated;
    }

    QuotaResponse getQuotaResponse() {
        return quotaResponse;
    }
}
