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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

class ImageStatisticsTest extends Specification {

    @Subject
    ImageStatistics imageStatistics = new ImageStatistics([:], Broadcaster.NO_OP)

    // Helper to create a simple test image with uniform value
    private static ImageWrapper32 createImage(int width, int height, float value) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }

    // Helper to create an image with varying values (0, 1, 2, 3, ...)
    private static ImageWrapper32 createImageWithPattern(int width, int height) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (x + y * width)
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }

    // ==================== IMG_AVG Tests ====================

    def "imgAvg returns scalar for single image"() {
        given:
        def img = createImage(2, 2, 10.0f)  // 4 pixels, all 10.0

        when:
        def result = imageStatistics.imgAvg([list: [img]])

        then:
        result == 10.0d
    }

    def "imgAvg computes average of all pixels"() {
        given:
        def img = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = imageStatistics.imgAvg([list: [img]])

        then:
        result == 1.5d  // (0 + 1 + 2 + 3) / 4 = 1.5
    }

    def "imgAvg returns list for multiple images"() {
        given:
        def img1 = createImage(2, 2, 10.0f)  // avg = 10
        def img2 = createImage(2, 2, 20.0f)  // avg = 20

        when:
        def result = imageStatistics.imgAvg([list: [img1, img2]])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 10.0d
        result[1] == 20.0d
    }

    // ==================== IMG_MEDIAN Tests ====================

    def "imgMedian returns scalar for single image"() {
        given:
        def img = createImage(2, 2, 10.0f)

        when:
        def result = imageStatistics.imgMedian([list: [img]])

        then:
        result == 10.0d
    }

    def "imgMedian computes median of all pixels with even count"() {
        given:
        def img = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = imageStatistics.imgMedian([list: [img]])

        then:
        result == 1.5d  // median of sorted [0, 1, 2, 3] = (1 + 2) / 2 = 1.5
    }

    def "imgMedian computes median of all pixels with odd count"() {
        given:
        // Create a 3x3 image with values 0-8
        float[][] data = new float[3][3]
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                data[y][x] = (float) (x + y * 3)
            }
        }
        def img = new ImageWrapper32(3, 3, data, [:])

        when:
        def result = imageStatistics.imgMedian([list: [img]])

        then:
        result == 4.0d  // median of sorted [0, 1, 2, 3, 4, 5, 6, 7, 8] = 4
    }

    def "imgMedian returns list for multiple images"() {
        given:
        def img1 = createImage(2, 2, 10.0f)
        def img2 = createImage(2, 2, 20.0f)

        when:
        def result = imageStatistics.imgMedian([list: [img1, img2]])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 10.0d
        result[1] == 20.0d
    }

    // ==================== IMG_MIN Tests ====================

    def "imgMin returns scalar for single image"() {
        given:
        def img = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = imageStatistics.imgMin([list: [img]])

        then:
        result == 0.0d
    }

    def "imgMin returns list for multiple images"() {
        given:
        def img1 = createImageWithPattern(2, 2)  // min = 0
        def img2 = createImage(2, 2, 50.0f)       // min = 50

        when:
        def result = imageStatistics.imgMin([list: [img1, img2]])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 0.0d
        result[1] == 50.0d
    }

    // ==================== IMG_MAX Tests ====================

    def "imgMax returns scalar for single image"() {
        given:
        def img = createImageWithPattern(2, 2)  // Values: 0, 1, 2, 3

        when:
        def result = imageStatistics.imgMax([list: [img]])

        then:
        result == 3.0d
    }

    def "imgMax returns list for multiple images"() {
        given:
        def img1 = createImageWithPattern(2, 2)  // max = 3
        def img2 = createImage(2, 2, 50.0f)       // max = 50

        when:
        def result = imageStatistics.imgMax([list: [img1, img2]])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 3.0d
        result[1] == 50.0d
    }

    // ==================== IMG_AVG2 (Sigma-Clipped Average) Tests ====================

    def "imgAvg2 returns scalar for single image"() {
        given:
        def img = createImage(2, 2, 10.0f)

        when:
        def result = imageStatistics.imgAvg2([list: [img], sigma: 2.0])

        then:
        result == 10.0d
    }

    def "imgAvg2 clips outliers"() {
        given:
        // Create image with one outlier: [10, 10, 10, 100]
        float[][] data = [[10.0f, 10.0f], [10.0f, 100.0f]]
        def img = new ImageWrapper32(2, 2, data, [:])

        when:
        // With low sigma, the outlier should be clipped
        def result = imageStatistics.imgAvg2([list: [img], sigma: 1.0])

        then:
        // After sigma clipping, only values within 1*stddev of mean should remain
        // Mean = (10+10+10+100)/4 = 32.5
        // The 100 is far from the mean and should be clipped
        result < 32.5d  // Should be closer to 10 after clipping
    }

    def "imgAvg2 returns list for multiple images"() {
        given:
        def img1 = createImage(2, 2, 10.0f)
        def img2 = createImage(2, 2, 20.0f)

        when:
        def result = imageStatistics.imgAvg2([list: [img1, img2], sigma: 2.0])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 10.0d
        result[1] == 20.0d
    }

    // ==================== IMG_MEDIAN2 (Sigma-Clipped Median) Tests ====================

    def "imgMedian2 returns scalar for single image"() {
        given:
        def img = createImage(2, 2, 10.0f)

        when:
        def result = imageStatistics.imgMedian2([list: [img], sigma: 2.0])

        then:
        result == 10.0d
    }

    def "imgMedian2 clips outliers before computing median"() {
        given:
        // Create image with one outlier: [10, 10, 10, 100]
        float[][] data = [[10.0f, 10.0f], [10.0f, 100.0f]]
        def img = new ImageWrapper32(2, 2, data, [:])

        when:
        def result = imageStatistics.imgMedian2([list: [img], sigma: 1.0])

        then:
        // After sigma clipping, the outlier should be removed
        result == 10.0d
    }

    def "imgMedian2 returns list for multiple images"() {
        given:
        def img1 = createImage(2, 2, 10.0f)
        def img2 = createImage(2, 2, 20.0f)

        when:
        def result = imageStatistics.imgMedian2([list: [img1, img2], sigma: 2.0])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 10.0d
        result[1] == 20.0d
    }

    // ==================== Nested List Handling Tests ====================

    def "handles nested lists by flattening"() {
        given:
        def img1 = createImage(2, 2, 10.0f)
        def img2 = createImage(2, 2, 20.0f)
        def img3 = createImage(2, 2, 30.0f)

        when:
        def result = imageStatistics.imgAvg([list: [[img1, img2], img3]])

        then:
        result instanceof List
        result.size() == 3
        result[0] == 10.0d
        result[1] == 20.0d
        result[2] == 30.0d
    }

    // ==================== Edge Cases ====================

    def "empty list throws exception"() {
        when:
        imageStatistics.imgAvg([list: []])

        then:
        thrown(IllegalArgumentException)
    }

    def "handles single pixel image"() {
        given:
        float[][] data = [[42.0f]]
        def img = new ImageWrapper32(1, 1, data, [:])

        when:
        def avg = imageStatistics.imgAvg([list: [img]])
        def median = imageStatistics.imgMedian([list: [img]])
        def min = imageStatistics.imgMin([list: [img]])
        def max = imageStatistics.imgMax([list: [img]])

        then:
        avg == 42.0d
        median == 42.0d
        min == 42.0d
        max == 42.0d
    }
}
