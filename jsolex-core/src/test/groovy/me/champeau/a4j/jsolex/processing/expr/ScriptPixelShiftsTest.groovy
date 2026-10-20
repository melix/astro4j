/*
 * Copyright 2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import spock.lang.Specification

import java.util.function.Function

class ScriptPixelShiftsTest extends Specification {

    def "collects the shifts used in every section"() {
        given:
        def executor = new DefaultImageScriptExecutor(
                ShiftCollectingImageExpressionEvaluator.zeroImages() as Function<PixelShift, ImageWrapper>,
                ScriptExecutionContext.empty(),
                Broadcaster.NO_OP)

        when:
        def result = executor.execute('''
[tmp]
a = img(-2)
b = img(3)

[outputs]
c = max(a, b)
d = img(5)
''', ImageMathScriptExecutor.SectionKind.SINGLE)

        then:
        result.pixelShifts() == [-2d, 3d, 5d] as Set
    }
}
