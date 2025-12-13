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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Navigation breadcrumb component. */
public class BreadcrumbNavigation extends HBox {

    /** Breadcrumb item representing a navigation step. */
    public static class BreadcrumbItem {
        private final String title;
        private final String id;
        private final Runnable action;

        /**
         * Creates a breadcrumb item.
         * @param id the item ID
         * @param title the item title
         * @param action the action to run when clicked
         */
        public BreadcrumbItem(String id, String title, Runnable action) {
            this.id = id;
            this.title = title;
            this.action = action;
        }

        /**
         * Gets the title of this breadcrumb item.
         * @return the item title
         */
        public String getTitle() { return title; }

        /**
         * Gets the unique identifier of this breadcrumb item.
         * @return the item ID
         */
        public String getId() { return id; }

        /**
         * Gets the action to execute when this breadcrumb item is clicked.
         * @return the item action
         */
        public Runnable getAction() { return action; }
    }

    private final List<BreadcrumbItem> items = new ArrayList<>();

    /** Creates a new breadcrumb navigation component. */
    public BreadcrumbNavigation() {
        getStyleClass().add("breadcrumb-nav");
        setSpacing(4);
        setPadding(new Insets(8, 16, 8, 16));
        setAlignment(Pos.CENTER_LEFT);
    }
    
    /**
     * Sets the breadcrumb path.
     * @param path the list of breadcrumb items
     */
    public void setPath(List<BreadcrumbItem> path) {
        this.items.clear();
        this.items.addAll(path);
        rebuildBreadcrumb();
    }

    /**
     * Adds a breadcrumb item.
     * @param item the item to add
     */
    public void addItem(BreadcrumbItem item) {
        items.add(item);
        rebuildBreadcrumb();
    }

    /**
     * Adds a breadcrumb item.
     * @param id the item ID
     * @param title the item title
     * @param action the action to run when clicked
     */
    public void addItem(String id, String title, Runnable action) {
        addItem(new BreadcrumbItem(id, title, action));
    }

    /** Removes the last breadcrumb item. */
    public void removeLastItem() {
        if (!items.isEmpty()) {
            items.remove(items.size() - 1);
            rebuildBreadcrumb();
        }
    }

    /** Clears all breadcrumb items. */
    public void clear() {
        items.clear();
        getChildren().clear();
    }

    private void rebuildBreadcrumb() {
        getChildren().clear();
        
        for (int i = 0; i < items.size(); i++) {
            BreadcrumbItem item = items.get(i);
            boolean isLast = i == items.size() - 1;
            
            if (isLast) {
                Label currentLabel = new Label(item.getTitle());
                currentLabel.getStyleClass().add("breadcrumb-current");
                getChildren().add(currentLabel);
            } else {
                Button breadcrumbButton = new Button(item.getTitle());
                breadcrumbButton.getStyleClass().add("breadcrumb-link");
                breadcrumbButton.setOnAction(e -> {
                    if (item.getAction() != null) {
                        item.getAction().run();
                    }
                });
                getChildren().add(breadcrumbButton);
                
                Label separator = new Label("›");
                separator.getStyleClass().add("breadcrumb-separator");
                getChildren().add(separator);
            }
        }
    }
    
    /**
     * Returns the list of breadcrumb items.
     * @return the list of items
     */
    public List<BreadcrumbItem> getItems() {
        return new ArrayList<>(items);
    }
}