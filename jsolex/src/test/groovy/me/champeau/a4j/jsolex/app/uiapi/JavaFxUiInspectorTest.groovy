/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.uiapi

import me.champeau.a4j.jsolex.server.ui.UiApiException
import me.champeau.a4j.jsolex.server.ui.UiQuery
import spock.lang.Specification

class JavaFxUiInspectorTest extends Specification {

    def "parses node reference #ref"() {
        expect:
        JavaFxUiInspector.parseRef(ref) == expected

        where:
        ref       | expected
        null      | []
        ""        | []
        "  "      | []
        "0"       | [0]
        "1/0/12"  | [1, 0, 12]
    }

    def "rejects malformed node reference #ref"() {
        when:
        JavaFxUiInspector.parseRef(ref)

        then:
        def e = thrown(UiApiException)
        e.status() == 400

        where:
        ref << ["a", "1/", "/1", "1//2", "-1", "1/x"]
    }

    def "builds child references"() {
        expect:
        JavaFxUiInspector.childRef("", 3) == "3"
        JavaFxUiInspector.childRef("3", 0) == "3/0"
        JavaFxUiInspector.childRef("3/0", 7) == "3/0/7"
    }

    def "child references round trip"() {
        expect:
        JavaFxUiInspector.parseRef(JavaFxUiInspector.childRef(JavaFxUiInspector.childRef("", 2), 5)) == [2, 5]
    }

    def "matches queries"() {
        expect:
        JavaFxUiInspector.matches(new UiQuery(null, id, text, type), "processButton", "Button", "Process file") == expected

        where:
        id              | text       | type     | expected
        null            | null       | null     | true
        "processButton" | null       | null     | true
        "process"       | null       | null     | false
        null            | "PROCESS"  | null     | true
        null            | "file"     | null     | true
        null            | "open"     | null     | false
        null            | null       | "Button" | true
        null            | null       | "button" | false
        "processButton" | "process"  | "Button" | true
        "processButton" | "process"  | "Label"  | false
    }

    def "text criterion never matches nodes without text"() {
        expect:
        !JavaFxUiInspector.matches(new UiQuery(null, null, "", null), null, "Pane", null)
        JavaFxUiInspector.matches(new UiQuery(null, null, "", null), null, "Label", "x")
    }

    def "normalizes text"() {
        expect:
        JavaFxUiInspector.normalizeText(null) == null
        JavaFxUiInspector.normalizeText("") == null
        JavaFxUiInspector.normalizeText("  ") == null
        JavaFxUiInspector.normalizeText(" a ") == " a "
    }
}
