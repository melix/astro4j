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
package me.champeau.a4j.jsolex.app

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class I18nConsistencyTest extends Specification {

    def "English and French properties files should have the same keys"() {
        given:
        def resourceDir = Path.of("src/main/resources/me/champeau/a4j/jsolex/app")
        def errors = []

        when:
        Files.walk(resourceDir)
            .filter { it.fileName.toString().endsWith(".properties") }
            .filter { !it.fileName.toString().contains("_") }
            .forEach { englishFile ->
                def baseName = englishFile.fileName.toString().replace(".properties", "")
                def frenchFile = englishFile.parent.resolve("${baseName}_fr.properties")

                if (Files.exists(frenchFile)) {
                    def englishProps = loadProperties(englishFile)
                    def frenchProps = loadProperties(frenchFile)

                    def missingInEnglish = frenchProps.keySet() - englishProps.keySet()
                    def missingInFrench = englishProps.keySet() - frenchProps.keySet()

                    if (missingInEnglish) {
                        errors << "Keys in ${baseName}_fr.properties but missing in ${baseName}.properties: ${missingInEnglish}"
                    }
                    if (missingInFrench) {
                        errors << "Keys in ${baseName}.properties but missing in ${baseName}_fr.properties: ${missingInFrench}"
                    }
                }
            }

        then:
        errors.isEmpty() || {
            throw new AssertionError("I18n consistency errors:\n" + errors.join("\n"))
        }()
    }

    private Properties loadProperties(Path path) {
        def props = new Properties()
        Files.newInputStream(path).withCloseable { stream ->
            props.load(stream)
        }
        return props
    }
}