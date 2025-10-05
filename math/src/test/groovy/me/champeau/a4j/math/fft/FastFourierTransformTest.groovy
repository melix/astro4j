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

class FastFourierTransformTest extends Specification {
    private static final double EPSILON = 0.00000001
    private static final List<Double> DATA = (0..<4096).collect {
        (double) (1 + it % 4)
    }
    private static final List<Double> ZERO = (0..<4096).collect { 0d }

    private double[] real
    private double[] imaginary

    @Subject
    FastFourierTransform fft

    def "computes FFT"() {
        given:
        real = DATA.toArray(new double[DATA.size()])
        imaginary = new double[real.length]
        fft = FastFourierTransform.ofComplex(real, imaginary)

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
        double avgError = 0
        elements.eachWithIndex { double entry, int i ->
            def error = Math.abs(entry - expected[i])
            avgError = avgError + (error - avgError) / (i + 1)
        }
        if (avgError > EPSILON) {
            throw new AssertionError("Average is greater than epsilon: error = $avgError (${avgError / EPSILON} times epsilon)")
        }
    }
}
