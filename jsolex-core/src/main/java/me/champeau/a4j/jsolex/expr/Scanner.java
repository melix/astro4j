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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Scanner {
    public static final String VARIABLE_REGEX = "[a-zA-Z_]\\w*";
    public static final String LITERAL_REGEX = "(-?\\d+(\\.\\d+)?|(\\.\\d+))|(\"[^\"]*\")";
    public static final String OPERATOR_REGEX = "[+\\-*/]";
    public static final String UNARY_OPERATOR_REGEX = "[+\\-]";
    public static final String FUNCTION_REGEX = Stream.concat(
            Arrays.stream(BuiltinFunction.values()).map(BuiltinFunction::name),
            Arrays.stream(BuiltinFunction.values()).map(BuiltinFunction::lowerCaseName)
    ).collect(Collectors.joining("|"));
    public static final String LEFT_PARENTHESIS_REGEX = "\\(";
    public static final String RIGHT_PARENTHESIS_REGEX = "\\)";
    public static final String COMMA_REGEX = "[,;]";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\s*(" + VARIABLE_REGEX + ")|(" + LITERAL_REGEX + ")|(" + OPERATOR_REGEX + ")|(" + UNARY_OPERATOR_REGEX + ")|(" + FUNCTION_REGEX + ")|(" + LEFT_PARENTHESIS_REGEX + ")|(" + RIGHT_PARENTHESIS_REGEX + ")|(" + COMMA_REGEX + ")\\s*"
    );

    public List<Token> scan(String input) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        Token previousToken = null;
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String value = matcher.group().trim();
            TokenType type = determineTokenType(value);

            // Skip empty tokens
            if (value.isEmpty()) {
                continue;
            }

            var token = new Token(type, value, start, end);
            if (previousToken != null && !isMinusSeparator(previousToken) && token.type() == TokenType.LITERAL) {
                if (token.value().startsWith("-")) {
                    // 1-1 is not (1, -1) but (1, -, 1)
                    tokens.add(new Token(TokenType.OPERATOR, "-", start, start + 1));
                    var fixedToken = new Token(TokenType.LITERAL, token.value().substring(1), start + 1, end);
                    tokens.add(fixedToken);
                    previousToken = fixedToken;
                } else if (token.value().startsWith("+")) {
                    // Handle unary plus
                    var fixedToken = new Token(TokenType.LITERAL, token.value().substring(1), start + 1, end);
                    tokens.add(fixedToken);
                    previousToken = fixedToken;
                } else {
                    tokens.add(token);
                    previousToken = token;
                }
            } else if (token.type() == TokenType.OPERATOR && token.value().equals("-") && isUnaryOperator(previousToken)) {
                // Handle unary minus
                var fixedToken = new Token(TokenType.UNARY_OPERATOR, "-", start, end);
                tokens.add(fixedToken);
                previousToken = fixedToken;
            } else {
                tokens.add(token);
                previousToken = token;
            }
        }

        return tokens;
    }

    private static boolean isMinusSeparator(Token previousToken) {
        var value = previousToken.value();
        return value.equals("(") || value.equals(";") || value.equals(",");
    }

    private static boolean isUnaryOperator(Token previousToken) {
        if (previousToken == null) {
            return true;
        }

        var value = previousToken.value();
        return value.matches(LEFT_PARENTHESIS_REGEX) || value.matches(OPERATOR_REGEX);
    }

    private TokenType determineTokenType(String tokenValue) {
        if (tokenValue.matches(LITERAL_REGEX)) {
            return TokenType.LITERAL;
        } else if (tokenValue.matches(OPERATOR_REGEX)) {
            return TokenType.OPERATOR;
        } else if (tokenValue.matches(UNARY_OPERATOR_REGEX)) {
            return TokenType.UNARY_OPERATOR;
        } else if (tokenValue.matches(FUNCTION_REGEX)) {
            return TokenType.FUNCTION;
        } else if (tokenValue.matches(COMMA_REGEX)) {
            return TokenType.COMMA;
        } else if (tokenValue.matches(LEFT_PARENTHESIS_REGEX)) {
            return TokenType.LEFT_PARENTHESIS;
        } else if (tokenValue.matches(RIGHT_PARENTHESIS_REGEX)) {
            return TokenType.RIGHT_PARENTHESIS;
        } else if (tokenValue.matches(VARIABLE_REGEX)) {
            return TokenType.VARIABLE;
        } else {
            throw new IllegalArgumentException("Unknown token type: " + tokenValue);
        }
    }
}
