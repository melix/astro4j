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
package me.champeau.a4j.jsolex.processing.spectrum.cloud

import spock.lang.Specification

class CloudMapRendererTest extends Specification {

    def "the velocity scale is centered on zero and covers fast flows"() {
        given:
        def scale = CloudMapRenderer.Scale.velocity()

        expect:
        Math.abs(scale.position(0) - 0.5) < 1e-9
        Math.abs(scale.position(-100)) < 1e-9
        Math.abs(scale.position(100) - 1) < 1e-9
        Math.abs(scale.position(-30) + scale.position(30) - 1) < 1e-9
        (scale.position(10) - scale.position(0)) / 10 > 4 * (scale.position(100) - scale.position(30)) / 70
        scale.tickValues() as List == [-100d, -30d, -10d, 0d, 10d, 30d, 100d]
    }

    def "the position and the value of a scale are inverse of each other"() {
        expect:
        Math.abs(scale.valueAt(scale.position(value)) - value) < 1e-6

        where:
        scale                                     | value
        CloudMapRenderer.Scale.velocity()         | -57
        CloudMapRenderer.Scale.velocity()         | 3
        CloudMapRenderer.Scale.linear(4000, 15000) | 9000
        CloudMapRenderer.Scale.log(1e-5, 1e-3)    | 2.5e-4
    }
}
