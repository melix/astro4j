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
package me.champeau.a4j.ser;

import static me.champeau.a4j.ser.SerFileReader.JSOLEX_RECORDER;

/**
 * The SER file header.
 * @param fileId the file ID
 * @param camera the camera
 * @param geometry the image geometry
 * @param frameCount the total frame count
 * @param metadata image metadata
 */
public record Header(
        String fileId,
        Camera camera,
        ImageGeometry geometry,
        int frameCount,
        ImageMetadata metadata
) {

    /**
     * Determines if this is a JSol'Ex trimmed SER file.
     *
     * @return true if this is a JSol'Ex trimmed SER file
     */
    public boolean isJSolexTrimmedSer() {
        return JSOLEX_RECORDER.equals(fileId);
    }
}
