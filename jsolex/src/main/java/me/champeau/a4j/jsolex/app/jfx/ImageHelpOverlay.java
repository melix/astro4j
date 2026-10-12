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
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.champeau.a4j.jsolex.app.jfx.help.ImageHelpContentProvider;
import me.champeau.a4j.jsolex.app.jfx.help.ImageHelpContentRegistry;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.util.List;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Help overlay for image viewers that displays a text description and
 * optionally rich animated content for specific image kinds.
 */
public class ImageHelpOverlay extends AbstractHelpOverlay {

    private static final double POPUP_WIDTH = 500;
    private static final double POPUP_MAX_HEIGHT = 600;
    private static final double PAGED_WIDTH = 780;
    private static final double PAGED_HEIGHT = 720;
    private static final double PAGED_MIN_HEIGHT = 420;
    private static final double PAGED_DIAGRAM_SCALE = 1.35;

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
    public void dispose() {
        super.dispose();
        if (contentProvider != null) {
            contentProvider.onHidden();
        }
        if (maximizedProvider != null) {
            maximizedProvider.onHidden();
        }
    }

    @Override
    protected StackPane createHelpPopup() {
        contentProvider = kind != null
                ? ImageHelpContentRegistry.getProvider(kind).orElse(null)
                : null;

        var hasRichContent = contentProvider != null;
        if (hasRichContent && contentProvider.introduction() != null) {
            return createPagedPopup();
        }

        var titleLabel = createTitle(title);

        var contentBox = new VBox(10);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.getChildren().add(titleLabel);

        if (hasRichContent && kind == GeneratedImageKind.RECONSTRUCTION) {
            var usageHeader = new Label(message("recon.view.usage.title"));
            usageHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
            usageHeader.setTextFill(Color.rgb(220, 220, 220));
            usageHeader.setPadding(new Insets(10, 0, 0, 0));
            contentBox.getChildren().add(usageHeader);
        }

        var descriptionFlow = parseFormattedText(description);
        descriptionFlow.setPadding(new Insets(5, 0, 0, 0));
        contentBox.getChildren().add(descriptionFlow);

        if (hasRichContent) {
            if (kind == GeneratedImageKind.RECONSTRUCTION) {
                var explanationHeader = new Label(message("recon.view.explanation.title"));
                explanationHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
                explanationHeader.setTextFill(Color.rgb(220, 220, 220));
                explanationHeader.setPadding(new Insets(10, 0, 0, 0));
                contentBox.getChildren().add(explanationHeader);
            }
            var scalableDiagram = createRichContent();
            scalableDiagram.setPadding(new Insets(20, 0, 0, 0));
            contentBox.getChildren().add(scalableDiagram);
        }

        var scrollPane = createScrollPane(contentBox);
        scrollPane.setFitToHeight(true);

        var content = createFlexibleContentPane(scrollPane, POPUP_WIDTH, POPUP_MAX_HEIGHT);
        return wrapInOverlay(content);
    }

    private StackPane createRichContent() {
        return createScalableDiagram(
                contentProvider.createContent(),
                () -> {
                    var fullscreenProvider = ImageHelpContentRegistry.getProvider(kind).orElse(null);
                    if (fullscreenProvider != null) {
                        maximizedProvider = fullscreenProvider;
                        return fullscreenProvider.createContent();
                    }
                    return contentProvider.createContent();
                },
                () -> {
                    if (maximizedProvider != null) {
                        maximizedProvider.onShown();
                    }
                },
                () -> {
                    if (maximizedProvider != null) {
                        maximizedProvider.onHidden();
                        maximizedProvider = null;
                    }
                }
        );
    }

    /**
     * Creates a popup with three pages: the description of the image, the introduction of the
     * content provider, and its animated diagram.
     */
    private StackPane createPagedPopup() {
        var diagram = createRichContent();
        var pages = List.<Node>of(
                createTextPage(title, description),
                createTextPage(message("image.help.page.principle"), contentProvider.introduction()),
                createPage(message("image.help.page.animation"), enlarge(diagram))
        );
        var pager = new Pager(pages);
        var ok = createNavigationButton("OK");
        ok.setOnAction(e -> hidePopup());
        var navigation = new HBox(20, pager.previous, pager.indicator.node(), pager.next, ok);
        navigation.setAlignment(Pos.CENTER);
        navigation.setPadding(new Insets(15, 20, 20, 20));

        var contentBox = new VBox(pager.holder);
        contentBox.setPadding(new Insets(0, 20, 0, 20));
        var scrollPane = createScrollPane(contentBox);
        var content = createContentPane(scrollPane, navigation, PAGED_WIDTH, PAGED_HEIGHT);
        return wrapInOverlay(content);
    }

    private static Label createTitle(String text) {
        var label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 16));
        label.setTextFill(Color.rgb(255, 200, 100));
        label.setWrapText(true);
        return label;
    }

    private static ScrollPane createScrollPane(Node content) {
        var scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private static Node enlarge(Node diagram) {
        diagram.setScaleX(PAGED_DIAGRAM_SCALE);
        diagram.setScaleY(PAGED_DIAGRAM_SCALE);
        var centered = new StackPane(new Group(diagram));
        centered.setAlignment(Pos.CENTER);
        return centered;
    }

    private VBox createTextPage(String pageTitle, String text) {
        var flow = parseFormattedText(text);
        flow.setMaxWidth(PAGED_WIDTH - 80);
        return createPage(pageTitle, flow);
    }

    private static VBox createPage(String pageTitle, Node content) {
        var titleLabel = createTitle(pageTitle);
        var page = new VBox(12, titleLabel, content);
        page.setAlignment(Pos.TOP_LEFT);
        return page;
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

    private static final class Pager {
        private final List<Node> pages;
        private final StackPane holder = new StackPane();
        private final Button previous = createNavigationButton(message("image.help.previous"));
        private final Button next = createNavigationButton(message("image.help.next"));
        private final PageIndicator indicator;
        private int current;

        private Pager(List<Node> pages) {
            this.pages = pages;
            holder.setAlignment(Pos.TOP_LEFT);
            holder.setMinHeight(PAGED_MIN_HEIGHT);
            indicator = new PageIndicator(pages.size(), this::show);
            previous.setOnAction(e -> show(current - 1));
            next.setOnAction(e -> show(current + 1));
            show(0);
        }

        private void show(int index) {
            current = index;
            holder.getChildren().setAll(pages.get(index));
            indicator.select(index);
            previous.setDisable(index == 0);
            next.setDisable(index == pages.size() - 1);
        }
    }
}
