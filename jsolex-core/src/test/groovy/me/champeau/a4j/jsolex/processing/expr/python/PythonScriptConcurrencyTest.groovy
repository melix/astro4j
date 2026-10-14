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
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult
import me.champeau.a4j.jsolex.processing.expr.ScriptExecutionContext
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.MutableMap
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function

class PythonScriptConcurrencyTest extends Specification {

    private static final String SCRIPT = '''
import jsolex

def single():
    outputs.image = jsolex.funcs.img(0)
'''

    private static final int CONCURRENT_SCRIPTS = 3

    private static final String PARALLEL_SAFE = '# meta:parallel_safe = "true"\n'

    @TempDir
    Path tempDir

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
                (1..10).collect { executors[i].executePythonScript(header + SCRIPT, SectionKind.SINGLE) }
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

        where:
        header << ['', PARALLEL_SAFE]
    }

    def "single() of a parallel safe script runs in parallel"() {
        when:
        def results = runConcurrently(PARALLEL_SAFE, 30)

        then:
        results.every { it.invalidExpressions().isEmpty() && it.valuesByLabel().together == 1 }
    }

    def "single() of other scripts runs one at a time"() {
        when:
        def results = runConcurrently('', 1)

        then:
        results.every { it.invalidExpressions().isEmpty() }
        !results.every { it.valuesByLabel().together == 1 }
    }

    def "idle contexts are closed and recreated when needed"() {
        given:
        runConcurrently(PARALLEL_SAFE, 30)
        def idle = new ArrayList(PythonScriptExecutor.IDLE_CONTEXTS)

        when:
        PythonScriptExecutor.closeIdleContexts()

        then:
        !idle.isEmpty()
        PythonScriptExecutor.IDLE_CONTEXTS.isEmpty()
        idle.every { it.thread().awaitTermination(30, TimeUnit.SECONDS) }
        idle.every { owned ->
            try {
                owned.context().eval("python", "1")
                false
            } catch (IllegalStateException ignored) {
                true
            }
        }

        when:
        def results = runConcurrently(PARALLEL_SAFE, 30)

        then:
        results.every { it.invalidExpressions().isEmpty() && it.valuesByLabel().together == 1 }
    }

    def "two batches can run one after the other"() {
        expect:
        (1..2).every { runBatch() == 3 }
    }

    private int runBatch() {
        def script = scriptWaitingForOthers(PARALLEL_SAFE, 30)
        def singles = runConcurrently(script)
        def owned = new ArrayList(PythonScriptExecutor.IDLE_CONTEXTS)
        def executor = newExecutor()
        executor.putVariable("together", singles.collect { it.valuesByLabel().together })
        def result = executor.executePythonScript(script, SectionKind.BATCH)
        PythonScriptExecutor.closeIdleContexts()
        assert singles.every { it.invalidExpressions().isEmpty() }
        assert result.invalidExpressions().isEmpty()
        assert owned.size() == CONCURRENT_SCRIPTS
        assert owned.every { it.thread().awaitTermination(30, TimeUnit.SECONDS) }
        result.valuesByLabel().total as int
    }

    private List<ImageMathScriptResult> runConcurrently(String header, int timeoutSeconds) {
        runConcurrently(scriptWaitingForOthers(header, timeoutSeconds))
    }

    private List<ImageMathScriptResult> runConcurrently(String script) {
        def pool = Executors.newFixedThreadPool(CONCURRENT_SCRIPTS)
        try {
            def futures = (1..CONCURRENT_SCRIPTS).collect {
                def executor = newExecutor()
                pool.submit({ -> executor.executePythonScript(script, SectionKind.SINGLE) } as Callable<ImageMathScriptResult>)
            }
            return futures.collect { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    private String scriptWaitingForOthers(String header, int timeoutSeconds) {
        def directory = Files.createTempDirectory(tempDir, "run")
        header + """
import os
import time
import uuid

def single():
    directory = ${pythonString(directory.toString())}
    with open(os.path.join(directory, str(uuid.uuid4())), 'w') as f:
        f.write('started')
    deadline = time.time() + ${timeoutSeconds}
    while len(os.listdir(directory)) < ${CONCURRENT_SCRIPTS} and time.time() < deadline:
        time.sleep(0.05)
    outputs.together = 1 if len(os.listdir(directory)) >= ${CONCURRENT_SCRIPTS} else 0

def batch(results):
    outputs.total = sum(results.together)
"""
    }

    private static DefaultImageScriptExecutor newExecutor() {
        new DefaultImageScriptExecutor({ PixelShift shift -> null } as Function<PixelShift, ImageWrapper>, ScriptExecutionContext.empty(), Broadcaster.NO_OP)
    }

    private static String pythonString(String value) {
        "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
}
