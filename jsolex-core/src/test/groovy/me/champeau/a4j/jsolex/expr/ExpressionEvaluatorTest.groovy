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
package me.champeau.a4j.jsolex.expr

import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor
import spock.lang.Specification
import spock.lang.Subject

class ExpressionEvaluatorTest extends Specification {
    @Subject
    private ExpressionEvaluator evaluator;

    def "can evaluate math expressions"() {
        given:
        evaluator = new SimpleMathEvaluator()

        expect:
        eval(expression) == result

        where:
        expression    | result
        '1+1'         | 2.0
        '1+1+1'       | 3.0
        '1+1+1+2'     | 5.0
        '1-1'         | 0.0
        '2*3'         | 6.0
        '1+2*3'       | 7.0
        '(1+2)*3'     | 9.0
        '1+(2*3)'     | 7.0
        '2*3+1'       | 7.0
        '8/2'         | 4.0
        '1+2*5'       | 11.0
        '(1-2)*5'     | -5.0
        '1+1+2*2'     | 6.0
        '1+3*(1+2*2)' | 16.0
        '2*3*4'       | 24.0
        'max(1,3)*5'  | 15.0
        'min(1,3)*5'  | 5.0
        'avg(8,2)'    | 5.0
        '+2'          | 2.0
        '-2'          | -2.0
        '-(2*3)'      | -6.0
        'pi/2'        | Math.PI / 2
    }

    def "can evaluate math expressions with variables"() {
        given:
        evaluator = new SimpleMathEvaluator()
        evaluator.putVariable('x', 5)
        evaluator.putVariable('y', 6)

        expect:
        eval(expression) == result

        where:
        expression | result
        'x+1'      | 6.0
        'x*y'      | 30.0
        'x*x'      | 25.0
    }

    def "handles undefined variables"() {
        given:
        evaluator = new SimpleMathEvaluator()

        when:
        eval('unknown')

        then:
        IllegalStateException ex = thrown()
        ex.message == "Undefined variable 'unknown'"
    }

    private static class SimpleMathEvaluator extends ExpressionEvaluator {

        @Override
        protected Object plus(Object left, Object right) {
            left + right
        }

        @Override
        protected Object minus(Object left, Object right) {
            left - right
        }

        @Override
        protected Object mul(Object left, Object right) {
            left * right
        }

        @Override
        protected Object div(Object left, Object right) {
            left / right
        }

        @Override
        protected Object variable(String name) {
            if ("pi" == name) {
                return Math.PI;
            }
            super.variable(name);
        }

        @Override
        protected Object functionCall(BuiltinFunction fun, List<Object> arguments) {
            return switch (fun) {
                case BuiltinFunction.MIN -> arguments.min()
                case BuiltinFunction.MAX -> arguments.max()
                case BuiltinFunction.AVG -> arguments.average()
            }
        }
    }

    private Object eval(String expr) {
        var parser = new ImageMathParser(expr)
        evaluator.evaluate(parser.parseAndInlineIncludes().findSections(ImageMathScriptExecutor.SectionKind.ALL).getFirst().children().getFirst())
    }
}
