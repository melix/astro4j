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

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A reusable UI component that displays inline guidance messages with different severity levels.
 */
public class InlineGuidance extends VBox {

    /** Types of guidance that can be displayed. */
    public enum GuidanceType {
        /** A helpful tip. */
        TIP,
        /** A recommended action. */
        RECOMMENDATION,
        /** A warning message. */
        WARNING,
        /** A required action. */
        REQUIREMENT
    }

    private final Button actionButton;

    /**
     * Creates a new inline guidance component.
     * @param title the title of the guidance
     * @param description the description text
     * @param type the type of guidance
     */
    public InlineGuidance(String title, String description, GuidanceType type) {
        getStyleClass().addAll("inline-guidance", "guidance-" + type.name().toLowerCase());
        setSpacing(8);
        setPadding(new Insets(4, 8, 8, 8));
        
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(getIconForType(type));
        iconLabel.getStyleClass().add("guidance-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("guidance-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dismissButton = new Button("×");
        dismissButton.getStyleClass().add("guidance-dismiss");
        dismissButton.setOnAction(e -> dismiss());
        
        header.getChildren().addAll(iconLabel, titleLabel, spacer, dismissButton);
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("guidance-description");
        descriptionLabel.setWrapText(true);
        actionButton = new Button();
        actionButton.getStyleClass().add("guidance-action");
        actionButton.setVisible(false);
        actionButton.setManaged(false);
        
        getChildren().addAll(header, descriptionLabel, actionButton);
    }

    /**
     * Creates a tip guidance.
     * @param title the title
     * @param description the description
     * @return a new tip guidance instance
     */
    public static InlineGuidance tip(String title, String description) {
        return new InlineGuidance(title, description, GuidanceType.TIP);
    }

    /**
     * Creates a recommendation guidance.
     * @param title the title
     * @param description the description
     * @return a new recommendation guidance instance
     */
    public static InlineGuidance recommendation(String title, String description) {
        return new InlineGuidance(title, description, GuidanceType.RECOMMENDATION);
    }

    /**
     * Creates a warning guidance.
     * @param title the title
     * @param description the description
     * @return a new warning guidance instance
     */
    public static InlineGuidance warning(String title, String description) {
        return new InlineGuidance(title, description, GuidanceType.WARNING);
    }

    /**
     * Creates a requirement guidance.
     * @param title the title
     * @param description the description
     * @return a new requirement guidance instance
     */
    public static InlineGuidance requirement(String title, String description) {
        return new InlineGuidance(title, description, GuidanceType.REQUIREMENT);
    }

    /**
     * Adds an action button to the guidance.
     * @param actionText the button text
     * @param action the action to perform when clicked
     * @return this guidance instance
     */
    public InlineGuidance withAction(String actionText, Runnable action) {
        actionButton.setText(actionText);
        actionButton.setOnAction(e -> {
            action.run();
            dismiss();
        });
        actionButton.setVisible(true);
        actionButton.setManaged(true);
        return this;
    }

    /** Dismisses the guidance with a fade-out animation. */
    public void dismiss() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), this);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
        });
        fadeOut.play();
    }

    /** Shows the guidance with a fade-in animation. */
    public void show() {
        setVisible(true);
        setManaged(true);
        setOpacity(0.0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), this);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private String getIconForType(GuidanceType type) {
        return switch (type) {
            case TIP -> "💡";
            case RECOMMENDATION -> "🎯";
            case WARNING -> "⚠️";
            case REQUIREMENT -> "❗";
        };
    }
}