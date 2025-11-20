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

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.processing.expr.InvalidExpression;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

public class ScriptErrorDialog {
    private static final String BUNDLE = "script-error";

    private final List<InvalidExpression> invalidExpressions;
    private final String title;
    private final String header;

    public ScriptErrorDialog(List<InvalidExpression> invalidExpressions) {
        this(invalidExpressions,
             I18N.string(ScriptErrorDialog.class, BUNDLE, "title"),
             I18N.string(ScriptErrorDialog.class, BUNDLE,
                 "header." + (invalidExpressions.size() == 1 ? "single" : "many")));
    }

    public ScriptErrorDialog(List<InvalidExpression> invalidExpressions, String title, String header) {
        this.invalidExpressions = invalidExpressions;
        this.title = title;
        this.header = header;
    }

    public void showAndWait() {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setResizable(true);

        var content = createContent();
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefSize(600, 400);

        var copyButton = new Button(I18N.string(ScriptErrorDialog.class, BUNDLE, "copy"));
        copyButton.setOnAction(e -> copyToClipboard());
        alert.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        alert.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        var buttonBar = alert.getDialogPane().lookup(".button-bar");
        if (buttonBar != null && buttonBar.getParent() instanceof HBox hbox) {
            hbox.getChildren().addFirst(copyButton);
        }

        alert.showAndWait();
    }

    private VBox createContent() {
        var vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        var summaryLabel = new Label(I18N.string(ScriptErrorDialog.class, BUNDLE, "summary"));
        summaryLabel.setStyle("-fx-font-weight: bold;");
        vbox.getChildren().add(summaryLabel);

        var summaryArea = new TextArea(formatSimpleErrors());
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefRowCount(Math.min(5, invalidExpressions.size()));
        summaryArea.setMaxHeight(150);
        vbox.getChildren().add(summaryArea);

        var detailsPane = new TitledPane();
        detailsPane.setText(I18N.string(ScriptErrorDialog.class, BUNDLE, "details"));
        detailsPane.setExpanded(false);

        var detailsArea = new TextArea(formatDetailedErrors());
        detailsArea.setEditable(false);
        detailsArea.setWrapText(false);
        detailsArea.setStyle("-fx-font-family: 'Courier New', monospace;");

        var scrollPane = new ScrollPane(detailsArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPrefViewportHeight(200);

        detailsPane.setContent(scrollPane);
        VBox.setVgrow(detailsPane, Priority.ALWAYS);

        vbox.getChildren().add(detailsPane);

        return vbox;
    }

    private String formatSimpleErrors() {
        return invalidExpressions.stream()
                .map(expr -> String.format("• %s (%s): %s",
                        expr.label(),
                        expr.expression(),
                        expr.error().getMessage()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatDetailedErrors() {
        var sb = new StringBuilder();
        for (int i = 0; i < invalidExpressions.size(); i++) {
            var expr = invalidExpressions.get(i);
            sb.append("=".repeat(80)).append(System.lineSeparator());
            sb.append(String.format("Error %d of %d%n", i + 1, invalidExpressions.size()));
            sb.append("=".repeat(80)).append(System.lineSeparator());
            sb.append(String.format("Expression: %s%n", expr.label()));
            sb.append(String.format("Code: %s%n", expr.expression()));
            sb.append("=".repeat(80)).append(System.lineSeparator());
            sb.append(System.lineSeparator());

            var sw = new StringWriter();
            expr.error().printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
            sb.append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void copyToClipboard() {
        var content = new ClipboardContent();
        var sb = new StringBuilder();
        sb.append(header).append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append(formatSimpleErrors());
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("=".repeat(80)).append(System.lineSeparator());
        sb.append("DETAILED STACK TRACES");
        sb.append(System.lineSeparator());
        sb.append("=".repeat(80)).append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append(formatDetailedErrors());

        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static void showErrors(List<InvalidExpression> invalidExpressions) {
        if (invalidExpressions != null && !invalidExpressions.isEmpty()) {
            new ScriptErrorDialog(invalidExpressions).showAndWait();
        }
    }
}
