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

import me.champeau.a4j.jsolex.expr.ast.Section
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

    def "handles params sections without throwing NPE"() {
        given:
        // Using the exact script from /tmp/test.math that caused the NPE
        var scriptWithParamsSection = """#********************************************************
#                 Script Jsolex
#             image HA - Protu - continuum
#            Ha color - negatif - crop protu
#********************************************************


#--------------------------------------------------------
# fonctions
#--------------------------------------------------------
[fun: doppler angstroms]

        shift=a2px(angstroms)
        red=auto_contrast(img(-shift);1.5)
        blue=auto_contrast(img(shift);1.5)
        green=min(red;blue)
	rgb=rgb(red;green;blue)
	result=saturate(rgb;0.5)

[params]
#--------------------------------------------------------
# variables
#--------------------------------------------------------
base=avg(range(-2;2;.5))
conti=continuum()
base_bg=bg_model(base;3;3)
puissance=1.5


[outputs]
#--------------------------------------------------------
#mode coro - eclipse artificielle
#--------------------------------------------------------
protus=asinh_stretch(disk_fill(base);0;10)


#--------------------------------------------------------
#disque en Ha
#--------------------------------------------------------
Ha=draw_text(draw_obs_details(max(protus;linear_stretch(adjust_contrast(sharpen(linear_stretch(pow(rl_decon(base);puissance));7);0;180)));100;100);100;1515;"(C) %OBSERVER% ")

//fiche_tech=draw_globe(draw_solar_params(Ha;100;1325))
Ech_Earth=draw_text(draw_arrow(draw_earth(draw_solar_params(Ha;100;1325);1280;1350);1350;1300;1306;1334);1362;1289;"TERRE")
"""

        when:
        var parser = new ImageMathParser(scriptWithParamsSection)
        var root = parser.parseAndInlineIncludes()
        var sections = root.childrenOfType(Section)

        then:
        root != null
        sections != null
        sections.each { section ->
            // This should not throw NPE for any section including [params]
            section.name()
        }
    }

    def "all template scripts should parse without errors"() {
        given:
        def templatesDir = new File("../jsolex/src/main/resources/me/champeau/a4j/jsolex/templates")
        def templateFiles = templatesDir.listFiles { File file -> file.name.endsWith(".math") }

        expect:
        templateFiles != null
        templateFiles.length > 0

        when:
        def parseResults = templateFiles.collectEntries { File templateFile ->
            try {
                def script = templateFile.text
                def parser = new ImageMathParser(script)
                parser.includeDir = templateFile.parentFile.toPath()
                def root = parser.parseAndInlineIncludes()

                // Also verify we can get sections without NPE
                def sections = root.childrenOfType(Section)
                sections.each { section ->
                    section.name() // Should not throw NPE
                }

                [templateFile.name, "SUCCESS"]
            } catch (Exception e) {
                [templateFile.name, "FAILED: ${e.class.simpleName} - ${e.message}"]
            }
        }

        then:
        parseResults.each { fileName, result ->
            assert result == "SUCCESS" : "Failed to parse ${fileName}: ${result}"
        }
    }
}
