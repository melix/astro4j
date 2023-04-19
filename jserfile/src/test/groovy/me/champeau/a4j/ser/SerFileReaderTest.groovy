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

    private void readSerFile(String name) {
        SerFileReaderTest.getResourceAsStream("/${name}.ser").withCloseable { stream ->
            def file = new File(tempDir, "${name}.ser")
            Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            serFile = SerFileReader.of(file)
        }
    }

}
