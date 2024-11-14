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
package me.champeau.a4j.math.image

import groovy.transform.CompileStatic
import spock.lang.Specification
import spock.lang.TempDir

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class DeconvolutionTest extends Specification {

    public static final Image PSF = Deconvolution.generateGaussianPSF(2.5, 2.5)

    @TempDir
    File tempDir

    private ImageMath imageMath = ImageMath.newInstance()

    def "generates PSF image"() {
        given:
        def img = loadImage("lena.gif")
        def sun = loadImage("sun.png")
        def blurred = imageMath.convolve(img, ImageKernel.of(PSF))

        when:
        def decon = new Deconvolution(imageMath)
                .richardsonLucy(sun, PSF, 4)

        then:
        def file = writeImage(blurred, "blurred.png")
        def file2 = writeImage(decon, "decon.png")
        file
    }

    private File writeImage(Image input, String fileName) {
        def data = input.data()
        int width = input.width()
        var height = input.height()
        def output = new BufferedImage(
                width,
                input.height(),
                BufferedImage.TYPE_INT_RGB
        )
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int grayScale = Math.round(data[y][x]) >> 8
                int pixelValue = (grayScale << 16) | (grayScale << 8) | grayScale
                output.setRGB(x, y, pixelValue);
            }
        }

        File file = new File(tempDir, fileName)
        ImageIO.write(output, "png", file)
        return file
    }

    @CompileStatic
    private static Image loadImage(String name) {
        var img = ImageIO.read(DeconvolutionTest.getResource(name))
        var refW = img.width
        var refH = img.height
        var loaded = new float[refH][refW]
        for (int x = 0; x < refW; x++) {
            for (int y = 0; y < refH; y++) {
                loaded[y][x] = (img.getRGB(x, y) & 0xFF) << 8
            }
        }
        new Image(refW, refH, loaded)
    }
}
