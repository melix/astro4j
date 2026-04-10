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
import me.champeau.a4j.jsolex.processing.util.Constants
import me.champeau.a4j.jsolex.processing.util.FileBackedImage
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import java.util.function.Function

class ScaleUnitFunctionsTest extends Specification {

    DefaultImageScriptExecutor executor

    private DefaultImageScriptExecutor executorWith(float[] pixels) {
        Function<PixelShift, ImageWrapper> imageSupplier = { PixelShift shift ->
            return new ImageWrapper32(pixels.length, 1, [pixels] as float[][], new LinkedHashMap<>())
        } as Function
        return new DefaultImageScriptExecutor(imageSupplier, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
    }

    private static float[] dataOf(result, String label) {
        return ((ImageWrapper32) ((FileBackedImage) result.imagesByLabel()[label]).unwrapToMemory()).data()[0]
    }

    def setup() {
        // 4 pixels: 0, halfway, max in-range, and over-range (will be clamped if requested)
        executor = executorWith([0f, 32768f, 65535f, 80000f] as float[])
    }

    def "scale_to_unit divides pixels by 65535 and clamps by default"() {
        given:
        def script = '''
[outputs]
out=scale_to_unit(img(0))
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)
        def out = dataOf(result, "out")

        then:
        out[0] == 0f
        out[1] == (float) (32768d / Constants.MAX_PIXEL_VALUE)
        out[2] == 1f
        // 80000/65535 ~= 1.22 — clamped to 1
        out[3] == 1f
    }

    def "scale_to_unit with clamp=0 keeps out-of-range values"() {
        given:
        def script = '''
[outputs]
out=scale_to_unit(img(0); 0)
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)
        def out = dataOf(result, "out")

        then:
        out[3] == (float) (80000d / Constants.MAX_PIXEL_VALUE)
        out[3] > 1f
    }

    def "scale_from_unit multiplies pixels by 65535 and clamps by default"() {
        given:
        def exec = executorWith([0f, 0.5f, 1f, 1.5f] as float[])

        and:
        def script = '''
[outputs]
out=scale_from_unit(img(0))
'''

        when:
        def result = exec.execute(script, SectionKind.SINGLE)
        def out = dataOf(result, "out")

        then:
        out[0] == 0f
        out[1] == 0.5f * Constants.MAX_PIXEL_VALUE
        out[2] == Constants.MAX_PIXEL_VALUE
        // 1.5 * 65535 = 98302.5 — clamped to 65535
        out[3] == Constants.MAX_PIXEL_VALUE
    }

    def "scale_from_unit with clamp=0 keeps out-of-range values"() {
        given:
        def exec = executorWith([0f, 0.5f, 1f, 1.5f] as float[])

        and:
        def script = '''
[outputs]
out=scale_from_unit(img: img(0); clamp: 0)
'''

        when:
        def result = exec.execute(script, SectionKind.SINGLE)
        def out = dataOf(result, "out")

        then:
        out[3] == 1.5f * Constants.MAX_PIXEL_VALUE
    }

    def "scale_to_unit and scale_from_unit are inverse for in-range pixels"() {
        given:
        def script = '''
[outputs]
roundtrip=scale_from_unit(scale_to_unit(img(0)))
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)
        def out = dataOf(result, "roundtrip")

        then:
        out[0] == 0f
        out[1] == 32768f
        out[2] == Constants.MAX_PIXEL_VALUE
    }

    def "both functions also work on scalar values"() {
        when:
        def result = executor.execute('''
[outputs]
half=scale_to_unit(32767.5)
back=scale_from_unit(0.5)
over_clamped=scale_to_unit(80000)
over_unclamped=scale_to_unit(80000; 0)
''', SectionKind.SINGLE)

        then:
        ((Number) result.valuesByLabel()["half"]).doubleValue() == 0.5d
        ((Number) result.valuesByLabel()["back"]).doubleValue() == 32767.5d
        ((Number) result.valuesByLabel()["over_clamped"]).doubleValue() == 1.0d
        ((Number) result.valuesByLabel()["over_unclamped"]).doubleValue() > 1.0d
    }
}
