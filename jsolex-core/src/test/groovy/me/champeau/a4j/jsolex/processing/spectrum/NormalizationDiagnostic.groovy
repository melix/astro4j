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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Evaluation only: shows what the moving continuum normalization does to a broad
 * line such as H-alpha, whose width exceeds the normalization window.
 * Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class NormalizationDiagnostic extends Specification {

    def "shows the effect of the moving window on a broad line"() {
        given:
        def fixture = load('/average/Ha/12_39_10.ser-average.fits')
        def analysis = new SpectrumFrameAnalyzer(fixture.width, fixture.height, false, null).analyze(fixture.data)
        def probe = new SpectrumAnalyzer.QueryDetails(SpectralRay.H_ALPHA, 2.4d, 1, SpectroHeliograph.SOLEX)
        def points = SpectrumAnalyzer.computeDataPoints(probe, analysis.distortionPolynomial().get(),
                analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(fixture.width),
                fixture.width, fixture.height, fixture.data)
        double dispersion = SpectrumAnalyzer.computeSpectralDispersion(SpectroHeliograph.SOLEX,
                SpectralRay.H_ALPHA.wavelength(), 2.4d).angstromsPerPixel()
        double[] raw = points.collect { it.intensity() } as double[]
        int window = Math.max(9, Math.min((int) Math.round(2 / dispersion), Math.max(9, (int) (raw.length / 2)))) | 1

        when:
        def moving = movingNormalize(raw, window)
        def envelope = envelopeNormalize(raw)

        then:
        println String.format(Locale.US,
                '\n=== %d points, %.4f A/px, window %d px (%.2f A) ===', raw.length, dispersion, window, window * dispersion)
        println 'shift   raw     moving-window   polynomial-envelope'
        def maxRaw = raw.max()
        for (int i = 0; i < raw.length; i += 2) {
            println String.format(Locale.US, '%+6.1f %6.3f %6.3f %-26s %6.3f %s',
                    points[i].pixelShift(), raw[i] / maxRaw,
                    moving[i], bar(moving[i]),
                    envelope[i], bar(envelope[i]))
        }
        println String.format(Locale.US,
                '\ncontrast retained: moving window %.3f, polynomial envelope %.3f (1 - min)',
                1 - moving.min(), 1 - envelope.min())
        true
    }

    private static String bar(double value) {
        var n = (int) Math.max(0, Math.round((value - 0.5d) * 40))
        return '#' * Math.min(24, n)
    }

    /** What the identifier does today. */
    private static double[] movingNormalize(double[] values, int window) {
        def result = new double[values.length]
        def half = window / 2 as int
        for (int i = 0; i < values.length; i++) {
            def slice = Arrays.copyOfRange(values, Math.max(0, i - half), Math.min(values.length, i + half + 1))
            Arrays.sort(slice)
            def continuum = slice[(int) (slice.length * 0.75d)]
            result[i] = continuum > 1e-9 ? values[i] / continuum : 1d
        }
        return result
    }

    /**
     * Alternative: a low order polynomial fitted to the upper envelope of the whole
     * profile. It removes the blaze and the illumination gradient, which are smooth,
     * without following a line however wide it is.
     */
    private static double[] envelopeNormalize(double[] values) {
        int n = values.length
        def xs = (0..<n).collect { (2d * it / (n - 1)) - 1d } as double[]
        def weights = new double[n]
        Arrays.fill(weights, 1d)
        double[] coefficients = null
        for (int iteration = 0; iteration < 6; iteration++) {
            coefficients = fitPolynomial(xs, values, weights, 3)
            for (int i = 0; i < n; i++) {
                var residual = values[i] - evaluate(coefficients, xs[i])
                // only points at or above the fit describe the continuum
                weights[i] = residual >= 0 ? 1d : 0.02d
            }
        }
        def result = new double[n]
        for (int i = 0; i < n; i++) {
            var continuum = evaluate(coefficients, xs[i])
            result[i] = continuum > 1e-9 ? values[i] / continuum : 1d
        }
        return result
    }

    private static double[] fitPolynomial(double[] xs, double[] ys, double[] weights, int degree) {
        int terms = degree + 1
        def normal = new double[terms][terms + 1]
        for (int i = 0; i < xs.length; i++) {
            def powers = new double[terms]
            powers[0] = 1
            for (int p = 1; p < terms; p++) {
                powers[p] = powers[p - 1] * xs[i]
            }
            for (int r = 0; r < terms; r++) {
                for (int c = 0; c < terms; c++) {
                    normal[r][c] += weights[i] * powers[r] * powers[c]
                }
                normal[r][terms] += weights[i] * powers[r] * ys[i]
            }
        }
        for (int col = 0; col < terms; col++) {
            int pivot = col
            for (int r = col + 1; r < terms; r++) {
                if (Math.abs(normal[r][col]) > Math.abs(normal[pivot][col])) {
                    pivot = r
                }
            }
            def tmp = normal[col]; normal[col] = normal[pivot]; normal[pivot] = tmp
            if (Math.abs(normal[col][col]) < 1e-12) {
                continue
            }
            for (int r = 0; r < terms; r++) {
                if (r == col) {
                    continue
                }
                def factor = normal[r][col] / normal[col][col]
                for (int c = col; c <= terms; c++) {
                    normal[r][c] -= factor * normal[col][c]
                }
            }
        }
        def result = new double[terms]
        for (int i = 0; i < terms; i++) {
            result[i] = Math.abs(normal[i][i]) < 1e-12 ? 0 : normal[i][terms] / normal[i][i]
        }
        return result
    }

    private static double evaluate(double[] coefficients, double x) {
        double acc = 0
        double power = 1
        for (double c : coefficients) {
            acc += c * power
            power *= x
        }
        return acc
    }

    private static Fixture load(String resource) {
        def file = new File(NormalizationDiagnostic.getResource(resource).toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        return new Fixture(image.width(), image.height(), image.data())
    }

    private record Fixture(int width, int height, float[][] data) {
    }
}
