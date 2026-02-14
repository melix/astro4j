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

    def "minimal batch mode test without multiline strings"() {
        given: "a simple batch script without multiline strings"
        def script = '''[outputs]
a = img(0)
b = img(1)

[[batch]]
[outputs]
c = median(a)
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        def batchSections = root.findSections(ImageMathScriptExecutor.SectionKind.BATCH)

        then: "single section has a and b"
        singleSections.size() == 1
        singleSections[0].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment).size() == 2

        and: "batch has [[batch]] and [outputs] with c"
        batchSections.size() == 2
    }

    def "batch mode with one multiline Python string"() {
        given: "script with one multiline Python string before batch"
        def script = '''[outputs]
a = python("""
result = 1 + 2
""")
b = img(1)

[[batch]]
[outputs]
c = median(a)
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        def batchSections = root.findSections(ImageMathScriptExecutor.SectionKind.BATCH)

        then: "single section has a and b"
        singleSections.size() == 1
        singleSections[0].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment).size() == 2

        and: "batch has [[batch]] and [outputs] with c"
        batchSections.size() == 2
    }

    def "two consecutive multiline strings without batch"() {
        given: "script with two multiline strings - no batch section"
        def script = '''[outputs]
a = python("""
result = 1
""")
b = python("""
result = 2
""")
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)

        then: "section has a and b"
        singleSections.size() == 1
        def singleAssignments = singleSections[0].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment)
        singleAssignments.size() == 2
        singleAssignments.collect { it.variableName().orElse('<anon>') } as Set == ["a", "b"] as Set
    }

    def "batch mode with Python multiline strings correctly separates sections"() {
        given: "the exact script from the documentation"
        def script = '''[outputs]
# Process each file with Python, compute and print statistics
processed = python("""
import jsolex
import numpy as np

img = jsolex.funcs.img(ps=0)
data = jsolex.toNumpy(img)
ellipse = jsolex.getEllipseParams(img)
source = jsolex.getSourceInfo()

# Compute statistics within the solar disk
cx, cy = int(ellipse["centerX"]), int(ellipse["centerY"])
radius = int(ellipse["radius"] * 0.95)

y, x = np.ogrid[:data.shape[0], :data.shape[1]]
mask = (x - cx)**2 + (y - cy)**2 <= radius**2
disk_data = data[mask]

mean_val = np.mean(disk_data)
std_val = np.std(disk_data)
contrast = std_val / mean_val

# Print statistics for this file
print(f"{source['fileName']}: mean={mean_val:.1f}, std={std_val:.1f}, contrast={contrast:.4f}")

# Return autocropped image for stacking
result = jsolex.funcs.autocrop2(img, 1.1, 32)
""")

# Return statistics as separate variables (will become lists in batch section)
mean_intensity = python("""
import jsolex
import numpy as np
img = jsolex.funcs.img(ps=0)
data = jsolex.toNumpy(img)
ellipse = jsolex.getEllipseParams(img)
cx, cy = int(ellipse["centerX"]), int(ellipse["centerY"])
radius = int(ellipse["radius"] * 0.95)
y, x = np.ogrid[:data.shape[0], :data.shape[1]]
mask = (x - cx)**2 + (y - cy)**2 <= radius**2
result = float(np.mean(data[mask]))
""")

contrast = python("""
import jsolex
import numpy as np
img = jsolex.funcs.img(ps=0)
data = jsolex.toNumpy(img)
ellipse = jsolex.getEllipseParams(img)
cx, cy = int(ellipse["centerX"]), int(ellipse["centerY"])
radius = int(ellipse["radius"] * 0.95)
y, x = np.ogrid[:data.shape[0], :data.shape[1]]
mask = (x - cx)**2 + (y - cy)**2 <= radius**2
disk_data = data[mask]
result = float(np.std(disk_data) / np.mean(disk_data))
""")

[[batch]]
[outputs]
# Generate summary graph from collected statistics
summary = python("""
import jsolex
import numpy as np
import matplotlib.pyplot as plt
from io import BytesIO
from PIL import Image

# Get aggregated statistics (lists, one value per file)
means = jsolex.vars.mean_intensity
contrasts = jsolex.vars.contrast

# Create summary plot
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8))

x = range(len(means))
ax1.bar(x, means, color='steelblue')
ax1.set_ylabel('Mean Intensity')
ax1.set_title('Batch Statistics Summary')
ax1.set_xticks(x)
ax1.set_xticklabels([f'File {i+1}' for i in x], rotation=45)

ax2.bar(x, contrasts, color='coral')
ax2.set_ylabel('Contrast (std/mean)')
ax2.set_xlabel('File')
ax2.set_xticks(x)
ax2.set_xticklabels([f'File {i+1}' for i in x], rotation=45)

plt.tight_layout()

# Convert plot to image
buf = BytesIO()
plt.savefig(buf, format='png', dpi=150)
buf.seek(0)
plot_img = np.array(Image.open(buf))[:, :, :3]
plt.close()

# Convert to grayscale and scale to 0-65535
gray = plot_img.mean(axis=2).astype(np.float32) * 257.0
jsolex.emit(jsolex.fromNumpy(gray), "Batch Statistics")
""")

# Combine all processed images using median stacking
stacked = sharpen(median(processed))
'''
        def parser = new ImageMathParser(script)

        when:
        def root = parser.parseAndInlineIncludes()
        def singleSections = root.findSections(ImageMathScriptExecutor.SectionKind.SINGLE)
        def batchSections = root.findSections(ImageMathScriptExecutor.SectionKind.BATCH)

        then: "single mode has exactly one section (outputs)"
        singleSections.size() == 1

        and: "single section is named 'outputs'"
        singleSections[0].name().isPresent()
        singleSections[0].name().get() == "outputs"

        and: "single section contains 3 assignments: processed, mean_intensity, contrast"
        def singleAssignments = singleSections[0].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment)
        singleAssignments.size() == 3
        singleAssignments.collect { it.variableName().orElse("<anon>") } as Set == ["processed", "mean_intensity", "contrast"] as Set

        and: "batch mode has exactly 2 sections"
        batchSections.size() == 2

        and: "first batch section is [[batch]]"
        batchSections[0].name().isPresent()
        batchSections[0].name().get() == "batch"

        and: "second batch section is [outputs]"
        batchSections[1].name().isPresent()
        batchSections[1].name().get() == "outputs"

        and: "batch outputs section contains 2 assignments: summary, stacked"
        def batchAssignments = batchSections[1].childrenOfType(me.champeau.a4j.jsolex.expr.ast.Assignment)
        batchAssignments.size() == 2
        batchAssignments.collect { it.variableName().orElse("<anon>") } as Set == ["summary", "stacked"] as Set
    }
}
