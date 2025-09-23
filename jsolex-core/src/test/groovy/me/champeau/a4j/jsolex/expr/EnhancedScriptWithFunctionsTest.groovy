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

class EnhancedScriptWithFunctionsTest extends Specification {

    def "can have both a meta block and functions"() {
        given:
        var scriptContent = '''meta {
    title="Test"
    requires="5.0.0"
    params {
        gamma {
            type = "number"
            name = "Gamma Correction"
            description {
                en = "Gamma correction value for contrast adjustment"
                fr = "Valeur de correction gamma pour l'ajustement du contraste"
            }
            default = 1.5
            min = 0.5
            max = 100
        }
        tileSize {
            type = "choice"
            name {
                en = "Tile Size"
                fr = "Taille de tuile"
            }
            description {
                en = "Tile size for processing (smaller = more detail, larger = faster)"
                fr = "Taille de tuile pour le traitement (plus petit = plus de détails, plus grand = plus rapide)"
            }
            default = "32"
            choices = "16,32,64,128"
        }

        sampling {
            type = "number"
            name {
                en = "Sampling Rate"
                fr = "Taux d'échantillonnage"
            }
            description {
                en = "Sampling rate for processing (lower = faster but less accurate)"
                fr = "Taux d'échantillonnage pour le traitement (plus bas = plus rapide mais moins précis)"
            }
            default = 0.25
            min = 0.1
            max = 1.0
        }
    }
}

[fun:foo i]
   result=img(i)

[tmp]
v=foo(2)
denoised=avg(range(-1;1))

[outputs]
processed=draw_text(denoised;100;100; "" + gamma)

[[batch]]
[outputs]
stacked=draw_text(auto_contrast(stack(processed;tileSize;sampling);gamma);100;100; "" + gamma)
'''
        var extractor = new ImageMathParameterExtractor()

        when:
        var result = extractor.extractParameters(scriptContent)

        then:
        println("=== PARAMETER EXTRACTION RESULT ===")
        println("Parameters found: ${result.parameters.size()}")
        result.parameters.each { param ->
            println("  - ${param.name}: ${param.class.simpleName} (default: ${param.defaultValue})")
        }
        println("Has parameters section: ${result.hasParametersSection()}")
        println("Required version: ${result.requiredVersion}")
        println("Title: ${result.title}")

        and:
        result.hasParametersSection()
        result.parameters.size() == 3
        result.parameters.find { it.name == "gamma" }?.defaultValue == 1.5
        result.parameters.find { it.name == "tileSize" }?.defaultValue == "32"
        result.parameters.find { it.name == "sampling" }?.defaultValue == 0.25
        result.requiredVersion == "5.0.0"
    }

    def "should inject parameter default values during script execution"() {
        given:
        var scriptContent = '''meta {
    params {
        gamma {
            type = "number"
            default = 1.5
        }
    }
}

[outputs]
result = gamma
'''

        when:
        var parser = new ImageMathParser(scriptContent)
        var script = parser.parseAndInlineIncludes()
        var topLevelParams = script.getTopLevelParameterDefs()

        then:
        println("Top level parameters found: ${topLevelParams.size()}")
        topLevelParams.each { param ->
            println("  - ${param.name}: ${param.objectValue?.getProperty('default')}")
        }
        topLevelParams.size() == 1
        topLevelParams[0].name == "gamma"
        topLevelParams[0].objectValue?.getNumberProperty("default")?.orElse(null) == 1.5
    }
}