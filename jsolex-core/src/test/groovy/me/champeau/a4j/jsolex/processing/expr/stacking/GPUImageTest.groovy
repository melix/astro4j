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
package me.champeau.a4j.jsolex.processing.expr.stacking

import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.ImagePlane
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Specification

@Requires({ OpenCLSupport.isAvailable() })
class GPUImageTest extends Specification {

    def setup() {
        System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, "true")
    }

    def cleanup() {
        System.clearProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
    }

    def "plane upload and download round trip (#width x #height)"() {
        given:
        def plane = randomPlane(width, height, width * 7 + height)

        when:
        def gpuImage = GPUImage.upload(plane, OpenCLSupport.getContext())
        def downloaded = gpuImage.download()

        then:
        gpuImage.width == width
        gpuImage.height == height
        downloaded.width() == width
        downloaded.height() == height
        downloaded.toArray() == plane.toArray()

        cleanup:
        gpuImage?.close()

        where:
        width | height
        1     | 1
        7     | 3
        513   | 257
        1031  | 777
    }

    def "dedistort of an uploaded plane matches the image based GPU dedistort (#width x #height)"() {
        given:
        def context = OpenCLSupport.getContext()
        def plane = randomPlane(width, height, 3)
        def gridStep = 32
        def gridDx = randomGrid(width.intdiv(gridStep) + 2, height.intdiv(gridStep) + 2, 5)
        def gridDy = randomGrid(width.intdiv(gridStep) + 2, height.intdiv(gridStep) + 2, 6)
        def expected = ImageMath.newInstance().dedistort(new Image(width, height, plane.toArray()), gridDx, gridDy, gridStep, true)

        when:
        def gpuImage = GPUImage.upload(plane, context)
        def gpuGrid = GPUDistortionGrid.upload(gridDx, gridDy, gridStep, context)
        def warped = GPUDedistort.dedistort(gpuImage, gpuGrid, true)
        def actual = warped.download().toArray()

        then:
        (0..<height).every { y ->
            (0..<width).every { x -> Float.floatToRawIntBits(actual[y][x]) == Float.floatToRawIntBits(expected.data()[y][x]) }
        }

        cleanup:
        warped?.close()
        gpuGrid?.close()
        gpuImage?.close()

        where:
        width | height
        513   | 257
        1031  | 777
    }

    private static ImagePlane randomPlane(int width, int height, long seed) {
        def random = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = random.nextFloat() * 65535f
            }
        }
        ImagePlane.of(width, height, data)
    }

    private static float[][] randomGrid(int width, int height, long seed) {
        def random = new Random(seed)
        def grid = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = (random.nextFloat() - 0.5f) * 6f
            }
        }
        grid
    }
}
