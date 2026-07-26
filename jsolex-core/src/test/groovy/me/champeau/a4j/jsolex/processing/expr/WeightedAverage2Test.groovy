/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.expr

import me.champeau.a4j.jsolex.processing.expr.impl.Utilities
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

class WeightedAverage2Test extends Specification {

    @Subject
    Utilities utilities = new Utilities([:], Broadcaster.NO_OP)

    def "computes a weighted average"() {
        expect:
        pixel(combine([100f, 200f], [1d, 3d])) == 175f
    }

    def "equal weights reproduce a plain average"() {
        expect:
        pixel(combine([100f, 200f, 300f], [1d, 1d, 1d])) == 200f
    }

    def "rejects outliers beyond the sigma threshold"() {
        given: "one frame far away from the others"
        var values = [1000f, 1010f, 990f, 1005f, 60000f]

        when:
        var withRejection = pixel(combine(values, [1d] * 5, 1.5d))
        var withoutRejection = pixel(combine(values, [1d] * 5, 1000d))

        then: "the deviant frame is excluded, the mean falls back to the other four"
        withRejection > 990 && withRejection < 1010
        withoutRejection > 12000
    }

    def "a zero weight excludes an image entirely"() {
        expect:
        pixel(combine([100f, 60000f], [1d, 0d])) == 100f
    }

    def "preserves values outside the displayable range"() {
        expect:
        pixel(combine([100000f, 100000f], [1d, 1d])) == 100000f
        pixel(combine([-5000f, -5000f], [1d, 1d])) == -5000f
    }

    def "weighted_avg also preserves the range"() {
        expect:
        pixel(utilities.weightedAverage(["images": [image(100000f), image(100000f)], "weights": [1d, 1d]])) == 100000f
    }

    def "inverse variance weighting favours the least noisy frames"() {
        given: "three measurements of the same true value 1000, with different noise"
        var values = [1300f, 980f, 1010f]
        var sigmas = [300d, 20d, 10d]
        var weights = sigmas.collect { 1d / (it * it) }

        when:
        var weighted = pixel(combine(values, weights, 1000d))
        var unweighted = pixel(combine(values, [1d, 1d, 1d], 1000d))

        then: "the weighted estimate is closer to the truth than the plain average"
        Math.abs(weighted - 1000) < Math.abs(unweighted - 1000)
    }

    def "rejects mismatched inputs"() {
        when:
        combine([100f, 200f], [1d])

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects negative weights"() {
        when:
        combine([100f, 200f], [1d, -1d])

        then:
        thrown(IllegalArgumentException)
    }

    private Object combine(List<Float> values, List<Double> weights, double sigma = 2.5d) {
        utilities.weightedAverage2([
                "images" : values.collect { image(it) },
                "weights": weights,
                "sigma"  : sigma
        ])
    }

    private static ImageWrapper32 image(float value) {
        float[][] data = new float[1][1]
        data[0][0] = value
        new ImageWrapper32(1, 1, data, [:])
    }

    private static float pixel(Object result) {
        ((ImageWrapper32) result).data()[0][0]
    }
}
