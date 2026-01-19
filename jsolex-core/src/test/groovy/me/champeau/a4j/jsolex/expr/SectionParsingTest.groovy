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

import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor
import spock.lang.Specification

/**
 * Regression tests for script section parsing.
 * These tests ensure that section names (especially [outputs]) are correctly identified
 * and that the script executor correctly distinguishes between output and non-output sections.
 */
class SectionParsingTest extends Specification {

    def "complex script with meta, tmp, outputs and batch sections parses correctly"() {
        given:
        def script = '''meta {
  title {
    en = "Aggressive H-alpha Stacking with Unsharp Mask"
    fr = "Stacking H-alpha avec traitement masque flou"
  }
  description {
    en = "Used in batch mode, this script will automatically stack images and generate: a stacked image with classic processing, an enhanced image, a negative image, prominences, Doppler and Doppler eclipse, as well as continuum and active regions"
    fr = "En mode batch, ce script va automatiquement empiler vos images et générer : une image classique, une avec un traitement avancé, une version négative, une des protubérances, les images Doppler et éclipse Doppler, ainsi que des images du continuum et des régions actives"
  }
  author = "Cédric Champeau"
  version = "1.3"
  requires = "4.4.3"
  params {
    tile_size {
      type = "choice"
      name {
        en = "Tile Size"
        fr = "Taille de tuile"
      }
      default = "64"
      choices = "16,32,64,128,256"
    }
    gamma {
      type = "number"
      name {
        en = "Gamma"
        fr = "Gamma"
      }
      default = 1.5
      min = 0.5
    }
  }
  outputs {
    red {
      title {
        en = "Red wing"
        fr = "Aile rouge"
      }
      description {
        en = "The red wing image"
        fr = "L'image de l'aile rouge"
      }
    }
    doppler {
      title {
        en = "Doppler"
        fr = "Doppler"
      }
      description {
        en = "A Doppler stacked image"
        fr = "Une image Doppler empilée"
      }
    }
    blue {
      title {
        en = "Blue wing"
        fr = "Aile bleue"
      }
      description {
        en = "The blue wing image."
        fr = "L'image sur l'aile bleue"
      }
    }
  }
}

[tmp]
shift=a2px(doppler_shift)
r=range(-1;1;.5)
c=img(a2px(continuum_shift))

[outputs]
base=avg(r)
conti=c
red=img(-shift)
blue=img(shift)

[[batch]]

[tmp]
kernel_size=ifeq(binning();1;7;3)
rescaled=radius_rescale(base)

[outputs]
doppler=saturate(rgb(stack_r;min(stack_r;stack_b);stack_b);.5)
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        def batchSections = root.findSections(ImageMathScriptExecutor.SectionKind.BATCH)

        then: "single mode sections are correctly identified"
        singleSections.size() == 2

        and: "first single section is [tmp]"
        singleSections[0].name().isPresent()
        singleSections[0].name().get() == "tmp"

        and: "second single section is [outputs]"
        singleSections[1].name().isPresent()
        singleSections[1].name().get() == "outputs"

        and: "batch mode sections are correctly identified"
        batchSections.size() == 3

        and: "first batch section is [[batch]]"
        batchSections[0].name().isPresent()
        batchSections[0].name().get() == "batch"

        and: "second batch section is [tmp]"
        batchSections[1].name().isPresent()
        batchSections[1].name().get() == "tmp"

        and: "third batch section is [outputs]"
        batchSections[2].name().isPresent()
        batchSections[2].name().get() == "outputs"
    }

    def "outputs metadata is correctly extracted from meta block"() {
        given:
        def script = '''meta {
  outputs {
    red {
      title {
        en = "Red wing"
        fr = "Aile rouge"
      }
      description {
        en = "The red wing image"
        fr = "L'image de l'aile rouge"
      }
    }
    doppler {
      title {
        en = "Doppler"
        fr = "Doppler"
      }
    }
    blue {
      title {
        en = "Blue wing"
        fr = "Aile bleue"
      }
      description {
        en = "The blue wing image."
        fr = "L'image sur l'aile bleue"
      }
    }
  }
}

[tmp]
shift=10

[outputs]
red=img(-shift)
blue=img(shift)
doppler=rgb(red;blue;blue)
'''
        def extractor = new ImageMathParameterExtractor()

        when:
        def result = extractor.extractParameters(script)

        then: "outputs metadata is extracted"
        result.outputsMetadata.size() == 3

        and: "red output metadata is correct"
        def redMeta = result.getOutputMetadata("red")
        redMeta.isPresent()
        redMeta.get().getDisplayTitle("en") == "Red wing"
        redMeta.get().getDisplayTitle("fr") == "Aile rouge"
        redMeta.get().getDisplayDescription("en") == "The red wing image"
        redMeta.get().getDisplayDescription("fr") == "L'image de l'aile rouge"

        and: "doppler output metadata is correct (no description)"
        def dopplerMeta = result.getOutputMetadata("doppler")
        dopplerMeta.isPresent()
        dopplerMeta.get().getDisplayTitle("en") == "Doppler"
        dopplerMeta.get().getDisplayTitle("fr") == "Doppler"
        dopplerMeta.get().getDisplayDescription("en") == null

        and: "blue output metadata is correct"
        def blueMeta = result.getOutputMetadata("blue")
        blueMeta.isPresent()
        blueMeta.get().getDisplayTitle("en") == "Blue wing"
        blueMeta.get().getDisplayTitle("fr") == "Aile bleue"
    }

    def "unnamed section followed by outputs section correctly identifies outputs"() {
        given: "a script with an unnamed section followed by an explicit [outputs] section"
        def script = '''a=img(0)
b=img(10)

[outputs]
my=anim(list(a;b))
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def sections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)

        then: "there are two sections"
        sections.size() == 2

        and: "first section is unnamed"
        sections[0].name().isEmpty()

        and: "second section is named 'outputs'"
        sections[1].name().isPresent()
        sections[1].name().get() == "outputs"

        and: "only output section contains the 'my' assignment"
        def outputAssignments = sections[1].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment)
        outputAssignments.size() == 1
        outputAssignments[0].variableName().get() == "my"

        and: "unnamed section contains 'a' and 'b' assignments"
        def unnamedAssignments = sections[0].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment)
        unnamedAssignments.size() == 2
        unnamedAssignments.collect { it.variableName().get() } as Set == ["a", "b"] as Set
    }

    def "params section is correctly identified"() {
        given:
        def script = '''[params]
gamma=1.5

[outputs]
result=img(0)
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def sections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)

        then: "params section is recognized"
        sections.any { it.name().orElse("") == "params" }

        and: "outputs section is recognized"
        sections.any { it.name().orElse("") == "outputs" }
    }
}
