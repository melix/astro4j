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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.LegacyImageAlgorithms
import me.champeau.a4j.jsolex.processing.color.ColorCurve
import me.champeau.a4j.jsolex.processing.util.ImageFormat
import me.champeau.a4j.math.image.ImagePlane
import spock.lang.Specification
import spock.lang.TempDir

import javax.imageio.ImageIO
import java.nio.file.Files
import java.nio.file.Path

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.assertSameBits
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class ImageUtilsPlanesTest extends Specification {
    @TempDir
    Path tempDir

    def "mono images written from a plane or an array are identical to the reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 7L * width + height)
        def reference = tempDir.resolve("reference.png").toFile()
        ImageIO.write(LegacyImageAlgorithms.monoImage(width, height, data), "png", reference)

        when:
        def fromPlane = ImageUtils.writeMonoImage(ImagePlane.of(width, height, data), tempDir.resolve("plane.png").toFile(), Set.of(ImageFormat.PNG))
        def fromArray = ImageUtils.writeMonoImage(width, height, data, tempDir.resolve("array.png").toFile(), Set.of(ImageFormat.PNG))

        then:
        Files.readAllBytes(fromPlane.first.toPath()) == Files.readAllBytes(reference.toPath())
        Files.readAllBytes(fromArray.first.toPath()) == Files.readAllBytes(reference.toPath())

        where:
        [width, height] << SIZES
    }

    def "color images written from planes or arrays are identical to the reference on #width x #height"() {
        given:
        def r = randomImage(width, height, 11L * width + height)
        def g = randomImage(width, height, 13L * width + height)
        def b = randomImage(width, height, 17L * width + height)
        def reference = tempDir.resolve("reference.png").toFile()
        ImageIO.write(LegacyImageAlgorithms.rgbImage(width, height, r, g, b), "png", reference)

        when:
        def fromPlane = ImageUtils.writeRgbImage(ImagePlane.of(width, height, r), ImagePlane.of(width, height, g), ImagePlane.of(width, height, b), tempDir.resolve("plane.png").toFile(), Set.of(ImageFormat.PNG))
        def fromArray = ImageUtils.writeRgbImage(width, height, r, g, b, tempDir.resolve("array.png").toFile(), Set.of(ImageFormat.PNG))

        then:
        Files.readAllBytes(fromPlane.first.toPath()) == Files.readAllBytes(reference.toPath())
        Files.readAllBytes(fromArray.first.toPath()) == Files.readAllBytes(reference.toPath())

        where:
        [width, height] << SIZES
    }

    def "color curve conversion of a plane is identical to the reference on #width x #height"() {
        given:
        def data = randomImage(width, height, 19L * width + height)
        def curve = new ColorCurve("test", 30, 200, 120, 90, 10, 60)
        def expected = LegacyImageAlgorithms.convertToRGB(curve, data)

        when:
        def planes = ImageUtils.convertToRGB(curve, ImagePlane.of(width, height, data))

        then:
        assertSameBits(expected, planes.collect { it.toArray() } as float[][][])
        assertSameBits(expected, ImageUtils.convertToRGB(curve, data))

        where:
        [width, height] << SIZES
    }

    def "HSL conversions of planes and arrays are identical to the reference on #width x #height"() {
        given:
        def rgb = [randomImage(width, height, 23L * width + height), randomImage(width, height, 29L * width + height), randomImage(width, height, 37L * width + height)] as float[][][]
        def expectedHsl = LegacyImageAlgorithms.fromRGBtoHSL(rgb)
        def expectedRgb = LegacyImageAlgorithms.fromHSLtoRGB(expectedHsl, new float[3][height][width])
        def planes = rgb.collect { ImagePlane.of(width, height, it) }

        when:
        def hslFromPlanes = ImageUtils.fromRGBtoHSL(planes[0], planes[1], planes[2])
        def hslFromArrays = ImageUtils.fromRGBtoHSL(rgb)
        def output = (0..<3).collect { ImagePlane.allocate(width, height) }
        ImageUtils.fromHSLtoRGB(expectedHsl, output[0], output[1], output[2])

        then:
        assertSameBits(expectedHsl, hslFromPlanes)
        assertSameBits(expectedHsl, hslFromArrays)
        assertSameBits(expectedRgb, output.collect { it.toArray() } as float[][][])
        assertSameBits(expectedRgb, ImageUtils.fromHSLtoRGB(expectedHsl))

        where:
        [width, height] << SIZES
    }
}
