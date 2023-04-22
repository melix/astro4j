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
    private static final float EPSILON = 0.000001f
    private static final int WIDTH = 16
    private static final List<Float> DATA = [
            0f, 2f, 2f, 2f, 1f, 1.5f, 2f, 4f, 2f, 2f, 2f, 1f, 0f, 0f, 5f, 0f,
            0f, 1.5f, 2f, 4f, 2f, 2f, 2f, 1f, 2f, 2f, 2f, 1f, 0f, 0f, 5f, 0f,
            0f, 2f, 2f, 2f, 1f, 1.5f, 2f, 0f, 2f, 2f, 2f, 1f, 0f, 2f, 5f, 0f,
            1f, 1.5f, 2f, 0f, 2f, 0f, 2f, 4f, 2f, 2f, 2f, 1f, 0f, 0f, 5f, 0f
    ]
    private static final List<Float> ZERO = (0..<4*16).collect { 0f }

    private float[] real
    private float[] imaginary

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

    private static void assertEquals(float[] array, List<Float> expected) {
        if (array.length != expected.size()) {
            throw new AssertionError("Expected array to have ${expected.size()} elements but got ${array.length}")
        }
        def elements = array as List<Float>
        elements.eachWithIndex { float entry, int i ->
            if (Math.abs(entry - expected[i])>EPSILON) {
                throw new AssertionError("At index $i expected ${expected[i]} but found $entry")
            }
        }
    }

    void setup() {
        int size = DATA.size()
        real = DATA.toArray(new float[size])
        imaginary = new float[real.length]
        fft = new FastFourierTransform2D(real, WIDTH, (int) (real.length/WIDTH))
    }
}
