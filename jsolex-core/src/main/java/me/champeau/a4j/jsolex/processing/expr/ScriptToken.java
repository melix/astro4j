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

public sealed interface ScriptToken {
    String value();
    int start();
    int end();
    default int length() {
        return end() - start();
    }

    record Comment(String value, int start, int end) implements ScriptToken {

    }

    record Section(String value, int start, int end) implements ScriptToken {
        public String name() {
            String trim = value.trim();
            return trim.substring(1, trim.length()-1);
        }
    }

    record Variable(String value, int start, int end) implements ScriptToken {
        public String name() {
            return value.trim();
        }
    }

    record Expression(String value, int start, int end, me.champeau.a4j.jsolex.expr.Expression expressionTokens) implements ScriptToken {
        public String expression() {
            return value.trim();
        }
    }

    record VariableDefinition(String value, int start, int end, Variable variable, ScriptToken expression) implements ScriptToken {

    }

    record Whitespace(String value, int start, int end) implements ScriptToken {

    }

    record Invalid(String value, int start, int end) implements ScriptToken {

    }
}
