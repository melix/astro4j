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
import me.champeau.a4j.jsolex.processing.sun.ImageUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Path

class MosaicBlendIntegrationTest extends Specification {

    private static ImageWrapper32 loadPanel(String resource) {
        var img = ImageIO.read(ImageUtils.getResource(resource))
        int w = img.width
        int h = img.height
        float[][] data = new float[h][w]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getRGB(x, y)
                int grey = pixel & 0xFF
                data[y][x] = (grey << 8) as float
            }
        }
        new ImageWrapper32(w, h, data, [:])
    }

    private static void savePng(ImageWrapper32 image, Path dest) {
        int w = image.width()
        int h = image.height()
        var bi = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        var data = image.data()
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = Math.clamp((int) (data[y][x]) >> 8, 0, 255)
                int rgb = (v << 16) | (v << 8) | v
                bi.setRGB(x, y, rgb)
            }
        }
        ImageIO.write(bi, "png", dest.toFile())
    }

    private static ImageWrapper32 padTo(ImageWrapper32 src, int width, int height) {
        if (src.width() == width && src.height() == height) {
            return src
        }
        float[][] out = new float[height][width]
        int sx = (width - src.width()) / 2
        int sy = (height - src.height()) / 2
        var srcData = src.data()
        for (int y = 0; y < src.height(); y++) {
            int ty = y + sy
            if (ty < 0 || ty >= height) continue
            for (int x = 0; x < src.width(); x++) {
                int tx = x + sx
                if (tx < 0 || tx >= width) continue
                out[ty][tx] = srcData[y][x]
            }
        }
        new ImageWrapper32(width, height, out, [:])
    }

    def "blends two real panels and writes result for visual inspection"() {
        given: "two real strip panels (upper and lower halves of the sun)"
        var upper = loadPanel("mosaic_panel_upper.jpg")
        var lower = loadPanel("mosaic_panel_lower.jpg")
        int W = Math.max(upper.width(), lower.width())
        int H = Math.max(upper.height(), lower.height())
        upper = padTo(upper, W, H)
        lower = padTo(lower, W, H)
        float background = 200f

        and: "print row-mean profiles so we can see real content extents"
        printRowProfile("upper", upper, H)
        printRowProfile("lower", lower, H)

        when: "running multiBandBlend (via mosaicBlend) directly"
        var blender = new TestableMosaicBlender()
        var smoothLower = lower.data()
        var result = blender.blend(upper, lower, smoothLower, background)

        and: "save to a file for visual inspection"
        var outPath = Path.of(System.getProperty("java.io.tmpdir"), "mosaic_blend_result.png")
        savePng(result, outPath)
        println "[mosaic-blend-test] Wrote result to ${outPath}"

        then:
        result.width() == W
        result.height() == H
    }

    private static void printRowProfile(String name, ImageWrapper32 img, int height) {
        var data = img.data()
        int w = img.width()
        def samples = []
        int step = (int) Math.max(1, (int) (height / 40))
        for (int y = 0; y < height; y += step) {
            double sum = 0
            for (int x = 0; x < w; x++) sum += data[y][x]
            samples << sprintf("y=%d:%.0f", y, sum / w)
        }
        println "[${name}] row profile (every ${step} rows): ${samples.join(' ')}"
    }

    /** Minimal wrapper that exposes mosaicBlend for testing. */
    static class TestableMosaicBlender {
        ImageWrapper32 blend(ImageWrapper32 reference, ImageWrapper32 compare, float[][] smoothCompareData, float background) {
            int w = reference.width()
            int h = reference.height()
            var mc = new MosaicComposition([:], Broadcaster.NO_OP, null, null)
            var method = MosaicComposition.class.getDeclaredMethod("mosaicBlend",
                ImageWrapper32, ImageWrapper32, float[][], float, int, int)
            method.setAccessible(true)
            return (ImageWrapper32) method.invoke(mc, reference, compare, smoothCompareData, background, w, h)
        }
    }
}
