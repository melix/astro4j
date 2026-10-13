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

import spock.lang.Specification

import java.util.function.BiPredicate

class SpectralLineFitTest extends Specification {

    private static final int WIDTH = 120
    private static final int HEIGHT = 80
    private static final double[] POSITIONS = (-4..8).collect { it as double } as double[]
    private static final double LINE_POSITION = 2.7
    private static final double LINE_FWHM = 3.3
    private static final BiPredicate<Integer, Integer> ALL = { x, y -> true }

    def "a background carrying absorption lines gives no emission, whatever its level"() {
        given:
        var cube = synthesize({ x, y -> 500 + 40 * x }, { x, y -> 0 }, { x, y -> 0 })

        when:
        var amplitude = fit(cube, ALL, null, false)

        then:
        maxAbs(amplitude, 0, WIDTH) < 1e-2

        and: "a difference between a line sample and the continuum is fooled by the same background"
        Math.abs(classicDifference(cube, WIDTH - 1, 10)) > 10
    }

    def "measures the amplitude of the emission line"() {
        given:
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < WIDTH.intdiv(2) ? 0 : 50 }, { x, y -> 0 })

        when:
        var amplitude = fit(cube, { x, y -> x < WIDTH.intdiv(2) }, null, false)

        then:
        Math.abs(amplitude[40][10]) < 1e-2
        Math.abs(amplitude[40][100] - 50) < 1e-2
    }

    def "a shift of the background is mistaken for emission unless it is corrected"() {
        given: "a background shifted in opposite directions on the left and on the right"
        var cube = synthesize({ x, y -> 3000 }, { x, y -> 0 }, { x, y -> 0.15 * (x - WIDTH.intdiv(2)) / WIDTH.intdiv(2) })

        when:
        var uncorrected = fit(cube, ALL, null, false)
        var perPixel = fit(cube, ALL, null, true)

        then: "the uncorrected fit shows fake emission of opposite signs on both sides"
        uncorrected[40][5] * uncorrected[40][WIDTH - 5] < 0
        Math.abs(uncorrected[40][5]) > 10

        and: "the correction removes most of it"
        Math.abs(perPixel[40][5]) < 0.05 * Math.abs(uncorrected[40][5])
        Math.abs(perPixel[40][WIDTH - 5]) < 0.05 * Math.abs(uncorrected[40][WIDTH - 5])
    }

    def "the shift correction follows a background whose level varies quickly"() {
        given: "a background shifted on the left, where its level drops quickly, and measured on the right"
        var cube = synthesize({ x, y -> 500 + 20000 * Math.exp(-x / 4d) }, { x, y -> 0 }, { x, y -> x < WIDTH.intdiv(2) ? 0.1 : 0 })
        BiPredicate<Integer, Integer> template = { x, y -> x >= 80 }

        when:
        var uncorrected = fit(cube, template, null, false)
        var corrected = fit(cube, template, null, true)

        then:
        (0..50).every { x -> Math.abs(corrected[40][x]) < 0.05 * Math.abs(uncorrected[40][x]) }
    }

    def "the shift correction keeps the emission"() {
        given: "emission on the bottom half only, over a background shifted in opposite directions on the left and on the right"
        var cube = synthesize({ x, y -> 3000 }, { x, y -> y < HEIGHT.intdiv(2) ? 0 : 40 }, { x, y -> 0.15 * (x - WIDTH.intdiv(2)) / WIDTH.intdiv(2) })

        when:
        var amplitude = fit(cube, { x, y -> y < HEIGHT.intdiv(2) }, null, true)

        then: "the second order effect of the shift is not corrected, but it is the same on both sides"
        Math.abs(amplitude[60][5] - 40) < 4
        Math.abs(amplitude[60][WIDTH - 5] - 40) < 4
        Math.abs(amplitude[60][5] - amplitude[60][WIDTH - 5]) < 2
    }

    def "missing samples are left out of the fit"() {
        given: "the reddest sample is outside of the capture window on the top half"
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < WIDTH.intdiv(2) ? 0 : 50 }, { x, y -> 0 })
        for (int y = 0; y < HEIGHT.intdiv(2); y++) {
            Arrays.fill(cube[POSITIONS.length - 1][y], 0f)
        }
        and: "and interpolated with the missing values at the boundary"
        for (int x = 0; x < WIDTH; x++) {
            cube[POSITIONS.length - 1][HEIGHT.intdiv(2)][x] *= 0.5f
        }

        when:
        var amplitude = fit(cube, { x, y -> x < WIDTH.intdiv(2) }, null, false)

        then:
        Math.abs(amplitude[10][100] - 50) < 1e-2
        Math.abs(amplitude[HEIGHT.intdiv(2)][100] - 50) < 1e-2
        Math.abs(amplitude[70][100] - 50) < 1e-2
        Math.abs(amplitude[10][10]) < 1e-2
    }

    def "pixels with too few samples are set to zero"() {
        given:
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < WIDTH.intdiv(2) ? 0 : 50 }, { x, y -> 0 })
        for (int i = 0; i < 9; i++) {
            cube[i][5][5] = 0f
        }

        when:
        var amplitude = fit(cube, { x, y -> y > 20 && x < WIDTH.intdiv(2) }, null, false)

        then:
        amplitude[5][5] == 0f
        Math.abs(amplitude[60][60] - 50) < 1e-2
    }

    def "pixels outside of the region are set to zero"() {
        given:
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < WIDTH.intdiv(2) ? 0 : 50 }, { x, y -> 0 })

        when:
        var amplitude = fit(cube, { x, y -> x < WIDTH.intdiv(2) }, { x, y -> y < HEIGHT.intdiv(2) }, false)

        then:
        Math.abs(amplitude[10][100] - 50) < 1e-2
        amplitude[70][100] == 0f
    }

    def "the estimate is unbiased in presence of noise"() {
        given:
        var random = new Random(42)
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < WIDTH.intdiv(2) ? 0 : 20 }, { x, y -> 0 })
        cube.each { plane -> plane.each { row -> for (int x = 0; x < row.length; x++) { row[x] += (float) (10 * random.nextGaussian()) } } }

        when:
        var amplitude = fit(cube, { x, y -> x < WIDTH.intdiv(2) }, null, false)
        var mean = 0d
        var count = 0
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = WIDTH.intdiv(2); x < WIDTH; x++) {
                mean += amplitude[y][x]
                count++
            }
        }
        mean /= count

        then:
        Math.abs(mean - 20) < 1
    }

    def "emission in the template region is subtracted from every pixel unless only its faintest part is used"() {
        given: "a template region on the left, whose top half still holds some emission"
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < 20 ? (y < HEIGHT.intdiv(2) ? 30 : 0) : (x >= 60 ? 50 : 0) }, { x, y -> 0 })
        BiPredicate<Integer, Integer> template = { x, y -> x < 20 }

        when:
        var whole = fit(cube, template, null, false)
        var faintest = fit(cube, template, null, false, 0.25)

        then: "with the whole region, half of the emission of the template is subtracted everywhere"
        Math.abs(whole[40][40] + 15) < 0.5
        Math.abs(whole[40][100] - 35) < 0.5

        and: "the faintest part of the region holds no emission"
        Math.abs(faintest[40][40]) < 0.05
        Math.abs(faintest[40][100] - 50) < 0.05
    }

    def "tiles whose spectrum is an outlier are not used for the template"() {
        given: "four tiles in the template region: two with emission, one clean, and one with an aberrant spectrum"
        var cube = synthesize({ x, y -> 2000 }, { x, y -> x < 20 && y < 40 ? 30 : (x >= 60 ? 50 : 0) }, { x, y -> 0 })
        for (int i = 0; i < POSITIONS.length; i++) {
            for (int y = 60; y < HEIGHT; y++) {
                for (int x = 0; x < 20; x++) {
                    cube[i][y][x] -= (float) (600 * line(POSITIONS[i]))
                }
            }
        }

        when:
        var amplitude = fit(cube, { x, y -> x < 20 }, null, false, 0.25)

        then:
        Math.abs(amplitude[40][40]) < 0.05
        Math.abs(amplitude[40][100] - 50) < 0.05
    }

    def "measures the thickness of a region"() {
        given:
        var region = new boolean[50][50]
        for (int y = 0; y < 50; y++) {
            for (int x = 20; x < 30; x++) {
                region[y][x] = true
            }
        }

        expect:
        Math.abs(SpectralLineFit.regionThickness(50, 50, region) - 10) <= 1
    }

    def "the derivative of the template follows a narrow line"() {
        given:
        var positions = (-6..6).collect { it as double } as double[]
        var values = positions.collect { background(it) } as double[]

        when:
        var derivatives = SpectralLineFit.splineDerivatives(positions, values)

        then: "the error is far below the slope, inside the line and on its wings"
        (2..10).every { i -> Math.abs(derivatives[i] - backgroundDerivative(positions[i])) < 0.01 }
    }

    def "solves small linear systems"() {
        when:
        var solution = SpectralLineFit.solveLinearSystem([[2d, 1d], [1d, 3d]] as double[][], [3d, 5d] as double[])

        then:
        Math.abs(solution[0] - 0.8d) < 1e-12
        Math.abs(solution[1] - 1.4d) < 1e-12
        SpectralLineFit.solveLinearSystem([[1d, 2d], [2d, 4d]] as double[][], [1d, 2d] as double[]) == null
    }

    private static float[][] fit(float[][][] cube, BiPredicate<Integer, Integer> template, BiPredicate<Integer, Integer> region, boolean correctShift, double faintest = 1) {
        SpectralLineFit.fit(WIDTH, HEIGHT, cube, POSITIONS, LINE_POSITION, LINE_FWHM, template, region, correctShift, faintest)
    }

    /**
     * A photospheric-like spectrum: a deep line at position 0 and a weak one at position 6,
     * on a continuum of 1.
     */
    private static double background(double position) {
        1 - 0.6 * Math.exp(-0.5 * (position / 1.2)**2) - 0.1 * Math.exp(-0.5 * ((position - 6) / 1.0)**2)
    }

    private static double backgroundDerivative(double position) {
        0.6 * position / (1.2**2) * Math.exp(-0.5 * (position / 1.2)**2) + 0.1 * (position - 6) * Math.exp(-0.5 * ((position - 6) / 1.0)**2)
    }

    private static double line(double position) {
        var sigma = LINE_FWHM / (2 * Math.sqrt(2 * Math.log(2)))
        Math.exp(-0.5 * ((position - LINE_POSITION) / sigma)**2)
    }

    private static float[][][] synthesize(Closure<Number> level, Closure<Number> emission, Closure<Number> shift) {
        var cube = new float[POSITIONS.length][HEIGHT][WIDTH]
        for (int i = 0; i < POSITIONS.length; i++) {
            var p = POSITIONS[i]
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    var d = shift(x, y) as double
                    cube[i][y][x] = (float) ((level(x, y) as double) * background(p - d) + (emission(x, y) as double) * line(p))
                }
            }
        }
        cube
    }

    private static double classicDifference(float[][][] cube, int x, int y) {
        var lineSample = cube[POSITIONS.findIndexOf { it == 3d }][y][x]
        var continuum = (cube[POSITIONS.findIndexOf { it == -3d }][y][x] + cube[POSITIONS.findIndexOf { it == 8d }][y][x]) / 2
        lineSample - continuum
    }

    private static double maxAbs(float[][] image, int x0, int x1) {
        var max = 0d
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = x0; x < x1; x++) {
                max = Math.max(max, Math.abs(image[y][x]))
            }
        }
        max
    }
}
