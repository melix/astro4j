/*
 * Copyright 2023-2026 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.util.RawImageIO
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SaveRawListTest extends Specification {

    @TempDir
    Path tempDir

    private static ImageWrapper32 image(float value) {
        float[][] data = new float[4][4]
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(4, 4, data, [:])
    }

    def "save_raw writes one file per image of a list and returns it unchanged"() {
        given:
        var loader = new Loader([:], Broadcaster.NO_OP)
        loader.setWorkingDirectory(tempDir)
        var images = [image(1f), image(2f), image(3f)]

        when:
        var result = loader.saveRaw([img: images, file: "frames"])

        then:
        result.is(images)
        (0..<3).every { tempDir.resolve("frames_${it}.jraw").toFile().exists() }

        and: "each file holds the image it was given, in order"
        (0..<3).every { RawImageIO.read(tempDir.resolve("frames_${it}.jraw")).data()[0][0] == (float) (it + 1) }
    }

    def "save_raw inserts the index before an explicit extension"() {
        given:
        var loader = new Loader([:], Broadcaster.NO_OP)
        loader.setWorkingDirectory(tempDir)

        when:
        loader.saveRaw([img: [image(1f), image(2f)], file: "sub/frames.jraw"])

        then:
        tempDir.resolve("sub/frames_0.jraw").toFile().exists()
        tempDir.resolve("sub/frames_1.jraw").toFile().exists()
    }

    def "save_raw still writes a single image to a single file"() {
        given:
        var loader = new Loader([:], Broadcaster.NO_OP)
        loader.setWorkingDirectory(tempDir)
        var img = image(42f)

        when:
        var result = loader.saveRaw([img: img, file: "single"])

        then:
        result.is(img)
        RawImageIO.read(tempDir.resolve("single.jraw")).data()[0][0] == 42f
    }
}
