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
package me.champeau.a4j.jsolex.processing.sun.workflow

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class AnalysisUtilsTest extends Specification {

    private static final int SIZE = 64

    private static Ellipse centeredDisk() {
        double c = SIZE / 2.0d
        double r = SIZE * 0.3d
        double a = 1.0d / (r * r)
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, a, -2.0d * c * a, -2.0d * c * a, 2.0d * c * c * a - 1.0d))
    }

    private static ImageWrapper32 image(Float poison) {
        var data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = 500f
            }
        }
        if (poison != null) {
            data[1][1] = poison
        }
        return new ImageWrapper32(SIZE, SIZE, data, [:])
    }

    def "black point estimate stays usable when the image contains #label"() {
        when:
        var estimate = AnalysisUtils.estimateBlackPoint(image(poison), centeredDisk())

        then: "the estimate can be used as a fill value without poisoning the image"
        Double.isFinite(estimate)
        Float.isFinite((float) (estimate * 1.2d))

        where:
        label            | poison
        "no invalid data" | null
        "an infinity"     | Float.POSITIVE_INFINITY
        "a NaN"           | Float.NaN
    }

    def "background estimate stays usable when the image contains an infinity"() {
        when:
        var estimate = AnalysisUtils.estimateBackground(image(Float.POSITIVE_INFINITY), centeredDisk())

        then:
        Double.isFinite(estimate)
    }

    def "estimates fall back to zero when there is no signal outside the disk"() {
        given:
        var empty = new ImageWrapper32(SIZE, SIZE, new float[SIZE][SIZE], [:])

        expect: "not Double.MAX_VALUE, which becomes infinity once cast to a float"
        AnalysisUtils.estimateBlackPoint(empty, centeredDisk()) == 0
        AnalysisUtils.estimateBackground(empty, centeredDisk()) == 0
    }

    def "an infinity is ignored rather than propagated"() {
        given:
        var clean = AnalysisUtils.estimateBlackPoint(image(null), centeredDisk())

        expect: "the invalid pixel is simply left out of the average"
        Math.abs(AnalysisUtils.estimateBlackPoint(image(Float.POSITIVE_INFINITY), centeredDisk()) - clean) < 1
    }
}
