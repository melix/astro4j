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

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.util.Duration;

/**
 * Custom tooltip implementation with better styling and reliable show/hide behavior.
 */
public class CustomTooltip {
    private final Popup popup;
    private final VBox content;
    private final PauseTransition showTimer;
    private final PauseTransition hideTimer;
    private boolean showing = false;
    
    public CustomTooltip(String text) {
        popup = new Popup();
        popup.setAutoHide(false);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);

        var textLabel = new Label(text);
        content = new VBox(textLabel);
        content.getStyleClass().add("custom-tooltip");
        
        popup.getContent().add(content);
        
        showTimer = new PauseTransition(Duration.millis(500));
        showTimer.setOnFinished(_ -> doShow());
        
        hideTimer = new PauseTransition(Duration.millis(200));
        hideTimer.setOnFinished(_ -> doHide());
    }
    
    private Node currentOwner;
    
    public void attachTo(Node node) {
        this.currentOwner = node;
        
        node.setOnMouseEntered(_ -> {
            hideTimer.stop();
            if (!showing) {
                showTimer.playFromStart();
            }
        });
        
        node.setOnMouseExited(_ -> {
            showTimer.stop();
            if (showing) {
                hideTimer.playFromStart();
            }
        });
        
        node.setOnMousePressed(_ -> {
            showTimer.stop();
            doHide();
        });
        
        content.setOnMouseEntered(_ -> hideTimer.stop());
        content.setOnMouseExited(_ -> {
            if (showing) {
                hideTimer.playFromStart();
            }
        });
    }
    
    private void doShow() {
        if (showing || currentOwner == null) {
            return;
        }
        
        try {
            Platform.runLater(() -> {
                if (showing || currentOwner == null) {
                    return;
                }
                
                try {
                    var scene = currentOwner.getScene();
                    var window = scene != null ? scene.getWindow() : null;
                    if (window == null || !window.isShowing()) {
                        return;
                    }
                    
                    var bounds = currentOwner.localToScreen(currentOwner.getBoundsInLocal());
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
                } catch (Exception e) {
                    try {
                        popup.show(currentOwner.getScene().getWindow(), 0, 0);
                        showing = true;
                    } catch (Exception fallbackException) {
                    }
                }
            });
        } catch (Exception e) {
        }
    }
    
    private void doHide() {
        if (!showing) {
            return;
        }
        
        try {
            popup.hide();
        } catch (Exception e) {
        } finally {
            showing = false;
        }
    }
}