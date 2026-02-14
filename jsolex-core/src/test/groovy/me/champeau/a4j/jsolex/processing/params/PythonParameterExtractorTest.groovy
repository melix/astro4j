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
package me.champeau.a4j.jsolex.processing.params

import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files

class PythonParameterExtractorTest extends Specification {

    @Subject
    PythonParameterExtractor extractor = new PythonParameterExtractor()

    // ==================== Meta Property Tests ====================

    def "parses meta title"() {
        given:
        def script = '''
# meta:title = "My Script"
def single():
    pass
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "My Script"
    }

    def "parses meta title without quotes"() {
        given:
        def script = '''
# meta:title = My Script
def single():
    pass
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "My Script"
    }

    def "parses localized meta title"() {
        given:
        def script = '''
# meta:title = "My Script"
# meta:title:fr = "Mon Script"
# meta:title:de = "Mein Skript"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "My Script"
        result.getDisplayTitle("fr") == "Mon Script"
        result.getDisplayTitle("de") == "Mein Skript"
    }

    def "parses meta author"() {
        given:
        def script = '''
# meta:author = "John Doe"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.author == "John Doe"
    }

    def "parses meta version"() {
        given:
        def script = '''
# meta:version = "2.1.0"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.version == "2.1.0"
    }

    def "parses meta requires"() {
        given:
        def script = '''
# meta:requires = "4.6.0"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.requiredVersion == "4.6.0"
    }

    def "parses meta description"() {
        given:
        def script = '''
# meta:description = "This script processes solar images"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayDescription("en") == "This script processes solar images"
    }

    def "parses localized meta description"() {
        given:
        def script = '''
# meta:description = "Processes images"
# meta:description:fr = "Traite les images"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayDescription("en") == "Processes images"
        result.getDisplayDescription("fr") == "Traite les images"
    }

    def "parses complete metadata block"() {
        given:
        def script = '''
# meta:title = "Solar Processor"
# meta:title:fr = "Processeur Solaire"
# meta:author = "Jane Smith"
# meta:version = "1.0.0"
# meta:requires = "4.5.0"
# meta:description = "Process solar H-alpha images"

from jsolex import funcs, vars

def single():
    return {"result": funcs.sharpen(vars.img)}
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "Solar Processor"
        result.getDisplayTitle("fr") == "Processeur Solaire"
        result.author == "Jane Smith"
        result.version == "1.0.0"
        result.requiredVersion == "4.5.0"
        result.getDisplayDescription("en") == "Process solar H-alpha images"
    }

    // ==================== Parameter Tests ====================

    def "parses number parameter with all attributes"() {
        given:
        def script = '''
# param:gamma:type = number
# param:gamma:default = 1.5
# param:gamma:min = 0.1
# param:gamma:max = 3.0
# param:gamma:name = "Gamma"
# param:gamma:description = "Contrast adjustment"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.parameters.size() == 1
        result.hasParametersSection()

        def param = result.parameters[0]
        param.name == "gamma"
        param.type == ParameterType.NUMBER
        param.defaultValue == 1.5d
        param instanceof NumberParameter

        def numParam = (NumberParameter) param
        numParam.min == 0.1d
        numParam.max == 3.0d
        param.getDisplayName("en") == "Gamma"
        param.getDescription("en") == "Contrast adjustment"
    }

    def "parses number parameter with localized names"() {
        given:
        def script = '''
# param:intensity:type = number
# param:intensity:default = 1.0
# param:intensity:name = "Intensity"
# param:intensity:name:fr = "Intensite"
# param:intensity:description = "Light intensity"
# param:intensity:description:fr = "Intensite lumineuse"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        def param = result.parameters[0]
        param.getDisplayName("en") == "Intensity"
        param.getDisplayName("fr") == "Intensite"
        param.getDescription("en") == "Light intensity"
        param.getDescription("fr") == "Intensite lumineuse"
    }

    def "parses string parameter"() {
        given:
        def script = '''
# param:label:type = string
# param:label:default = "Output"
# param:label:name = "Label"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.parameters.size() == 1
        def param = result.parameters[0]
        param.name == "label"
        param.type == ParameterType.STRING
        param.defaultValue == "Output"
        param instanceof StringParameter
    }

    def "parses choice parameter"() {
        given:
        def script = '''
# param:mode:type = choice
# param:mode:choices = fast,balanced,quality
# param:mode:default = balanced
# param:mode:name = "Processing Mode"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.parameters.size() == 1
        def param = result.parameters[0]
        param.name == "mode"
        param.type == ParameterType.CHOICE
        param.defaultValue == "balanced"
        param instanceof ChoiceParameter

        def choiceParam = (ChoiceParameter) param
        choiceParam.choices == ["fast", "balanced", "quality"]
    }

    def "parses multiple parameters"() {
        given:
        def script = '''
# param:gamma:type = number
# param:gamma:default = 1.0

# param:sigma:type = number
# param:sigma:default = 2.5

# param:mode:type = choice
# param:mode:choices = fast,slow
# param:mode:default = fast
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.parameters.size() == 3
        result.parameters.find { it.name == "gamma" } != null
        result.parameters.find { it.name == "sigma" } != null
        result.parameters.find { it.name == "mode" } != null
    }

    def "ignores parameter without type"() {
        given:
        def script = '''
# param:incomplete:default = 1.0
# param:incomplete:name = "Incomplete"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.parameters.size() == 0
    }

    // ==================== Edge Case Tests ====================

    def "ignores non-meta comments"() {
        given:
        def script = '''
# This is a regular comment
# meta:title = "My Script"
# Another comment
def single():
    # Not a meta comment
    pass
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "My Script"
        result.author == null
    }

    def "handles empty script"() {
        when:
        def result = extractor.extractParameters("", "test.py")

        then:
        result.title.isEmpty()
        result.parameters.isEmpty()
        result.scriptFileName == "test.py"
    }

    def "handles script with no metadata"() {
        given:
        def script = '''
def single():
    return {"result": 42}
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.title.isEmpty()
        result.parameters.isEmpty()
    }

    def "handles trailing whitespace in values"() {
        given:
        def script = '''
# meta:title = "My Script"
# meta:author = "John Doe"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "My Script"
        result.author == "John Doe"
    }

    def "handles single quotes"() {
        given:
        def script = """
# meta:title = 'Single Quoted'
# meta:author = 'Jane'
"""

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "Single Quoted"
        result.author == "Jane"
    }

    def "extracts from file path"() {
        given:
        def tempFile = Files.createTempFile("test_script", ".py")
        Files.writeString(tempFile, '''
# meta:title = "File Test"
# meta:version = "1.0"
''')

        when:
        def result = extractor.extractParameters(tempFile)

        then:
        result.getDisplayTitle("en") == "File Test"
        result.version == "1.0"
        result.scriptFileName == tempFile.fileName.toString()

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "integer parameter values are parsed correctly"() {
        given:
        def script = '''
# param:count:type = number
# param:count:default = 5
# param:count:min = 1
# param:count:max = 10
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        def param = result.parameters[0] as NumberParameter
        param.defaultValue == 5
        param.min == 1.0d
        param.max == 10.0d
    }

    def "meta:name is alias for meta:title"() {
        given:
        def script = '''
# meta:name = "Using name instead of title"
'''

        when:
        def result = extractor.extractParameters(script, "test.py")

        then:
        result.getDisplayTitle("en") == "Using name instead of title"
    }
}
