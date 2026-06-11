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

class JaggingCorrectionTest extends Specification {

    def "realigns jagged borders and emits a chart debug image"() {
        given: "a disk with random per-line jitter"
        def width = 800
        def height = 1200
        def ellipse = createTestEllipse(width, height)
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
                def shift = 3 * random.nextGaussian()
                left[y] = (int) Math.round(x1 + shift)
                right[y] = (int) Math.round(x2 + shift)
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
            captured.name = name
            captured.width = w
            captured.height = h
            captured.rgb = supplier.get()
        }] as ImageEmitter

        when:
        def model = JaggingCorrection.computeModel(borders, ellipse, width, height, JaggingCorrection.DEFAULT_SIGMA, null, emitter)
        model.ifPresent { JaggingCorrection.applyCorrection(image, it) }

        then:
        model.present

        and: "the disk borders are realigned with the ellipse"
        // a few sigma-clipped lines receive an interpolated correction which can be
        // off by several pixels, so the alignment is checked with the RMS error
        def squares = 0d
        def count = 0
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
            squares += (edge - x1) * (edge - x1)
            count++
        }
        Math.sqrt(squares / count) < 1.0

        and: "a chart debug image was emitted"
        captured.kind == GeneratedImageKind.DEBUG
        captured.name == "jagging-correction"
        captured.rgb != null
        captured.rgb.length == 3
    }

    def "returns an empty model when no border was detected"() {
        given:
        def width = 800
        def height = 1200
        def ellipse = createTestEllipse(width, height)
        def left = new int[height]
        def right = new int[height]
        Arrays.fill(left, -1)
        Arrays.fill(right, -1)

        expect:
        JaggingCorrection.computeModel(new PhenomenaDetector.BorderDetection(left, right), ellipse, width, height, JaggingCorrection.DEFAULT_SIGMA, null, null).empty
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
}
