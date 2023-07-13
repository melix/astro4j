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

import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.expr.ExpressionParser;
import me.champeau.a4j.jsolex.expr.Token;
import me.champeau.a4j.jsolex.expr.TokenType;
import me.champeau.a4j.jsolex.expr.Variable;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ScriptToken;
import me.champeau.a4j.jsolex.processing.expr.ScriptTokenizer;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ImageMathTextArea extends BorderPane {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CodeArea codeArea = new CodeArea();
    private final ExpressionParser expressionParser;

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
        expressionParser = new ExpressionParser();
    }

    public void setText(String text) {
        BatchOperations.submit(() -> {
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
        var tokenizer = new ScriptTokenizer();
        var tokens = tokenizer.tokenize(text);
        var knownVariables = tokens.stream()
                .filter(ScriptToken.VariableDefinition.class::isInstance)
                .map(ScriptToken.VariableDefinition.class::cast)
                .map(ScriptToken.VariableDefinition::variable)
                .map(ScriptToken.Variable::name)
                .collect(Collectors.toSet());
        knownVariables.add(DefaultImageScriptExecutor.BLACK_POINT_VAR);
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        for (var token : tokens) {
            int tokenLength = token.length();
            if (token instanceof ScriptToken.VariableDefinition definition) {
                spansBuilder.add(toStyleSpan(definition.variable()), definition.variable().length());
                var expression = definition.expression();
                spansBuilder.add(List.of(), expression.start() - definition.variable().end());
                if (expression instanceof ScriptToken.Expression expr) {
                    highlightExpression(spansBuilder, expr, knownVariables);
                } else {
                    spansBuilder.add(toStyleSpan(definition.expression()), definition.expression().length());
                }
            } else if (token instanceof ScriptToken.Expression expression) {
                highlightExpression(spansBuilder, expression, knownVariables);
            } else {
                spansBuilder.add(toStyleSpan(token), tokenLength);
            }
        }
        if (tokens.isEmpty()) {
            spansBuilder.add(List.of(""), 0);
        }
        return spansBuilder.create();
    }

    private void highlightExpression(StyleSpansBuilder<Collection<String>> spansBuilder, ScriptToken.Expression expression, Set<String> knownVariables) {
        int offset = expression.start();
        int start = 0;
        int end = expression.end() - offset;
        String text = expression.expression();
        var tokens = expressionParser.parseExpression(text).tokens();
        var sortedTokens = tokens.stream().sorted(Comparator.comparingInt(Token::start)).toList();
        for (Token token : sortedTokens) {
            var tokenStart = token.start();
            var tokenEnd = token.end();
            var tokenLen = tokenEnd - tokenStart;
            if (tokenStart > start) {
                spansBuilder.add(List.of(), tokenStart - start);
            }
            var tokenType = token.type().name().toLowerCase(Locale.US);
            var value = token.value();
            if (token.type() == TokenType.VARIABLE && !knownVariables.contains(value)) {
                spansBuilder.add(List.of("underline_error", "token_" + tokenType), tokenLen);
            } else {
                spansBuilder.add(List.of("token_" + tokenType), tokenLen);
            }
            start = tokenEnd;
        }
        int pad = end - start;
        if (pad > 0) {
            spansBuilder.add(List.of(), pad);
        }
    }

    public void close() {
        executor.shutdownNow();
    }

    private List<String> toStyleSpan(ScriptToken token) {
        var simpleName = token.getClass().getSimpleName();
        var styleClass = simpleName.toLowerCase(Locale.US);
        if (token instanceof ScriptToken.Variable variable && Variable.isReservedName(variable.name())) {
            return List.of("underline_error", styleClass);
        }
        return List.of(styleClass);
    }

}
