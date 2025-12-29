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
package me.champeau.a4j.jsolex.app.listeners

import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class ScriptExecutionHelperTest extends Specification {

    @TempDir
    Path tempDir

    def "extractOutputsMetadata extracts metadata from script with meta block"() {
        given:
        def script = '''meta {
    outputs {
        doppler {
            title = "Doppler Image"
            description = "A doppler image"
        }
    }
}

[outputs]
doppler = img(0)
'''

        when:
        def metadata = ScriptExecutionHelper.extractOutputsMetadata(script)

        then:
        metadata != null
        metadata.containsKey('doppler')
        metadata['doppler'].getDisplayTitle('en') == 'Doppler Image'
    }

    def "extractOutputsMetadata extracts metadata from file"() {
        given:
        def scriptFile = tempDir.resolve("test-script.math").toFile()
        scriptFile.text = '''meta {
    outputs {
        output_image {
            title = "Output Image"
        }
    }
}

[outputs]
output_image = img(0)
'''

        when:
        def metadata = ScriptExecutionHelper.extractOutputsMetadata(scriptFile)

        then:
        metadata != null
        metadata.containsKey('output_image')
    }

    def "extractOutputsMetadata returns empty map for invalid script"() {
        given:
        def script = 'invalid script content $%^&*'

        when:
        def metadata = ScriptExecutionHelper.extractOutputsMetadata(script)

        then:
        metadata != null
        metadata.isEmpty()
    }

    def "extractOutputsMetadata returns empty map for script without meta block"() {
        given:
        def script = '''
[params]
x = 1

[outputs]
y = x + 1
'''

        when:
        def metadata = ScriptExecutionHelper.extractOutputsMetadata(script)

        then:
        metadata != null
        metadata.isEmpty()
    }

    def "processScriptErrors does not throw when result has empty invalid expressions"() {
        given:
        def result = ImageMathScriptResult.EMPTY

        when:
        ScriptExecutionHelper.processScriptErrors(result)

        then:
        noExceptionThrown()
    }
}
