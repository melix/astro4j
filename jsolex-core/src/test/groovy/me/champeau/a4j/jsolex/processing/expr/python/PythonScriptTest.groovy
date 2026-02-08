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

import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files

class PythonScriptTest extends Specification {

    @Subject
    PythonScriptExecutor executor

    ShiftCollectingImageExpressionEvaluator evaluator
    Map<Class<?>, Object> context

    def setup() {
        evaluator = new ShiftCollectingImageExpressionEvaluator(Broadcaster.NO_OP)
        context = new HashMap<>()
        executor = new PythonScriptExecutor(evaluator, context, Broadcaster.NO_OP)
    }

    // ==================== Basic Execution Tests ====================

    def "can execute simple arithmetic expression"() {
        when:
        def result = executor.executeInline("result = 2 + 3", [:])

        then:
        result == 5.0d
    }

    def "can execute multi-line Python script"() {
        when:
        def result = executor.executeInline('''
x = 10
y = 20
result = x * y
''', [:])

        then:
        result == 200.0d
    }

    def "can access variables passed to script"() {
        when:
        def result = executor.executeInline("result = a + b", [a: 5.0, b: 3.0])

        then:
        result == 8.0d
    }

    def "returns null when no result is set"() {
        when:
        def result = executor.executeInline("x = 42", [:])

        then:
        result == null
    }

    // ==================== Type Conversion Tests ====================

    def "can return string from Python"() {
        when:
        def result = executor.executeInline('result = "hello"', [:])

        then:
        result == "hello"
    }

    def "can return boolean from Python"() {
        when:
        def trueResult = executor.executeInline("result = True", [:])
        def falseResult = executor.executeInline("result = False", [:])

        then:
        trueResult == true
        falseResult == false
    }

    def "can return list from Python"() {
        when:
        def result = executor.executeInline("result = [1, 2, 3]", [:])

        then:
        result instanceof List
        result == [1.0d, 2.0d, 3.0d]
    }

    def "can return dict from Python"() {
        when:
        def result = executor.executeInline('result = {"a": 1, "b": 2}', [:])

        then:
        result instanceof Map
        result == [a: 1.0d, b: 2.0d]
    }

    def "can pass and receive ImageWrapper"() {
        given:
        def img = createImage(10, 10, 100.0f)

        when:
        def result = executor.executeInline("result = img", [img: img])

        then:
        result.is(img)
    }

    // ==================== Python Module Import Tests ====================

    def "can use jsolex module via global variable"() {
        when:
        def result = executor.executeInline('''
result = jsolex is not None
''', [:])

        then:
        result == true
    }

    def "can import jsolex module"() {
        when:
        def result = executor.executeInline('''
import jsolex
result = jsolex is not None
''', [:])

        then:
        result == true
    }

    def "can use from jsolex import syntax"() {
        when:
        def result = executor.executeInline('''
from jsolex import getVariable, setVariable
result = True
''', [:])

        then:
        result == true
    }

    // ==================== Bridge Function Tests ====================

    def "can access variables via bridge"() {
        given:
        evaluator.putVariable("myVar", 42.0)

        when:
        def result = executor.executeInline('''
result = jsolex.getVariable("myVar")
''', [:])

        then:
        result == 42.0d
    }

    def "can get image dimensions via bridge"() {
        given:
        def img = createImage(100, 50, 0.0f)

        when:
        def widthResult = executor.executeInline('result = img.width()', [img: img])
        def heightResult = executor.executeInline('result = img.height()', [img: img])

        then:
        widthResult == 100.0d
        heightResult == 50.0d
    }

    def "can create image via bridge"() {
        when:
        def result = executor.executeInline('''
data = [[1.0, 2.0], [3.0, 4.0]]
result = jsolex.createMono(2, 2, data)
''', [:])

        then:
        result instanceof ImageWrapper32
        def img = (ImageWrapper32) result
        img.width() == 2
        img.height() == 2
        img.data()[0][0] == 1.0f
        img.data()[0][1] == 2.0f
        img.data()[1][0] == 3.0f
        img.data()[1][1] == 4.0f
    }

    // ==================== Control Structure Tests ====================

    def "can use Python for loop"() {
        when:
        def result = executor.executeInline('''
total = 0
for i in range(5):
    total += i
result = total
''', [:])

        then:
        result == 10.0d  // 0+1+2+3+4
    }

    def "can use Python while loop"() {
        when:
        def result = executor.executeInline('''
i = 0
total = 0
while i < 5:
    total += i
    i += 1
result = total
''', [:])

        then:
        result == 10.0d
    }

    def "can use Python if/else"() {
        when:
        def resultTrue = executor.executeInline('''
if value > 5:
    result = "greater"
else:
    result = "not greater"
''', [value: 10.0])
        def resultFalse = executor.executeInline('''
if value > 5:
    result = "greater"
else:
    result = "not greater"
''', [value: 3.0])

        then:
        resultTrue == "greater"
        resultFalse == "not greater"
    }

    def "can use Python list comprehension"() {
        when:
        def result = executor.executeInline('''
result = [x * 2 for x in range(5)]
''', [:])

        then:
        result == [0.0d, 2.0d, 4.0d, 6.0d, 8.0d]
    }

    def "can use Python try/except"() {
        when:
        def result = executor.executeInline('''
try:
    x = 1 / 0
    result = "no error"
except:
    result = "error caught"
''', [:])

        then:
        result == "error caught"
    }

    // ==================== File-Based Script Tests ====================

    def "can execute Python file"() {
        given:
        def tempFile = Files.createTempFile("test_script", ".py")
        Files.writeString(tempFile, '''
x = 10
y = 20
result = x + y
''')

        when:
        def result = executor.executeFile(tempFile.toString(), [:])

        then:
        result == 30.0d

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    // ==================== Context Reuse Tests ====================

    def "context is reused across multiple calls"() {
        when:
        executor.executeInline("persistent_var = 42", [:])
        def result = executor.executeInline("result = persistent_var", [:])

        then:
        result == 42.0d
    }

    def "getOrCreate returns same instance"() {
        when:
        def executor1 = PythonScriptExecutor.getOrCreate(context, evaluator, Broadcaster.NO_OP)
        def executor2 = PythonScriptExecutor.getOrCreate(context, evaluator, Broadcaster.NO_OP)

        then:
        executor1.is(executor2)

    }

    // ==================== Error Handling Tests ====================

    def "Python syntax error throws exception"() {
        when:
        executor.executeInline("if if if", [:])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Python error")
    }

    def "Python runtime error throws exception"() {
        when:
        executor.executeInline("undefined_variable", [:])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Python error")
    }

    def "file not found throws exception"() {
        when:
        executor.executeInline("/nonexistent/path/script.py", [:])

        then:
        thrown(IllegalStateException)
    }

    // ==================== Edge Case Tests ====================

    def "empty script returns null"() {
        when:
        def result = executor.executeInline("", [:])

        then:
        result == null
    }

    def "script with only comments returns null"() {
        when:
        def result = executor.executeInline("# just a comment", [:])

        then:
        result == null
    }

    def "Unicode in script works"() {
        when:
        def result = executor.executeInline('result = "Bonjour \u00e0 tous"', [:])

        then:
        result == "Bonjour à tous"
    }

    def "can pass map from ImageMath to Python"() {
        when:
        def result = executor.executeInline('''
result = data["key"]
''', [data: [key: "value"]])

        then:
        result == "value"
    }

    def "can return nested structures"() {
        when:
        def result = executor.executeInline('''
result = {
    "numbers": [1, 2, 3],
    "nested": {"a": 1}
}
''', [:])

        then:
        result instanceof Map
        result["numbers"] == [1.0d, 2.0d, 3.0d]
        result["nested"] == [a: 1.0d]
    }

    // ==================== Helper Methods ====================

    private static ImageWrapper32 createImage(int width, int height, float value) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }
}
