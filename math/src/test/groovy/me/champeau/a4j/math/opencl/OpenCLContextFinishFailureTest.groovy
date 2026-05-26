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
package me.champeau.a4j.math.opencl

import spock.lang.Requires
import spock.lang.Specification

/**
 * Regression test for the silent-garbage class of bug in
 * {@link OpenCLContext#readBuffer}, {@link OpenCLContext#readBufferInt},
 * and {@link OpenCLContext#finish}.
 *
 * <p>Background: when {@code clFinish} returns a non-success error code —
 * typically {@code CL_MEM_OBJECT_ALLOCATION_FAILURE = -4} under VRAM pressure
 * (frequently observed in batch mode on an NVIDIA RTX 4060 with other GPU
 * users active), or {@code CL_INVALID_COMMAND_QUEUE = -36} after a queue
 * teardown — the kernels the read was waiting on may have aborted, so the
 * buffer contents are undefined per OpenCL spec. The previous code logged
 * the error and read anyway, returning plausible-looking but corrupted
 * pixels with no signal.
 *
 * <p>The fix routes all three call sites through
 * {@code requireFinishSuccess(int, String)} which throws on non-success.
 * This test exercises that contract directly with the two error codes that
 * actually occur in production: -4 (driver-side OOM) and -36 (invalidated
 * queue). It cannot exercise the public {@code readBuffer} path via real
 * driver failure because NVIDIA's libOpenCL segfaults on an invalidated
 * queue rather than returning the spec'd error code; the wiring at each
 * call site is a single inspectable line.
 */
@Requires({ OpenCLSupport.isAvailable() })
class OpenCLContextFinishFailureTest extends Specification {

    private OpenCLContext context

    def setup() {
        context = OpenCLSupport.getContext()
    }

    def "requireFinishSuccess throws on CL_MEM_OBJECT_ALLOCATION_FAILURE (-4) — the VRAM-pressure failure observed in production"() {
        when:
        context.requireFinishSuccess(-4, "readBuffer")

        then:
        def e = thrown(OpenCLException)
        e.message.contains("clFinish")
        e.message.contains("readBuffer")
        e.message.contains("-4")
    }

    def "requireFinishSuccess throws on CL_INVALID_COMMAND_QUEUE (-36)"() {
        when:
        context.requireFinishSuccess(-36, "finish")

        then:
        def e = thrown(OpenCLException)
        e.message.contains("clFinish")
        e.message.contains("finish")
    }

    def "requireFinishSuccess does not throw on CL_SUCCESS (0)"() {
        when:
        context.requireFinishSuccess(0, "readBuffer")

        then:
        noExceptionThrown()
    }

    def "requireFinishSuccess increments the finishCount diagnostic on both success and failure"() {
        given:
        int before = context.getFinishCount()

        when:
        context.requireFinishSuccess(0, "readBuffer")

        then:
        context.getFinishCount() == before + 1

        when: "a failing call still counts the attempt"
        try {
            context.requireFinishSuccess(-4, "readBuffer")
        } catch (OpenCLException ignored) {
        }

        then:
        context.getFinishCount() == before + 2
    }

    def "requireFinishSuccess records each non-success as a GPU error for diagnostic surfacing"() {
        given:
        context.resetErrorTracking()

        when:
        try {
            context.requireFinishSuccess(-4, "readBuffer")
        } catch (OpenCLException ignored) {
        }

        then:
        context.getTotalErrors() == 1
        context.getLastError().message().contains("readBuffer.clFinish")
    }
}
