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

class ScriptProcessParamsOverridesTest extends Specification {

    def "process params survive a json round trip"() {
        given:
        var gson = ProcessParamsIO.newGsonBuilder().create()
        var params = ProcessParamsIO.createNewDefaults()

        when:
        var tree = gson.toJsonTree(params)
        var restored = gson.fromJson(tree, ProcessParams)

        then:
        gson.toJsonTree(restored) == tree
    }

    def "image math and python scripts declare the same overrides"() {
        given:
        var imageMath = """
meta {
    title = "Corona"
    overrides {
        bandingCorrectionParams {
            passes = 0
        }
        geometryParams {
            autocropMode = "RADIUS_1_5"
        }
    }
}

[outputs]
result = img(0)
"""
        var python = '''
# meta:title = "Corona"
# meta:overrides:bandingCorrectionParams.passes = 0
# meta:overrides:geometryParams.autocropMode = "RADIUS_1_5"

def process():
    pass
'''

        when:
        var fromImageMath = new ImageMathParameterExtractor().extractParameters(imageMath, "script.math").processParamsOverrides
        var fromPython = new PythonParameterExtractor().extractParameters(python, "script.py").processParamsOverrides

        then:
        fromImageMath == fromPython
        fromImageMath.getAsJsonObject("bandingCorrectionParams").get("passes").asInt == 0
        fromImageMath.getAsJsonObject("geometryParams").get("autocropMode").asString == "RADIUS_1_5"
    }

    def "overrides replace the values configured by the user"() {
        given:
        var params = ProcessParamsIO.createNewDefaults()
                .withBandingCorrectionParams(new BandingCorrectionParams(24, 4))
                .withGeometryParams(ProcessParamsIO.createNewDefaults().geometryParams().withAutocropMode(AutocropMode.RADIUS_1_2))
        var overrides = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        bandingCorrectionParams {
            passes = 0
        }
        geometryParams {
            autocropMode = "RADIUS_1_5"
        }
    }
}

[outputs]
result = img(0)
""", "script.math").processParamsOverrides

        when:
        var updated = ScriptProcessParamsOverrides.apply(params, overrides)

        then: "the overridden values are replaced"
        updated.bandingCorrectionParams().passes() == 0
        updated.geometryParams().autocropMode() == AutocropMode.RADIUS_1_5

        and: "the values which are not overridden are preserved"
        updated.bandingCorrectionParams().width() == 24
        updated.spectrumParams() == params.spectrumParams()
    }

    def "unknown parameters and invalid values are ignored"() {
        given:
        var params = ProcessParamsIO.createNewDefaults()
        var overrides = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        $declaration
    }
}

[outputs]
result = img(0)
""", "script.math").processParamsOverrides

        when:
        var updated = ScriptProcessParamsOverrides.apply(params, overrides)

        then:
        ProcessParamsIO.serializeToJson(updated) == ProcessParamsIO.serializeToJson(params)

        where:
        declaration << [
                'notAParamsGroup { passes = 0 }',
                'geometryParams { autocropMode = "NOT_A_MODE" }'
        ]
    }

    def "boolean parameters can be overridden"() {
        given:
        var params = ProcessParamsIO.createNewDefaults()
        var overrides = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        geometryParams {
            spectrumVFlip = "true"
        }
    }
}

[outputs]
result = img(0)
""", "script.math").processParamsOverrides

        expect: "booleans are quoted in scripts but typed in the configuration"
        overrides.getAsJsonObject("geometryParams").get("spectrumVFlip").asBoolean
        !params.geometryParams().isSpectrumVFlip()
        ScriptProcessParamsOverrides.apply(params, overrides).geometryParams().isSpectrumVFlip()
    }

    def "overrides can be flattened to paths and back"() {
        given:
        var overrides = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        bandingCorrectionParams {
            passes = 0
            width = 64
        }
        geometryParams {
            autocropMode = "RADIUS_1_5"
        }
    }
}

[outputs]
result = img(0)
""", "script.math").processParamsOverrides

        when:
        var flattened = ScriptProcessParamsOverrides.flatten(overrides)

        then:
        flattened == [
                "bandingCorrectionParams.passes": "0",
                "bandingCorrectionParams.width" : "64",
                "geometryParams.autocropMode"   : "RADIUS_1_5"
        ]

        and:
        ScriptProcessParamsOverrides.fromFlattened(flattened) == overrides
    }

    def "overridable paths cover the parameters exposed in the configuration"() {
        when:
        var paths = ScriptProcessParamsOverrides.overridablePaths(ProcessParamsIO.createNewDefaults())

        then:
        paths.contains("bandingCorrectionParams.passes")
        paths.contains("bandingCorrectionParams.width")
        paths.contains("geometryParams.autocropMode")
    }

    def "the first script wins when scripts disagree"() {
        given:
        var first = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        bandingCorrectionParams { passes = 0 }
    }
}

[outputs]
result = img(0)
""", "first.math").processParamsOverrides
        var second = new ImageMathParameterExtractor().extractParameters("""
meta {
    overrides {
        bandingCorrectionParams { passes = 8 width = 64 }
    }
}

[outputs]
result = img(0)
""", "second.math").processParamsOverrides

        when:
        var merged = ScriptProcessParamsOverrides.merge([first, second])
        var updated = ScriptProcessParamsOverrides.apply(ProcessParamsIO.createNewDefaults(), merged)

        then: "the conflicting value comes from the first script"
        updated.bandingCorrectionParams().passes() == 0

        and: "the value only declared by the second script is still applied"
        updated.bandingCorrectionParams().width() == 64
    }
}
