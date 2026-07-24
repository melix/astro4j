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

import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor.SectionKind
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import java.util.function.Function

class SignedWorkflowScriptTest extends Specification {
    private static final int SIZE = 200
    private static final double R = 50

    def "the per-image section of the offset-free corona workflow executes"() {
        given:
        def executor = executorWithDiskImage()
        def script = '''
[tmp]
line=img(0)
cont=img(1)
diff=signed_diff(line;cont)

[outputs]
diffed=disk_fill(diff;0)
review=mtf_autostretch(img: diffed; mask: annulus_mask(1.05; 1.5))
'''

        when:
        def result = executor.execute(script, SectionKind.SINGLE)

        then:
        result.invalidExpressions().forEach { println("INVALID: ${it.label()} (${it.expression()}): ${it.error()}") }
        result.invalidExpressions().isEmpty()
        result.imagesByLabel().containsKey("diffed")
        result.imagesByLabel().containsKey("review")
    }

    private static DefaultImageScriptExecutor executorWithDiskImage() {
        Function<PixelShift, ImageWrapper> imageSupplier = { PixelShift shift ->
            def data = new float[SIZE][SIZE]
            def cx = SIZE / 2
            def cy = SIZE / 2
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    def r = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / R
                    data[y][x] = (float) (r <= 1 ? 30000 : 5000 + 100 * shift.pixelShift() + 500 * Math.exp(-(r - 1)))
                }
            }
            def meta = new LinkedHashMap<Class<?>, Object>()
            meta.put(Ellipse.class, circle(cx, cy, R))
            return new ImageWrapper32(SIZE, SIZE, data, meta)
        } as Function
        return new DefaultImageScriptExecutor(imageSupplier, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
    }

    private static Ellipse circle(double cx, double cy, double radius) {
        double a = 1.0 / (radius * radius), c = 1.0 / (radius * radius)
        double d = -2.0 * cx / (radius * radius), e = -2.0 * cy / (radius * radius)
        double f = cx * cx / (radius * radius) + cy * cy / (radius * radius) - 1.0
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }
}
