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
package me.champeau.a4j.math

import spock.lang.Specification

class MathUtilsTest extends Specification {
    def "median of null array returns NaN"() {
        when:
        def result = MathUtils.median(null)

        then:
        Double.isNaN(result)
    }

    def "median of empty array returns NaN"() {
        given:
        def values = [] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        Double.isNaN(result)
    }

    def "median of single element"() {
        given:
        def values = [5.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 5.0
    }

    def "median of odd number of elements"() {
        given:
        def values = [3.0, 1.0, 5.0, 2.0, 4.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 3.0
    }

    def "median of even number of elements returns middle-right value"() {
        given:
        def values = [3.0, 1.0, 5.0, 2.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 3.0
    }

    def "median of already sorted array"() {
        given:
        def values = [1.0, 2.0, 3.0, 4.0, 5.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 3.0
    }

    def "median of reverse sorted array"() {
        given:
        def values = [5.0, 4.0, 3.0, 2.0, 1.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 3.0
    }

    def "median with duplicate values"() {
        given:
        def values = [3.0, 3.0, 3.0, 1.0, 5.0] as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 3.0
    }

    def "median with all identical values"() {
        given:
        def values = [5.0] * 1000 as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 5.0
    }

    def "median with mostly identical values"() {
        given:
        def values = ([5.0] * 1000 + [1.0, 2.0, 3.0, 4.0]) as double[]

        when:
        def result = MathUtils.median(values)

        then:
        result == 5.0
    }

    def "median of random values matches traditional sort-based median"() {
        given:
        def random = new Random(seed)
        def values = new double[100]
        for (int i = 0; i < values.length; i++) {
            values[i] = (int) (random.nextDouble() * 100.0)
        }

        when:
        def quickSelectResult = MathUtils.median(values)
        def sortedValues = values.clone()
        Arrays.sort(sortedValues)
        def medianIndex = (int) (sortedValues.length / 2)
        def traditionalMedian = sortedValues[medianIndex]

        then:
        quickSelectResult == traditionalMedian

        where:
        seed << [42, 7, 123, 999, 2024, 84469]
    }
}
