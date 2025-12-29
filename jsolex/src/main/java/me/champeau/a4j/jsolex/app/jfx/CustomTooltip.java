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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom tooltip implementation with better styling and reliable show/hide behavior.
 */
public class CustomTooltip {
    private final Popup popup;
    private final VBox content;
    private final PauseTransition showTimer;
    private final PauseTransition hideTimer;
    private final List<Node> triggers = new ArrayList<>();
    private Timeline refreshTimer;
    private boolean showing = false;
    private Runnable onBeforeShow;

    /**
     * Creates a custom tooltip with the specified text.
     * @param text the tooltip text
     */
    public CustomTooltip(String text) {
        this(new Label(text));
    }

    /**
     * Creates a custom tooltip with the specified content node.
     * @param contentNode the tooltip content
     */
    public CustomTooltip(Node contentNode) {
        popup = new Popup();
        popup.setAutoHide(false);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);

        content = new VBox(contentNode);
        content.getStyleClass().add("custom-tooltip");

        popup.getContent().add(content);

        showTimer = new PauseTransition(Duration.millis(300));
        showTimer.setOnFinished(_ -> doShow());

        hideTimer = new PauseTransition(Duration.millis(100));
        hideTimer.setOnFinished(_ -> doHide());
    }

    /**
     * Updates the tooltip content with a new node.
     * @param newContent the new content to display
     */
    public void setContent(Node newContent) {
        content.getChildren().clear();
        content.getChildren().add(newContent);
    }

    /**
     * Sets a callback to be invoked just before the tooltip is shown.
     * This can be used to update the content dynamically.
     * @param callback the callback to invoke before showing
     */
    public void setOnBeforeShow(Runnable callback) {
        this.onBeforeShow = callback;
    }

    /**
     * Enables auto-refresh of the tooltip content while it's visible.
     * @param intervalMs the refresh interval in milliseconds
     */
    public void enableAutoRefresh(int intervalMs) {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        refreshTimer = new Timeline(new KeyFrame(Duration.millis(intervalMs), _ -> {
            if (showing && onBeforeShow != null) {
                onBeforeShow.run();
            }
        }));
        refreshTimer.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Attaches this tooltip to the specified node as the primary anchor.
     * The anchor is used for positioning the tooltip.
     * @param node the node to attach to
     */
    public void attachTo(Node node) {
        detachFromCurrent();
        addTriggerNode(node);

        content.setOnMouseEntered(_ -> hideTimer.stop());

        content.setOnMouseExited(_ -> {
            if (showing) {
                hideTimer.playFromStart();
            }
        });
    }

    /**
     * Adds a trigger node that will show/hide this tooltip on hover.
     * The first trigger added becomes the anchor for positioning.
     * @param node the trigger node
     */
    public void addTriggerNode(Node node) {
        if (node != null && !triggers.contains(node)) {
            triggers.add(node);
            setupNodeHandlers(node);
        }
    }

    private void setupNodeHandlers(Node node) {
        node.setOnMouseEntered(_ -> {
            hideTimer.stop();
            if (!showing) {
                showTimer.playFromStart();
            }
        });

        node.setOnMouseExited(event -> {
            showTimer.stop();
            if (showing && !isMouseOverTooltip(event)) {
                hideTimer.playFromStart();
            }
        });

        node.setOnMousePressed(_ -> {
            showTimer.stop();
            hideTimer.stop();
            if (!showing) {
                doShow();
            } else {
                doHide();
            }
        });
    }

    /**
     * Detaches this tooltip from all attached nodes.
     */
    public void detachFromCurrent() {
        showTimer.stop();
        hideTimer.stop();
        doHide();

        for (var trigger : triggers) {
            trigger.setOnMouseEntered(null);
            trigger.setOnMouseExited(null);
            trigger.setOnMousePressed(null);
        }
        triggers.clear();
    }
    
    private boolean isMouseOverTooltip(MouseEvent event) {
        if (!showing || popup == null || !popup.isShowing()) {
            return false;
        }
        
        try {
            var screenBounds = popup.getContent().getFirst().localToScreen(popup.getContent().getFirst().getBoundsInLocal());
            if (screenBounds != null) {
                return screenBounds.contains(event.getScreenX(), event.getScreenY());
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
    
    private Node getAnchor() {
        return triggers.isEmpty() ? null : triggers.getFirst();
    }

    private void doShow() {
        if (showing || getAnchor() == null) {
            return;
        }

        if (onBeforeShow != null) {
            onBeforeShow.run();
        }

        if (Platform.isFxApplicationThread()) {
            showOnFxThread();
        } else {
            Platform.runLater(this::showOnFxThread);
        }
    }

    private void showOnFxThread() {
        var anchor = getAnchor();
        if (showing || anchor == null) {
            return;
        }

        try {
            var scene = anchor.getScene();
            var window = scene != null ? scene.getWindow() : null;
            if (window == null || !window.isShowing()) {
                return;
            }

            var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
            if (bounds == null || bounds.isEmpty()) {
                return;
            }

            var screen = Screen.getPrimary().getVisualBounds();

            var x = bounds.getCenterX() - 75;
            var y = bounds.getMinY() - 35;

            if (x < screen.getMinX()) {
                x = screen.getMinX() + 5;
            } else if (x + 150 > screen.getMaxX()) {
                x = screen.getMaxX() - 155;
            }

            if (y < screen.getMinY()) {
                y = bounds.getMaxY() + 5;
            }

            if (y + 50 > screen.getMaxY()) {
                y = screen.getMaxY() - 55;
            }

            popup.show(window, x, y);
            showing = true;
            if (refreshTimer != null) {
                refreshTimer.playFromStart();
            }
        } catch (Exception e) {
            try {
                var scene = anchor.getScene();
                if (scene != null && scene.getWindow() != null) {
                    popup.show(scene.getWindow(), 0, 0);
                    showing = true;
                    if (refreshTimer != null) {
                        refreshTimer.playFromStart();
                    }
                }
            } catch (Exception fallbackException) {
                // ignore
            }
        }
    }
    
    private void doHide() {
        if (!showing) {
            return;
        }
        
        if (Platform.isFxApplicationThread()) {
            hideOnFxThread();
        } else {
            Platform.runLater(this::hideOnFxThread);
        }
    }
    
    private void hideOnFxThread() {
        if (!showing) {
            return;
        }

        try {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            if (popup != null && popup.isShowing()) {
                popup.hide();
            }
        } catch (Exception e) {
            // ignore
        } finally {
            showing = false;
        }
    }
}