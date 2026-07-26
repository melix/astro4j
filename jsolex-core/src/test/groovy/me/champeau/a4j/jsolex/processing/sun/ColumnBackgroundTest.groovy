/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import spock.lang.Specification

import java.util.function.BiPredicate

class ColumnBackgroundTest extends Specification {

    def "recovers a smooth dome hidden by noise and a masked disk"() {
        given: "an image whose background is a dome along x, with noise and a central disk excluded from the estimation"
        def width = 512
        def height = 512
        def data = new float[height][width]
        def random = new Random(42)
        def truth = new double[width]
        for (int x = 0; x < width; x++) {
            def u = 2.0 * x / (width - 1) - 1.0
            truth[x] = 1000 + 800 * (1 - u * u)
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (truth[x] + 50 * random.nextGaussian())
            }
        }
        def cx = width / 2
        def cy = height / 2
        def radius = 150
        def usable = { Integer x, Integer y ->
            def dx = x - cx
            def dy = y - cy
            dx * dx + dy * dy > radius * radius
        } as BiPredicate

        when:
        def levels = ColumnBackground.estimate(width, height, data, usable, 8d, 2.5d)

        then: "the model follows the dome everywhere, including under the disk"
        for (int x = 0; x < width; x += 16) {
            assert Math.abs(levels[x] - truth[x]) < 25
        }
    }

    def "ignores the uniform padding rows added by cropping"() {
        given: "the same dome, with constant padding rows at the top and bottom"
        def width = 256
        def height = 256
        def padding = 40
        def data = new float[height][width]
        def random = new Random(42)
        def truth = new double[width]
        for (int x = 0; x < width; x++) {
            def u = 2.0 * x / (width - 1) - 1.0
            truth[x] = 500 + 300 * (1 - u * u)
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y < padding || y >= height - padding) {
                    data[y][x] = 123f
                } else {
                    data[y][x] = (float) (truth[x] + 20 * random.nextGaussian())
                }
            }
        }

        when:
        def levels = ColumnBackground.estimate(width, height, data, null, 8d, 2.5d)

        then: "the padding value does not drag the model down"
        for (int x = 0; x < width; x += 16) {
            assert Math.abs(levels[x] - truth[x]) < 15
        }
    }

    def "a bright localized feature does not bend the model"() {
        given: "a flat background with a bright blob covering a few columns"
        def width = 256
        def height = 256
        def data = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (800 + 20 * random.nextGaussian())
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 100; x < 120; x++) {
                data[y][x] += 5000f
            }
        }

        when:
        def levels = ColumnBackground.estimate(width, height, data, null, 8d, 2.5d)

        then: "the blob columns are rejected instead of bending the model"
        for (int x = 0; x < width; x += 8) {
            if (x < 90 || x > 130) {
                assert Math.abs(levels[x] - 800) < 30
            }
        }
    }

    def "models the illumination dome of a real spectroheliograph image"() {
        given: "a single continuum image whose columns crossing the disk sit above the ones beside it"
        def image = loadFits('unsat-single.fits')
        def ellipse = image.findMetadata(Ellipse).orElseThrow()
        def width = image.width()
        def height = image.height()
        def data = image.data()
        def margin = ellipse.rescale(1.05d, 1.05d)
        def usable = { Integer x, Integer y -> !margin.isWithin(x, y) } as BiPredicate

        when:
        def levels = ColumnBackground.estimate(width, height, data, usable, 8d, 2.5d)

        then: "the model reproduces the measured dome: high at the disk centre, low beside the limbs"
        def cx = (int) ellipse.center().a()
        def r = ellipse.semiAxis().a()
        levels[cx] > 1500
        levels[(int) (cx - 1.2 * r)] < 350
        levels[(int) (cx + 1.2 * r)] < 350

        and: "the fall right past the limb is followed instead of being rounded off"
        Math.abs(levels[(int) (cx + 1.04 * r)] - 246) < 30
        Math.abs(levels[(int) (cx - 1.02 * r)] - 260) < 40

        and: "it stays close to the medians actually measured on the sky columns"
        def buffer = new double[height]
        double ss = 0
        int count = 0
        for (int x = 0; x < width; x += 8) {
            int n = 0
            for (int y = 0; y < height; y++) {
                if (!margin.isWithin(x, y) && data[y][x] != 0f) {
                    buffer[n++] = data[y][x]
                }
            }
            if (n > 100) {
                def copy = Arrays.copyOf(buffer, n)
                Arrays.sort(copy)
                def residual = copy[(int) (n / 2)] - levels[x]
                ss += residual * residual
                count++
            }
        }
        Math.sqrt(ss / count) < 10

        when: "the model is normalized"
        ColumnBackground.normalizeLevels(levels, ellipse)

        then: "the columns beside the disk sit at 1 and the dome rises well above them"
        Math.abs(levels[(int) (cx - 1.2 * r)] - 1) < 0.5
        Math.abs(levels[(int) (cx + 1.2 * r)] - 1) < 0.5
        levels[cx] > 5
    }

    private static ImageWrapper32 loadFits(String name) {
        def file = File.createTempFile('columnbg', '.fits')
        file.deleteOnExit()
        ColumnBackgroundTest.getResourceAsStream("/columnbg/$name").withCloseable { input ->
            file.withOutputStream { it << input }
        }
        FitsUtils.readFitsFile(file) as ImageWrapper32
    }
}
