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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Overlay for 3D viewers showing a help button.
 * When clicked, displays explanatory information about the view.
 * This overlay should not be included in exports.
 */
public class Viewer3DHelpOverlay extends StackPane {

    private static final String HELP_ICON = "?";
    private static final double BUTTON_SIZE = 32;
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private final String i18nBundle;
    private final StackPane helpPopup;

    public Viewer3DHelpOverlay(String i18nBundle) {
        this.i18nBundle = i18nBundle;
        setPickOnBounds(false);
        setMouseTransparent(false);

        var helpButton = createHelpButton();
        StackPane.setAlignment(helpButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(helpButton, new Insets(15));

        helpPopup = createHelpPopup();
        helpPopup.setVisible(false);
        StackPane.setAlignment(helpPopup, Pos.CENTER);

        getChildren().addAll(helpPopup, helpButton);
    }

    private Button createHelpButton() {
        var button = new Button(HELP_ICON);
        button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setFont(Font.font("System", FontWeight.BOLD, 16));
        button.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 16; " +
                "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e ->
                button.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.8); " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 16; " +
                        "-fx-cursor: hand;"
                )
        );
        button.setOnMouseExited(e ->
                button.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 16; " +
                        "-fx-cursor: hand;"
                )
        );

        button.setOnAction(e -> helpPopup.setVisible(true));

        return button;
    }

    private StackPane createHelpPopup() {
        var titleLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, i18nBundle, "help.text");
        var textFlow = parseFormattedText(helpText);
        textFlow.setMaxWidth(560);

        var okButton = new Button("OK");
        okButton.setFont(Font.font("System", FontWeight.BOLD, 13));
        okButton.setMinWidth(80);
        okButton.setStyle(
                "-fx-background-color: rgba(80, 80, 100, 0.9); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        );
        okButton.setOnMouseEntered(e -> okButton.setStyle(
                "-fx-background-color: rgba(100, 100, 130, 0.95); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        ));
        okButton.setOnMouseExited(e -> okButton.setStyle(
                "-fx-background-color: rgba(80, 80, 100, 0.9); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        ));
        okButton.setOnAction(e -> helpPopup.setVisible(false));

        var buttonBox = new HBox(okButton);
        buttonBox.setAlignment(Pos.CENTER);

        var spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        var content = new VBox(15, titleLabel, textFlow, spacer, buttonBox);
        content.setPadding(new Insets(20));
        content.setMaxWidth(600);
        content.setStyle(
                "-fx-background-color: rgba(30, 30, 40, 0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: rgba(255, 255, 255, 0.2); " +
                "-fx-border-radius: 12; " +
                "-fx-border-width: 1;"
        );

        var overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                helpPopup.setVisible(false);
            }
        });

        return overlay;
    }

    private TextFlow parseFormattedText(String text) {
        List<Node> nodes = new ArrayList<>();
        text = text.replace("\\n", "\n");
        var matcher = BOLD_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                nodes.add(createText(text.substring(lastEnd, matcher.start())));
            }
            nodes.add(createBoldText(matcher.group(1)));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            nodes.add(createText(text.substring(lastEnd)));
        }

        var textFlow = new TextFlow(nodes.toArray(new Node[0]));
        textFlow.setLineSpacing(4);
        return textFlow;
    }

    private Text createText(String content) {
        var text = new Text(content);
        text.setFill(Color.rgb(220, 220, 220));
        text.setFont(Font.font("System", FontWeight.NORMAL, 13));
        return text;
    }

    private Text createBoldText(String content) {
        var text = new Text(content);
        text.setFill(Color.rgb(255, 200, 100));
        text.setFont(Font.font("System", FontWeight.BOLD, 13));
        return text;
    }
}
