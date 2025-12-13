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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/** Pane displaying a category of images with links. */
public class CategoryPane extends VBox {

    private final List<Hyperlink> safeLinks = Collections.synchronizedList(new ArrayList<>());
    private final ObservableList<Hyperlink> links = FXCollections.observableArrayList();

    private Hyperlink selected = null;
    private final Consumer<? super CategoryPane> whenEmpty;

    /**
     * Creates a category pane.
     * @param title the category title
     * @param whenEmpty callback when the category becomes empty
     */
    public CategoryPane(String title, Consumer<? super CategoryPane> whenEmpty) {
        this.whenEmpty = whenEmpty;
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("category-title");
        getChildren().add(titleLabel);
        getStyleClass().add("category-pane");
    }

    Hyperlink addImage(String title, PixelShift pixelShift, Consumer<? super Hyperlink> onClick, Consumer<? super Hyperlink> onClose) {
        var box = new HBox();
        box.getProperties().put(PixelShift.class, pixelShift);
        box.setAlignment(Pos.CENTER_LEFT);
        var link = new Hyperlink(title);
        link.setOnAction(e -> {
            if (selected == link) {
                return;
            }
            onClick.accept(link);
            select(link);
        });
        link.getStyleClass().add("category-link");
        safeLinks.add(link);
        box.getChildren().add(link);
        var tooltip = title;
        if (pixelShift != null && pixelShift.pixelShift() != 0) {
            var label = pixelShift.pixelShift() > 0 ? "+" : "";
            var shiftValue = pixelShift.pixelShift();
            if (shiftValue == Math.floor(shiftValue)) {
                label = String.format("%s%d", label, (int) shiftValue);
            } else {
                label = String.format("%s%.2f", label, shiftValue);
            }
            var shift = new Label(label);
            shift.getStyleClass().add("category-shift");
            box.getChildren().add(shift);
            tooltip += String.format(" (%s)", label);
        }
        link.setTooltip(new Tooltip(tooltip));
        int insertPoint = 1;
        for (int i = 1; i < getChildren().size(); i++) {
            var child = getChildren().get(i);
            var shift = (PixelShift) child.getProperties().get(PixelShift.class);
            if (shift == null || pixelShift == null || shift.pixelShift() > pixelShift.pixelShift()) {
                break;
            }
            insertPoint++;
        }
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        box.getChildren().add(spacer);
        var close = createCloseLink(box, link, onClose);
        box.getChildren().add(close);
        getChildren().add(insertPoint, box);
        Platform.runLater(() -> links.setAll(safeLinks));
        return link;
    }

    private Hyperlink createCloseLink(HBox box, Hyperlink link, Consumer<? super Hyperlink> onClose) {
        var close = new Hyperlink("x");
        close.getStyleClass().add("category-close");
        close.setAlignment(Pos.CENTER_RIGHT);
        close.setOnAction(e -> {
            getChildren().remove(box);
            var idx = Math.max(0, links.indexOf(link) - 1);
            links.remove(link);
            onClose.accept(link);
            if (links.isEmpty()) {
                whenEmpty.accept(this);
            }
            if (selected == link && !links.isEmpty()) {
                links.get(idx).fire();
            }
        });
        close.setTooltip(new Tooltip(message("close.image")));
        return close;
    }

    /**
     * Adds a video link.
     * @param title the video title
     * @param onClick callback when clicked
     * @param onClose callback when closed
     * @return the created hyperlink
     */
    public Hyperlink addVideo(String title, Consumer<? super Hyperlink> onClick, Consumer<? super Hyperlink> onClose) {
        var link = new Hyperlink(title);
        link.setOnAction(e -> {
            if (selected == link) {
                return;
            }
            onClick.accept(link);
            select(link);
        });
        link.getStyleClass().add("category-link");
        links.add(link);
        var box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var close = createCloseLink(box, link, onClose);
        box.getChildren().addAll(link, spacer, close);
        getChildren().add(box);
        return link;
    }

    private void select(Hyperlink link) {
        clearSelection();
        link.getStyleClass().add("category-link-selected");
        selected = link;
    }

    /** Clears the current selection. */
    public void clearSelection() {
        if (selected != null) {
            selected.getStyleClass().remove("category-link-selected");
            selected = null;
        }
    }

    /** Selects the first item. */
    public void selectFirst() {
        links.stream()
            .findFirst()
            .ifPresent(Hyperlink::fire);
    }

}
