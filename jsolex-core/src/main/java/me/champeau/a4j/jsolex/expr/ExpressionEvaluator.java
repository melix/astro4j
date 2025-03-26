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

import me.champeau.a4j.jsolex.expr.ast.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                return functionCall(fun, functionCall.getArguments().stream().map(this::doEvaluate).toList());
            } else {
                var fun = userFunctions.get(functionCall.getFunctionName());
                if (fun != null) {
                    return userFunctionCall(fun, functionCall.getArguments().stream().map(this::doEvaluate).toArray());
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

    protected abstract Object functionCall(BuiltinFunction function, List<Object> arguments);

    protected Object userFunctionCall(UserFunction function, Object[] arguments) {
        return function.invoke(arguments);
    }

    public record NestedInvocationResult(
            Map<String, Object> variables
    ) {

    }
}
