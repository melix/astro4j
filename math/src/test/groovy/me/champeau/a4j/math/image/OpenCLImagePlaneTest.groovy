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

import me.champeau.a4j.math.opencl.GPUImageCache
import me.champeau.a4j.math.opencl.GpuOp
import me.champeau.a4j.math.opencl.OpenCLSpecification
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires

import java.util.function.Consumer

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE

@Requires({ OpenCLSupport.isAvailable() })
class OpenCLImagePlaneTest extends OpenCLSpecification {

    private static final List<List<Integer>> SIZES = [[1, 1], [7, 3], [513, 257], [1031, 777]]

    private OpenCLImageMath gpuMath

    def setup() {
        gpuMath = new OpenCLImageMath(OpenCLSupport.getContext(), 0, 0, false)
    }

    def "plane convolution on GPU matches image convolution (#width x #height, #kernelName)"() {
        given:
        def image = randomImage(width, height, width * 31 + height)
        def plane = toPlane(image)

        expect:
        sameBits(gpuMath.convolve(plane, kernel), gpuMath.convolve(image, kernel))

        where:
        [width, height, kernelName, kernel] << SIZES.collectMany { size ->
            kernels().collect { name, kernel -> [size[0], size[1], name, kernel] }
        }
    }

    def "plane rotateAndScale on GPU matches image rotateAndScale (#width x #height)"() {
        given:
        def image = randomImage(width, height, 7)
        def plane = toPlane(image)

        expect:
        sameBits(gpuMath.rotateAndScale(plane, angle, 12f, scaleX, scaleY), gpuMath.rotateAndScale(image, angle, 12f, scaleX, scaleY))

        where:
        [width, height, angle, scaleX, scaleY] << SIZES.collectMany { size ->
            [[0.3d, 1.0d, 1.0d], [-1.1d, 1.5d, 1.25d], [Math.PI / 2, 2d, 2d]].collect { [size[0], size[1]] + it }
        }
    }

    def "plane rotate on GPU matches image rotate (#width x #height, resize=#resize)"() {
        given:
        def image = randomImage(width, height, 11)
        def plane = toPlane(image)

        expect:
        sameBits(gpuMath.rotate(plane, 0.7d, 3f, resize), gpuMath.rotate(image, 0.7d, 3f, resize))

        where:
        [width, height, resize] << SIZES.collectMany { size -> [true, false].collect { [size[0], size[1], it] } }
    }

    def "plane rotate on GPU with a negative blackpoint produces no negative pixels (resize=#resize)"() {
        given:
        def plane = toPlane(randomImage(513, 257, 17))

        when:
        def result = gpuMath.rotate(plane, 0.7d, -1f, resize).toArray()

        then:
        result.every { row -> row.every { it >= 0 } }

        where:
        resize << [true, false]
    }

    def "plane rescale on GPU matches image rescale (#width x #height -> #newWidth x #newHeight)"() {
        given:
        def image = randomImage(width, height, 13)
        def plane = toPlane(image)

        expect:
        sameBits(gpuMath.rescale(plane, newWidth, newHeight), gpuMath.rescale(image, newWidth, newHeight))

        where:
        [width, height, newWidth, newHeight] << SIZES.collectMany { size ->
            [[size[0] * 2, size[1] * 2], [Math.max(1, size[0].intdiv(2)), Math.max(1, size[1].intdiv(3))], [size[0] + 5, size[1]]].collect { [size[0], size[1]] + it }
        }
    }

    def "plane mirror on GPU matches image mirror (#width x #height, h=#horizontal, v=#vertical)"() {
        given:
        def image = randomImage(width, height, 17)
        def plane = toPlane(image)

        expect:
        sameBits(gpuMath.mirror(plane, horizontal, vertical), gpuMath.mirror(image, horizontal, vertical))

        where:
        [width, height, horizontal, vertical] << SIZES.collectMany { size ->
            [[true, false], [false, true], [true, true], [false, false]].collect { [size[0], size[1]] + it }
        }
    }

    def "plane sharpness on GPU matches image sharpness (#width x #height)"() {
        given:
        def image = randomImage(width, height, 23)
        def plane = toPlane(image)

        expect:
        Double.doubleToLongBits(gpuMath.estimateSharpness(plane)) == Double.doubleToLongBits(gpuMath.estimateSharpness(image))

        where:
        [width, height] << SIZES
    }

    def "plane mirror without flip returns an independent copy"() {
        given:
        def plane = toPlane(randomImage(64, 32, 19))

        when:
        def result = gpuMath.mirror(plane, false, false)

        then:
        !result.is(plane)
        result.toArray() == plane.toArray()
    }

    def "plane methods fall back to the CPU like image methods for small images (#width x #height)"() {
        given:
        def math = new OpenCLImageMath(OpenCLSupport.getContext())
        def image = randomImage(width, height, 23)
        def plane = toPlane(image)

        expect:
        sameBits(math.convolve(plane, Kernel33.GAUSSIAN_BLUR), math.convolve(image, Kernel33.GAUSSIAN_BLUR))
        sameBits(math.rotateAndScale(plane, 0.4d, 0f, 1.2d, 0.8d), math.rotateAndScale(image, 0.4d, 0f, 1.2d, 0.8d))
        sameBits(math.rotate(plane, 0.4d, 0f, true), math.rotate(image, 0.4d, 0f, true))
        sameBits(math.rescale(plane, width * 2, height + 1), math.rescale(image, width * 2, height + 1))
        sameBits(math.mirror(plane, true, true), math.mirror(image, true, true))

        where:
        width | height
        1     | 1
        7     | 3
        100   | 60
    }

    def "segment upload and download round trip through the GPU"() {
        given:
        def context = OpenCLSupport.getContext()
        def plane = toPlane(randomImage(width, height, 29))
        def copy = ImagePlane.allocate(width, height)

        when:
        context.runOp({ op ->
            def buffer = op.allocateBuffer(width * height * Float.BYTES, CL_MEM_READ_WRITE)
            plane.withSegment { op.write(buffer, it) }
            copy.withSegment { op.read(buffer, it) }
        } as Consumer<GpuOp>)

        then:
        copy.toArray() == plane.toArray()

        where:
        [width, height] << SIZES
    }

    def "cache of planes uploads the same bytes as a cache of arrays"() {
        given:
        def context = OpenCLSupport.getContext()
        def width = 513
        def height = 257
        def planes = (0..<3).collect { toPlane(randomImage(width, height, 31 + it)) }
        def planeCache = GPUImageCache.ofPlanes(context, 3, width, height, { int idx -> planes[idx] })
        def arrayCache = new GPUImageCache(context, 3, width, height, { int idx -> flat(planes[idx]) })

        when:
        def fromPlanes = (0..<3).collect { download(context, planeCache.getImage(it), width, height) }
        def fromArrays = (0..<3).collect { download(context, arrayCache.getImage(it), width, height) }

        then:
        (0..<3).every { Arrays.equals(fromPlanes[it], fromArrays[it]) && Arrays.equals(fromPlanes[it], flat(planes[it])) }

        cleanup:
        planeCache?.close()
        arrayCache?.close()
    }

    private static float[] download(def context, long buffer, int width, int height) {
        def result = new float[width * height]
        context.readBuffer(buffer, result)
        result
    }

    private static float[] flat(ImagePlane plane) {
        def result = new float[plane.width() * plane.height()]
        for (int y = 0; y < plane.height(); y++) {
            plane.getRow(y, 0, result, y * plane.width(), plane.width())
        }
        result
    }

    private static Map<String, Kernel> kernels() {
        def gaussian = [1f, 4f, 9f, 14f, 16f, 14f, 9f, 4f, 1f] as float[]
        def separable = new float[gaussian.length][gaussian.length]
        for (int y = 0; y < gaussian.length; y++) {
            for (int x = 0; x < gaussian.length; x++) {
                separable[y][x] = gaussian[y] * gaussian[x]
            }
        }
        def random = new Random(5)
        def dense = new float[7][7]
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                dense[y][x] = random.nextFloat()
            }
        }
        [
                gaussian3 : Kernel33.GAUSSIAN_BLUR,
                sharpen   : Kernel33.SHARPEN,
                separable9: ImageKernel.of(new Image(9, 9, separable)),
                dense7    : ImageKernel.of(new Image(7, 7, dense))
        ]
    }

    private static Image randomImage(int width, int height, long seed) {
        def random = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * 65535f
            }
        }
        new Image(width, height, data)
    }

    private static ImagePlane toPlane(Image image) {
        ImagePlane.of(image.width(), image.height(), image.data())
    }

    private static boolean sameBits(ImagePlane plane, Image image) {
        assert plane.width() == image.width()
        assert plane.height() == image.height()
        def actual = plane.toArray()
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                assert Float.floatToRawIntBits(actual[y][x]) == Float.floatToRawIntBits(image.data()[y][x]): "Pixel ($x, $y) differs"
            }
        }
        true
    }
}
