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

/**
 * A dialog for displaying script expression errors with detailed stack traces.
 * Provides both a summary view and expandable details for debugging script issues.
 */
public class ScriptErrorDialog {
    private static final String BUNDLE = "script-error";

    private final List<InvalidExpression> invalidExpressions;
    private final String title;
    private final String header;

    /**
     * Creates a dialog with default localized title and header.
     *
     * @param invalidExpressions the list of invalid expressions to display
     */
    public ScriptErrorDialog(List<InvalidExpression> invalidExpressions) {
        this(invalidExpressions,
             I18N.string(ScriptErrorDialog.class, BUNDLE, "title"),
             I18N.string(ScriptErrorDialog.class, BUNDLE,
                 "header." + (invalidExpressions.size() == 1 ? "single" : "many")));
    }

    /**
     * Creates a dialog with custom title and header.
     *
     * @param invalidExpressions the list of invalid expressions to display
     * @param title the dialog title
     * @param header the dialog header text
     */
    public ScriptErrorDialog(List<InvalidExpression> invalidExpressions, String title, String header) {
        this.invalidExpressions = invalidExpressions;
        this.title = title;
        this.header = header;
    }

    /**
     * Displays the error dialog and waits for the user to close it.
     * The dialog shows a summary of errors and expandable details with stack traces.
     */
    public void showAndWait() {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setResizable(true);

        var content = createContent();
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefSize(700, 500);
        alert.getDialogPane().setMinSize(500, 300);

        // Add copy button alongside OK
        var copyButtonType = new ButtonType(I18N.string(ScriptErrorDialog.class, BUNDLE, "copy"));
        alert.getButtonTypes().addFirst(copyButtonType);

        alert.showAndWait().ifPresent(result -> {
            if (result == copyButtonType) {
                copyToClipboard();
            }
        });
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
        summaryArea.setPrefRowCount(Math.min(8, invalidExpressions.size() * 3));
        VBox.setVgrow(summaryArea, Priority.SOMETIMES);
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
        scrollPane.setPrefViewportHeight(250);

        detailsPane.setContent(scrollPane);
        VBox.setVgrow(detailsPane, Priority.ALWAYS);

        vbox.getChildren().add(detailsPane);

        return vbox;
    }

    private String formatSimpleErrors() {
        return invalidExpressions.stream()
                .map(expr -> {
                    var expression = truncateExpression(expr.expression());
                    var errorMessage = expr.error().getMessage();
                    return String.format("• %s%n  Expression: %s%n  Error: %s",
                            expr.label(),
                            expression,
                            errorMessage != null ? errorMessage : "Unknown error");
                })
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    private String truncateExpression(String expression) {
        if (expression == null) {
            return "";
        }
        // Take first line only, and truncate if too long
        var firstLine = expression.lines().findFirst().orElse(expression);
        if (firstLine.length() > 60) {
            firstLine = firstLine.substring(0, 57) + "...";
        }
        if (!firstLine.equals(expression)) {
            firstLine = firstLine + " ...";
        }
        return firstLine;
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

    /**
     * Convenience method to display errors in a dialog.
     * Does nothing if the list is null or empty.
     *
     * @param invalidExpressions the list of invalid expressions to display, or null
     */
    public static void showErrors(List<InvalidExpression> invalidExpressions) {
        if (invalidExpressions != null && !invalidExpressions.isEmpty()) {
            new ScriptErrorDialog(invalidExpressions).showAndWait();
        }
    }
}
