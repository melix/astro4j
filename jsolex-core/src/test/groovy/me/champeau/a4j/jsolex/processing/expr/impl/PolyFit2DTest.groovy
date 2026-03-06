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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification
import spock.lang.Subject

class PolyFit2DTest extends Specification {

    @Subject
    PolyFit2D polyFit2D = new PolyFit2D([:], Broadcaster.NO_OP)

    private static ImageWrapper32 createImageWithLinearGradient(int size, double cx, double cy, double radius) {
        float[][] data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                def px = x - cx
                def py = y - cy
                if (px * px + py * py < radius * radius) {
                    data[y][x] = (float) (2.0 * (x - cx) / radius + 3.0 * (y - cy) / radius)
                }
            }
        }
        def metadata = new LinkedHashMap<Class<?>, Object>()
        // Circle equation: (x-cx)^2 + (y-cy)^2 = r^2
        // Expanded: x^2 + y^2 - 2*cx*x - 2*cy*y + cx^2 + cy^2 - r^2 = 0
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(
            1.0d, 0.0d, 1.0d,
            -2.0d * cx, -2.0d * cy,
            cx * cx + cy * cy - radius * radius
        ))
        metadata.put(Ellipse.class, ellipse)
        new ImageWrapper32(size, size, data, metadata)
    }

    def "fits a linear gradient within the disk"() {
        given:
        def size = 100
        def cx = 50.0d
        def cy = 50.0d
        def radius = 40.0d
        def img = createImageWithLinearGradient(size, cx, cy, radius)

        when:
        ImageWrapper32 result = polyFit2D.polyFit2D([image: img, degree: 1]) as ImageWrapper32

        then:
        def fitRadius = 0.97 * radius
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                def px = x - cx
                def py = y - cy
                if (px * px + py * py < fitRadius * fitRadius) {
                    def expected = (float) (2.0 * px / radius + 3.0 * py / radius)
                    assert Math.abs(result.data()[y][x] - expected) < 0.1f
                }
            }
        }
    }

    def "missing ellipse throws"() {
        given:
        float[][] data = new float[10][10]
        def img = new ImageWrapper32(10, 10, data, [:])

        when:
        polyFit2D.polyFit2D([image: img])

        then:
        thrown(IllegalArgumentException)
    }

    def "default degree works"() {
        given:
        def img = createImageWithLinearGradient(100, 50.0d, 50.0d, 40.0d)

        when:
        ImageWrapper32 result = polyFit2D.polyFit2D([image: img]) as ImageWrapper32

        then:
        result != null
        result.width() == 100
        result.height() == 100
    }

    def "RGB input throws"() {
        given:
        float[][] r = new float[10][10]
        float[][] g = new float[10][10]
        float[][] b = new float[10][10]
        def rgb = new RGBImage(10, 10, r, g, b, [:])

        when:
        polyFit2D.polyFit2D([image: rgb])

        then:
        thrown(IllegalArgumentException)
    }
}
