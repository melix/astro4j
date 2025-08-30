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

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
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


import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class SimpleMarkdownViewer {
    private final String title;
    private final HostServices hostServices;
    private ScrollPane scrollPane;

    public SimpleMarkdownViewer(String title, HostServices hostServices) {
        this.title = title;
        this.hostServices = hostServices;
    }

    public void render(Scene parent, String markdown, Runnable onDismiss) {
        var parser = Parser.builder().build();
        var document = parser.parse(markdown);
        
        var contentVBox = new VBox(16);
        contentVBox.setPadding(new Insets(24, 32, 24, 32));
        contentVBox.setStyle("-fx-background-color: white;");
        
        var anchorMap = new HashMap<String, javafx.scene.Node>();
        buildMarkdownContent(document, contentVBox, anchorMap);

        this.scrollPane = new ScrollPane(contentVBox);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setStyle("-fx-background: white; -fx-background-color: white;");
        
        var root = new BorderPane();
        root.setCenter(this.scrollPane);
        
        var closeButton = new Button(message("do.not.show.again"));
        closeButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; " +
                           "-fx-background-radius: 6px; -fx-border-radius: 6px; " +
                           "-fx-padding: 8px 16px; -fx-font-size: 13px; " +
                           "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);");
        closeButton.setOnAction(unused -> {
            var stage = (Stage) closeButton.getScene().getWindow();
            stage.close();
            onDismiss.run();
        });
        
        var buttonBar = new HBox(closeButton);
        buttonBar.setPadding(new Insets(16, 24, 20, 24));
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                         "-fx-border-width: 1px 0 0 0;");
        root.setBottom(buttonBar);

        var scene = new Scene(root, 850, 650);
        root.setStyle("-fx-background-color: white;");

        scene.getStylesheets().add(JSolEx.class.getResource("text-viewer.css").toExternalForm());
        var stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent.getWindow());
        stage.setResizable(true);
        stage.showAndWait();
    }

    private void buildMarkdownContent(Node document, VBox contentVBox, Map<String, javafx.scene.Node> anchorMap) {
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                var text = new javafx.scene.text.Text(collectTextContent(heading));
                text.getStyleClass().add("heading-" + heading.getLevel());
                var textFlow = new TextFlow(text);

                var anchor = buildAnchor(collectTextContent(heading));
                anchorMap.put(anchor, textFlow);
                
                contentVBox.getChildren().add(textFlow);
            }

            @Override
            public void visit(Paragraph paragraph) {
                var textFlow = new TextFlow();
                paragraph.accept(new AbstractVisitor() {
                    @Override
                    public void visit(org.commonmark.node.Text textNode) {
                        var text = new javafx.scene.text.Text(textNode.getLiteral());
                        textFlow.getChildren().add(text);
                    }

                    @Override
                    public void visit(StrongEmphasis strongEmphasis) {
                        var text = new javafx.scene.text.Text(collectTextContent(strongEmphasis));
                        text.getStyleClass().add("bold");
                        textFlow.getChildren().add(text);
                    }

                    @Override
                    public void visit(Emphasis emphasis) {
                        var text = new javafx.scene.text.Text(collectTextContent(emphasis));
                        text.getStyleClass().add("italic");
                        textFlow.getChildren().add(text);
                    }

                    @Override
                    public void visit(Link link) {
                        var text = new javafx.scene.text.Text(collectTextContent(link));
                        text.getStyleClass().add("link");
                        text.setStyle("-fx-cursor: hand;");
                        text.setOnMouseClicked(event -> {
                            if (link.getDestination().startsWith("#")) {
                                var anchor = buildAnchor(link.getDestination().substring(1));
                                var targetNode = anchorMap.get(anchor);
                                if (targetNode != null) {
                                    scrollToNode(targetNode, contentVBox);
                                }
                            } else {
                                hostServices.showDocument(link.getDestination());
                            }
                        });
                        textFlow.getChildren().add(text);
                    }

                    @Override
                    public void visit(Code code) {
                        var text = new javafx.scene.text.Text(code.getLiteral());
                        text.getStyleClass().add("code");
                        textFlow.getChildren().add(text);
                    }

                    @Override
                    public void visit(org.commonmark.node.Image image) {
                        var linkedImage = new LinkedImage(image.getDestination(), collectTextContent(image));
                        var imageNode = linkedImage.createNode();
                        textFlow.getChildren().add(imageNode);
                    }
                });
                
                if (!textFlow.getChildren().isEmpty()) {
                    contentVBox.getChildren().add(textFlow);
                }
            }

            @Override
            public void visit(BulletList bulletList) {
                var listVBox = new VBox(4);
                bulletList.accept(new AbstractVisitor() {
                    @Override
                    public void visit(ListItem listItem) {
                        var textFlow = new TextFlow();
                        var bullet = new javafx.scene.text.Text("• ");
                        bullet.getStyleClass().add("bullet");
                        textFlow.getChildren().add(bullet);
                        
                        listItem.accept(new AbstractVisitor() {
                            @Override
                            public void visit(org.commonmark.node.Text textNode) {
                                var text = new javafx.scene.text.Text(textNode.getLiteral());
                                textFlow.getChildren().add(text);
                            }

                            @Override
                            public void visit(StrongEmphasis strongEmphasis) {
                                var text = new javafx.scene.text.Text(collectTextContent(strongEmphasis));
                                text.getStyleClass().add("bold");
                                textFlow.getChildren().add(text);
                            }

                            @Override
                            public void visit(Emphasis emphasis) {
                                var text = new javafx.scene.text.Text(collectTextContent(emphasis));
                                text.getStyleClass().add("italic");
                                textFlow.getChildren().add(text);
                            }

                            @Override
                            public void visit(Link link) {
                                var text = new javafx.scene.text.Text(collectTextContent(link));
                                text.getStyleClass().add("link");
                                text.setStyle("-fx-cursor: hand;");
                                text.setOnMouseClicked(event -> {
                                    if (link.getDestination().startsWith("#")) {
                                        var anchor = buildAnchor(link.getDestination().substring(1));
                                        var targetNode = anchorMap.get(anchor);
                                        if (targetNode != null) {
                                            scrollToNode(targetNode, contentVBox);
                                        }
                                    } else {
                                        hostServices.showDocument(link.getDestination());
                                    }
                                });
                                textFlow.getChildren().add(text);
                            }

                            @Override
                            public void visit(Code code) {
                                var text = new javafx.scene.text.Text(code.getLiteral());
                                text.getStyleClass().add("code");
                                textFlow.getChildren().add(text);
                            }
                        });
                        
                        listVBox.getChildren().add(textFlow);
                    }
                });
                contentVBox.getChildren().add(listVBox);
            }
        });
    }

    private static String buildAnchor(String text) {
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]", "-");
    }

    private void scrollToNode(javafx.scene.Node targetNode, VBox contentVBox) {
        var targetBounds = targetNode.getBoundsInParent();
        var contentHeight = contentVBox.getHeight();
        var viewportHeight = scrollPane.getViewportBounds().getHeight();
        
        if (contentHeight > viewportHeight) {
            var targetY = targetBounds.getMinY();
            var scrollRatio = targetY / (contentHeight - viewportHeight);
            scrollPane.setVvalue(Math.max(0, Math.min(1, scrollRatio)));
        }
    }

    private String collectTextContent(Node node) {
        var content = new StringBuilder();
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

            @Override
            public void visit(org.commonmark.node.Image image) {
            }
        });
        return content.toString();
    }

    private static class LinkedImage {
        private final String url;
        private final String altText;
        private final Image image;

        LinkedImage(String url, String altText) {
            this.url = url;
            this.altText = altText;
            this.image = loadImage(url);
        }

        private Image loadImage(String url) {
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return new Image(url, true);
                } else {
                    Image image = null;
                    
                    var resource = JSolEx.class.getResource(url);
                    if (resource != null) {
                        image = new Image(resource.toExternalForm());
                    }

                    return image;
                }
            } catch (Exception e) {
                // ignore
            }
            return null;
        }

        public javafx.scene.Node createNode() {
            if (image != null && !image.isError()) {
                var imageView = new ImageView(image);
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(400);
                return imageView;
            } else {
                var label = new Label("[Image: " + altText + "]");
                label.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 4px;");
                return label;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            var that = (LinkedImage) obj;
            return Objects.equals(url, that.url) && Objects.equals(altText, that.altText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, altText);
        }
    }

}
