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

import static me.champeau.a4j.jsolex.expr.BuiltinFunction.AVG
import static me.champeau.a4j.jsolex.expr.BuiltinFunction.CONTINUUM
import static me.champeau.a4j.jsolex.expr.BuiltinFunction.MIN

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
        ".5"       | 0.5d
        "85"       | 85d
        "-85"      | -85d
        '"hello"'  | "hello"
        "+8"       | 8d
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
        expression                                | function  | expected
        'avg(1,2,3)'                              | AVG       | 'AVG(1.0,2.0,3.0)'
        'min(avg(1,2), x)'                        | MIN       | 'MIN(AVG(1.0,2.0),VAR(x))'
        'min(avg(a,b), max(x))'                   | MIN       | 'MIN(AVG(VAR(a),VAR(b)),MAX(VAR(x)))'
        'min(avg(a,b), max(x, y))'                | MIN       | 'MIN(AVG(VAR(a),VAR(b)),MAX(VAR(x),VAR(y)))'
        'min(1,2,3,4, max(5,6))'                  | MIN       | 'MIN(1.0,2.0,3.0,4.0,MAX(5.0,6.0))'
        'avg(min(max(1, avg(2,3,y*y), 4), 5), 6)' | AVG       | 'AVG(MIN(MAX(1.0,AVG(2.0,3.0,VAR(y)*VAR(y)),4.0),5.0),6.0)'
        'continuum()'                             | CONTINUUM | 'CONTINUUM()'
    }

    def "parses complex expression"() {
        when:
        def expr = expressionParser.parseExpression(expression)

        then:
        expr.toString() == expected

        where:
        expression                           | expected
        '()'                                 | "null"
        'continuum()'                        | 'CONTINUUM()'
        '-a'                                 | '(0.0-VAR(a))'
        'a-2'                                | '(VAR(a)-2.0)'
        '(a+b)/2'                            | '(VAR(a)+VAR(b))/2.0'
        '-1*((a-b)/2)'                       | '-1.0*(VAR(a)-VAR(b))/2.0'
        '-1*((a-b)/avg(a,b))'                | '-1.0*(VAR(a)-VAR(b))/AVG(VAR(a),VAR(b))'
        'max(img(a),img(b))'                 | 'MAX(IMG(VAR(a)),IMG(VAR(b)))'
        '1-2'                                | '(1.0-2.0)'
        'a-(2)'                              | '(VAR(a)-2.0)'
        'img(shift) - coef*max(continuum())' | '(IMG(VAR(shift))-VAR(coef)*MAX(CONTINUUM()))'
        'range(-100;-70;10)'                 | 'RANGE(-100.0,-70.0,10.0)'
        '0.5*(-a)'                           | '0.5*(0.0-VAR(a))'
        '.5*(-a)'                            | '0.5*(0.0-VAR(a))'
        '.5*(-(a+b))'                        | '0.5*(0.0-(VAR(a)+VAR(b)))'
        'img(HeliumShift) - continuum()'     | '(IMG(VAR(HeliumShift))-CONTINUUM())'
    }
}
