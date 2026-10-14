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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class OpenCLSupportCrashMarkerTest extends Specification {

    @TempDir
    Path tempDir

    private Path previousDirectory
    private String previousProperty

    def setup() {
        previousDirectory = OpenCLSupport.CRASH_MARKER_DIR.get()
        previousProperty = System.getProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
        OpenCLSupport.setCrashMarkerDirectory(tempDir)
        System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, "true")
        OpenCLSupport.AVAILABILITY_PROBE.set(null)
    }

    def cleanup() {
        OpenCLSupport.setCrashMarkerDirectory(previousDirectory)
        if (previousProperty == null) {
            System.clearProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
        } else {
            System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, previousProperty)
        }
        OpenCLSupport.PREVIOUS_CRASH_DETECTED.set(false)
        OpenCLSupport.AVAILABILITY_PROBE.set(null)
    }

    def "a marker left by a crashed probe disables OpenCL until retried"() {
        given:
        def marker = Files.createFile(tempDir.resolve("opencl-init.marker"))

        when:
        def enabled = OpenCLSupport.isEnabled()

        then:
        !enabled
        OpenCLSupport.isPreviousCrashDetected()
        Files.exists(marker)

        when:
        OpenCLSupport.retry()

        then:
        !OpenCLSupport.isPreviousCrashDetected()
        !Files.exists(marker)
    }

    def "no crash is reported when there is no marker"() {
        when:
        OpenCLSupport.isEnabled()

        then:
        !OpenCLSupport.isPreviousCrashDetected()
        !Files.exists(tempDir.resolve("opencl-init.marker"))
    }
}
