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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.math.Point2D
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.regression.EllipseRegression
import spock.lang.Specification

/**
 * Verifies that the geometry warp and the analytic ellipse transform used for
 * metadata stay in agreement: the corrected disk must actually land where
 * {@link GeometryUtils#computeCorrectedCircle} says it does.
 */
class GeometryWarpAlignmentTest extends Specification {

    private static final float DISK_VALUE = 40000f
    private static final float THRESHOLD = 20000f

    def "corrected disk lands where computeCorrectedCircle predicts (tilt #tiltDegrees deg, downsampling allowed: #allowDownsampling)"() {
        given:
        def width = 400
        def height = 400
        def a = 120d
        def b = 80d
        def theta = Math.toRadians(tiltDegrees)
        def ellipse = ellipseOf(200d, 200d, a, b, theta)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = ellipse.isWithin(x, y) ? DISK_VALUE : 0f
            }
        }

        when:
        def transform = GeometryTransform.of(ellipse, null, null, width, height, !allowDownsampling)
        def corrected = GeometryUtils.applyGeometryCorrection(new ImageWrapper32(width, height, data, [:]), transform, 0f)
        def circle = GeometryUtils.computeCorrectedCircle(ellipse, transform)
        def semi = circle.semiAxis()
        def measured = centroid(corrected)
        def measuredRadius = Math.sqrt(brightArea(corrected) / Math.PI)

        then: "the predicted shape is a circle"
        Math.abs(semi.a() - semi.b()) < 1.0

        and: "the predicted centre is exactly the transform of the source centre"
        Math.abs(circle.center().a() - transform.transformX(ellipse.center().a(), ellipse.center().b())) < 1e-6
        Math.abs(circle.center().b() - transform.transformY(ellipse.center().b())) < 1e-6

        and: "the actual bright blob is centred where predicted, up to the centroid estimator"
        Math.abs(measured[0] - circle.center().a()) < 1.0
        Math.abs(measured[1] - circle.center().b()) < 1.0

        and: "the actual blob radius matches the predicted radius"
        Math.abs(measuredRadius - semi.a()) < 1.5

        where:
        [tiltDegrees, allowDownsampling] << [[-20, -5, 5, 20], [true, false]].combinations()
    }

    def "the warp moves a spot exactly where transformX and transformY say (tilt #tiltDegrees deg, downsampling allowed: #allowDownsampling)"() {
        given: "a small smooth spot, whose intensity centroid is preserved by the resampling kernel"
        def width = 400
        def height = 400
        def spotX = 150.0d
        def spotY = 260.0d
        def sigma = 4.0d
        def ellipse = ellipseOf(200d, 200d, 120d, 80d, Math.toRadians(tiltDegrees))
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def dx = x - spotX
                def dy = y - spotY
                data[y][x] = (float) (DISK_VALUE * Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma)))
            }
        }

        when:
        def transform = GeometryTransform.of(ellipse, null, null, width, height, !allowDownsampling)
        def corrected = GeometryUtils.applyGeometryCorrection(new ImageWrapper32(width, height, data, [:]), transform, 0f)
        def measured = weightedCentroid(corrected)

        then: "the spot lands within a small fraction of a pixel of the predicted position"
        Math.abs(measured[0] - transform.transformX(spotX, spotY)) < 0.01
        Math.abs(measured[1] - transform.transformY(spotY)) < 0.01

        where:
        [tiltDegrees, allowDownsampling] << [[-20, -5, 5, 20], [true, false]].combinations()
    }

    def "the widened downscale kernel preserves the mean level"() {
        given: "a flat field, so any weight-normalisation error shows up directly"
        def width = 400
        def height = 400
        def a = 150d
        def b = 100d
        def theta = Math.toRadians(15)
        def ellipse = ellipseOf(200d, 200d, a, b, theta)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            Arrays.fill(data[y], 30000f)
        }

        when: "downsampling is allowed, which widens the horizontal kernel"
        def transform = GeometryTransform.of(ellipse, null, null, width, height, false)
        def corrected = GeometryUtils.applyGeometryCorrection(new ImageWrapper32(width, height, data, [:]), transform, 0f)

        then: "the interior is unchanged, no ripple and no level shift"
        def d = corrected.data()
        def cy = corrected.height() / 2 as int
        def samples = (100..<(corrected.width() - 100)).collect { d[cy][it] as double }
        samples.min() > 29999
        samples.max() < 30001
    }

    def "a transform computed for other dimensions is rejected"() {
        given: "a transform whose shift was computed for a taller image"
        def ellipse = ellipseOf(200d, 200d, 120d, 80d, Math.toRadians(20))
        def transform = GeometryTransform.of(ellipse, null, null, 400, 800, true)

        when:
        GeometryUtils.applyGeometryCorrection(new ImageWrapper32(400, 400, new float[400][400], [:]), transform, 0f)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("400x800")
        ex.message.contains("400x400")
    }

    def "a transform computed for another width is rejected"() {
        given:
        def ellipse = ellipseOf(200d, 200d, 120d, 80d, Math.toRadians(20))
        def transform = GeometryTransform.of(ellipse, null, null, 500, 400, true)

        when:
        GeometryUtils.applyGeometryCorrection(new ImageWrapper32(400, 400, new float[400][400], [:]), transform, 0f)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("500x400")
    }

    private static Ellipse ellipseOf(double cx, double cy, double a, double b, double theta) {
        def points = (0..<64).collect {
            def t = 2 * Math.PI * it / 64
            def x = a * Math.cos(t)
            def y = b * Math.sin(t)
            new Point2D(cx + x * Math.cos(theta) - y * Math.sin(theta), cy + x * Math.sin(theta) + y * Math.cos(theta))
        }
        new EllipseRegression(points).solve()
    }

    private static double[] centroid(ImageWrapper32 image) {
        double sx = 0
        double sy = 0
        double count = 0
        def data = image.data()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (data[y][x] > THRESHOLD) {
                    sx += x
                    sy += y
                    count++
                }
            }
        }
        [sx / count, sy / count] as double[]
    }

    private static double[] weightedCentroid(ImageWrapper32 image) {
        double sx = 0
        double sy = 0
        double sum = 0
        def data = image.data()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                def v = data[y][x] as double
                sx += v * x
                sy += v * y
                sum += v
            }
        }
        [sx / sum, sy / sum] as double[]
    }

    private static double brightArea(ImageWrapper32 image) {
        double count = 0
        def data = image.data()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (data[y][x] > THRESHOLD) {
                    count++
                }
            }
        }
        count
    }
}
