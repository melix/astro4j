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

/**
 * A free-text annotation placed at an arbitrary position on the image. It shares the same
 * rendering and editing code as the signature; the only difference is that the user chooses
 * where to position it and that there can be an arbitrary number of them.
 */
public record TextAreaOverlay(
        String text,
        String color,
        String fontFamily,
        Integer fontSize,
        String fontWeight,
        Integer x,
        Integer y
) {
    public TextAreaOverlay withText(String v) {
        return new TextAreaOverlay(v, color, fontFamily, fontSize, fontWeight, x, y);
    }

    public TextAreaOverlay withColor(String v) {
        return new TextAreaOverlay(text, v, fontFamily, fontSize, fontWeight, x, y);
    }

    public TextAreaOverlay withFontFamily(String v) {
        return new TextAreaOverlay(text, color, v, fontSize, fontWeight, x, y);
    }

    public TextAreaOverlay withFontSize(Integer v) {
        return new TextAreaOverlay(text, color, fontFamily, v, fontWeight, x, y);
    }

    public TextAreaOverlay withFontWeight(String v) {
        return new TextAreaOverlay(text, color, fontFamily, fontSize, v, x, y);
    }

    public TextAreaOverlay withPosition(Integer x, Integer y) {
        return new TextAreaOverlay(text, color, fontFamily, fontSize, fontWeight, x, y);
    }
}
