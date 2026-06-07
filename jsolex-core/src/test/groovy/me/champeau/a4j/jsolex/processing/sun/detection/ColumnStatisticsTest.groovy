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
package me.champeau.a4j.jsolex.processing.sun.detection

import spock.lang.Specification

class ColumnStatisticsTest extends Specification {

    // Two-row columns are used so the standard deviation is exactly |v0 - v1| / 2,
    // which is representable as a double and can be asserted with strict equality.
    private static final float[][] DATA = [
            [10f, 100f, 0f] as float[],
            [30f, 100f, 8f] as float[]
    ] as float[][]

    def "computes per-column averages over the requested range"() {
        given:
        double[] averages = new double[3]

        when:
        PhenomenaDetector.computeColumnAverages(DATA, 0, 3, 2, averages)

        then:
        averages[0] == 20.0d
        averages[1] == 100.0d
        averages[2] == 4.0d
    }

    def "computes per-column standard deviations over the requested range"() {
        given:
        double[] averages = new double[3]
        PhenomenaDetector.computeColumnAverages(DATA, 0, 3, 2, averages)
        double[] stddevs = new double[3]

        when:
        PhenomenaDetector.computeColumnStddevs(DATA, 0, 3, 2, averages, stddevs)

        then:
        stddevs[0] == 10.0d
        stddevs[1] == 0.0d
        stddevs[2] == 4.0d
    }

    def "leaves columns outside the [left, right) range untouched"() {
        given:
        double[] averages = new double[3]
        double[] stddevs = new double[3]

        when:
        PhenomenaDetector.computeColumnAverages(DATA, 1, 2, 2, averages)
        PhenomenaDetector.computeColumnStddevs(DATA, 1, 2, 2, averages, stddevs)

        then:
        averages[0] == 0.0d
        averages[1] == 100.0d
        averages[2] == 0.0d
        stddevs[0] == 0.0d
        stddevs[1] == 0.0d
        stddevs[2] == 0.0d
    }
}
