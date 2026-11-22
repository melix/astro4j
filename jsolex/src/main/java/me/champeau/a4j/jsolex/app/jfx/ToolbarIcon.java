/*
 * Copyright 2026-2026 the original author or authors.
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

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.List;
import java.util.Locale;

/**
 * Coloured icons of the image viewer toolbar. Each icon is made of a few vector
 * shapes drawn on a 16x16 grid. Colours are set by the stylesheet, so that icons
 * can be greyed out when their button is disabled.
 */
public enum ToolbarIcon {
    ALIGN(
            stroke("M3 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", IconColor.BLUE, 1.6),
            stroke("M8 1v4M8 11v4M1 8h4M11 8h4", IconColor.BLUE, 1.6),
            fill("M6.6 8a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0", IconColor.RED)
    ),
    ZOOM_OUT(
            filledStroke("M1.8 6.5a4.7 4.7 0 1 0 9.4 0a4.7 4.7 0 1 0 -9.4 0", IconColor.LIGHT_BLUE, IconColor.BLUE, 1.6),
            stroke("M10 10l4.3 4.3", IconColor.GREY, 2.4),
            stroke("M4.3 6.5h4.4", IconColor.BLUE, 1.6)
    ),
    ZOOM_IN(
            filledStroke("M1.8 6.5a4.7 4.7 0 1 0 9.4 0a4.7 4.7 0 1 0 -9.4 0", IconColor.LIGHT_BLUE, IconColor.BLUE, 1.6),
            stroke("M10 10l4.3 4.3", IconColor.GREY, 2.4),
            stroke("M4.3 6.5h4.4M6.5 4.3v4.4", IconColor.BLUE, 1.6)
    ),
    FIT_IMAGE(
            filledStroke("M1 3h14v10h-14z", IconColor.LIGHT_GREY, IconColor.GREY, 1.2),
            stroke("M3.5 8h9M3.5 8l2-2M3.5 8l2 2M12.5 8l-2-2M12.5 8l-2 2", IconColor.BLUE, 1.5)
    ),
    FIT_DISK(
            filledStroke("M5 4.5h6v7h-6z", IconColor.LIGHT_BLUE, IconColor.GREY, 1.2),
            stroke("M0.8 8h3M3.8 8l-1.6-1.6M3.8 8l-1.6 1.6M15.2 8h-3M12.2 8l1.6-1.6M12.2 8l1.6 1.6", IconColor.BLUE, 1.5)
    ),
    ROTATE_LEFT(
            stroke("M3.6 6.2A5 5 0 1 1 4 11.6", IconColor.GREEN, 1.8),
            fill("M1.6 2.6L3.8 7.2 7.9 4.7z", IconColor.GREEN)
    ),
    ROTATE_RIGHT(Transform.MIRROR, ROTATE_LEFT),
    VERTICAL_FLIP(
            fill("M3 1.5h10L8 6.5z", IconColor.TEAL),
            filledStroke("M3 14.5h10L8 9.5z", IconColor.PALE_TEAL, IconColor.TEAL, 1.2),
            stroke("M1 8h2.5M5 8h2.5M9 8h2.5M13 8h2", IconColor.GREY, 1)
    ),
    HORIZONTAL_FLIP(Transform.QUARTER_TURN, VERTICAL_FLIP),
    APPLY(
            stroke("M2.8 8.6l3.6 3.6L13.4 4.4", IconColor.GREEN, 2.2)
    ),
    CROP(
            new Part("M4.5 1v10.5H15M1 4.5h10.5V15", null, IconColor.ORANGE, 1.9, StrokeLineCap.SQUARE)
    ),
    ANNOTATIONS(
            stroke("M2 9.5L8 13l6-3.5", IconColor.PURPLE, 1.5),
            filledStroke("M8 2.5L14 6 8 9.5 2 6z", IconColor.PALE_PURPLE, IconColor.PURPLE, 1.5)
    ),
    MEASURE(Transform.TILT,
            filledStroke("M0.5 5h15v6h-15z", IconColor.PALE_AMBER, IconColor.AMBER, 1.2),
            stroke("M3.5 5v2.4M6 5v3.4M8.5 5v2.4M11 5v3.4M13.5 5v2.4", IconColor.AMBER, 1)
    ),
    VIEW_3D(
            filledStroke("M1.5 8a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0", IconColor.PALE_INDIGO, IconColor.DARK_INDIGO, 1.3),
            stroke("M5.2 8a2.8 6.5 0 1 0 5.6 0a2.8 6.5 0 1 0 -5.6 0", IconColor.INDIGO, 1),
            stroke("M1.5 8h13M2.5 4.6h11M2.5 11.4h11", IconColor.INDIGO, 1)
    ),
    SAVE(
            fill("M2 2H12L14 4V14H2z", IconColor.BLUE),
            fill("M4.5 2h6v4h-6z", IconColor.WHITE),
            fill("M8.3 2.7h1.4v2.6h-1.4z", IconColor.BLUE),
            fill("M4 9h8v5h-8z", IconColor.PALE_BLUE)
    ),
    FOLDER(
            fill("M1.5 3.5h5l1.6 1.6h6.4v8.4h-13z", IconColor.YELLOW),
            fill("M1.5 6.5h13v7h-13z", IconColor.PALE_YELLOW)
    ),
    ADJUSTMENTS(
            stroke("M2 4h12M2 8h12M2 12h12", IconColor.MID_GREY, 1.4),
            fill("M3 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", IconColor.BLUE),
            fill("M9 8a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", IconColor.ORANGE),
            fill("M5 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", IconColor.GREEN)
    ),
    ADD(
            fill("M1.5 8a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0", IconColor.GREEN),
            stroke("M8 4.8v6.4M4.8 8h6.4", IconColor.WHITE, 1.8)
    ),
    RESET(
            stroke("M12.6 5.2A5.3 5.3 0 1 0 13.3 9.6", IconColor.GREY, 1.8),
            fill("M14.5 1.8L13.6 6.9 9.2 4.6z", IconColor.GREY)
    );

    private static final double SIZE = 16;

    private final Transform transform;
    private final List<Part> parts;

    ToolbarIcon(Part... parts) {
        this(Transform.NONE, parts);
    }

    ToolbarIcon(Transform transform, Part... parts) {
        this.transform = transform;
        this.parts = List.of(parts);
    }

    ToolbarIcon(Transform transform, ToolbarIcon source) {
        this(transform, source.parts.toArray(Part[]::new));
    }

    /**
     * Creates a new node showing this icon.
     *
     * @return the icon node
     */
    public Node createNode() {
        var pane = new Pane();
        pane.getStyleClass().add("toolbar-icon");
        pane.setMinSize(SIZE, SIZE);
        pane.setPrefSize(SIZE, SIZE);
        pane.setMaxSize(SIZE, SIZE);
        pane.setMouseTransparent(true);
        for (var part : parts) {
            pane.getChildren().add(part.createShape());
        }
        switch (transform) {
            case MIRROR -> pane.setScaleX(-1);
            case QUARTER_TURN -> pane.setRotate(90);
            case TILT -> pane.setRotate(-35);
            case NONE -> {
            }
        }
        return pane;
    }

    private static Part fill(String path, IconColor color) {
        return new Part(path, color, null, 0, StrokeLineCap.ROUND);
    }

    private static Part stroke(String path, IconColor color, double width) {
        return new Part(path, null, color, width, StrokeLineCap.ROUND);
    }

    private static Part filledStroke(String path, IconColor fill, IconColor stroke, double width) {
        return new Part(path, fill, stroke, width, StrokeLineCap.ROUND);
    }

    private enum Transform {
        NONE,
        MIRROR,
        QUARTER_TURN,
        TILT
    }

    private enum IconColor {
        BLUE(false),
        LIGHT_BLUE(true),
        PALE_BLUE(true),
        GREY(false),
        LIGHT_GREY(true),
        MID_GREY(false),
        RED(false),
        GREEN(false),
        TEAL(false),
        PALE_TEAL(true),
        ORANGE(false),
        PURPLE(false),
        PALE_PURPLE(true),
        AMBER(false),
        PALE_AMBER(true),
        PALE_INDIGO(true),
        INDIGO(false),
        DARK_INDIGO(false),
        YELLOW(false),
        PALE_YELLOW(true),
        WHITE(true);

        private final boolean pale;

        IconColor(boolean pale) {
            this.pale = pale;
        }

        private String styleSuffix() {
            return name().toLowerCase(Locale.US).replace('_', '-');
        }
    }

    private record Part(String path, IconColor fill, IconColor stroke, double strokeWidth, StrokeLineCap lineCap) {
        private SVGPath createShape() {
            var shape = new SVGPath();
            shape.setContent(path);
            var styles = shape.getStyleClass();
            if (fill == null) {
                shape.setFill(null);
            } else {
                styles.addAll("icon-fill", "fill-" + fill.styleSuffix());
                if (fill.pale) {
                    styles.add("icon-fill-pale");
                }
            }
            if (stroke != null) {
                styles.addAll("icon-stroke", "stroke-" + stroke.styleSuffix());
                if (stroke.pale) {
                    styles.add("icon-stroke-pale");
                }
                shape.setStrokeWidth(strokeWidth);
                shape.setStrokeLineCap(lineCap);
                shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
            }
            return shape;
        }
    }
}
