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

import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor.SectionKind
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import java.nio.file.Files
import java.util.function.Function

/**
 * Variables of the outputs section whose name starts with {@code __} are computed
 * like any other variable, but are not exposed as outputs of the script.
 */
class InternalOutputsTest extends Specification {

    DefaultImageScriptExecutor executor

    def setup() {
        Function<PixelShift, ImageWrapper> imageSupplier = { PixelShift shift ->
            new ImageWrapper32(1, 1, [[(float) shift.pixelShift()] as float[]] as float[][], new LinkedHashMap<>())
        } as Function
        executor = new DefaultImageScriptExecutor(imageSupplier, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
    }

    def "images prefixed with __ are not outputs but can be used by other expressions"() {
        given:
        def script = '''
[outputs]
__hidden=img(0)
visible=__hidden
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)

        then:
        result.invalidExpressions().empty
        result.imagesByLabel().keySet() == ["visible"] as Set
    }

    def "values prefixed with __ are not outputs"() {
        given:
        def script = '''
[outputs]
__intermediate=1+2
total=__intermediate*2
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)

        then:
        result.invalidExpressions().empty
        result.valuesByLabel().keySet() == ["total"] as Set
        result.valuesByLabel()["total"] == 6.0d
    }

    def "file outputs prefixed with __ are not outputs"() {
        given:
        def tempFile = Files.createTempFile("internal_file_output_test", ".bin")
        executor.putVariable("file_out", new SingleFileOutput(tempFile))
        def script = '''
[outputs]
__hidden_file=file_out
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)

        then:
        result.invalidExpressions().empty
        result.filesByLabel().isEmpty()

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}
