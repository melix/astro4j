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
package me.champeau.a4j.jsolex.app

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class ChineseLocalizationTest extends Specification {
    private static final Path RESOURCE_DIR = Path.of("src/main/resources/me/champeau/a4j/jsolex/app")

    def "Chinese bundles preserve keys, placeholders and help markup"() {
        given:
        def chineseFiles = chineseFiles()

        expect:
        !chineseFiles.isEmpty()
        chineseFiles.each { chineseFile ->
            def englishFile = chineseFile.resolveSibling(chineseFile.fileName.toString().replace('_zh.properties', '.properties'))
            def english = loadProperties(englishFile)
            def chinese = loadProperties(chineseFile)
            assert chinese.keySet() == english.keySet(): "${chineseFile.fileName}: resource keys differ"
            english.each { key, value ->
                def translated = chinese.getProperty(key)
                assert tokens(translated) == tokens(value): "${chineseFile.fileName}: ${key} placeholders differ"
                assert translated.count('**') == value.count('**'): "${chineseFile.fileName}: ${key} bold markup differs"
                assert !translated.contains('\uFFFD'): "${chineseFile.fileName}: ${key} contains a replacement character"
            }
            // Chinese text must be escaped for Properties.load(InputStream)'s ISO-8859-1 encoding.
            assert Files.readAllBytes(chineseFile).every { it >= 0 }: "${chineseFile.fileName}: use Unicode escapes for non-ASCII text"
        }
    }

    def "BASS2000 fields retain scientific units and instrument identifiers"() {
        given:
        def chinese = loadProperties(RESOURCE_DIR.resolve('bass2000-submission_zh.properties'))

        expect:
        chinese.getProperty(key).contains(expected)

        where:
        key                                  | expected
        'instrument.wavelength.label'        | '\u00C5'
        'instrument.pixel.size.only.label'   | '\u00B5m'
        'instrument.pixel.size.prompt'       | '2.4\u00B5m'
        'wavelength.error.message'           | '6562.8\u00C5'
        'wavelength.error.message'           | '3933.7\u00C5'
        'wavelength.error.message'           | '3968.5\u00C5'
        'requirements.requirements'          | '1\u00B0'
        'gong.loading'                       | 'GONG'
        'orientation.gong.reference'         | 'GONG'
        'validation.tooltip.spectrograph'    | "Sol'Ex"
        'validation.error.spectrograph'      | "Sol'Ex"
    }

    def "translated ImageMath example preserves executable lines"() {
        given:
        def english = loadProperties(RESOURCE_DIR.resolve('imagemath-editor.properties'))
        def chinese = loadProperties(RESOURCE_DIR.resolve('imagemath-editor_zh.properties'))

        expect:
        scriptLines(chinese.getProperty('example.script')) == scriptLines(english.getProperty('example.script'))
    }

    private static List<String> scriptLines(String script) {
        script.readLines().findAll { !it.isBlank() && !it.startsWith('#') }
    }

    private static List<String> tokens(String value) {
        // Named template tokens, MessageFormat/SLF4J arguments and printf formats.
        // Do not treat ordinary percentages such as "25% larger" as printf formats.
        value.findAll(/%[A-Z_]+%|\{[^{}]*\}|%(?:\d+\$)?[-#+0,(<]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAn%]/).sort()
    }

    private static List<Path> chineseFiles() {
        Files.list(RESOURCE_DIR).withCloseable { files ->
            files.filter { it.fileName.toString().endsWith('_zh.properties') }.toList()
        }
    }

    private static Properties loadProperties(Path path) {
        def properties = new Properties()
        Files.newInputStream(path).withCloseable { stream ->
            properties.load(stream)
        }
        properties
    }
}
