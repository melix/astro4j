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
package me.champeau.a4j.jsolex.processing.stretching

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

class MidtoneTransferFunctionAutostretchStrategyTest extends Specification {

    private static final float MAX = 65535f

    def "brings the background to the target level whatever the level it starts from"() {
        given:
        def image = noisyBackground(background)

        when:
        new MidtoneTransferFunctionAutostretchStrategy(-2.8, 0.25, null).stretch(image)

        then:
        Math.abs(medianOf(image) / MAX - 0.25) < 0.05

        where:
        background << [500f, 8000f, 33000f, 50000f]
    }

    def "maps the pixels below the shadow point to black"() {
        given:
        def image = noisyBackground(33000f)
        // A region far below the background, such as a disk filled with zero
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                image.data()[y][x] = 0f
            }
        }

        when:
        new MidtoneTransferFunctionAutostretchStrategy(-2.8, 0.25, null).stretch(image)

        then:
        image.data()[0][0] == 0f
    }

    private static ImageWrapper32 noisyBackground(float level) {
        var width = 128
        var height = 128
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (level + 500 * (random.nextFloat() - 0.5f))
            }
        }
        return new ImageWrapper32(width, height, data, [:])
    }

    private static double medianOf(ImageWrapper32 image) {
        var values = image.data().collectMany { it as List<Float> }.sort()
        return values[values.size() / 2 as int]
    }
}
