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
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class SimpleMarkdownViewer {
    private final String title;

    public SimpleMarkdownViewer(String title) {
        this.title = title;
    }

    public void render(Scene parent, String markdown, Runnable onDismiss) {
        var styledTextArea = new StyleClassedTextArea();
        styledTextArea.setWrapText(true);
        styledTextArea.setEditable(false);
        styledTextArea.setPadding(new Insets(4));

        var parser = Parser.builder().build();
        var document = parser.parse(markdown);
        var anchorPositions = new HashMap<String, Integer>();
        var linkPositions = new HashMap<Integer, String>();

        applyStyles(document, styledTextArea, anchorPositions, linkPositions);

        // Ensure the text area is scrolled to the top
        styledTextArea.moveTo(0);
        styledTextArea.requestFollowCaret();

        configureLinkClicks(styledTextArea, linkPositions, anchorPositions);

        var root = new BorderPane();
        root.setCenter(new VirtualizedScrollPane<>(styledTextArea));
        var closeButton = new Button(message("do.not.show.again"));
        closeButton.setOnAction(unused -> {
            var stage = (Stage) closeButton.getScene().getWindow();
            stage.close();
            onDismiss.run();
        });
        // add the button centered at the bottom
        var buttonBar = new HBox(closeButton);
        buttonBar.setPadding(new Insets(4));
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER);
        root.setBottom(buttonBar);

        Scene scene = new Scene(root, 800, 600);

        scene.getStylesheets().add(JSolEx.class.getResource("text-viewer.css").toExternalForm());
        var stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent.getWindow());
        stage.showAndWait();
    }

    private static void configureLinkClicks(StyleClassedTextArea styledTextArea, HashMap<Integer, String> linkPositions, HashMap<String, Integer> anchorPositions) {
        styledTextArea.setOnMouseClicked(event -> {
            var offset = styledTextArea.getCaretPosition();
            if (linkPositions.containsKey(offset)) {
                var link = linkPositions.get(offset);
                if (link.startsWith("#")) {
                    var anchor = buildAnchor(link.substring(1));
                    if (anchorPositions.containsKey(anchor)) {
                        int position = anchorPositions.get(anchor);
                        int paragraph = styledTextArea.offsetToPosition(position, TwoDimensional.Bias.Forward).getMajor();
                        // Ensure the target is at the top of the viewport
                        styledTextArea.showParagraphAtTop(paragraph);
                    }
                }
            }
        });
    }

    private static String buildAnchor(String text) {
        return text.toLowerCase().replace(" ", "-").replaceAll("[()]", "");
    }

    private void applyStyles(Node document, StyleClassedTextArea styledTextArea, Map<String, Integer> anchorPositions, Map<Integer, String> linkPositions) {
        StringBuilder markdownText = new StringBuilder();
        List<StyleRange> styleRanges = new ArrayList<>();

        document.accept(new AbstractVisitor() {
            int currentOffset = 0;

            @Override
            public void visit(Heading heading) {
                int start = markdownText.length();
                String text = collectTextContent(heading);
                markdownText.append(text).append("\n\n"); // Add extra newline for spacing
                int end = markdownText.length();
                styleRanges.add(new StyleRange(start, end - 1, "heading-" + heading.getLevel()));

                // Store the position of the anchor
                String anchor = buildAnchor(text);
                anchorPositions.put(anchor, currentOffset);
                currentOffset = markdownText.length();

                visitChildren(heading);
            }

            @Override
            public void visit(Paragraph paragraph) {
                paragraph.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Text textNode) {
                        maybeAppendSpace();
                        markdownText.append(textNode.getLiteral());
                    }

                    private void maybeAppendSpace() {
                        if (!markdownText.isEmpty()) {
                            var pChar = markdownText.charAt(markdownText.length() - 1);
                            if ((pChar != ' ') && (pChar != '\n')) {
                                markdownText.append(' ');
                            }
                        }
                    }

                    @Override
                    public void visit(StrongEmphasis strongEmphasis) {
                        maybeAppendSpace();
                        int start = markdownText.length();
                        String boldText = collectTextContent(strongEmphasis);
                        markdownText.append(boldText);
                        int end = markdownText.length();
                        styleRanges.add(new StyleRange(start, end, "bold"));
                    }

                    @Override
                    public void visit(Emphasis emphasis) {
                        maybeAppendSpace();
                        int start = markdownText.length();
                        String italicText = collectTextContent(emphasis);
                        markdownText.append(italicText);
                        int end = markdownText.length();
                        styleRanges.add(new StyleRange(start, end, "italic"));
                    }

                    @Override
                    public void visit(Link link) {
                        maybeAppendSpace();
                        int start = markdownText.length();
                        String linkText = collectTextContent(link);
                        markdownText.append(linkText); // Only append link text, not the Markdown syntax
                        int end = markdownText.length();
                        styleRanges.add(new StyleRange(start, end, "link"));

                        // Map the position of the link text to its destination
                        for (int i = start; i < end; i++) {
                            linkPositions.put(i, link.getDestination());
                        }
                    }

                    @Override
                    public void visit(Code code) {
                        maybeAppendSpace();
                        int start = markdownText.length();
                        String codeText = code.getLiteral();
                        markdownText.append(codeText); // Append the code text
                        int end = markdownText.length();
                        styleRanges.add(new StyleRange(start, end, "code"));
                    }

                });
                markdownText.append("\n\n");
                visitChildren(paragraph);
            }

            @Override
            public void visit(BulletList bulletList) {
                bulletList.accept(new AbstractVisitor() {
                    @Override
                    public void visit(ListItem listItem) {
                        int start = markdownText.length();
                        markdownText.append("- "); // Add bullet point
                        listItem.accept(new AbstractVisitor() {
                            @Override
                            public void visit(Text textNode) {
                                markdownText.append(textNode.getLiteral());
                            }

                            @Override
                            public void visit(StrongEmphasis strongEmphasis) {
                                int start = markdownText.length();
                                String boldText = collectTextContent(strongEmphasis);
                                markdownText.append(boldText);
                                int end = markdownText.length();
                                styleRanges.add(new StyleRange(start, end, "bold"));
                                visitChildren(strongEmphasis);
                            }

                            @Override
                            public void visit(Emphasis emphasis) {
                                int start = markdownText.length();
                                String italicText = collectTextContent(emphasis);
                                markdownText.append(italicText);
                                int end = markdownText.length();
                                styleRanges.add(new StyleRange(start, end, "italic"));
                                visitChildren(emphasis);
                            }

                            @Override
                            public void visit(Link link) {
                                int start = markdownText.length();
                                String linkText = collectTextContent(link);
                                markdownText.append(linkText); // Only append link text, not the Markdown syntax
                                int end = markdownText.length();
                                styleRanges.add(new StyleRange(start, end, "link"));

                                // Map the position of the link text to its destination
                                for (int i = start; i < end; i++) {
                                    linkPositions.put(i, link.getDestination());
                                }

                            }
                        });
                        markdownText.append("\n");
                    }
                });
                markdownText.append("\n\n");
            }
        });

        styledTextArea.replaceText(markdownText.toString());

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        for (StyleRange styleRange : styleRanges) {
            spansBuilder.add(Collections.emptyList(), styleRange.start - lastEnd);
            spansBuilder.add(Collections.singleton(styleRange.style), styleRange.end - styleRange.start);
            lastEnd = styleRange.end;
        }
        spansBuilder.add(Collections.emptyList(), markdownText.length() - lastEnd);

        styledTextArea.setStyleSpans(0, spansBuilder.create());

    }

    private String collectTextContent(Node node) {
        StringBuilder content = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                content.append(text.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak softLineBreak) {
                content.append('\n');
            }

            @Override
            public void visit(HardLineBreak hardLineBreak) {
                content.append('\n');
            }
        });
        return content.toString();
    }

    private record ImageInsertion(int position, String url) {

    }

    private static class StyleRange {
        final int start;
        final int end;
        final String style;

        StyleRange(int start, int end, String style) {
            this.start = start;
            this.end = end;
            this.style = style;
        }
    }
}
