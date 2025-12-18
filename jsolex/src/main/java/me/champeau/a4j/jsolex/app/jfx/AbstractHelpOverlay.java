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

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract base class for help overlays with an animated help button.
 * Provides a "?" button that shows an attention animation the first time
 * a user sees it (per viewer, per release).
 */
public abstract class AbstractHelpOverlay extends StackPane {

    private static final String HELP_ICON = "?";
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    protected static final double BUTTON_SIZE = 32;
    protected static final String WINDOW_BUTTON_STYLE = "-fx-background-color: transparent; " +
            "-fx-text-fill: rgba(255, 255, 255, 0.6); " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;";
    protected static final String WINDOW_BUTTON_HOVER_STYLE = "-fx-background-color: rgba(255, 255, 255, 0.1); " +
            "-fx-background-radius: 14; " +
            "-fx-text-fill: white; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;";

    private final String viewerId;
    private StackPane helpPopup;
    private Timeline attentionAnimation;

    protected AbstractHelpOverlay(String viewerId) {
        this.viewerId = viewerId;
        setPickOnBounds(false);
        setMouseTransparent(false);

        var helpButtonContainer = createHelpButtonWithAnimation();
        StackPane.setAlignment(helpButtonContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(helpButtonContainer, new Insets(15));

        getChildren().add(helpButtonContainer);
    }

    private StackPane getOrCreateHelpPopup() {
        if (helpPopup == null) {
            helpPopup = createHelpPopup();
            helpPopup.setVisible(false);
            StackPane.setAlignment(helpPopup, Pos.CENTER);
            getChildren().addFirst(helpPopup);
        }
        return helpPopup;
    }

    /**
     * Creates the help popup content. Subclasses must implement this.
     * @return the popup StackPane
     */
    protected abstract StackPane createHelpPopup();

    /**
     * Called when the popup is shown. Subclasses can override to start animations.
     */
    protected void onPopupShown() {
    }

    /**
     * Called when the popup is hidden. Subclasses can override to stop animations.
     */
    protected void onPopupHidden() {
    }

    private StackPane createHelpButtonWithAnimation() {
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

        // Create ripple circle for wave effect (behind the button)
        var ripple = new Circle(BUTTON_SIZE / 2);
        ripple.setFill(Color.TRANSPARENT);
        ripple.setStroke(Color.rgb(255, 60, 60));
        ripple.setStrokeWidth(3);
        ripple.setOpacity(0);

        // Container to hold button and ripple
        var container = new StackPane(ripple, button);
        container.setMaxSize(BUTTON_SIZE * 2, BUTTON_SIZE * 2);

        // Check if animation should be shown
        var config = Configuration.getInstance();
        if (!config.isHelpAnimationSeen(viewerId)) {
            attentionAnimation = createAttentionAnimation(button, ripple);
            attentionAnimation.play();
        }

        button.setOnAction(e -> {
            // Stop attention animation and mark as seen
            if (attentionAnimation != null) {
                attentionAnimation.stop();
                attentionAnimation = null;
                // Reset button scale
                button.setScaleX(1.0);
                button.setScaleY(1.0);
                ripple.setOpacity(0);
            }
            config.setHelpAnimationSeen(viewerId);
            showPopup();
        });

        return container;
    }

    private Timeline createAttentionAnimation(Button button, Circle ripple) {
        var animation = new Timeline();

        // Animation: ripple appears first, then button bumps, then pause
        double ripplePeak = 0.15;
        double bumpPeak = 0.25;
        double bumpEnd = 0.5;
        double pauseDuration = 0.5;
        double cycleDuration = bumpEnd + pauseDuration;

        animation.getKeyFrames().addAll(
                // Start of cycle - everything at rest
                new KeyFrame(Duration.ZERO,
                        new KeyValue(button.scaleXProperty(), 1.0),
                        new KeyValue(button.scaleYProperty(), 1.0),
                        new KeyValue(ripple.opacityProperty(), 0),
                        new KeyValue(ripple.scaleXProperty(), 1.0),
                        new KeyValue(ripple.scaleYProperty(), 1.0)
                ),
                // Ripple appears first
                new KeyFrame(Duration.seconds(ripplePeak),
                        new KeyValue(ripple.opacityProperty(), 0.8, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleXProperty(), 1.3, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleYProperty(), 1.3, Interpolator.EASE_OUT)
                ),
                // Button bump peaks
                new KeyFrame(Duration.seconds(bumpPeak),
                        new KeyValue(button.scaleXProperty(), 1.2, Interpolator.EASE_OUT),
                        new KeyValue(button.scaleYProperty(), 1.2, Interpolator.EASE_OUT),
                        new KeyValue(ripple.opacityProperty(), 0.5),
                        new KeyValue(ripple.scaleXProperty(), 1.8),
                        new KeyValue(ripple.scaleYProperty(), 1.8)
                ),
                // Everything returns to rest
                new KeyFrame(Duration.seconds(bumpEnd),
                        new KeyValue(button.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(button.scaleYProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(ripple.opacityProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleXProperty(), 2.2, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleYProperty(), 2.2, Interpolator.EASE_OUT)
                ),
                // Pause - hold position until cycle ends
                new KeyFrame(Duration.seconds(cycleDuration),
                        new KeyValue(button.scaleXProperty(), 1.0),
                        new KeyValue(button.scaleYProperty(), 1.0),
                        new KeyValue(ripple.opacityProperty(), 0),
                        new KeyValue(ripple.scaleXProperty(), 1.0),
                        new KeyValue(ripple.scaleYProperty(), 1.0)
                )
        );

        animation.setCycleCount(Timeline.INDEFINITE);

        return animation;
    }

    protected void showPopup() {
        getOrCreateHelpPopup().setVisible(true);
        onPopupShown();
    }

    protected void hidePopup() {
        getOrCreateHelpPopup().setVisible(false);
        onPopupHidden();
    }

    /**
     * Creates a styled content pane with a close button.
     * @param scrollPane the scroll pane containing the content
     * @param normalWidth the normal width
     * @param normalHeight the normal height
     * @return a configured StackPane with window controls
     */
    protected StackPane createContentPane(ScrollPane scrollPane, double normalWidth, double normalHeight) {
        var closeButton = new Button("\u2715");
        closeButton.setFont(Font.font("System", FontWeight.NORMAL, 14));
        closeButton.setMinSize(28, 28);
        closeButton.setMaxSize(28, 28);
        closeButton.setStyle(WINDOW_BUTTON_STYLE);
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(WINDOW_BUTTON_STYLE));
        closeButton.setOnAction(e -> hidePopup());

        // Spacer to push button to the right
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var buttonBar = new HBox(4, spacer, closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8));

        // Use BorderPane for proper layout: button at top, scrollPane in center
        var borderPane = new BorderPane();
        borderPane.setTop(buttonBar);
        borderPane.setCenter(scrollPane);

        // Wrapper with styling
        var content = new StackPane(borderPane);
        content.setMaxWidth(normalWidth);
        content.setMaxHeight(normalHeight);
        content.setStyle(
                "-fx-background-color: rgba(30, 30, 40, 0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: rgba(255, 255, 255, 0.2); " +
                "-fx-border-radius: 12; " +
                "-fx-border-width: 1;"
        );

        return content;
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, java.util.function.Supplier<Node> createDiagramCopy) {
        return createScalableDiagram(diagram, createDiagramCopy, null);
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @param onMaximized optional callback invoked after the maximized view is shown (e.g., to start animations)
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, java.util.function.Supplier<Node> createDiagramCopy, Runnable onMaximized) {
        return createScalableDiagram(diagram, createDiagramCopy, onMaximized, null);
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @param onMaximized optional callback invoked after the maximized view is shown (e.g., to start animations)
     * @param onClosed optional callback invoked when the maximized view is closed (e.g., to stop animations)
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, java.util.function.Supplier<Node> createDiagramCopy, Runnable onMaximized, Runnable onClosed) {
        var maximizeButton = new Button("\u26F6");
        maximizeButton.setFont(Font.font("System", FontWeight.NORMAL, 12));
        maximizeButton.setMinSize(24, 24);
        maximizeButton.setMaxSize(24, 24);
        maximizeButton.setStyle(WINDOW_BUTTON_STYLE);
        maximizeButton.setOnMouseEntered(e -> maximizeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        maximizeButton.setOnMouseExited(e -> maximizeButton.setStyle(WINDOW_BUTTON_STYLE));

        maximizeButton.setOnAction(e -> showMaximizedDiagram(createDiagramCopy.get(), onMaximized, onClosed));

        var container = new StackPane(diagram, maximizeButton);
        StackPane.setAlignment(maximizeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(maximizeButton, new Insets(4));

        return container;
    }

    private void showMaximizedDiagram(Node diagram, Runnable onShown, Runnable onClosed) {
        var closeButton = new Button("\u2715");
        closeButton.setFont(Font.font("System", FontWeight.BOLD, 16));
        closeButton.setMinSize(36, 36);
        closeButton.setMaxSize(36, 36);
        closeButton.setStyle(WINDOW_BUTTON_STYLE);
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(WINDOW_BUTTON_STYLE));

        var diagramContainer = new StackPane(diagram, closeButton);
        StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(closeButton, new Insets(20));

        var overlay = new StackPane(diagramContainer);
        overlay.setStyle("-fx-background-color: rgba(20, 20, 30, 0.98);");

        // Compute scale factor based on available space after layout
        overlay.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds.getWidth() > 0 && newBounds.getHeight() > 0) {
                var diagramBounds = diagram.getBoundsInLocal();
                double padding = 120;
                double availableWidth = newBounds.getWidth() - padding;
                double availableHeight = newBounds.getHeight() - padding;
                double scaleX = availableWidth / diagramBounds.getWidth();
                double scaleY = availableHeight / diagramBounds.getHeight();
                double scale = Math.min(scaleX, scaleY);
                diagram.setScaleX(scale);
                diagram.setScaleY(scale);
            }
        });

        if (onShown != null) {
            onShown.run();
        }

        Runnable closeOverlay = () -> {
            getChildren().remove(overlay);
            if (onClosed != null) {
                onClosed.run();
            }
        };

        closeButton.setOnAction(e -> closeOverlay.run());
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeOverlay.run();
            }
        });

        getChildren().add(overlay);
    }

    /**
     * Wraps the content pane in a semi-transparent overlay that closes on click outside.
     * @param content the content pane to wrap
     * @return the overlay StackPane
     */
    protected StackPane wrapInOverlay(StackPane content) {
        var overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hidePopup();
            }
        });
        return overlay;
    }

    /**
     * Creates centered diagram text.
     */
    protected Text createDiagramText(String content, double x, double y, double size, boolean bold, Color color) {
        var text = new Text(content);
        text.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        text.setFill(color);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y);
        return text;
    }

    /**
     * Parses text with **bold** markdown markers into a TextFlow.
     */
    protected TextFlow parseFormattedText(String text) {
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
