/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.params.AutoStretchParams
import me.champeau.a4j.jsolex.processing.params.ClaheParams
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory
import me.champeau.a4j.jsolex.processing.util.MetadataIO
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.stretching.StretchingTestImages.noise

class StretchRecipeTest extends Specification {

    def "default steps use the default value of each parameter"() {
        when:
        def step = StretchStep.defaultsOf(StretchStepKind.AUTOSTRETCH)

        then:
        step.value("gamma") == AutohistogramStrategy.DEFAULT_GAMMA
        step.value("background.threshold") == AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD
        step.value("protus.stretch") == AutohistogramStrategy.DEFAULT_PROM_STRETCH
    }

    def "values are brought into the range of their parameter"() {
        given:
        def step = StretchStep.defaultsOf(kind)

        expect:
        step.withValue(name, value).value(name) == expected

        where:
        kind                         | name        | value | expected
        StretchStepKind.LINEAR       | "high"      | 300   | 255
        StretchStepKind.LINEAR       | "low"       | -4    | 0
        StretchStepKind.LINEAR       | "low"       | 12.6  | 13
        StretchStepKind.CLAHE        | "tile.size" | 100   | 128
        StretchStepKind.CLAHE        | "bins"      | 20    | 32
        StretchStepKind.CLAHE2       | "clip"      | 2.25  | 2.25
        StretchStepKind.SHARPEN      | "size"      | 4     | 3
        StretchStepKind.BLUR         | "size"      | 18    | 15
        StretchStepKind.UNSHARP_MASK | "strength"  | 7     | 5
    }

    def "rejects a step with a wrong number of values"() {
        when:
        StretchStep.of(StretchStepKind.LINEAR, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "default linear step is the linear stretch of the image viewer"() {
        given:
        def image = noise(1)
        def expected = image.copy()
        new StretchingChain(RangeExpansionStrategy.DEFAULT, ContrastAdjustmentStrategy.DEFAULT, RangeExpansionStrategy.DEFAULT).stretch(expected)

        when:
        StretchStep.defaultsOf(StretchStepKind.LINEAR).toStrategy().stretch(image)

        then:
        image.data() == expected.data()
    }

    def "contrast enhancement techniques produce the same image as their strategy"() {
        given:
        def image = noise(2)
        def expected = image.copy()
        strategy.stretch(expected)

        when:
        StretchRecipe.forContrastEnhancement(enhancement, new ClaheParams(16, 128, 1.2), new AutoStretchParams(2.0, 0.3, 1.5)).applyTo(image)

        then:
        image.data() == expected.data()

        where:
        enhancement                     | strategy
        ContrastEnhancement.AUTOSTRETCH | new AutohistogramStrategy(2.0, true, 0.3, 1.5)
        ContrastEnhancement.CLAHE       | new ClaheStrategy(16, 128, 1.2)
        ContrastEnhancement.CLAHE2      | new AdaptativeClaheStrategy(1.2)
    }

    def "applying a recipe records each step in the transformation history"() {
        given:
        def image = noise(3)
        def recipe = StretchRecipe.forContrastEnhancement(ContrastEnhancement.CLAHE, new ClaheParams(16, 128, 1.2), null)
                .append(StretchStep.defaultsOf(StretchStepKind.LINEAR))

        when:
        recipe.applyTo(image)

        then:
        image.findMetadata(TransformationHistory).get().transforms() == [
                "CLAHE (tile size: 16, clip limit: 1.2, bins: 128)",
                "Linear (low: 0, high: 255)"
        ]
    }

    def "missing parameters give an empty recipe"() {
        expect:
        StretchRecipe.forContrastEnhancement(ContrastEnhancement.CLAHE, null, null).isEmpty()
        StretchRecipe.forContrastEnhancement(ContrastEnhancement.AUTOSTRETCH, null, null).isEmpty()
    }

    def "the automatic technique must be resolved first"() {
        when:
        StretchRecipe.forContrastEnhancement(ContrastEnhancement.AUTO, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "recipes are edited by copy"() {
        given:
        def linear = StretchStep.defaultsOf(StretchStepKind.LINEAR)
        def curve = StretchStep.defaultsOf(StretchStepKind.CURVE)
        def clahe2 = StretchStep.defaultsOf(StretchStepKind.CLAHE2)
        def recipe = StretchRecipe.of(linear, curve)

        expect:
        recipe.append(clahe2).steps() == [linear, curve, clahe2]
        recipe.remove(0).steps() == [curve]
        recipe.replace(1, clahe2).steps() == [linear, clahe2]
        recipe.prefix(1).steps() == [linear]
        recipe.steps() == [linear, curve]
    }

    def "only AutoStretch requires the solar disk"() {
        expect:
        StretchStepKind.values().findAll { it.requiresSolarDisk() } == [StretchStepKind.AUTOSTRETCH]
    }

    def "multi-scale CLAHE tile sizes halve down to the minimum tile size"() {
        expect:
        AdaptativeClaheStrategy.computeTileSizes(rawStart, 256, 6) == expected

        where:
        rawStart | expected
        400      | [256, 128, 64, 32, 16]
        5        | [16]
    }

    def "an image carrying a recipe is materialized as a stretched copy without the recipe"() {
        given:
        def image = noise(4)
        def recipe = StretchRecipe.of(StretchStep.defaultsOf(StretchStepKind.CLAHE2))
        image.metadata().put(StretchRecipe, recipe)
        def expected = image.copy()
        new AdaptativeClaheStrategy(AdaptativeClaheStrategy.DEFAULT_CLIP).stretch(expected)

        when:
        def materialized = StretchRecipe.materialize(image)

        then:
        !materialized.is(image)
        materialized.data() == expected.data()
        materialized.findMetadata(StretchRecipe).isEmpty()
        materialized.findMetadata(TransformationHistory).get().transforms() == ["CLAHE2 (clip limit: 1.5)"]

        and: "the source image is left untouched"
        image.findMetadata(StretchRecipe).get() == recipe
        image.findMetadata(TransformationHistory).isEmpty()
    }

    def "an image without a recipe is not copied"() {
        given:
        def image = noise(5)

        expect:
        StretchRecipe.materialize(image).is(image)
    }

    def "recipes survive a session round trip"() {
        given:
        def recipe = StretchRecipe.forContrastEnhancement(ContrastEnhancement.AUTOSTRETCH, null, new AutoStretchParams(2.0, 0.3, 1.5))
                .append(StretchStep.defaultsOf(StretchStepKind.CLAHE))
        def metadata = [(StretchRecipe): recipe] as Map<Class<?>, Object>

        when:
        def restored = MetadataIO.deserialize(MetadataIO.serialize(metadata))

        then:
        restored[StretchRecipe] == recipe
    }
}
