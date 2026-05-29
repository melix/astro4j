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

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;

import java.util.function.BiConsumer;

class DraggableTextOverlayLayer extends StackPane {
    private final ZoomableImageView host;
    private final BiConsumer<Integer, Integer> onPositionChange;
    private final TextFlow textFlow;
    private String family = ImageDraw.DEFAULT_SIGNATURE_FONT;
    private int fontSize = ImageDraw.DEFAULT_SIGNATURE_SIZE;
    private Color color = Color.web("#cccccc");
    private int imageX;
    private int imageY;
    private double pressSceneX;
    private double pressSceneY;
    private double pressLayoutX;
    private double pressLayoutY;

    DraggableTextOverlayLayer(ZoomableImageView host, BiConsumer<Integer, Integer> onPositionChange) {
        this.host = host;
        this.onPositionChange = onPositionChange;
        this.textFlow = new TextFlow();
        this.textFlow.setMouseTransparent(true);
        getChildren().add(textFlow);
        setCursor(Cursor.MOVE);
        setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
        setOnMousePressed(this::handlePressed);
        setOnMouseDragged(this::handleDragged);
        setOnMouseReleased(e -> onPositionChange.accept(imageX, imageY));
    }

    void update(String content, String fontFamily, Integer fontSizeBoxed, String hexColor) {
        this.family = fontFamily != null && !fontFamily.isBlank() ? fontFamily : ImageDraw.DEFAULT_SIGNATURE_FONT;
        this.fontSize = fontSizeBoxed != null && fontSizeBoxed > 0 ? fontSizeBoxed : ImageDraw.DEFAULT_SIGNATURE_SIZE;
        this.color = parseColor(hexColor);
        rebuildTextFlow(content == null ? "" : content);
        applyLayout();
    }

    void place(int imageX, int imageY) {
        this.imageX = imageX;
        this.imageY = imageY;
        applyLayout();
    }

    int getImageX() {
        return imageX;
    }

    int getImageY() {
        return imageY;
    }

    void applyLayout() {
        double zoom = scale();
        for (var node : textFlow.getChildren()) {
            if (node instanceof Text t) {
                boolean bold = FontWeight.BOLD.equals(getWeight(t));
                t.setFont(Font.font(family, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize * zoom));
            }
        }
        setLayoutX(imageX * zoom);
        setLayoutY(imageY * zoom);
    }

    private void rebuildTextFlow(String content) {
        textFlow.getChildren().clear();
        var lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            boolean bold = raw.startsWith("<b>");
            String body = bold ? raw.substring(3) : raw;
            var text = new Text(body);
            text.setFill(color);
            text.setFont(Font.font(family, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
            tagWeight(text, bold ? FontWeight.BOLD : FontWeight.NORMAL);
            textFlow.getChildren().add(text);
            if (i < lines.length - 1) {
                var br = new Text("\n");
                textFlow.getChildren().add(br);
            }
        }
    }

    private double scale() {
        double zoom = host.getZoom();
        return zoom > 0 ? zoom : 1.0;
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.length() != 6) {
            return Color.web("#cccccc");
        }
        try {
            return Color.web("#" + hex);
        } catch (IllegalArgumentException ex) {
            return Color.web("#cccccc");
        }
    }

    private static final Object WEIGHT_KEY = new Object();

    private static void tagWeight(Text t, FontWeight weight) {
        t.getProperties().put(WEIGHT_KEY, weight);
    }

    private static FontWeight getWeight(Text t) {
        var v = t.getProperties().get(WEIGHT_KEY);
        return v instanceof FontWeight w ? w : FontWeight.NORMAL;
    }

    private void handlePressed(MouseEvent e) {
        pressSceneX = e.getSceneX();
        pressSceneY = e.getSceneY();
        pressLayoutX = getLayoutX();
        pressLayoutY = getLayoutY();
        e.consume();
    }

    private void handleDragged(MouseEvent e) {
        double zoom = scale();
        double newLayoutX = pressLayoutX + (e.getSceneX() - pressSceneX);
        double newLayoutY = pressLayoutY + (e.getSceneY() - pressSceneY);
        imageX = (int) Math.round(newLayoutX / zoom);
        imageY = (int) Math.round(newLayoutY / zoom);
        setLayoutX(newLayoutX);
        setLayoutY(newLayoutY);
        e.consume();
    }
}
