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

import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor
import spock.lang.Specification

import java.nio.file.Path

class ImageMathParserTest extends Specification {
    def "parses valid scripts"() {
        def parser = new ImageMathParser(script)
        parser.includeDir = Path.of("src/test/resources/me/champeau/a4j/jsolex/expr")

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root.dump()

        where:
        script << buildSamples()
    }

    private static List<String> buildSamples() {
        int i = 0
        List<String> samples = []
        while (true) {
            var stream = ImageMathParserTest.getResourceAsStream("script${i++}.txt")
            if (stream == null) {
                break
            }
            samples << stream.text
        }
        samples
    }

    def "should extract parameters even when functions are present"() {
        given:
        var scriptWithoutFunction = """meta {
    title="Test"
    requires="5.0.0"
    params {
        gamma {
            type = "number"
            name = "Gamma Correction"
            default = 1.5
            min = 0.5
            max = 100
        }
    }
}

[tmp]
v=1

[outputs]
processed=draw_text(v; 100; 100; "" + gamma)
"""

        var scriptWithFunction = """meta {
    title="Test"
    requires="5.0.0"
    params {
        gamma {
            type = "number"
            name = "Gamma Correction"
            default = 1.5
            min = 0.5
            max = 100
        }
    }
}

[fun:foo i]
   result=img(i)

[tmp]
v=foo(2)

[outputs]
processed=draw_text(v; 100; 100; "" + gamma)
"""

        var extractor = new ImageMathParameterExtractor()

        when:
        var resultWithoutFunction = extractor.extractParameters(scriptWithoutFunction)
        var resultWithFunction = extractor.extractParameters(scriptWithFunction)

        then:
        resultWithoutFunction.parameters.size() == 1
        resultWithoutFunction.parameters[0].name == "gamma"

        and:
        resultWithFunction.parameters.size() == 1
        resultWithFunction.parameters[0].name == "gamma"
    }
}
