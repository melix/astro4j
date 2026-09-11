/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class TelluricTransmissionTest extends Specification {
    def "knows the water lines around H-alpha"() {
        expect: "the two water lines on the wings of H-alpha, which the solar atlas does not have"
        TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(6564.18d)) < 0.85d
        TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(6552.61d)) < 0.75d

        and: "clear sky between them"
        TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(6560.0d)) > 0.97d
    }

    def "is transparent where the atlas has nothing to say"() {
        expect:
        TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(4000.0d)) == 1.0d
        TelluricTransmission.transmissionAt(Wavelen.ofAngstroms(8000.0d)) == 1.0d
    }

    def "the reference of the live identification carries the atmosphere, the one of the free search does not"() {
        when:
        def sky = DeepLineIdentifier.atlasLines(true, 2, 6563.0d, 6566.0d)
        def sun = DeepLineIdentifier.atlasLines(false, 2, 6563.0d, 6566.0d)

        then:
        sky.any { Math.abs(it - 6564.18d) < 0.1d }
        !sun.any { Math.abs(it - 6564.18d) < 0.1d }
    }
}
