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
package me.champeau.a4j.jsolex.processing.util

import me.champeau.a4j.jsolex.processing.params.ProcessParams
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Image operations no longer clamp their result, so values outside the displayable range reach
 * the FITS writer. They must saturate rather than wrap around.
 */
class FitsOutOfRangeTest extends Specification {

    @TempDir
    Path tempDir

    def "out of range values saturate instead of wrapping around"() {
        given:
        var image = new ImageWrapper32(4, 1, [[value, 1000f, 30000f, 65535f] as float[]] as float[][], [:])
        var file = tempDir.resolve("out.fits").toFile()

        when:
        FitsUtils.writeFitsFile(image, file, ProcessParams.loadDefaults())
        var reloaded = (ImageWrapper32) FitsUtils.readFitsFile(file)

        then: "the extreme pixel saturates to white, and the in-range pixels are untouched"
        reloaded.data()[0][0] == expected
        reloaded.data()[0][1] == 1000f
        reloaded.data()[0][2] == 30000f
        reloaded.data()[0][3] == 65535f

        where:
        value     || expected
        65536f    || 65535f
        100000f   || 65535f
        -5000f    || 0f
    }
}
