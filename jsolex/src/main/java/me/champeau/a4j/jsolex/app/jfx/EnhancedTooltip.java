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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class EnhancedTooltip extends Tooltip {
    
    public enum TooltipType {
        INFO, WARNING, SUCCESS, ERROR, HELP
    }
    
    private final TooltipType type;
    private final String title;
    private final String description;
    private final String icon;
    
    public EnhancedTooltip(String title, String description, TooltipType type) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.icon = getIconForType(type);
        
        setupTooltip();
    }
    
    public static EnhancedTooltip info(String title, String description) {
        return new EnhancedTooltip(title, description, TooltipType.INFO);
    }
    
    public static EnhancedTooltip warning(String title, String description) {
        return new EnhancedTooltip(title, description, TooltipType.WARNING);
    }
    
    public static EnhancedTooltip help(String title, String description) {
        return new EnhancedTooltip(title, description, TooltipType.HELP);
    }
    
    public static EnhancedTooltip success(String description) {
        return new EnhancedTooltip("Success", description, TooltipType.SUCCESS);
    }
    
    public static EnhancedTooltip error(String description) {
        return new EnhancedTooltip("Error", description, TooltipType.ERROR);
    }
    
    private void setupTooltip() {
        getStyleClass().addAll("enhanced-tooltip", "tooltip-" + type.name().toLowerCase());
        setShowDelay(Duration.millis(300));
        setHideDelay(Duration.millis(100));
        setAutoFix(true);
        
        VBox content = new VBox(6);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(8, 12, 8, 12));
        
        if (title != null && !title.isEmpty()) {
            HBox titleBox = new HBox(6);
            titleBox.setAlignment(Pos.CENTER_LEFT);
            
            Label iconLabel = new Label(icon);
            iconLabel.getStyleClass().add("tooltip-icon");
            
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("tooltip-title");
            
            titleBox.getChildren().addAll(iconLabel, titleLabel);
            content.getChildren().add(titleBox);
        }
        if (description != null && !description.isEmpty()) {
            Label descLabel = new Label(description);
            descLabel.getStyleClass().add("tooltip-description");
            descLabel.setWrapText(true);
            descLabel.setMaxWidth(300);
            content.getChildren().add(descLabel);
        }
        
        setGraphic(content);
    }
    
    private String getIconForType(TooltipType type) {
        return switch (type) {
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case SUCCESS -> "✅";
            case ERROR -> "❌";
            case HELP -> "❓";
        };
    }
    
    public static void attachTo(Node node, String title, String description, TooltipType type) {
        EnhancedTooltip tooltip = new EnhancedTooltip(title, description, type);
        Tooltip.install(node, tooltip);
    }
    
    public static void attachInfoTo(Node node, String title, String description) {
        attachTo(node, title, description, TooltipType.INFO);
    }
    
    public static void attachHelpTo(Node node, String title, String description) {
        attachTo(node, title, description, TooltipType.HELP);
    }
    
    public static void attachWarningTo(Node node, String title, String description) {
        attachTo(node, title, description, TooltipType.WARNING);
    }
}