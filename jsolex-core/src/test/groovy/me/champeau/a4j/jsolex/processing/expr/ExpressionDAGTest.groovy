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

class ExpressionDAGTest extends Specification {

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

    def "builds DAG for independent expressions"() {
        given:
        def script = """
        red=img(7)
        blue=img(-7)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 1
        levels[0].canRunInParallel()
        levels[0].expressions().size() == 2
        levels[0].expressions().collect { it.variableName() } as Set == Set.of("red", "blue")
    }

    def "builds DAG for dependent expressions"() {
        given:
        def script = """
        a=img(5)
        b=img(-5)
        c=min(a,b)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 2
        levels[0].canRunInParallel()
        levels[0].expressions().size() == 2
        levels[0].expressions().collect { it.variableName() } as Set == Set.of("a", "b")

        levels[1].canRunInParallel()
        levels[1].expressions().size() == 1
        levels[1].expressions()[0].variableName() == "c"
    }

    def "builds DAG for chain of dependencies"() {
        given:
        def script = """
        a=img(0)
        b=min(a,a)
        c=max(b,b)
        d=avg(c,c)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 4
        levels.every { it.canRunInParallel() && it.expressions().size() == 1 }
        levels[0].expressions()[0].variableName() == "a"
        levels[1].expressions()[0].variableName() == "b"
        levels[2].expressions()[0].variableName() == "c"
        levels[3].expressions()[0].variableName() == "d"
    }

    def "separates stateful functions into sequential levels"() {
        given:
        def script = """
        dir=workdir('/tmp')
        a=img(5)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 2
        def varNames = levels.collectMany { it.expressions().collect { it.variableName() } } as Set
        varNames == Set.of("dir", "a")

        def statefulLevel = levels.find { !it.canRunInParallel() }
        statefulLevel != null
        statefulLevel.expressions().size() == 1
        statefulLevel.expressions()[0].variableName() == "dir"

        def parallelLevel = levels.find { it.canRunInParallel() }
        parallelLevel != null
        parallelLevel.expressions().size() == 1
        parallelLevel.expressions()[0].variableName() == "a"
    }

    def "handles complex dependency graph"() {
        given:
        def script = """
        red=img(7)
        blue=img(-7)
        green=min(red,blue)
        doppler_raw=rgb(red,green,blue)
        doppler=saturate(doppler_raw,1.5)
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 4
        levels[0].canRunInParallel()
        levels[0].expressions().collect { it.variableName() } as Set == Set.of("red", "blue")

        levels[1].canRunInParallel()
        levels[1].expressions()[0].variableName() == "green"

        levels[2].canRunInParallel()
        levels[2].expressions()[0].variableName() == "doppler_raw"

        levels[3].canRunInParallel()
        levels[3].expressions()[0].variableName() == "doppler"
    }

    def "detects circular dependency"() {
        given:
        def infos = [
                new ExpressionDependencyAnalyzer.DependencyInfo(
                        "a",
                        null,
                        Set.of(),
                        true,
                        false,
                        false,
                        false,
                        ""
                ),
                new ExpressionDependencyAnalyzer.DependencyInfo(
                        "b",
                        null,
                        Set.of("a"),
                        true,
                        false,
                        false,
                        false,
                        ""
                ),
                new ExpressionDependencyAnalyzer.DependencyInfo(
                        "c",
                        null,
                        Set.of("c"),
                        true,
                        false,
                        false,
                        false,
                        ""
                )
        ]

        when:
        def dag = ExpressionDAG.build(infos)
        dag.computeExecutionLevels()

        then:
        thrown(IllegalStateException)
    }

    def "handles non-function-call assignments as sequential"() {
        given:
        def script = """
        a=img(5)
        b=10
        c=a+b
        """
        def assignments = parseAssignments(script)
        def analyzer = new ExpressionDependencyAnalyzer(Set.of())
        def infos = analyzer.analyzeAll(assignments)

        when:
        def dag = ExpressionDAG.build(infos)
        def levels = dag.computeExecutionLevels()

        then:
        levels.size() == 3
        levels[0].canRunInParallel()
        levels[0].expressions()[0].variableName() == "a"

        levels[1].canRunInParallel() == false
        levels[1].expressions()[0].variableName() == "b"

        levels[2].canRunInParallel() == false
        levels[2].expressions()[0].variableName() == "c"
    }
}
