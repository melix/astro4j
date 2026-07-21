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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.params.ConditionalFlip
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReferencePolynomialSelectionTest extends Specification {

    private static final LocalDateTime PIVOT = LocalDateTime.of(2026, 7, 20, 12, 33, 0)

    def "does not select a reference on the other side of the pivot date"() {
        given: "a saturated scan at 12:23, references at 12:20 and 12:33, pivot at 12:33"
        def entries = [
                entry("/refs/before.ser", "2026-07-20T12:20:00"),
                entry("/refs/after.ser", "2026-07-20T12:33:00")
        ]
        def conditions = [new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER)]

        when:
        def selection = ReferencePolynomialProvider.selectReference(entries, utc("2026-07-20T12:23:00"), "/scans/current.ser", conditions)

        then: "the nearest reference in time (12:33) is rejected, the 12:20 one is used"
        selection.nearest().path() == "/refs/before.ser"
        selection.excludedByPivot() == 1
    }

    def "reports no candidate when all references are on the other side"() {
        given:
        def entries = [entry("/refs/after.ser", "2026-07-20T12:33:00")]
        def conditions = [new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER)]

        when:
        def selection = ReferencePolynomialProvider.selectReference(entries, utc("2026-07-20T12:23:00"), "/scans/current.ser", conditions)

        then:
        selection.nearest() == null
        selection.excludedByPivot() == 1
    }

    def "selects the nearest reference when no conditional flip is active"() {
        given:
        def entries = [
                entry("/refs/before.ser", "2026-07-20T12:20:00"),
                entry("/refs/after.ser", "2026-07-20T12:33:00")
        ]

        when:
        def selection = ReferencePolynomialProvider.selectReference(entries, utc("2026-07-20T12:30:00"), "/scans/current.ser", [])

        then:
        selection.nearest().path() == "/refs/after.ser"
        selection.excludedByPivot() == 0
    }

    def "excludes the current file from the candidates"() {
        given:
        def entries = [entry("/scans/current.ser", "2026-07-20T12:23:00")]

        when:
        def selection = ReferencePolynomialProvider.selectReference(entries, utc("2026-07-20T12:23:00"), "/scans/current.ser", [])

        then:
        selection.nearest() == null
    }

    def "checks each axis pivot independently"() {
        given: "an horizontal pivot at 12:33 and a vertical pivot at 14:00"
        def entries = [
                entry("/refs/before-both.ser", "2026-07-20T12:20:00"),
                entry("/refs/between.ser", "2026-07-20T13:00:00"),
                entry("/refs/after-both.ser", "2026-07-20T14:30:00")
        ]
        def conditions = [
                new ConditionalFlip(PIVOT, ConditionalFlip.Mode.FLIP_AFTER),
                new ConditionalFlip(LocalDateTime.of(2026, 7, 20, 14, 0, 0), ConditionalFlip.Mode.FLIP_BEFORE)
        ]

        when: "processing a scan between the two pivots"
        def selection = ReferencePolynomialProvider.selectReference(entries, utc("2026-07-20T12:45:00"), "/scans/current.ser", conditions)

        then: "only the reference between the two pivots is acceptable"
        selection.nearest().path() == "/refs/between.ser"
        selection.excludedByPivot() == 2
    }

    private static ReferencePolynomialProvider.ReferenceEntry entry(String path, String date) {
        new ReferencePolynomialProvider.ReferenceEntry(path, utc(date))
    }

    private static ZonedDateTime utc(String date) {
        LocalDateTime.parse(date).atZone(ZoneOffset.UTC)
    }
}
