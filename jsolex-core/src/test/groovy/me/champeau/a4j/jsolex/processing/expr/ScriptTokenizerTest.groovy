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
package me.champeau.a4j.jsolex.processing.expr

import spock.lang.Specification
import spock.lang.Subject

class ScriptTokenizerTest extends Specification {
    @Subject
    private final ScriptTokenizer tokenizer = new ScriptTokenizer()

    def "parses script"() {
        String script = """
# this is a comment
var = some_expression

// Another comment style
[a section]
var2 = other_expression

avg(range(variable_definition))

img("foo") // with comment
img("foo # invalid expression

   img("indented")
   
   # indented comment
   indentedVar = indented
   
helium_raw = autocrop(img(HeliumShift) - ContinuumCoef*continuum)

[[major-section]]
foo=bar
"""
        when:
        def tokens = tokenizer.tokenize(script)

        then: "parses tokens"
        tokens.each { token ->
            assert token.value() == script.substring(token.start(), token.end())
        }

        def relevantTokens = tokens.findAll {
            it !instanceof ScriptToken.Whitespace
        }

        and:
        relevantTokens[0] instanceof ScriptToken.Comment
        relevantTokens[1] instanceof ScriptToken.VariableDefinition
        relevantTokens[2] instanceof ScriptToken.Comment
        relevantTokens[3] instanceof ScriptToken.Section
        relevantTokens[4] instanceof ScriptToken.VariableDefinition
        relevantTokens[5] instanceof ScriptToken.Expression
        relevantTokens[6] instanceof ScriptToken.Expression
        relevantTokens[7] instanceof ScriptToken.Comment
        relevantTokens[8] instanceof ScriptToken.Invalid
        relevantTokens[9] instanceof ScriptToken.Comment
        relevantTokens[10] instanceof ScriptToken.Expression
        relevantTokens[11] instanceof ScriptToken.Comment
        relevantTokens[12] instanceof ScriptToken.VariableDefinition
        relevantTokens[13] instanceof ScriptToken.VariableDefinition
        relevantTokens[14] instanceof ScriptToken.Section
        relevantTokens[14].isMajor()
        relevantTokens[14].name() == 'major-section'
        relevantTokens[15] instanceof ScriptToken.VariableDefinition
    }
}
