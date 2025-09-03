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

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.expr.BuiltinFunction;
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
    private final ContextMenu completionPopup = new ContextMenu();
    private CompletionProvider completionProvider;
    private Tooltip hoverTooltip = null;
    private javafx.animation.Timeline hoverDelayTimeline;

    private Path includesDir;

    public ImageMathTextArea() {
        this.completionProvider = new ImageMathCompletionProvider(null, knownVariables);

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
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyHighlighting);
        setCenter(codeArea);
        setupContextMenu();
        setupCompletion();
        setupHoverTooltip();
    }

    public void setIncludesDir(Path includesDir) {
        this.includesDir = includesDir;
        this.completionProvider = new ImageMathCompletionProvider(includesDir, knownVariables);
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

    public CodeArea getCodeArea() {
        return codeArea;
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
        checkForAutoCompletion(highlighting);
    }

    private void checkForAutoCompletion(StyleSpans<Collection<String>> highlighting) {
        if (completionPopup.isShowing()) {
            return;
        }

        int caretPos = codeArea.getCaretPosition();
        if (caretPos == 0) {
            return;
        }

        // Get the token at caret to see if it's a partial identifier
        var context = CompletionContext.analyze(codeArea.getText(), caretPos, includesDir);
        String partial = context.getPartialToken();

        // Check for auto-completion opportunities
        if (partial.length() > 1 && context.getContextType() == CompletionContext.ContextType.GENERAL) {
            var completions = completionProvider.getCompletions(codeArea.getText(), caretPos);

            // Show auto-completion if we have a partial match and there are completions
            if (!completions.isEmpty()) {
                // Check if any completion starts with our partial (indicating a good match)
                boolean hasGoodMatch = completions.stream()
                        .anyMatch(completion -> completion.text().toLowerCase().startsWith(partial.toLowerCase()));

                if (hasGoodMatch) {
                    // Small delay to ensure caret position is stable
                    Platform.runLater(() -> {
                        Platform.runLater(() -> {
                            populateCompletionPopup(completions);
                            showCompletionPopup();
                        });
                    });
                }
            }
        }
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

    private void setupContextMenu() {
        var contextMenu = new ContextMenu();

        var copyItem = new MenuItem(I18N.string(JSolEx.class, "app", "copy"));
        copyItem.setOnAction(e -> {
            String selectedText = codeArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                var clipboard = Clipboard.getSystemClipboard();
                var content = new ClipboardContent();
                content.putString(selectedText);
                clipboard.setContent(content);
            }
        });

        var pasteItem = new MenuItem(I18N.string(JSolEx.class, "app", "paste"));
        pasteItem.setOnAction(e -> {
            var clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                String clipboardText = clipboard.getString();
                codeArea.replaceSelection(clipboardText);
            }
        });

        var selectAllItem = new MenuItem(I18N.string(JSolEx.class, "app", "select.all"));
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        contextMenu.getItems().addAll(copyItem, pasteItem, selectAllItem);
        codeArea.setContextMenu(contextMenu);

        // Enable/disable menu items based on selection and clipboard content
        contextMenu.setOnShowing(e -> {
            boolean hasSelection = codeArea.getSelectedText() != null && !codeArea.getSelectedText().isEmpty();
            boolean hasClipboardText = Clipboard.getSystemClipboard().hasString();
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!hasClipboardText);
        });
    }

    private void setupCompletion() {
        completionPopup.setAutoHide(true);

        // Intercept Tab events at the highest level before CodeArea processes them
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (completionPopup.isShowing() && event.getCode() == KeyCode.TAB) {
                if (!completionPopup.getItems().isEmpty()) {
                    var firstItem = completionPopup.getItems().get(0);
                    if (firstItem instanceof CustomMenuItem customItem) {
                        customItem.fire();
                        event.consume();
                    }
                }
            }
        });

        // Hide popup on text changes (typing new characters)
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (completionPopup.isShowing()) {
                // Update popup with filtered completions if still relevant
                Platform.runLater(() -> updateCompletionFilter());
            }
        });

        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
                showCompletion();
                event.consume();
            } else if (completionPopup.isShowing()) {
                switch (event.getCode()) {
                    case ESCAPE -> {
                        completionPopup.hide();
                        event.consume();
                    }
                    case ENTER -> {
                        if (!completionPopup.getItems().isEmpty()) {
                            // Simulate clicking on the first item - use the same codepath
                            var firstItem = completionPopup.getItems().get(0);
                            if (firstItem instanceof CustomMenuItem customItem) {
                                // Fire the action that's already configured for clicking
                                customItem.fire();
                                event.consume();
                            }
                        }
                    }
                }
            } else if (event.getCode() == KeyCode.TAB) {
                // Check if we should auto-complete when Tab is pressed outside popup
                var completions = completionProvider != null ?
                        completionProvider.getCompletions(codeArea.getText(), codeArea.getCaretPosition()) :
                        List.<CompletionItem>of();

                if (!completions.isEmpty()) {
                    var context = CompletionContext.analyze(codeArea.getText(), codeArea.getCaretPosition(), includesDir);
                    String partial = context.getPartialToken();

                    // Auto-complete if there's a partial match
                    if (!partial.isEmpty() && partial.length() > 1) {
                        insertCompletion(completions.get(0));
                        event.consume();
                    }
                }
            }
        });
    }

    private void showCompletion() {
        if (completionProvider == null) {
            return;
        }

        var completions = completionProvider.getCompletions(
                codeArea.getText(),
                codeArea.getCaretPosition()
        );

        if (completions.isEmpty()) {
            completionPopup.hide();
            return;
        }

        populateCompletionPopup(completions);
        showCompletionPopup();
    }

    private void updateCompletionFilter() {
        if (!completionPopup.isShowing()) {
            return;
        }

        var context = CompletionContext.analyze(codeArea.getText(), codeArea.getCaretPosition(), includesDir);
        String partial = context.getPartialToken();

        if (partial.isEmpty() || partial.length() < 2) {
            completionPopup.hide();
            return;
        }

        var completions = completionProvider.getCompletions(codeArea.getText(), codeArea.getCaretPosition());
        if (completions.isEmpty()) {
            completionPopup.hide();
            return;
        }

        // Check if any completion still matches
        boolean hasMatch = completions.stream()
                .anyMatch(completion -> completion.text().toLowerCase().startsWith(partial.toLowerCase()));

        if (!hasMatch) {
            completionPopup.hide();
        } else {
            // Update popup with filtered completions
            populateCompletionPopup(completions);
        }
    }

    private void showCompletionPopup() {
        var caretBounds = codeArea.getCaretBounds();
        if (caretBounds.isPresent()) {
            var bounds = caretBounds.get();
            completionPopup.show(codeArea, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private void populateCompletionPopup(List<CompletionItem> completions) {
        completionPopup.getItems().clear();

        int maxItems = Math.min(completions.size(), 10);
        for (int i = 0; i < maxItems; i++) {
            var completion = completions.get(i);
            var label = new Label(completion.text());
            label.getStyleClass().add("completion-" + completion.type().name().toLowerCase());

            var menuItem = new CustomMenuItem(label);
            menuItem.setHideOnClick(false);
            menuItem.setUserData(completion); // Store the completion item directly
            menuItem.setOnAction(action -> {
                insertCompletion(completion);
                completionPopup.hide();
            });

            completionPopup.getItems().add(menuItem);
        }
    }

    private CompletionItem getSelectedCompletionItem() {
        if (completionPopup.getItems().isEmpty()) {
            return null;
        }

        var firstItem = completionPopup.getItems().get(0);
        if (firstItem instanceof CustomMenuItem customItem) {
            var label = (Label) customItem.getContent();
            return findCompletionItemByLabel(label.getText());
        }

        return null;
    }

    private CompletionItem getFirstCompletionItem() {
        if (completionProvider == null) {
            return null;
        }

        var completions = completionProvider.getCompletions(
                codeArea.getText(),
                codeArea.getCaretPosition()
        );

        return completions.isEmpty() ? null : completions.get(0);
    }

    private CompletionItem getFirstCompletionFromPopup() {
        if (completionPopup.getItems().isEmpty()) {
            return null;
        }

        var firstItem = completionPopup.getItems().get(0);
        if (firstItem instanceof CustomMenuItem customItem) {
            return (CompletionItem) customItem.getUserData();
        }

        return null;
    }

    private CompletionItem findCompletionItemByLabel(String labelText) {
        if (completionProvider == null) {
            return null;
        }

        var completions = completionProvider.getCompletions(
                codeArea.getText(),
                codeArea.getCaretPosition()
        );

        return completions.stream()
                .filter(item -> item.text().equals(labelText))
                .findFirst()
                .orElse(null);
    }

    private void insertCompletion(CompletionItem item) {
        var context = CompletionContext.analyze(codeArea.getText(), codeArea.getCaretPosition(), includesDir);
        String partial = context.getPartialToken();

        int startPos = codeArea.getCaretPosition() - partial.length();
        int endPos = codeArea.getCaretPosition();

        codeArea.replaceText(startPos, endPos, item.text());
    }

    private void setupHoverTooltip() {
        codeArea.setOnMouseMoved(this::handleMouseHover);
        codeArea.setOnMouseExited(event -> hideHoverTooltip());
    }

    private void handleMouseHover(MouseEvent event) {
        hideHoverTooltip();
        
        // Cancel any existing delayed hover
        if (hoverDelayTimeline != null) {
            hoverDelayTimeline.stop();
        }

        var mousePosition = codeArea.hit(event.getX(), event.getY());
        var charIndexOpt = mousePosition.getCharacterIndex();
        if (charIndexOpt.isEmpty() || charIndexOpt.getAsInt() >= codeArea.getLength()) {
            return;
        }

        int position = charIndexOpt.getAsInt();
        var functionCall = findFunctionCallAtPosition(position);

        if (functionCall != null) {
            try {
                var builtinFunction = BuiltinFunction.valueOf(functionCall.toUpperCase());
                
                // Create a delayed hover with 250ms delay
                hoverDelayTimeline = new Timeline(new KeyFrame(
                    javafx.util.Duration.millis(250),
                    e -> showHoverTooltip(event, builtinFunction)
                ));
                hoverDelayTimeline.play();
            } catch (IllegalArgumentException e) {
                // Not a builtin function
            }
        }
    }

    private String findFunctionCallAtPosition(int position) {
        var parser = new ImageMathParser(codeArea.getText());
        parser.setParserTolerant(true);
        parser.setIncludeDir(includesDir);

        try {
            parser.parse();
            var root = parser.rootNode();

            var functionCalls = root.descendantsOfType(FunctionCall.class);
            for (var call : functionCalls) {
                int start = call.getBeginOffset();
                int end = Math.min(start + call.getFunctionName().length(), call.getEndOffset());

                if (position >= start && position <= end) {
                    return call.getFunctionName().toLowerCase();
                }
            }
        } catch (ParseException e) {
            // ignore parse errors for hover
        }

        return null;
    }

    private void showHoverTooltip(MouseEvent event, BuiltinFunction builtinFunction) {
        var content = createDocumentationContent(builtinFunction);

        hoverTooltip = new Tooltip();
        hoverTooltip.setGraphic(content);
        hoverTooltip.setShowDelay(javafx.util.Duration.millis(300));
        hoverTooltip.setHideDelay(javafx.util.Duration.millis(200));
        hoverTooltip.setShowDuration(javafx.util.Duration.INDEFINITE);

        hoverTooltip.show(codeArea, event.getScreenX() + 10, event.getScreenY() + 10);
    }

    private VBox createDocumentationContent(BuiltinFunction builtinFunction) {
        var content = new VBox(5);
        content.setStyle("-fx-padding: 8; -fx-background-color: #2b2b2b; -fx-text-fill: white; -fx-font-size: 12px;");

        String currentLanguage = Locale.getDefault().getLanguage();

        // Function name
        var nameText = new Text(builtinFunction.name());
        nameText.setStyle("-fx-fill: #569cd6; -fx-font-weight: bold; -fx-font-size: 14px;");

        // Description
        var descriptionText = new Text(builtinFunction.getDocumentation(currentLanguage));
        descriptionText.setStyle("-fx-fill: #cccccc;");
        descriptionText.setWrappingWidth(400);

        content.getChildren().addAll(nameText, descriptionText);

        // Parameters
        var parameterInfo = builtinFunction.getParameterInfo();
        if (!parameterInfo.isEmpty()) {
            var parametersLabel = new Text(getLocalizedText("parameters", currentLanguage) + ":");
            parametersLabel.setStyle("-fx-fill: #4ec9b0; -fx-font-weight: bold;");
            content.getChildren().add(parametersLabel);

            for (var param : parameterInfo) {
                var optionalText = param.optional() ? " (" + getLocalizedText("optional", currentLanguage) + ")" : "";
                var paramLine = new Text(String.format("  %s%s: %s",
                        param.name(),
                        optionalText,
                        param.getDescription(currentLanguage)
                ));
                paramLine.setStyle("-fx-fill: #cccccc;");
                paramLine.setWrappingWidth(380);
                content.getChildren().add(paramLine);
            }
        }

        // Examples
        var examples = builtinFunction.getExamples(currentLanguage);
        if (!examples.isEmpty()) {
            var examplesLabel = new Text(getLocalizedText("examples", currentLanguage) + ":");
            examplesLabel.setStyle("-fx-fill: #4ec9b0; -fx-font-weight: bold;");
            content.getChildren().add(examplesLabel);

            for (var example : examples) {
                var exampleText = new Text("  " + example);
                exampleText.setStyle("-fx-fill: #ce9178; -fx-font-family: monospace;");
                content.getChildren().add(exampleText);
            }
        }

        return content;
    }

    private String getLocalizedText(String key, String language) {
        return switch (key) {
            case "parameters" -> switch (language) {
                case "fr" -> "Paramètres";
                default -> "Parameters";
            };
            case "examples" -> switch (language) {
                case "fr" -> "Exemples";
                default -> "Examples";
            };
            case "optional" -> switch (language) {
                case "fr" -> "optionnel";
                default -> "optional";
            };
            default -> key;
        };
    }

    private void hideHoverTooltip() {
        if (hoverDelayTimeline != null) {
            hoverDelayTimeline.stop();
            hoverDelayTimeline = null;
        }
        if (hoverTooltip != null) {
            hoverTooltip.hide();
            hoverTooltip = null;
        }
    }

    public void close() {
        hideHoverTooltip();
        executor.shutdownNow();
    }

    private static List<String> toStyleSpan(Node token) {
        var simpleName = token.getClass().getSimpleName();
        var styleClass = simpleName.toLowerCase(Locale.US);
        return List.of(styleClass);
    }

}
