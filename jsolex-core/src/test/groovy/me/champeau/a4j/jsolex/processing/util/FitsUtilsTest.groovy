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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.jsolex.processing.params.ProcessParams
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class FitsUtilsTest extends Specification {

    @TempDir
    Path tempDir

    def "should write and read back random image data correctly"() {
        given: "a random image and process params"
        def width = 100
        def height = 80
        def originalData = generateRandomImageData(width, height)
        // Add some dummy metadata so the file is identified as a JSol'Ex file
        def metadata = [(PixelShift): new PixelShift(0.0)] as Map<Class<?>, Object>
        def originalImage = new ImageWrapper32(width, height, originalData, metadata)
        def processParams = ProcessParams.loadDefaults()
        def fitsFile = tempDir.resolve("test_image.fits").toFile()

        when: "writing and reading the image"
        FitsUtils.writeFitsFile(originalImage, fitsFile, processParams)
        def readImage = FitsUtils.readFitsFile(fitsFile)

        then: "the read image matches the original"
        readImage.width() == width
        readImage.height() == height
        readImage instanceof ImageWrapper32

        and: "the image data matches within tolerance"
        def readData = ((ImageWrapper32) readImage).data()
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Math.abs(originalData[y][x] - readData[y][x]) < 1.0f
            }
        }
    }

    def "should detect vertical flip bug in FITS roundtrip"() {
        given: "an image with distinct values that reveal Y-axis issues"
        def width = 3
        def height = 3
        def data = new float[height][width]

        data[0][0] = 10000.0f; data[0][1] = 10001.0f; data[0][2] = 10002.0f  // top row
        data[1][0] = 30000.0f; data[1][1] = 30001.0f; data[1][2] = 30002.0f  // middle row  
        data[2][0] = 50000.0f; data[2][1] = 50001.0f; data[2][2] = 50002.0f  // bottom row

        // Add some dummy metadata so the file is identified as a JSol'Ex file  
        def metadata = [(PixelShift): new PixelShift(0.0)] as Map<Class<?>, Object>
        def originalImage = new ImageWrapper32(width, height, data, metadata)
        def processParams = ProcessParams.loadDefaults()
        def fitsFile = tempDir.resolve("flip_test.fits").toFile()

        when: "writing and reading the image"
        FitsUtils.writeFitsFile(originalImage, fitsFile, processParams)
        def readImage = FitsUtils.readFitsFile(fitsFile)

        then: "the Y coordinates should match (exposing any flip bug)"
        def readData = ((ImageWrapper32) readImage).data()

        println "Original data:"
        for (int y = 0; y < height; y++) {
            println "  Row ${y}: [${data[y][0]}, ${data[y][1]}, ${data[y][2]}]"
        }

        println "Read data:"
        for (int y = 0; y < height; y++) {
            println "  Row ${y}: [${readData[y][0]}, ${readData[y][1]}, ${readData[y][2]}]"
        }

        println "Checking for perfect Y-flip pattern:"
        println "  Original row 0 vs Read row 2: ${Arrays.equals(data[0], readData[2])}"
        println "  Original row 2 vs Read row 0: ${Arrays.equals(data[2], readData[0])}"

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                def originalValue = data[y][x]
                def readValue = readData[y][x]
                def diff = Math.abs(originalValue - readValue)
                assert diff < 2.0f: "Mismatch at [${y}][${x}]: original=${originalValue}, read=${readValue}, diff=${diff}"
            }
        }
    }

    private static float[][] generateRandomImageData(int width, int height) {
        def data = new float[height][width]
        def random = new Random(42)

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Use a range that works well with FITS 16-bit format
                data[y][x] = 1000.0f + random.nextFloat() * 60000.0f
            }
        }

        return data
    }
}