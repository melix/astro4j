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
import java.util.Deque;
import java.util.List;

public class ExpressionParser {

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
                        outputQueue.add(new Token(TokenType.LITERAL, String.valueOf(argCount)));
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
                case LITERAL -> queue.push(new Literal(Double.parseDouble(token.value())));
                case VARIABLE -> queue.push(new Variable(token.value()));
                case FUNCTION -> {
                    int argCount = ((Literal) queue.pop()).value().intValue();
                    var argList = new ArrayList<Expression>();
                    for (int i=0; i<argCount; i++) {
                        argList.add(0, queue.pop());
                    }
                    var fun = new FunctionCall(token.value(), argList);
                    queue.push(fun);
                }
                case OPERATOR -> {
                    var right = queue.pop();
                    var left = queue.pop();
                    switch (token.value()) {
                        case "+" -> queue.push(new Addition(left, right));
                        case "-" -> queue.push(new Substraction(left, right));
                        case "*" -> queue.push(new Multiplication(left, right));
                        case "/" -> queue.push(new Division(left, right));
                    }
                }
                default -> {
                }
            }
        }
        if (queue.size() != 1) {
            throw new IllegalStateException("Unexpected expression result : " + queue);
        }
        return queue.pop();
    }

    private static int operatorPrecedenceOf(Token operatorToken) {
        return switch (operatorToken.value()) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }
}
