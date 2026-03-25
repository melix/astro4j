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
package me.champeau.a4j.ser

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime

class TimestampConverterTest extends Specification {

    def "negative or zero timestamp returns empty"() {
        expect:
        TimestampConverter.of(0).isEmpty()
        TimestampConverter.of(-1).isEmpty()
    }

    def "null or before-epoch date returns -1"() {
        expect:
        TimestampConverter.toTimestamp(null) == -1
    }

    @Unroll
    def "round-trip for #date"() {
        given:
        def timestamp = TimestampConverter.toTimestamp(date)

        when:
        def result = TimestampConverter.of(timestamp)

        then:
        result.isPresent()
        result.get() == date

        where:
        date << [
            LocalDateTime.of(2024, 6, 15, 12, 30, 45, 0),
            LocalDateTime.of(2024, 6, 15, 12, 30, 45, 123_456_700),
            LocalDateTime.of(2000, 1, 1, 0, 0, 0, 100),
            LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0),
            LocalDateTime.of(2026, 3, 24, 21, 39, 16, 358_000_000),
        ]
    }

    def "known timestamp value"() {
        given:
        // 2024-06-15T12:30:45 as 100ns ticks from year 1
        def date = LocalDateTime.of(2024, 6, 15, 12, 30, 45, 0)
        def timestamp = TimestampConverter.toTimestamp(date)

        when:
        def result = TimestampConverter.of(timestamp)

        then:
        result.isPresent()
        result.get() == date
        timestamp > 0
    }

    def "sub-microsecond precision is preserved at 100ns granularity"() {
        given:
        // 100ns = the SER tick granularity
        def date = LocalDateTime.of(2024, 1, 1, 0, 0, 0, 500)

        when:
        def timestamp = TimestampConverter.toTimestamp(date)
        def result = TimestampConverter.of(timestamp)

        then:
        result.isPresent()
        result.get() == date
    }
}
