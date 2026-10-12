/*
 * Copyright 2026 the original author or authors.
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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A row of dots showing which page or phase of a help is displayed. Clicking a dot selects it.
 */
public final class PageIndicator {
    private static final Color CURRENT = Color.WHITE;
    private static final Color OTHER = Color.gray(0.4);
    private static final Color HOVERED = Color.gray(0.6);

    private final HBox box = new HBox(8);
    private final List<Circle> dots = new ArrayList<>();
    private int current = -1;

    public PageIndicator(int count, IntConsumer onSelect) {
        box.setAlignment(Pos.CENTER);
        for (var i = 0; i < count; i++) {
            var dot = new Circle(6, OTHER);
            dot.setCursor(Cursor.HAND);
            var index = i;
            dot.setOnMouseClicked(e -> onSelect.accept(index));
            dot.setOnMouseEntered(e -> refresh(dot, index));
            dot.setOnMouseExited(e -> refresh(dot, index));
            dots.add(dot);
            box.getChildren().add(dot);
        }
    }

    public Node node() {
        return box;
    }

    /**
     * Shows the given page or phase as the current one.
     */
    public void select(int index) {
        if (index == current) {
            return;
        }
        current = index;
        for (var i = 0; i < dots.size(); i++) {
            refresh(dots.get(i), i);
        }
    }

    private void refresh(Circle dot, int index) {
        dot.setFill(index == current ? CURRENT : dot.isHover() ? HOVERED : OTHER);
    }
}
