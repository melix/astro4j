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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scanner {
    private static final String VARIABLE_REGEX = "[a-zA-Z_]\\w*";
    private static final String LITERAL_REGEX = "-?\\d+(\\.\\d+)?";
    private static final String OPERATOR_REGEX = "\\+|\\-|\\*|\\/"; // Add more operators as needed
    private static final String FUNCTION_REGEX = "max|min|avg|img|range|invert"; // Add more operators as needed
    private static final String LEFT_PARENTHESIS_REGEX = "\\(";
    private static final String RIGHT_PARENTHESIS_REGEX = "\\)";
    private static final String COMMA_REGEX = "[,;]";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\s*(" + VARIABLE_REGEX + ")|(" + LITERAL_REGEX + ")|(" + OPERATOR_REGEX + ")|(" + FUNCTION_REGEX + ")|(" + LEFT_PARENTHESIS_REGEX + ")|(" + RIGHT_PARENTHESIS_REGEX + ")|(" + COMMA_REGEX + ")\\s*"
            );

    public List<Token> scan(String input) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        Token previousToken = null;
        while (matcher.find()) {
            String value = matcher.group().trim();
            TokenType type = determineTokenType(value);

            // Skip empty tokens
            if (value.isEmpty()) {
                continue;
            }

            var token = new Token(type, value);
            if (previousToken != null && previousToken.type() == TokenType.LITERAL && token.type() == TokenType.LITERAL) {
                if (token.value().startsWith("-")) {
                    // 1-1 is not (1, -1) but (1, -, 1)
                    tokens.add(new Token(TokenType.OPERATOR, "-"));
                    var fixedToken = new Token(TokenType.LITERAL, token.value().substring(1));
                    tokens.add(fixedToken);
                    previousToken = fixedToken;
                } else {
                    tokens.add(token);
                    previousToken = token;
                }
            } else {
                tokens.add(token);
                previousToken = token;
            }
        }

        return tokens;
    }

    private TokenType determineTokenType(String tokenValue) {
        if (tokenValue.matches(LITERAL_REGEX)) {
            return TokenType.LITERAL;
        } else if (tokenValue.matches(OPERATOR_REGEX)) {
            return TokenType.OPERATOR;
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
