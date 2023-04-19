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
package me.champeau.a4j.jsolex.processing.stats

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.imageio.ImageIO

class DefaultImageStatsComputerTest extends Specification {
    private static final ImageWrapper MONO_IMAGE = load("mono")
    private static final ImageWrapper COLOR_IMAGE = load("color")

    @Subject
    DefaultImageStatsComputer producer = new DefaultImageStatsComputer()

    private static ImageWrapper load(String name) {
        def image = ImageIO.read(RGBImageStats.getResourceAsStream("/${name}.tif"))
        def rgb = new double[image.width * image.height * 3]
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                int pixel = image.getRGB(x, y)
                double r = (pixel >> 16) & 0xFF
                double g = (pixel >> 8) & 0xFF
                double b = pixel & 0xFF
                int i = (y * image.width + x) * 3
                rgb[i] = r
                rgb[i + 1] = g
                rgb[i + 2] = b
            }
        }
        return new ImageWrapper(rgb, image.width, image.height)
    }

    @Unroll("computes histogram of #name image")
    void "computes histogram of image"() {
        when:
        def stats = RGBImageStats.of(
                producer.computeStats(image.rgb, image.width, image.height)
        )
        def r = stats.r()
        def g = stats.g()
        def b = stats.b()

        then:
        r.min() == minR
        r.max() == maxR
        g.min() == minG
        g.max() == maxG
        b.min() == minB
        b.max() == maxB
        // mono image, channels are the same
        def rh = r.histogram()
        rh[0] == rh0
        rh[13] == rh13
        rh[242] == rh242
        int accR = r.cumulativeHistogram()[242]
        accR == image.width * image.height

        def rg = g.histogram()
        rg[0] == rg0
        rg[13] == rg13
        rg[242] == rg242

        int accG = g.cumulativeHistogram()[242]
        accG == image.width * image.height

        def rb = b.histogram()
        rb[0] == rb0
        rb[13] == rb13
        rb[242] == rb242

        int accB = b.cumulativeHistogram()[242]
        accB == image.width * image.height

        where:
        name    | image       | minR | maxR | minG | maxG | minB | maxB | rh0    | rh13  | rh242 | rg0    | rg13  | rg242 | rb0    | rb13  | rb242
        'mono'  | MONO_IMAGE  | 0    | 242  | 0    | 242  | 0    | 242  | 120095 | 27875 | 1     | 120095 | 27875 | 1     | 120095 | 27875 | 1
        'color' | COLOR_IMAGE | 0    | 208  | 0    | 214  | 0    | 180  | 137937 | 486   | 0     | 121836 | 645   | 0     | 107488 | 741   | 0
    }

    private static class ImageWrapper {
        final double[] rgb
        final int width
        final int height

        ImageWrapper(double[] rgb, int width, int height) {
            this.rgb = rgb
            this.width = width
            this.height = height
        }
    }
}
