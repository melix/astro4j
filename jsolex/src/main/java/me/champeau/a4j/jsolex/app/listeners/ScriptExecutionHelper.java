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
package me.champeau.a4j.jsolex.app.listeners;

import javafx.application.Platform;
import me.champeau.a4j.jsolex.app.jfx.ScriptErrorDialog;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.params.OutputMetadata;
import me.champeau.a4j.jsolex.processing.params.ScriptParameterExtractor;

import java.io.File;
import java.util.Map;

/**
 * Helper class for script execution operations shared between
 * SingleModeProcessingEventListener and BatchModeEventListener.
 */
final class ScriptExecutionHelper {

    private ScriptExecutionHelper() {
        // utility class
    }

    /**
     * Processes script errors by displaying them in a dialog on the FX thread.
     *
     * @param result the script execution result containing any invalid expressions
     */
    static void processScriptErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        if (!invalidExpressions.isEmpty()) {
            Platform.runLater(() -> ScriptErrorDialog.showErrors(invalidExpressions));
        }
    }

    /**
     * Extracts output metadata from a script file.
     *
     * @param scriptFile the script file to parse
     * @return a map of output names to their metadata
     */
    static Map<String, OutputMetadata> extractOutputsMetadata(File scriptFile) {
        return ScriptParameterExtractor.extractOutputsMetadataOnly(scriptFile.toPath());
    }

    /**
     * Extracts output metadata from a script string (assumes ImageMath).
     *
     * @param script the script content to parse
     * @return a map of output names to their metadata
     */
    static Map<String, OutputMetadata> extractOutputsMetadata(String script) {
        return ScriptParameterExtractor.extractOutputsMetadataOnly(script, "script.math");
    }

    /**
     * Extracts output metadata from a script string with explicit filename for type detection.
     *
     * @param script   the script content to parse
     * @param filename the filename hint for determining script type
     * @return a map of output names to their metadata
     */
    static Map<String, OutputMetadata> extractOutputsMetadata(String script, String filename) {
        return ScriptParameterExtractor.extractOutputsMetadataOnly(script, filename);
    }
}
