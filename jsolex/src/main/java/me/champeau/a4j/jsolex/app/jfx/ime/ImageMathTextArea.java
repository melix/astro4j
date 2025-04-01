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
import me.champeau.a4j.jsolex.expr.ast.NamedArgument;
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
import java.util.ArrayList;
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
        parser.setParserTolerant(true);
        parser.setIncludeDir(includesDir);
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        Node root;
        Node inlined;
        boolean error = false;
        try {
            parser.parse();
            root = parser.rootNode();
            parser = new ImageMathParser(text);
            parser.setIncludeDir(includesDir);
            parser.parseAndInlineIncludes();
            inlined = parser.rootNode();
        } catch (ParseException e) {
            root = parser.rootNode();
            inlined = parser.rootNode();
            error = true;
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
        knownVariables.add(DefaultImageScriptExecutor.DETECTED_WAVELEN);
        knownVariables.add(DefaultImageScriptExecutor.DETECTED_DISPERSION);
        knownVariables.addAll(this.knownVariables);
        var userFunctionNames = root.childrenOfType(FunctionDef.class)
            .stream()
            .map(FunctionDef::name)
            .collect(Collectors.toSet());
        var descendants = root instanceof InvalidToken ? List.of(root) : Stream.concat(
                root.descendants().stream(),
                root.getAllTokens(true).stream()
            ).sorted(Comparator.comparingInt(Node::getBeginOffset))
            .toList();
        int pos = 0;
        boolean hasSpans = false;
        Node previousToken = null;
        for (var token : descendants) {
            List<String> styles = new ArrayList<>();
            if (error) {
                styles.add("underline_error");
            }
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
                spansBuilder.add(styles, tokenStart - pos);
                pos = tokenStart;
            }
            switch (token) {
                case Comment comment -> {
                    var tokenLength = comment.getLength();
                    pos += tokenLength;
                    hasSpans = true;
                    spansBuilder.add(List.of("comment"), tokenLength);
                }
                case Assignment assignment -> {
                    var variable = assignment.variable();
                    if (variable.isPresent()) {
                        int tokenLength = variable.get().getLength();
                        pos += tokenLength;
                        hasSpans = true;
                        styles.add("variable_def");
                        spansBuilder.add(styles, tokenLength);
                        knownVariables.add(variable.toString());
                    }
                }
                case FunctionCall functionCall -> {
                    var functionName = functionCall.getFunctionName();
                    int tokenLength = functionName.length();
                    pos += tokenLength;
                    if (functionCall.getBuiltinFunction().isPresent() || userFunctionNames.contains(functionName)) {
                        styles.add("token_function");
                        spansBuilder.add(styles, tokenLength);
                    } else {
                        styles.addAll(List.of("underline_error", "token_function"));
                        spansBuilder.add(styles, tokenLength);
                    }
                    hasSpans = true;
                }
                case StringLiteral nodes -> {
                    var parent = token.getParent();
                    if (parent instanceof IncludeDef include && include.isUnresolved()) {
                        styles.addAll(List.of("token_literal", "underline_error"));
                    } else {
                        styles.add("token_literal");
                    }
                    var tokenLength = token.getLength();
                    pos += tokenLength;
                    spansBuilder.add(styles, tokenLength);
                    hasSpans = true;
                }
                case NumericalLiteral numericalLiteral -> {
                    var parent = token.getParent();
                    if (parent instanceof IncludeDef include && include.isUnresolved()) {
                        styles.addAll(List.of("token_literal", "underline_error"));
                    } else {
                        styles.add("token_literal");
                    }
                    var tokenLength = token.getLength();
                    pos += tokenLength;
                    spansBuilder.add(styles, tokenLength);
                    hasSpans = true;
                }
                case SectionHeader nodes -> {
                    var tokenLength = token.getLength();
                    pos += tokenLength;
                    styles.addAll(toStyleSpan(token));
                    spansBuilder.add(styles, tokenLength);
                }
                case Identifier nodes -> {
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
                        styles.add("variable");
                        spansBuilder.add(styles, tokenLength);
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
                            styles.add("variable");
                            spansBuilder.add(styles, tokenLength);
                        } else if (token.getParent() instanceof NamedArgument namedArgument) {
                            parent = namedArgument;
                            while (parent != null && !(parent instanceof FunctionCall)) {
                                parent = parent.getParent();
                            }
                            if (parent instanceof FunctionCall call) {
                                styles.add("named_arg");
                                if (call.getBuiltinFunction().isPresent()) {
                                    if (call.getBuiltinFunction().get().getAllParameterNames().contains(token.toString())) {
                                        spansBuilder.add(styles, tokenLength);
                                    } else {
                                        styles.add("named_arg");
                                        spansBuilder.add(styles, tokenLength);
                                    }
                                } else {
                                    // find a user function with the same name
                                    var functionName = call.getFunctionName();
                                    var functionDef = root.childrenOfType(FunctionDef.class)
                                            .stream()
                                            .filter(f -> f.name().equals(functionName))
                                            .findFirst();
                                    if (functionDef.isPresent()) {
                                        var args = functionDef.get().arguments();
                                        if (args.contains(token.toString())) {
                                            spansBuilder.add(styles, tokenLength);
                                        } else {
                                            styles.add("underline_error");
                                            spansBuilder.add(styles, tokenLength);
                                        }
                                    } else {
                                        styles.add("underline_error");
                                        spansBuilder.add(styles, tokenLength);
                                    }
                                }
                            }
                        } else {
                            styles.addAll(List.of("underline_error", "identifier"));
                            spansBuilder.add(styles, tokenLength);
                        }
                    }
                    hasSpans = true;
                }
                case InvalidToken invalid -> {
                    var tokenLength = invalid.getLength();
                    pos += tokenLength;
                    styles.add("invalid");
                    spansBuilder.add(styles, tokenLength);
                    hasSpans = true;
                }
                case Keyword nodes -> {
                    var tokenLength = token.getLength();
                    pos += tokenLength;
                    styles.add("keyword");
                    spansBuilder.add(styles, tokenLength);
                    hasSpans = true;
                }
                default -> {
                }
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
