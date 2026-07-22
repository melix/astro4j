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
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class MtfAutostretchAnnulusTest extends Specification {

    static final double R = 100
    static final float DISK_FILL = 5000
    static final float SKY = 300

    private static Ellipse diskEllipse(double cx, double cy) {
        double a = 1.0 / (R * R), c = 1.0 / (R * R), d = -2.0 * cx / (R * R), e = -2.0 * cy / (R * R)
        double f = cx * cx / (R * R) + cy * cy / (R * R) - 1.0
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static double noise(double dx, double dy) {
        double v = Math.sin(dx * 12.9898 + dy * 78.233) * 43758.5453
        return 200 * (v - Math.floor(v))
    }

    // pixel value depends only on the position relative to disk center,
    // so two crops of the same scene share identical pixels where they overlap
    private static float pixelAt(double dx, double dy) {
        double r = Math.hypot(dx, dy)
        if (r <= R) {
            return DISK_FILL
        }
        double corona = 4000 * Math.exp(-(r - R) / 25.0)
        return (float) (SKY + corona + noise(dx, dy))
    }

    private ImageWrapper32 croppedScene(int margin) {
        int size = 2 * (int) (R + margin)
        double cx = size / 2, cy = size / 2
        float[][] data = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = pixelAt(x - cx, y - cy)
            }
        }
        def meta = new HashMap<Class<?>, Object>()
        meta.put(Ellipse.class, diskEllipse(cx, cy))
        return new ImageWrapper32(size, size, data, meta)
    }

    private static Stretching stretching() {
        new Stretching([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    private static float limbValue(ImageWrapper32 image) {
        int cx = (int) (image.width() / 2)
        int cy = (int) (image.height() / 2)
        return image.data()[cy][cx + (int) (1.1 * R)]
    }

    private static double relativeDifference(float a, float b) {
        return Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b))
    }

    def "annulus statistics make the stretch independent of the crop margin"() {
        given:
        def tight = croppedScene(30)
        def wide = croppedScene(120)

        when:
        def stretchedTight = stretching().mtfAutostretch(img: tight, stats_rmin: 1.02, stats_rmax: 1.25) as ImageWrapper32
        def stretchedWide = stretching().mtfAutostretch(img: wide, stats_rmin: 1.02, stats_rmax: 1.25) as ImageWrapper32

        then:
        relativeDifference(limbValue(stretchedTight), limbValue(stretchedWide)) < 0.001

        when:
        def wholeFrameTight = stretching().mtfAutostretch(img: tight) as ImageWrapper32
        def wholeFrameWide = stretching().mtfAutostretch(img: wide) as ImageWrapper32

        then:
        relativeDifference(limbValue(wholeFrameTight), limbValue(wholeFrameWide)) > 0.05
    }

    def "annulus statistics require a detected solar disk"() {
        given:
        def image = new ImageWrapper32(64, 64, new float[64][64], [:])

        when:
        stretching().mtfAutostretch(img: image, stats_rmin: 1.02, stats_rmax: 1.25)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects invalid annulus bounds"() {
        when:
        stretching().mtfAutostretch(img: croppedScene(30), stats_rmin: 1.5, stats_rmax: 1.2)

        then:
        thrown(IllegalArgumentException)
    }
}
