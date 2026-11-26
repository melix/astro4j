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

import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns text with **bold** markers into a text flow.
 */
public final class FormattedText {
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private FormattedText() {

    }

    /**
     * Parses text with **bold** markers.
     *
     * @param text the text to parse
     * @param plain creates the nodes for the plain parts
     * @param bold creates the nodes for the bold parts
     * @return the text flow
     */
    public static TextFlow parse(String text, Function<String, Text> plain, Function<String, Text> bold) {
        List<Node> nodes = new ArrayList<>();
        var content = text.replace("\\n", "\n");
        var matcher = BOLD_PATTERN.matcher(content);
        var lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                nodes.add(plain.apply(content.substring(lastEnd, matcher.start())));
            }
            nodes.add(bold.apply(matcher.group(1)));
            lastEnd = matcher.end();
        }
        if (lastEnd < content.length()) {
            nodes.add(plain.apply(content.substring(lastEnd)));
        }
        return new TextFlow(nodes.toArray(new Node[0]));
    }
}
