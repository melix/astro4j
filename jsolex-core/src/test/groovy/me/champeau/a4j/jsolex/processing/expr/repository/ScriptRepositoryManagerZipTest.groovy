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
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScriptRepositoryManagerZipTest extends Specification {
    @Subject
    ScriptRepositoryManager manager = new ScriptRepositoryManager()

    @TempDir
    Path tempDir

    def "parses valid zip with main.txt and script"() {
        given:
        def zipBytes = createZip([
            'main.txt': 'test-script.math',
            'test-script.math': '// some script content',
            'helper.py': 'def helper(): pass'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'test-script.math'
        contents.files().size() == 2
        contents.files().containsKey('test-script.math')
        contents.files().containsKey('helper.py')
        new String(contents.files().get('test-script.math')) == '// some script content'
        new String(contents.files().get('helper.py')) == 'def helper(): pass'
    }

    def "uses single math file as main when no main.txt"() {
        given:
        def zipBytes = createZip([
            'test-script.math': '// some script content',
            'helper.py': 'def helper(): pass'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'test-script.math'
        contents.files().size() == 2
    }

    def "returns null for zip with multiple math files and no main.txt"() {
        given:
        def zipBytes = createZip([
            'script1.math': '// first script',
            'script2.math': '// second script',
            'helper.py': 'def helper(): pass'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents == null
    }

    def "returns null for zip with no math files"() {
        given:
        def zipBytes = createZip([
            'helper.py': 'def helper(): pass',
            'utils.py': 'def utils(): pass'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents == null
    }

    def "falls back to single math file when main.txt is empty"() {
        given:
        def zipBytes = createZip([
            'main.txt': '',
            'test-script.math': '// some script content'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'test-script.math'
    }

    def "falls back to single math file when main.txt is whitespace-only"() {
        given:
        def zipBytes = createZip([
            'main.txt': '   \n  ',
            'test-script.math': '// some script content'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'test-script.math'
    }

    def "returns null when main.txt is empty and multiple math files"() {
        given:
        def zipBytes = createZip([
            'main.txt': '',
            'script1.math': '// first',
            'script2.math': '// second'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents == null
    }

    def "trims whitespace from main.txt content"() {
        given:
        def zipBytes = createZip([
            'main.txt': '  test-script.math  \n',
            'test-script.math': '// some script content'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'test-script.math'
    }

    def "preserves directory structure"() {
        given:
        def zipBytes = createZipWithPaths([
            'main.txt': 'script.math',
            'script.math': '// main script',
            'lib/utils.py': 'def util(): pass',
            'lib/deep/nested/helper.py': 'def helper(): pass'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'script.math'
        contents.files().size() == 3
        contents.files().containsKey('script.math')
        contents.files().containsKey('lib/utils.py')
        contents.files().containsKey('lib/deep/nested/helper.py')
    }

    def "skips directory entries"() {
        given:
        def baos = new ByteArrayOutputStream()
        def zos = new ZipOutputStream(baos)

        // Add directory entry
        zos.putNextEntry(new ZipEntry('scripts/'))
        zos.closeEntry()

        // Add main.txt
        zos.putNextEntry(new ZipEntry('main.txt'))
        zos.write('script.math'.bytes)
        zos.closeEntry()

        // Add script
        zos.putNextEntry(new ZipEntry('script.math'))
        zos.write('// content'.bytes)
        zos.closeEntry()

        zos.close()
        def zipBytes = baos.toByteArray()

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.mainScriptName() == 'script.math'
        contents.files().size() == 1
        contents.files().containsKey('script.math')
    }

    def "preserves multiple files with same basename from different directories"() {
        given:
        def zipBytes = createZipWithPaths([
            'main.txt': 'script.math',
            'script.math': '// main',
            'dir1/utils.py': 'version 1',
            'dir2/utils.py': 'version 2'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        contents.files().size() == 3
        contents.files().containsKey('script.math')
        contents.files().containsKey('dir1/utils.py')
        contents.files().containsKey('dir2/utils.py')
        new String(contents.files().get('dir1/utils.py')) == 'version 1'
        new String(contents.files().get('dir2/utils.py')) == 'version 2'
    }

    def "main.txt itself is not included in extracted files"() {
        given:
        def zipBytes = createZip([
            'main.txt': 'script.math',
            'script.math': '// content'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        contents != null
        !contents.files().containsKey('main.txt')
    }

    def "handles zip with only main.txt (referenced script missing)"() {
        given:
        def zipBytes = createZip([
            'main.txt': 'missing-script.math'
        ])

        when:
        def contents = manager.parseZipContents(zipBytes)

        then:
        // parseZipContents returns the contents, but the file won't be there
        contents != null
        contents.mainScriptName() == 'missing-script.math'
        contents.files().isEmpty()
    }

    // ========== Integration tests for full extraction flow ==========

    def "extracts zip contents to cache directory"() {
        given:
        def scriptContent = createValidMathScript('Test Script', 'Test Author', '1.0')
        def pythonContent = 'def helper():\n    return 42'
        def zipBytes = createZip([
            'main.txt': 'test-script.math',
            'test-script.math': scriptContent,
            'helper.py': pythonContent
        ])
        def repository = new ScriptRepository('test-repo', 'http://example.com/scripts', null)
        def cacheDir = tempDir.resolve('cache')
        Files.createDirectories(cacheDir)

        when:
        def result = manager.processZipContent(repository, 'bundle.zip', zipBytes, cacheDir)

        then:
        result != null
        result.title() == 'Test Script'
        result.author() == 'Test Author'
        result.version() == '1.0'

        and: 'files are extracted to cache directory'
        Files.exists(cacheDir.resolve('test-script.math'))
        Files.exists(cacheDir.resolve('helper.py'))
        Files.readString(cacheDir.resolve('helper.py')) == pythonContent
    }

    def "returns null when main script not found in zip"() {
        given:
        def zipBytes = createZip([
            'main.txt': 'missing.math',
            'other.math': createValidMathScript('Other', 'Author', '1.0')
        ])
        def repository = new ScriptRepository('test-repo', 'http://example.com/scripts', null)
        def cacheDir = tempDir.resolve('cache')
        Files.createDirectories(cacheDir)

        when:
        def result = manager.processZipContent(repository, 'bundle.zip', zipBytes, cacheDir)

        then:
        result == null

        and: 'no files are extracted'
        Files.list(cacheDir).count() == 0
    }

    def "extracts single math file without main.txt"() {
        given:
        def scriptContent = createValidMathScript('Script', 'Author', '1.0')
        def zipBytes = createZip([
            'script.math': scriptContent,
            'helper.py': 'def helper(): pass'
        ])
        def repository = new ScriptRepository('test-repo', 'http://example.com/scripts', null)
        def cacheDir = tempDir.resolve('cache')
        Files.createDirectories(cacheDir)

        when:
        def result = manager.processZipContent(repository, 'bundle.zip', zipBytes, cacheDir)

        then:
        result != null
        result.title() == 'Script'
        Files.exists(cacheDir.resolve('script.math'))
        Files.exists(cacheDir.resolve('helper.py'))
    }

    def "returns null when multiple math files and no main.txt"() {
        given:
        def zipBytes = createZip([
            'script1.math': createValidMathScript('Script1', 'Author', '1.0'),
            'script2.math': createValidMathScript('Script2', 'Author', '1.0')
        ])
        def repository = new ScriptRepository('test-repo', 'http://example.com/scripts', null)
        def cacheDir = tempDir.resolve('cache')
        Files.createDirectories(cacheDir)

        when:
        def result = manager.processZipContent(repository, 'bundle.zip', zipBytes, cacheDir)

        then:
        result == null
    }

    def "extracts nested files preserving directory structure"() {
        given:
        def scriptContent = createValidMathScript('Nested Script', 'Author', '1.0')
        def zipBytes = createZipWithPaths([
            'main.txt': 'script.math',
            'script.math': scriptContent,
            'lib/utils.py': 'utils code',
            'lib/math/helpers.py': 'helpers code'
        ])
        def repository = new ScriptRepository('test-repo', 'http://example.com/scripts', null)
        def cacheDir = tempDir.resolve('cache')
        Files.createDirectories(cacheDir)

        when:
        def result = manager.processZipContent(repository, 'bundle.zip', zipBytes, cacheDir)

        then:
        result != null

        and: 'main script at root'
        Files.exists(cacheDir.resolve('script.math'))

        and: 'nested files preserve directory structure'
        Files.exists(cacheDir.resolve('lib/utils.py'))
        Files.exists(cacheDir.resolve('lib/math/helpers.py'))
        Files.readString(cacheDir.resolve('lib/utils.py')) == 'utils code'
        Files.readString(cacheDir.resolve('lib/math/helpers.py')) == 'helpers code'
    }

    // Helper to create a valid .math script with required metadata
    private String createValidMathScript(String title, String author, String version) {
        return """meta {
    title = "${title}"
    author = "${author}"
    version = "${version}"
}

[outputs]
result = img(0)
"""
    }

    // Helper method to create a simple zip file
    private byte[] createZip(Map<String, String> files) {
        def baos = new ByteArrayOutputStream()
        def zos = new ZipOutputStream(baos)

        files.each { name, content ->
            zos.putNextEntry(new ZipEntry(name))
            zos.write(content.bytes)
            zos.closeEntry()
        }

        zos.close()
        return baos.toByteArray()
    }

    // Helper method to create zip with specific paths (for testing nested directories)
    private byte[] createZipWithPaths(Map<String, String> pathsAndContents) {
        def baos = new ByteArrayOutputStream()
        def zos = new ZipOutputStream(baos)

        pathsAndContents.each { path, content ->
            zos.putNextEntry(new ZipEntry(path))
            zos.write(content.bytes)
            zos.closeEntry()
        }

        zos.close()
        return baos.toByteArray()
    }
}
