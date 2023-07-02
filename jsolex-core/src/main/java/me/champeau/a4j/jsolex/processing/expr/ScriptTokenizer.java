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

import me.champeau.a4j.jsolex.expr.ExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ScriptTokenizer {
    private static final String COMMENT = "\\s*(#|//).*$";
    private static final Pattern COMMENT_PATTERN = Pattern.compile(COMMENT);
    private static final String SECTION = "\\s*\\[.+?\\]";
    private static final Pattern SECTION_PATTERN = Pattern.compile(SECTION);
    private static final String LINE = "(?:(\\s*[a-zA-Z_]\\w*)\\s*=\\s*)?(.*)";
    private static final Pattern LINE_PATTERN = Pattern.compile(LINE);
    private static final Pattern PATTERN = Pattern.compile(
            "(?m)(" + COMMENT + ")|(" + SECTION + ")|(" + LINE + ")"
    );

    private final ExpressionParser expressionParser = new ExpressionParser();

    public List<ScriptToken> tokenize(String script) {
        var tokens = new ArrayList<ScriptToken>();
        var matcher = PATTERN.matcher(script);
        int pEnd = -1;
        while (matcher.find()) {
            String token = matcher.group();
            if (token.isEmpty()) {
                continue;
            }
            int start = matcher.start();
            if (start>0 && start > pEnd) {
                if (pEnd == -1) {
                    tokens.addAll(asToken(script, 0, start));
                } else {
                    tokens.addAll(asToken(script, pEnd, start));
                }
            }
            var tokenMatcher = COMMENT_PATTERN.matcher(token);
            if (tokenMatcher.matches()) {
                tokens.add(new ScriptToken.Comment(tokenMatcher.group(), start + tokenMatcher.start(), start + tokenMatcher.end()));
            } else {
                tokenMatcher = SECTION_PATTERN.matcher(token);
                if (tokenMatcher.matches()) {
                    tokens.add(new ScriptToken.Section(tokenMatcher.group(), start + tokenMatcher.start(), start + tokenMatcher.end()));
                } else {
                    tokenMatcher = LINE_PATTERN.matcher(token);
                    if (tokenMatcher.matches()) {
                        String variable = matcher.group(5);
                        if (variable != null) {
                            var subtokens = asToken(
                                    script,
                                    matcher.start(6),
                                    matcher.end(6)
                            );
                            tokens.add(new ScriptToken.VariableDefinition(
                                    tokenMatcher.group(),
                                    start + tokenMatcher.start(),
                                    start + tokenMatcher.end(),
                                    new ScriptToken.Variable(
                                            variable,
                                            matcher.start(5),
                                            matcher.end(5)
                                    ),
                                    subtokens.get(0)
                            ));
                            if (subtokens.size() == 2) {
                                tokens.add(subtokens.get(1));
                            }
                        } else {
                            tokens.addAll(asToken(
                                    script,
                                    start + tokenMatcher.start(),
                                    start + tokenMatcher.end()
                            ));
                        }
                    }
                }
            }
            pEnd = matcher.end();
        }
        return tokens;
    }

    private List<ScriptToken> asToken(String text, int start, int end) {
        String substring = text.substring(start, end);
        if (substring.replaceAll("\\s*|\r|\n", "").isEmpty()) {
            return List.of(new ScriptToken.Whitespace(substring, start, end));
        }
        var matcher = COMMENT_PATTERN.matcher(substring);
        if (matcher.find()) {
            int commentStart = matcher.start();
            return List.of(
                    expressionToken(start, start + commentStart, substring.substring(0, commentStart)),
                    new ScriptToken.Comment(substring.substring(commentStart), start + commentStart, end)
            );
        } else {
            return List.of(expressionToken(start, end, substring));
        }
    }

    private ScriptToken expressionToken(int start, int end, String substring) {
        try {
            var expression = expressionParser.parseExpression(substring);
            return new ScriptToken.Expression(substring, start, end, expression);
        } catch (Exception ex) {
            return new ScriptToken.Invalid(substring, start, end);
        }
    }
}
