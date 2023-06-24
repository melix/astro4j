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

class ExpressionParserTest extends Specification {
    @Subject
    private final ExpressionParser expressionParser = new ExpressionParser()

    def "parses literal"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr instanceof Literal
        expr.value() == value

        where:
        expression | value
        "1.0"      | 1.0
        "0"        | 0
        "-1"       | -1
        "-1.5"     | -1.5d
        "(-1.5)"   | -1.5d
        "(1.5)"    | 1.5d
    }

    def "parses variable"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr instanceof Variable
        expr.name() == name

        where:
        expression | name
        'x'        | 'x'
        'abc'      | 'abc'
        'ab_cd'    | 'ab_cd'
        '(ABcd09)' | 'ABcd09'
    }

    def "parses binary operators"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr.class.isAssignableFrom(type)
        expr.left.class.isAssignableFrom(left)
        expr.right.class.isAssignableFrom(right)

        where:
        expression    | type           | left           | right
        '1+1'         | Addition       | Literal        | Literal
        '1 + 1'       | Addition       | Literal        | Literal
        '1*1'         | Multiplication | Literal        | Literal
        '1 * 2'       | Multiplication | Literal        | Literal
        '1-1'         | Substraction   | Literal        | Literal
        '2 - 2'       | Substraction   | Literal        | Literal
        '1/2.5'       | Division       | Literal        | Literal
        '3.0 / 1.1'   | Division       | Literal        | Literal
        'a + b'       | Addition       | Variable       | Variable
        'a + (c + d)' | Addition       | Variable       | Addition
        '1 + 2 * 3'   | Addition       | Literal        | Multiplication
        '1 * 2 + 3'   | Addition       | Multiplication | Literal
        '(1 + 2) * 3' | Multiplication | Addition       | Literal
    }

    def "parses function call"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr instanceof FunctionCall
        expr.function() == function
        expr.toString() == expected

        where:
        expression                                | function | expected
        'avg(1,2,3)'                              | 'avg'    | 'avg(1.0,2.0,3.0)'
        'min(avg(1,2), x)'                        | 'min'    | 'min(avg(1.0,2.0),var(x))'
        'min(avg(a,b), max(x))'                   | 'min'    | 'min(avg(var(a),var(b)),max(var(x)))'
        'min(avg(a,b), max(x, y))'                | 'min'    | 'min(avg(var(a),var(b)),max(var(x),var(y)))'
        'min(1,2,3,4, max(5,6))'                  | 'min'    | 'min(1.0,2.0,3.0,4.0,max(5.0,6.0))'
        'avg(min(max(1, avg(2,3,y*y), 4), 5), 6)' | 'avg'    | 'avg(min(max(1.0,avg(2.0,3.0,var(y)*var(y)),4.0),5.0),6.0)'
    }

    def "parses complex expression"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr.toString() == expected

        where:
        expression            | expected
        '(a+b)/2'             | '(var(a)+var(b))/2.0'
        '-1*((a-b)/2)'        | '-1.0*(var(a)-var(b))/2.0'
        '-1*((a-b)/avg(a,b))' | '-1.0*(var(a)-var(b))/avg(var(a),var(b))'
        'max(img(a),img(b))'  | 'max(img(var(a)),img(var(b)))'
    }
}
