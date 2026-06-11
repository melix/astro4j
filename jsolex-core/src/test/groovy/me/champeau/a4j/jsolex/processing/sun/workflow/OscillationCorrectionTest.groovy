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

import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import java.util.zip.GZIPInputStream

class OscillationCorrectionTest extends Specification {

    def "recovers a sinusoidal oscillation from a noisy signal with trend and gaps"() {
        given:
        def n = 1500
        def period = 87.0
        def amplitude = 2.3
        def phase = 0.7
        def random = new Random(42)
        def values = new double[n]
        def weights = new double[n]
        for (int y = 0; y < n; y++) {
            values[y] = amplitude * Math.sin(2 * Math.PI * y / period + phase) + 0.001 * y - 0.5 + 0.5 * random.nextGaussian()
            weights[y] = 1.0
        }
        for (int y = 600; y < 700; y++) {
            weights[y] = 0
        }

        when:
        def model = OscillationCorrection.findOscillation(values, weights)

        then:
        model.present
        def m = model.get()
        Math.abs(m.period() - period) / period < 0.01
        Math.abs(m.amplitude() - amplitude) < 0.25

        and: "modeled shifts match the generated oscillation"
        double sum = 0
        for (int y = 0; y < n; y++) {
            def expected = amplitude * Math.sin(2 * Math.PI * y / period + phase)
            def diff = m.shiftAt(y) - expected
            sum += diff * diff
        }
        Math.sqrt(sum / n) < 0.25
    }

    def "does not detect an oscillation in pure noise"() {
        given:
        def n = 1500
        def random = new Random(42)
        def values = new double[n]
        def weights = new double[n]
        for (int y = 0; y < n; y++) {
            values[y] = 0.8 * random.nextGaussian()
            weights[y] = 1.0
        }

        expect:
        OscillationCorrection.findOscillation(values, weights).empty
    }

    def "rejects oscillations below the minimum amplitude"() {
        given:
        def n = 1500
        def random = new Random(42)
        def values = new double[n]
        def weights = new double[n]
        for (int y = 0; y < n; y++) {
            values[y] = 0.1 * Math.sin(2 * Math.PI * y / 90) + 0.05 * random.nextGaussian()
            weights[y] = 1.0
        }

        expect:
        OscillationCorrection.findOscillation(values, weights).empty
    }

    def "requires a minimum number of samples"() {
        given:
        def n = 32
        def values = new double[n]
        def weights = new double[n]
        for (int y = 0; y < n; y++) {
            values[y] = Math.sin(2 * Math.PI * y / 10)
            weights[y] = 1.0
        }

        expect:
        OscillationCorrection.findOscillation(values, weights).empty
    }

    def "corrects a synthetic disk and emits a debug chart"() {
        given:
        def width = 800
        def height = 1200
        def ellipse = createTestEllipse(width, height)
        def period = 90.0
        def amplitude = 3.0
        def random = new Random(42)
        def left = new int[height]
        def right = new int[height]
        Arrays.fill(left, -1)
        Arrays.fill(right, -1)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            def prediction = ellipse.findX(y)
            if (prediction.present) {
                def pair = prediction.get()
                def x1 = Math.min(pair.a(), pair.b())
                def x2 = Math.max(pair.a(), pair.b())
                def shift = amplitude * Math.sin(2 * Math.PI * y / period)
                left[y] = (int) Math.round(x1 + shift + 0.5 * random.nextGaussian())
                right[y] = (int) Math.round(x2 + shift + 0.5 * random.nextGaussian())
                for (int x = Math.max(0, left[y]); x <= Math.min(width - 1, right[y]); x++) {
                    data[y][x] = 30000f
                }
            }
        }
        def image = new ImageWrapper32(width, height, data, new HashMap<Class<?>, Object>())
        def borders = new PhenomenaDetector.BorderDetection(left, right)
        def captured = [:]
        def emitter = [newColorImage: { kind, category, title, name, description, w, h, metadata, supplier ->
            captured.kind = kind
            captured.width = w
            captured.height = h
            captured.rgb = supplier.get()
        }] as ImageEmitter

        when:
        def detected = OscillationCorrection.detectOscillation(borders, ellipse, width, height, 30.0d, emitter)
        detected.ifPresent { OscillationCorrection.applyCorrection(image, it) }

        then: "the oscillation is detected"
        detected.present
        def model = detected.get()
        Math.abs(model.period() - period) / period < 0.02
        Math.abs(model.amplitude() - amplitude) < 0.3

        and: "the disk borders are realigned with the ellipse"
        def maxError = 0d
        for (int y = 400; y < 800; y++) {
            def pair = ellipse.findX(y).get()
            def x1 = Math.min(pair.a(), pair.b())
            int edge = -1
            for (int x = 0; x < width; x++) {
                if (data[y][x] > 15000f) {
                    edge = x
                    break
                }
            }
            maxError = Math.max(maxError, Math.abs(edge - x1))
        }
        maxError < 2.5

        and: "a debug chart was emitted"
        captured.kind == GeneratedImageKind.DEBUG
        captured.rgb != null
        captured.rgb.length == 3
    }

    def "tracks an oscillation with a drifting period"() {
        given:
        def n = 3000
        def random = new Random(42)
        def values = new double[n]
        def weights = new double[n]
        def phase = 0d
        for (int y = 0; y < n; y++) {
            def period = 28 + 8 * Math.sin(2 * Math.PI * y / 1500)
            phase += 2 * Math.PI / period
            values[y] = 2.0 * Math.sin(phase) + 0.4 * random.nextGaussian()
            weights[y] = 1.0
        }

        when:
        def model = OscillationCorrection.findOscillation(values, weights)

        then:
        model.present

        and: "the residual is much smaller than the original oscillation"
        double signal = 0
        double residual = 0
        for (int y = 0; y < n; y++) {
            signal += values[y] * values[y]
            def r = values[y] - model.get().shiftAt(y)
            residual += r * r
        }
        Math.sqrt(residual / n) < 0.5 * Math.sqrt(signal / n)
    }

    def "detects the oscillation of a real capture"() {
        given: "limb shift measurements from a real SER file with mount oscillations"
        def values = []
        def weights = []
        OscillationCorrectionTest.getResourceAsStream("/oscillation/limb-shift-real.csv.gz").withCloseable { stream ->
            new GZIPInputStream(stream).readLines().each { line ->
                def parts = line.split(";")
                values << (parts[1] as double)
                weights << (parts[2] as double)
            }
        }
        def v = values as double[]
        def w = weights as double[]

        when:
        def model = OscillationCorrection.findOscillation(v, w)

        then:
        model.present

        and: "the modeled shift removes most of the oscillation band"
        def corrected = new double[v.length]
        for (int y = 0; y < v.length; y++) {
            corrected[y] = v[y] - model.get().shiftAt(y)
        }
        // the slowly varying baseline is excluded from the comparison, since the
        // model intentionally does not correct it
        detrendedRms(corrected, w) < 0.55 * detrendedRms(v, w)
    }

    def "ignores outliers in the displacement signal"() {
        given:
        def n = 1500
        def period = 120.0
        def amplitude = 1.5
        def random = new Random(123)
        def values = new double[n]
        def weights = new double[n]
        for (int y = 0; y < n; y++) {
            values[y] = amplitude * Math.sin(2 * Math.PI * y / period) + 0.3 * random.nextGaussian()
            weights[y] = 1.0
        }
        for (int y = 100; y < 1500; y += 100) {
            values[y] += 15
        }

        when:
        def model = OscillationCorrection.findOscillation(values, weights)

        then:
        model.present
        Math.abs(model.get().period() - period) / period < 0.01
        Math.abs(model.get().amplitude() - amplitude) < 0.25
    }

    private static Ellipse createTestEllipse(int width, int height) {
        double cx = width / 2.0
        double cy = height / 2.0
        double rx = width * 0.4
        double ry = height * 0.4
        double a = 1.0 / (rx * rx)
        double c = 1.0 / (ry * ry)
        double d = -2.0 * cx / (rx * rx)
        double e = -2.0 * cy / (ry * ry)
        double f = cx * cx / (rx * rx) + cy * cy / (ry * ry) - 1.0
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static double detrendedRms(double[] values, double[] weights) {
        int window = 300
        double squares = 0
        double weightSum = 0
        for (int start = 0; start < values.length; start += window) {
            int end = Math.min(values.length, start + window)
            double mean = 0
            double wsum = 0
            for (int i = start; i < end; i++) {
                mean += weights[i] * values[i]
                wsum += weights[i]
            }
            if (wsum == 0) {
                continue
            }
            mean /= wsum
            for (int i = start; i < end; i++) {
                squares += weights[i] * (values[i] - mean) * (values[i] - mean)
            }
            weightSum += wsum
        }
        return Math.sqrt(squares / weightSum)
    }
}
