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
package me.champeau.a4j.jsolex.expr;

import me.champeau.a4j.jsolex.expr.ast.Argument;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.BinaryExpression;
import me.champeau.a4j.jsolex.expr.ast.Expression;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.Identifier;
import me.champeau.a4j.jsolex.expr.ast.ImageMathScript;
import me.champeau.a4j.jsolex.expr.ast.NamedArgument;
import me.champeau.a4j.jsolex.expr.ast.NumericalLiteral;
import me.champeau.a4j.jsolex.expr.ast.Section;
import me.champeau.a4j.jsolex.expr.ast.StringLiteral;
import me.champeau.a4j.jsolex.expr.ast.UnaryExpression;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class ExpressionEvaluator {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, UserFunction> userFunctions = new HashMap<>();

    public void putVariable(String name, Object value) {
        variables.put(name, value);
    }

    public void putFunction(String name, UserFunction function) {
        userFunctions.put(name, function);
    }

    public Map<String, UserFunction> getUserFunctions() {
        return Collections.unmodifiableMap(userFunctions);
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public final Object evaluate(Expression expression) {
        return doEvaluate(expression);
    }

    protected Object doEvaluate(Node expression) {
        if (expression instanceof StringLiteral literal) {
            return literal.toString();
        }
        if (expression instanceof NumericalLiteral number) {
            return Double.parseDouble(number.toString());
        }
        if (expression instanceof BinaryExpression binary) {
            var left = doEvaluate(binary.left());
            var right = doEvaluate(binary.right());
            return switch (binary.operator().toString()) {
                case "+" -> plus(left, right);
                case "-" -> minus(left, right);
                case "*" -> mul(left, right);
                case "/" -> div(left, right);
                default -> throw new UnsupportedOperationException("Unknown operator " + binary.operator());
            };
        }
        if (expression instanceof UnaryExpression unary) {
            var operand = doEvaluate(unary.operand());
            var operator = unary.operator();
            if (operator != null && operator.toString().equals("-")) {
                return minus(0, operand);
            }
            return operand;
        }
        if (expression instanceof Assignment assignment) {
            var value = doEvaluate(assignment.expression());
            assignment.variableName().ifPresent(name -> variables.put(name, value));
            return value;
        }
        if (expression instanceof Identifier) {
            return variable(expression.toString());
        }
        if (expression instanceof FunctionCall functionCall) {
            if (functionCall.getBuiltinFunction().isPresent()) {
                var fun = functionCall.getBuiltinFunction().get();
                var args = functionCall.getArguments();
                if (args.stream().allMatch(arg -> arg.getFirst() instanceof Expression)) {
                    // all arguments are expressions
                    return functionCall(fun, fun.mapPositionalArguments(args.stream().map(this::doEvaluate).toList()));
                }
                if (args.stream().allMatch(arg -> arg.getFirst() instanceof NamedArgument)) {
                    var map = args.stream()
                            .map(arg -> (NamedArgument) arg.getFirst())
                            .map(namedArg -> new Object() {
                                private final String name = namedArg.children().getFirst().toString();
                                private final Object value = doEvaluate(namedArg.children().getLast());
                            })
                            .collect(Collectors.toMap(o -> o.name, o -> o.value, (e1, e2) -> e1, LinkedHashMap::new));
                    return functionCall(fun, map);
                }
                throw new RuntimeException("Mixing named and positional arguments is not supported");
            } else {
                var fun = userFunctions.get(functionCall.getFunctionName());
                if (fun != null) {
                    var args = functionCall.getArguments();
                    if (args.stream().allMatch(arg -> arg.getFirst() instanceof Expression)) {
                        var arguments = fun.arguments();
                        var params = IntStream.range(0, arguments.size())
                                .mapToObj(i -> new Object() {
                                    private final String name = arguments.get(i);
                                    private final Object value = doEvaluate(args.get(i));
                                })
                                .collect(Collectors.toMap(o -> o.name, o -> o.value, (e1, e2) -> e1, LinkedHashMap::new));
                        return userFunctionCall(fun, params);
                    }
                    if (args.stream().allMatch(arg -> arg.getFirst() instanceof NamedArgument)) {
                        var map = args.stream()
                                .map(arg -> (NamedArgument) arg.getFirst())
                                .map(namedArg -> new Object() {
                                    private final String name = namedArg.children().getFirst().toString();
                                    private final Object value = doEvaluate(namedArg.children().getLast());
                                })
                                .collect(Collectors.toMap(o -> o.name, o -> o.value, (e1, e2) -> e1, LinkedHashMap::new));
                        return userFunctionCall(fun, map);
                    }
                    throw new RuntimeException("Mixing named and positional arguments is not supported");
                }
            }
            throw new UnsupportedOperationException("Unknown function " + functionCall.getFunctionName());
        }
        if (expression instanceof ImageMathScript) {
            var sections = expression.childrenOfType(Section.class);
            if (sections.size() == 1) {
                return doEvaluate(sections.getFirst());
            }
        }
        if (expression instanceof Section || expression instanceof Expression || expression instanceof Argument) {
            var children = expression.children().stream().filter(Expression.class::isInstance).toList();
            if (children.size() == 1) {
                return doEvaluate(children.getFirst());
            }
            var ids = expression.childrenOfType(Identifier.class);
            if (ids.size() == 1) {
                return variable(ids.getFirst().toString());
            }
        }
        throw new UnsupportedOperationException("Unexpected expression type '" + expression + "'");
    }

    protected abstract Object plus(Object left, Object right);

    protected abstract Object minus(Object left, Object right);

    protected abstract Object mul(Object left, Object right);

    protected abstract Object div(Object left, Object right);

    protected Object variable(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        throw new IllegalStateException("Undefined variable '" + name + "'");
    }

    protected abstract Object functionCall(BuiltinFunction function, Map<String, Object> arguments);

    protected Object userFunctionCall(UserFunction function, Map<String, Object> arguments) {
        return function.invoke(arguments);
    }

    public record NestedInvocationResult(
            Map<String, Object> variables
    ) {

    }
}
