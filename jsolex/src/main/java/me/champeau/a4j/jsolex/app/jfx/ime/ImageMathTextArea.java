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
import me.champeau.a4j.jsolex.processing.expr.ScriptToken;
import me.champeau.a4j.jsolex.processing.expr.ScriptTokenizer;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageMathTextArea extends BorderPane {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CodeArea codeArea = new CodeArea();

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
                    if(t.isSuccess()) {
                        return Optional.of(t.get());
                    } else {
                        t.getFailure().printStackTrace();
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyHighlighting);
        setCenter(codeArea);
    }

    public void setText(String text) {
        codeArea.replaceText(text);
        codeArea.moveTo(0);
        codeArea.showParagraphAtTop(0);
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

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        var tokenizer = new ScriptTokenizer();
        var tokens = tokenizer.tokenize(text);
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        for (var token : tokens) {
            int tokenLength = token.length();
            if (token instanceof ScriptToken.VariableDefinition definition) {
                spansBuilder.add(toStyleSpan(definition.variable()), definition.variable().length());
                spansBuilder.add(List.of(), definition.expression().start() - definition.variable().end());
                spansBuilder.add(toStyleSpan(definition.expression()), definition.expression().length());
            } else {
                spansBuilder.add(toStyleSpan(token), tokenLength);
            }
        }
        return spansBuilder.create();
    }

    public void close() {
        executor.shutdownNow();
    }

    private List<String> toStyleSpan(ScriptToken token) {
        var simpleName = token.getClass().getSimpleName();
        var styleClass = simpleName.toLowerCase(Locale.US);
        return List.of(styleClass);
    }

}
