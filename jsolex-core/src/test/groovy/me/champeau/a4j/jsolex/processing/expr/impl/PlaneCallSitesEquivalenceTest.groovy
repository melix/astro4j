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
import me.champeau.a4j.jsolex.processing.sun.crop.Cropper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.ImagePlane
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoublePair
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.SIZES
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.assertSameBits
import static me.champeau.a4j.jsolex.processing.EquivalenceSupport.randomImage

class PlaneCallSitesEquivalenceTest extends Specification {

    def "cropping to a square or a rectangle (#width x #height)"() {
        given:
        def data = randomImage(width, height, 1)
        def plane = ImagePlane.of(width, height, data)

        expect:
        for (center in centers(width, height)) {
            for (radius in [0.5d, width / 3d, width * 2d]) {
                def disk = circle(center[0], center[1], radius)
                for (factor in [null, 1.1d, 0.7d]) {
                    def rounding = factor == null ? null : 16
                    def actual = Cropper.cropToSquare(plane, disk, 42f, factor, rounding)
                    def expected = referenceCropToSquare(data, width, height, disk, 42f, factor, rounding)
                    assert actual.centerShift() == expected.centerShift
                    assertSameBits(expected.cropped, actual.cropped().toArray())
                }
                for (dimensions in [[1, 1], [width, height], [width + 5, Math.max(1, height - 2)], [17, 33]]) {
                    def actual = Cropper.cropToRectangle(plane, disk, 7f, dimensions[0], dimensions[1])
                    def expected = referenceCropToRectangle(data, width, height, disk, 7f, dimensions[0], dimensions[1])
                    assert actual.centerShift() == expected.centerShift
                    assertSameBits(expected.cropped, actual.cropped().toArray())
                }
            }
        }

        where:
        [width, height] << SIZES
    }

    def "cropping an area of an image (#width x #height)"() {
        given:
        def data = randomImage(width, height, 1)
        def image = new ImageWrapper32(width, height, data, [:])
        def crop = new Crop([:], Broadcaster.NO_OP)

        expect:
        for (area in [[0, 0, width, height], [width.intdiv(3), height.intdiv(4), width.intdiv(2), height.intdiv(2)], [width - 1, height - 1, 10, 10]]) {
            def cropped = (ImageWrapper32) crop.crop([img: image, left: area[0], top: area[1], width: area[2], height: area[3]])
            def cropWidth = Math.min(area[2], width - area[0])
            def cropHeight = Math.min(area[3], height - area[1])
            def expected = new float[cropHeight][cropWidth]
            for (int y = 0; y < cropHeight; y++) {
                for (int x = 0; x < cropWidth; x++) {
                    expected[y][x] = data[y + area[1]][x + area[0]]
                }
            }
            assertSameBits(expected, cropped.pixels().toArray())
        }

        where:
        [width, height] << SIZES
    }

    def "placing an image in a canvas (#width x #height)"() {
        given:
        def data = randomImage(width, height, 1)
        def plane = ImagePlane.of(width, height, data)

        expect:
        for (canvas in [[width, height], [width * 2 + 1, height + 3], [1, 1]]) {
            for (offset in [[0, 0], [3, 2], [-2, -1], [width.intdiv(2), -height.intdiv(3)], [-width - 1, 0], [canvas[0], canvas[1]]]) {
                def actual = Scaling.placeInCanvas(plane, canvas[0], canvas[1], offset[0], offset[1])
                def expected = new float[canvas[1]][canvas[0]]
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int canvasX = x + offset[0]
                        int canvasY = y + offset[1]
                        if (canvasX >= 0 && canvasX < canvas[0] && canvasY >= 0 && canvasY < canvas[1]) {
                            expected[canvasY][canvasX] = data[y][x]
                        }
                    }
                }
                assertSameBits(expected, actual.toArray())
            }
        }

        where:
        [width, height] << SIZES
    }

    def "rotation by an arbitrary angle (#width x #height)"() {
        given:
        def red = randomImage(width, height, 1)
        def green = randomImage(width, height, 2)
        def blue = randomImage(width, height, 3)
        def rotate = new Rotate([:], Broadcaster.NO_OP)
        ImageMath imageMath = rotate.imageMath

        expect:
        Rotate.estimateBorderLevel(ImagePlane.of(width, height, red)) == referenceBorderLevel(red, width, height)
        for (angle in [0d, 30d, 90d, -123d]) {
            def mono = (ImageWrapper32) rotate.rotateDegrees([img: new ImageWrapper32(width, height, red, [:]), angle: angle])
            def expected = imageMath.rotate(new Image(width, height, red), Math.toRadians(angle), referenceBorderLevel(red, width, height), false)
            assertSameBits(expected.data(), mono.pixels().toArray())
            def rgb = (RGBImage) rotate.rotateDegrees([img: new RGBImage(width, height, red, green, blue, [:]), angle: angle, resize: 1])
            def channels = [rgb.red(), rgb.green(), rgb.blue()]
            [red, green, blue].eachWithIndex { channel, i ->
                def expectedChannel = imageMath.rotate(new Image(width, height, channel), Math.toRadians(angle), referenceBorderLevel(channel, width, height), true)
                assertSameBits(expectedChannel.data(), channels[i].toArray())
            }
        }

        where:
        [width, height] << SIZES
    }

    def "quarter turns and flips match the per-pixel loops (#width x #height)"() {
        given:
        def data = randomImage(width, height, 1)
        def plane = ImagePlane.of(width, height, data)
        def imageMath = ImageMath.newInstance()
        def left = new float[width][height]
        def right = new float[width][height]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                left[width - x - 1][y] = data[y][x]
                right[x][height - y - 1] = data[y][x]
            }
        }

        expect:
        assertSameBits(left, imageMath.rotateLeft(plane).toArray())
        assertSameBits(right, imageMath.rotateRight(plane).toArray())
        for (hflip in [true, false]) {
            for (vflip in [true, false]) {
                def flipped = new float[height][width]
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        flipped[vflip ? height - y - 1 : y][hflip ? width - x - 1 : x] = data[y][x]
                    }
                }
                assertSameBits(flipped, imageMath.mirror(plane, hflip, vflip).toArray())
            }
        }

        where:
        [width, height] << SIZES
    }

    private static List<List<Double>> centers(int width, int height) {
        [[width / 2d, height / 2d], [0d, 0d], [width - 0.6d, height + 4d], [-3.2d, height / 3d]]
    }

    private static Ellipse circle(double cx, double cy, double radius) {
        double a = 1.0d / (radius * radius)
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, a, -2.0d * cx * a, -2.0d * cy * a, cx * cx * a + cy * cy * a - 1.0d))
    }

    private static float referenceBorderLevel(float[][] data, int width, int height) {
        def samples = new float[2 * width + 2 * height]
        def count = 0
        for (int x = 0; x < width; x++) {
            samples[count++] = data[0][x]
            samples[count++] = data[height - 1][x]
        }
        for (int y = 0; y < height; y++) {
            samples[count++] = data[y][0]
            samples[count++] = data[y][width - 1]
        }
        Arrays.sort(samples, 0, count)
        samples[count.intdiv(2)]
    }

    private static Map referenceCropToSquare(float[][] source, int width, int height, Ellipse sunDisk, float blackPoint, Double diameterFactor, Integer rounding) {
        def center = sunDisk.center()
        def semiAxis = sunDisk.semiAxis()
        def diameter = (semiAxis.a() + semiAxis.b())
        int square = diameter > width || diameter > height ? Math.max(width, height) : Math.min(width, height)
        if (diameterFactor != null) {
            square = (int) (diameter * diameterFactor)
            int remainder = square % rounding
            if (remainder != 0) {
                int closestMultiple = (remainder <= rounding.intdiv(2)) ? -remainder : (rounding - remainder)
                square += closestMultiple
            }
        }
        int half = square.intdiv(2)
        referenceCrop(source, width, height, (int) Math.round(center.a()) - half, (int) Math.round(center.b()) - half, square, square, blackPoint)
    }

    private static Map referenceCropToRectangle(float[][] source, int sourceWidth, int sourceHeight, Ellipse sunDisk, float blackPoint, int width, int height) {
        def center = sunDisk.center()
        referenceCrop(source, sourceWidth, sourceHeight, (int) Math.round(center.a()) - width.intdiv(2), (int) Math.round(center.b()) - height.intdiv(2), width, height, blackPoint)
    }

    private static Map referenceCrop(float[][] source, int sourceWidth, int sourceHeight, int originX, int originY, int width, int height, float blackPoint) {
        def cropped = new float[height][width]
        for (float[] line : cropped) {
            Arrays.fill(line, blackPoint)
        }
        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) {
                int sourceX = originX + xx
                int sourceY = originY + yy
                if (sourceX >= 0 && sourceY >= 0 && sourceX < sourceWidth && sourceY < sourceHeight) {
                    cropped[yy][xx] = source[sourceY][sourceX]
                }
            }
        }
        [cropped: cropped, centerShift: new DoublePair(originX, originY)]
    }
}
