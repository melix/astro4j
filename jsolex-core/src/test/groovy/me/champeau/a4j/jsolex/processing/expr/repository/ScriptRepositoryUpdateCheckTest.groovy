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

    def "checks for updates when repository was never refreshed"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)

        expect:
        manager.shouldCheckForUpdates(repository)
        manager.lastSuccessfulCheck(repository).empty
    }

    def "checks for updates when the recorded check is older than a day"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)
        writeMarker(repository, Instant.now() - Duration.ofHours(hoursAgo))

        expect:
        manager.shouldCheckForUpdates(repository) == shouldCheck

        where:
        hoursAgo | shouldCheck
        1        | false
        23       | false
        25       | true
        240      | true
    }

    def "checks for updates when the recorded check cannot be read"() {
        given:
        def repository = new ScriptRepository("My scripts", "https://example.com/scripts/", null)
        def repoDir = cacheRoot.resolve("My_scripts")
        Files.createDirectories(repoDir)
        Files.writeString(repoDir.resolve(".last-check"), "not a timestamp")

        expect:
        manager.shouldCheckForUpdates(repository)
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

    private void writeMarker(ScriptRepository repository, Instant instant) {
        def repoDir = cacheRoot.resolve("My_scripts")
        Files.createDirectories(repoDir)
        Files.writeString(repoDir.resolve(".last-check"), String.valueOf(instant.toEpochMilli()))
    }
}
