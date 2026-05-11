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
package me.champeau.a4j.jsolex.processing.sun

import spock.lang.Specification

class ImageUtilsHSLTest extends Specification {

    // Pixel values are in [0, 65535] (MAX_VALUE used by the converter).
    private static float[][][] rgb(float[][] r, float[][] g, float[][] b) {
        return new float[][][] { r, g, b }
    }

    private static float[][] cell(float v) {
        return new float[][] { new float[] { v } }
    }

    def "fromRGBtoHSL produces expected H/S/L for primary and gray colors"() {
        given:
        // Pure red: R=65535, G=0, B=0 → HSL = (0°, 1, 0.5)
        // Pure green:                 → HSL = (120°, 1, 0.5)
        // Pure blue:                  → HSL = (240°, 1, 0.5)
        // Mid gray: R=G=B=32767       → HSL = (0°, 0, ~0.5)
        // Black:                      → HSL = (0°, 0, 0)
        // White: R=G=B=65535          → HSL = (0°, 0, 1.0)
        def reds   = [65535f, 0f,     0f,     32767f, 0f, 65535f]
        def greens = [0f,     65535f, 0f,     32767f, 0f, 65535f]
        def blues  = [0f,     0f,     65535f, 32767f, 0f, 65535f]
        def expectedH = [0f,   120f,  240f,   0f,     0f, 0f]
        def expectedS = [1f,   1f,    1f,     0f,     0f, 0f]
        def expectedL = [0.5f, 0.5f,  0.5f,   0.5f,   0f, 1f]

        when:
        def results = []
        for (int i = 0; i < reds.size(); i++) {
            def hsl = ImageUtils.fromRGBtoHSL(rgb(cell(reds[i]), cell(greens[i]), cell(blues[i])))
            results << [hsl[0][0][0], hsl[1][0][0], hsl[2][0][0]]
        }

        then:
        for (int i = 0; i < reds.size(); i++) {
            assert Math.abs(results[i][0] - expectedH[i]) < 0.01f
            assert Math.abs(results[i][1] - expectedS[i]) < 0.001f
            assert Math.abs(results[i][2] - expectedL[i]) < 0.001f
        }
    }

    def "RGB -> HSL -> RGB recovers original within tolerance for all hue segments"() {
        given:
        // Coverage across all six hue segments plus edge cases:
        //   segment 0 (R→Y, 0..60°), 1 (Y→G, 60..120°), 2 (G→C, 120..180°),
        //   segment 3 (C→B, 180..240°), 4 (B→M, 240..300°), 5 (M→R, 300..360°).
        def points = [
                [60000f, 30000f, 10000f],  // segment 0
                [30000f, 60000f, 10000f],  // segment 1
                [10000f, 60000f, 30000f],  // segment 2
                [10000f, 30000f, 60000f],  // segment 3
                [30000f, 10000f, 60000f],  // segment 4
                [50000f, 12000f, 40000f],  // segment 5
                [65535f, 0f, 0f],          // pure red
                [0f, 65535f, 0f],          // pure green
                [0f, 0f, 65535f],          // pure blue
                [65535f, 32767f, 100f],
                [0f, 0f, 0f],              // black
                [65535f, 65535f, 65535f]   // white
        ]

        when:
        def recovered = []
        for (def p : points) {
            def src = rgb(cell((float) p[0]), cell((float) p[1]), cell((float) p[2]))
            def hsl = ImageUtils.fromRGBtoHSL(src)
            def back = ImageUtils.fromHSLtoRGB(hsl)
            recovered << [back[0][0][0], back[1][0][0], back[2][0][0]]
        }

        then:
        for (int i = 0; i < points.size(); i++) {
            // 1.0 tolerance on the 65535-scale (≈ 0.0015 % FSR) covers float rounding.
            assert Math.abs(recovered[i][0] - points[i][0]) < 1.0f
            assert Math.abs(recovered[i][1] - points[i][1]) < 1.0f
            assert Math.abs(recovered[i][2] - points[i][2]) < 1.0f
        }
    }

    def "fromRGBtoHSL(rgb, output) writes into caller-provided buffer"() {
        given:
        def src = rgb(cell(65535f), cell(0f), cell(0f))
        def output = new float[3][][]
        for (int i = 0; i < 3; i++) {
            output[i] = new float[1][1]
        }

        when:
        def result = ImageUtils.fromRGBtoHSL(src, output)

        then:
        result.is(output)
        Math.abs(output[0][0][0] - 0f) < 0.01f      // H = 0
        Math.abs(output[1][0][0] - 1f) < 0.001f     // S = 1
        Math.abs(output[2][0][0] - 0.5f) < 0.001f   // L = 0.5
    }

    def "fromHSLtoRGB(hsl, output) writes into caller-provided buffer"() {
        given:
        // HSL (0°, 1, 0.5) should give pure red on the 65535-scale.
        def hsl = new float[3][][]
        hsl[0] = cell(0f)
        hsl[1] = cell(1f)
        hsl[2] = cell(0.5f)
        def output = new float[3][][]
        for (int i = 0; i < 3; i++) {
            output[i] = new float[1][1]
        }

        when:
        def result = ImageUtils.fromHSLtoRGB(hsl, output)

        then:
        result.is(output)
        Math.abs(output[0][0][0] - 65535f) < 1.0f
        Math.abs(output[1][0][0] - 0f) < 1.0f
        Math.abs(output[2][0][0] - 0f) < 1.0f
    }
}
