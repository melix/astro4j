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

class GeometryTransformTest extends Specification {

    def "stretches the vertical axis when downsampling is disallowed"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, true)

        then: "the detected ratio is above one, so the image is stretched instead of shrunk"
        transform.detectedRatio() > 1
        transform.sx() == 1.0d
        transform.sy() == transform.detectedRatio()
    }

    def "shrinks the horizontal axis when downsampling is allowed"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, 400, false)

        then:
        transform.sy() == 1.0d
        transform.sx() == 1 / transform.detectedRatio()
    }

    def "a forced ratio overrides the detected one but is still reported"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        when:
        def transform = GeometryTransform.of(ellipse, null, 2.0d, 400, true)

        then:
        transform.sy() == 2.0d
        transform.sx() == 1.0d
        transform.detectedRatio() != 2.0d
    }

    def "a forced tilt overrides the ellipse rotation"() {
        given:
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(10))

        expect:
        GeometryTransform.of(ellipse, 0d, null, 400, true).theta() == 0d
        GeometryTransform.of(ellipse, 0d, null, 400, true).shear() == 0d
        GeometryTransform.of(ellipse, null, null, 400, true).theta() == ellipse.rotationAngle()
        GeometryTransform.of(ellipse, null, null, 400, true).shear() != 0d
    }

    def "the shift keeps the sheared image within positive coordinates"() {
        given:
        def height = 400
        def ellipse = ellipseOf(120d, 80d, Math.toRadians(tiltDegrees))

        when:
        def transform = GeometryTransform.of(ellipse, null, null, height, true)

        then: "the leftmost corner of the sheared image lands at abscissa zero or above"
        def corners = [transform.transformX(0, 0), transform.transformX(0, height)]
        corners.min() > -1e-9

        where:
        tiltDegrees << [-20, -5, 5, 20]
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
