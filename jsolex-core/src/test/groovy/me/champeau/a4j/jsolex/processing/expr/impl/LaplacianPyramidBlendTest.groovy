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
package me.champeau.a4j.jsolex.processing.expr.impl

import spock.lang.Specification

class LaplacianPyramidBlendTest extends Specification {

    private static float[][] constant(int w, int h, float v) {
        def d = new float[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = v
            }
        }
        d
    }

    private static float[][] rampX(int w, int h, float scale) {
        def d = new float[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = x * scale
            }
        }
        d
    }

    private static float[][] halfMask(int w, int h) {
        def d = new float[h][w]
        int half = w / 2
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = x < half ? 0f : 1f
            }
        }
        d
    }

    def "blending an image with itself reproduces it"() {
        given:
        def a = rampX(64, 64, 2f)
        def mask = halfMask(64, 64)

        when:
        def out = LaplacianPyramidBlend.blend(a, a, mask, 5)

        then:
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - a[y][x]) < 1e-3f
            }
        }
    }

    def "all-zero mask returns image a"() {
        given:
        def a = rampX(64, 64, 1f)
        def b = constant(64, 64, 500f)
        def mask = constant(64, 64, 0f)

        when:
        def out = LaplacianPyramidBlend.blend(a, b, mask, 5)

        then:
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - a[y][x]) < 1e-3f
            }
        }
    }

    def "all-one mask returns image b"() {
        given:
        def a = constant(64, 64, 10f)
        def b = rampX(64, 64, 3f)
        def mask = constant(64, 64, 1f)

        when:
        def out = LaplacianPyramidBlend.blend(a, b, mask, 5)

        then:
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - b[y][x]) < 1e-3f
            }
        }
    }

    def "sharp intensity step is blended smoothly across the seam"() {
        given:
        def a = constant(128, 64, 0f)
        def b = constant(128, 64, 100f)
        def mask = halfMask(128, 64)

        when:
        def out = LaplacianPyramidBlend.blend(a, b, mask, 5)

        then: "no adjacent-pixel difference exceeds a fraction of the original step"
        float maxStep = 0f
        for (int y = 0; y < 64; y++) {
            for (int x = 1; x < 128; x++) {
                float d = Math.abs(out[y][x] - out[y][x - 1])
                if (d > maxStep) {
                    maxStep = d
                }
            }
        }
        maxStep < 40f
    }

    def "sharp step blend is monotonic along the seam row"() {
        given:
        def a = constant(128, 32, 0f)
        def b = constant(128, 32, 200f)
        def mask = halfMask(128, 32)

        when:
        def out = LaplacianPyramidBlend.blend(a, b, mask, 5)

        then: "values increase (or stay equal) left-to-right"
        for (int y = 0; y < 32; y++) {
            for (int x = 1; x < 128; x++) {
                assert out[y][x] >= out[y][x - 1] - 1e-3f
            }
        }
    }

    def "maxLevels saturates for small images and grows for large ones"() {
        expect:
        LaplacianPyramidBlend.maxLevels(4, 4) == 1
        LaplacianPyramidBlend.maxLevels(8, 8) == 2
        LaplacianPyramidBlend.maxLevels(256, 256) >= 5
    }

    private static float[][] stripCanvas(int w, int h, int y1, int y2, float value) {
        def d = new float[h][w]
        for (int y = y1; y <= y2; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = value
            }
        }
        d
    }

    def "joinAtMidline preserves upper content above midline when zeros below content"() {
        given: "upper has content only in rows [0, 35], lower has content only in rows [20, 63]"
        def upper = stripCanvas(64, 64, 0, 35, 2000f)
        def lower = stripCanvas(64, 64, 20, 63, 2000f)
        int midline = 28

        when:
        def out = LaplacianPyramidBlend.joinAtMidline(upper, lower, midline, 4)

        then: "in the middle of upper's strip, output matches upper"
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - 2000f) < 100f
            }
        }
    }

    def "joinAtMidline preserves lower content below midline when zeros above content"() {
        given:
        def upper = stripCanvas(64, 64, 0, 35, 2000f)
        def lower = stripCanvas(64, 64, 20, 63, 2000f)
        int midline = 28

        when:
        def out = LaplacianPyramidBlend.joinAtMidline(upper, lower, midline, 4)

        then: "deep in lower's exclusive region, output matches lower"
        for (int y = 55; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - 2000f) < 100f
            }
        }
    }

    def "joinAtMidline with identical images reproduces them"() {
        given:
        def img = rampX(64, 64, 1f)

        when:
        def out = LaplacianPyramidBlend.joinAtMidline(img, img, 32, 4)

        then:
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assert Math.abs(out[y][x] - img[y][x]) < 1e-2f
            }
        }
    }

    def "odd dimensions are handled without losing content"() {
        given:
        def a = rampX(65, 33, 1f)
        def b = rampX(65, 33, 1f)
        def mask = halfMask(65, 33)

        when:
        def out = LaplacianPyramidBlend.blend(a, b, mask, 4)

        then:
        for (int y = 0; y < 33; y++) {
            for (int x = 0; x < 65; x++) {
                assert Math.abs(out[y][x] - a[y][x]) < 1e-3f
            }
        }
    }
}
