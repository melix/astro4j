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
package me.champeau.a4j.jsolex.processing.params

import me.champeau.a4j.jsolex.processing.util.ImageFormat
import spock.lang.Specification

import java.nio.file.Files

/**
 * Tests backward compatibility for ProcessParamsIO, ensuring that old JSON files
 * with the deprecated imageFormats field can still be loaded without errors.
 */
class ProcessParamsIOBackwardCompatibilityTest extends Specification {

    def "loads old JSON with imageFormats field gracefully"() {
        given: "An old JSON file containing imageFormats in extraParams"
        def oldJsonContent = '''
        {
          "spectrumParams": {
            "ray": "H_ALPHA",
            "pixelShift": 0,
            "spectrumSmoothing": 3,
            "continuumShift": 0.0,
            "switchRedBlueChannels": false
          },
          "observationDetails": {
            "instrument": "SOLEX",
            "date": "2025-10-27T12:00:00Z",
            "observer": "Test",
            "telescope": "Test",
            "instrument_name": "Test",
            "email": "test@example.com",
            "camera": "Test",
            "pixelSize": 2.4,
            "focalLength": 1000.0,
            "latitude": 48.8,
            "longitude": 2.3
          },
          "extraParams": {
            "generateDebugImages": false,
            "autosave": true,
            "imageFormats": ["PNG", "JPG", "FITS"],
            "fileNamePattern": "{{BASENAME}}_{{TITLE}}",
            "datetimeFormat": "yyyy_MM_dd_HH_mm_ss",
            "dateFormat": "yyyy-MM-dd",
            "reviewImagesAfterBatch": false,
            "globeStyle": "EQUATORIAL_COORDS"
          }
        }
        '''

        def tempFile = Files.createTempFile("test-process-params", ".json")
        tempFile.toFile().deleteOnExit()
        tempFile.toFile().text = oldJsonContent

        when: "Loading the old JSON file"
        def params = ProcessParamsIO.readFrom(tempFile)

        then: "The file loads successfully without throwing an exception"
        params != null

        and: "The extraParams are loaded correctly (without imageFormats field)"
        params.extraParams().autosave() == true
        params.extraParams().generateDebugImages() == false
        params.extraParams().fileNamePattern() == "{{BASENAME}}_{{TITLE}}"
        params.extraParams().datetimeFormat() == "yyyy_MM_dd_HH_mm_ss"
        params.extraParams().dateFormat() == "yyyy-MM-dd"
        params.extraParams().reviewImagesAfterBatch() == false
        params.extraParams().globeStyle() == GlobeStyle.EQUATORIAL_COORDS
    }

    def "loads old JSON with only PNG format"() {
        given: "An old JSON file with only PNG in imageFormats"
        def oldJsonContent = '''
        {
          "extraParams": {
            "generateDebugImages": false,
            "autosave": true,
            "imageFormats": ["PNG"],
            "fileNamePattern": "{{BASENAME}}_{{TITLE}}",
            "datetimeFormat": "yyyy_MM_dd_HH_mm_ss",
            "dateFormat": "yyyy-MM-dd",
            "reviewImagesAfterBatch": false,
            "globeStyle": "EQUATORIAL_COORDS"
          }
        }
        '''

        def tempFile = Files.createTempFile("test-process-params-png", ".json")
        tempFile.toFile().deleteOnExit()
        tempFile.toFile().text = oldJsonContent

        when: "Loading the old JSON file"
        def params = ProcessParamsIO.readFrom(tempFile)

        then: "The file loads successfully"
        params != null
        params.extraParams() != null
    }

    def "new JSON does not contain imageFormats field after save"() {
        given: "New process params created programmatically"
        def params = ProcessParamsIO.createNewDefaults()

        when: "Serializing to JSON"
        def json = ProcessParamsIO.serializeToJson(params)

        then: "The JSON does not contain imageFormats field in extraParams"
        !json.contains('"imageFormats"')
    }

    def "loads old JSON with all format combinations"() {
        given: "Various format combinations in old JSON"
        def oldJsonContent = """
        {
          "extraParams": {
            "generateDebugImages": false,
            "autosave": true,
            "imageFormats": $formats,
            "fileNamePattern": "{{BASENAME}}_{{TITLE}}",
            "datetimeFormat": "yyyy_MM_dd_HH_mm_ss",
            "dateFormat": "yyyy-MM-dd",
            "reviewImagesAfterBatch": false,
            "globeStyle": "EQUATORIAL_COORDS"
          }
        }
        """

        def tempFile = Files.createTempFile("test-process-params-formats", ".json")
        tempFile.toFile().deleteOnExit()
        tempFile.toFile().text = oldJsonContent

        when: "Loading the old JSON file"
        def params = ProcessParamsIO.readFrom(tempFile)

        then: "The file loads successfully"
        params != null
        params.extraParams() != null

        where:
        formats << [
                '["PNG"]',
                '["JPG"]',
                '["TIF"]',
                '["FITS"]',
                '["PNG", "JPG"]',
                '["PNG", "FITS"]',
                '["JPG", "TIF", "FITS"]',
                '["PNG", "JPG", "TIF", "FITS"]'
        ]
    }
}
