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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A vertical sidebar component for navigation between different content panels.
 */
public class NavigationSidebar extends VBox {

    /**
     * Represents a navigation item with an icon, title, description and associated content.
     */
    public static class NavigationItem {
        private final String id;
        private final String title;
        private final String icon;
        private final Node content;
        private final String description;

        /**
         * Creates a new navigation item.
         *
         * @param id the unique identifier for this item
         * @param title the display title
         * @param icon the icon text
         * @param content the content node to display when selected
         * @param description the optional description text
         */
        public NavigationItem(String id, String title, String icon, Node content, String description) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.content = content;
            this.description = description;
        }

        /**
         * Returns the unique identifier.
         *
         * @return the id
         */
        public String getId() { return id; }

        /**
         * Returns the display title.
         *
         * @return the title
         */
        public String getTitle() { return title; }

        /**
         * Returns the icon text.
         *
         * @return the icon
         */
        public String getIcon() { return icon; }

        /**
         * Returns the content node.
         *
         * @return the content
         */
        public Node getContent() { return content; }

        /**
         * Returns the description text.
         *
         * @return the description
         */
        public String getDescription() { return description; }
    }
    
    private final List<NavigationItem> items = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();
    private final ObjectProperty<NavigationItem> selectedItem = new SimpleObjectProperty<>();
    private Consumer<NavigationItem> onItemSelected;

    /**
     * Creates a new navigation sidebar.
     */
    public NavigationSidebar() {
        getStyleClass().add("navigation-sidebar");
        setPrefWidth(300);
        setMinWidth(260);
        setMaxWidth(370);
        setSpacing(2);
        setPadding(new Insets(12));
        setFillWidth(true);
    }

    /**
     * Adds a navigation item to the sidebar.
     *
     * @param item the item to add
     */
    public void addNavigationItem(NavigationItem item) {
        items.add(item);

        var navButton = createNavigationButton(item);
        navButtons.add(navButton);
        getChildren().add(navButton);
        
        if (selectedItem.get() == null) {
            selectItem(item);
        }
    }
    
    private Button createNavigationButton(NavigationItem item) {
        var button = new Button();
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(40);
        button.setMaxHeight(120);
        button.setPrefHeight(Region.USE_COMPUTED_SIZE);
        button.setAlignment(Pos.CENTER_LEFT);
        
        var buttonContent = new HBox(8);
        buttonContent.setAlignment(Pos.CENTER_LEFT);
        buttonContent.setFillHeight(true);
        
        var iconLabel = new Label(item.getIcon());
        iconLabel.getStyleClass().add("nav-icon");
        iconLabel.setMinWidth(20);
        iconLabel.setPrefWidth(20);

        var textContainer = new VBox(2);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        textContainer.setFillWidth(true);
        textContainer.setMaxWidth(220);
        textContainer.setPrefWidth(220);
        
        var titleLabel = new Label(item.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(200);
        titleLabel.setPrefWidth(200);
        titleLabel.setMinWidth(200);
        titleLabel.getStyleClass().add("nav-title");
        
        textContainer.getChildren().add(titleLabel);
        
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            var descLabel = new Label(item.getDescription());
            descLabel.setWrapText(true);
            descLabel.setMaxWidth(200);
            descLabel.setPrefWidth(200);
            descLabel.getStyleClass().add("nav-description");
            textContainer.getChildren().add(descLabel);
        }
        
        buttonContent.getChildren().addAll(iconLabel, textContainer);
        HBox.setHgrow(textContainer, Priority.ALWAYS);
        
        button.setGraphic(buttonContent);
        
        button.setOnAction(e -> selectItem(item));
        
        return button;
    }

    /**
     * Selects the specified navigation item.
     *
     * @param item the item to select
     */
    public void selectItem(NavigationItem item) {
        var previousItem = selectedItem.get();
        selectedItem.set(item);
        
        for (var i = 0; i < items.size(); i++) {
            var button = navButtons.get(i);
            if (items.get(i) == item) {
                button.getStyleClass().add("nav-button-selected");
            } else {
                button.getStyleClass().removeAll("nav-button-selected");
            }
        }
        
        if (onItemSelected != null) {
            onItemSelected.accept(item);
        }
    }

    /**
     * Sets the callback to invoke when an item is selected.
     *
     * @param onItemSelected the callback consumer
     */
    public void setOnItemSelected(Consumer<NavigationItem> onItemSelected) {
        this.onItemSelected = onItemSelected;
    }

    /**
     * Returns the property for the selected item.
     *
     * @return the selected item property
     */
    public ObjectProperty<NavigationItem> selectedItemProperty() {
        return selectedItem;
    }

    /**
     * Returns the currently selected item.
     *
     * @return the selected item
     */
    public NavigationItem getSelectedItem() {
        return selectedItem.get();
    }

    /**
     * Returns all navigation items.
     *
     * @return the list of navigation items
     */
    public List<NavigationItem> getNavigationItems() {
        return items;
    }

    /**
     * Adds a visual separator to the sidebar.
     */
    public void addSeparator() {
        var separator = new Label();
        separator.getStyleClass().add("nav-separator");
        separator.setPrefHeight(1);
        separator.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(separator);
    }

    /**
     * Adds a section title to the sidebar.
     *
     * @param title the section title text
     */
    public void addTitle(String title) {
        var titleLabel = new Label(title);
        titleLabel.getStyleClass().add("nav-section-title");
        titleLabel.setPadding(new Insets(16, 0, 8, 0));
        getChildren().add(titleLabel);
    }

    /**
     * Creates a fade transition for content panel switching.
     *
     * @param content the content node to transition
     * @return the configured fade transition
     */
    public static FadeTransition createContentTransition(Node content) {
        var transition = new FadeTransition(Duration.millis(200), content);
        transition.setFromValue(0.0);
        transition.setToValue(1.0);
        return transition;
    }
}