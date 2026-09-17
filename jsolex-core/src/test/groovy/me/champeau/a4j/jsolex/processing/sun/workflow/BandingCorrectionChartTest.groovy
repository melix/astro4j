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
package me.champeau.a4j.jsolex.processing.sun.workflow

import me.champeau.a4j.jsolex.processing.params.BandingCorrectionMethod
import spock.lang.Specification

class BandingCorrectionChartTest extends Specification {

    def "emits a chart of the corrections of each pass"() {
        given:
        def captured = [:]
        def emitter = [newColorImage: { kind, category, title, name, description, w, h, metadata, supplier ->
            captured.kind = kind
            captured.name = name
            captured.rgb = supplier.get()
        }] as ImageEmitter
        def firstPass = new double[100]
        def secondPass = new double[100]
        for (int i = 0; i < 100; i++) {
            firstPass[i] = i < 10 ? Double.NaN : correction + Math.sin(i / 5d) * amplitude
            secondPass[i] = i < 10 ? Double.NaN : correction + Math.cos(i / 7d) * amplitude / 2
        }

        when:
        BandingCorrectionChart.emit(emitter, method, "Banding correction", [firstPass, secondPass])

        then:
        captured.kind == GeneratedImageKind.DEBUG
        captured.name == "transversallium-correction"
        captured.rgb.length == 3

        where:
        method                                     | correction | amplitude
        BandingCorrectionMethod.BANDING_CORRECTION | 1          | 0.02
        BandingCorrectionMethod.DESTRIPE           | 0          | 150
    }

    def "doesn't emit a chart when no pass was applied"() {
        given:
        def emitted = false
        def emitter = [newColorImage: { kind, category, title, name, description, w, h, metadata, supplier ->
            emitted = true
        }] as ImageEmitter

        when:
        BandingCorrectionChart.emit(emitter, BandingCorrectionMethod.DESTRIPE, "Destripe", [])

        then:
        !emitted
    }
}
