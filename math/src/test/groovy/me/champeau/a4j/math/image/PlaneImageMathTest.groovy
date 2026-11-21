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
package me.champeau.a4j.math.image

import groovy.transform.CompileStatic
import spock.lang.Shared
import spock.lang.Specification

class PlaneImageMathTest extends Specification {

    @Shared
    List<ImageMath> implementations = [new FallbackImageMath(), new VectorApiImageMath()]

    @Shared
    List<List<Integer>> sizes = [[1, 1], [7, 3], [3, 7], [64, 64], [65, 130], [513, 257], [1031, 777]]

    @Shared
    List<Double> angles = [0d, Math.PI / 2, -Math.PI / 2, Math.PI, Math.PI / 7, -0.3d, 2.5d]

    def "rotations by a quarter turn (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        sameBits(imageMath.rotateLeft(plane), imageMath.rotateLeft(image))
        sameBits(imageMath.rotateRight(plane), imageMath.rotateRight(image))

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "arbitrary rotations (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        for (angle in angles) {
            for (blackpoint in [0f, 12.5f, -1f]) {
                for (resize in [true, false]) {
                    def expected = LegacyImageMath.rotate(image, angle, blackpoint, resize)
                    assert sameBits(imageMath.rotate(plane, angle, blackpoint, resize), expected)
                    assert sameBits(imageMath.rotate(image, angle, blackpoint, resize).data(), expected.data())
                }
            }
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "rotations with scaling (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        for (angle in angles) {
            for (scale in [[1d, 1d], [0.5d, 0.5d], [2d, 1.5d], [1.3d, 0.7d]]) {
                for (blackpoint in [0f, 7f]) {
                    def expected = LegacyImageMath.rotateAndScale(image, angle, blackpoint, scale[0], scale[1])
                    assert sameBits(imageMath.rotateAndScale(plane, angle, blackpoint, scale[0], scale[1]), expected)
                    assert sameBits(imageMath.rotateAndScale(image, angle, blackpoint, scale[0], scale[1]).data(), expected.data())
                }
            }
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "rescaling (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        for (dimensions in [[width * 2, height * 2], [Math.max(1, width.intdiv(2)), Math.max(1, height.intdiv(2))], [width + 3, height], [1, 1], [width, height]]) {
            def expected = LegacyImageMath.rescale(image, dimensions[0], dimensions[1])
            assert sameBits(imageMath.rescale(plane, dimensions[0], dimensions[1]), expected)
            assert sameBits(imageMath.rescale(image, dimensions[0], dimensions[1]).data(), expected.data())
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "mirroring (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        for (horizontal in [true, false]) {
            for (vertical in [true, false]) {
                def mirrored = imageMath.mirror(plane, horizontal, vertical)
                assert !mirrored.is(plane)
                assert sameBits(mirrored, imageMath.mirror(image, horizontal, vertical))
            }
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "convolution (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)
        def random = new Random(width * 31 + height)
        List<Kernel> kernels = [
                Kernel33.GAUSSIAN_BLUR,
                Kernel33.SOBEL_LEFT,
                Kernel33.LAPLACIAN,
                Kernel33.SHARPEN2,
                SharpenKernel.of(5),
                BlurKernel.of(7),
                ImageKernel.of(randomImage(5, 3, random)),
                ImageKernel.of(randomImage(2, 4, random)),
                ImageKernel.of(randomImage(1, 1, random))
        ]

        expect:
        for (kernel in kernels) {
            assert sameBits(imageMath.convolve(plane, kernel), imageMath.convolve(image, kernel))
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "sharpness estimation (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        expect:
        Double.doubleToRawLongBits(imageMath.estimateSharpness(plane)) == Double.doubleToRawLongBits(imageMath.estimateSharpness(image))

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "integral image (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)

        when:
        def integral = imageMath.integralImage(plane)
        def expected = imageMath.integralImage(image)

        then:
        integral.width() == expected.width()
        integral.height() == expected.height()
        sameBits(integral.data(), expected.data())

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    def "dedistortion (#label, #width x #height)"() {
        given:
        def image = randomImage(width, height)
        def plane = toPlane(image)
        def random = new Random(width * 17 + height)
        def gridStep = 16
        def gridDx = randomGrid(width.intdiv(gridStep) + 2, height.intdiv(gridStep) + 2, random)
        def gridDy = randomGrid(width.intdiv(gridStep) + 2, height.intdiv(gridStep) + 2, random)

        expect:
        for (scale in [1d, 1.5d, 2d]) {
            for (lanczos in [true, false]) {
                assert sameBits(imageMath.dedistort(plane, gridDx, gridDy, gridStep, lanczos, scale), imageMath.dedistort(image, gridDx, gridDy, gridStep, lanczos, scale))
            }
        }

        where:
        [imageMath, size] << [implementations, sizes].combinations()
        label = imageMath.class.simpleName
        width = size[0]
        height = size[1]
    }

    private static Image randomImage(int width, int height, Random random = new Random(width * 7919L + height)) {
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (random.nextFloat() * 70000d - 1000d)
            }
        }
        new Image(width, height, data)
    }

    private static float[][] randomGrid(int width, int height, Random random) {
        def grid = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = (float) (random.nextDouble() * 6d - 3d)
            }
        }
        grid
    }

    private static ImagePlane toPlane(Image image) {
        ImagePlane.of(image.width(), image.height(), image.data())
    }

    private static boolean sameBits(ImagePlane plane, Image image) {
        plane.width() == image.width() && plane.height() == image.height() && sameBits(plane.toArray(), image.data())
    }

    @CompileStatic
    private static boolean sameBits(float[][] actual, float[][] expected) {
        if (actual.length != expected.length) {
            return false
        }
        for (int y = 0; y < actual.length; y++) {
            float[] a = actual[y]
            float[] e = expected[y]
            if (a.length != e.length) {
                return false
            }
            for (int x = 0; x < a.length; x++) {
                if (Float.floatToRawIntBits(a[x]) != Float.floatToRawIntBits(e[x])) {
                    return false
                }
            }
        }
        true
    }
}
