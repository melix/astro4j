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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.event.ProgressOperation
import me.champeau.a4j.jsolex.processing.params.ProcessParams
import me.champeau.a4j.jsolex.processing.sun.AverageImageCreator
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.ImageUtils
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.ser.ColorMode
import me.champeau.a4j.ser.SerFileReader
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * One-shot fixture generator: reads a SER file and writes its average image as
 * FITS so it can be committed as a test resource. Skipped unless the source SER
 * is explicitly provided via the {@code averageFixture.ser} system property.
 */
@IgnoreIf({ System.getenv('AVG_FIXTURE_SER') == null })
class GenerateAverageFixture extends Specification {

    def "writes the average image of a SER as FITS"() {
        given:
        def source = new File(System.getenv('AVG_FIXTURE_SER'))
        def target = new File(System.getenv('AVG_FIXTURE_OUT'))
        target.parentFile.mkdirs()

        when:
        def reader = SerFileReader.of(source)
        def header = reader.header()
        def geometry = header.geometry()
        def converter = ImageUtils.createImageConverter(ColorMode.MONO, false)
        def operation = ProgressOperation.root('fixture', {})
        def creator = new AverageImageCreator(converter, operation, Broadcaster.NO_OP)
        creator.computeAverageImage(reader)
        def average = creator.getAverageImage()
        reader.close()

        // readFitsFile flips Y for JSol'Ex spectrum files (legacy <= 3.3.x
        // compatibility), so pre-flip here to make the round trip an identity.
        var h = geometry.height()
        var flipped = new float[h][]
        for (int y = 0; y < h; y++) {
            flipped[y] = average[h - 1 - y]
        }
        FitsUtils.writeFitsFile(
                new ImageWrapper32(geometry.width(), h, flipped, [:]),
                target,
                ProcessParams.loadDefaults())

        then:
        println "SER ${geometry.width()}x${geometry.height()} x${header.frameCount()} frames"
        println "wrote ${target.absolutePath} (${target.length()} bytes)"
        target.exists()
    }
}
