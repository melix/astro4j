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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.AnnulusMask
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.InvertedMask
import me.champeau.a4j.jsolex.processing.util.RangeMask
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class ImageMaskTest extends Specification {
    private static final int SIZE = 200
    private static final double CX = 100
    private static final double CY = 100
    private static final double R = 50

    def "annulus_mask and range_mask create masks"() {
        expect:
        masking().annulusMask([rmin: 1.1, rmax: 1.5]) == new AnnulusMask(1.1, 1.5)
        masking().annulusMask([rmax: 1.0]) == new AnnulusMask(0, 1.0)
        masking().rangeMask([lo: 100, hi: 5000]) == new RangeMask(100, 5000)
        masking().rangeMask([lo: 100]) == new RangeMask(100, Double.POSITIVE_INFINITY)
    }

    def "invert_mask inverts a mask and inverting twice restores it"() {
        given:
        def mask = new AnnulusMask(0, 1)

        when:
        def inverted = masking().invertMask([mask: mask])

        then:
        inverted == new InvertedMask(mask)

        when:
        def restored = masking().invertMask([mask: inverted])

        then:
        restored == mask
    }

    def "invert_mask requires a mask"() {
        when:
        masking().invertMask([mask: 42])

        then:
        thrown(IllegalArgumentException)
    }

    def "invalid masks are rejected"() {
        when:
        masking().annulusMask([rmin: 1.5, rmax: 1.1])

        then:
        thrown(IllegalArgumentException)

        when:
        masking().rangeMask([lo: 5000, hi: 100])

        then:
        thrown(IllegalArgumentException)
    }

    def "percentile_stretch computes its statistics over the mask only"() {
        given: "an image whose annulus values span 10000-20000, everything else 40000-59900"
        def mask = masking().annulusMask([rmin: 1.1, rmax: 1.5])

        when:
        def stretched = (stretching().percentileStretch([img: annulusImage(), mask: mask]) as ImageWrapper32).data()
        def unrestricted = (stretching().percentileStretch([img: annulusImage()]) as ImageWrapper32).data()

        then: "the mid-annulus value lands mid-range instead of in the shadows"
        def midAnnulus = stretched[100][(int) (CX + 1.3 * R)]
        Math.abs(midAnnulus - 32767) < 3000
        unrestricted[100][(int) (CX + 1.3 * R)] < 16384

        and: "pixels outside the mask are still transformed, clipped to white"
        stretched[100][100] == 65535f
        stretched[2][2] == 65535f
    }

    def "a value range selecting the same pixels as the annulus gives the same stretch"() {
        given: "annulus pixels are exactly those with values in 10000-20000"
        def byGeometry = masking().annulusMask([rmin: 1.1, rmax: 1.5])
        def byValue = masking().rangeMask([lo: 10000, hi: 20000])

        when:
        def geometric = (stretching().mtfAutostretch([img: annulusImage(), mask: byGeometry]) as ImageWrapper32).data()
        def byRange = (stretching().mtfAutostretch([img: annulusImage(), mask: byValue]) as ImageWrapper32).data()
        def unrestricted = (stretching().mtfAutostretch([img: annulusImage()]) as ImageWrapper32).data()

        then:
        geometric == byRange
        geometric != unrestricted
    }

    def "an inverted mask selects the complement"() {
        given: "a mask of everything but the annulus"
        def mask = masking().invertMask([mask: masking().annulusMask([rmin: 1.1, rmax: 1.5])])

        when: "stats are computed over the constant 50000 region only"
        def stretched = (stretching().percentileStretch([img: annulusImage(), mask: mask]) as ImageWrapper32).data()

        then: "the annulus values, below the stats range, are clipped to black"
        stretched[100][(int) (CX + 1.3 * R)] == 0f
    }

    def "a mask selecting no pixel leaves the image unchanged"() {
        given: "an annulus entirely outside the frame"
        def mask = masking().annulusMask([rmin: 3.0, rmax: 4.0])

        when:
        def stretched = (stretching().percentileStretch([img: annulusImage(), mask: mask]) as ImageWrapper32).data()

        then:
        stretched == annulusImage().data()
    }

    def "stretching functions reject masks of the wrong type"() {
        when:
        stretching().percentileStretch([img: annulusImage(), mask: "oops"])

        then:
        thrown(IllegalArgumentException)
    }

    private static Masking masking() {
        new Masking([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    private static Stretching stretching() {
        new Stretching([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    private static ImageWrapper32 annulusImage() {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def r = Math.sqrt((x - CX) * (x - CX) + (y - CY) * (y - CY)) / R
                if (r >= 1.1 && r <= 1.5) {
                    data[y][x] = (float) (10000 + 25000 * (r - 1.1))
                } else {
                    data[y][x] = (float) (40000 + 100 * x)
                }
            }
        }
        new ImageWrapper32(SIZE, SIZE, data, [(Ellipse): circle(CX, CY, R)] as Map<Class<?>, Object>)
    }

    private static Ellipse circle(double cx, double cy, double radius) {
        double a = 1.0 / (radius * radius), c = 1.0 / (radius * radius)
        double d = -2.0 * cx / (radius * radius), e = -2.0 * cy / (radius * radius)
        double f = cx * cx / (radius * radius) + cy * cy / (radius * radius) - 1.0
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }
}
