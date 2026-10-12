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
package me.champeau.a4j.jsolex.app.jfx.help;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Colors and text helpers shared by the animated help diagrams.
 */
final class HelpDiagrams {

    static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    static final Color COLOR_BLUE = Color.rgb(80, 140, 255);
    static final Color COLOR_RED = Color.rgb(255, 100, 80);

    private HelpDiagrams() {
    }

    /**
     * Creates a text horizontally centered on the given x coordinate.
     */
    static Text createText(String content, double x, double y, double size, boolean bold) {
        var text = new Text();
        text.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        text.setFill(COLOR_TEXT);
        text.setTextAlignment(TextAlignment.CENTER);
        setCenteredText(text, content, x);
        text.setY(y);
        return text;
    }

    /**
     * Replaces the content of a text, keeping it horizontally centered on the given x coordinate.
     */
    static void setCenteredText(Text text, String content, double x) {
        text.setText(content);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
    }
}
