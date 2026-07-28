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
package me.champeau.a4j.jsolex.processing.sun

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReferenceDirectoryListingTest extends Specification {

    @TempDir
    Path refDir

    private File touch(String name) {
        var f = refDir.resolve(name)
        Files.writeString(f, "not a real ser")
        f.toFile()
    }

    /** Derives a date from the file name, standing in for reading the SER header. */
    private static final java.util.function.Function<File, Optional<ZonedDateTime>> BY_NAME = { File f ->
        var m = (f.name =~ /(\d\d)_(\d\d)_(\d\d)\.ser/)
        m ? Optional.of(ZonedDateTime.of(2026, 7, 28,
                m[0][1] as int, m[0][2] as int, m[0][3] as int, 0, ZoneOffset.UTC)) : Optional.empty()
    }

    def "a reference appearing after a first lookup is taken into account"() {
        given: "one reference, as at the start of a live batch"
        touch("11_43_02.ser")

        when:
        var first = ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME)

        then:
        first.size() == 1

        when: "further references are captured while the batch is still running"
        touch("11_52_12.ser")
        touch("12_01_23.ser")
        var second = ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME)

        then: "they are visible immediately, without relying on the directory timestamp"
        second.size() == 3
        second*.path().collect { new File(it).name }.toSorted() == ["11_43_02.ser", "11_52_12.ser", "12_01_23.ser"]
    }

    def "a scan captured mid batch becomes the nearest reference once it exists"() {
        given:
        touch("11_43_02.ser")
        var observation = ZonedDateTime.of(2026, 7, 28, 14, 11, 53, 0, ZoneOffset.UTC)

        when: "only the early reference exists"
        var before = ReferencePolynomialProvider.selectReference(
                ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME), observation, null, [])

        then: "it is used despite being hours away"
        new File(before.nearest().path()).name == "11_43_02.ser"
        before.nearestGap().toHours() == 2

        when: "a reference captured close to the observation appears"
        touch("14_05_04.ser")
        var after = ReferencePolynomialProvider.selectReference(
                ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME), observation, null, [])

        then:
        new File(after.nearest().path()).name == "14_05_04.ser"
        after.nearestGap().toMinutes() < 10
    }

    def "unreadable references are skipped rather than failing the listing"() {
        given:
        touch("11_43_02.ser")
        touch("broken.ser")

        expect:
        ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME).size() == 1
    }

    def "the gap to the selected reference is reported in a readable way"() {
        expect:
        ReferencePolynomialProvider.formatGap(java.time.Duration.ofSeconds(42)) == "42 s"
        ReferencePolynomialProvider.formatGap(java.time.Duration.ofSeconds(102)) == "1 min"
        ReferencePolynomialProvider.formatGap(java.time.Duration.ofMinutes(59)) == "59 min"
        ReferencePolynomialProvider.formatGap(java.time.Duration.ofMinutes(148)) == "2h28m"
    }

    def "non ser files are ignored"() {
        given:
        touch("11_43_02.ser")
        Files.writeString(refDir.resolve("notes.txt"), "hello")

        expect:
        ReferencePolynomialProvider.listReferences(refDir.toFile(), BY_NAME).size() == 1
    }
}
