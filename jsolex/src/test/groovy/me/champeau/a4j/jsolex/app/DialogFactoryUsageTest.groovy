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
package me.champeau.a4j.jsolex.app

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class DialogFactoryUsageTest extends Specification {
    private static final def DIRECT_CREATION = ~/new\s+(Alert|TextInputDialog|ChoiceDialog|Dialog)\s*(<[^>]*>)?\s*\(/

    def "dialogs are only created through AlertFactory"() {
        given:
        def sourceDir = Path.of("src/main/java")
        def offenders = []

        when:
        Files.walk(sourceDir)
            .filter { it.fileName.toString().endsWith(".java") }
            .filter { it.fileName.toString() != "AlertFactory.java" }
            .forEach { file ->
                Files.readAllLines(file).eachWithIndex { line, index ->
                    if (line =~ DIRECT_CREATION) {
                        offenders << "${sourceDir.relativize(file)}:${index + 1}: ${line.trim()}"
                    }
                }
            }

        then:
        offenders.isEmpty() || {
            throw new AssertionError("Create dialogs with AlertFactory so that their buttons are styled:\n" + offenders.join("\n"))
        }()
    }
}
