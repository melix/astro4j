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
package me.champeau.a4j.math.fft

import spock.lang.Specification
import spock.lang.Subject

class FastFourierTransform2DTest extends Specification {
    private static final double EPSILON = 0.000001
    private static final int WIDTH = 16
    private static final List<Double> DATA = [
            0d, 2d, 2d, 2d, 1d, 1.5d, 2d, 4d, 2d, 2d, 2d, 1d, 0d, 0d, 5d, 0d,
            0d, 1.5d, 2d, 4d, 2d, 2d, 2d, 1d, 2d, 2d, 2d, 1d, 0d, 0d, 5d, 0d,
            0d, 2d, 2d, 2d, 1d, 1.5d, 2d, 0d, 2d, 2d, 2d, 1d, 0d, 2d, 5d, 0d,
            1d, 1.5d, 2d, 0d, 2d, 0d, 2d, 4d, 2d, 2d, 2d, 1d, 0d, 0d, 5d, 0d
    ]
    private static final List<Double> ZERO = (0..<4*16).collect { 0d }

    private double[] real
    private double[] imaginary

    @Subject
    FastFourierTransform2D fft

    def "verifies row inversion"() {
        when:
        fft.transformRows()
        fft.inverseRows()

        then:
        assertEquals(real, DATA)
        assertEquals(imaginary, ZERO)
    }

    def "verifies column inversion"() {
        when:
        fft.transformColumns()
        fft.inverseColumns()

        then:
        assertEquals(real, DATA)
        assertEquals(imaginary, ZERO)
    }

    def "computes FFT"() {
        when:
        fft.transform()
        fft.inverseTransform()

        then:
        assertEquals(real, DATA)
        assertEquals(imaginary, ZERO)
    }

    private static void assertEquals(double[] array, List<Double> expected) {
        if (array.length != expected.size()) {
            throw new AssertionError("Expected array to have ${expected.size()} elements but got ${array.length}")
        }
        def elements = array as List<Double>
        elements.eachWithIndex { double entry, int i ->
            if (Math.abs(entry - expected[i])>EPSILON) {
                throw new AssertionError("At index $i expected ${expected[i]} but found $entry")
            }
        }
    }

    void setup() {
        int size = DATA.size()
        real = DATA.toArray(new double[size])
        imaginary = new double[real.length]
        fft = new FastFourierTransform2D(real, WIDTH, (int) (real.length/WIDTH))
    }
}
