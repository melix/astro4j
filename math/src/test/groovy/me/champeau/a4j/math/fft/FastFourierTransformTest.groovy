/*
 * Copyright 2003-2021 the original author or authors.
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
    private static final float EPSILON = 0.001f
    private static final List<Float> DATA = (0..4096).collect {
        (float) (1 + it % 4)
    }
    private static final List<Float> ZERO = (0..4096).collect { 0f }

    private float[] real
    private float[] imaginary

    @Subject
    FastFourierTransform fft

    def "computes FFT using #type"() {
        prepare(type)

        when:
        fft.transform()
        fft.inverseTransform()

        then:
        assertEquals(real, DATA)
        assertEquals(imaginary, ZERO)

        where:
        type << [FallbackFloatFastFourierTransform, VectorizedFloatFastFourierTransform]
    }

    private static void assertEquals(float[] array, List<Float> expected) {
        if (array.length != expected.size()) {
            throw new AssertionError("Expected array to have ${expected.size()} elements but got ${array.length}")
        }
        def elements = array as List<Float>
        double avgError = 0
        elements.eachWithIndex { float entry, int i ->
            def error = Math.abs(entry - expected[i])
            avgError = avgError + (error - avgError) / (i + 1)
        }
        if (avgError > EPSILON) {
            throw new AssertionError("Average is greater than epsilon: error = $avgError (${avgError / EPSILON} times epsilon)")
        }
    }

    void prepare(Class<FastFourierTransform> clazz) {
        real = DATA.toArray(new float[DATA.size()])
        imaginary = new float[real.length]
        fft = clazz.newInstance(real, imaginary)
    }
}
