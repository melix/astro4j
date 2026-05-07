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
package me.champeau.a4j.ser


import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SerFileReaderTest extends Specification {
    @Subject
    private SerFileReader serFile

    @TempDir
    File tempDir

    private File serFilePath

    def cleanup() {
        if (serFile != null) {
            serFile.close()
        }
    }

    def "reads ser file"() {
        given:
        readSerFile "Mars_150414_002445_OSC_F0001-0500"
//        readSerFile "Jup_200415_204534_R_F0001-0300"

        expect:
        serFile.header() != null
        serFile.header().frameCount() == 125
        serFile.header().geometry().colorMode() == ColorMode.BAYER_GRBG
        serFile.header().geometry().width() == 512
        serFile.header().geometry().height() == 440
        serFile.header().geometry().pixelDepthPerPlane() == 8
    }

    def "frame data is a read-only view"() {
        given:
        readSerFile "Mars_150414_002445_OSC_F0001-0500"

        when:
        def buffer = serFile.currentFrame().data()

        then:
        buffer.isReadOnly()
    }

    def "frame data matches raw file bytes"() {
        given:
        readSerFile "Mars_150414_002445_OSC_F0001-0500"
        def rawBytes = Files.readAllBytes(serFilePath.toPath())
        int bytesPerFrame = serFile.header().geometry().getBytesPerFrame()
        int headerLength = rawBytes.length - bytesPerFrame * serFile.header().frameCount() - 8 * serFile.header().frameCount()

        when:
        serFile.seekFrame(frame)
        def buffer = serFile.currentFrame().data()
        byte[] actual = new byte[bytesPerFrame]
        buffer.get(actual)
        byte[] expected = new byte[bytesPerFrame]
        System.arraycopy(rawBytes, headerLength + frame * bytesPerFrame, expected, 0, bytesPerFrame)

        then:
        actual == expected

        where:
        frame << [0, 1, 42, 124]
    }

    def "currentFrame is idempotent and does not mutate buffer state"() {
        given:
        readSerFile "Mars_150414_002445_OSC_F0001-0500"
        int bytesPerFrame = serFile.header().geometry().getBytesPerFrame()

        when:
        serFile.seekFrame(10)
        def first = serFile.currentFrame().data()
        def second = serFile.currentFrame().data()
        byte[] firstBytes = new byte[bytesPerFrame]
        first.get(firstBytes)
        byte[] secondBytes = new byte[bytesPerFrame]
        second.get(secondBytes)

        then:
        !first.is(second)
        firstBytes == secondBytes
    }

    private void readSerFile(String name) {
        SerFileReaderTest.getResourceAsStream("/${name}.ser").withCloseable { stream ->
            def file = new File(tempDir, "${name}.ser")
            Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            serFilePath = file
            serFile = SerFileReader.of(file)
        }
    }

}
