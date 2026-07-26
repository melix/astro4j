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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class RawImageIOTest extends Specification {
    @TempDir
    Path tempDir

    def "round-trips a mono image bit for bit with its metadata"() {
        given:
        def width = 64
        def height = 48
        def data = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (random.nextDouble() * 100000 - 20000)
            }
        }
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1, 0.1, 1.2, -32, -24, 200))
        def image = new ImageWrapper32(width, height, data, [(Ellipse): ellipse] as Map<Class<?>, Object>)
        def target = tempDir.resolve("image.jraw")

        when:
        RawImageIO.write(image, target)
        def restored = RawImageIO.read(target) as ImageWrapper32

        then:
        restored.width() == width
        restored.height() == height
        for (int y = 0; y < height; y++) {
            assert Arrays.equals(restored.data()[y], data[y])
        }
        restored.findMetadata(Ellipse).present

        and: "negative and out-of-range values survive, unlike a 16 bit round-trip"
        restored.data()[0].toList().min() < 0
    }

    def "rejects a file which is not a raw image"() {
        given:
        def target = tempDir.resolve("bogus.jraw")
        target.toFile().text = "not a raw image"

        when:
        RawImageIO.read(target)

        then:
        thrown(ProcessingException)
    }
}
