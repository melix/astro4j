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

import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files

@Requires({ OpenCLSupport.isAvailable() })
class GpuOperationEventTest extends Specification {

    def setup() {
        System.setProperty("opencl.enabled", "true")
    }

    def cleanup() {
        System.clearProperty("opencl.enabled")
    }

    def "runOp emits a GpuOperation JFR event"() {
        given:
        def context = OpenCLSupport.getContext()
        def recording = new Recording()
        recording.enable("me.champeau.a4j.math.opencl.GpuOperation")
        def dump = Files.createTempFile("gpu-op", ".jfr")

        when:
        recording.start()
        def selfTestPassed = context.runSelfTest()
        recording.stop()
        recording.dump(dump)
        def events = RecordingFile.readAllEvents(dump).findAll {
            it.eventType.name == "me.champeau.a4j.math.opencl.GpuOperation"
        }

        then:
        selfTestPassed
        def event = events.find { it.getString("kernels") == "selftest.selftest" }
        event != null
        event.getInt("kernelLaunches") == 1
        event.getInt("buffersAllocated") == 1
        event.getLong("bytesAllocated") == 16 * Float.BYTES
        event.getLong("bytesDownloaded") == 16 * Float.BYTES
        event.getLong("bytesUploaded") == 0
        event.duration.toNanos() > 0

        cleanup:
        recording?.close()
        Files.deleteIfExists(dump)
    }
}
