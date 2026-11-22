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
package me.champeau.a4j.jsolex.app.jfx

import spock.lang.Specification

class TitleOrderTest extends Specification {
    def "orders detections with their profiles"() {
        given:
        def titles = [
                "Ellerman bomb 10 (profile)",
                "Flare 1",
                "Ellerman bomb 2 (profile)",
                "Ellerman bomb 10",
                "Ellerman bomb 1 (profile)",
                "Ellerman bomb 2",
                "Ellerman bomb 1",
        ]

        expect:
        titles.toSorted(new TitleOrder()) == [
                "Ellerman bomb 1",
                "Ellerman bomb 1 (profile)",
                "Ellerman bomb 2",
                "Ellerman bomb 2 (profile)",
                "Ellerman bomb 10",
                "Ellerman bomb 10 (profile)",
                "Flare 1",
        ]
    }

    def "compares numbers by value"() {
        expect:
        new TitleOrder().compare(first, second) < 0

        where:
        first      | second
        "Image 2"  | "Image 10"
        "Image 02" | "Image 10"
        "Image 9"  | "Image 9b"
        "Image"    | "Image 1"
    }

    def "orders accented titles alphabetically"() {
        expect:
        ["Éclipse virtuelle", "Couronne", "Doppler"].toSorted(new TitleOrder()) == ["Couronne", "Doppler", "Éclipse virtuelle"]
    }
}
