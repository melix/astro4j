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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Shared
import spock.lang.Specification

class StackedMetadataTest extends Specification {

    @Shared
    Utilities utilities = new Utilities([:], Broadcaster.NO_OP)

    @Shared
    SimpleFunctionCall simple = new SimpleFunctionCall([:], Broadcaster.NO_OP)

    private static Ellipse circle(double cx, double cy, double radius) {
        Ellipse.ofCartesian(new DoubleSextuplet(
                1d, 0d, 1d, -2 * cx, -2 * cy, cx * cx + cy * cy - radius * radius))
    }

    private static ImageWrapper32 frame(Random random, double cx, double cy, double radius) {
        float[][] data = new float[64][64]
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                data[y][x] = (float) (20000 + random.nextGaussian() * 400)
            }
        }
        new ImageWrapper32(64, 64, data, [(Ellipse): circle(cx, cy, radius)])
    }

    def "#name does not inherit the ellipse of a single frame"() {
        given: "frames whose disks agree, except the last one which is fitted well off"
        def random = new Random(4)
        def images = (0..<8).collect { frame(random, 32d, 32d, 20d) }
        images[7] = frame(random, 34.5d, 31d, 23d)

        when:
        def stacked = stack.call(images)
        def ellipse = stacked.findMetadata(Ellipse).get()

        then: "the outlier is diluted rather than copied verbatim"
        ellipse.center().a() != 34.5d
        Math.abs(ellipse.center().a() - 32d) < 1
        Math.abs(ellipse.center().b() - 32d) < 1
        Math.abs(ellipse.semiAxis().a() - 20d) < 1

        where:
        name            | stack
        'weighted_avg2' | { imgs -> utilities.weightedAverage2([images: imgs, weights: imgs.collect { 1d }, sigma: 2.5d]) }
        'weighted_avg'  | { imgs -> utilities.weightedAverage([images: imgs, weights: imgs.collect { 1d }]) }
        'avg2'          | { imgs -> simple.applyFunction('avg2', [list: imgs], { s -> AbstractImageExpressionEvaluator.applySigmaClippedAverage(s, 2.5d) }) }
    }
}
