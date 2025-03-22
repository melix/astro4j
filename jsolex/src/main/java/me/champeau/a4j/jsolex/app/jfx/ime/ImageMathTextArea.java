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
package me.champeau.a4j.jsolex.app.jfx.ime;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import me.champeau.a4j.jsolex.expr.ImageMathParser;
import me.champeau.a4j.jsolex.expr.InvalidToken;
import me.champeau.a4j.jsolex.expr.Node;
import me.champeau.a4j.jsolex.expr.ParseException;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.Comment;
import me.champeau.a4j.jsolex.expr.ast.Delimiter;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.FunctionDef;
import me.champeau.a4j.jsolex.expr.ast.FunctionParams;
import me.champeau.a4j.jsolex.expr.ast.Identifier;
import me.champeau.a4j.jsolex.expr.ast.IncludeDef;
import me.champeau.a4j.jsolex.expr.ast.Keyword;
import me.champeau.a4j.jsolex.expr.ast.NumericalLiteral;
import me.champeau.a4j.jsolex.expr.ast.Section;
import me.champeau.a4j.jsolex.expr.ast.SectionHeader;
import me.champeau.a4j.jsolex.expr.ast.StringLiteral;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImageMathTextArea extends BorderPane {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CodeArea codeArea = new CodeArea();
    private final Set<String> knownVariables = new HashSet<>();

    private Path includesDir;

    public ImageMathTextArea() {
        codeArea.prefWidthProperty().bind(widthProperty());
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(true);
        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(250))
            .retainLatestUntilLater(executor)
            .supplyTask(this::computeHighlightingAsync)
            .awaitLatest(codeArea.multiPlainChanges())
            .filterMap(t -> {
                if (t.isSuccess()) {
                    return Optional.of(t.get());
                } else {
                    t.getFailure().printStackTrace();
                    return Optional.empty();
                }
            })
            .subscribe(this::applyHighlighting);
        setCenter(codeArea);
    }

    public void setIncludesDir(Path includesDir) {
        this.includesDir = includesDir;
        requestHighlighting();
    }

    public void addKnownVariable(String variable) {
        knownVariables.add(variable);
    }

    public void setText(String text) {
        Platform.runLater(() -> {
            codeArea.replaceText(text);
            codeArea.moveTo(0);
            codeArea.showParagraphAtTop(0);
            requestHighlighting();
        });
    }

    public String getText() {
        return codeArea.textProperty().getValue();
    }

    public ObservableValue<String> textProperty() {
        return codeArea.textProperty();
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = codeArea.getText();
        var task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                return computeHighlighting(text);
            }
        };
        executor.execute(task);
        return task;
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        codeArea.setStyleSpans(0, highlighting);
    }

    public void requestHighlighting() {
        try {
            applyHighlighting(computeHighlightingAsync().get());
        } catch (InterruptedException | ExecutionException e) {
            // ignore
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        var parser = new ImageMathParser(text);
        parser.setIncludeDir(includesDir);
        Node root;
        Node inlined;
        try {
            parser.parse();
            root = parser.rootNode();
            parser = new ImageMathParser(text);
            parser.parseAndInlineIncludes();
            inlined = parser.rootNode();
        } catch (ParseException e) {
            root = e.getToken();
            inlined = e.getToken();
        }
        var knownVariables = inlined.descendantsOfType(Assignment.class)
            .stream()
            .map(Assignment::variableName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
        knownVariables.add(DefaultImageScriptExecutor.BLACK_POINT_VAR);
        knownVariables.add(DefaultImageScriptExecutor.ANGLE_P_VAR);
        knownVariables.add(DefaultImageScriptExecutor.B0_VAR);
        knownVariables.add(DefaultImageScriptExecutor.L0_VAR);
        knownVariables.add(DefaultImageScriptExecutor.CARROT_VAR);
        knownVariables.addAll(this.knownVariables);
        var userFunctionNames = root.childrenOfType(FunctionDef.class)
            .stream()
            .map(FunctionDef::name)
            .collect(Collectors.toSet());
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        var descendants = root instanceof InvalidToken ? List.of(root) : Stream.concat(
                root.descendants().stream(),
                root.getAllTokens(true).stream()
            ).sorted(Comparator.comparingInt(Node::getBeginOffset))
            .toList();
        int pos = 0;
        boolean hasSpans = false;
        Node previousToken = null;
        for (var token : descendants) {
            if (token instanceof Section) {
                previousToken = token;
                continue;
            }
            if (token.getBeginOffset() < pos) {
                previousToken = token;
                continue;
            }
            int tokenStart = token.getBeginOffset();
            if (tokenStart > pos) {
                hasSpans = true;
                spansBuilder.add(List.of(), tokenStart - pos);
                pos = tokenStart;
            }
            if (token instanceof Comment comment) {
                var tokenLength = comment.getLength();
                pos += tokenLength;
                hasSpans = true;
                spansBuilder.add(List.of("comment"), tokenLength);
            } else if (token instanceof Assignment assignment) {
                var variable = assignment.variable();
                if (variable.isPresent()) {
                    int tokenLength = variable.get().getLength();
                    pos += tokenLength;
                    hasSpans = true;
                    spansBuilder.add(List.of("variable_def"), tokenLength);
                    knownVariables.add(variable.toString());
                }
            } else if (token instanceof FunctionCall functionCall) {
                var functionName = functionCall.getFunctionName();
                int tokenLength = functionName.length();
                pos += tokenLength;
                if (functionCall.getBuiltinFunction().isPresent() || userFunctionNames.contains(functionName)) {
                    spansBuilder.add(List.of("token_function"), tokenLength);
                } else {
                    spansBuilder.add(List.of("underline_error", "token_function"), tokenLength);
                }
                hasSpans = true;
            } else if (token instanceof StringLiteral || token instanceof NumericalLiteral) {
                var parent = token.getParent();
                List<String> styles;
                if (parent instanceof IncludeDef include && include.isUnresolved()) {
                    styles = List.of("token_literal", "underline_error");
                } else {
                    styles = List.of("token_literal");
                }
                var tokenLength = token.getLength();
                pos += tokenLength;
                spansBuilder.add(styles, tokenLength);
                hasSpans = true;
            } else if (token instanceof SectionHeader) {
                var tokenLength = token.getLength();
                pos += tokenLength;
                spansBuilder.add(toStyleSpan(token), tokenLength);
            } else if (token instanceof Identifier) {
                var tokenLength = token.getLength();
                pos += tokenLength;
                var variables = knownVariables;
                if (previousToken instanceof FunctionParams) {
                    variables = previousToken.children()
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet());
                }
                if (variables.contains(token.toString()) || previousToken instanceof Keyword) {
                    spansBuilder.add(List.of("variable"), tokenLength);
                } else {
                    Node parent = token.getParent();
                    while (parent != null && !(parent instanceof FunctionDef)) {
                        parent = parent.getParent();
                    }
                    if (parent instanceof FunctionDef functionDef) {
                        if (previousToken instanceof Delimiter) {
                            variables = Set.of(functionDef.name());
                        } else {
                            variables = new HashSet<>(functionDef.arguments());
                        }
                    }
                    if (variables.contains(token.toString())) {
                        spansBuilder.add(List.of("variable"), tokenLength);
                    } else {
                        spansBuilder.add(List.of("underline_error", "variable"), tokenLength);
                    }
                }
                hasSpans = true;
            } else if (token instanceof InvalidToken invalid) {
                var tokenLength = invalid.getLength();
                pos += tokenLength;
                spansBuilder.add(List.of("invalid"), tokenLength);
                hasSpans = true;
            } else if (token instanceof Keyword) {
                var tokenLength = token.getLength();
                pos += tokenLength;
                spansBuilder.add(List.of("keyword"), tokenLength);
                hasSpans = true;
            }
            previousToken = token;
        }
        if (!hasSpans) {
            spansBuilder.add(List.of(""), 0);
        }
        return spansBuilder.create();
    }

    public void close() {
        executor.shutdownNow();
    }

    private static List<String> toStyleSpan(Node token) {
        var simpleName = token.getClass().getSimpleName();
        var styleClass = simpleName.toLowerCase(Locale.US);
        return List.of(styleClass);
    }

}
