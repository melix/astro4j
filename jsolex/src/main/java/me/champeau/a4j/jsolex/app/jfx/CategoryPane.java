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

import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CategoryPane extends VBox {

    private final List<Hyperlink> links = new ArrayList<>();
    private Hyperlink selected = null;

    public CategoryPane(String title) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("category-title");
        getChildren().add(titleLabel);
        getStyleClass().add("category-pane");
    }

    Hyperlink addImage(String title, PixelShift pixelShift, Consumer<? super Hyperlink> onClick) {
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
        links.add(link);
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
        getChildren().add(insertPoint, box);
        return link;
    }

    public Hyperlink addVideo(String title, Consumer<? super Hyperlink> onClick) {
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
        getChildren().add(link);
        return link;
    }

    private void select(Hyperlink link) {
        clearSelection();
        link.getStyleClass().add("category-link-selected");
        selected = link;
    }

    public void clearSelection() {
        if (selected != null) {
            selected.getStyleClass().remove("category-link-selected");
            selected = null;
        }
    }

    public void selectFirst() {
        links.stream()
            .findFirst()
            .ifPresent(Hyperlink::fire);
    }

}
