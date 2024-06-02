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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class ExpressionParser {

    public static final Literal ZERO = new Literal(0.0d, List.of());
    private final Scanner scanner = new Scanner();

    public Expression parseExpression(String expression) {
        return parse(scanner.scan(expression));
    }

    private Expression parse(List<Token> tokens) {
        var outputQueue = new ArrayDeque<Token>();
        var operatorStack = new ArrayDeque<Token>();
        var argumentCountStack = new ArrayDeque<Integer>();
        for (Token token : tokens) {
            switch (token.type()) {
                case VARIABLE, LITERAL -> outputQueue.add(token);
                case FUNCTION -> operatorStack.push(token);
                case UNARY_OPERATOR -> operatorStack.push(token);
                case OPERATOR -> {
                    while (!operatorStack.isEmpty() &&
                           operatorStack.peek().type() == TokenType.OPERATOR &&
                           operatorPrecedenceOf(token) <= operatorPrecedenceOf(operatorStack.peek())) {
                        outputQueue.add(operatorStack.pop());
                    }
                    operatorStack.push(token);
                }
                case COMMA -> {
                    var cpt = argumentCountStack.pop();
                    while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PARENTHESIS) {
                        outputQueue.add(operatorStack.pop());
                    }
                    argumentCountStack.push(cpt + 1);
                    if (operatorStack.isEmpty() || operatorStack.peek().type() != TokenType.LEFT_PARENTHESIS) {
                        throw new IllegalArgumentException("Mismatched parentheses or misplaced comma.");
                    }
                }
                case LEFT_PARENTHESIS -> {
                    operatorStack.push(token);
                    argumentCountStack.push(1);
                }
                case RIGHT_PARENTHESIS -> {
                    var argCount = argumentCountStack.pop();
                    while (!operatorStack.isEmpty() && operatorStack.peek().type() != TokenType.LEFT_PARENTHESIS) {
                        outputQueue.add(operatorStack.pop());
                    }
                    if (operatorStack.isEmpty() || operatorStack.peek().type() != TokenType.LEFT_PARENTHESIS) {
                        throw new IllegalArgumentException("Mismatched parentheses.");
                    }
                    operatorStack.pop();
                    if (!operatorStack.isEmpty() && operatorStack.peek().type() == TokenType.FUNCTION) {
                        outputQueue.add(new Token(TokenType.LITERAL, String.valueOf(argCount), -1, -1));
                        outputQueue.add(operatorStack.pop());
                    }
                }
            }
        }

        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek().type() == TokenType.LEFT_PARENTHESIS || operatorStack.peek().type() == TokenType.RIGHT_PARENTHESIS) {
                throw new IllegalArgumentException("Mismatched parentheses.");
            }
            outputQueue.add(operatorStack.pop());
        }

        return toExpression(outputQueue);
    }

    private Expression toExpression(Deque<Token> tokens) {
        Deque<Expression> queue = new ArrayDeque<>();
        for (Token token : tokens) {
            switch (token.type()) {
                case LITERAL -> queue.push(toLiteral(token));
                case VARIABLE -> queue.push(new Variable(token.value(), List.of(token)));
                case FUNCTION -> {
                    int argCount = ((Number)((Literal) queue.pop()).value()).intValue();
                    List<Token> funTokens = new ArrayList<>(argCount + 1);
                    funTokens.add(token);
                    List<Expression> argList = new ArrayList<>();
                    for (int i=0; i<argCount; i++) {
                        var arg = queue.pop();
                        argList.add(0, arg);
                        funTokens.addAll(arg.tokens());
                    }
                    argList = argList.stream().filter(e -> !(e instanceof Literal l && l.value() == null)).toList();
                    var fun = new FunctionCall(BuiltinFunction.of(token.value()), argList, Collections.unmodifiableList(funTokens));
                    queue.push(fun);
                }
                case UNARY_OPERATOR -> {
                    if (token.value().equals("-")) {
                        var operand = queue.pop();
                        List<Token> allTokens = new ArrayList<>();
                        allTokens.add(token);
                        allTokens.addAll(operand.tokens());
                        queue.push(new Substraction(new Literal(0d, List.of()), operand, Collections.unmodifiableList(allTokens)));
                    }
                }
                case OPERATOR -> {
                        var right = queue.pop();
                        List<Token> allTokens = new ArrayList<>();
                        allTokens.addAll(right.tokens());
                        if (queue.isEmpty()) {
                            // unary operator
                            switch (token.value()) {
                                case "+" -> queue.push(right);
                                case "-" -> queue.push(new Substraction(ZERO, right, Collections.unmodifiableList(allTokens)));
                                default -> throw new IllegalStateException("Illegal operation '" + token.value() + "' : not enough operands on stack");
                            }
                        } else {
                            var left = queue.pop();
                            allTokens.addAll(left.tokens());
                            switch (token.value()) {
                                case "+" -> queue.push(new Addition(left, right, Collections.unmodifiableList(allTokens)));
                                case "-" -> queue.push(new Substraction(left, right, Collections.unmodifiableList(allTokens)));
                                case "*" -> queue.push(new Multiplication(left, right, Collections.unmodifiableList(allTokens)));
                                case "/" -> queue.push(new Division(left, right, Collections.unmodifiableList(allTokens)));
                            }
                        }
                }
                default -> {
                }
            }
        }
        if (queue.size() > 1) {
            throw new IllegalStateException("Unexpected expression result : " + queue);
        }
        if (queue.isEmpty()) {
            return null;
        }
        return queue.pop();
    }

    private static Literal toLiteral(Token token) {
        var value = token.value();
        if (value == null) {
            return new Literal(null, List.of(token));
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return new Literal(value.substring(1, value.length() - 1), List.of(token));
        }
        return new Literal(Double.parseDouble(value), List.of(token));
    }

    private static int operatorPrecedenceOf(Token operatorToken) {
        return switch (operatorToken.value()) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }
}
