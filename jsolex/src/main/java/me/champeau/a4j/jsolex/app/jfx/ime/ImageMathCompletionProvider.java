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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ImageMathCompletionProvider implements CompletionProvider {
    private final Path includesDir;
    private final Set<String> knownVariables;

    public ImageMathCompletionProvider(Path includesDir, Set<String> knownVariables) {
        this.includesDir = includesDir;
        this.knownVariables = knownVariables;
    }

    @Override
    public List<CompletionItem> getCompletions(String text, int caretPosition) {
        var context = CompletionContext.analyze(text, caretPosition, includesDir);
        String partial = context.getPartialToken().toLowerCase();
        
        return switch (context.getContextType()) {
            case FUNCTION_PARAMETER -> getParameterCompletions(context, partial);
            case NAMED_PARAMETER_VALUE -> getParameterValueCompletions(context, partial);
            case GENERAL -> getGeneralCompletions(context, partial);
        };
    }

    private List<CompletionItem> getGeneralCompletions(CompletionContext context, String partial) {
        List<CompletionItem> completions = new ArrayList<>();
        
        completions.addAll(getFunctionCompletions(partial));
        completions.addAll(getUserFunctionCompletions(context, partial));
        completions.addAll(getVariableCompletions(context, partial));
        completions.addAll(getKeywordCompletions(partial));
        
        return completions;
    }

    private List<CompletionItem> getFunctionCompletions(String partial) {
        return Arrays.stream(BuiltinFunction.values())
            .map(f -> f.name().toLowerCase())
            .filter(name -> name.startsWith(partial))
            .map(name -> new CompletionItem(name + "(", CompletionType.FUNCTION))
            .sorted((a, b) -> a.text().compareTo(b.text()))
            .toList();
    }

    private List<CompletionItem> getUserFunctionCompletions(CompletionContext context, String partial) {
        return context.getUserFunctions().stream()
            .filter(name -> name.toLowerCase().startsWith(partial))
            .map(name -> new CompletionItem(name + "(", CompletionType.USER_FUNCTION))
            .sorted((a, b) -> a.text().compareTo(b.text()))
            .toList();
    }

    private List<CompletionItem> getVariableCompletions(CompletionContext context, String partial) {
        var systemVariables = Stream.of(
            DefaultImageScriptExecutor.BLACK_POINT_VAR,
            DefaultImageScriptExecutor.ANGLE_P_VAR,
            DefaultImageScriptExecutor.B0_VAR,
            DefaultImageScriptExecutor.L0_VAR,
            DefaultImageScriptExecutor.CARROT_VAR,
            DefaultImageScriptExecutor.DETECTED_WAVELEN,
            DefaultImageScriptExecutor.DETECTED_DISPERSION
        ).filter(name -> name.toLowerCase().startsWith(partial))
        .map(name -> new CompletionItem(name, CompletionType.VARIABLE));

        var userVariables = Stream.concat(
            context.getKnownVariables().stream(),
            knownVariables.stream()
        ).filter(name -> name.toLowerCase().startsWith(partial))
        .map(name -> new CompletionItem(name, CompletionType.VARIABLE));

        return Stream.concat(systemVariables, userVariables)
            .distinct()
            .sorted((a, b) -> a.text().compareTo(b.text()))
            .toList();
    }

    private List<CompletionItem> getKeywordCompletions(String partial) {
        return Stream.of("if", "else", "for", "while", "function", "include")
            .filter(keyword -> keyword.startsWith(partial))
            .map(keyword -> new CompletionItem(keyword, CompletionType.KEYWORD))
            .toList();
    }

    private List<CompletionItem> getParameterCompletions(CompletionContext context, String partial) {
        return context.getCurrentFunctionCall()
            .map(call -> call.getBuiltinFunction()
                .map(bf -> bf.getAllParameterNames().stream()
                    .filter(param -> param.toLowerCase().startsWith(partial))
                    .map(param -> new CompletionItem(param + ": ", CompletionType.PARAMETER))
                    .toList())
                .orElse(getUserFunctionParameterCompletions(call, context)))
            .orElse(tryInferFunctionFromText(context, partial));
    }
    
    private List<CompletionItem> getUserFunctionParameterCompletions(FunctionCall call, CompletionContext context) {
        String functionName = call.getFunctionName();
        return context.getUserFunctions().stream()
            .filter(name -> name.equals(functionName))
            .findFirst()
            .map(name -> List.<CompletionItem>of()) // User functions don't have known parameters
            .orElse(List.of());
    }
    
    private List<CompletionItem> tryInferFunctionFromText(CompletionContext context, String partial) {
        // Try to find function by text analysis when AST parsing fails
        String text = context.getText();
        int pos = context.getCaretPosition() - partial.length();
        
        // Look backwards to find function name before opening parenthesis
        while (pos > 0 && Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }
        
        if (pos > 0 && text.charAt(pos - 1) == '(') {
            // Find function name
            int nameEnd = pos - 1;
            int nameStart = nameEnd - 1;
            while (nameStart >= 0 && (Character.isLetterOrDigit(text.charAt(nameStart)) || text.charAt(nameStart) == '_')) {
                nameStart--;
            }
            nameStart++;
            
            if (nameStart < nameEnd) {
                String functionName = text.substring(nameStart, nameEnd);
                try {
                    var builtinFunction = BuiltinFunction.valueOf(functionName.toUpperCase());
                    return builtinFunction.getAllParameterNames().stream()
                        .filter(param -> param.toLowerCase().startsWith(partial))
                        .map(param -> new CompletionItem(param + ": ", CompletionType.PARAMETER))
                        .toList();
                } catch (IllegalArgumentException e) {
                    // Not a builtin function
                }
            }
        }
        
        return List.of();
    }

    private List<CompletionItem> getParameterValueCompletions(CompletionContext context, String partial) {
        return getVariableCompletions(context, partial);
    }
}