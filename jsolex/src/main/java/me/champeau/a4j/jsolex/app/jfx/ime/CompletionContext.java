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

import me.champeau.a4j.jsolex.expr.ImageMathParser;
import me.champeau.a4j.jsolex.expr.Node;
import me.champeau.a4j.jsolex.expr.ParseException;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.FunctionDef;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CompletionContext {
    private final String text;
    private final int caretPosition;
    private final String partialToken;
    private final ContextType contextType;
    private final FunctionCall currentFunctionCall;
    private final Set<String> knownVariables;
    private final Set<String> userFunctions;

    private CompletionContext(String text, int caretPosition, String partialToken,
                              ContextType contextType, FunctionCall currentFunctionCall,
                              Set<String> knownVariables, Set<String> userFunctions) {
        this.text = text;
        this.caretPosition = caretPosition;
        this.partialToken = partialToken;
        this.contextType = contextType;
        this.currentFunctionCall = currentFunctionCall;
        this.knownVariables = knownVariables;
        this.userFunctions = userFunctions;
    }

    public static CompletionContext analyze(String text, int caretPosition, Path includesDir) {
        var parser = new ImageMathParser(text);
        parser.setParserTolerant(true);
        parser.setIncludeDir(includesDir);

        Node root;
        try {
            parser.parse();
            root = parser.rootNode();
        } catch (ParseException e) {
            root = parser.rootNode();
        }

        var knownVariables = root.descendantsOfType(Assignment.class)
                .stream()
                .map(Assignment::variableName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        var userFunctions = root.childrenOfType(FunctionDef.class)
                .stream()
                .map(FunctionDef::name)
                .collect(Collectors.toSet());

        String partialToken = extractPartialToken(text, caretPosition);
        ContextType contextType = determineContextType(text, caretPosition, root, partialToken);
        Optional<FunctionCall> currentFunctionCall = findCurrentFunctionCall(text, caretPosition, root);

        return new CompletionContext(text, caretPosition, partialToken, contextType,
                currentFunctionCall.orElse(null), knownVariables, userFunctions);
    }

    private static String extractPartialToken(String text, int caretPosition) {
        if (caretPosition == 0 || caretPosition > text.length()) {
            return "";
        }

        int start = caretPosition;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }

        return text.substring(start, caretPosition);
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static ContextType determineContextType(String text, int caretPosition, Node root, String partialToken) {
        if (caretPosition == 0) {
            return ContextType.GENERAL;
        }

        int pos = Math.min(caretPosition, text.length());
        while (pos > 0 && Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }

        if (pos > 0 && text.charAt(pos - 1) == '(') {
            return ContextType.FUNCTION_PARAMETER;
        }

        if (pos > 0 && text.charAt(pos - 1) == ':') {
            return ContextType.NAMED_PARAMETER_VALUE;
        }

        if (pos > 2 && text.substring(pos - 2, pos).equals(": ")) {
            return ContextType.NAMED_PARAMETER_VALUE;
        }

        // Only check for function parameters if we didn't already determine we're in one
        // and we're not at the start of a potential identifier
        if (partialToken.isEmpty() && isInsideFunctionCall(text, caretPosition)) {
            return ContextType.FUNCTION_PARAMETER;
        }

        return ContextType.GENERAL;
    }

    private static boolean isInsideFunctionCall(String text, int caretPosition) {
        int parenDepth = 0;
        for (int i = caretPosition - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')') {
                parenDepth++;
            } else if (c == '(') {
                parenDepth--;
                if (parenDepth < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<FunctionCall> findCurrentFunctionCall(String text, int caretPosition, Node root) {
        var functionCalls = root.descendantsOfType(FunctionCall.class);
        for (var call : functionCalls) {
            int start = call.getBeginOffset();
            int end = call.getEndOffset();
            if (caretPosition >= start && caretPosition <= end) {
                return Optional.of(call);
            }
        }

        // Fallback: try to find function call by parsing backwards from caret
        return findFunctionCallByName(text, caretPosition, root);
    }

    private static Optional<FunctionCall> findFunctionCallByName(String text, int caretPosition, Node root) {
        if (!isInsideFunctionCall(text, caretPosition)) {
            return Optional.empty();
        }

        // Find the opening parenthesis
        int parenPos = -1;
        int parenDepth = 0;
        for (int i = caretPosition - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')') {
                parenDepth++;
            } else if (c == '(') {
                parenDepth--;
                if (parenDepth < 0) {
                    parenPos = i;
                    break;
                }
            }
        }

        if (parenPos == -1) {
            return Optional.empty();
        }

        // Extract function name before the parenthesis
        int nameEnd = parenPos;
        int nameStart = parenPos - 1;
        while (nameStart >= 0 && isIdentifierChar(text.charAt(nameStart))) {
            nameStart--;
        }
        nameStart++;

        if (nameStart >= nameEnd) {
            return Optional.empty();
        }

        String functionName = text.substring(nameStart, nameEnd);

        // Find matching FunctionCall in AST
        final int finalNameStart = nameStart;
        return root.descendantsOfType(FunctionCall.class)
                .stream()
                .filter(call -> call.getFunctionName().equals(functionName))
                .filter(call -> {
                    int start = call.getBeginOffset();
                    int end = call.getEndOffset();
                    return finalNameStart >= start && finalNameStart <= end;
                })
                .findFirst();
    }

    public String getPartialToken() {
        return partialToken;
    }

    public ContextType getContextType() {
        return contextType;
    }

    public Optional<FunctionCall> getCurrentFunctionCall() {
        return Optional.ofNullable(currentFunctionCall);
    }

    public Set<String> getKnownVariables() {
        return knownVariables;
    }

    public Set<String> getUserFunctions() {
        return userFunctions;
    }

    public String getText() {
        return text;
    }

    public int getCaretPosition() {
        return caretPosition;
    }

    public enum ContextType {
        GENERAL,
        FUNCTION_PARAMETER,
        NAMED_PARAMETER_VALUE
    }
}