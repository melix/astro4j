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
import spock.lang.Unroll

class ProgressPathFormatterTest extends Specification {

    @Unroll
    def "fits #segments in #maxWidth characters"() {
        expect:
        ProgressPathFormatter.fit(segments, maxWidth, { (double) it.length() }) == expected

        where:
        segments                                  | maxWidth | expected
        []                                        | 10       | ""
        ["Batch"]                                 | 2        | "Batch"
        ["Batch", "file.ser"]                     | 5        | "Batch / file.ser"
        ["Batch", "file.ser", "Scripts", "Eval"]  | 100      | "Batch / file.ser / Scripts / Eval"
        ["Batch", "file.ser", "Scripts", "Eval"]  | 26       | "Batch / … / Scripts / Eval"
        ["Batch", "file.ser", "Scripts", "Eval"]  | 20       | "Batch / … / Eval"
        ["Batch", "file.ser", "Scripts", "Eval"]  | 5        | "Batch / … / Eval"
    }

    def "does not split a task name which contains the separator"() {
        expect:
        ProgressPathFormatter.fit(["Batch", "file.ser", "Evaluating a / 2"], 30, { (double) it.length() }) == "Batch / … / Evaluating a / 2"
    }
}
