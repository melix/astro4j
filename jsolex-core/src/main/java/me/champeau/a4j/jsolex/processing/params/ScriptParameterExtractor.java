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

import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor.ParameterExtractionResult;

import java.nio.file.Path;
import java.util.Map;

/**
 * Unified script parameter extractor that dispatches to the appropriate
 * extractor based on script type (Python or ImageMath).
 */
public final class ScriptParameterExtractor {

    private ScriptParameterExtractor() {
    }

    public static boolean isPythonScript(String filename) {
        return filename != null && filename.endsWith(".py");
    }

    public static boolean isPythonContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        var trimmed = content.stripLeading();
        return trimmed.startsWith("# meta:") || trimmed.startsWith("import ") || trimmed.startsWith("from ");
    }

    public static ParameterExtractionResult extractParameters(Path scriptFile) throws Exception {
        var filename = scriptFile.getFileName().toString();
        if (isPythonScript(filename)) {
            return new PythonParameterExtractor().extractParameters(scriptFile);
        }
        return new ImageMathParameterExtractor().extractParameters(scriptFile);
    }

    public static ParameterExtractionResult extractParameters(String content, String filename) throws Exception {
        if (isPythonScript(filename)) {
            return new PythonParameterExtractor().extractParameters(content, filename);
        }
        return new ImageMathParameterExtractor().extractParameters(content, filename);
    }

    public static ParameterExtractionResult extractParameters(String content, boolean isPython) throws Exception {
        if (isPython) {
            return new PythonParameterExtractor().extractParameters(content, "script.py");
        }
        return new ImageMathParameterExtractor().extractParameters(content, "script.math");
    }

    public static Map<String, OutputMetadata> extractOutputsMetadataOnly(Path scriptFile) {
        try {
            return extractParameters(scriptFile).getOutputsMetadata();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static Map<String, OutputMetadata> extractOutputsMetadataOnly(String content, String filename) {
        try {
            return extractParameters(content, filename).getOutputsMetadata();
        } catch (Exception e) {
            return Map.of();
        }
    }
}
