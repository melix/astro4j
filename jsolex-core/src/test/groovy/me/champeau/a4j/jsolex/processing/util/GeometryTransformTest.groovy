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

import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords
import me.champeau.a4j.math.Point2D
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.regression.EllipseRegression
import spock.lang.Specification

class GeometryTransformTest extends Specification {

    def "stretches the vertical axis when downsampling is disallowed"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, 400, true)

        then: "the detected ratio is above one, so the image is stretched instead of shrunk"
        transform.detectedRatio() > 1
        transform.sx() == 1.0d
        transform.sy() == transform.detectedRatio()
    }

    def "shrinks the horizontal axis when downsampling is allowed"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, 400, false)

        then:
        transform.sy() == 1.0d
        transform.sx() == 1 / transform.detectedRatio()
    }

    def "a forced ratio overrides the detected one but is still reported"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, 2.0d, 400, 400, true)

        then:
        transform.sy() == 2.0d
        transform.sx() == 1.0d
        transform.detectedRatio() != 2.0d
    }

    def "a forced tilt overrides the ellipse rotation"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        expect:
        GeometryTransform.of(ellipse, 0d, null, 400, 400, true).theta() == 0d
        GeometryTransform.of(ellipse, 0d, null, 400, 400, true).shear() == 0d
        GeometryTransform.of(ellipse, null, null, 400, 400, true).theta() == ellipse.rotationAngle()
        GeometryTransform.of(ellipse, null, null, 400, 400, true).shear() != 0d
    }

    def "the shift keeps the sheared image within positive coordinates"() {
        given:
        def height = 400
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(tiltDegrees))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, height, true)

        then: "the leftmost corner of the sheared image lands at abscissa zero or above, up to the centring offset"
        def corners = [transform.transformX(0, 0), transform.transformX(0, height)]
        corners.min() - transform.offsetX() > -1e-9

        where:
        tiltDegrees << [-20, -5, 5, 20]
    }

    def "owns the dimensions of the corrected image (downsampling allowed: #allowDownsampling)"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(15))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, 400, !allowDownsampling)
        def corrected = GeometryUtils.applyGeometryCorrection(new ImageWrapper32(400, 400, new float[400][400], [:]), transform, 0f)

        then:
        corrected.width() == transform.outputWidth()
        corrected.height() == transform.outputHeight()

        where:
        allowDownsampling << [true, false]
    }

    def "the reference coordinates recorded for the correction invert it exactly (downsampling allowed: #allowDownsampling)"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(15))
        def transform = GeometryTransform.of(ellipse, null, null, 400, 400, !allowDownsampling)
        def coords = new ReferenceCoords([])
                .addShearShiftCombined(transform.shear(), transform.shift())
                .addScaleX(transform.sx())
                .addScaleY(transform.sy())
                .addOffset2D(-transform.offsetX(), -transform.offsetY())

        when:
        def source = new Point2D(x, y)
        def corrected = new Point2D(transform.transformX(x, y), transform.transformY(y))
        def back = coords.determineOriginalCoordinates(corrected, ReferenceCoords.NO_LIMIT)

        then:
        Math.abs(back.x() - source.x()) < 1e-6
        Math.abs(back.y() - source.y()) < 1e-6

        where:
        [x, y, allowDownsampling] << [[0d, 137d, 399d], [0d, 42d, 399d], [true, false]].combinations()
    }

    private static Ellipse ellipseOf(double a, double b, double theta) {
        def points = (0..<64).collect {
            def t = 2 * Math.PI * it / 64
            def x = a * Math.cos(t)
            def y = b * Math.sin(t)
            new Point2D(200 + x * Math.cos(theta) - y * Math.sin(theta), 200 + x * Math.sin(theta) + y * Math.cos(theta))
        }
        new EllipseRegression(points).solve()
    }
}
