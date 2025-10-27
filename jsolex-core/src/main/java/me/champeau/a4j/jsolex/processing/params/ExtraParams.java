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
package me.champeau.a4j.jsolex.processing.params;

public record ExtraParams(
        boolean generateDebugImages,
        boolean autosave,
        String fileNamePattern,
        String datetimeFormat,
        String dateFormat,
        boolean reviewImagesAfterBatch,
        GlobeStyle globeStyle) {
    public ExtraParams withGenerateDebugImages(boolean generateDebugImages) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withAutosave(boolean autosave) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withFileNamePattern(String fileNamePattern) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withDateTimeFormat(String datetimeFormat) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withDateFormat(String dateFormat) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withReviewImagesAfterBatch(boolean reviewImagesAfterBatch) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }

    public ExtraParams withGlobeStyle(GlobeStyle globeStyle) {
        return new ExtraParams(generateDebugImages, autosave, fileNamePattern, datetimeFormat, dateFormat, reviewImagesAfterBatch, globeStyle);
    }
}
