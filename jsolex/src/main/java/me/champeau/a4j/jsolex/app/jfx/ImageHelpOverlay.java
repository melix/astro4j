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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.champeau.a4j.jsolex.app.jfx.help.ImageHelpContentProvider;
import me.champeau.a4j.jsolex.app.jfx.help.ImageHelpContentRegistry;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

/**
 * Help overlay for image viewers that displays a text description and
 * optionally rich animated content for specific image kinds.
 */
public class ImageHelpOverlay extends AbstractHelpOverlay {

    private static final double POPUP_WIDTH = 500;
    private static final double POPUP_MAX_HEIGHT = 600;

    private final String title;
    private final String description;
    private final GeneratedImageKind kind;
    private ImageHelpContentProvider contentProvider;
    private ImageHelpContentProvider maximizedProvider;

    public ImageHelpOverlay(String title, String description, GeneratedImageKind kind) {
        super("image-" + (kind != null ? kind.name().toLowerCase() : "generic"));
        this.title = title;
        this.description = description;
        this.kind = kind;
    }

    @Override
    protected StackPane createHelpPopup() {
        contentProvider = kind != null
                ? ImageHelpContentRegistry.getProvider(kind).orElse(null)
                : null;

        var hasRichContent = contentProvider != null;

        var titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.rgb(255, 200, 100));
        titleLabel.setWrapText(true);

        var descriptionFlow = parseFormattedText(description);
        descriptionFlow.setPadding(new Insets(10, 0, 0, 0));

        var contentBox = new VBox(10);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.getChildren().addAll(titleLabel, descriptionFlow);

        if (hasRichContent) {
            var diagramContent = contentProvider.createContent();
            // Wrap with scalable diagram for fullscreen support
            var scalableDiagram = createScalableDiagram(
                    diagramContent,
                    () -> {
                        // Create fresh provider and content for fullscreen view
                        var fullscreenProvider = ImageHelpContentRegistry.getProvider(kind).orElse(null);
                        if (fullscreenProvider != null) {
                            maximizedProvider = fullscreenProvider;
                            return fullscreenProvider.createContent();
                        }
                        return contentProvider.createContent();
                    },
                    () -> {
                        // Start animation when maximized view is shown
                        if (maximizedProvider != null) {
                            maximizedProvider.onShown();
                        }
                    },
                    () -> {
                        // Stop animation when maximized view is closed
                        if (maximizedProvider != null) {
                            maximizedProvider.onHidden();
                            maximizedProvider = null;
                        }
                    }
            );
            scalableDiagram.setPadding(new Insets(20, 0, 0, 0));
            contentBox.getChildren().add(scalableDiagram);
        }

        var scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        var content = createFlexibleContentPane(scrollPane, POPUP_WIDTH, POPUP_MAX_HEIGHT);
        return wrapInOverlay(content);
    }

    @Override
    protected void onPopupShown() {
        if (contentProvider != null) {
            contentProvider.onShown();
        }
    }

    @Override
    protected void onPopupHidden() {
        if (contentProvider != null) {
            contentProvider.onHidden();
        }
    }

    /**
     * Shows the help popup. Can be called externally to trigger the popup
     * without using the built-in help button.
     */
    public void show() {
        showPopup();
    }
}
