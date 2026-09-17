/*
 * Copyright 2026-2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor.SectionKind
import me.champeau.a4j.jsolex.processing.expr.ScriptExecutionContext
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.MutableMap
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.function.Function

class PythonScriptConcurrencyTest extends Specification {

    private static final String SCRIPT = '''
import jsolex

def single():
    outputs.image = jsolex.funcs.img(0)
'''

    def "concurrent scripts only see the images of their own file"() {
        given:
        def widths = [8, 16, 32]
        def executors = widths.collect { width ->
            def image = new ImageWrapper32(width, width, new float[width][width], MutableMap.of())
            new DefaultImageScriptExecutor({ PixelShift shift -> image } as Function<PixelShift, ImageWrapper>, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
        }
        def pool = Executors.newFixedThreadPool(widths.size())

        when:
        def futures = (0..<widths.size()).collect { i ->
            pool.submit({ ->
                (1..10).collect { executors[i].executePythonScript(SCRIPT, SectionKind.SINGLE) }
            } as Callable)
        }
        def results = futures.collect { it.get() }
        pool.shutdown()

        then:
        (0..<widths.size()).every { i ->
            results[i].every { result ->
                result.invalidExpressions().isEmpty() && result.imagesByLabel().image.width() == widths[i]
            }
        }
    }
}
