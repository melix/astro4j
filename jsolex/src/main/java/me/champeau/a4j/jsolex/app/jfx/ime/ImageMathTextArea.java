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
import javafx.scene.input.KeyEvent;
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
import me.champeau.a4j.jsolex.expr.ast.MetaBlock;
import me.champeau.a4j.jsolex.expr.ast.NamedArgument;
import me.champeau.a4j.jsolex.expr.ast.NumericalLiteral;
import me.champeau.a4j.jsolex.expr.ast.ParameterDef;
import me.champeau.a4j.jsolex.expr.ast.ParameterObject;
import me.champeau.a4j.jsolex.expr.ast.ParameterProperty;
import me.champeau.a4j.jsolex.expr.ast.ParametersBlock;
import me.champeau.a4j.jsolex.expr.ast.Section;
import me.champeau.a4j.jsolex.expr.ast.SectionHeader;
import me.champeau.a4j.jsolex.expr.ast.StringLiteral;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private final Set<FoldedRegion> foldedRegions = new HashSet<>();
    private boolean autoFoldMetaBlocks = false;

    private HBox searchBar;
    private TextField searchField;
    private TextField replaceField;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox wholeWordCheckBox;
    private CheckBox regexCheckBox;
    private int currentSearchIndex = -1;
    private List<SearchMatch> searchMatches = new ArrayList<>();

    private static class SearchMatch {
        final int start;
        final int end;

        SearchMatch(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class FoldedRegion {
        final int startParagraph;
        final int endParagraph;
        final String foldText;

        FoldedRegion(int startParagraph, int endParagraph, String foldText) {
            this.startParagraph = startParagraph;
            this.endParagraph = endParagraph;
            this.foldText = foldText;
        }
    }

    public ImageMathTextArea() {
        this.completionProvider = new ImageMathCompletionProvider(null, knownVariables);

        codeArea.prefWidthProperty().bind(widthProperty());
        codeArea.setParagraphGraphicFactory(this::createParagraphGraphic);
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

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            Platform.runLater(() -> {
                foldedRegions.removeIf(region -> region.endParagraph >= codeArea.getParagraphs().size());
                codeArea.setParagraphGraphicFactory(this::createParagraphGraphic);
            });
            if (!searchBar.isVisible()) {
                clearSearchHighlights();
            }
        });

        setCenter(codeArea);
        setupSearchBar();
        setupContextMenu();
        setupCompletion();
        setupHoverTooltip();
        setupKeyboardShortcuts();
    }

    public void setIncludesDir(Path includesDir) {
        this.includesDir = includesDir;
        this.completionProvider = new ImageMathCompletionProvider(includesDir, knownVariables);
        requestHighlighting();
    }

    public void addKnownVariable(String variable) {
        knownVariables.add(variable);
    }

    public void setAutoFoldMetaBlocks(boolean autoFold) {
        this.autoFoldMetaBlocks = autoFold;
    }

    public void setText(String text) {
        Platform.runLater(() -> {
            foldedRegions.clear();
            codeArea.replaceText(text);
            codeArea.moveTo(0);
            codeArea.showParagraphAtTop(0);
            requestHighlighting();

            if (autoFoldMetaBlocks) {
                autoFoldMetaBlocksIfPresent();
            }
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
        var text = codeArea.getText();
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
        var error = false;
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

        // Add parameter names from params blocks as known variables
        var parameterNames = inlined.descendantsOfType(ParameterDef.class)
                .stream()
                .map(paramDef -> paramDef.firstChildOfType(Identifier.class))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
        knownVariables.addAll(parameterNames);

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
        var pos = 0;
        var hasSpans = false;
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
            var tokenStart = token.getBeginOffset();
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
                        var tokenLength = variable.get().getLength();
                        pos += tokenLength;
                        hasSpans = true;
                        styles.add("variable_def");
                        spansBuilder.add(styles, tokenLength);
                        knownVariables.add(variable.toString());
                    }
                }
                case FunctionCall functionCall -> {
                    var functionName = functionCall.getFunctionName();
                    var tokenLength = functionName.length();
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

                    // Check if this identifier is in a valid parameter context
                    var parameterContextStyle = getParameterContextStyle(token);

                    if (parameterContextStyle != null) {
                        styles.add(parameterContextStyle);
                        spansBuilder.add(styles, tokenLength);
                    } else if (variables.contains(token.toString()) || previousToken instanceof Keyword) {
                        styles.add("variable");
                        spansBuilder.add(styles, tokenLength);
                    } else {
                        var parent = token.getParent();
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
                case MetaBlock metaBlock -> {
                    // MetaBlock itself doesn't need highlighting, its children will be handled
                }
                case ParametersBlock parametersBlock -> {
                    // ParametersBlock itself doesn't need highlighting, its children will be handled
                }
                case ParameterDef parameterDef -> {
                    // ParameterDef itself doesn't need highlighting, its children will be handled
                }
                case ParameterObject parameterObject -> {
                    // ParameterObject itself doesn't need highlighting, its children will be handled
                }
                case ParameterProperty parameterProperty -> {
                    // ParameterProperty itself doesn't need highlighting, its children will be handled
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
            var selectedText = codeArea.getSelectedText();
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
                var clipboardText = clipboard.getString();
                codeArea.replaceSelection(clipboardText);
            }
        });

        var selectAllItem = new MenuItem(I18N.string(JSolEx.class, "app", "select.all"));
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        contextMenu.getItems().addAll(copyItem, pasteItem, selectAllItem);
        codeArea.setContextMenu(contextMenu);

        // Enable/disable menu items based on selection and clipboard content
        contextMenu.setOnShowing(e -> {
            var hasSelection = codeArea.getSelectedText() != null && !codeArea.getSelectedText().isEmpty();
            var hasClipboardText = Clipboard.getSystemClipboard().hasString();
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!hasClipboardText);
        });
    }

    private void setupCompletion() {
        completionPopup.setAutoHide(true);
        completionPopup.setHideOnEscape(true);

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (completionPopup.isShowing()) {
                if (event.getCode() == KeyCode.TAB) {
                    if (!completionPopup.getItems().isEmpty()) {
                        var firstItem = completionPopup.getItems().getFirst();
                        if (firstItem instanceof CustomMenuItem customItem) {
                            customItem.fire();
                            event.consume();
                        }
                    }
                }
            }
        });

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (completionPopup.isShowing()) {
                Platform.runLater(this::updateCompletionFilter);
            }
        });
        
        codeArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && completionPopup.isShowing()) {
                completionPopup.hide();
            }
        });

        codeArea.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                completionPopup.hide();
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
        var partial = context.getPartialToken();

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
        var hasMatch = completions.stream()
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

        var maxItems = Math.min(completions.size(), 10);
        for (var i = 0; i < maxItems; i++) {
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

    private CompletionItem getFirstCompletionItem() {
        if (completionProvider == null) {
            return null;
        }

        var completions = completionProvider.getCompletions(
                codeArea.getText(),
                codeArea.getCaretPosition()
        );

        return completions.isEmpty() ? null : completions.getFirst();
    }

    private CompletionItem getFirstCompletionFromPopup() {
        if (completionPopup.getItems().isEmpty()) {
            return null;
        }

        var firstItem = completionPopup.getItems().getFirst();
        if (firstItem instanceof CustomMenuItem customItem) {
            return (CompletionItem) customItem.getUserData();
        }

        return null;
    }

    private void insertCompletion(CompletionItem item) {
        var context = CompletionContext.analyze(codeArea.getText(), codeArea.getCaretPosition(), includesDir);
        var partial = context.getPartialToken();

        var startPos = codeArea.getCaretPosition() - partial.length();
        var endPos = codeArea.getCaretPosition();

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

        var position = charIndexOpt.getAsInt();
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
                var start = call.getBeginOffset();
                var end = Math.min(start + call.getFunctionName().length(), call.getEndOffset());

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

        var currentLanguage = LocaleUtils.getConfiguredLocale().getLanguage();

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

    private String getParameterContextStyle(Node token) {
        // Known parameter properties
        var knownProperties = Set.of("type", "name", "description", "default", "min", "max", "choices", "step");

        // Known meta properties
        var knownMetaProperties = Set.of("author", "title", "version", "requires");

        // Language codes (extend as needed)
        var languageCodes = Set.of("en", "fr", "de", "es", "it", "pt", "nl", "sv", "da", "no", "fi", "pl", "cs", "sk", "hu", "ro", "bg", "hr", "sl", "et", "lv", "lt", "mt", "ga", "cy");

        var tokenText = token.toString();

        // Check if this is a known parameter property
        if (knownProperties.contains(tokenText)) {
            return "parameter_property";
        }

        // Check if this is a known meta property
        if (knownMetaProperties.contains(tokenText)) {
            return "parameter_property"; // Use same styling as parameter properties
        }

        // Check if this is a language code inside a name or description object
        if (languageCodes.contains(tokenText)) {
            var parent = token.getParent();
            while (parent != null) {
                if (parent instanceof ParameterProperty prop &&
                    ("name".equals(prop.getName()) || "description".equals(prop.getName()))) {
                    return "language_code";
                }
                // Also check for meta properties that support i18n (like title)
                if (parent instanceof ParameterObject) {
                    var grandParent = parent.getParent();
                    if (grandParent != null) {
                        // Check if this is a MetaProperty with a supported i18n property name
                        var metaPropertyName = getMetaPropertyName(grandParent);
                        if ("title".equals(metaPropertyName)) {
                            return "language_code";
                        }
                    }
                }
                parent = parent.getParent();
            }
        }

        // Check if this is a parameter name (identifier that is a direct child of ParametersBlock)
        var parent = token.getParent();
        if (parent instanceof ParameterDef) {
            // This is a parameter name like "gamma", "tileSize", etc.
            return "parameter_name";
        }

        return null;
    }

    private String getMetaPropertyName(Node node) {
        // Try to get the name of a MetaProperty by finding its identifier child
        var identifier = node.firstChildOfType(Identifier.class);
        return identifier != null ? identifier.toString() : null;
    }

    // Code folding implementation
    private javafx.scene.Node createParagraphGraphic(int paragraphIndex) {
        // Check if this paragraph is hidden due to folding
        var isHiddenInFold = isParaHiddenInFoldedRegion(paragraphIndex);
        if (isHiddenInFold) {
            // Return empty node with zero height to effectively hide the paragraph
            var hiddenNode = new Label("");
            hiddenNode.setMaxHeight(0);
            hiddenNode.setPrefHeight(0);
            hiddenNode.setMinHeight(0);
            hiddenNode.setVisible(false);
            hiddenNode.setManaged(false);
            return hiddenNode;
        }

        var hbox = new HBox();
        hbox.setSpacing(5);
        hbox.setPadding(new Insets(0, 5, 0, 0));

        // Line number
        var lineNumber = new Label(String.format("%d", paragraphIndex + 1));
        lineNumber.setStyle("-fx-font-family: monospace; -fx-text-fill: #999999; -fx-font-size: 12px;");
        lineNumber.setMinWidth(35);
        hbox.getChildren().add(lineNumber);

        // Fold indicator
        var foldRegion = findFoldRegionStartingAt(paragraphIndex);
        if (foldRegion != null) {
            var foldButton = new Button();
            foldButton.setPrefSize(12, 12);
            foldButton.setStyle("-fx-font-size: 8px; -fx-padding: 0;");

            var isFolded = foldedRegions.stream().anyMatch(r -> r.startParagraph == paragraphIndex);
            foldButton.setText(isFolded ? "+" : "-");

            foldButton.setOnAction(e -> {
                if (isFolded) {
                    unfoldRegion(paragraphIndex);
                } else {
                    foldRegion(paragraphIndex, foldRegion.endLine);
                }
            });

            hbox.getChildren().add(foldButton);

            // Add fold indicator if this region is folded (right after the fold button)
            if (isFolded) {
                var folded = getFoldedRegionForParagraph(paragraphIndex);
                if (folded != null) {
                    var foldIndicator = new Label("... " + (folded.endParagraph - folded.startParagraph) + " lines");
                    foldIndicator.setStyle("-fx-text-fill: #888888; -fx-font-style: italic; -fx-font-size: 10px; -fx-padding: 0 0 0 5;");
                    hbox.getChildren().add(foldIndicator);
                }
            }
        } else {
            // Empty space where fold button would be
            var spacer = new Label(" ");
            spacer.setPrefSize(12, 12);
            hbox.getChildren().add(spacer);
        }

        return hbox;
    }


    private boolean isParaHiddenInFoldedRegion(int paragraphIndex) {
        for (var region : foldedRegions) {
            if (paragraphIndex > region.startParagraph && paragraphIndex <= region.endParagraph) {
                return true;
            }
        }
        return false;
    }

    private FoldedRegion getFoldedRegionForParagraph(int paragraphIndex) {
        for (var region : foldedRegions) {
            if (region.startParagraph == paragraphIndex) {
                return region;
            }
        }
        return null;
    }

    private static class FoldRegion {
        final int startLine;
        final int endLine;

        FoldRegion(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    private FoldRegion findFoldRegionStartingAt(int paragraphIndex) {
        return findFoldRegionStartingAt(paragraphIndex, codeArea.getText());
    }

    private void foldRegion(int startParagraph, int endParagraph) {
        // Store the original text content for this region
        var originalContent = new StringBuilder();
        for (var i = startParagraph; i <= endParagraph && i < codeArea.getParagraphs().size(); i++) {
            originalContent.append(codeArea.getParagraph(i).toString());
            if (i < endParagraph) {
                originalContent.append("\n");
            }
        }

        var region = new FoldedRegion(startParagraph, endParagraph, originalContent.toString());
        foldedRegions.add(region);

        // Style the folded paragraphs to hide them
        for (var i = startParagraph + 1; i <= endParagraph && i < codeArea.getParagraphs().size(); i++) {
            codeArea.setParagraphStyle(i, List.of("folded-hidden"));
        }

        Platform.runLater(() -> {
            codeArea.setParagraphGraphicFactory(this::createParagraphGraphic);
            requestHighlighting();
        });
    }

    private void unfoldRegion(int startParagraph) {
        FoldedRegion toRemove = null;
        for (var region : foldedRegions) {
            if (region.startParagraph == startParagraph) {
                toRemove = region;
                break;
            }
        }

        if (toRemove != null) {
            foldedRegions.remove(toRemove);

            // Remove the hidden styling from the unfolded paragraphs
            for (var i = toRemove.startParagraph + 1; i <= toRemove.endParagraph && i < codeArea.getParagraphs().size(); i++) {
                codeArea.setParagraphStyle(i, List.of());
            }

            Platform.runLater(() -> {
                codeArea.setParagraphGraphicFactory(this::createParagraphGraphic);
                requestHighlighting();
            });
        }
    }

    private void autoFoldMetaBlocksIfPresent() {
        Platform.runLater(() -> {
            var paragraphCount = codeArea.getParagraphs().size();
            for (var paragraphIndex = 0; paragraphIndex < paragraphCount; paragraphIndex++) {
                var foldRegion = findFoldRegionStartingAt(paragraphIndex);
                if (foldRegion != null) {
                    // Auto-fold this meta block
                    foldRegion(paragraphIndex, foldRegion.endLine);
                    break; // Only fold the first meta block found
                }
            }
        });
    }




    private FoldRegion findFoldRegionStartingAt(int paragraphIndex, String text) {
        var lines = text.split("\n", -1);

        if (paragraphIndex >= lines.length) {
            return null;
        }

        var line = lines[paragraphIndex];

        if (!line.trim().startsWith("meta")) {
            return null;
        }

        var braceIndex = line.indexOf('{');
        if (braceIndex == -1) {
            return null;
        }

        var braceCount = 0;

        for (var i = 0; i <= braceIndex; i++) {
            if (line.charAt(i) == '{') {
                braceCount++;
            } else if (line.charAt(i) == '}') {
                braceCount--;
            }
        }

        for (var i = paragraphIndex; i < lines.length; i++) {
            var currentLine = i == paragraphIndex ? line.substring(braceIndex + 1) : lines[i];

            for (var c : currentLine.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        if (i > paragraphIndex) {
                            return new FoldRegion(paragraphIndex, i);
                        }
                        return null;
                    }
                }
            }
        }

        return null;
    }

    private void setupSearchBar() {
        searchBar = new HBox(8);
        searchBar.setPadding(new Insets(8));
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setStyle("-fx-background-color: #3c3f41; -fx-border-color: #555555; -fx-border-width: 0 0 1 0;");

        var searchLabel = new Label(I18N.string(JSolEx.class, "imagemath-editor", "find"));
        searchLabel.setStyle("-fx-text-fill: white;");
        searchLabel.setMinWidth(Region.USE_PREF_SIZE);

        searchField = new TextField();
        searchField.setPrefWidth(150);
        searchField.setMaxWidth(150);
        searchField.setPromptText(I18N.string(JSolEx.class, "imagemath-editor", "find.prompt"));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> performSearch());
        searchField.setOnAction(e -> findNext());

        var findNextButton = new Button("▼");
        findNextButton.setTooltip(new javafx.scene.control.Tooltip(I18N.string(JSolEx.class, "imagemath-editor", "find.next")));
        findNextButton.setOnAction(e -> findNext());
        findNextButton.setMinWidth(Region.USE_PREF_SIZE);

        var findPrevButton = new Button("▲");
        findPrevButton.setTooltip(new javafx.scene.control.Tooltip(I18N.string(JSolEx.class, "imagemath-editor", "find.previous")));
        findPrevButton.setOnAction(e -> findPrevious());
        findPrevButton.setMinWidth(Region.USE_PREF_SIZE);

        var replaceLabel = new Label(I18N.string(JSolEx.class, "imagemath-editor", "replace"));
        replaceLabel.setStyle("-fx-text-fill: white;");
        replaceLabel.setMinWidth(Region.USE_PREF_SIZE);

        replaceField = new TextField();
        replaceField.setPrefWidth(150);
        replaceField.setMaxWidth(150);
        replaceField.setPromptText(I18N.string(JSolEx.class, "imagemath-editor", "replace.prompt"));

        var replaceButton = new Button(I18N.string(JSolEx.class, "imagemath-editor", "replace"));
        replaceButton.setOnAction(e -> replaceCurrent());
        replaceButton.setMinWidth(Region.USE_PREF_SIZE);

        var replaceAllButton = new Button(I18N.string(JSolEx.class, "imagemath-editor", "replace.all"));
        replaceAllButton.setOnAction(e -> replaceAll());
        replaceAllButton.setMinWidth(Region.USE_PREF_SIZE);

        caseSensitiveCheckBox = new CheckBox(I18N.string(JSolEx.class, "imagemath-editor", "case.sensitive"));
        caseSensitiveCheckBox.setStyle("-fx-text-fill: white;");
        caseSensitiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> performSearch());
        caseSensitiveCheckBox.setMinWidth(Region.USE_PREF_SIZE);

        wholeWordCheckBox = new CheckBox(I18N.string(JSolEx.class, "imagemath-editor", "whole.word"));
        wholeWordCheckBox.setStyle("-fx-text-fill: white;");
        wholeWordCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> performSearch());
        wholeWordCheckBox.setMinWidth(Region.USE_PREF_SIZE);

        regexCheckBox = new CheckBox(I18N.string(JSolEx.class, "imagemath-editor", "regex"));
        regexCheckBox.setStyle("-fx-text-fill: white;");
        regexCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> performSearch());
        regexCheckBox.setMinWidth(Region.USE_PREF_SIZE);

        var closeButton = new Button("×");
        closeButton.setStyle("-fx-font-size: 18px; -fx-padding: 0 8;");
        closeButton.setOnAction(e -> hideSearchBar());
        closeButton.setMinWidth(Region.USE_PREF_SIZE);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchBar.getChildren().addAll(
            searchLabel, searchField, findPrevButton, findNextButton,
            replaceLabel, replaceField, replaceButton, replaceAllButton,
            caseSensitiveCheckBox, wholeWordCheckBox, regexCheckBox,
            spacer, closeButton
        );

        searchBar.setVisible(false);
        searchBar.setManaged(false);
    }

    private void setupKeyboardShortcuts() {
        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F && event.isControlDown()) {
                if (searchBar.isVisible()) {
                    hideSearchBar();
                } else {
                    showSearchBar(false);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.H && event.isControlDown()) {
                if (searchBar.isVisible()) {
                    hideSearchBar();
                } else {
                    showSearchBar(true);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && searchBar.isVisible()) {
                hideSearchBar();
                event.consume();
            } else if (event.getCode() == KeyCode.F3) {
                if (event.isShiftDown()) {
                    findPrevious();
                } else {
                    findNext();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
                showCompletion();
                event.consume();
            } else if (completionPopup.isShowing()) {
                if (event.getCode() == KeyCode.ENTER) {
                    if (!completionPopup.getItems().isEmpty()) {
                        var firstItem = completionPopup.getItems().getFirst();
                        if (firstItem instanceof CustomMenuItem customItem) {
                            customItem.fire();
                            event.consume();
                        }
                    }
                }
            } else if (event.getCode() == KeyCode.TAB) {
                var completions = completionProvider != null ?
                        completionProvider.getCompletions(codeArea.getText(), codeArea.getCaretPosition()) :
                        List.<CompletionItem>of();

                if (!completions.isEmpty()) {
                    var context = CompletionContext.analyze(codeArea.getText(), codeArea.getCaretPosition(), includesDir);
                    var partial = context.getPartialToken();

                    if (!partial.isEmpty() && partial.length() > 1) {
                        insertCompletion(completions.getFirst());
                        event.consume();
                    }
                }
            }
        });
    }

    public void showSearchBar(boolean withReplace) {
        if (!searchBar.isVisible()) {
            setTop(searchBar);
            searchBar.setVisible(true);
            searchBar.setManaged(true);
        }
        replaceField.setVisible(withReplace);
        replaceField.setManaged(withReplace);
        var replaceLabel = (Label) searchBar.getChildren().get(4);
        replaceLabel.setVisible(withReplace);
        replaceLabel.setManaged(withReplace);
        var replaceButton = (Button) searchBar.getChildren().get(6);
        replaceButton.setVisible(withReplace);
        replaceButton.setManaged(withReplace);
        var replaceAllButton = (Button) searchBar.getChildren().get(7);
        replaceAllButton.setVisible(withReplace);
        replaceAllButton.setManaged(withReplace);

        searchField.requestFocus();
        if (codeArea.getSelectedText() != null && !codeArea.getSelectedText().isEmpty()) {
            searchField.setText(codeArea.getSelectedText());
            searchField.selectAll();
        }
    }

    public void hideSearchBar() {
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        setTop(null);
        clearSearchHighlights();
        requestHighlighting();
        codeArea.requestFocus();
    }

    private void performSearch() {
        clearSearchHighlights();
        requestHighlighting();

        var searchText = searchField.getText();
        if (searchText == null || searchText.isEmpty()) {
            return;
        }

        var text = codeArea.getText();
        var caseSensitive = caseSensitiveCheckBox.isSelected();
        var wholeWord = wholeWordCheckBox.isSelected();
        var regex = regexCheckBox.isSelected();

        if (regex) {
            try {
                var pattern = java.util.regex.Pattern.compile(searchText,
                    caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE);
                var matcher = pattern.matcher(text);
                while (matcher.find()) {
                    searchMatches.add(new SearchMatch(matcher.start(), matcher.end()));
                }
            } catch (java.util.regex.PatternSyntaxException e) {
                return;
            }
        } else {
            var searchLower = caseSensitive ? searchText : searchText.toLowerCase();
            var textLower = caseSensitive ? text : text.toLowerCase();

            var index = 0;
            while ((index = textLower.indexOf(searchLower, index)) != -1) {
                if (wholeWord) {
                    var beforeOk = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
                    var afterOk = index + searchText.length() >= text.length() ||
                                  !Character.isLetterOrDigit(text.charAt(index + searchText.length()));
                    if (beforeOk && afterOk) {
                        searchMatches.add(new SearchMatch(index, index + searchText.length()));
                    }
                } else {
                    searchMatches.add(new SearchMatch(index, index + searchText.length()));
                }
                index += searchText.length();
            }
        }

        highlightSearchMatches();
        if (!searchMatches.isEmpty()) {
            currentSearchIndex = 0;
            selectSearchMatch(0);
        }
    }

    private void findNext() {
        if (searchMatches.isEmpty()) {
            performSearch();
            return;
        }
        currentSearchIndex = (currentSearchIndex + 1) % searchMatches.size();
        selectSearchMatch(currentSearchIndex);
    }

    private void findPrevious() {
        if (searchMatches.isEmpty()) {
            performSearch();
            return;
        }
        currentSearchIndex = currentSearchIndex - 1;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchMatches.size() - 1;
        }
        selectSearchMatch(currentSearchIndex);
    }

    private void selectSearchMatch(int index) {
        if (index < 0 || index >= searchMatches.size()) {
            return;
        }
        var match = searchMatches.get(index);
        codeArea.selectRange(match.start, match.end);
        codeArea.requestFollowCaret();
    }

    private void highlightSearchMatches() {
        if (!searchBar.isVisible()) {
            return;
        }
        for (var match : searchMatches) {
            codeArea.setStyle(match.start, match.end, List.of("search-highlight"));
        }
    }

    private void clearSearchHighlights() {
        searchMatches.clear();
        currentSearchIndex = -1;
    }

    private void replaceCurrent() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchMatches.size()) {
            return;
        }
        var match = searchMatches.get(currentSearchIndex);
        var replaceText = replaceField.getText();
        if (replaceText == null) {
            replaceText = "";
        }

        codeArea.replaceText(match.start, match.end, replaceText);
        performSearch();

        if (!searchMatches.isEmpty() && currentSearchIndex < searchMatches.size()) {
            selectSearchMatch(currentSearchIndex);
        }
    }

    private void replaceAll() {
        if (searchMatches.isEmpty()) {
            return;
        }

        var replaceText = replaceField.getText();
        if (replaceText == null) {
            replaceText = "";
        }

        for (var i = searchMatches.size() - 1; i >= 0; i--) {
            var match = searchMatches.get(i);
            codeArea.replaceText(match.start, match.end, replaceText);
        }

        performSearch();
    }

}
