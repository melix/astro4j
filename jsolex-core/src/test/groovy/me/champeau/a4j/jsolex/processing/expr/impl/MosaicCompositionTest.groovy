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

class MosaicCompositionTest extends Specification {

    private static ImageWrapper32 loadPanel(String resource) {
        var img = ImageIO.read(ImageUtils.getResource(resource))
        int w = img.width
        int h = img.height
        float[][] data = new float[h][w]
        if (img.colorModel.pixelSize >= 16) {
            var raster = img.raster
            int[] pixel = new int[1]
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    raster.getPixel(x, y, pixel)
                    data[y][x] = pixel[0] as float
                }
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    data[y][x] = ((img.getRGB(x, y) & 0xFF) << 8) as float
                }
            }
        }
        new ImageWrapper32(w, h, data, [:])
    }

    private static MosaicComposition newMosaic() {
        var context = new HashMap<Class<?>, Object>()
        var broadcaster = Broadcaster.NO_OP
        var ellipseFit = new EllipseFit(context, broadcaster)
        var crop = new Crop(context, broadcaster)
        var scaling = new Scaling(context, broadcaster, crop)
        return new MosaicComposition(context, broadcaster, ellipseFit, scaling)
    }

    private static long countPixelsAbove(ImageWrapper32 img, float threshold) {
        var data = img.data()
        long count = 0L
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                if (data[y][x] > threshold) {
                    count++
                }
            }
        }
        return count
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

    def "empty image list returns an empty list"() {
        given:
        var mc = newMosaic()

        expect:
        mc.mosaic([images: []]) == []
    }

    def "single-image input returns that image unchanged"() {
        given:
        var mc = newMosaic()
        var top = loadPanel("mosaic_top.png")

        when:
        var result = mc.mosaic([images: [top]])

        then:
        result.is(top)
    }

    def "rejects tile size smaller than the minimum of 16"() {
        given:
        var mc = newMosaic()
        var top = loadPanel("mosaic_top.png")
        var middle = loadPanel("mosaic_middle.png")

        when:
        mc.mosaic([images: [top, middle], ts: 8])

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects non-positive sampling factor"() {
        given:
        var mc = newMosaic()
        var top = loadPanel("mosaic_top.png")
        var middle = loadPanel("mosaic_middle.png")

        when:
        mc.mosaic([images: [top, middle], sampling: 0f])

        then:
        thrown(IllegalArgumentException)
    }

    def "assembles 3 overlapping panels into a single mosaic with visible disk content"() {
        given:
        var mc = newMosaic()
        var top = loadPanel("mosaic_top.png")
        var middle = loadPanel("mosaic_middle.png")
        var bottom = loadPanel("mosaic_bottom.png")

        when:
        var result = mc.mosaic([images: [top, middle, bottom], ts: 64, sampling: 0.25f])

        then: "a single wrapped image is returned with non-zero dimensions"
        result instanceof ImageWrapper32
        var img = (ImageWrapper32) result
        img.width() > 0
        img.height() > 0

        and: "disk content covers a substantial fraction of the output"
        var litPixels = countPixelsAbove(img, 1000f)
        var total = (long) img.width() * (long) img.height()
        litPixels > total * 0.1
        litPixels < total

        and: "write a PNG for visual inspection"
        savePng(img, Path.of(System.getProperty("java.io.tmpdir"), "mosaic_composition_result.png"))
    }
}
