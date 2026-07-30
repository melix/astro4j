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
import me.champeau.a4j.jsolex.processing.util.RangeMask
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
        // After sigma clipping with sigma=1.0:
        // Mean = (10+10+10+100)/4 = 32.5
        // StdDev = sqrt(((10-32.5)^2 + (10-32.5)^2 + (10-32.5)^2 + (100-32.5)^2)/4) = sqrt((506.25+506.25+506.25+4556.25)/4) = sqrt(1518.75) = 38.97
        // Threshold = 1.0 * 38.97 = 38.97
        // Values within [32.5-38.97, 32.5+38.97] = [-6.47, 71.47] are kept
        // So 10, 10, 10 are kept, 100 is outside threshold and clipped
        // Average of remaining values = (10+10+10)/3 = 10.0
        result == 10.0d
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

    private static ImageWrapper32 clippedImage(Random random, double flatFraction, float clipped) {
        int size = 60
        float[][] data = new float[size][size]
        var flatRows = (int) Math.round(size * flatFraction)
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data[y][x] = y < flatRows ? clipped : (float) (3000 + random.nextGaussian() * 400)
            }
        }
        new ImageWrapper32(size, size, data, [:])
    }

    def "noise_sigma stays stable when clipped pixels become the majority"() {
        given:
        var fractions = [0.40d, 0.45d, 0.49d, 0.50d, 0.51d, 0.55d, 0.60d, 0.80d]

        when:
        var sigmas = fractions.collect { imageStatistics.noiseSigma([img: clippedImage(new Random(1), it, 0f)]) }

        then: "no estimate collapses, so weights derived as 1/sigma² cannot diverge"
        sigmas.every { it > 0 && Double.isFinite(it) }

        and: "crossing the halfway point does not make the estimate jump"
        var min = sigmas.min()
        var max = sigmas.max()
        max / min < 2
    }

    def "noise_sigma ignores non finite pixels"() {
        given:
        var random = new Random(2)
        var clean = clippedImage(random, 0d, 0f)
        var polluted = clippedImage(new Random(2), 0d, 0f)
        for (int i = 0; i < 2000; i++) {
            polluted.data()[(int) (i / 60)][(int) (i % 60)] = i % 2 == 0 ? Float.NaN : Float.POSITIVE_INFINITY
        }

        when:
        var sigma = imageStatistics.noiseSigma([img: polluted])

        then:
        Double.isFinite(sigma)
        sigma > 0
    }

    def "noise_sigma of a fully flat image reports that it cannot measure noise"() {
        expect:
        imageStatistics.noiseSigma([img: createImage(20, 20, 1000f)]) == 0d
    }

    // ==================== IMG_PERCENTILE Tests ====================

    def "imgPercentile agrees with imgMin, imgMedian and imgMax at 0, 50 and 100"() {
        given:
        def img = createImageWithPattern(width, height)

        expect:
        imageStatistics.imgPercentile([list: [img], p: 0d]) == imageStatistics.imgMin([list: [img]])
        imageStatistics.imgPercentile([list: [img], p: 50d]) == imageStatistics.imgMedian([list: [img]])
        imageStatistics.imgPercentile([list: [img], p: 100d]) == imageStatistics.imgMax([list: [img]])

        where: "both an odd and an even number of pixels, since the median differs between the two"
        width | height
        3     | 3
        2     | 2
    }

    def "imgPercentile interpolates between the two surrounding pixels"() {
        given: "values 0, 1, 2, 3, so ranks 0 to 3"
        def img = createImageWithPattern(2, 2)

        expect: "rank = p/100 * 3, interpolated linearly"
        imageStatistics.imgPercentile([list: [img], p: 25d]) == 0.75d
        Math.abs(imageStatistics.imgPercentile([list: [img], p: 10d]) - 0.3d) < 1e-9
    }

    def "imgPercentile returns list for multiple images"() {
        given:
        def img1 = createImageWithPattern(2, 2)
        def img2 = createImage(2, 2, 50.0f)

        when:
        def result = imageStatistics.imgPercentile([list: [img1, img2], p: 100d])

        then:
        result instanceof List
        result.size() == 2
        result[0] == 3.0d
        result[1] == 50.0d
    }

    def "imgPercentile only considers the pixels selected by the mask"() {
        given:
        def img = createImageWithPattern(4, 1)  // Values: 0, 1, 2, 3

        when: "the two highest pixels are masked out"
        def result = imageStatistics.imgPercentile([list: [img], p: 100d, mask: new RangeMask(0d, 1d)])

        then:
        result == 1.0d
    }

    def "imgMin and imgMax only consider the pixels selected by the mask"() {
        given:
        def img = createImageWithPattern(4, 1)  // Values: 0, 1, 2, 3
        def mask = new RangeMask(1d, 2d)

        expect:
        imageStatistics.imgMin([list: [img], mask: mask]) == 1.0d
        imageStatistics.imgMax([list: [img], mask: mask]) == 2.0d
    }

    def "imgPercentile requires the percentile to compute"() {
        when:
        imageStatistics.imgPercentile([list: [createImage(2, 2, 1f)]])

        then:
        thrown(IllegalArgumentException)
    }

    def "imgPercentile rejects a percentile outside 0 to 100"() {
        when:
        imageStatistics.imgPercentile([list: [createImage(2, 2, 1f)], p: value])

        then:
        thrown(IllegalArgumentException)

        where:
        value << [-1d, 101d]
    }
}
