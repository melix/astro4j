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

import spock.lang.Specification
import spock.lang.Subject

class ExpressionEvaluatorTest extends Specification {
    @Subject
    private ExpressionEvaluator evaluator;

    def "can evaluate math expressions"() {
        given:
        evaluator = new SimpleMathEvaluator()

        expect:
        evaluator.evaluate(expression) == result

        where:
        expression   | result
        '1+1'        | 2.0
        '2*3'        | 6.0
        '8/2'        | 4.0
        '1+2*5'      | 11.0
        '(1-2)*5'    | -5.0
        'max(1,3)*5' | 15.0
        'min(1,3)*5' | 5.0
        'avg(8,2)'   | 5.0
        'pi/2'       | Math.PI / 2
    }

    def "can evaluate math expressions with variables"() {
        given:
        evaluator = new SimpleMathEvaluator()
        evaluator.putVariable('x', '5')
        evaluator.putVariable('y', '6')

        expect:
        evaluator.evaluate(expression) == result

        where:
        expression | result
        'x+1'      | 6.0
        'x*y'      | 30.0
        'x*x'      | 25.0
    }

    def "variables can be other expressions"() {
        given:
        evaluator = new SimpleMathEvaluator()
        evaluator.putVariable('x', '5')
        evaluator.putVariable('y', 'x+2')

        expect:
        evaluator.evaluate(expression) == result

        where:
        expression | result
        'y'        | 7.0
        'x*y'      | 35.0
    }

    def "variables cannot reference themselves"() {
        given:
        evaluator = new SimpleMathEvaluator()
        evaluator.putVariable('y', 'y+2')

        when:
        evaluator.evaluate('y')

        then:
        StackOverflowError ex = thrown()
    }

    def "handles undefined variables"() {
        given:
        evaluator = new SimpleMathEvaluator()

        when:
        evaluator.evaluate('unknown')

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
}
