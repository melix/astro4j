/*
 * Copyright 2023-2025 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class SpectralLineCatalogTest extends Specification {

    def "exposes the catalog with concise marker names"() {
        expect:
        !SpectralLineCatalog.defaults().isEmpty()
        // named lines drop the redundant parenthetical
        SpectralLineCatalog.defaults().find { it.wavelength().angstroms() == 3970.075d }.shortName() == 'H-epsilon'
        // bare element symbols keep the identifier to stay distinct
        SpectralLineCatalog.defaults().find { it.element() == 'Na' && it.identifier() == 'D2' }.shortName() == 'Na (D2)'
    }

    def "finds all catalog lines in the window, excluding only the studied line"() {
        given: "the studied line is the sodium D2 line"
        def lambda0 = Wavelen.ofAngstroms(5889.95d)
        // a very wide range so the test does not depend on the exact dispersion
        def range = new PixelShiftRange(-100000d, 100000d, 1d)

        when:
        def lines = SpectralLineCatalog.findLinesInWindow(lambda0, 2.4d, 1, SpectroHeliograph.SOLEX, range)
        def wavelengths = lines.collect { it.wavelength().angstroms() }

        then: "a neighbouring line is detected with its concise name"
        lines.find { it.name() == 'Na (D1)' } != null

        and: "the studied line itself is excluded (pixel shift 0)"
        wavelengths.every { Math.abs(it - 5889.95d) > 0.01d }

        and: "the catalog is pure: Helium D3 is returned (it is the consumer's job to filter it out)"
        wavelengths.find { Math.abs(it - 5875.618d) < 0.01d } != null
    }

    def "returns no line when the window is too narrow to contain another line"() {
        given: "H-alpha with a sub-pixel window"
        def lambda0 = Wavelen.ofAngstroms(6562.81d)
        def range = new PixelShiftRange(-0.1d, 0.1d, 0.01d)

        expect:
        SpectralLineCatalog.findLinesInWindow(lambda0, 2.4d, 1, SpectroHeliograph.SOLEX, range).isEmpty()
    }
}
