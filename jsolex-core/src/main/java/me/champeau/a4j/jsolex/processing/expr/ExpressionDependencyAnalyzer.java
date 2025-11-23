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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.Expression;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpressionDependencyAnalyzer {
    private final Set<String> parameterNames;
    private final Set<String> definedVariables;

    public ExpressionDependencyAnalyzer(Set<String> parameterNames) {
        this.parameterNames = Set.copyOf(parameterNames);
        this.definedVariables = new HashSet<>();
    }

    public String getVariableName(Assignment assignment) {
        return assignment.variableName().orElse("");
    }

    public Set<String> findDependencies(Assignment assignment) {
        var expression = assignment.expression();
        var identifiers = expression.descendantsOfType(Identifier.class);
        var functionNames = extractFunctionNames(expression);

        return identifiers.stream()
                .map(Object::toString)
                .filter(name -> !functionNames.contains(name))
                .filter(name -> !parameterNames.contains(name))
                .filter(name -> definedVariables.contains(name))
                .collect(Collectors.toSet());
    }

    public void registerVariable(String variableName) {
        definedVariables.add(variableName);
    }

    public boolean containsFunctionCall(Assignment assignment) {
        var expression = assignment.expression();
        return !expression.descendantsOfType(FunctionCall.class).isEmpty();
    }

    public boolean hasStatefulFunction(Assignment assignment) {
        var expression = assignment.expression();
        var functionCalls = expression.descendantsOfType(FunctionCall.class);

        return functionCalls.stream()
                .map(FunctionCall::getBuiltinFunction)
                .flatMap(Optional::stream)
                .anyMatch(BuiltinFunction::hasSideEffect);
    }

    public boolean hasNonConcurrentFunction(Assignment assignment) {
        var expression = assignment.expression();
        var functionCalls = expression.descendantsOfType(FunctionCall.class);

        return functionCalls.stream()
                .map(FunctionCall::getBuiltinFunction)
                .flatMap(Optional::stream)
                .anyMatch(fun -> !fun.isConcurrent());
    }

    public boolean hasParallelFunctionArguments(Assignment assignment) {
        var expression = assignment.expression();
        var functionCalls = expression.descendantsOfType(FunctionCall.class);

        return functionCalls.stream().anyMatch(fc -> {
            var args = fc.getArguments();
            if (args.size() <= 1) {
                return false;
            }
            var hasMultipleFunctionArgs = args.stream().anyMatch(arg -> arg.getFirst() instanceof FunctionCall);
            if (!hasMultipleFunctionArgs) {
                return false;
            }
            var hasNonConcurrentInArgs = args.stream()
                    .anyMatch(arg -> containsNonConcurrentFunction(arg.getFirst()));
            return !hasNonConcurrentInArgs;
        });
    }

    private boolean containsNonConcurrentFunction(Object node) {
        if (node instanceof Expression expr) {
            var functionCalls = expr.descendantsOfType(FunctionCall.class);
            return functionCalls.stream()
                    .map(FunctionCall::getBuiltinFunction)
                    .flatMap(Optional::stream)
                    .anyMatch(fun -> !fun.isConcurrent());
        }
        return false;
    }

    private Set<String> extractFunctionNames(Expression expression) {
        var functionCalls = expression.descendantsOfType(FunctionCall.class);
        return functionCalls.stream()
                .map(FunctionCall::getFunctionName)
                .collect(Collectors.toSet());
    }

    public record DependencyInfo(
            String variableName,
            Assignment assignment,
            Set<String> dependencies,
            boolean hasFunctionCall,
            boolean hasStatefulFunction,
            boolean hasNonConcurrentFunction,
            boolean hasParallelFunctionArguments,
            String sectionName
    ) {
    }

    public DependencyInfo analyze(Assignment assignment, String sectionName) {
        var variableName = getVariableName(assignment);
        var dependencies = findDependencies(assignment);
        var hasFunctionCall = containsFunctionCall(assignment);
        var hasStatefulFunction = hasStatefulFunction(assignment);
        var hasNonConcurrent = hasNonConcurrentFunction(assignment);
        var hasParallelArgs = hasParallelFunctionArguments(assignment);

        registerVariable(variableName);

        return new DependencyInfo(
                variableName,
                assignment,
                dependencies,
                hasFunctionCall,
                hasStatefulFunction,
                hasNonConcurrent,
                hasParallelArgs,
                sectionName
        );
    }

    public DependencyInfo analyze(Assignment assignment) {
        return analyze(assignment, "");
    }

    public List<DependencyInfo> analyzeAll(List<Assignment> assignments) {
        return assignments.stream()
                .map(this::analyze)
                .toList();
    }
}
