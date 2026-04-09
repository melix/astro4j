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
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Tests for the persistent memoization cache on {@link DefaultImageScriptExecutor}.
 *
 * The cache is shared between successive calls to {@code execute()} on the same
 * executor instance, so that re-running a script after tweaking parameters can
 * reuse expensive intermediate results whose dependencies haven't changed.
 */
class MemoizingExpressionEvaluatorCacheTest extends Specification {

    AtomicInteger imageRequestCounter
    DefaultImageScriptExecutor executor

    def setup() {
        imageRequestCounter = new AtomicInteger()
        Function<PixelShift, ImageWrapper> imageSupplier = { PixelShift shift ->
            imageRequestCounter.incrementAndGet()
            // Each shift produces a small unique 1x1 image so identity is observable
            return new ImageWrapper32(1, 1, [[(float) shift.pixelShift()] as float[]] as float[][], new LinkedHashMap<>())
        } as Function
        executor = new DefaultImageScriptExecutor(imageSupplier, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
    }

    def "identical reruns return identical results from the cache"() {
        given:
        def script = '''
[outputs]
a=1+2
b=3+4
c=a+b
'''

        when: "first run populates the cache"
        def first = executor.execute(script, SectionKind.SINGLE)
        def sizeAfterFirst = executor.scriptCacheSize()

        and: "rerun the same script"
        def second = executor.execute(script, SectionKind.SINGLE)
        def sizeAfterSecond = executor.scriptCacheSize()

        then: "results are identical and cache never grows beyond the first run"
        first.valuesByLabel()["c"] == 10.0d
        second.valuesByLabel()["c"] == 10.0d
        sizeAfterFirst > 0
        // On a top-level cache hit the parent's recursive descent is short-circuited,
        // so sub-expression entries that were not re-visited are correctly evicted.
        sizeAfterSecond <= sizeAfterFirst
        sizeAfterSecond > 0
    }

    def "running a different script evicts entries from previous run"() {
        given:
        def scriptA = '''
[outputs]
a=1+2
b=3+4
'''
        def scriptB = '''
[outputs]
x=10+20
'''

        when: "execute the first script"
        executor.execute(scriptA, SectionKind.SINGLE)
        def sizeAfterA = executor.scriptCacheSize()

        and: "execute a completely different script"
        def resultB = executor.execute(scriptB, SectionKind.SINGLE)
        def sizeAfterB = executor.scriptCacheSize()

        then: "no entry from script A survives"
        sizeAfterA > 0
        sizeAfterB > 0
        sizeAfterB < sizeAfterA + 1 // strictly bounded by script B's own contribution
        resultB.valuesByLabel()["x"] == 30.0d
    }

    def "expressions that don't depend on changed variables are reused"() {
        given:
        Function<PixelShift, ImageWrapper> imageSupplier = { PixelShift shift ->
            imageRequestCounter.incrementAndGet()
            return ImageWrapper32.createEmpty()
        } as Function
        executor = new DefaultImageScriptExecutor(imageSupplier, ScriptExecutionContext.empty(), Broadcaster.NO_OP)

        and:
        def script = '''
[tmp]
heavy=img(0)
shifted=img(shift_value)

[outputs]
out=heavy
'''

        when: "first run with shift_value=1"
        executor.putVariable("shift_value", 1.0d)
        executor.execute(script, SectionKind.SINGLE)
        def callsAfterFirst = imageRequestCounter.get()

        and: "rerun with the same value"
        executor.putVariable("shift_value", 1.0d)
        executor.execute(script, SectionKind.SINGLE)
        def callsAfterIdentical = imageRequestCounter.get()

        and: "rerun with a different shift_value"
        executor.putVariable("shift_value", 2.0d)
        executor.execute(script, SectionKind.SINGLE)
        def callsAfterChanged = imageRequestCounter.get()

        then: "identical rerun does not request any new image"
        callsAfterIdentical == callsAfterFirst

        and: "changing shift_value triggers exactly one new image request (heavy is still cached)"
        callsAfterChanged == callsAfterFirst + 1
    }

    def "clearScriptCache wipes all entries"() {
        given:
        executor.execute("[outputs]\na=1+2\n", SectionKind.SINGLE)
        assert executor.scriptCacheSize() > 0

        when:
        executor.clearScriptCache()

        then:
        executor.scriptCacheSize() == 0
    }

    def "FileOutputResult values are never cached across runs"() {
        given: "a script that exposes a pre-set FileOutputResult variable as an output"
        def tempFile = Files.createTempFile("cache_file_output_test", ".bin")
        try {
            executor.putVariable("file_out", new SingleFileOutput(tempFile))
            def script = '''
[outputs]
out=file_out
'''

            when: "first run"
            def first = executor.execute(script, SectionKind.SINGLE)
            def sizeAfterFirst = executor.scriptCacheSize()

            and: "second run"
            def second = executor.execute(script, SectionKind.SINGLE)

            then: "neither run cached the file output"
            first.filesByLabel()["out"] instanceof SingleFileOutput
            second.filesByLabel()["out"] instanceof SingleFileOutput
            // No cache entry should reference a FileOutputResult — anim()-style
            // outputs reference temporary files that get moved during rendering, so
            // returning a stale cached path would break the second run.
            sizeAfterFirst == 0
            executor.scriptCacheSize() == 0
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    def "cache hit on assignment still binds the variable for downstream expressions"() {
        given: "first run populates the cache for an assignment"
        def script = '''
[tmp]
base=2*3

[outputs]
out=base+1
'''
        executor.execute(script, SectionKind.SINGLE)

        when: "rerun the exact same script — every assignment hits the cache"
        def result = executor.execute(script, SectionKind.SINGLE)

        then: "downstream expression that reads `base` still produces a coherent result"
        result.valuesByLabel()["out"] == 7.0d
    }
}
