/*
 * Copyright 2023-2026 the original author or authors.
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

import java.nio.file.Files
import java.time.LocalDateTime

class ConditionalFlipTest extends Specification {

    private static final LocalDateTime PIVOT = LocalDateTime.of(2026, 7, 21, 14, 30, 0)

    def "flips the expected side of the pivot date"() {
        given:
        def flip = new ConditionalFlip(PIVOT, mode)

        expect:
        flip.flipAt(PIVOT.minusSeconds(1)) == flippedBefore
        flip.flipAt(PIVOT) == flippedAtPivot
        flip.flipAt(PIVOT.plusSeconds(1)) == flippedAtPivot

        where:
        mode                            | flippedBefore | flippedAtPivot
        ConditionalFlip.Mode.FLIP_BEFORE | true          | false
        ConditionalFlip.Mode.FLIP_AFTER  | false         | true
    }

    def "inverting a conditional flip swaps the flipped side"() {
        given:
        def flip = new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_BEFORE)

        when:
        def inverted = flip.inverted()

        then:
        inverted == new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER)
        inverted.inverted() == flip
        !inverted.flipAt(PIVOT.minusSeconds(1))
        inverted.flipAt(PIVOT)
    }

    def "same side comparison uses the pivot date"() {
        given:
        def flip = new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER)

        expect:
        flip.isSameSide(PIVOT.minusHours(1), PIVOT.minusSeconds(1))
        flip.isSameSide(PIVOT, PIVOT.plusHours(2))
        !flip.isSameSide(PIVOT.minusSeconds(1), PIVOT)
    }

    def "resolving replaces conditions with plain mirror flags"() {
        given:
        def geometry = ProcessParamsIO.createNewDefaults().geometryParams()
                .withHorizontalMirror(true)
                .withVerticalFlipCondition(new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER))

        when:
        def resolved = geometry.resolveFlipConditions(captureDate)

        then:
        resolved.verticalFlipCondition().isEmpty()
        resolved.horizontalFlipCondition().isEmpty()
        resolved.isVerticalMirror() == verticalMirror
        resolved.isHorizontalMirror()

        where:
        captureDate           | verticalMirror
        PIVOT.minusMinutes(5) | false
        PIVOT                 | true
        PIVOT.plusMinutes(5)  | true
    }

    def "resolving without conditions is a no-op"() {
        given:
        def geometry = ProcessParamsIO.createNewDefaults().geometryParams()

        expect:
        geometry.resolveFlipConditions(PIVOT).is(geometry)
    }

    def "flip conditions survive a serialization round-trip"() {
        given:
        def params = ProcessParamsIO.createNewDefaults()
        params = params.withGeometryParams(params.geometryParams()
                .withHorizontalFlipCondition(new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_BEFORE))
                .withVerticalFlipCondition(new ConditionalFlip(PIVOT.plusHours(1), ConditionalFlip.Mode.FLIP_AFTER)))
        def tempFile = Files.createTempFile("test-conditional-flip", ".json")
        tempFile.toFile().deleteOnExit()

        when:
        tempFile.toFile().text = ProcessParamsIO.serializeToJson(params)
        def loaded = ProcessParamsIO.readFrom(tempFile)

        then:
        loaded.geometryParams().horizontalFlipCondition().get() == new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_BEFORE)
        loaded.geometryParams().verticalFlipCondition().get() == new ConditionalFlip(PIVOT.plusHours(1), ConditionalFlip.Mode.FLIP_AFTER)
    }
}
