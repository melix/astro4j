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
package me.champeau.a4j.jsolex.expr

import me.champeau.a4j.jsolex.expr.ast.Identifier
import me.champeau.a4j.jsolex.expr.ast.MetaBlock
import me.champeau.a4j.jsolex.expr.ast.ParameterObject
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor
import spock.lang.Specification

class ParameterParsingTest extends Specification {
    def "parses simple parameter definition"() {
        def script = """meta {
    params {
        test {
            type = "number"
            default = 42
        }
    }
}"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def paramsSection = root.findParamsSection()
        paramsSection.isPresent()
        def section = paramsSection.get()
        def paramDefs = section.getParameterDefs()
        paramDefs.size() == 1
        paramDefs[0].getName() == "test"
    }

    def "parses parameter with object syntax"() {
        def script = """meta {
    params {
        brightness {
            type = "number"
            default = 1.0
            min = 0.0
            max = 2.0
        }
    }
}"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def paramsSection = root.findParamsSection()
        paramsSection.isPresent()
        def section = paramsSection.get()
        def paramDefs = section.getParameterDefs()
        paramDefs.size() == 1
        paramDefs[0].getName() == "brightness"
    }

    def "parses mixed params and single sections"() {
        def script = """meta {
    params {
        brightness {
            type = "number"
            default = 1.0
            min = 0.0
            max = 2.0
        }
    }
}

[[single]]
result = adjust_gamma(img(0), brightness)"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def paramsSection = root.findParamsSection()
        paramsSection.isPresent()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        singleSections.size() == 1
    }

    def "extracts parameters using ImageMathParameterExtractor"() {
        def script = """meta {
    params {
        gamma {
            type = "number"
            default = 1.5
            min = 0.5
            max = 3.0
            description = "Gamma correction factor"
        }
        method {
            type = "choice"
            default = "auto"
            choices = "auto,manual,hybrid"
            description = "Processing method"
        }
    }
}

[[single]]
result = img(0)"""
        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.hasParametersSection()
        result.parameters.size() == 2
        def gammaParam = result.parameters.find { it.name == "gamma" }
        gammaParam != null
        gammaParam.type.name() == "NUMBER"
        gammaParam.defaultValue == 1.5
        def methodParam = result.parameters.find { it.name == "method" }
        methodParam != null
        methodParam.type.name() == "CHOICE"
        methodParam.defaultValue == "auto"
    }

    def "parses nested parameter objects"() {
        def script = """meta {
    params {
        gamma {
            type = "number"
            name {
                en = "Gamma Correction"
            }
            default = 1.5
        }
    }
}"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def paramsSection = root.findParamsSection()
        paramsSection.isPresent()
        def paramDefs = paramsSection.get().getParameterDefs()
        paramDefs.size() == 1
        paramDefs[0].getName() == "gamma"
    }

    def "parses internationalized title in meta section"() {
        def script = """meta {
    author = "Test Author"
    title {
        en = "English Title"
        fr = "Titre Français"
    }
    version = "1.0"
    params {
        gamma {
            type = "number"
            default = 1.5
        }
    }
}

image = gamma(image)
"""

        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def metaBlocks = root.childrenOfType(MetaBlock.class)
        metaBlocks.size() == 1
        def metaBlock = metaBlocks[0]

        // Check that the meta content contains both regular properties and nested objects
        def metaContent = metaBlock.getContent()
        def metaProperties = metaContent.getMetaProperties()
        metaProperties.size() == 3 // author, title, version

        // Find the title property with nested language structure
        def titleProperty = metaProperties.find { prop ->
            def identifier = prop.firstChildOfType(Identifier.class)
            identifier != null && identifier.toString() == "title"
        }
        titleProperty != null

        // Verify it has a ParameterObject child (for the nested structure)
        def titleObject = titleProperty.firstChildOfType(ParameterObject.class)
        titleObject != null

        // Check for parameters section
        def paramsBlocks = metaContent.getParametersBlocks()
        paramsBlocks.size() == 1
    }

    def "handles shorthand notation as default locale"() {
        def script = """meta {
    params {
        gamma {
            type = "number"
            name = "Gamma Correction"
            description = "Adjusts gamma correction"
            default = 1.5
        }
        method {
            type = "choice"
            name {
                en = "Processing Method"
                fr = "Méthode de traitement"
            }
            description = "Choose processing method"
            choices = "auto,manual"
            default = "auto"
        }
    }
}"""

        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.hasParametersSection()
        result.parameters.size() == 2

        // Check gamma parameter with shorthand notation
        def gammaParam = result.parameters.find { it.name == "gamma" }
        gammaParam != null
        gammaParam.displayName.size() == 1
        gammaParam.displayName.containsKey("default")
        gammaParam.displayName.get("default") == "Gamma Correction"
        gammaParam.description.size() == 1
        gammaParam.description.containsKey("default")
        gammaParam.description.get("default") == "Adjusts gamma correction"

        // Check method parameter with mixed notation
        def methodParam = result.parameters.find { it.name == "method" }
        methodParam != null
        methodParam.displayName.size() == 2
        methodParam.displayName.containsKey("en")
        methodParam.displayName.get("en") == "Processing Method"
        methodParam.displayName.containsKey("fr")
        methodParam.displayName.get("fr") == "Méthode de traitement"
        methodParam.description.size() == 1
        methodParam.description.containsKey("default")
        methodParam.description.get("default") == "Choose processing method"
    }

    def "fallback logic works correctly for display names"() {
        def script = """meta {
    params {
        shorthandParam {
            type = "number"
            name = "Shorthand Name"
            default = 1.0
        }
        englishParam {
            type = "number"
            name {
                en = "English Name"
                de = "German Name"
            }
            default = 2.0
        }
        multiLangParam {
            type = "number"
            name {
                fr = "French Name"
                de = "German Name"
                es = "Spanish Name"
            }
            default = 3.0
        }
    }
}"""

        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.parameters.size() == 3

        // Test shorthand parameter
        def shorthandParam = result.parameters.find { it.name == "shorthandParam" }
        shorthandParam != null
        shorthandParam.getDisplayName("fr") == "Shorthand Name"  // Falls back to default
        shorthandParam.getDisplayName("en") == "Shorthand Name"  // Falls back to default
        shorthandParam.getDisplayName("default") == "Shorthand Name"  // Direct access

        // Test English parameter (no shorthand, has English)
        def englishParam = result.parameters.find { it.name == "englishParam" }
        englishParam != null
        englishParam.getDisplayName("fr") == "English Name"  // Falls back to English
        englishParam.getDisplayName("en") == "English Name"  // Direct English
        englishParam.getDisplayName("de") == "German Name"  // Direct German

        // Test multi-language parameter (no shorthand, no English)
        def multiLangParam = result.parameters.find { it.name == "multiLangParam" }
        multiLangParam != null
        multiLangParam.getDisplayName("en") != null  // Falls back to first available
        multiLangParam.getDisplayName("it") != null  // Falls back to first available
        multiLangParam.getDisplayName("fr") == "French Name"  // Direct French
    }

    def "parses outputs section in meta block"() {
        def script = """meta {
    outputs {
        result {
            title {
                en = "Processed Image"
                fr = "Image traitée"
            }
            description {
                en = "The final processed image"
            }
        }
    }
}

[[single]]
result = img(0)"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root != null
        def outputsSection = root.findOutputsSection()
        outputsSection.isPresent()
        def section = outputsSection.get()
        def outputDefs = section.getOutputDefs()
        outputDefs.size() == 1
        outputDefs[0].getName() == "result"
    }

    def "extracts outputs metadata using ImageMathParameterExtractor"() {
        def script = """meta {
    outputs {
        solarDisk {
            title {
                en = "Solar Disk"
                fr = "Disque solaire"
            }
            description {
                en = "The processed solar disk image"
                fr = "Image du disque solaire traitée"
            }
        }
        prominences {
            title {
                en = "Prominences"
            }
        }
    }
}

[[single]]
solarDisk = img(0)
prominences = img(1)"""
        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.outputsMetadata.size() == 2

        def solarDiskMeta = result.getOutputMetadata("solarDisk")
        solarDiskMeta.isPresent()
        solarDiskMeta.get().getDisplayTitle("en") == "Solar Disk"
        solarDiskMeta.get().getDisplayTitle("fr") == "Disque solaire"
        solarDiskMeta.get().getDisplayDescription("en") == "The processed solar disk image"
        solarDiskMeta.get().getDisplayDescription("fr") == "Image du disque solaire traitée"

        def prominencesMeta = result.getOutputMetadata("prominences")
        prominencesMeta.isPresent()
        prominencesMeta.get().getDisplayTitle("en") == "Prominences"
        prominencesMeta.get().getDisplayDescription("en") == null  // No description provided
    }

    def "outputs metadata fallback logic works correctly"() {
        def script = """meta {
    outputs {
        shorthandOutput {
            title = "Default Title"
            description = "Default Description"
        }
        englishOutput {
            title {
                en = "English Title"
                de = "German Title"
            }
        }
        noTitleOutput {
            description {
                en = "Only description"
            }
        }
    }
}"""
        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.outputsMetadata.size() == 3

        // Test shorthand output - falls back to "default" key
        def shorthandMeta = result.getOutputMetadata("shorthandOutput").get()
        shorthandMeta.getDisplayTitle("fr") == "Default Title"  // Falls back to default
        shorthandMeta.getDisplayTitle("en") == "Default Title"  // Falls back to default
        shorthandMeta.getDisplayDescription("fr") == "Default Description"

        // Test English output - falls back to English when language not found
        def englishMeta = result.getOutputMetadata("englishOutput").get()
        englishMeta.getDisplayTitle("fr") == "English Title"  // Falls back to English
        englishMeta.getDisplayTitle("en") == "English Title"
        englishMeta.getDisplayTitle("de") == "German Title"

        // Test output with no title - returns variable name
        def noTitleMeta = result.getOutputMetadata("noTitleOutput").get()
        noTitleMeta.getDisplayTitle("en") == "noTitleOutput"  // Falls back to name
        noTitleMeta.getDisplayDescription("en") == "Only description"
    }

    def "parses mixed params and outputs sections"() {
        def script = """meta {
    params {
        gamma {
            type = "number"
            default = 1.5
        }
    }
    outputs {
        result {
            title {
                en = "Result Image"
            }
        }
    }
}

[[single]]
result = adjust_gamma(img(0), gamma)"""
        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then:
        result != null
        result.hasParametersSection()
        result.parameters.size() == 1
        result.parameters[0].name == "gamma"

        result.outputsMetadata.size() == 1
        result.getOutputMetadata("result").isPresent()
        result.getOutputMetadata("result").get().getDisplayTitle("en") == "Result Image"
    }

    def "section named outputs is correctly identified"() {
        def script = """a=img(0)
b=img(10)

[outputs]
my=anim(list(a;b))"""
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def sections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)

        then:
        sections.size() == 2
        // First section is unnamed
        sections[0].name().isEmpty()
        // Second section should be named "outputs"
        sections[1].name().isPresent()
        sections[1].name().get() == "outputs"
    }
}