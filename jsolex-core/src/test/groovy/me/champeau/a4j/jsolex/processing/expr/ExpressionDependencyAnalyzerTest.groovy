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

import me.champeau.a4j.jsolex.expr.ImageMathParser
import me.champeau.a4j.jsolex.expr.ast.Assignment
import me.champeau.a4j.jsolex.expr.ast.Expression
import spock.lang.Specification
import spock.lang.Subject

class ExpressionDependencyAnalyzerTest extends Specification {

    private static List<Assignment> parseAssignments(String script) {
        def parser = new ImageMathParser(script)
        def root = parser.parseAndInlineIncludes()
        def sections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        if (sections.isEmpty()) {
            return []
        }
        return sections.collectMany { section ->
            section.childrenOfType(Expression).findAll { it instanceof Assignment }.collect { it as Assignment }
        }
    }

    def "extracts variable name from assignment"() {
        given:
        def script = "red=img(7)"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def varName = analyzer.getVariableName(assignment)

        then:
        varName == "red"
    }

    def "finds no dependencies for simple function call"() {
        given:
        def script = "red=img(7)"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def deps = analyzer.findDependencies(assignment)

        then:
        deps.isEmpty()
    }

    def "finds dependencies on other variables"() {
        given:
        def script = """
        a=img(5)
        b=img(-5)
        c=min(a,b)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        analyzer.registerVariable("a")
        analyzer.registerVariable("b")
        def deps = analyzer.findDependencies(assignments[2])

        then:
        deps == Set.of("a", "b")
    }

    def "filters out parameter references"() {
        given:
        def script = """
        [params]
        pixelShift=7

        red=img(pixelShift)
        """
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of("pixelShift"))

        when:
        def deps = analyzer.findDependencies(assignment)

        then:
        deps.isEmpty()
    }

    def "filters out function names"() {
        given:
        def script = "red=img(5)"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def deps = analyzer.findDependencies(assignment)

        then:
        !deps.contains("img")
    }

    def "detects function call in assignment"() {
        given:
        def script = "red=img(7)"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def hasFunctionCall = analyzer.containsFunctionCall(assignment)

        then:
        hasFunctionCall
    }

    def "detects no function call in simple assignment"() {
        given:
        def script = """
        a=5
        """
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def hasFunctionCall = analyzer.containsFunctionCall(assignment)

        then:
        !hasFunctionCall
    }

    def "analyzes all assignments and builds dependency info"() {
        given:
        def script = """
        a=img(5)
        b=img(-5)
        c=min(a,b)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def infos = analyzer.analyzeAll(assignments)

        then:
        infos.size() == 3
        infos[0].variableName() == "a"
        infos[0].dependencies().isEmpty()
        infos[0].hasFunctionCall()

        infos[1].variableName() == "b"
        infos[1].dependencies().isEmpty()
        infos[1].hasFunctionCall()

        infos[2].variableName() == "c"
        infos[2].dependencies() == Set.of("a", "b")
        infos[2].hasFunctionCall()
    }

    def "detects stateful function with side effects"() {
        given:
        def script = "workdir('/tmp')"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def hasStatefulFunction = analyzer.hasStatefulFunction(assignment)

        then:
        hasStatefulFunction
    }

    def "detects no stateful function in pure function call"() {
        given:
        def script = "red=img(7)"
        def assignments = parseAssignments(script)
        def assignment = assignments.first()
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())

        when:
        def hasStatefulFunction = analyzer.hasStatefulFunction(assignment)

        then:
        !hasStatefulFunction
    }
}
