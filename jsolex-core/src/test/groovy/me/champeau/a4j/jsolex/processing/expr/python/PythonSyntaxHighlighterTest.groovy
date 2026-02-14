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
package me.champeau.a4j.jsolex.processing.expr.python

import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PythonSyntaxHighlighterTest extends Specification {

    @Subject
    PythonSyntaxHighlighter highlighter = new PythonSyntaxHighlighter()

    def setup() {
        // Wait for context to be ready before running tests
        if (!highlighter.isReady()) {
            def latch = new CountDownLatch(1)
            highlighter.setOnContextReady { latch.countDown() }
            highlighter.warmUp()
            latch.await(60, TimeUnit.SECONDS)
        }
    }

    def "highlights Python keywords"() {
        given:
        def code = "def foo():\n    if True:\n        return None"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "def")
        hasSpan(spans, "keyword", code, "if")
        hasSpan(spans, "keyword", code, "True")
        hasSpan(spans, "keyword", code, "return")
        hasSpan(spans, "keyword", code, "None")
    }

    def "highlights Python comments"() {
        given:
        def code = "x = 1  # this is a comment"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "comment", code, "# this is a comment")
    }

    def "highlights string literals"() {
        given:
        def code = 'name = "hello world"'

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "token_literal", code, '"hello world"')
    }

    def "highlights number literals"() {
        given:
        def code = "x = 42\ny = 3.14"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "token_literal", code, "42")
        hasSpan(spans, "token_literal", code, "3.14")
    }

    def "highlights builtin functions"() {
        given:
        def code = "print(len(items))"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "token_function", code, "print")
        hasSpan(spans, "token_function", code, "len")
    }

    def "handles empty code"() {
        when:
        def spans = highlighter.computeHighlighting("")

        then:
        spans.isEmpty()
    }

    def "handles null code"() {
        when:
        def spans = highlighter.computeHighlighting(null)

        then:
        spans.isEmpty()
    }

    def "handles syntax errors gracefully"() {
        given:
        def code = "def foo(\n    # incomplete"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        noExceptionThrown()
        hasSpan(spans, "keyword", code, "def")
    }

    def "highlights variable names"() {
        given:
        def code = "my_var = other_var"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "token_variable", code, "my_var")
        hasSpan(spans, "token_variable", code, "other_var")
    }

    def "highlights class definitions"() {
        given:
        def code = "class MyClass:\n    pass"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "class")
        hasSpan(spans, "keyword", code, "pass")
    }

    def "highlights for loop"() {
        given:
        def code = "for i in range(10):\n    break"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "for")
        hasSpan(spans, "keyword", code, "in")
        hasSpan(spans, "keyword", code, "break")
        hasSpan(spans, "token_function", code, "range")
    }

    def "highlights while loop with else"() {
        given:
        def code = "while True:\n    continue\nelse:\n    pass"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "while")
        hasSpan(spans, "keyword", code, "True")
        hasSpan(spans, "keyword", code, "continue")
        hasSpan(spans, "keyword", code, "else")
        hasSpan(spans, "keyword", code, "pass")
    }

    def "highlights try/except/finally"() {
        given:
        def code = "try:\n    pass\nexcept:\n    pass\nfinally:\n    pass"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "try")
        hasSpan(spans, "keyword", code, "except")
        hasSpan(spans, "keyword", code, "finally")
    }

    def "highlights import statements"() {
        given:
        def code = "import jsolex\nfrom math import sqrt"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "import")
        hasSpan(spans, "keyword", code, "from")
    }

    def "highlights lambda expressions"() {
        given:
        def code = "f = lambda x: x * 2"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "lambda")
    }

    def "highlights async/await keywords"() {
        given:
        def code = "async def foo():\n    await bar()"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "keyword", code, "async")
        hasSpan(spans, "keyword", code, "def")
        hasSpan(spans, "keyword", code, "await")
    }

    def "highlights method calls as functions"() {
        given:
        def code = "obj.method()\nfoo(x)\nbar()"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        hasSpan(spans, "token_variable", code, "obj")
        hasSpan(spans, "token_function", code, "method")
        hasSpan(spans, "token_function", code, "foo")
        hasSpan(spans, "token_function", code, "bar")
        hasSpan(spans, "token_variable", code, "x")
    }

    def "spans are sorted by position"() {
        given:
        def code = "x = 1\ny = 2"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        spans.size() >= 2
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i].start() >= spans[i-1].start()
        }
    }

    def "spans do not overlap"() {
        given:
        def code = "def foo(a, b):\n    return a + b"

        when:
        def spans = highlighter.computeHighlighting(code)

        then:
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i].start() >= spans[i-1].end()
        }
    }

    private boolean hasSpan(List<PythonSyntaxHighlighter.HighlightSpan> spans, String styleClass, String code, String text) {
        return spans.any { span ->
            span.styleClass() == styleClass &&
            span.start() >= 0 &&
            span.end() <= code.length() &&
            code.substring(span.start(), span.end()) == text
        }
    }
}
