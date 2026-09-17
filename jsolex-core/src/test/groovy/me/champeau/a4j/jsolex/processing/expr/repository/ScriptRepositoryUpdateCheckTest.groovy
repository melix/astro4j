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
package me.champeau.a4j.jsolex.processing.expr.repository

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class ScriptRepositoryUpdateCheckTest extends Specification {
    @TempDir
    Path cacheRoot

    ScriptRepositoryManager manager

    def setup() {
        manager = new ScriptRepositoryManager(cacheRoot)
    }

    def "has no recorded check time when repository was never refreshed"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)

        expect:
        manager.lastSuccessfulCheck(repository).empty
    }

    def "reads back the recorded check time"() {
        given:
        def recorded = Instant.now() - Duration.ofHours(hoursAgo)
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)
        writeMarker(repository, recorded)

        expect:
        manager.lastSuccessfulCheck(repository).get().toEpochMilli() == recorded.toEpochMilli()

        where:
        hoursAgo << [1, 23, 25, 240]
    }

    def "has no recorded check time when the marker cannot be read"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)
        def repoDir = cacheRoot.resolve("My_scripts")
        Files.createDirectories(repoDir)
        Files.writeString(repoDir.resolve(".last-check"), "not a timestamp")

        expect:
        manager.lastSuccessfulCheck(repository).empty
    }

    def "the recorded check time is independent of the value stored in preferences"() {
        given:
        def recorded = Instant.now() - Duration.ofHours(2)
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", Instant.now())
        writeMarker(repository, recorded)

        expect:
        manager.lastSuccessfulCheck(repository).get().toEpochMilli() == recorded.toEpochMilli()
    }

    def "reports a pending update when the remote script requires a newer version"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)
        def repoDir = cacheRoot.resolve("My_scripts")
        Files.createDirectories(repoDir)
        Files.writeString(repoDir.resolve("stack.math"), script("1.4", "5.0.0"))
        Files.writeString(repoDir.resolve("stack.math.pending"), script("1.5", "99.0.0"))
        Files.writeString(repoDir.resolve("other.math"), script("1.0", "5.0.0"))

        when:
        def scripts = manager.getLocalScripts(repository).sort { it.filename }

        then:
        scripts*.filename == ["other.math", "stack.math"]
        scripts[0].version == "1.0"
        scripts[0].pendingUpdate.empty
        scripts[1].version == "1.4"
        scripts[1].pendingUpdate.get() == new RemoteScript.PendingUpdate("1.5", "99.0.0")
    }

    private static String script(String version, String requires) {
        """meta {
  title = "Stacking"
  author = "Cédric Champeau"
  version = "${version}"
  requires = "${requires}"
}
[outputs]
result = img(0)
"""
    }

    private void writeMarker(ScriptRepository repository, Instant instant) {
        def repoDir = cacheRoot.resolve("My_scripts")
        Files.createDirectories(repoDir)
        Files.writeString(repoDir.resolve(".last-check"), String.valueOf(instant.toEpochMilli()))
    }
}
